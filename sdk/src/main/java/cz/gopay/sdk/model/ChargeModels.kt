package cz.gopay.sdk.model

import com.squareup.moshi.Json

enum class ChargeState {
    @Json(name = "REQUESTED") REQUESTED,
    @Json(name = "PROCESSING") PROCESSING,
    @Json(name = "ACTION_REQUIRED") ACTION_REQUIRED,
    @Json(name = "SUCCEEDED") SUCCEEDED,
    @Json(name = "FAILED") FAILED
}

enum class ChallengePreference {
    @Json(name = "CHALLENGE_PREFERRED") CHALLENGE_PREFERRED,
    @Json(name = "NO_CHALLENGE_PREFERRED") NO_CHALLENGE_PREFERRED,
    @Json(name = "AUTO") AUTO
}

enum class BankSwift {
    @Json(name = "GIBACZPX") GIBACZPX,
    @Json(name = "KOMBCZPP") KOMBCZPP,
    @Json(name = "SUBASKBX") SUBASKBX,
    @Json(name = "GIBASKBX") GIBASKBX,
    @Json(name = "OTHERS") OTHERS
}

enum class BankPaymentType {
    @Json(name = "PSD2") PSD2,
    @Json(name = "ONLINE") ONLINE,
    @Json(name = "QRPAYMENT") QRPAYMENT,
    @Json(name = "OFFLINE") OFFLINE
}

enum class ChargeActionType {
    @Json(name = "EMV3DS") EMV3DS
}

enum class Emv3dsState {
    @Json(name = "CREATED") CREATED,
    @Json(name = "CHALLENGE_REQUIRED") CHALLENGE_REQUIRED,
    @Json(name = "AUTHENTICATED_CHALLENGE") AUTHENTICATED_CHALLENGE,
    @Json(name = "AUTHENTICATED_FRICTIONLESS") AUTHENTICATED_FRICTIONLESS,
    @Json(name = "NOT_AUTHENTICATED") NOT_AUTHENTICATED,
    @Json(name = "FAILED") FAILED
}

/**
 * Flat input details for a payment instrument. Nullable fields cover all charge variants
 * (CARD_TOKEN, SWIFT, IBAN, ACCOUNT_TOKEN) without requiring a custom Moshi adapter.
 *
 * Note: `challenge_preferrence` is an intentional typo preserved from the API spec wire format.
 */
data class InstrumentInputDetails(
    @Json(name = "input_type") val inputType: String,
    @Json(name = "card_token") val cardToken: String? = null,
    @Json(name = "challenge_preferrence") val challengePreference: ChallengePreference? = null,
    val iban: String? = null,
    val swift: BankSwift? = null,
    @Json(name = "account_holder_name") val accountHolderName: String? = null,
    @Json(name = "bank_payment_type") val bankPaymentType: BankPaymentType? = null,
    @Json(name = "account_token") val accountToken: String? = null
)

/**
 * Payment instrument wrapper for charge requests.
 * Use the factory methods to construct the correct instrument type.
 */
data class PaymentInstrumentInput(
    @Json(name = "payment_instrument") val paymentInstrument: String,
    val input: InstrumentInputDetails
) {
    companion object {
        fun cardToken(
            cardToken: String,
            challengePreference: ChallengePreference? = ChallengePreference.AUTO
        ): PaymentInstrumentInput = PaymentInstrumentInput(
            paymentInstrument = "PAYMENT_CARD",
            input = InstrumentInputDetails(
                inputType = "CARD_TOKEN",
                cardToken = cardToken,
                challengePreference = challengePreference
            )
        )

        fun bankSwift(
            swift: BankSwift,
            bankPaymentType: BankPaymentType? = null
        ): PaymentInstrumentInput = PaymentInstrumentInput(
            paymentInstrument = "BANK_ACCOUNT",
            input = InstrumentInputDetails(
                inputType = "SWIFT",
                swift = swift,
                bankPaymentType = bankPaymentType
            )
        )

        fun bankIban(
            iban: String,
            swift: BankSwift? = null,
            accountHolderName: String? = null
        ): PaymentInstrumentInput = PaymentInstrumentInput(
            paymentInstrument = "BANK_ACCOUNT",
            input = InstrumentInputDetails(
                inputType = "IBAN",
                iban = iban,
                swift = swift,
                accountHolderName = accountHolderName
            )
        )
    }
}

/**
 * Browser data for 3DS authentication. Required for card payments.
 * Collected from the customer's device environment.
 */
data class BrowserData(
    val language: String,
    val timezone: Int,
    @Json(name = "screen_width") val screenWidth: Int,
    @Json(name = "screen_height") val screenHeight: Int,
    @Json(name = "color_depth") val colorDepth: Int,
    @Json(name = "user_agent") val userAgent: String? = null,
    @Json(name = "accept_header") val acceptHeader: String? = null,
    @Json(name = "javascript_enabled") val javascriptEnabled: Boolean? = null
)

/**
 * Request body for POST /payments/{payment_id}/charge.
 * Maps to Payment-Charge-Input in Payments.yaml.
 */
data class ChargePaymentRequest(
    @Json(name = "payment_instrument") val paymentInstrument: PaymentInstrumentInput,
    @Json(name = "return_url") val returnUrl: String = "cz.gopay.sdk://3ds-complete",
    @Json(name = "browser_data") val browserData: BrowserData? = null
)

/**
 * Flat details for a charge response instrument. Nullable fields cover both PAYMENT_CARD
 * and BANK_ACCOUNT variants without requiring a custom Moshi adapter.
 */
data class InstrumentDetails(
    @Json(name = "input_type") val inputType: String,
    @Json(name = "masked_pan") val maskedPan: String? = null,
    @Json(name = "expiration_month") val expirationMonth: String? = null,
    @Json(name = "expiration_year") val expirationYear: String? = null,
    val scheme: CardScheme? = null,
    val fingerprint: String? = null,
    val iban: String? = null,
    val swift: BankSwift? = null,
    @Json(name = "account_holder_name") val accountHolderName: String? = null
)

/**
 * Payment instrument data returned in charge responses.
 * The `paymentInstrument` field is "PAYMENT_CARD" or "BANK_ACCOUNT".
 */
data class PaymentInstrumentData(
    @Json(name = "payment_instrument") val paymentInstrument: String,
    val details: InstrumentDetails
)

/**
 * Follow-up action required to complete a charge (e.g. 3DS redirect).
 * Maps to Payment-Charge-Action in Payments.yaml.
 */
data class ChargeAction(
    @Json(name = "action_type") val actionType: ChargeActionType,
    val state: Emv3dsState? = null,
    @Json(name = "redirect_url") val redirectUrl: String? = null
)

/**
 * Response for POST /payments/{payment_id}/charge and GET /payments/{payment_id}/charge.
 * Maps to Payment-Charge-Response-Data in Payments.yaml.
 */
data class ChargePaymentResponse(
    val id: String,
    val state: ChargeState,
    @Json(name = "payment_instrument") val paymentInstrument: PaymentInstrumentData,
    @Json(name = "return_url") val returnUrl: String,
    val action: ChargeAction? = null
)
