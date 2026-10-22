#!/usr/bin/env bash
#
# Propagates a release version into the source files semantic-release doesn't know about.
#
# semantic-release owns package.json (via @semantic-release/npm) and CHANGELOG.md (via
# @semantic-release/changelog). Everything else that carries the version has to be rewritten
# here, and listed in the `assets` of @semantic-release/git so the release commit includes it:
#
#   * sdk/gradle.properties: sdk.version, what Maven Central publishes and BuildConfig reports
#   * README.md: the cz.gopay:sdk dependency in Installation, so a copied install gets this release
#
# Invoked automatically by @semantic-release/exec's prepareCmd. Safe to run by hand too:
#
#   ./scripts/set-version.sh 2.0.0
#
set -euo pipefail

version="${1:-}"
if [[ -z "$version" ]]; then
    echo "usage: $(basename "$0") <version>" >&2
    exit 1
fi

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?$ ]]; then
    echo "error: '$version' is not a semver version" >&2
    exit 1
fi

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Rewrites the value of the line starting with `key` in `file`, and fails loudly if it matched
# nothing. A silent no-op here would ship a release with a stale version baked in.
replace() {
    local file="$1" key="$2"
    local path="$root/$file"

    VERSION="$version" perl -0777 -pi -e \
        "die qq(no line starting with ${key} in \$ARGV\\n) unless s/^(${key}[ \\t]*=[ \\t]*)[^\\s]*\$/\$1\$ENV{VERSION}/mg" \
        "$path"

    echo "  $file"
}

# Rewrites the version in every `cz.gopay:sdk:<version>` coordinate of `file`, failing the same
# way when there is none.
replace_coordinate() {
    local file="$1"
    local path="$root/$file"

    VERSION="$version" perl -0777 -pi -e \
        "die qq(no cz.gopay:sdk coordinate in \$ARGV\\n) unless s/(cz\\.gopay:sdk:)[0-9][^\"'\\s]*/\$1\$ENV{VERSION}/g" \
        "$path"

    echo "  $file"
}

echo "Setting version $version in:"
replace "sdk/gradle.properties" 'sdk\.version'
replace_coordinate "README.md"
