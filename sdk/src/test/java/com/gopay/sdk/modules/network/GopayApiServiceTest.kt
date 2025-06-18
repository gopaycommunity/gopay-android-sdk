package com.gopay.sdk.modules.network

import com.gopay.sdk.model.CardTokenRequest
import com.gopay.sdk.model.CardTokenResponse
import com.gopay.sdk.model.Jwk
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

        fun setAuthenticateResponse(response: AuthResponse) {
            authenticateResponse = response
        }
        
        fun setPublicKeyResponse(response: Jwk) {
            publicKeyResponse = response
        }
        
        fun setCardTokenResponse(response: CardTokenResponse) {
            cardTokenResponse = response
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
    }
} 