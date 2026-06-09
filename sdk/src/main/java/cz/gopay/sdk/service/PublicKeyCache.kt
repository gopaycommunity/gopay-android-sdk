package cz.gopay.sdk.service

import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.modules.network.PublicApi
import cz.gopay.sdk.modules.network.unwrap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory cache for the merchant's encryption JWK fetched from `GET /cards/public-key`.
 *
 * Cleared on process death by design — the key is non-sensitive but rotates, and the SDK now
 * avoids on-disk persistence. Concurrent fetches are single-flighted under a [Mutex].
 */
internal class PublicKeyCache(
    private val publicApi: PublicApi
) {
    private val mutex = Mutex()

    @Volatile
    private var cached: Jwk? = null

    suspend fun get(forceRefresh: Boolean = false): Jwk {
        if (!forceRefresh) {
            cached?.let { return it }
        }
        return mutex.withLock {
            if (!forceRefresh) {
                cached?.let { return@withLock it }
            }
            val jwk = publicApi.getPublicKey().unwrap("fetch public key")
            cached = jwk
            jwk
        }
    }

    fun invalidate() {
        cached = null
    }
}
