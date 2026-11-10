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
 * The payment as `GET /payments/{payment_id}` returns it, the `Payment-Details` schema: what
 * [cz.gopay.sdk.session.PaymentSession.getStatus] returns and
 * [PaymentOutcome.payment] carries. `charge` is populated once the payment has been charged and
 * mirrors `Payment-Charge-Status-Response`. `PaymentDetails` on iOS too; 2.x called it
 * `PaymentCreateResponse`.
 */
data class PaymentDetails(
    val id: String,
    @Json(name = "order_number")
    val orderNumber: String,
    val state: PaymentState,
    val amount: Long,
    val currency: Currency,
    val customer: PaymentCustomer,
    /** The address of the payment on the GoPay gateway, `gw_url`; `gatewayUrl` on iOS too. */
    @Json(name = "gw_url")
    val gatewayUrl: String,
    val charge: ChargePaymentResponse? = null,
    @Json(name = "payment_secret")
    val paymentSecret: String? = null
) {
    /** The 2.x name of [gatewayUrl]. */
    @Deprecated("Renamed to gatewayUrl, the name the iOS SDK uses", ReplaceWith("gatewayUrl"))
    val gwUrl: String get() = gatewayUrl
}

/** The 2.x name of [PaymentDetails]. */
@Deprecated("Renamed to PaymentDetails, the name the iOS SDK uses", ReplaceWith("PaymentDetails"))
typealias PaymentCreateResponse = PaymentDetails
