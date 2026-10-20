package cz.gopay.sdk.session

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * The polling loop behind [PaymentSession.awaitChargeState], modelled on the GoPay browser
 * SDK's `charge-polling.ts`; the iOS SDK runs the same loop. It is kept apart from the session
 * so a unit test can drive it with faked states and virtual time.
 *
 * The next read always comes [AwaitChargeOptions.intervalMs] after the previous answer arrived,
 * never on a fixed cadence, and after a 3DS challenge the same wait applies. While the charge
 * is undecided ([ChargeState.REQUESTED], [ChargeState.PROCESSING] or [ChargeState.UNKNOWN]) the
 * initial timeout runs: the last wait before the deadline is cut to what is left of it, so one
 * read lands on the deadline and decides. A charge still undecided there is
 * [GopayErrorCodes.PAYMENT_TIMEOUT], with that state on the error; one that moved is handled as
 * that state, and so is an answer that arrives after the deadline, since a read is never cut
 * off.
 *
 * An [ChargeState.ACTION_REQUIRED] state counts only with a non-empty `redirect_url`, as in the
 * browser SDK; without one nothing is reported or opened and the timeout runs on. A new address
 * is reported once, and then: with a [verify], the challenge runs and the timeout starts over
 * from its return, so a gateway that keeps the charge in `PROCESSING` or echoes the same address
 * afterwards is not polled forever; without one the host owns the challenge, so the first
 * address disarms the timeout for good.
 *
 * @param read one `GET /payments/{id}/charge`; its errors end the loop unchanged.
 * @param verify runs the 3DS challenge for a redirect URL, null when the host runs it itself
 *        ([ThreeDSMode.MANUAL]); its errors end the loop unchanged.
 * @param now a clock in milliseconds; only differences are read. Tests pass virtual time.
 */
internal class ChargePoller(
    private val options: AwaitChargeOptions,
    private val read: suspend () -> ChargePaymentResponse,
    private val verify: (suspend (redirectUrl: String) -> Unit)?,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }
) {

    suspend fun run(): ChargePaymentResponse {
        var deadline: Long? = now() + options.initialTimeoutMs
        // Every address the gateway has asked a challenge for: a gateway that keeps echoing the
        // same action must not send the user through the same challenge twice.
        val handled = HashSet<String>()

        while (true) {
            coroutineContext.ensureActive()
            val state = read()
            options.onStateChange?.invoke(state)

            when (state.state) {
                ChargeState.SUCCEEDED -> return state
                ChargeState.FAILED -> throw GopaySDKException(
                    errorCode = GopayErrorCodes.PAYMENT_DECLINED,
                    message = "Charge failed" + (state.failReason?.let { ": $it" } ?: ""),
                    chargeState = state
                )
                ChargeState.ACTION_REQUIRED -> {
                    val redirectUrl = state.action?.redirectUrl?.takeIf { it.isNotEmpty() }
                    if (redirectUrl != null && handled.add(redirectUrl)) {
                        options.onActionRequired?.invoke(redirectUrl)
                        deadline = if (verify != null) {
                            verify.invoke(redirectUrl)
                            now() + options.initialTimeoutMs
                        } else {
                            null
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
                        errorCode = GopayErrorCodes.PAYMENT_TIMEOUT,
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

    /** `30000` as `30`, `2500` as `2.5`: the same figure the iOS SDK's message shows. */
    private fun formatSeconds(ms: Long): String =
        if (ms % 1_000 == 0L) (ms / 1_000).toString() else (ms / 1_000.0).toString()
}
