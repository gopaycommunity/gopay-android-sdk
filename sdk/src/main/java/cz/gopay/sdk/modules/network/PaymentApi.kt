package cz.gopay.sdk.modules.network

import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.InitSessionRequest
import cz.gopay.sdk.model.PaymentCreateResponse
import cz.gopay.sdk.model.QrPaymentDetails
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

/**
 * Retrofit interface for payment-scoped routes — every endpoint here is callable with a
 * `payment_credentials` JWT issued for the matching `payment_id`.
 *
 * Authorization is attached by [SessionAuthInterceptor] bound to the owning
 * [cz.gopay.sdk.session.PaymentSession].
 *
 * Routes deliberately excluded (merchant_credentials only): `POST /eshops/{goid}/payments`,
 * `POST /cards/tokens`, `/payments/{payment_id}/refunds` (POST), `/eshops/{goid}/recurrences`,
 * `/eshops/{goid}/links`, etc. These remain on the merchant backend.
 */
internal interface PaymentApi {

    @GET("payments/{payment_id}")
    @Headers("Accept: application/json")
    suspend fun getPaymentStatus(
        @Path("payment_id") paymentId: String
    ): Response<PaymentCreateResponse>

    @POST("payments/{payment_id}/charge")
    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    suspend fun chargePayment(
        @Path("payment_id") paymentId: String,
        @Body request: ChargePaymentRequest
    ): Response<ChargePaymentResponse>

    @GET("payments/{payment_id}/charge")
    @Headers("Accept: application/json")
    suspend fun getChargeState(
        @Path("payment_id") paymentId: String
    ): Response<ChargePaymentResponse>

    @GET("payments/{payment_id}/qr-payment/info")
    @Headers("Accept: application/json")
    suspend fun getQrPaymentInfo(
        @Path("payment_id") paymentId: String,
        @Query("format") format: String? = null
    ): Response<QrPaymentDetails>

    @GET("payments/{payment_id}/google-pay/info")
    @Headers("Accept: application/json")
    suspend fun getGooglePayInfo(
        @Path("payment_id") paymentId: String
    ): Response<GooglePayInfoResponse>

    /**
     * `POST /payments/{payment_id}/init-session`: tells GoPay which SDK, in which version, works
     * with the payment, and sets the payment's language from [acceptLanguage]; without one the
     * request goes without the header. Answers `204` with no body. Not `suspend`:
     * [cz.gopay.sdk.session.PaymentSession.startInitSession] enqueues it and nothing waits for it.
     * [noAuthRecovery] keeps [SessionAuthInterceptor] from renewing the session's token for it;
     * [closeSignal] tells a cancel by `PaymentSession.close()` from a timeout.
     */
    @POST("payments/{payment_id}/init-session")
    @Headers("Content-Type: application/json")
    fun initSession(
        @Path("payment_id") paymentId: String,
        @Header("Accept-Language") acceptLanguage: String?,
        @Body request: InitSessionRequest,
        @Tag noAuthRecovery: NoAuthRecovery,
        @Tag closeSignal: SessionCloseSignal
    ): Call<Unit>
}
