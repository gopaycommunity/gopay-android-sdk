package cz.gopay.sdk.modules.network

import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkManagerTest {

    @Test
    fun authApiIsBuiltOnInit() {
        val manager = NetworkManager(GopayConfig(environment = Environment.SANDBOX))
        assertNotNull("AuthApi should be available without any extra config", manager.authApi)
    }

    @Test
    fun buildPaymentApiReturnsDistinctInstancePerProvider() {
        val manager = NetworkManager(GopayConfig(environment = Environment.SANDBOX))

        val a = manager.buildPaymentApi(noopProvider())
        val b = manager.buildPaymentApi(noopProvider())

        // Distinct Retrofit-generated proxies are expected per provider.
        assert(a !== b)
    }

    @Test
    fun publicApiThrowsWhenShareableKeyMissing() {
        val manager = NetworkManager(GopayConfig(environment = Environment.SANDBOX))

        val ex = assertThrows(GopaySDKException::class.java) {
            manager.publicApi  // lazy — triggers the check
        }
        assertEquals(GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING, ex.errorCode)
    }

    @Test
    fun publicApiBuildsWhenShareableKeyPresent() {
        val manager = NetworkManager(
            GopayConfig(
                environment = Environment.SANDBOX,
                clientId = "cid",
                shareableKey = "sk"
            )
        )
        assertNotNull(manager.publicApi)
    }

    @Test
    fun configMappingAcceptsVariedEnvironments() {
        listOf(
            GopayConfig(
                environment = Environment.DEVELOPMENT.create("https://localhost:8080"),
                requestTimeoutMs = 10_000,
                debug = true
            ),
            GopayConfig(
                environment = Environment.PRODUCTION,
                requestTimeoutMs = 20_000
            )
        ).forEach { cfg ->
            val manager = NetworkManager(cfg)
            assertNotNull(manager.authApi)
        }
    }

    private fun noopProvider() = object : SessionTokenProvider {
        override fun currentToken(): String? = "fake.jwt.token"
        override suspend fun reauthenticate(): String = "fake.jwt.token"
        override fun invalidateToken() {}
    }
}
