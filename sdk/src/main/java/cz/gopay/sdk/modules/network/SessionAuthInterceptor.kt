package cz.gopay.sdk.modules.network

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Attaches a `Bearer` token from a specific [SessionTokenProvider] (a PaymentSession). On a
 * 401 from the server, re-authenticates exactly once and retries the request; a second 401 is
 * surfaced as [GopayErrorCodes.AUTH_PAYMENT_TOKEN_EXPIRED] so the caller can request fresh
 * `payment_id` / `payment_secret` from its backend.
 *
 * The interceptor is per-session — one instance per OkHttp client per PaymentSession — so
 * concurrent sessions never share a token. [SessionTokenProvider.currentToken] returns null when
 * the cached token is missing or has expired, so no per-request JWT parsing is needed here.
 */
internal class SessionAuthInterceptor(
    private val tokenProvider: SessionTokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenProvider.currentToken() ?: reauthBlocking()

        val firstResponse = chain.proceed(originalRequest.withBearer(token))
        if (firstResponse.code != 401) {
            return firstResponse
        }

        // Stale token — close the first response, re-auth once, retry.
        firstResponse.close()
        tokenProvider.invalidateToken()
        val refreshed = reauthBlocking()

        val retryResponse = chain.proceed(originalRequest.withBearer(refreshed))
        if (retryResponse.code == 401) {
            retryResponse.close()
            throw GopaySDKException(
                errorCode = GopayErrorCodes.AUTH_PAYMENT_TOKEN_EXPIRED,
                message = "Payment-scoped token still rejected after re-authentication"
            )
        }
        return retryResponse
    }

    /**
     * Runs the suspending re-auth on [Dispatchers.IO] so the OkHttp dispatcher thread doesn't
     * get pinned waiting for it. Single-flighted by the session's Mutex.
     */
    private fun reauthBlocking(): String = try {
        runBlocking(Dispatchers.IO) { tokenProvider.reauthenticate() }
    } catch (e: GopaySDKException) {
        throw e
    } catch (e: Throwable) {
        throw GopaySDKException(
            errorCode = GopayErrorCodes.AUTH_PAYMENT_TOKEN_EXPIRED,
            message = "Payment-scoped token expired and re-authentication failed",
            cause = e
        )
    }

    private fun Request.withBearer(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()
}
