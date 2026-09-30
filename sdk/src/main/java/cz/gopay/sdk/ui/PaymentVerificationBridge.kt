package cz.gopay.sdk.ui

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
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

    private class Pending(val owner: Owner, val deferred: CompletableDeferred<Boolean>)

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

    fun complete(owner: Owner) { take(owner)?.complete(true) }

    /** User dismissal. Surfaces to the host as a [kotlinx.coroutines.CancellationException]. */
    fun cancel(owner: Owner) { take(owner)?.cancel() }

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
     * Releases the bridge, but only while [owner] still holds it. The caller runs this in a
     * `finally`, which can land after a later verification has already claimed the bridge.
     */
    fun clear(owner: Owner) {
        val current = pending.get() ?: return
        if (current.owner === owner) pending.compareAndSet(current, null)
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
