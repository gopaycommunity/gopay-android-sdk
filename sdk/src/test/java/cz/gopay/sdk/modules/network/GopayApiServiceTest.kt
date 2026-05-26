package cz.gopay.sdk.modules.network

import cz.gopay.sdk.model.BankAccountLocalDetails
import cz.gopay.sdk.model.BankTransferRecipient
import cz.gopay.sdk.model.CardTokenRequest
import cz.gopay.sdk.model.CardTokenResponse
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.Currency
import cz.gopay.sdk.model.GooglePayDataRequest
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.GooglePayMerchantInfo
import cz.gopay.sdk.model.GooglePayTransactionInfo
import cz.gopay.sdk.model.InstrumentDetails
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.model.PaymentCallback
import cz.gopay.sdk.model.PaymentCreateRequest
import cz.gopay.sdk.model.PaymentCreateResponse
import cz.gopay.sdk.model.PaymentCustomer
import cz.gopay.sdk.model.PaymentInstrumentData
import cz.gopay.sdk.model.PaymentState
import cz.gopay.sdk.model.QrCodeList
import cz.gopay.sdk.model.QrPaymentDetails
import cz.gopay.sdk.model.RecipientBankAccount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.mock.BehaviorDelegate
import retrofit2.mock.MockRetrofit
import retrofit2.mock.NetworkBehavior
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class GopayApiServiceTest {

    private lateinit var mockRetrofit: MockRetrofit
    private lateinit var behaviorDelegate: BehaviorDelegate<GopayApiService>
    private lateinit var mockApiService: MockGopayApiService
    private val baseUrl = "https://api.example.com/"

    @Before
    fun setup() {
        // Create a mock Retrofit instance
        val retrofit = NetworkModule.createRetrofit(
            OkHttpClient.Builder().build(),
            baseUrl
        )
        
        // Create a NetworkBehavior to use in the mock
        val behavior = NetworkBehavior.create().apply {
            setDelay(0, TimeUnit.MILLISECONDS)
            setVariancePercent(0)
            setFailurePercent(0)
        }
        
        // Create a MockRetrofit object with the NetworkBehavior
        mockRetrofit = MockRetrofit.Builder(retrofit)
            .networkBehavior(behavior)
            .build()
        
        // Get a behavior delegate that will be used for mocking the API service
        behaviorDelegate = mockRetrofit.create(GopayApiService::class.java)
        
        // Create a mock implementation of the API service
        mockApiService = MockGopayApiService(behaviorDelegate)
    }
    
    @Test
    fun testAuthenticate() = runTest {
        // Given a mock response for authenticate
        val mockResponse = AuthResponse(
            access_token = "test_access_token",
            token_type = "Bearer",
            refresh_token = "test_refresh_token",
            scope = "payment:read payment:write"
        )
        mockApiService.setAuthenticateResponse(mockResponse)
        
        // When calling authenticate
        val authToken = "Bearer test_token"
        val result = mockApiService.authenticate(
            authorization = authToken,
            grantType = "client_credentials",
            scope = "payment:read payment:write"
        )
        
        // Then the result should match the expected response
        assertEquals(mockResponse, result)
    }

    @Test
    fun testGetPublicKey() = runTest {
        // Given a mock response for getPublicKey
        val mockResponse = Jwk(
            kty = "RSA",
            kid = "test-key-id",
            use = "enc", 
            alg = "RSA-OAEP-256",
            n = "test-modulus-value",
            e = "AQAB"
        )
        mockApiService.setPublicKeyResponse(mockResponse)
        
        // When calling getPublicKey
        val result = mockApiService.getPublicKey()
        
        // Then the result should be successful and contain the expected response
        assertEquals(true, result.isSuccessful)
        assertEquals(mockResponse, result.body())
    }

    @Test
    fun testCreateCardToken() = runTest {
        // Given a mock response for createCardToken
        val mockResponse = CardTokenResponse(
            maskedPan = "4444************",
            expirationMonth = "01",
            expirationYear = "27",
            brand = "visa",
            cardArtUrl = "https://example.com/card-art.png",
            token = "test-card-token-12345",
            fingerprint = "test-fingerprint",
            maskedVirtualPan = "4444************",
            expiresIn = "123123123"
        )
        mockApiService.setCardTokenResponse(mockResponse)
        
        // Given a card token request
        val request = CardTokenRequest(
            payload = "eyJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIiwia2lkIjoidGVzdC1rZXktaWQifQ.test-encrypted-key.test-iv.test-ciphertext.test-tag",
            permanent = false
        )
        
        // When calling createCardToken
        val result = mockApiService.createCardToken(request)
        
        // Then the result should be successful and contain the expected response
        assertEquals(true, result.isSuccessful)
        assertEquals(mockResponse, result.body())
    }

    @Test
    fun testCreatePayment() = runTest {
        // Given a mock response for createPayment
        val mockResponse = PaymentCreateResponse(
            id = "300000001",
            orderNumber = "2025010199",
            state = PaymentState.CREATED,
            amount = 10000,
            currency = Currency.CZK,
            customer = PaymentCustomer(
                email = "john.doe@example.com",
                firstName = "John",
                lastName = "Doe"
            ),
            gwUrl = "https://gw.sandbox.gopay.com/gw/pay/300000001"
        )
        mockApiService.setPaymentCreateResponse(mockResponse)

        // Given a payment create request
        val request = PaymentCreateRequest(
            amount = 10000,
            currency = Currency.CZK,
            orderNumber = "2025010199",
            orderDescription = "Test order",
            customer = PaymentCustomer(
                email = "john.doe@example.com",
                firstName = "John",
                lastName = "Doe"
            ),
            additionalParams = null,
            callback = PaymentCallback(
                notificationUrl = "https://example.com/notify",
                returnUrl = "https://example.com/return"
            )
        )

        // When calling createPayment
        val result = mockApiService.createPayment("123456", request)

        // Then the result should be successful and contain the expected response
        assertEquals(true, result.isSuccessful)
        assertEquals(mockResponse, result.body())
    }

    // Mock implementation of GopayApiService for testing
    private class MockGopayApiService(
        private val delegate: BehaviorDelegate<GopayApiService>
    ) : GopayApiService {
        private var authenticateResponse: AuthResponse = AuthResponse(
            access_token = "default_token",
            token_type = "Bearer",
            refresh_token = "default_refresh_token"
        )
        
        private var publicKeyResponse: Jwk = Jwk(
        kty = "RSA",
        kid = "test-key-id",
        use = "enc",
        alg = "RSA-OAEP-256",
        n = "test-modulus-value",
        e = "AQAB"
    )

        private var cardTokenResponse = CardTokenResponse(
            maskedPan = "3242************",
            expirationMonth = "12",
            expirationYear = "2025",
            brand = "visa",
            cardArtUrl = "https://example.com",
            token = "acsdsadcdafhgdhsjgfjh",
            fingerprint = "AQFD",
            maskedVirtualPan = "3242************",
            expiresIn = "123123123"
        )

        private var paymentCreateResponse: PaymentCreateResponse = PaymentCreateResponse(
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

        fun setAuthenticateResponse(response: AuthResponse) {
            authenticateResponse = response
        }
        
        fun setPublicKeyResponse(response: Jwk) {
            publicKeyResponse = response
        }
        
        fun setCardTokenResponse(response: CardTokenResponse) {
            cardTokenResponse = response
        }

        fun setPaymentCreateResponse(response: PaymentCreateResponse) {
            paymentCreateResponse = response
        }

        private var googlePayInfoResponse: GooglePayInfoResponse = GooglePayInfoResponse(
            environment = "TEST",
            paymentDataRequest = GooglePayDataRequest(
                apiVersion = 2,
                apiVersionMinor = 0,
                allowedPaymentMethods = emptyList(),
                transactionInfo = GooglePayTransactionInfo(
                    currencyCode = "CZK",
                    countryCode = "CZ",
                    totalPriceStatus = "FINAL",
                    totalPrice = "100.00"
                ),
                merchantInfo = GooglePayMerchantInfo(
                    merchantName = "Test Merchant",
                    merchantId = "123456"
                )
            )
        )

        fun setGooglePayInfoResponse(response: GooglePayInfoResponse) {
            googlePayInfoResponse = response
        }

        override suspend fun authenticate(
            authorization: String?,
            grantType: String,
            scope: String?,
            refreshToken: String?,
            clientId: String?
        ): AuthResponse {
            return delegate.returningResponse(authenticateResponse)
                .authenticate(authorization, grantType, scope, refreshToken, clientId)
        }
        
        override suspend fun getPublicKey(): Response<Jwk> {
            return delegate.returningResponse(publicKeyResponse)
                .getPublicKey()
        }

        override suspend fun createCardToken(request: CardTokenRequest): Response<CardTokenResponse> {
            return delegate.returningResponse(cardTokenResponse).createCardToken(request)
        }

        override suspend fun createPayment(
            goid: String,
            request: PaymentCreateRequest
        ): Response<PaymentCreateResponse> {
            return delegate.returningResponse(paymentCreateResponse).createPayment(goid, request)
        }

        override suspend fun getPaymentStatus(paymentId: String): Response<PaymentCreateResponse> {
            return delegate.returningResponse(paymentCreateResponse).getPaymentStatus(paymentId)
        }

        override suspend fun chargePayment(
            paymentId: String,
            request: ChargePaymentRequest
        ): Response<ChargePaymentResponse> {
            val defaultChargeResponse = ChargePaymentResponse(
                id = "9123456789",
                state = ChargeState.REQUESTED,
                paymentInstrument = PaymentInstrumentData(
                    paymentInstrument = "PAYMENT_CARD",
                    details = InstrumentDetails(inputType = "CARD_TOKEN")
                ),
                returnUrl = "https://example.com/return"
            )
            return delegate.returningResponse(defaultChargeResponse).chargePayment(paymentId, request)
        }

        override suspend fun getChargeState(paymentId: String): Response<ChargePaymentResponse> {
            val defaultChargeResponse = ChargePaymentResponse(
                id = "9123456789",
                state = ChargeState.REQUESTED,
                paymentInstrument = PaymentInstrumentData(
                    paymentInstrument = "PAYMENT_CARD",
                    details = InstrumentDetails(inputType = "CARD_TOKEN")
                ),
                returnUrl = "https://example.com/return"
            )
            return delegate.returningResponse(defaultChargeResponse).getChargeState(paymentId)
        }

        override suspend fun getQrPaymentInfo(
            paymentId: String,
            format: String?
        ): Response<QrPaymentDetails> {
            val defaultQrDetails = QrPaymentDetails(
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
                qrCode = QrCodeList(spayd = "base64encodedSpaydImage==")
            )
            return delegate.returningResponse(defaultQrDetails).getQrPaymentInfo(paymentId, format)
        }

        override suspend fun getGooglePayInfo(paymentId: String): Response<GooglePayInfoResponse> {
            return delegate.returningResponse(googlePayInfoResponse).getGooglePayInfo(paymentId)
        }
    }

    @Test
    fun testGetGooglePayInfo() = runTest {
        // Given a mock response for getGooglePayInfo
        val mockResponse = GooglePayInfoResponse(
            environment = "PRODUCTION",
            paymentDataRequest = GooglePayDataRequest(
                apiVersion = 2,
                apiVersionMinor = 0,
                allowedPaymentMethods = emptyList(),
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
        mockApiService.setGooglePayInfoResponse(mockResponse)

        // When calling getGooglePayInfo
        val result = mockApiService.getGooglePayInfo("300000001")

        // Then the result should be successful and contain the expected response
        assertEquals(true, result.isSuccessful)
        assertEquals(mockResponse, result.body())
        assertEquals("PRODUCTION", result.body()?.environment)
        assertEquals("GoPay Czech", result.body()?.paymentDataRequest?.merchantInfo?.merchantName)
    }
}