package cz.gopay.sdk.ui

import android.content.ComponentName
import android.content.Intent
import cz.gopay.sdk.ui.PaymentVerificationPolicy.LoadFailure
import cz.gopay.sdk.ui.PaymentVerificationPolicy.Navigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Pins the rules that decide whether a payment is reported as authorised, abandoned or failed.
 *
 * The WebViewClient that calls these cannot be constructed in a plain JVM unit test, so this
 * covers the decisions and [PaymentVerificationBridgeTest] covers what each one does to the
 * caller.
 */
class PaymentVerificationPolicyTest {

    // Same code as WebViewClient.ERROR_UNSUPPORTED_SCHEME, spelled out because android.jar is
    // stubbed here.
    private val errorUnsupportedScheme = -10
    private val errorHostLookup = -2

    @Test
    fun `the return URL completes the challenge`() {
        assertEquals(
            Navigation.COMPLETE,
            PaymentVerificationPolicy.navigationFor(
                scheme = PaymentVerificationPolicy.RETURN_URL_SCHEME,
                isForMainFrame = true
            )
        )
    }

    @Test
    fun `web pages stay in the WebView`() {
        // The seven the SDK treats as the page's own, iOS included. about, data, blob, file and
        // javascript address the challenge's own content or the browser it runs in, so asking
        // another application to open them is never right.
        listOf("http", "https", "about", "data", "blob", "file", "javascript").forEach { scheme ->
            assertEquals(
                "$scheme must stay in the WebView",
                Navigation.LOAD,
                PaymentVerificationPolicy.navigationFor(scheme, isForMainFrame = true)
            )
        }
    }

    @Test
    fun `a web scheme is recognised whatever its case`() {
        assertEquals(
            Navigation.LOAD,
            PaymentVerificationPolicy.navigationFor("HTTPS", isForMainFrame = true)
        )
        assertEquals(
            Navigation.LOAD,
            PaymentVerificationPolicy.navigationFor("JavaScript", isForMainFrame = true)
        )
    }

    @Test
    fun `a banking app scheme is handed to the system`() {
        // The schemes European ACS actually redirect to.
        listOf("intent", "bankid", "csob", "tel", "mailto").forEach { scheme ->
            assertEquals(
                "$scheme must go to the system",
                Navigation.HAND_OFF,
                PaymentVerificationPolicy.navigationFor(scheme, isForMainFrame = true)
            )
        }
    }

    @Test
    fun `an iframe cannot send the user out of the payment`() {
        // Leaving for another application is the page navigating itself away, which only the
        // main frame does. An iframe the ACS embedded would otherwise be able to fire
        // startActivity on its own and throw the user out mid-payment.
        listOf("intent", "bankid", "csob", "tel", "mailto").forEach { scheme ->
            assertEquals(
                "$scheme from a subframe must stay with the WebView",
                Navigation.LOAD,
                PaymentVerificationPolicy.navigationFor(scheme, isForMainFrame = false)
            )
        }
        // And the WebView then declines it quietly rather than failing the challenge.
        assertEquals(
            LoadFailure.IGNORE,
            PaymentVerificationPolicy.loadFailureFor(
                isForMainFrame = false,
                errorCode = errorUnsupportedScheme,
                hasHandedOffNavigation = false,
                hasRenderedChallenge = false
            )
        )
    }

    @Test
    fun `a navigation without a scheme is handed over rather than loaded`() {
        assertEquals(
            Navigation.HAND_OFF,
            PaymentVerificationPolicy.navigationFor(null, isForMainFrame = true)
        )
    }

    @Test
    fun `a subresource failure is not the challenge failing`() {
        assertEquals(
            LoadFailure.IGNORE,
            PaymentVerificationPolicy.loadFailureFor(
                isForMainFrame = false,
                errorCode = errorHostLookup,
                hasHandedOffNavigation = false,
                hasRenderedChallenge = false
            )
        )
        assertEquals(
            LoadFailure.IGNORE,
            PaymentVerificationPolicy.failureFor(
                isForMainFrame = false,
                hasRenderedChallenge = false
            )
        )
    }

    @Test
    fun `a main frame failure before the challenge draws is unreachable`() {
        assertEquals(
            LoadFailure.REPORT_UNREACHABLE,
            PaymentVerificationPolicy.loadFailureFor(
                isForMainFrame = true,
                errorCode = errorHostLookup,
                hasHandedOffNavigation = false,
                hasRenderedChallenge = false
            )
        )
        assertEquals(
            LoadFailure.REPORT_UNREACHABLE,
            PaymentVerificationPolicy.failureFor(
                isForMainFrame = true,
                hasRenderedChallenge = false
            )
        )
    }

    @Test
    fun `a main frame failure after the challenge drew is a cancellation`() {
        // The user answered and the ACS is redirecting back; only the charge state knows whether
        // the payment went through, so this must not read as a challenge that never loaded.
        assertEquals(
            LoadFailure.CANCEL,
            PaymentVerificationPolicy.loadFailureFor(
                isForMainFrame = true,
                errorCode = errorHostLookup,
                hasHandedOffNavigation = false,
                hasRenderedChallenge = true
            )
        )
        assertEquals(
            LoadFailure.CANCEL,
            PaymentVerificationPolicy.failureFor(
                isForMainFrame = true,
                hasRenderedChallenge = true
            )
        )
    }

    @Test
    fun `an unsupported scheme after a hand-off is never reported`() {
        assertEquals(
            LoadFailure.IGNORE,
            PaymentVerificationPolicy.loadFailureFor(
                isForMainFrame = true,
                errorCode = errorUnsupportedScheme,
                hasHandedOffNavigation = true,
                hasRenderedChallenge = false
            )
        )
        assertEquals(
            LoadFailure.IGNORE,
            PaymentVerificationPolicy.loadFailureFor(
                isForMainFrame = true,
                errorCode = errorUnsupportedScheme,
                hasHandedOffNavigation = false,
                hasRenderedChallenge = true
            )
        )
    }

    @Test
    fun `an unsupported scheme before any hand-off is the redirect URL failing`() {
        // shouldOverrideUrlLoading is not called for the URL handed to loadUrl, so this is the
        // challenge itself not loading. Ignoring it left the caller suspended for the life of
        // the process and every later verification failing on PAYMENT_008.
        assertEquals(
            LoadFailure.REPORT_UNREACHABLE,
            PaymentVerificationPolicy.loadFailureFor(
                isForMainFrame = true,
                errorCode = errorUnsupportedScheme,
                hasHandedOffNavigation = false,
                hasRenderedChallenge = false
            )
        )
    }

    @Test
    fun `an unclaimed hand-off is the challenge failing only before it draws`() {
        assertEquals(
            LoadFailure.REPORT_UNREACHABLE,
            PaymentVerificationPolicy.unclaimedHandOffFailure(
                isForMainFrame = true,
                hasRenderedChallenge = false
            )
        )
        // The user is looking at a page they can still answer, and the link was as likely a
        // support tel: as the challenge's next step. Ending the payment over it would be worse
        // than the missing app.
        assertEquals(
            LoadFailure.IGNORE,
            PaymentVerificationPolicy.unclaimedHandOffFailure(
                isForMainFrame = true,
                hasRenderedChallenge = true
            )
        )
        assertEquals(
            LoadFailure.IGNORE,
            PaymentVerificationPolicy.unclaimedHandOffFailure(
                isForMainFrame = false,
                hasRenderedChallenge = false
            )
        )
    }

    @Test
    fun `only an http address is loadable as a challenge`() {
        assertTrue(PaymentVerificationPolicy.isLoadableChallengeUrl("https://3ds.example.com/c"))
        assertTrue(PaymentVerificationPolicy.isLoadableChallengeUrl("HTTP://3ds.example.com/c"))
        assertFalse(PaymentVerificationPolicy.isLoadableChallengeUrl(""))
        assertFalse(PaymentVerificationPolicy.isLoadableChallengeUrl("bankid://auth"))
        assertFalse(PaymentVerificationPolicy.isLoadableChallengeUrl("javascript:alert(1)"))
    }

    @Test
    fun `a hand-off intent keeps nothing the page chose for it`() {
        // parseUri carries an explicit component and a selector straight out of the URL, and the
        // URL came off the network: left on, a challenge page could start any activity of the
        // host app. parseUri itself is not mockable against the stubbed android.jar, so this
        // covers the stripping, which is the part that decides what leaves the SDK.
        val parsed = mock<Intent>()

        PaymentVerificationPolicy.sanitizedHandOff(parsed)

        verify(parsed).component = null as ComponentName?
        verify(parsed).selector = null
        verify(parsed).flags = Intent.FLAG_ACTIVITY_NEW_TASK
        verify(parsed).addCategory(Intent.CATEGORY_BROWSABLE)
    }

    @Test
    fun `an intent URL names the page to use when no app takes it`() {
        val url = "intent://auth/x#Intent;scheme=bankid;package=cz.bank;" +
            "S.browser_fallback_url=https%3A%2F%2F3ds.example.com%2Ffallback;end"

        assertEquals(
            "https://3ds.example.com/fallback",
            PaymentVerificationPolicy.fallbackUrlFor(url)
        )
    }

    @Test
    fun `a hand-off without a usable fallback offers none`() {
        assertNull(PaymentVerificationPolicy.fallbackUrlFor("bankid://auth"))
        assertNull(
            PaymentVerificationPolicy.fallbackUrlFor("intent://auth/x#Intent;scheme=bankid;end")
        )
        // A fallback that is itself a hand-off would put the WebView back where it started.
        assertNull(
            PaymentVerificationPolicy.fallbackUrlFor(
                "intent://auth/x#Intent;scheme=bankid;S.browser_fallback_url=bankid%3A%2F%2Fy;end"
            )
        )
    }
}
