package cz.gopay.sdk.modules.network

import cz.gopay.sdk.config.GopayEnvironment
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
        val manager = NetworkManager(GopayConfig(environment = GopayEnvironment.SANDBOX))
        assertNotNull("AuthApi should be available without any extra config", manager.authApi)
    }

    @Test
    fun buildPaymentApiReturnsDistinctInstancePerProvider() {
        val manager = NetworkManager(GopayConfig(environment = GopayEnvironment.SANDBOX))

        val a = manager.buildPaymentApi(noopProvider())
        val b = manager.buildPaymentApi(noopProvider())

        // Distinct Retrofit-generated proxies are expected per provider.
        assert(a !== b)
    }

    @Test
    fun publicApiThrowsWhenShareableKeyMissing() {
        val manager = NetworkManager(GopayConfig(environment = GopayEnvironment.SANDBOX))

        val ex = assertThrows(GopaySDKException::class.java) {
            manager.publicApi  // lazy — triggers the check
        }
        assertEquals(GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING, ex.code)
    }

    @Test
    fun publicApiBuildsWhenShareableKeyPresent() {
        val manager = NetworkManager(
            GopayConfig(
                environment = GopayEnvironment.SANDBOX,
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
                environment = GopayEnvironment.DEVELOPMENT.create("https://localhost:8080"),
                requestTimeoutMs = 10_000,
                debugLoggingEnabled = true
            ),
            GopayConfig(
                environment = GopayEnvironment.PRODUCTION,
                requestTimeoutMs = 20_000
            )
        ).forEach { cfg ->
            val manager = NetworkManager(cfg)
            assertNotNull(manager.authApi)
        }
    }

    @Test
    fun `a requestTimeoutMs the HTTP client cannot use is replaced by the default`() {
        fun timeout(asked: Long) =
            NetworkManager.requestTimeoutMs(GopayConfig(GopayEnvironment.SANDBOX, requestTimeoutMs = asked))

        for (unusable in listOf(0L, -1L, Long.MIN_VALUE, Int.MAX_VALUE + 1L, Long.MAX_VALUE)) {
            assertEquals("$unusable", GopayConfig.DEFAULT_REQUEST_TIMEOUT_MS, timeout(unusable))
        }
        assertEquals(1L, timeout(1))
        assertEquals(1_500L, timeout(1_500))
        assertEquals(Int.MAX_VALUE.toLong(), timeout(Int.MAX_VALUE.toLong()))
        assertEquals(30_000L, GopayConfig(GopayEnvironment.SANDBOX).requestTimeoutMs)
    }

    @Test
    fun `connecting gets half of the timeout and never none`() {
        assertEquals(15_000L, NetworkManager.connectTimeoutMs(30_000))
        assertEquals("under two seconds used to come out as zero, no timeout", 750L, NetworkManager.connectTimeoutMs(1_500))
        assertEquals(1L, NetworkManager.connectTimeoutMs(1))
    }

    @Test
    fun `the SDK builds its clients with any requestTimeoutMs`() {
        for (asked in listOf(-5L, 0L, 1L, 1_999L, Long.MAX_VALUE)) {
            val manager = NetworkManager(GopayConfig(GopayEnvironment.SANDBOX, requestTimeoutMs = asked))
            assertNotNull("$asked", manager.authApi)
        }
    }

    private fun noopProvider() = object : SessionTokenProvider {
        override fun currentToken(): String? = "fake.jwt.token"
        override suspend fun reauthenticate(): String = "fake.jwt.token"
        override fun invalidateToken() {}
    }
}
