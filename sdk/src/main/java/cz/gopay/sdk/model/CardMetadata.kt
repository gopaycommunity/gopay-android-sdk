package cz.gopay.sdk.model

/**
 * What [cz.gopay.sdk.GopaySDK.submitCardFormWithMetadata] returns: the JWE of the card, as
 * `submitCardForm` returns it, and what the gateway knows about the card. The same names on iOS
 * and in the browser SDK.
 *
 * @property encryptedPayload JWE compact serialization (RFC 7516) ready to send to the merchant
 *           backend, good for a single charge.
 * @property cardMetadata the metadata of the card, or null when the gateway knows none, the
 *           lookup failed or took longer than 3 seconds. The payload is good either way.
 */
data class EncryptedCardPayloadWithMetadata(
    val encryptedPayload: String,
    val cardMetadata: CardMetadata?
) {
    /** Masked: the data-class default would print the JWE, which must not be logged. */
    override fun toString(): String =
        "EncryptedCardPayloadWithMetadata(encryptedPayload=***, cardMetadata=$cardMetadata)"
}

/**
 * What the gateway knows about a card from the start of its number: the answer of
 * `POST /cards/bin-lookup`, returned beside the JWE by
 * [cz.gopay.sdk.GopaySDK.submitCardFormWithMetadata]. Shown to the customer or used to pick a
 * flow; a payment never depends on it. `CardMetadata` on iOS and in the browser SDK too.
 *
 * @property scheme the card scheme, [CardScheme.VISA] or [CardScheme.MASTERCARD]; the gateway
 *           knows no other here. The SDK never puts [CardScheme.UNKNOWN] here: unlike the enums
 *           of other answers, which read a value they do not know as `UNKNOWN`, an answer with
 *           any other scheme is read as no metadata at all, on purpose, as the browser SDK
 *           reads it.
 * @property corporate whether the card is a corporate one.
 * @property brand the product level of the card, e.g. `GOLD`; null when the gateway
 *           does not say.
 * @property serviceType debit or credit; null when the gateway does not say, or says a value
 *           this version of the SDK does not know.
 * @property issuer the name of the bank that issued the card; null when the gateway does not say.
 * @property issuerCountry the issuer's country, ISO 3166-1 alpha-3, e.g. `CZE`; null when the
 *           gateway does not say.
 */
data class CardMetadata(
    val scheme: CardScheme,
    val corporate: Boolean,
    val brand: String? = null,
    val serviceType: CardServiceType? = null,
    val issuer: String? = null,
    val issuerCountry: String? = null
)

/** Whether a card is a debit or a credit card, [CardMetadata.serviceType]. */
enum class CardServiceType {
    DEBIT,
    CREDIT
}
