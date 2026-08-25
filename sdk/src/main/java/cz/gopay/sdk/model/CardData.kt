package cz.gopay.sdk.model

import com.squareup.moshi.Json

/** Last four digits for logging, or full mask when the value is too short to mask safely. */
private fun maskPan(pan: String): String =
    if (pan.length >= 13) "****${pan.takeLast(4)}" else "****"

/**
 * Raw card data collected from the customer. Passed to [cz.gopay.sdk.GopaySDK.encryptCardData]
 * which produces a JWE for the merchant backend to tokenize.
 */
data class CardData(
    @Json(name = "card_pan") val cardPan: String,
    @Json(name = "exp_month") val expMonth: String,
    @Json(name = "exp_year") val expYear: String,
    val cvv: String
) {
    /** Masked — the data-class default would print the full PAN and CVV into any log. */
    override fun toString(): String =
        "CardData(cardPan=${maskPan(cardPan)}, expMonth=$expMonth, expYear=$expYear, cvv=***)"
}

/** Full JWE plaintext payload — card fields plus the metadata claims required by the spec. */
internal data class CardJwePayload(
    @Json(name = "card_pan") val cardPan: String,
    @Json(name = "exp_month") val expMonth: String,
    @Json(name = "exp_year") val expYear: String,
    val cvv: String,
    @Json(name = "client_id") val clientId: String,
    // NumericDate (Unix seconds) — backend rejects ISO 8601 strings
    val iat: Long,
    val exp: Long,
    val jti: String
) {
    /** Masked — the data-class default would print the full PAN and CVV into any log. */
    override fun toString(): String =
        "CardJwePayload(cardPan=${maskPan(cardPan)}, expMonth=$expMonth, expYear=$expYear, " +
            "cvv=***, clientId=$clientId, iat=$iat, exp=$exp, jti=$jti)"
}

/** JWE header structure (RFC 7516 §4) emitted by [cz.gopay.sdk.service.EncryptionService]. */
data class JweHeader(
    val alg: String = "RSA-OAEP-256",
    val enc: String = "A256GCM",
    val kid: String,
    val typ: String = "JWE"
)

/** JWK (JSON Web Key, RFC 7517) returned by `GET /cards/public-key`. */
data class Jwk(
    val kty: String,
    val kid: String,
    val use: String,
    val alg: String,
    val n: String,
    val e: String
)

/**
 * Card scheme returned in `Charge-Response.payment_instrument.details.scheme`.
 */
enum class CardScheme {
    @Json(name = "VISA")
    VISA,
    @Json(name = "MASTERCARD")
    MASTERCARD
}
