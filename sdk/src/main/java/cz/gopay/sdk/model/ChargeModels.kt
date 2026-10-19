package cz.gopay.sdk.model

import com.squareup.moshi.Json

/**
 * State of a charge. Only [SUCCEEDED] and [FAILED] are results, and [ACTION_REQUIRED] waits for a
 * 3DS challenge; [REQUESTED] and [PROCESSING] are not an outcome yet, so read
 * [cz.gopay.sdk.session.PaymentSession.getChargeState] again until the state is one of those.
 */
enum class ChargeState {
    @Json(name = "REQUESTED") REQUESTED,
    @Json(name = "PROCESSING") PROCESSING,
    @Json(name = "ACTION_REQUIRED") ACTION_REQUIRED,
    @Json(name = "SUCCEEDED") SUCCEEDED,
    @Json(name = "FAILED") FAILED,
    /**
     * A value this version of the SDK does not know, which the gateway added later. The rest
     * of the response is read as usual; the raw value is in the debug log. It is not a result:
     * keep reading [cz.gopay.sdk.session.PaymentSession.getChargeState], or settle the payment
     * on your backend.
     */
    UNKNOWN
}

enum class ChallengePreference {
    @Json(name = "CHALLENGE_PREFERRED") CHALLENGE_PREFERRED,
    @Json(name = "NO_CHALLENGE_PREFERRED") NO_CHALLENGE_PREFERRED,
    @Json(name = "AUTO") AUTO
}

enum class ChargeActionType {
    @Json(name = "EMV3DS") EMV3DS,
    /**
     * A value this version of the SDK does not know, which the gateway added later. The rest
     * of the response is read as usual; the raw value is in the debug log.
     */
    UNKNOWN
}

enum class Emv3dsState {
    @Json(name = "CREATED") CREATED,
    @Json(name = "CHALLENGE_REQUIRED") CHALLENGE_REQUIRED,
    @Json(name = "AUTHENTICATED_CHALLENGE") AUTHENTICATED_CHALLENGE,
    @Json(name = "AUTHENTICATED_FRICTIONLESS") AUTHENTICATED_FRICTIONLESS,
    @Json(name = "NOT_AUTHENTICATED") NOT_AUTHENTICATED,
    @Json(name = "FAILED") FAILED,
    /**
     * A value this version of the SDK does not know, which the gateway added later. The rest
     * of the response is read as usual; the raw value is in the debug log.
     */
    UNKNOWN
}

/**
 * Browser data collected for 3DS authentication. Required on every card charge regardless of
 * the input type. Maps to `Browser-Data` in the published spec.
 *
 * The gateway requires every field, [ip] included, and rejects a charge without it. The device
 * cannot know its own public address, and the issuer expects [userAgent] and [acceptHeader] to
 * come from the same request that produced the address, so the SDK fetches the three from
 * `GET /cards/browser-data` right before it charges and fills in whichever of them is null; see
 * [cz.gopay.sdk.session.PaymentSession.charge]. A value you set yourself is kept, so pass [ip]
 * only if you collected it in the customer's own browser. [javascriptEnabled] is filled as
 * `true` when null, because the challenge runs in a WebView with JavaScript on.
 */
data class BrowserData(
    val language: String,
    val timezone: Int,
    @Json(name = "screen_width") val screenWidth: Int,
    @Json(name = "screen_height") val screenHeight: Int,
    @Json(name = "color_depth") val colorDepth: Int,
    @Json(name = "user_agent") val userAgent: String? = null,
    @Json(name = "accept_header") val acceptHeader: String? = null,
    @Json(name = "javascript_enabled") val javascriptEnabled: Boolean? = null,
    /** Public address of the customer's browser, at most 45 characters. */
    val ip: String? = null
) {
    /** Anchor for [deviceDefault], the device-derived factory in `BrowserDataDevice.kt`. */
    companion object
}

/**
 * Response of `GET /cards/browser-data`: the [BrowserData] fields the device cannot determine on
 * its own, derived by the gateway from the request that fetched them. Maps to
 * `Browser-Data-Detected`. The SDK merges it into the charge's [BrowserData].
 */
data class BrowserDataDetected(
    val ip: String,
    @Json(name = "user_agent") val userAgent: String,
    @Json(name = "accept_header") val acceptHeader: String
)

/**
 * Header fields embedded in an Apple Pay payment token. Maps to the nested `header` object on
 * `Apple-Pay-Input` in Payments.yaml.
 */
data class ApplePayHeader(
    val ephemeralPublicKey: String,
    val publicKeyHash: String,
    val transactionId: String
)

/**
 * Card-payment input. Discriminated `oneOf` over `input_type`. Maps to `Payment-Card-Input`
 * (`CARD_TOKEN | GOOGLE_PAY | APPLE_PAY | ENCRYPTED_CARD`).
 *
 * Use the [Companion] factories; the constructor is open so the SDK can add new variants
 * without breaking callers. `payload` belongs to the `ENCRYPTED_CARD` variant — a JWE produced
 * by [cz.gopay.sdk.GopaySDK.encryptCardData].
 */
data class PaymentCardInput(
    @Json(name = "input_type") val inputType: String,
    // CARD_TOKEN
    @Json(name = "card_token") val cardToken: String? = null,
    // GOOGLE_PAY — note camelCase keys mirror what the Google Pay token carries
    val protocolVersion: String? = null,
    val signature: String? = null,
    val intermediateSigningKey: IntermediateSigningKey? = null,
    val signedMessage: String? = null,
    // APPLE_PAY
    val data: String? = null,
    val version: String? = null,
    val header: ApplePayHeader? = null,
    // ENCRYPTED_CARD
    val payload: String? = null
) {
    companion object {
        fun cardToken(cardToken: String): PaymentCardInput =
            PaymentCardInput(inputType = "CARD_TOKEN", cardToken = cardToken)

        /**
         * `ENCRYPTED_CARD` input — charges directly with a JWE-encrypted card, skipping the
         * server-side `POST /cards/tokens` round-trip. [payload] is the JWE compact string from
         * [cz.gopay.sdk.GopaySDK.encryptCardData].
         */
        fun encryptedCard(payload: String): PaymentCardInput =
            PaymentCardInput(inputType = "ENCRYPTED_CARD", payload = payload)

        fun googlePay(
            protocolVersion: String,
            signature: String,
            intermediateSigningKey: IntermediateSigningKey?,
            signedMessage: String
        ): PaymentCardInput = PaymentCardInput(
            inputType = "GOOGLE_PAY",
            protocolVersion = protocolVersion,
            signature = signature,
            intermediateSigningKey = intermediateSigningKey,
            signedMessage = signedMessage
        )

        fun applePay(
            data: String,
            signature: String,
            version: String,
            header: ApplePayHeader
        ): PaymentCardInput = PaymentCardInput(
            inputType = "APPLE_PAY",
            data = data,
            signature = signature,
            version = version,
            header = header
        )
    }
}

/**
 * Card-instrument variant of the `Payment-Charge-Data` union. Carries the input together with
 * the required browser data and an optional 3DS challenge preference. Maps to
 * `Payment-Card-Charge-Data`.
 *
 * The current Payments 4.0 schema only defines `PAYMENT_CARD` for [paymentInstrument]; non-card
 * instruments are not chargeable through this endpoint.
 */
data class PaymentChargeInstrument(
    @Json(name = "payment_instrument") val paymentInstrument: String = "PAYMENT_CARD",
    val input: PaymentCardInput,
    @Json(name = "browser_data") val browserData: BrowserData,
    @Json(name = "challenge_preference") val challengePreference: ChallengePreference? = null
)

/**
 * Request body for `POST /payments/{payment_id}/charge`. Maps to `Payment-Charge-Input`.
 *
 * Use the [Companion] factories for the common card-token and Google Pay flows.
 */
data class ChargePaymentRequest(
    @Json(name = "payment_instrument") val paymentInstrument: PaymentChargeInstrument
) {
    companion object {
        fun cardToken(
            cardToken: String,
            browserData: BrowserData,
            challengePreference: ChallengePreference? = null
        ): ChargePaymentRequest = ChargePaymentRequest(
            paymentInstrument = PaymentChargeInstrument(
                input = PaymentCardInput.cardToken(cardToken),
                browserData = browserData,
                challengePreference = challengePreference
            )
        )

        /**
         * Charge directly with a JWE-encrypted card ([payload]), skipping the server-side
         * `POST /cards/tokens` tokenization step. [payload] is the JWE compact string returned by
         * [cz.gopay.sdk.GopaySDK.encryptCardData].
         */
        fun encryptedCard(
            payload: String,
            browserData: BrowserData,
            challengePreference: ChallengePreference? = null
        ): ChargePaymentRequest = ChargePaymentRequest(
            paymentInstrument = PaymentChargeInstrument(
                input = PaymentCardInput.encryptedCard(payload),
                browserData = browserData,
                challengePreference = challengePreference
            )
        )

        fun googlePay(
            protocolVersion: String,
            signature: String,
            intermediateSigningKey: IntermediateSigningKey?,
            signedMessage: String,
            browserData: BrowserData,
            challengePreference: ChallengePreference? = null
        ): ChargePaymentRequest = ChargePaymentRequest(
            paymentInstrument = PaymentChargeInstrument(
                input = PaymentCardInput.googlePay(
                    protocolVersion = protocolVersion,
                    signature = signature,
                    intermediateSigningKey = intermediateSigningKey,
                    signedMessage = signedMessage
                ),
                browserData = browserData,
                challengePreference = challengePreference
            )
        )
    }
}

/**
 * Output card details returned in a charge response. Maps to `Payment-Card-Charge-Details`.
 */
data class InstrumentDetails(
    @Json(name = "input_type") val inputType: String,
    @Json(name = "masked_pan") val maskedPan: String? = null,
    @Json(name = "expiration_month") val expirationMonth: String? = null,
    @Json(name = "expiration_year") val expirationYear: String? = null,
    val scheme: CardScheme? = null,
    val fingerprint: String? = null
)

/**
 * Payment instrument block in a charge response. `paymentInstrument` is always `PAYMENT_CARD`
 * for the current Payments 4.0 schema.
 */
data class PaymentInstrumentData(
    @Json(name = "payment_instrument") val paymentInstrument: String,
    val details: InstrumentDetails? = null
)

/**
 * Follow-up action required to complete a charge (e.g. 3DS redirect). Maps to
 * `Payment-Charge-Action`.
 */
data class ChargeAction(
    @Json(name = "action_type") val actionType: ChargeActionType,
    val state: Emv3dsState? = null,
    @Json(name = "redirect_url") val redirectUrl: String? = null
)

/**
 * Response for POST/GET `/payments/{payment_id}/charge`, and the value of
 * `Payment-Details.charge` returned by `GET /payments/{payment_id}`. Maps to
 * `Payment-Charge-Status-Response` in Payments.yaml. Per the spec only `id`, `state`, and
 * `return_url` are required; instrument details and follow-up action are absent in early
 * states, and `fail_reason` is only present when `state == FAILED`.
 *
 * GoPay is removing `return_url` from the charge response, whatever the spec says, so
 * [returnUrl] is deprecated and decodes as null when it is missing; see
 * [ChargePaymentResponseAdapter].
 */
data class ChargePaymentResponse(
    val id: String,
    val state: ChargeState,
    /**
     * The `return_url` the charge response used to repeat. GoPay is removing it from the charge
     * response, so do not rely on it; 2.0 keeps the field so code that reads it still compiles.
     *
     * The address [cz.gopay.sdk.session.PaymentSession.handle3dsVerification] needs is the one
     * your backend created the payment with, `callback.return_url`, and the app gets it from its
     * backend together with `payment_id` and `payment_secret`. Without one the SDK waits for
     * [cz.gopay.sdk.GopaySDK.CHARGE_RETURN_URL], and the payment then has to have been created
     * with that address. A blank value decodes as null too: `url.startsWith("")` matches every
     * URL, so the first page of a challenge would read as a finished verification.
     */
    @Deprecated(
        "GoPay is removing return_url from the charge response; do not rely on it. Pass " +
            "handle3dsVerification the callback.return_url your backend created the payment with."
    )
    @Json(name = "return_url") val returnUrl: String? = null,
    @Json(name = "payment_instrument") val paymentInstrument: PaymentInstrumentData? = null,
    val action: ChargeAction? = null,
    @Json(name = "fail_reason") val failReason: String? = null
)
