package com.gopay.sdk.model

import com.squareup.moshi.Json

/**
 * Card data to be encrypted in JWE payload
 * Based on GoPay API specification for card tokenization
 */
data class CardData(
    val cardPan: String,
    val expMonth: String,
    val expYear: String,
    val cvv: String
)

/**
 * JWE header structure according to RFC 7516 Section 4
 */
data class JweHeader(
    val alg: String = "RSA-OAEP-256",
    val enc: String = "A256GCM", 
    val kid: String,
    val typ: String = "JWE"
)

/**
 * JWK (JSON Web Key) structure according to RFC 7515
 */
data class Jwk(
    val kty: String,
    val kid: String,
    val use: String,
    val alg: String,
    val n: String,
    val e: String
)

/**
 * Card tokenization request payload
 */
data class CardTokenRequest(
    val payload: String, // JWE string
    val permanent: Boolean = false
)

/**
 * Card scheme enum
 * Based on GoPay API specification for card tokenization
 * https://speca.io/gopaycz/gopay-next-gen#card-scheme
 */
enum class CardScheme {
    @Json(name = "VISA")
    VISA,
    @Json(name = "MASTERCARD")
    MASTERCARD
}


/**
 * Card scheme enum
 * Based on GoPay API specification for card tokenization
 * https://speca.io/gopaycz/gopay-next-gen#card-scheme
 */
enum class CardServiceType {
    @Json(name = "DEBIT")
    DEBIT,
    @Json(name = "CREDIT")
    CREDIT
}

/**
 * Card tokenization response
 * Maps server response fields (snake_case) to Kotlin properties (camelCase)
 * Based on actual GoPay API response format
 */
data class CardTokenResponse(
    @Json(name = "masked_pan")
    val maskedPan: String,
    @Json(name = "expiration_month") 
    val expirationMonth: String,
    @Json(name = "expiration_year")
    val expirationYear: String,
    val scheme: CardScheme? = null,
    val brand: String? = null,
    @Json(name = "service_type")
    val serviceType: CardServiceType? = null,
    val corporate: Boolean? = null,
    val fingerprint: String,
    val token: String,
    @Json(name = "expires_in")
    val expiresIn: String? = null,
    @Json(name = "card_art_url")
    val cardArtUrl: String? = null,
    @Json(name = "masked_virtual_pan")
    val maskedVirtualPan: String? = null
) 