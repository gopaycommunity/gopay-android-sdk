package cz.gopay.sdk.session

import android.app.Activity
import cz.gopay.sdk.model.ChargePaymentResponse

/**
 * What [PaymentSession.awaitChargeState] does with a 3DS challenge the gateway asks for.
 */
enum class ThreeDSMode {
    /**
     * The SDK opens the challenge itself, through [PaymentSession.handle3dsVerification], with
     * the [AwaitChargeOptions.activity] and [AwaitChargeOptions.returnUrl] it was given, and
     * polls on once the challenge returns. The default.
     */
    AUTOMATIC,

    /**
     * The SDK only reports the challenge through [AwaitChargeOptions.onActionRequired] and keeps
     * polling; the host opens it, with [PaymentSession.handle3dsVerification] or on its own.
     */
    MANUAL
}

/**
 * Options for [PaymentSession.awaitChargeState]. The defaults wait with the cadence GoPay
 * recommends, one request a second, give up on a charge that does not move within 30 seconds,
 * and run a 3DS challenge themselves; the iOS SDK's `AwaitChargeOptions` has the matching options,
 * in seconds.
 *
 * @param intervalMs how long to wait after one charge state arrives before asking for the next,
 *        in milliseconds. Has to be positive.
 * @param initialTimeoutMs how long, in milliseconds from the call, the charge may stay
 *        undecided (`REQUESTED`, `PROCESSING`, `UNKNOWN`, or `ACTION_REQUIRED` without a new
 *        address) before the call gives up with
 *        [cz.gopay.sdk.exception.GopayErrorCodes.PAYMENT_TIMEOUT]. A 3DS challenge has no time
 *        limit: in [ThreeDSMode.AUTOMATIC] the timeout starts over once the challenge returns,
 *        in [ThreeDSMode.MANUAL] the first reported address cancels it for good. Measured on
 *        the device's monotonic clock, while the waits between reads are `delay` calls, so in a
 *        `runTest` with virtual time the deadline passes only after real seconds. Has to be
 *        positive.
 * @param threeDSMode whether the SDK runs a 3DS challenge itself or leaves it to the host.
 * @param returnUrl for [ThreeDSMode.AUTOMATIC]: the `callback.return_url` your backend created
 *        the payment with, passed to [PaymentSession.handle3dsVerification] as its `returnUrl`,
 *        with the same meaning and the same fallback when it is null.
 * @param activity for [ThreeDSMode.AUTOMATIC]: the activity the 3DS challenge is started from,
 *        as [PaymentSession.handle3dsVerification] takes it. Required in that mode, unused in
 *        [ThreeDSMode.MANUAL]. The SDK keeps it only for the duration of the call: nothing stores
 *        it, and cancelling the calling coroutine, which a `lifecycleScope` does when the activity
 *        goes away, ends the call and releases it.
 * @param onStateChange called with every charge state the call reads, the final one included,
 *        before the call returns or throws. Runs on the calling coroutine's dispatcher. An
 *        exception it throws ends the call with that exception, unchanged.
 * @param onActionRequired called with `action.redirect_url` the first time an `ACTION_REQUIRED`
 *        state carries that non-empty address, once per distinct address, before the SDK opens
 *        the challenge in [ThreeDSMode.AUTOMATIC]. Runs on the calling coroutine's dispatcher.
 *        An exception it throws ends the call with that exception, unchanged, and the challenge
 *        is not opened.
 */
data class AwaitChargeOptions(
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    val initialTimeoutMs: Long = DEFAULT_INITIAL_TIMEOUT_MS,
    val threeDSMode: ThreeDSMode = ThreeDSMode.AUTOMATIC,
    val returnUrl: String? = null,
    val activity: Activity? = null,
    val onStateChange: ((ChargePaymentResponse) -> Unit)? = null,
    val onActionRequired: ((redirectUrl: String) -> Unit)? = null
) {
    companion object {
        /** One second, the cadence GoPay's "Polling and notifications" guide recommends. */
        const val DEFAULT_INTERVAL_MS: Long = 1_000

        /** Thirty seconds, as the GoPay browser SDK's `awaitChargeState` uses. */
        const val DEFAULT_INITIAL_TIMEOUT_MS: Long = 30_000
    }
}
