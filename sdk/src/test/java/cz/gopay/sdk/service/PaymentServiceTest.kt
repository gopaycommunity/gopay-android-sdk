package cz.gopay.sdk.service

import cz.gopay.sdk.model.Currency
import cz.gopay.sdk.model.PaymentCallback
import cz.gopay.sdk.model.PaymentCreateRequest
import cz.gopay.sdk.model.PaymentCreateResponse
import cz.gopay.sdk.model.PaymentCustomer
import cz.gopay.sdk.model.PaymentState
import cz.gopay.sdk.modules.network.GopayApiService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

@ExperimentalCoroutinesApi
class PaymentServiceTest {

    private val apiService: GopayApiService = mock()
    private val paymentService = PaymentService(apiService)

    @Test
    fun createPayment_success_returnsResponse() = runTest {
        val request = PaymentCreateRequest(
            amount = 10000,
            currency = Currency.CZK,
            orderNumber = "2025010199",
            orderDescription = "Test order",
            customer = PaymentCustomer(
                email = "john.doe@example.com"
            ),
            additionalParams = null,
            callback = PaymentCallback(
                notificationUrl = "https://example.com/notify",
                returnUrl = "https://example.com/return"
            )
        )

        val expectedResponse = PaymentCreateResponse(
            id = "300000001",
            orderNumber = "2025010199",
            state = PaymentState.CREATED,
            amount = 10000,
            currency = Currency.CZK,
            customer = PaymentCustomer(
                email = "john.doe@example.com"
            ),
            gwUrl = "https://gw.sandbox.gopay.com/gw/pay/300000001"
        )

        whenever(apiService.createPayment("123456", request))
            .thenReturn(Response.success(expectedResponse))

        val result = paymentService.createPayment("123456", request)

        assertEquals(expectedResponse, result)
    }

    @Test
    fun createPayment_httpError_throwsException() = runTest {
        val request = PaymentCreateRequest(
            amount = 10000,
            currency = Currency.CZK,
            orderNumber = "2025010199",
            orderDescription = "Test order",
            customer = PaymentCustomer(
                email = "john.doe@example.com"
            ),
            additionalParams = null,
            callback = PaymentCallback(
                notificationUrl = "https://example.com/notify",
                returnUrl = "https://example.com/return"
            )
        )

        val errorBody = ResponseBody.create(
            "application/json".toMediaTypeOrNull(),
            """{"code":400,"message":"Bad Request"}"""
        )

        whenever(apiService.createPayment("123456", request))
            .thenReturn(Response.error(400, errorBody))

        assertThrows(Exception::class.java) {
            runTest {
                paymentService.createPayment("123456", request)
            }
        }
    }

    @Test
    fun createPayment_nullBody_throwsException() = runTest {
        val request = PaymentCreateRequest(
            amount = 10000,
            currency = Currency.CZK,
            orderNumber = "2025010199",
            orderDescription = "Test order",
            customer = PaymentCustomer(
                email = "john.doe@example.com"
            ),
            additionalParams = null,
            callback = PaymentCallback(
                notificationUrl = "https://example.com/notify",
                returnUrl = "https://example.com/return"
            )
        )

        whenever(apiService.createPayment("123456", request))
            .thenReturn(Response.success(null))

        assertThrows(Exception::class.java) {
            runTest {
                paymentService.createPayment("123456", request)
            }
        }
    }
}

