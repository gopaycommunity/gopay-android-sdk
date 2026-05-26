package cz.gopay.sdk.service

import cz.gopay.sdk.model.BankAccountLocalDetails
import cz.gopay.sdk.model.BankTransferRecipient
import cz.gopay.sdk.model.ChargeAction
import cz.gopay.sdk.model.GooglePayAllowedMethod
import cz.gopay.sdk.model.GooglePayDataRequest
import cz.gopay.sdk.model.GooglePayGatewayParameters
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.GooglePayMerchantInfo
import cz.gopay.sdk.model.GooglePayMethodParameters
import cz.gopay.sdk.model.GooglePayTokenizationSpec
import cz.gopay.sdk.model.GooglePayTransactionInfo
import cz.gopay.sdk.model.ChargeActionType
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.model.ChallengePreference
import cz.gopay.sdk.model.Currency
import cz.gopay.sdk.model.Emv3dsState
import cz.gopay.sdk.model.InstrumentDetails
import cz.gopay.sdk.model.PaymentCallback
import cz.gopay.sdk.model.PaymentChargeRef
import cz.gopay.sdk.model.PaymentCreateRequest
import cz.gopay.sdk.model.PaymentCreateResponse
import cz.gopay.sdk.model.PaymentCustomer
import cz.gopay.sdk.model.PaymentInstrumentData
import cz.gopay.sdk.model.PaymentInstrumentInput
import cz.gopay.sdk.model.PaymentState
import cz.gopay.sdk.model.QrCodeFormat
import cz.gopay.sdk.model.QrCodeList
import cz.gopay.sdk.model.QrPaymentDetails
import cz.gopay.sdk.model.RecipientBankAccount
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

    // --- getPaymentStatus ---

    @Test
    fun getPaymentStatus_success_returnsResponse() = runTest {
        val expectedResponse = PaymentCreateResponse(
            id = "300000001",
            orderNumber = "2025010199",
            state = PaymentState.CREATED,
            amount = 10000,
            currency = Currency.CZK,
            customer = PaymentCustomer(email = "john.doe@example.com"),
            gwUrl = "https://gw.sandbox.gopay.com/gw/pay/300000001",
            charge = PaymentChargeRef(
                id = "9123456789",
                state = ChargeState.REQUESTED,
                href = "https://api.gopay.com/api/4.0/payments/9123456789/charge"
            )
        )

        whenever(apiService.getPaymentStatus("300000001"))
            .thenReturn(Response.success(expectedResponse))

        val result = paymentService.getPaymentStatus("300000001")

        assertEquals(expectedResponse, result)
    }

    @Test
    fun getPaymentStatus_httpError_throwsException() = runTest {
        val errorBody = ResponseBody.create(
            "application/json".toMediaTypeOrNull(),
            """{"code":404,"message":"Not Found"}"""
        )

        whenever(apiService.getPaymentStatus("999"))
            .thenReturn(Response.error(404, errorBody))

        assertThrows(Exception::class.java) {
            runTest { paymentService.getPaymentStatus("999") }
        }
    }

    @Test
    fun getPaymentStatus_nullBody_throwsException() = runTest {
        whenever(apiService.getPaymentStatus("300000001"))
            .thenReturn(Response.success(null))

        assertThrows(Exception::class.java) {
            runTest { paymentService.getPaymentStatus("300000001") }
        }
    }

    // --- chargePayment ---

    @Test
    fun chargePayment_success_returnsResponse() = runTest {
        val request = ChargePaymentRequest(
            paymentInstrument = PaymentInstrumentInput.cardToken(
                cardToken = "J7HjFNwzyBOHS+jwIMMktubTwoIRy6qB",
                challengePreference = ChallengePreference.AUTO
            ),
            returnUrl = "https://example.com/return"
        )

        val expectedResponse = ChargePaymentResponse(
            id = "9123456789",
            state = ChargeState.REQUESTED,
            paymentInstrument = PaymentInstrumentData(
                paymentInstrument = "PAYMENT_CARD",
                details = InstrumentDetails(
                    inputType = "CARD_TOKEN",
                    maskedPan = "406821******1234",
                    expirationMonth = "01",
                    expirationYear = "30"
                )
            ),
            returnUrl = "https://example.com/return",
            action = ChargeAction(
                actionType = ChargeActionType.EMV3DS,
                state = Emv3dsState.CREATED,
                redirectUrl = "https://gate.gopay.com/redirect"
            )
        )

        whenever(apiService.chargePayment("300000001", request))
            .thenReturn(Response.success(201, expectedResponse))

        val result = paymentService.chargePayment("300000001", request)

        assertEquals(expectedResponse, result)
    }

    @Test
    fun chargePayment_httpError_throwsException() = runTest {
        val request = ChargePaymentRequest(
            paymentInstrument = PaymentInstrumentInput.cardToken("token"),
            returnUrl = "https://example.com/return"
        )
        val errorBody = ResponseBody.create(
            "application/json".toMediaTypeOrNull(),
            """{"code":400,"message":"Bad Request"}"""
        )

        whenever(apiService.chargePayment("300000001", request))
            .thenReturn(Response.error(400, errorBody))

        assertThrows(Exception::class.java) {
            runTest { paymentService.chargePayment("300000001", request) }
        }
    }

    @Test
    fun chargePayment_nullBody_throwsException() = runTest {
        val request = ChargePaymentRequest(
            paymentInstrument = PaymentInstrumentInput.cardToken("token"),
            returnUrl = "https://example.com/return"
        )

        whenever(apiService.chargePayment("300000001", request))
            .thenReturn(Response.success(null))

        assertThrows(Exception::class.java) {
            runTest { paymentService.chargePayment("300000001", request) }
        }
    }

    // --- getChargeState ---

    @Test
    fun getChargeState_success_returnsResponse() = runTest {
        val expectedResponse = ChargePaymentResponse(
            id = "9123456789",
            state = ChargeState.PROCESSING,
            paymentInstrument = PaymentInstrumentData(
                paymentInstrument = "PAYMENT_CARD",
                details = InstrumentDetails(
                    inputType = "CARD_TOKEN",
                    maskedPan = "406821******1234",
                    expirationMonth = "01",
                    expirationYear = "30"
                )
            ),
            returnUrl = "https://example.com/return"
        )

        whenever(apiService.getChargeState("300000001"))
            .thenReturn(Response.success(expectedResponse))

        val result = paymentService.getChargeState("300000001")

        assertEquals(expectedResponse, result)
    }

    @Test
    fun getChargeState_httpError_throwsException() = runTest {
        val errorBody = ResponseBody.create(
            "application/json".toMediaTypeOrNull(),
            """{"code":404,"message":"Not Found"}"""
        )

        whenever(apiService.getChargeState("999"))
            .thenReturn(Response.error(404, errorBody))

        assertThrows(Exception::class.java) {
            runTest { paymentService.getChargeState("999") }
        }
    }

    @Test
    fun getChargeState_nullBody_throwsException() = runTest {
        whenever(apiService.getChargeState("300000001"))
            .thenReturn(Response.success(null))

        assertThrows(Exception::class.java) {
            runTest { paymentService.getChargeState("300000001") }
        }
    }

    // --- getQrPaymentInfo ---

    private fun buildQrPaymentDetails() = QrPaymentDetails(
        amount = 10000,
        currency = Currency.CZK,
        recipient = BankTransferRecipient(
            name = "GoPay Czech",
            bankAccount = RecipientBankAccount(
                local = BankAccountLocalDetails(
                    prefix = "000000",
                    accountNumber = "9878039",
                    bankCode = "2010",
                    variableSymbol = "3123456789"
                )
            )
        ),
        qrCode = QrCodeList(
            spayd = "base64encodedSpaydImage=="
        )
    )

    @Test
    fun getQrPaymentInfo_success_returnsResponse() = runTest {
        val expected = buildQrPaymentDetails()

        whenever(apiService.getQrPaymentInfo("300000001", null))
            .thenReturn(Response.success(expected))

        val result = paymentService.getQrPaymentInfo("300000001")

        assertEquals(expected, result)
    }

    @Test
    fun getQrPaymentInfo_withFormatParam_passesFormatString() = runTest {
        val expected = buildQrPaymentDetails()

        whenever(apiService.getQrPaymentInfo("300000001", "svg"))
            .thenReturn(Response.success(expected))

        val result = paymentService.getQrPaymentInfo("300000001", QrCodeFormat.SVG)

        assertEquals(expected, result)
    }

    @Test
    fun getQrPaymentInfo_httpError_throwsException() = runTest {
        val errorBody = ResponseBody.create(
            "application/json".toMediaTypeOrNull(),
            """{"code":404,"message":"Not Found"}"""
        )

        whenever(apiService.getQrPaymentInfo("999", null))
            .thenReturn(Response.error(404, errorBody))

        assertThrows(Exception::class.java) {
            runTest { paymentService.getQrPaymentInfo("999") }
        }
    }

    @Test
    fun getQrPaymentInfo_nullBody_throwsException() = runTest {
        whenever(apiService.getQrPaymentInfo("300000001", null))
            .thenReturn(Response.success(null))

        assertThrows(Exception::class.java) {
            runTest { paymentService.getQrPaymentInfo("300000001") }
        }
    }

    // --- getGooglePayInfo ---

    private fun buildGooglePayInfoResponse() = GooglePayInfoResponse(
        environment = "TEST",
        paymentDataRequest = GooglePayDataRequest(
            apiVersion = 2,
            apiVersionMinor = 0,
            allowedPaymentMethods = listOf(
                GooglePayAllowedMethod(
                    type = "CARD",
                    parameters = GooglePayMethodParameters(
                        allowedAuthMethods = listOf("PAN_ONLY", "CRYPTOGRAM_3DS"),
                        allowedCardNetworks = listOf("VISA", "MASTERCARD")
                    ),
                    tokenizationSpecification = GooglePayTokenizationSpec(
                        type = "PAYMENT_GATEWAY",
                        parameters = GooglePayGatewayParameters(
                            gateway = "gopay",
                            gatewayMerchantId = "26046768005768011132"
                        )
                    )
                )
            ),
            transactionInfo = GooglePayTransactionInfo(
                currencyCode = "CZK",
                countryCode = "CZ",
                totalPriceStatus = "FINAL",
                totalPrice = "5.00"
            ),
            merchantInfo = GooglePayMerchantInfo(
                merchantName = "GoPay Czech",
                merchantId = "14846034534970557458"
            ),
            emailRequired = true
        )
    )

    @Test
    fun getGooglePayInfo_success_returnsResponse() = runTest {
        val expected = buildGooglePayInfoResponse()

        whenever(apiService.getGooglePayInfo("300000001"))
            .thenReturn(Response.success(expected))

        val result = paymentService.getGooglePayInfo("300000001")

        assertEquals(expected, result)
        assertEquals("TEST", result.environment)
        assertEquals("GoPay Czech", result.paymentDataRequest.merchantInfo.merchantName)
    }

    @Test
    fun getGooglePayInfo_httpError_throwsException() = runTest {
        val errorBody = ResponseBody.create(
            "application/json".toMediaTypeOrNull(),
            """{"code":404,"message":"Not Found"}"""
        )

        whenever(apiService.getGooglePayInfo("999"))
            .thenReturn(Response.error(404, errorBody))

        assertThrows(Exception::class.java) {
            runTest { paymentService.getGooglePayInfo("999") }
        }
    }

    @Test
    fun getGooglePayInfo_nullBody_throwsException() = runTest {
        whenever(apiService.getGooglePayInfo("300000001"))
            .thenReturn(Response.success(null))

        assertThrows(Exception::class.java) {
            runTest { paymentService.getGooglePayInfo("300000001") }
        }
    }
}
