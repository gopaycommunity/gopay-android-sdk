package cz.gopay.sdk.service

import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.PaymentCreateRequest
import cz.gopay.sdk.model.PaymentCreateResponse
import cz.gopay.sdk.model.QrCodeFormat
import cz.gopay.sdk.model.QrPaymentDetails
import cz.gopay.sdk.modules.network.GopayApiService

/**
 * High-level service for creating payments using GoPay API.
 * Wraps the Retrofit API and provides simple error handling.
 */
class PaymentService(
    private val apiService: GopayApiService
) {

    /**
     * Creates a payment for the specified e-shop (goid).
     *
     * @param goid E-shop identifier
     * @param request Payment creation request
     * @return PaymentCreateResponse with payment details and gateway URL
     * @throws Exception for HTTP errors or empty response body
     */
    suspend fun createPayment(
        goid: String,
        request: PaymentCreateRequest
    ): PaymentCreateResponse {
        val response = apiService.createPayment(goid, request)

        if (!response.isSuccessful) {
            throw Exception("Payment creation failed: ${response.code()} ${response.message()}")
        }

        return response.body()
            ?: throw Exception("Empty response body from payment creation")
    }

    /**
     * Retrieves the current status of a payment.
     *
     * @param paymentId Payment identifier
     * @return PaymentCreateResponse with payment details including optional charge reference
     * @throws Exception for HTTP errors or empty response body
     */
    suspend fun getPaymentStatus(paymentId: String): PaymentCreateResponse {
        val response = apiService.getPaymentStatus(paymentId)

        if (!response.isSuccessful) {
            throw Exception("Get payment status failed: ${response.code()} ${response.message()}")
        }

        return response.body()
            ?: throw Exception("Empty response body from get payment status")
    }

    /**
     * Charges a payment using the specified instrument.
     *
     * @param paymentId Payment identifier
     * @param request Charge request with instrument and return URL
     * @return ChargePaymentResponse with charge details and optional action
     * @throws Exception for HTTP errors or empty response body
     */
    suspend fun chargePayment(
        paymentId: String,
        request: ChargePaymentRequest
    ): ChargePaymentResponse {
        val response = apiService.chargePayment(paymentId, request)

        if (!response.isSuccessful) {
            throw Exception("Charge payment failed: ${response.code()} ${response.message()}")
        }

        return response.body()
            ?: throw Exception("Empty response body from charge payment")
    }

    /**
     * Gets the current state of a payment charge.
     *
     * @param paymentId Payment identifier
     * @return ChargePaymentResponse with current charge state
     * @throws Exception for HTTP errors or empty response body
     */
    suspend fun getChargeState(paymentId: String): ChargePaymentResponse {
        val response = apiService.getChargeState(paymentId)

        if (!response.isSuccessful) {
            throw Exception("Get charge state failed: ${response.code()} ${response.message()}")
        }

        return response.body()
            ?: throw Exception("Empty response body from get charge state")
    }

    /**
     * Retrieves QR code payment info for a bank transfer payment.
     *
     * @param paymentId Payment identifier
     * @param format QR code image format (PNG or SVG, defaults to PNG)
     * @return QrPaymentDetails with recipient info and base64-encoded QR code images
     * @throws Exception for HTTP errors or empty response body
     */
    suspend fun getQrPaymentInfo(paymentId: String, format: QrCodeFormat? = null): QrPaymentDetails {
        val response = apiService.getQrPaymentInfo(paymentId, format?.name?.lowercase())

        if (!response.isSuccessful) {
            throw Exception("Get QR payment info failed: ${response.code()} ${response.message()}")
        }

        return response.body()
            ?: throw Exception("Empty response body from get QR payment info")
    }
}

