package cz.gopay.sdk.ui

import androidx.annotation.VisibleForTesting
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

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
     * reach the verification that replaced it. [id] travels in the challenge's intent, as in
     * [GooglePayBridge.Owner]: a challenge that turns up late, after its own verification gave
     * up on it and the next one claimed the bridge, finds no verification under its id and
     * closes itself rather than serve the one running by then.
     */
    internal class Owner(val id: Long)

    private class Pending(val owner: Owner, val deferred: CompletableDeferred<Boolean>) {
        /** Closes the activity serving this verification; set by [attach]. */
        @Volatile var close: (() -> Unit)? = null

        /** Completed once the challenge screen took the verification over, or it ended. */
        val shown = CompletableDeferred<Unit>()
    }

    private val pending = AtomicReference<Pending?>()

    /** Starts at a random point, for the reason [GooglePayBridge] gives for its own ids. */
    private val nextId = AtomicLong(Random.nextLong(0, Long.MAX_VALUE / 2))

    /**
     * Claims the bridge for [deferred].
     *
     * @return the token every later report has to carry, or null when a verification is already
     *         in flight.
     */
    fun register(deferred: CompletableDeferred<Boolean>): Owner? {
        val owner = Owner(nextId.incrementAndGet())
        return if (pending.compareAndSet(null, Pending(owner, deferred))) owner else null
    }

    /** The verification with [id], while it still holds the bridge; null once it is over. */
    fun currentOwner(id: Long): Owner? = pending.get()?.owner?.takeIf { it.id == id }

    /** Whichever verification holds the bridge, for a test to answer as its challenge would. */
    @VisibleForTesting
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
        current.shown.complete(Unit)
        // dismiss can release the bridge between the read above and the write, without seeing
        // close; the activity has to find out here then.
        return pending.get() === current
    }

    /**
     * Waits for [owner]'s result, once the challenge screen was started. A screen that has not
     * taken the verification over within [SdkScreens.showTimeoutMs] never appeared: the
     * verification ends as [GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE], and a screen that
     * turns up later finds nothing to serve and closes itself. When the calling coroutine is
     * cancelled first, which is the host giving up rather than the user backing out (that
     * completes [deferred]), the activity serving it is closed through [dismiss]: the challenge
     * would otherwise stay on screen with nobody left to answer to. The cancellation itself goes
     * on to the caller unchanged.
     */
    suspend fun await(owner: Owner, deferred: CompletableDeferred<Boolean>) {
        try {
            val current = pending.get()?.takeIf { it.owner === owner }
            if (current != null && withTimeoutOrNull(SdkScreens.showTimeoutMs) { current.shown.await() } == null) {
                giveUpUnlessShown(current)
            }
            deferred.await()
        } catch (e: CancellationException) {
            if (!deferred.isCompleted) dismiss(owner)
            throw e
        }
    }

    /**
     * The limit ran out for [current]. A screen that took the verification over in the same
     * instant counts as shown: the limit and the screen can cross, and the caller must not be
     * told the challenge is unreachable while the user is looking at it. Otherwise the
     * verification ends here, and a screen attaching in this very moment is closed, or closes
     * itself when [attach] tells it the bridge has moved on.
     */
    private fun giveUpUnlessShown(current: Pending) {
        if (current.shown.isCompleted || !pending.compareAndSet(current, null)) return
        current.shown.complete(Unit)
        current.close?.invoke()
        current.deferred.completeExceptionally(
            GopaySDKException(
                code = GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE,
                message = "3DS verification could not be loaded: " + SdkScreens.notShown("the challenge screen")
            )
        )
    }

    /**
     * Ends [owner]'s verification with [cause] while its challenge is not on screen yet: the
     * call is still waiting for a screen to open from, or the challenge was started and has not
     * appeared. A challenge the user already sees is left alone. For a session that is closed
     * while its verification waits: the bridge would otherwise stay held, and the next payment
     * be told a verification is in progress.
     *
     * @return whether the verification was ended.
     */
    fun releaseUnshown(owner: Owner, cause: Throwable): Boolean {
        val current = pending.get()?.takeIf { it.owner === owner } ?: return false
        if (current.shown.isCompleted || !pending.compareAndSet(current, null)) return false
        current.shown.complete(Unit)
        current.close?.invoke()
        current.deferred.completeExceptionally(cause)
        return true
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
            GopaySDKException(code = errorCode, message = message)
        )
    }

    /**
     * Takes the deferred away from [owner]: the first report wins, a second one is a no-op, and
     * a report from an owner the bridge has moved on from reaches nothing.
     */
    private fun take(owner: Owner): CompletableDeferred<Boolean>? {
        val current = pending.get() ?: return null
        if (current.owner !== owner) return null
        if (!pending.compareAndSet(current, null)) return null
        current.shown.complete(Unit)
        return current.deferred
    }
}
