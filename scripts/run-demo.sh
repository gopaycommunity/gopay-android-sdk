#!/usr/bin/env bash
#
# Launches the demo app against the gateway described in `.env`.
#
#   ./scripts/run-demo.sh
#   ./scripts/run-demo.sh --install
#
# The credentials travel as intent extras, which is the one channel that does not bake them into
# the APK — see `app/src/main/java/com/gopay/example/DemoLaunchOverrides.kt`. That also means they
# appear in this process's `adb` command line and in logcat: fine for a demo pointed at a test
# gateway, not a pattern for a real app.
#
# Set ANDROID_SERIAL to pick a device when more than one is attached.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${GOPAY_DEMO_ENV_FILE:-$REPO_ROOT/.env}"
ACTIVITY="com.gopay.example/.MainActivity"
KEYS=(BASE_URL CLIENT_ID SHAREABLE_KEY CLIENT_SECRET GOID)

INSTALL=0
while [ $# -gt 0 ]; do
    case "$1" in
        --install) INSTALL=1; shift ;;
        -h|--help)
            echo "usage: ${BASH_SOURCE[0]##*/} [--install]"
            echo "  --install   run ./gradlew :app:installDebug first"
            echo "Reads $ENV_FILE — copy .env.example and fill in your GoPay values."
            exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

if [ ! -f "$ENV_FILE" ]; then
    echo "error: $ENV_FILE not found — copy .env.example to .env and fill it in." >&2
    exit 1
fi

command -v adb >/dev/null || { echo "error: adb not on PATH (install platform-tools)." >&2; exit 1; }

# Read the file rather than sourcing it: a stray backtick or $(...) in a credential should stay a
# credential, not become a command. Accepts `KEY=value`, ignores comments and blank lines, and
# strips one layer of surrounding quotes.
lookup() {
    local wanted="$1" line key value
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line#"${line%%[![:space:]]*}"}"
        case "$line" in ''|'#'*) continue ;; esac
        key="${line%%=*}"
        [ "$key" = "$line" ] && continue
        key="${key#export }"
        key="${key%"${key##*[![:space:]]}"}"
        [ "$key" = "$wanted" ] || continue
        value="${line#*=}"
        value="${value#"${value%%[![:space:]]*}"}"
        value="${value%"${value##*[![:space:]]}"}"
        case "$value" in
            \"*\") value="${value#\"}"; value="${value%\"}" ;;
            \'*\') value="${value#\'}"; value="${value%\'}" ;;
        esac
        printf '%s' "$value"
        return 0
    done < "$ENV_FILE"
    return 0
}

# `adb shell` joins its arguments and hands the string to the device's shell, so every value is
# quoted for that second shell, not just for this one. Building the single quote as $sq keeps the
# substitution readable and, more to the point, correct on bash 3.2, the stock shell on macOS.
sq="'"

EXTRAS=()
MISSING=()
for key in "${KEYS[@]}"; do
    value="$(lookup "GOPAY_DEMO_${key}")"
    if [ -z "$value" ]; then
        MISSING+=("GOPAY_DEMO_${key}")
        continue
    fi
    EXTRAS+=(-e "GOPAY_DEMO_${key}" "$sq${value//$sq/$sq\\$sq$sq}$sq")
done

# Every key is optional, matching the app's own contract: an extra left out keeps its compiled-in
# default. Warn rather than refuse, so a partial .env is still runnable.
if [ ${#MISSING[@]} -gt 0 ]; then
    echo "warning: $ENV_FILE has no value for ${MISSING[*]}; the app keeps its built-in default for those" >&2
fi

if [ "$INSTALL" -eq 1 ]; then
    echo "==> ./gradlew :app:installDebug"
    (cd "$REPO_ROOT" && ./gradlew :app:installDebug)
fi

base_url="$(lookup GOPAY_DEMO_BASE_URL)"
echo "==> launching against ${base_url:-the built-in gateway}"

# -S force-stops first, so the extras reach a cold start instead of merely fronting a running
# task that would keep the gateway it already had.
#
# am start reports a failure in its output, not its exit status, so without reading it a launch
# onto a device with no app installed looks like a success. The ${EXTRAS[@]+...} form is what
# keeps an empty array from tripping `set -u` on bash 3.2.
launch_output="$(adb shell am start -S -n "$ACTIVITY" ${EXTRAS[@]+"${EXTRAS[@]}"} 2>&1)"
printf '%s\n' "$launch_output"
if printf '%s\n' "$launch_output" | grep -q '^Error'; then
    exit 1
fi
