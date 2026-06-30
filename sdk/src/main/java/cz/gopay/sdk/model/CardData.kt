package cz.gopay.sdk.model

import com.squareup.moshi.Json

/**
 * Raw card data collected from the customer. Passed to [cz.gopay.sdk.GopaySDK.encryptCardData]
 * which produces a JWE for the merchant backend to tokenize.
 */
data class CardData(
    @Json(name = "card_pan") val cardPan: String,
    @Json(name = "exp_month") val expMonth: String,
    @Json(name = "exp_year") val expYear: String,
    val cvv: String
)

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
)

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
