package cz.gopay.sdk.exception

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies [ErrorReporter] — the singleton that fans SDK errors out to a host-supplied callback.
 * Each test resets the global callback so state can't leak between cases (or into other suites).
 */
class ErrorReporterTest {

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
    fun `creating an exception reports it to the callback`() {
        val received = mutableListOf<GopaySDKException>()
        ErrorReporter.setErrorCallback { received.add(it) }

        val ex = GopaySDKException("AUTH_001", "boom")

        assertEquals(1, received.size)
        assertSame(ex, received.first())
    }

    @Test
    fun `no callback means report is a no-op`() {
        // No callback configured — must not throw when an exception is created.
        GopaySDKException("NETWORK_002", "boom")
    }

    @Test
    fun `callback exceptions are swallowed`() {
        ErrorReporter.setErrorCallback { throw RuntimeException("callback blew up") }

        // Construction must still succeed despite the misbehaving callback.
        val ex = GopaySDKException("PAYMENT_003", "boom")
        assertEquals("PAYMENT_003", ex.errorCode)
    }

    @Test
    fun `setErrorCallback null disables reporting`() {
        var calls = 0
        ErrorReporter.setErrorCallback { calls++ }
        ErrorReporter.setErrorCallback(null)

        GopaySDKException("AUTH_001", "boom")

        assertEquals(0, calls)
        assertFalse(ErrorReporter.isEnabled())
    }
}
