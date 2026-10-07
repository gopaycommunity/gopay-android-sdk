package cz.gopay.sdk.model

import com.squareup.moshi.Json

/**
 * Payment currency supported by GoPay API.
 * Matches the Currency schema from Payments.yaml.
 */
enum class Currency {
    @Json(name = "CZK")
    CZK,
    @Json(name = "EUR")
    EUR,
    @Json(name = "PLN")
    PLN,
    @Json(name = "USD")
    USD,
    @Json(name = "GBP")
    GBP,
    @Json(name = "HUF")
    HUF,
    @Json(name = "RON")
    RON,
    /**
     * A value this version of the SDK does not know, which the gateway added later. The rest
     * of the response is read as usual; the raw value is in the debug log.
     */
    UNKNOWN
}

/**
 * Customer information for a payment.
 * Based on components.schemas.Customer from Payments.yaml.
 */
data class PaymentCustomer(
    val email: String,
    @Json(name = "first_name")
    val firstName: String? = null,
    @Json(name = "last_name")
    val lastName: String? = null,
    @Json(name = "phone_number")
    val phoneNumber: String? = null,
    val city: String? = null,
    val street: String? = null,
    @Json(name = "postal_code")
    val postalCode: String? = null,
    @Json(name = "country_code")
    val countryCode: String? = null,
    @Json(name = "customer_id")
    val customerId: String? = null
)

/**
 * Additional parameter for a payment.
 * Based on components.schemas.Additional-Param from Payments.yaml.
 */
data class AdditionalParam(
    val name: String? = null,
    val value: String? = null
)

/**
 * Callback URLs for a payment.
 * Based on callback object inside Payment-Create-Request in Payments.yaml.
 */
data class PaymentCallback(
    @Json(name = "notification_url")
    val notificationUrl: String,
    @Json(name = "return_url")
    val returnUrl: String
)

/**
 * Request body for creating a payment via /eshops/{goid}/payments.
 * Based directly on components.requestBodies.Payment-Create-Request.
 */
data class PaymentCreateRequest(
    val amount: Long,
    val currency: Currency,
    @Json(name = "order_number")
    val orderNumber: String,
    @Json(name = "order_description")
    val orderDescription: String? = null,
    val customer: PaymentCustomer,
    @Json(name = "additional_params")
    val additionalParams: List<AdditionalParam>? = null,
    val callback: PaymentCallback
)

/**
 * Payment state as returned by Payment-Create-Response.
 */
enum class PaymentState {
    @Json(name = "CREATED")
    CREATED,
    @Json(name = "PAID")
    PAID,
    @Json(name = "CANCELED")
    CANCELED,
    @Json(name = "PAYMENT_METHOD_CHOSEN")
    PAYMENT_METHOD_CHOSEN,
    @Json(name = "TIMEOUTED")
    TIMEOUTED,
    @Json(name = "AUTHORIZED")
    AUTHORIZED,
    @Json(name = "REFUNDED")
    REFUNDED,
    @Json(name = "PARTIALLY_REFUNDED")
    PARTIALLY_REFUNDED,
    /**
     * A value this version of the SDK does not know, which the gateway added later. The rest
     * of the response is read as usual; the raw value is in the debug log. It is not a result:
     * keep reading [cz.gopay.sdk.session.PaymentSession.getStatus], or settle the payment on
     * your backend.
     */
    UNKNOWN
}

/**
 * Response body for payment creation and payment status.
 * Both POST /eshops/{goid}/payments and GET /payments/{payment_id} return
 * the Payment-Details schema. `charge` is populated once the payment has been charged and
 * mirrors `Payment-Charge-Status-Response`.
 */
data class PaymentCreateResponse(
    val id: String,
    @Json(name = "order_number")
    val orderNumber: String,
    val state: PaymentState,
    val amount: Long,
    val currency: Currency,
    val customer: PaymentCustomer,
    @Json(name = "gw_url")
    val gwUrl: String,
    val charge: ChargePaymentResponse? = null,
    @Json(name = "payment_secret")
    val paymentSecret: String? = null
)

