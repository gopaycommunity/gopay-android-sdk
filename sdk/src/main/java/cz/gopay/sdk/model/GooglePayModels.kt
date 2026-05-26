package cz.gopay.sdk.model

import com.squareup.moshi.Json

data class GooglePayInfoResponse(
    val environment: String,
    @Json(name = "paymentDataRequest") val paymentDataRequest: GooglePayDataRequest
)

data class GooglePayDataRequest(
    val apiVersion: Int,
    val apiVersionMinor: Int,
    val allowedPaymentMethods: List<GooglePayAllowedMethod>,
    val transactionInfo: GooglePayTransactionInfo,
    val merchantInfo: GooglePayMerchantInfo,
    val emailRequired: Boolean? = null
)

data class GooglePayAllowedMethod(
    val type: String,
    val parameters: GooglePayMethodParameters,
    val tokenizationSpecification: GooglePayTokenizationSpec
)

data class GooglePayMethodParameters(
    val allowedAuthMethods: List<String>,
    val allowedCardNetworks: List<String>
)

data class GooglePayTokenizationSpec(
    val type: String,
    val parameters: GooglePayGatewayParameters
)

data class GooglePayGatewayParameters(
    val gateway: String,
    val gatewayMerchantId: String
)

data class GooglePayTransactionInfo(
    val currencyCode: String,
    val countryCode: String,
    val totalPriceStatus: String,
    val totalPrice: String
)

data class GooglePayMerchantInfo(
    val merchantName: String,
    val merchantId: String? = null
)

data class IntermediateSigningKey(
    val signedKey: String,
    val signatures: List<String>
)
