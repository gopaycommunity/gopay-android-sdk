package cz.gopay.sdk.session

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * The polling loop behind [PaymentSession.awaitChargeState], modelled on the GoPay browser
 * SDK's `charge-polling.ts`; the iOS SDK runs the same loop. It is kept apart from the session
 * so a unit test can drive it with faked states and virtual time.
 *
 * The next read always comes [AwaitChargeOptions.intervalMs] after the previous answer arrived,
 * never on a fixed cadence, and after a 3DS challenge the same wait applies. While the charge
 * is undecided ([ChargeState.REQUESTED], [ChargeState.PROCESSING], [ChargeState.UNKNOWN], or
 * [ChargeState.ACTION_REQUIRED] without a new address) the initial timeout runs: the last wait
 * before the deadline is cut to what is left of it, so one read lands on the deadline and
 * decides. A charge still undecided there is [GopayErrorCodes.PAYMENT_TIMEOUT], with that state
 * on the error; one that moved is handled as that state, and so is an answer that arrives after
 * the deadline, since a read is never cut off.
 *
 * An [ChargeState.ACTION_REQUIRED] state counts only with a non-empty `redirect_url`, as in the
 * browser SDK; without one nothing is reported or opened and the timeout runs on. A new address
 * is reported once, and then: with a [verify], the challenge runs and the timeout starts over
 * from its return, so a gateway that keeps the charge in `PROCESSING` or echoes the same address
 * afterwards is not polled forever; without one the host owns the challenge, so the first
 * address disarms the timeout for good.
 *
 * A challenge needs a screen to open on. When there is none at the moment the gateway asks for
 * one, the app is in the background or its activity is being recreated, the loop waits for the
 * screen and keeps reading the charge meanwhile, each read [AwaitChargeOptions.intervalMs] after
 * the last: the screen coming up opens the challenge, and a charge that left that
 * `ACTION_REQUIRED` in the meantime, answered in the bank's app say, is handled as the state it
 * moved to, with the timeout started over. The timeout does not run while the loop waits for a
 * screen, as it does not during the challenge. A screen that goes in the moment the challenge
 * is about to open sends the loop back into the same wait, still reading. Nothing limits that
 * wait: the reads go on until the charge moves or the caller cancels the call.
 *
 * @param read one `GET /payments/{id}/charge`; its errors end the loop unchanged.
 * @param verify runs the 3DS challenge for a redirect URL, null when the host runs it itself
 *        ([ThreeDSMode.MANUAL]); its errors end the loop unchanged, except [ScreenWentAway],
 *        which it throws when there was no screen to open the challenge on after all.
 * @param screenReady whether [verify] has a screen to open the challenge on right now.
 * @param awaitScreen suspends until it has one.
 * @param now a clock in milliseconds; only differences are read. Tests pass virtual time.
 */
internal class ChargePoller(
    private val options: AwaitChargeOptions,
    private val read: suspend () -> ChargePaymentResponse,
    private val verify: (suspend (redirectUrl: String) -> Unit)?,
    private val screenReady: () -> Boolean = { true },
    private val awaitScreen: suspend () -> Unit = {},
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }
) {

    suspend fun run(): ChargePaymentResponse {
        var deadline: Long? = now() + options.initialTimeoutMs
        // Every address the gateway has asked a challenge for: a gateway that keeps echoing the
        // same action must not send the user through the same challenge twice.
        val handled = HashSet<String>()
        // Every address reported to the host; one the loop waited for a screen with and did not
        // get to open is reported once and may still be opened later.
        val reported = HashSet<String>()
        // A state read while waiting for a screen, which the loop takes up instead of reading.
        var carried: ChargePaymentResponse? = null

        while (true) {
            coroutineContext.ensureActive()
            val state = carried ?: read().also { options.onStateChange?.invoke(it) }
            carried = null

            when (state.state) {
                ChargeState.SUCCEEDED -> return state
                ChargeState.FAILED -> throw GopaySDKException(
                    code = GopayErrorCodes.PAYMENT_DECLINED,
                    message = "Charge failed" + (state.failReason?.let { ": $it" } ?: ""),
                    chargeState = state
                )
                ChargeState.ACTION_REQUIRED -> {
                    val redirectUrl = state.action?.redirectUrl?.takeIf { it.isNotEmpty() }
                    if (redirectUrl != null && redirectUrl !in handled) {
                        if (reported.add(redirectUrl)) options.onActionRequired?.invoke(redirectUrl)
                        if (verify == null) {
                            handled.add(redirectUrl)
                            deadline = null
                        } else {
                            val moved = verifyOnAScreen(redirectUrl, verify)
                            deadline = now() + options.initialTimeoutMs
                            if (moved != null) {
                                carried = moved
                                continue
                            }
                            handled.add(redirectUrl)
                        }
                    }
                }
                // Not decided yet; a state this SDK does not know is waited out like PROCESSING.
                ChargeState.REQUESTED, ChargeState.PROCESSING, ChargeState.UNKNOWN -> Unit
            }

            var wait = options.intervalMs
            deadline?.let {
                val remaining = it - now()
                if (remaining <= 0) {
                    throw GopaySDKException(
                        code = GopayErrorCodes.PAYMENT_TIMEOUT,
                        message = "Charge did not leave ${state.state} within " +
                            "${formatSeconds(options.initialTimeoutMs)} s; read getChargeState() " +
                            "or wait for the payment notification before offering a new payment",
                        chargeState = state
                    )
                }
                // The read that decides the timeout lands on the deadline, not an interval past it.
                wait = minOf(wait, remaining)
            }
            delay(wait)
        }
    }

    /**
     * Runs the challenge for [redirectUrl] once there is a screen for it, reading the charge
     * while there is none. The screen can go between the moment it is seen and the moment the
     * challenge opens on it; [verify] then says so, and the wait starts again, with its reads,
     * rather than going on inside [verify] where nothing reads the charge.
     *
     * @return null once the challenge has run, or the state the charge moved to meanwhile.
     */
    private suspend fun verifyOnAScreen(
        redirectUrl: String,
        verify: suspend (redirectUrl: String) -> Unit
    ): ChargePaymentResponse? {
        var screenWent = false
        while (true) {
            if (screenWent || !screenReady()) readUntilScreen(redirectUrl)?.let { return it }
            try {
                verify(redirectUrl)
                return null
            } catch (e: ScreenWentAway) {
                screenWent = true
            }
        }
    }

    /**
     * Waits for a screen to open the challenge for [redirectUrl] on, reading the charge every
     * [AwaitChargeOptions.intervalMs] meanwhile.
     *
     * @return null once a screen is there, or the first state read that is no longer that
     *         `ACTION_REQUIRED` with [redirectUrl], already handed to `onStateChange`.
     */
    private suspend fun readUntilScreen(redirectUrl: String): ChargePaymentResponse? = coroutineScope {
        val screen = async { awaitScreen() }
        var moved: ChargePaymentResponse? = null
        try {
            // Each round waits one interval for the screen; without it, reads the charge.
            while (moved == null && withTimeoutOrNull(options.intervalMs) { screen.await() } == null) {
                val state = read()
                options.onStateChange?.invoke(state)
                val sameChallenge = state.state == ChargeState.ACTION_REQUIRED &&
                    state.action?.redirectUrl == redirectUrl
                if (!sameChallenge) moved = state
            }
        } finally {
            screen.cancel()
        }
        moved
    }

    /** Thrown by `verify` when the screen it was to open the challenge on is gone. */
    internal class ScreenWentAway : Exception("no screen to open the 3DS challenge on")

    /** `30000` as `30`, `2500` as `2.5`: the same figure the iOS SDK's message shows. */
    private fun formatSeconds(ms: Long): String =
        if (ms % 1_000 == 0L) (ms / 1_000).toString() else (ms / 1_000.0).toString()
}
