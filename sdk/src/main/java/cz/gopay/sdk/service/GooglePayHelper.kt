package cz.gopay.sdk.service

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import cz.gopay.sdk.model.GooglePayDataRequest
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.IntermediateSigningKey
import cz.gopay.sdk.model.PaymentInstrumentInput

/**
 * Utility for bridging between the GoPay API and the Google Pay Android SDK.
 *
 * Usage:
 * 1. Call [buildPaymentDataRequestJson] with the result of [cz.gopay.sdk.GopaySDK.getGooglePayInfo]
 *    to get a JSON string for `PaymentDataRequest.fromJson()` in the Google Pay SDK.
 * 2. After the user approves, call [parseGooglePayToken] with `PaymentData.toJson()` to get a
 *    [PaymentInstrumentInput] ready for [cz.gopay.sdk.model.ChargePaymentRequest].
 */
object GooglePayHelper {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

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
     * returns a [PaymentInstrumentInput] ready for use in a charge request.
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
    fun parseGooglePayToken(paymentDataJson: String): PaymentInstrumentInput {
        val outerAdapter = moshi.adapter(GooglePayOuterData::class.java)
        val outer = try {
            outerAdapter.fromJson(paymentDataJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse Google Pay PaymentData JSON: ${e.message}", e)
        } ?: throw IllegalArgumentException("Failed to parse Google Pay PaymentData JSON")

        val tokenJson = outer.paymentMethodData?.tokenizationData?.token
            ?: throw IllegalArgumentException("Missing paymentMethodData.tokenizationData.token in Google Pay PaymentData")

        val tokenAdapter = moshi.adapter(GooglePayTokenContent::class.java)
        val token = tokenAdapter.fromJson(tokenJson)
            ?: throw IllegalArgumentException("Failed to parse Google Pay token JSON")

        val protocolVersion = token.protocolVersion
            ?: throw IllegalArgumentException("Missing protocolVersion in Google Pay token")
        val signature = token.signature
            ?: throw IllegalArgumentException("Missing signature in Google Pay token")
        val intermediateSigningKey = token.intermediateSigningKey
            ?: throw IllegalArgumentException("Missing intermediateSigningKey in Google Pay token")
        val signedMessage = token.signedMessage
            ?: throw IllegalArgumentException("Missing signedMessage in Google Pay token")

        return PaymentInstrumentInput.googlePay(
            protocolVersion = protocolVersion,
            signature = signature,
            intermediateSigningKey = intermediateSigningKey,
            signedMessage = signedMessage
        )
    }

    // Private models for parsing the PaymentData JSON from the Google Pay SDK

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
