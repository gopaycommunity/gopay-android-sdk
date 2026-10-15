package cz.gopay.sdk.modules.network

import okhttp3.logging.HttpLoggingInterceptor

/**
 * The debug log of the SDK's HTTP traffic, with the values that would let someone charge or act
 * on a payment, or that GoPay forbids logging, taken out of the bodies before they reach [sink].
 *
 * The `Authorization` header is redacted by the interceptor itself (see
 * [NetworkModule.loggingInterceptor]); it carries `payment_id:payment_secret` on the token
 * request; the GoPay spec says of `payment_secret` that it must not be logged
 * (`Payment-Details.payment_secret`). What is left are body values:
 * - `access_token`, the payment-scoped JWT the token request answers with;
 * - `card_token`, which another payment of the same merchant can be charged with;
 * - `payload`, the card JWE, which the GoPay guide says must not be logged
 *   (Security and credentials, Card data encryption);
 * - `signedMessage`, the encrypted Google Pay payment data, which the GoPay guide calls single
 *   use like the JWE (Google Pay).
 */
internal class RedactingHttpLogger(
    private val sink: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT
) : HttpLoggingInterceptor.Logger {

    override fun log(message: String) {
        sink.log(redact(message))
    }

    companion object {
        /** What a redacted value is replaced with, the mark OkHttp uses for a redacted header. */
        const val MASK = "██"

        private val SECRET_JSON_VALUE =
            // Possessive, so a long escaped value cannot make the matcher backtrack.
            Regex("(\"(?:access_token|card_token|payload|signedMessage)\"\\s*+:\\s*+)\"(?:[^\"\\\\]++|\\\\.)*+\"")

        fun redact(line: String): String =
            SECRET_JSON_VALUE.replace(line) { "${it.groupValues[1]}\"$MASK\"" }
    }
}
