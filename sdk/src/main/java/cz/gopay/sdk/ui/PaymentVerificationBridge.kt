package cz.gopay.sdk.ui

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

/**
 * Carries the result of a 3DS challenge from [PaymentVerificationActivity] back to the
 * `handle3dsVerification` call that started it.
 *
 * Every report names the verification it belongs to. An activity outlives its own result: it
 * reports and calls `finish()`, and `onDestroy` runs later, by which time the caller may already
 * have started the next verification. Without an owner that late `onDestroy` cancels the new
 * one, and the host is told a challenge the user never saw was dismissed by the user.
 */
internal object PaymentVerificationBridge {

    /**
     * Identity of one verification. Only the caller that registered it and the activity serving
     * it hold the token, so a report from an activity whose verification is already over cannot
     * reach the verification that replaced it.
     */
    internal class Owner

    private class Pending(val owner: Owner, val deferred: CompletableDeferred<Boolean>) {
        /** Closes the activity serving this verification; set by [attach]. */
        @Volatile var close: (() -> Unit)? = null
    }

    private val pending = AtomicReference<Pending?>()

    /**
     * Claims the bridge for [deferred].
     *
     * @return the token every later report has to carry, or null when a verification is already
     *         in flight.
     */
    fun register(deferred: CompletableDeferred<Boolean>): Owner? {
        val owner = Owner()
        return if (pending.compareAndSet(null, Pending(owner, deferred))) owner else null
    }

    /** The verification an activity starting right now was launched for. */
    fun currentOwner(): Owner? = pending.get()?.owner

    /**
     * Registers how to close the activity serving [owner], so [dismiss] can take it off the
     * screen. A recreated activity attaches again and replaces the one before it.
     *
     * @return false when [owner] no longer holds the bridge, the caller having given up between
     *         the activity starting and attaching; the activity then has nobody to serve and
     *         closes itself.
     */
    fun attach(owner: Owner, close: () -> Unit): Boolean {
        val current = pending.get()?.takeIf { it.owner === owner } ?: return false
        current.close = close
        // dismiss can release the bridge between the read above and the write, without seeing
        // close; the activity has to find out here then.
        return pending.get() === current
    }

    /**
     * Waits for [owner]'s result. When the calling coroutine is cancelled first, which is the
     * host giving up rather than the user backing out (that completes [deferred]), the activity
     * serving it is closed through [dismiss]: the challenge would otherwise stay on screen with
     * nobody left to answer to. The cancellation itself goes on to the caller unchanged.
     */
    suspend fun await(owner: Owner, deferred: CompletableDeferred<Boolean>) {
        try {
            deferred.await()
        } catch (e: CancellationException) {
            if (!deferred.isCompleted) dismiss(owner)
            throw e
        }
    }

    /**
     * The caller gave up on [owner], typically because its coroutine was cancelled: closes the
     * activity serving it, if one is up, and releases the bridge. Nothing is reported, since the
     * caller is no longer waiting, and the activity's own reports reach nothing afterwards.
     */
    fun dismiss(owner: Owner) {
        val current = pending.get() ?: return
        if (current.owner !== owner || !pending.compareAndSet(current, null)) return
        current.close?.invoke()
    }

    fun complete(owner: Owner) { take(owner)?.complete(true) }

    /**
     * The challenge closed before it returned: the user backed out, the task was swiped away, or
     * the page failed after it had started rendering. Surfaces to the host as
     * [GopayErrorCodes.PAYMENT_CANCELLED], not as a coroutine cancellation, which would read as
     * the host's own scope going away.
     */
    fun cancel(owner: Owner, reason: String = "the user closed it") {
        failWith(owner, GopayErrorCodes.PAYMENT_CANCELLED, "3DS verification was closed: $reason")
    }

    /**
     * The challenge never reached the user: the page could not be loaded, or no app on the
     * device took the hand-off it asked for. Distinct from [cancel] on purpose: the host has to
     * be able to tell a user who walked away from a verification that never had a chance to run.
     */
    fun failUnreachable(owner: Owner, reason: String) {
        failWith(
            owner,
            GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE,
            "3DS verification could not be loaded: $reason"
        )
    }

    /**
     * The SDK itself got the verification wrong, as opposed to the challenge being unreachable.
     * Nothing the host does differently would have helped, so it never carries the advice
     * attached to [GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE] about charging again.
     */
    fun failInternally(owner: Owner, reason: String) {
        failWith(
            owner,
            GopayErrorCodes.INTERNAL_UNEXPECTED_ERROR,
            "3DS verification could not start: $reason"
        )
    }

    private fun failWith(owner: Owner, errorCode: String, message: String) {
        take(owner)?.completeExceptionally(
            GopaySDKException(errorCode = errorCode, message = message)
        )
    }

    /**
     * Takes the deferred away from [owner]: the first report wins, a second one is a no-op, and
     * a report from an owner the bridge has moved on from reaches nothing.
     */
    private fun take(owner: Owner): CompletableDeferred<Boolean>? {
        val current = pending.get() ?: return null
        if (current.owner !== owner) return null
        return if (pending.compareAndSet(current, null)) current.deferred else null
    }
}
