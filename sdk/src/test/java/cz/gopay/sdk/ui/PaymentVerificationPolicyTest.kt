package cz.gopay.sdk.ui

import android.content.ComponentName
import android.content.Intent
import cz.gopay.sdk.GopaySDK
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
                url = GopaySDK.CHARGE_RETURN_URL,
                scheme = "https",
                isForMainFrame = true
            )
        )
    }

    @Test
    fun `the return URL is an https address and still never loads as a page`() {
        // The return URL is https because the gateway accepts nothing else as a return_url,
        // and https is the first scheme the WebView would otherwise load. The prefix has to win.
        assertEquals("https://gopay.com/sdk/charge-return", GopaySDK.CHARGE_RETURN_URL)
        assertEquals(
            Navigation.COMPLETE,
            PaymentVerificationPolicy.navigationFor(
                url = "https://gopay.com/sdk/charge-return",
                scheme = "https",
                isForMainFrame = true
            )
        )
    }

    @Test
    fun `the return URL is recognised by prefix whatever the gateway appends`() {
        // Same test as iOS: url.hasPrefix(chargeReturnURL). The ACS comes back with whatever
        // query or fragment the gateway attached, and a subframe counts as much as the main one.
        listOf(
            "https://gopay.com/sdk/charge-return?id=123&state=PAID",
            "https://gopay.com/sdk/charge-return/",
            "https://gopay.com/sdk/charge-return#done"
        ).forEach { url ->
            assertEquals(
                "$url must complete the challenge",
                Navigation.COMPLETE,
                PaymentVerificationPolicy.navigationFor(
                    url, scheme = "https", isForMainFrame = true
                )
            )
        }
        assertEquals(
            Navigation.COMPLETE,
            PaymentVerificationPolicy.navigationFor(
                url = "https://gopay.com/sdk/charge-return?id=123",
                scheme = "https",
                isForMainFrame = false
            )
        )
    }

    private val merchantReturnUrl = "https://shop.example.com/gopay/return"

    @Test
    fun `the return URL the charge response carried completes the challenge`() {
        listOf(
            merchantReturnUrl,
            "$merchantReturnUrl?id=123&state=PAID"
        ).forEach { url ->
            assertEquals(
                "$url must complete the challenge",
                Navigation.COMPLETE,
                PaymentVerificationPolicy.navigationFor(
                    url, scheme = "https", isForMainFrame = true, returnUrl = merchantReturnUrl
                )
            )
        }
    }

    @Test
    fun `without a return URL the SDK constant completes the challenge`() {
        assertEquals(
            Navigation.COMPLETE,
            PaymentVerificationPolicy.navigationFor(
                GopaySDK.CHARGE_RETURN_URL,
                scheme = "https",
                isForMainFrame = true,
                returnUrl = null
            )
        )
        assertEquals(
            Navigation.LOAD,
            PaymentVerificationPolicy.navigationFor(
                merchantReturnUrl, scheme = "https", isForMainFrame = true, returnUrl = null
            )
        )
    }

    @Test
    fun `the return URL from the response wins over the SDK constant`() {
        // The ACS comes back only to the address the payment was created with, so the constant
        // is just another page here.
        assertEquals(
            Navigation.LOAD,
            PaymentVerificationPolicy.navigationFor(
                GopaySDK.CHARGE_RETURN_URL,
                scheme = "https",
                isForMainFrame = true,
                returnUrl = merchantReturnUrl
            )
        )
    }

    @Test
    fun `an unusable return URL falls back to the SDK constant`() {
        // Blank, or a scheme with no host once parsed, would match every page of the challenge
        // as a prefix and close it as answered on its first navigation. The iOS cases, plus
        // HTTPS:// in upper case.
        listOf(
            "",
            "   ",
            "gopaysdk://charge-return",
            "https://",
            "HTTPS://",
            "shop.example.com/return",
            "https://:443",
            "https://user@/r",
            "https://@/r"
        ).forEach { returnUrl ->
            assertEquals(
                "'$returnUrl' must fall back to the constant",
                GopaySDK.CHARGE_RETURN_URL,
                PaymentVerificationPolicy.completionUrlFor(returnUrl)
            )
            assertEquals(
                Navigation.COMPLETE,
                PaymentVerificationPolicy.navigationFor(
                    GopaySDK.CHARGE_RETURN_URL,
                    scheme = "https",
                    isForMainFrame = true,
                    returnUrl = returnUrl
                )
            )
            assertEquals(
                "'$returnUrl' must not complete an ordinary page",
                Navigation.LOAD,
                PaymentVerificationPolicy.navigationFor(
                    "https://3ds.example.com/challenge",
                    scheme = "https",
                    isForMainFrame = true,
                    returnUrl = returnUrl
                )
            )
        }
    }

    @Test
    fun `a return URL in another case matches the navigation the WebView reports`() {
        // The WebView reports scheme and host in lower case, so the return URL is compared in
        // the same canonical form.
        assertEquals(
            "https://shop.example/return",
            PaymentVerificationPolicy.completionUrlFor("HTTPS://Shop.Example/return")
        )
        assertEquals(
            Navigation.COMPLETE,
            PaymentVerificationPolicy.navigationFor(
                "https://shop.example/return?id=1",
                scheme = "https",
                isForMainFrame = true,
                returnUrl = "HTTPS://Shop.Example/return"
            )
        )
        assertEquals(
            merchantReturnUrl,
            PaymentVerificationPolicy.completionUrlFor(merchantReturnUrl)
        )
    }

    @Test
    fun `a usable return URL keeps its user info and loses whitespace and the default port`() {
        assertEquals(
            "https://user@shop.example/r",
            PaymentVerificationPolicy.completionUrlFor("https://user@Shop.Example/r")
        )
        assertEquals(
            "https://shop.example/r",
            PaymentVerificationPolicy.completionUrlFor("https://Shop.Example:443/r")
        )
        // HttpUrl trims surrounding whitespace, so a trailing space does not make the address
        // unusable; it is taken without it.
        assertEquals(
            "https://shop.example/r",
            PaymentVerificationPolicy.completionUrlFor("https://shop.example/r ")
        )
        assertEquals(
            Navigation.COMPLETE,
            PaymentVerificationPolicy.navigationFor(
                "https://shop.example/r?id=1",
                scheme = "https",
                isForMainFrame = true,
                returnUrl = "https://shop.example/r "
            )
        )
    }

    @Test
    fun `an international host is matched in the punycode the WebView reports`() {
        assertEquals(
            "https://obchod.xn--z-cia/r",
            PaymentVerificationPolicy.completionUrlFor("https://obchod.čz/r")
        )
        assertEquals(
            Navigation.COMPLETE,
            PaymentVerificationPolicy.navigationFor(
                "https://obchod.xn--z-cia/r?id=1",
                scheme = "https",
                isForMainFrame = true,
                returnUrl = "https://obchod.čz/r"
            )
        )
    }

    @Test
    fun `another page on the return host is an ordinary page`() {
        listOf(
            "https://gopay.com/sdk/other",
            "https://gopay.com/",
            "https://example.com/?next=https://gopay.com/sdk/charge-return"
        ).forEach { url ->
            assertEquals(
                "$url must load as a page of the challenge",
                Navigation.LOAD,
                PaymentVerificationPolicy.navigationFor(
                    url, scheme = "https", isForMainFrame = true
                )
            )
        }
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
                PaymentVerificationPolicy.navigationFor("$scheme:x", scheme, isForMainFrame = true)
            )
        }
    }

    @Test
    fun `a web scheme is recognised whatever its case`() {
        assertEquals(
            Navigation.LOAD,
            PaymentVerificationPolicy.navigationFor(
                "HTTPS://3ds.example.com/c", "HTTPS", isForMainFrame = true
            )
        )
        assertEquals(
            Navigation.LOAD,
            PaymentVerificationPolicy.navigationFor(
                "JavaScript:void(0)", "JavaScript", isForMainFrame = true
            )
        )
    }

    @Test
    fun `a banking app scheme is handed to the system`() {
        // The schemes European ACS actually redirect to.
        listOf("intent", "bankid", "csob", "tel", "mailto").forEach { scheme ->
            assertEquals(
                "$scheme must go to the system",
                Navigation.HAND_OFF,
                PaymentVerificationPolicy.navigationFor(
                    "$scheme://auth", scheme, isForMainFrame = true
                )
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
                PaymentVerificationPolicy.navigationFor(
                    "$scheme://auth", scheme, isForMainFrame = false
                )
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
            PaymentVerificationPolicy.navigationFor("auth/x", null, isForMainFrame = true)
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
