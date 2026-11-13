package cz.gopay.sdk.model

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
