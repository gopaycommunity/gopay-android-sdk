package cz.gopay.sdk.modules.network

/**
 * Request tag: [SessionAuthInterceptor] sends the request with the session's current token only,
 * never exchanges the credentials for it, and hands its 401 back as it is. For `init-session`,
 * which the SDK sends on its own behalf and nothing waits on, so it cannot touch the token the
 * session's own calls use. The browser SDK's `quiet` request does the same (`recoverAuth = false`).
 */
internal object NoAuthRecovery

/**
 * Request tag that says whether `PaymentSession.close()` cancelled the request. [closed] is set
 * before the cancel, so a failure that follows is known to be the close and not the network: a
 * call timeout cancels the call too, and only the close goes unreported. Read by the telemetry
 * interceptor and by the request's own callback, never by the auth.
 */
internal class SessionCloseSignal {
    @Volatile
    var closed: Boolean = false
}
