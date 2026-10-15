package cz.gopay.sdk.ui

import android.app.Activity
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wallet.AutoResolveHelper
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Carries the Google Pay token from [GooglePayLauncherActivity] back to the `chargeWithGooglePay`
 * call that started it.
 *
 * Every report names the request it belongs to, as in [PaymentVerificationBridge]. A launcher can
 * outlive its own request: the caller can give up first and release the bridge, the next payment
 * can claim it, and only then does the old sheet come back with a result, or the old launcher
 * finish. Without an owner that late report would end the new payment, or hand it a token the
 * user approved for the old one.
 */
internal object GooglePayBridge {

    /**
     * Identity of one request. [id] travels in the launcher's intent, so a launcher, fresh or
     * recreated under the open sheet, serves its own request and never whichever is running by
     * then.
     */
    internal class Owner(val id: Long)

    private class Pending(val owner: Owner, val deferred: CompletableDeferred<Result<String>>)

    private val pending = AtomicReference<Pending?>()
    /**
     * Starts at a random point rather than at zero: after the process dies, a launcher the system
     * restores still carries the old [Owner.id], and a counter starting over would hand the same
     * id to the first payment of the new process.
     */
    private val nextId = AtomicLong(Random.nextLong(0, Long.MAX_VALUE / 2))

    /**
     * Claims the bridge for [deferred].
     *
     * @return the token every later report has to carry, or null when a sheet is already up.
     */
    fun register(deferred: CompletableDeferred<Result<String>>): Owner? {
        val owner = Owner(nextId.incrementAndGet())
        return if (pending.compareAndSet(null, Pending(owner, deferred))) owner else null
    }

    /** The request with [id], while it still holds the bridge; null once it is over. */
    fun currentOwner(id: Long): Owner? = pending.get()?.owner?.takeIf { it.id == id }

    /** Hands [result] to [owner]'s caller; the first report wins and a stale one reaches nothing. */
    fun report(owner: Owner, result: Result<String>) {
        val current = pending.get() ?: return
        if (current.owner !== owner) return
        if (pending.compareAndSet(current, null)) current.deferred.complete(result)
    }

    /**
     * Releases the bridge, but only while [owner] still holds it: the caller's `finally` can land
     * after a later request has claimed it.
     */
    fun clear(owner: Owner) {
        val current = pending.get() ?: return
        if (current.owner === owner) pending.compareAndSet(current, null)
    }

    /**
     * What `onActivityResult` reports for a Google Pay result, worked out here without the Android
     * runtime so it can be tested:
     * - a token for `RESULT_OK` with payment data, and a failure for `RESULT_OK` without any;
     * - a user dismissal for `RESULT_CANCELED`;
     * - for `AutoResolveHelper.RESULT_ERROR`, [UnavailableThrowable] when Google Play services
     *   cannot show the sheet, any other error otherwise;
     * - a failure for a result code Google Pay does not document, rather than no report at all,
     *   which would leave the caller waiting for good.
     */
    fun resultFor(
        resultCode: Int,
        paymentDataJson: String?,
        statusCode: Int?,
        statusMessage: String?
    ): Result<String> = when (resultCode) {
        Activity.RESULT_OK -> paymentDataJson?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Google Pay returned no payment data"))
        Activity.RESULT_CANCELED -> dismissed()
        AutoResolveHelper.RESULT_ERROR -> {
            val message = "Google Pay error: $statusMessage ($statusCode)"
            Result.failure(
                if (isUnavailable(statusCode)) UnavailableThrowable(message) else Exception(message)
            )
        }
        else -> Result.failure(IllegalStateException("Unexpected Google Pay result code $resultCode"))
    }

    /** The user leaving the launcher before Google Pay answered, e.g. with Back. */
    fun dismissed(): Result<String> = Result.failure(UserCancelledThrowable())

    // Sentinel for a user dismissal, which failureFor turns into PAYMENT_CANCELLED
    internal class UserCancelledThrowable : Throwable("Google Pay sheet was dismissed by the user")

    /** Google Pay cannot show its sheet on this device at all, as opposed to failing in it. */
    internal class UnavailableThrowable(message: String) : Throwable(message)

    /**
     * Google Pay status codes that mean the service itself is not there to show the sheet:
     * Google Play services missing, disabled or too old.
     */
    private val UNAVAILABLE_STATUS_CODES = setOf(
        CommonStatusCodes.API_NOT_CONNECTED,
        CommonStatusCodes.SERVICE_DISABLED,
        CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED
    )

    fun isUnavailable(statusCode: Int?): Boolean = statusCode in UNAVAILABLE_STATUS_CODES

    /**
     * What `chargeWithGooglePay` throws for a sheet that did not produce a token:
     * - the user closing it is [GopayErrorCodes.PAYMENT_CANCELLED];
     * - Google Pay unable to show it is [GopayErrorCodes.PAYMENT_METHOD_UNAVAILABLE];
     * - any other failure is [GopayErrorCodes.PAYMENT_PROCESSING_FAILED], with [cause] kept.
     *
     * [GopayErrorCodes.PAYMENT_GOOGLE_PAY_IN_PROGRESS] is only ever for a sheet already open.
     * The iOS SDK maps Apple Pay the same way.
     */
    fun failureFor(cause: Throwable): GopaySDKException = when (cause) {
        is UserCancelledThrowable -> GopaySDKException(
            errorCode = GopayErrorCodes.PAYMENT_CANCELLED,
            message = "Google Pay sheet was closed without paying"
        )
        is UnavailableThrowable -> GopaySDKException(
            errorCode = GopayErrorCodes.PAYMENT_METHOD_UNAVAILABLE,
            message = "Google Pay is not available: ${cause.message}",
            cause = cause
        )
        else -> GopaySDKException(
            errorCode = GopayErrorCodes.PAYMENT_PROCESSING_FAILED,
            message = "Google Pay failed: ${cause.message}",
            cause = cause
        )
    }
}
