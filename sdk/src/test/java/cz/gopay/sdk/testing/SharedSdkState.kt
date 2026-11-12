package cz.gopay.sdk.testing

import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.telemetry.Telemetry
import cz.gopay.sdk.ui.ForegroundScreen
import cz.gopay.sdk.ui.GooglePayBridge
import cz.gopay.sdk.ui.PaymentVerificationBridge
import cz.gopay.sdk.ui.SdkScreens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import org.junit.rules.ExternalResource

/**
 * Part of the state the SDK keeps once per process, which every test class in the JVM therefore
 * shares, and no more than this: the two bridges to the SDK's own screens, the screen in front,
 * how the screens are started and how long they are waited for, the main dispatcher, the
 * telemetry of the SDK initialized last and the error callback. The SDK singleton, the locales,
 * the card form store and the telemetry's HTTP override and counters are not touched here; a
 * class that changes those puts them back itself.
 *
 * A class that touches any of the listed state takes this rule:
 *
 *     @get:Rule val sharedState = SharedSdkState()
 *
 * It empties all of it before each test and after it, around the class's own `@Before` and
 * `@After`. Before a test it first looks at what it is about to empty: anything found there was
 * left by a test that ran earlier, in this class or another, and the test fails naming it. A
 * leak then fails where it is found, with its cause, rather than as a wait that never ends in
 * whichever test the leak happens to reach. Whether a test set the main dispatcher cannot be
 * read, so that one is only reset.
 *
 * Nothing here waits. A test that starts work on a thread of its own ends that thread itself,
 * see [drain]; what the thread left behind is what the next test's rule reports.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedSdkState : ExternalResource() {

    override fun before() {
        val left = leftBehind()
        clean()
        if (left.isNotEmpty()) {
            throw AssertionError(
                "An earlier test left the SDK's shared state behind: " + left.joinToString("; ")
            )
        }
    }

    override fun after() = clean()

    private fun clean() {
        PaymentVerificationBridge.reset()
        GooglePayBridge.reset()
        ForegroundScreen.reset()
        ForegroundScreen.afterCurrentCallback = afterCurrentCallback
        SdkScreens.showTimeoutMs = SHOW_TIMEOUT_MS
        SdkScreens.startVerification = startVerification
        SdkScreens.startGooglePay = startGooglePay
        Dispatchers.resetMain()
        Telemetry.current = null
        ErrorReporter.clear()
    }

    companion object {
        /** The limit of `SdkScreens.showTimeoutMs` in production. */
        private const val SHOW_TIMEOUT_MS = 30_000L

        // The functions the SDK ships with, taken when the rule's class is loaded, before its
        // first test. A test that replaced one before that is caught by leftBehind(), by the
        // class of the function.
        private val afterCurrentCallback = ForegroundScreen.afterCurrentCallback
        private val startVerification = SdkScreens.startVerification
        private val startGooglePay = SdkScreens.startGooglePay

        /**
         * What an earlier test left in the shared state, each item by name; empty when it is as
         * a fresh process has it.
         */
        internal fun leftBehind(): List<String> = buildList {
            if (PaymentVerificationBridge.currentOwner() != null) {
                add("a 3DS verification still holds PaymentVerificationBridge")
            }
            if (googlePayIsHeld()) add("a Google Pay request still holds GooglePayBridge")
            if (ForegroundScreen.appContext != null) add("ForegroundScreen still has a Context")
            if (ForegroundScreen.current() != null) add("ForegroundScreen still has a screen in front")
            if (!isTheSdksOwn(ForegroundScreen.afterCurrentCallback)) {
                add("ForegroundScreen.afterCurrentCallback is a test's")
            }
            if (SdkScreens.showTimeoutMs != SHOW_TIMEOUT_MS) {
                add("SdkScreens.showTimeoutMs is ${SdkScreens.showTimeoutMs}")
            }
            if (!isTheSdksOwn(SdkScreens.startVerification)) add("SdkScreens.startVerification is a test's")
            if (!isTheSdksOwn(SdkScreens.startGooglePay)) add("SdkScreens.startGooglePay is a test's")
            if (Telemetry.current != null) add("Telemetry.current is set")
            if (ErrorReporter.isEnabled()) add("ErrorReporter still has an error callback")
        }

        /**
         * Whether a request holds `GooglePayBridge`, asked the way a caller asks: the bridge
         * hands out an owner only while it is free, and the one handed out here is given back.
         */
        private fun googlePayIsHeld(): Boolean {
            val owner = GooglePayBridge.register(CompletableDeferred()) ?: return true
            GooglePayBridge.clear(owner)
            return false
        }

        /**
         * Whether a replaceable function is the one the SDK ships with: a lambda is a class of
         * the file it is written in, so the SDK's own live in the SDK's `ui` package and a
         * test's does not.
         */
        private fun isTheSdksOwn(function: Any): Boolean =
            function.javaClass.name.startsWith("cz.gopay.sdk.ui.") && !function.javaClass.name.contains("Test")
    }
}
