package cz.gopay.sdk.exception

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies [ErrorReporter], the singleton that fans SDK errors out to a host-supplied callback.
 * Each test resets the global callback so state can't leak between cases (or into other suites).
 *
 * The rule under test is the one shared with iOS: an error reaches the callback once, where the
 * SDK throws it, and building one reports nothing.
 */
class ErrorReporterTest {

    private val received = mutableListOf<GopaySDKException>()

    @Before
    fun setUp() = ErrorReporter.clear()

    @After
    fun tearDown() = ErrorReporter.clear()

    @Test
    fun `isEnabled reflects whether a callback is set`() {
        assertFalse(ErrorReporter.isEnabled())
        ErrorReporter.setErrorCallback { }
        assertTrue(ErrorReporter.isEnabled())
        ErrorReporter.clear()
        assertFalse(ErrorReporter.isEnabled())
    }

    @Test
    fun `building an exception reports nothing, a host's own never reaches the callback`() {
        ErrorReporter.setErrorCallback { received.add(it) }

        GopaySDKException("AUTH_010", "built by the host")

        assertTrue(received.isEmpty())
    }

    @Test
    fun `reporting an exception hands it to the callback once`() {
        ErrorReporter.setErrorCallback { received.add(it) }
        val ex = GopaySDKException("AUTH_010", "boom")

        ErrorReporter.report(ex)
        // An outer entry point the error passes through reports it again; that reaches nothing.
        ErrorReporter.report(ex)

        assertEquals(1, received.size)
        assertSame(ex, received.first())
    }

    @Test
    fun `reportErrors reports what the block throws and rethrows it unchanged`() {
        ErrorReporter.setErrorCallback { received.add(it) }
        val ex = GopaySDKException("NETWORK_002", "boom")

        val thrown = runCatching { reportErrors<Unit> { throw ex } }.exceptionOrNull()

        assertSame(ex, thrown)
        assertEquals(listOf(ex), received)
    }

    @Test
    fun `nested reportErrors blocks report once`() {
        ErrorReporter.setErrorCallback { received.add(it) }
        val ex = GopaySDKException("AUTH_013", "closed")

        runCatching { reportErrors<Unit> { reportErrors<Unit> { throw ex } } }

        assertEquals(listOf(ex), received)
    }

    @Test
    fun `reportErrors leaves anything but an SDK error alone`() {
        ErrorReporter.setErrorCallback { received.add(it) }
        val cancel = kotlinx.coroutines.CancellationException("host left")

        val thrown = runCatching { reportErrors<Unit> { throw cancel } }.exceptionOrNull()

        assertSame(cancel, thrown)
        assertTrue(received.isEmpty())
        assertEquals("passes the value through", 42, reportErrors { 42 })
    }

    @Test
    fun `a closed payment window is not reported as an error`() {
        ErrorReporter.setErrorCallback { received.add(it) }

        ErrorReporter.report(GopaySDKException(GopayErrorCodes.PAYMENT_CANCELLED, "Google Pay sheet was closed"))

        assertTrue(received.isEmpty())
    }

    @Test
    fun `PAYMENT_TIMEOUT is not reported, PAYMENT_DECLINED is`() {
        ErrorReporter.setErrorCallback { received.add(it) }

        ErrorReporter.report(GopaySDKException(GopayErrorCodes.PAYMENT_TIMEOUT, "Charge did not leave PROCESSING"))
        assertTrue("an undecided charge is not an error", received.isEmpty())

        ErrorReporter.report(GopaySDKException(GopayErrorCodes.PAYMENT_DECLINED, "Charge failed"))
        assertEquals(listOf(GopayErrorCodes.PAYMENT_DECLINED), received.map { it.errorCode })
    }

    @Test
    fun `no callback means report is a no-op`() {
        ErrorReporter.report(GopaySDKException("NETWORK_002", "boom"))
    }

    @Test
    fun `callback exceptions are swallowed`() {
        ErrorReporter.setErrorCallback { throw RuntimeException("callback blew up") }

        // Reporting must still succeed despite the misbehaving callback.
        val ex = GopaySDKException("PAYMENT_003", "boom")
        ErrorReporter.report(ex)
        assertEquals("PAYMENT_003", ex.errorCode)
    }

    @Test
    fun `setErrorCallback null disables reporting`() {
        var calls = 0
        ErrorReporter.setErrorCallback { calls++ }
        ErrorReporter.setErrorCallback(null)

        ErrorReporter.report(GopaySDKException("AUTH_010", "boom"))

        assertEquals(0, calls)
        assertFalse(ErrorReporter.isEnabled())
    }
}
