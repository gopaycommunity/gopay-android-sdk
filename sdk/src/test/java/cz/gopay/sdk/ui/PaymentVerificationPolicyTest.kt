package cz.gopay.sdk.ui

import cz.gopay.sdk.ui.PaymentVerificationPolicy.LoadFailure
import cz.gopay.sdk.ui.PaymentVerificationPolicy.Navigation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the rules that decide whether a payment is reported as authorised, abandoned or failed.
 *
 * The WebViewClient that calls these cannot be constructed in a plain JVM unit test, so this
 * covers the decisions and [PaymentVerificationBridgeTest] covers what each one does to the
 * caller.
 */
class PaymentVerificationPolicyTest {

    @Test
    fun `the return URL completes the challenge`() {
        assertEquals(
            Navigation.COMPLETE,
            PaymentVerificationPolicy.navigationFor(PaymentVerificationPolicy.RETURN_URL_SCHEME)
        )
    }

    @Test
    fun `web pages stay in the WebView`() {
        assertEquals(Navigation.LOAD, PaymentVerificationPolicy.navigationFor("https"))
        assertEquals(Navigation.LOAD, PaymentVerificationPolicy.navigationFor("http"))
    }

    @Test
    fun `a subresource failure is not the challenge failing`() {
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
            PaymentVerificationPolicy.failureFor(
                isForMainFrame = true,
                hasRenderedChallenge = true
            )
        )
    }

}
