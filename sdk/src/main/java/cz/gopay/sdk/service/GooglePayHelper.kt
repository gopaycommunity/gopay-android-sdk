package cz.gopay.sdk.service

import com.squareup.moshi.Json
import cz.gopay.sdk.model.GooglePayAllowedMethod
import cz.gopay.sdk.model.GooglePayDataRequest
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.IntermediateSigningKey
import cz.gopay.sdk.model.PaymentCardInput
import cz.gopay.sdk.modules.network.NetworkModule

/**
 * Utility for bridging between the GoPay API and the Google Pay Android SDK.
 *
 * Usage:
 * 1. Call [buildPaymentDataRequestJson] with the result of
 *    [cz.gopay.sdk.session.PaymentSession.getGooglePayInfo] to get a JSON string for
 *    `PaymentDataRequest.fromJson()` in the Google Pay SDK.
 * 2. After the user approves, call [parseGooglePayToken] with `PaymentData.toJson()` to get a
 *    [PaymentCardInput] you can wrap in [cz.gopay.sdk.model.ChargePaymentRequest.googlePay].
 */
object GooglePayHelper {

    private val moshi get() = NetworkModule.moshi

    /**
     * Serializes [GooglePayInfoResponse.paymentDataRequest] to a JSON string suitable for
     * `PaymentDataRequest.fromJson()` in the Google Pay Android SDK.
     */
    fun buildPaymentDataRequestJson(info: GooglePayInfoResponse): String {
        val adapter = moshi.adapter(GooglePayDataRequest::class.java)
        return adapter.toJson(info.paymentDataRequest)
    }

    /**
     * Parses the JSON string from `PaymentData.toJson()` (Google Pay Android SDK result) and
     * returns a [PaymentCardInput] of `input_type=GOOGLE_PAY`.
     *
     * The expected structure is:
     * ```json
     * {
     *   "paymentMethodData": {
     *     "tokenizationData": {
     *       "token": "<nested_json_string>"
     *     }
     *   }
     * }
     * ```
     * Where the nested token string contains `protocolVersion`, `signature`,
     * `intermediateSigningKey`, and `signedMessage`.
     *
     * @throws IllegalArgumentException if the JSON is malformed or required fields are missing
     */
    fun parseGooglePayToken(paymentDataJson: String): PaymentCardInput {
        val outerAdapter = moshi.adapter(GooglePayOuterData::class.java)
        val outer = try {
            outerAdapter.fromJson(paymentDataJson)!!
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse Google Pay PaymentData JSON: ${e.message}", e)
        }

        val tokenJson = outer.paymentMethodData?.tokenizationData?.token
            ?: throw IllegalArgumentException("Missing paymentMethodData.tokenizationData.token in Google Pay PaymentData")

        val tokenAdapter = moshi.adapter(GooglePayTokenContent::class.java)
        val token = tokenAdapter.fromJson(tokenJson)
            ?: throw IllegalArgumentException("Failed to parse Google Pay token JSON")

        val protocolVersion = token.protocolVersion
            ?: throw IllegalArgumentException("Missing protocolVersion in Google Pay token")
        val signature = token.signature
            ?: throw IllegalArgumentException("Missing signature in Google Pay token")
        val signedMessage = token.signedMessage
            ?: throw IllegalArgumentException("Missing signedMessage in Google Pay token")

        return PaymentCardInput.googlePay(
            protocolVersion = protocolVersion,
            signature = signature,
            intermediateSigningKey = token.intermediateSigningKey, // null for ECv1 (PAN_ONLY)
            signedMessage = signedMessage
        )
    }

    /**
     * Builds a JSON string suitable for `IsReadyToPayRequest.fromJson()` derived from the
     * allowed payment methods in the GoPay API response. Gateway-specific parameters are
     * intentionally omitted — `IsReadyToPayRequest` only needs the card type and auth methods.
     */
    internal fun buildIsReadyToPayRequestJson(allowedMethods: List<GooglePayAllowedMethod>): String {
        val request = IsReadyToPayRequest(
            apiVersion = 2,
            apiVersionMinor = 0,
            allowedPaymentMethods = allowedMethods.map {
                IsReadyToPayMethod(
                    type = it.type,
                    parameters = IsReadyToPayParameters(
                        allowedAuthMethods = it.parameters.allowedAuthMethods,
                        allowedCardNetworks = it.parameters.allowedCardNetworks
                    )
                )
            }
        )
        return moshi.adapter(IsReadyToPayRequest::class.java).toJson(request)
    }

    // --- Private models ---

    private data class IsReadyToPayRequest(
        val apiVersion: Int,
        val apiVersionMinor: Int,
        val allowedPaymentMethods: List<IsReadyToPayMethod>
    )

    private data class IsReadyToPayMethod(
        val type: String,
        val parameters: IsReadyToPayParameters
    )

    private data class IsReadyToPayParameters(
        val allowedAuthMethods: List<String>,
        val allowedCardNetworks: List<String>
    )

    private data class GooglePayOuterData(
        val paymentMethodData: GooglePayPaymentMethodData?
    )

    private data class GooglePayPaymentMethodData(
        val tokenizationData: GooglePayTokenizationData?
    )

    private data class GooglePayTokenizationData(
        val token: String?
    )

    private data class GooglePayTokenContent(
        val protocolVersion: String? = null,
        val signature: String? = null,
        @Json(name = "intermediateSigningKey") val intermediateSigningKey: IntermediateSigningKey? = null,
        val signedMessage: String? = null
    )
}
