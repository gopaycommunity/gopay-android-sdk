package cz.gopay.sdk.ui

import android.app.Activity
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wallet.AutoResolveHelper
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bridge between `chargeWithGooglePay` and its launcher activity: one request at a time, and
 * no report from a request that is already over can reach the one after it.
 */
class GooglePayBridgeTest {

    private val owners = mutableListOf<GooglePayBridge.Owner>()

    @After
    fun tearDown() {
        owners.forEach(GooglePayBridge::clear)
    }

    private fun register(d: CompletableDeferred<Result<String>>): GooglePayBridge.Owner? =
        GooglePayBridge.register(d)?.also { owners += it }

    @Test
    fun `register hands out no owner while a sheet is up`() {
        assertNotNull(register(CompletableDeferred()))
        assertNull(register(CompletableDeferred()))
    }

    @Test
    fun `a report reaches the caller once`() {
        val d = CompletableDeferred<Result<String>>()
        val owner = register(d)!!

        GooglePayBridge.report(owner, Result.success("""{"protocolVersion":"ECv2"}"""))
        GooglePayBridge.report(owner, GooglePayBridge.dismissed())

        assertEquals("""{"protocolVersion":"ECv2"}""", d.getCompleted().getOrNull())
        assertNull(GooglePayBridge.currentOwner(owner.id))
    }

    @Test
    fun `a late report of a finished request does not reach the next one`() {
        val first = register(CompletableDeferred())!!
        GooglePayBridge.clear(first)
        val next = CompletableDeferred<Result<String>>()
        val second = register(next)!!

        // The old sheet answers, then the old launcher finishes.
        GooglePayBridge.report(first, Result.success("token of the first payment"))
        GooglePayBridge.report(first, GooglePayBridge.dismissed())
        GooglePayBridge.clear(first)

        assertFalse(next.isCompleted)
        assertSame(second, GooglePayBridge.currentOwner(second.id))
    }

    @Test
    fun `a launcher finds only the request its intent names`() {
        val first = register(CompletableDeferred())!!
        GooglePayBridge.clear(first)
        val second = register(CompletableDeferred())!!

        // A launcher started for the first payment, arriving late, must not take the second.
        assertNull(GooglePayBridge.currentOwner(first.id))
        assertSame(second, GooglePayBridge.currentOwner(second.id))
    }

    @Test
    fun `a sheet that returns payment data hands over its JSON`() {
        val result = GooglePayBridge.resultFor(Activity.RESULT_OK, "{\"x\":1}", null, null)

        assertEquals("{\"x\":1}", result.getOrNull())
    }

    @Test
    fun `RESULT_OK without payment data is a failure, not a crash`() {
        val failure = GooglePayBridge.resultFor(Activity.RESULT_OK, null, null, null).exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(
            GopayErrorCodes.PAYMENT_PROCESSING_FAILED,
            (GooglePayBridge.failureFor(failure!!) as GopaySDKException).errorCode
        )
    }

    @Test
    fun `a closed sheet and Back on the launcher both read as the user leaving`() {
        val closed = GooglePayBridge.resultFor(Activity.RESULT_CANCELED, null, null, null)

        assertTrue(closed.exceptionOrNull() is GooglePayBridge.UserCancelledThrowable)
        assertTrue(GooglePayBridge.dismissed().exceptionOrNull() is GooglePayBridge.UserCancelledThrowable)
    }

    @Test
    fun `a Google Pay error tells an unavailable sheet from a failed one`() {
        val unavailable = GooglePayBridge.resultFor(
            AutoResolveHelper.RESULT_ERROR, null, CommonStatusCodes.SERVICE_DISABLED, "disabled"
        )
        val failed = GooglePayBridge.resultFor(
            AutoResolveHelper.RESULT_ERROR, null, CommonStatusCodes.DEVELOPER_ERROR, "merchant"
        )

        assertTrue(unavailable.exceptionOrNull() is GooglePayBridge.UnavailableThrowable)
        assertFalse(failed.exceptionOrNull() is GooglePayBridge.UnavailableThrowable)
        assertTrue(failed.exceptionOrNull()!!.message!!.contains("merchant"))
    }

    @Test
    fun `an undocumented result code is a failure rather than no report`() {
        val failure = GooglePayBridge.resultFor(42, null, null, null).exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains("42"))
    }

    @Test
    fun `a dismissed sheet surfaces as a cancellation`() {
        val failure = GooglePayBridge.failureFor(GooglePayBridge.UserCancelledThrowable())

        assertTrue(failure is CancellationException)
    }

    @Test
    fun `a sheet Google Pay cannot show is PAYMENT_004`() {
        val cause = GooglePayBridge.UnavailableThrowable("Google Play services disabled")

        val failure = GooglePayBridge.failureFor(cause) as GopaySDKException

        assertEquals(GopayErrorCodes.PAYMENT_METHOD_UNAVAILABLE, failure.errorCode)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `any other Google Pay failure is PAYMENT_003, never PAYMENT_009`() {
        val causes = listOf(
            Exception("Google Pay error: merchant account error (409)"),
            IllegalStateException("PaymentData is null"),
            IllegalArgumentException("Missing signature in Google Pay token")
        )
        for (cause in causes) {
            val failure = GooglePayBridge.failureFor(cause) as GopaySDKException

            assertEquals("$cause", GopayErrorCodes.PAYMENT_PROCESSING_FAILED, failure.errorCode)
            assertSame(cause, failure.cause)
        }
    }

    @Test
    fun `only a missing or outdated Google Play service counts as unavailable`() {
        assertTrue(GooglePayBridge.isUnavailable(CommonStatusCodes.API_NOT_CONNECTED))
        assertTrue(GooglePayBridge.isUnavailable(CommonStatusCodes.SERVICE_DISABLED))
        assertTrue(GooglePayBridge.isUnavailable(CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED))
        assertFalse(GooglePayBridge.isUnavailable(CommonStatusCodes.DEVELOPER_ERROR))
        assertFalse(GooglePayBridge.isUnavailable(CommonStatusCodes.INTERNAL_ERROR))
        assertFalse(GooglePayBridge.isUnavailable(null))
    }
}
