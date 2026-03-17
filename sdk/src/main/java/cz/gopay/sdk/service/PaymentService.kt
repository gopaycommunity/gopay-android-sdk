package cz.gopay.sdk.service

import cz.gopay.sdk.model.PaymentCreateRequest
import cz.gopay.sdk.model.PaymentCreateResponse
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
}

