package cz.gopay.sdk.telemetry

import cz.gopay.sdk.exception.GopaySDKException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException

/**
 * The two kinds of text that leave the device in operational data: messages, and the request
 * and response bodies of the SDK's own gateway calls. A port of the GoPay browser SDK's
 * `sanitize.ts`, rule for rule and in the same order, so both SDKs drop the same things.
 *
 * A message is not key-structured, so it is scrubbed by shape and capped ([safeErrorMessage]).
 * A body is redacted by key, and every string that survives the keys still goes through the
 * message scrub: a token under a name nobody predicted still has the shape of a token
 * ([serializeBody]).
 *
 * The patterns spell out ASCII classes rather than `\d`, `\w` and `\s`: Android's regex engine is
 * ICU, where those match Unicode digits, letters and spaces, and the rules would then differ
 * from the browser's, and from the JVM the unit tests run on.
 *
 * Mirrors the iOS SDK's `TelemetryRedaction`.
 */
internal object Redaction {

    const val REDACTED = "[redacted]"

    /** Longest message kept; anything past this is cut. */
    private const val MAX_MESSAGE_LENGTH = 500

    /** The browser's `\s`, so its `\S+` and this one stop at the same character. */
    private const val SPACE =
        "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
    private const val NOT_SPACE = "[^$SPACE]"

    /** 12 to 19 digits is a PAN in every scheme, written whole or in groups. */
    private val PAN_LIKE = Regex("[0-9](?:[ -]?[0-9]){11,18}")

    /** Card-comm form URLs carry a signed session token after the form-type letter. */
    private val CARD_COMM_URL = Regex("(/gp-card-comm/[a-z0-9]+/)$NOT_SPACE+", RegexOption.IGNORE_CASE)

    /** A query string or a fragment can carry a token or an e-mail; the fragment above all. */
    private val QUERY_OR_FRAGMENT = Regex("[?#]$NOT_SPACE*")

    /**
     * A run long enough to be a compact JOSE token; whether it is one is decided in
     * [isCompactJose], which keeps the pattern linear on long text without dots.
     */
    private val JOSE_RUN = Regex("[A-Za-z0-9._-]{26,}")

    /**
     * `name=value` for names never worth printing, without a `?` in front: 3DS posts `MD` and
     * `PaRes` as form fields. Any name ending in a token or a secret. The browser's `\b` in
     * front is the look-behind here; its `\w` is ASCII.
     */
    private val SENSITIVE_PAIR = Regex(
        "(?<![A-Za-z0-9_])(?:MD|PaRes|CRes|creq|authorization|password|[A-Za-z0-9_]*(?:token|secret))" +
            "[$SPACE]*=[$SPACE]*$NOT_SPACE+",
        RegexOption.IGNORE_CASE
    )

    /** Three to five base64url segments of at least eight characters each. */
    private fun isCompactJose(run: String): Boolean {
        val parts = run.split('.')
        return parts.size in 3..5 && parts.all { it.length >= 8 }
    }

    /** A run that is a token, redacted with the dots around it kept: a full stop ends a sentence. */
    private fun redactJose(run: String): String {
        var start = 0
        var end = run.length
        while (start < end && run[start] == '.') start++
        while (end > start && run[end - 1] == '.') end--
        if (!isCompactJose(run.substring(start, end))) return run
        return run.substring(0, start) + REDACTED + run.substring(end)
    }

    private fun isEmailLocalChar(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "._%+-"

    private fun isDomainChar(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-'

    /**
     * The end of the domain half of an e-mail address starting at the `@` at [at], the browser's
     * `@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+`, or -1. Scanned by hand: Java's regex engine recurses
     * once per repetition of a group, and `x@a.a.a…` long enough overflows the stack.
     */
    private fun emailDomainEnd(text: String, at: Int): Int {
        var end = at + 1
        while (end < text.length && isDomainChar(text[end])) end++
        if (end == at + 1) return -1
        var labels = 0
        while (end < text.length && text[end] == '.') {
            var next = end + 1
            while (next < text.length && isDomainChar(text[next])) next++
            if (next == end + 1) break
            end = next
            labels++
        }
        return if (labels > 0) end else -1
    }

    /**
     * E-mail addresses: each domain found from its `@`, the local part walked back from it rather
     * than matched, so every character is visited a bounded number of times.
     */
    private fun redactEmails(text: String): String {
        val out = StringBuilder()
        var last = 0
        var at = text.indexOf('@')
        while (at >= 0) {
            val end = emailDomainEnd(text, at)
            if (end < 0) {
                at = text.indexOf('@', at + 1)
                continue
            }
            var start = at
            while (start > last && isEmailLocalChar(text[start - 1])) start--
            // An `@` with nothing usable in front of it is not an address.
            if (start != at) {
                out.append(text, last, start).append(REDACTED)
                last = end
            }
            at = text.indexOf('@', end)
        }
        return out.append(text, last, text.length).toString()
    }

    /**
     * A message safe to put in a log line: no e-mail, no signed form URL, no query string or
     * fragment, no `name=value` pair for a name never worth printing, no compact JOSE token, no
     * PAN-shaped digits, at most 500 characters. Null reads as `Unknown error`.
     */
    fun safeErrorMessage(message: String?): String {
        var text = redactEmails(message ?: "Unknown error")
        text = CARD_COMM_URL.replace(text) { it.groupValues[1] + REDACTED }
        // Keeps the `?` or `#` so a reader can see what was dropped.
        text = QUERY_OR_FRAGMENT.replace(text) { it.value[0] + REDACTED }
        text = SENSITIVE_PAIR.replace(text, REDACTED)
        text = JOSE_RUN.replace(text) { redactJose(it.value) }
        // Last: the rules above leave `[redacted]` behind, and a PAN can still sit in the rest.
        text = PAN_LIKE.replace(text, REDACTED)
        return text.take(MAX_MESSAGE_LENGTH)
    }

    /**
     * Where a redirect sends the user, as far as it may be recorded: origin and path, scrubbed
     * like a message. Null for something that does not parse. The host is kept as written, as on
     * iOS.
     */
    fun safeTarget(url: String): String? = try {
        val uri = URI(url)
        val scheme = uri.scheme
        val host = uri.host
        if (scheme == null || host == null) {
            null
        } else {
            val port = if (uri.port == -1 || uri.port == defaultPort(scheme.lowercase())) "" else ":${uri.port}"
            val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
            safeErrorMessage("$scheme://$host$port$path")
        }
    } catch (e: Exception) {
        null
    }

    private fun defaultPort(scheme: String): Int = when (scheme) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }

    /**
     * Keys whose whole value is dropped, matched exactly once normalized: the browser SDK's
     * list, gw-ui's plus the instrument of a charge (`input`) and the payer's details, and the
     * payee's bank account the QR payment info carries, in every QR format, which the browser
     * SDK's list does not reach.
     */
    private val SENSITIVE_CONTAINER_KEYS = setOf(
        "response", "payment", "paymentdata", "applepaypaymentdata", "googlepaymentdata",
        "card", "cards", "maskedcard", "maskedcards", "profile", "profiles", "srcprofile",
        "consumer", "consumeridentity", "identity", "threeds", "threedsdata", "challenge", "acs",
        "acsdata", "credential", "credentials", "checkout", "checkoutresponse",
        "checkoutresponsesignature", "redirectparams", "merchantsession",
        "merchantsessionidentifier", "validationdata", "encryptedcard", "recognitiontoken",
        "idtoken", "idtokens", "digitalcardid", "recipientaddress", "accountname", "bic",
        "input", "customer", "contact", "payer", "ip", "ipaddress", "useragent",
        "qrcode", "spayd", "sepa", "mnbqr", "paybysquare", "bankaccount", "recipient"
    )

    /** Fragments that drop any key containing them; the browser SDK's list. */
    private val SENSITIVE_KEY_SUBSTRINGS = listOf(
        "pan", "token", "jwe", "cvv", "cvc", "cryptogram", "cavv", "secret", "signature",
        "password", "identityvalue", "cardnumber", "digitalcardid", "authentication", "otp",
        "email", "phone", "mobile", "firstname", "lastname", "accountnumber", "iban",
        "redirecturl", "maskedvalidationchannel", "encrypted", "signedmessage", "signedkey",
        "publickey", "nonce", "gwurl", "address", "holder", "fingerprint", "expiration",
        "expiry", "expmonth", "expyear", "securitycode", "cardformurl"
    )

    /** Nested deeper than this is dropped whole. */
    private const val MAX_DEPTH = 4

    /** The cap per body. */
    private const val MAX_BODY_LENGTH = 2000

    private val NOT_ALPHANUMERIC = Regex("[^a-z0-9]")

    /** Compared without separators, so `redirect_url` hits `redirecturl`: snake and camel alike. */
    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase().replace(NOT_ALPHANUMERIC, "")
        return normalized in SENSITIVE_CONTAINER_KEYS ||
            SENSITIVE_KEY_SUBSTRINGS.any { normalized.contains(it) }
    }

    /** JSON that arrived as text, parsed; null when it is not an object or an array. */
    private fun parseJsonText(text: String): Any? {
        val first = text.trimStart().firstOrNull()
        if (first != '{' && first != '[') return null
        return try {
            JsonText.parse(text)?.takeIf { it is Map<*, *> || it is List<*> }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * [value] with every sensitive key replaced by `[redacted]`, every string left in it
     * scrubbed, and anything nested past [MAX_DEPTH] dropped. A string holding JSON is read as
     * the structure it is, at the same depth.
     */
    private fun sanitizeLogData(value: Any?, depth: Int = 0): Any? {
        if (depth >= MAX_DEPTH) return REDACTED
        return when (value) {
            is List<*> -> value.map { sanitizeLogData(it, depth + 1) }
            is Map<*, *> -> LinkedHashMap<String, Any?>().also { result ->
                for ((key, item) in value) {
                    val name = key.toString()
                    result[name] = if (isSensitiveKey(name)) REDACTED else sanitizeLogData(item, depth + 1)
                }
            }
            is String -> parseJsonText(value)?.let { sanitizeLogData(it, depth) }
                ?: safeErrorMessage(value)
            null, is Boolean, is JsonText.JsonNumber -> value
            else -> REDACTED
        }
    }

    /**
     * A request or response body as `req_body` / `res_body` carry it: redacted, as JSON, cut at
     * 2000 characters. [body] is the text as it went over the wire, or a form's fields as a map.
     * Null for no body, and never throws: a body is only a diagnostic.
     */
    fun serializeBody(body: Any?): String? {
        if (body == null || body == "" || (body is Map<*, *> && body.isEmpty())) return null
        return try {
            JsonText.stringify(sanitizeLogData(body)).take(MAX_BODY_LENGTH)
        } catch (e: Exception) {
            null
        }
    }

    /** The schema's cap on `params`. */
    private const val MAX_PARAMS_LENGTH = 4096

    /**
     * A `params` object of primitives: strings scrubbed like a message, nulls dropped, and an
     * object too long for the field replaced by its length rather than cut into invalid JSON.
     */
    fun describeParams(fields: Map<String, Any?>): String? {
        val kept = LinkedHashMap<String, Any?>()
        for ((key, value) in fields) {
            when (value) {
                null -> Unit
                is Boolean, is Int, is Long -> kept[key] = value
                // A string, or anything else as its text: scrubbed, never the reason to lose the event.
                else -> kept[key] = safeErrorMessage(value.toString())
            }
        }
        if (kept.isEmpty()) return null
        val json = JsonText.stringify(kept)
        return if (json.length <= MAX_PARAMS_LENGTH) json else JsonText.stringify(mapOf("truncated" to json.length))
    }

    /**
     * The message of [error] with only the text the SDK itself wrote, as iOS cuts it: a charge's
     * `fail_reason`, which the gateway wrote, goes, and every foreign exception the message quotes
     * from its causes, its class name and its message, is replaced by its [failureLabel]. A cause
     * that is itself a [GopaySDKException] is the SDK's own and stays, its own causes are cut.
     */
    fun sdkMessage(error: GopaySDKException): String {
        var text = error.message ?: return "Unknown error"
        error.chargeState?.failReason?.takeIf { it.isNotEmpty() }?.let { text = text.replace(": $it", "") }
        generateSequence(error.cause) { it.cause }.take(10).forEach { cause ->
            if (cause is GopaySDKException) return@forEach
            val label = failureLabel(cause)
            val name = cause.javaClass.simpleName
            val foreign = cause.message.orEmpty()
            if (name.isNotEmpty() && foreign.isNotEmpty()) text = replaceWord(text, "$name: $foreign", label)
            text = replaceWord(text, foreign, label)
            text = replaceWord(text, name, label)
        }
        return text
    }

    /**
     * [text] with [needle] replaced only where it stands as a whole word and is at least three
     * characters long: a foreign message of `x`, or a class named `Error`, must not eat into the
     * words the SDK wrote around it.
     */
    private fun replaceWord(text: String, needle: String, replacement: String): String {
        if (needle.length < 3) return text
        val word = Regex("(?<![A-Za-z0-9_])" + Regex.escape(needle) + "(?![A-Za-z0-9_])")
        return word.replace(text) { replacement }
    }

    /**
     * A foreign failure by kind, never by its class name, which R8 renames in the host's release
     * build, nor by its message: `timeout`, `dns`, `io` or `error`. An SDK error by its code.
     * The same words as on iOS.
     */
    fun failureLabel(error: Throwable): String = when {
        error is GopaySDKException -> error.errorCode
        error is SocketTimeoutException -> "timeout"
        // OkHttp's call timeout reports itself this way.
        error is InterruptedIOException && error.message == "timeout" -> "timeout"
        error is UnknownHostException -> "dns"
        error is IOException -> "io"
        else -> "error"
    }

    /** A code, not prose: uppercase, underscores, bounded. */
    private val CODE = Regex("[A-Z][A-Z0-9_]{0,63}")

    /**
     * What a failed step's `return_value` says: the SDK's error code, the HTTP status of a
     * request that failed, or the [failureLabel] of anything else; never a message.
     */
    fun describeFailure(error: Throwable): String = when {
        error is GopaySDKException && error.errorCode.matches(CODE) -> error.errorCode
        error is GopaySDKException && error.httpContext != null -> "HTTP_${error.httpContext.statusCode}"
        error is GopaySDKException -> "error"
        else -> failureLabel(error)
    }
}
