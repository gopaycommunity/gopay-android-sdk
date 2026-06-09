package cz.gopay.sdk.modules.network

/**
 * Narrow contract exposed by [cz.gopay.sdk.session.PaymentSession] to [SessionAuthInterceptor].
 *
 * Kept as a separate interface so the interceptor doesn't reach into the rest of PaymentSession,
 * and so the session can be stubbed in tests without spinning up a real OkHttp client.
 */
internal interface SessionTokenProvider {
    /** Currently cached JWT, or null if the session has never authenticated / has been invalidated. */
    fun currentToken(): String?

    /** Fetches a fresh JWT from `payment_credentials`. Single-flighted; safe under contention. */
    suspend fun reauthenticate(): String

    /** Marks the cached JWT as no longer valid, forcing the next call to re-authenticate. */
    fun invalidateToken()
}
