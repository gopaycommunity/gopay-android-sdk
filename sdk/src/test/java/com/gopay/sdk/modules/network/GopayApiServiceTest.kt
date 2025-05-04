package com.gopay.sdk.modules.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
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
        val expectedResponse = "token_response"
        mockApiService.setAuthenticateResponse(expectedResponse)
        
        // When calling authenticate
        val authToken = "Bearer test_token"
        val result = mockApiService.authenticate(authToken)
        
        // Then the result should match the expected response
        assertEquals(expectedResponse, result)
    }

    // Mock implementation of GopayApiService for testing
    private class MockGopayApiService(
        private val delegate: BehaviorDelegate<GopayApiService>
    ) : GopayApiService {
        private var authenticateResponse: Any = "default_response"
        
        fun setAuthenticateResponse(response: Any) {
            authenticateResponse = response
        }
        
        override suspend fun authenticate(authorization: String): Any {
            return delegate.returningResponse(authenticateResponse)
                .authenticate(authorization)
        }
    }
} 