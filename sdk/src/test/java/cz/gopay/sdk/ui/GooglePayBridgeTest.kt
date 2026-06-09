package cz.gopay.sdk.ui

import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePayBridgeTest {

    @After
    fun tearDown() {
        GooglePayBridge.clear()
    }

    @Test
    fun `register returns true when bridge is empty`() {
        val d = CompletableDeferred<Result<String>>()
        assertTrue(GooglePayBridge.register(d))
    }

    @Test
    fun `register returns false when deferred already registered`() {
        val d1 = CompletableDeferred<Result<String>>()
        val d2 = CompletableDeferred<Result<String>>()
        GooglePayBridge.register(d1)
        assertFalse(GooglePayBridge.register(d2))
    }

    @Test
    fun `complete delivers token JSON as success result`() {
        val d = CompletableDeferred<Result<String>>()
        GooglePayBridge.register(d)
        GooglePayBridge.complete("""{"protocolVersion":"ECv2"}""")

        assertTrue(d.isCompleted)
        val result = d.getCompleted()
        assertTrue(result.isSuccess)
        assertEquals("""{"protocolVersion":"ECv2"}""", result.getOrNull())
    }

    @Test
    fun `cancel without cause delivers UserCancelledThrowable`() {
        val d = CompletableDeferred<Result<String>>()
        GooglePayBridge.register(d)
        GooglePayBridge.cancel()

        assertTrue(d.isCompleted)
        val result = d.getCompleted()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GooglePayBridge.UserCancelledThrowable)
    }

    @Test
    fun `cancel with cause delivers that cause as failure`() {
        val d = CompletableDeferred<Result<String>>()
        GooglePayBridge.register(d)
        val cause = Exception("Google Pay error: DEVELOPER_ERROR")
        GooglePayBridge.cancel(cause)

        assertTrue(d.isCompleted)
        val result = d.getCompleted()
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is GooglePayBridge.UserCancelledThrowable)
        assertEquals("Google Pay error: DEVELOPER_ERROR", result.exceptionOrNull()?.message)
    }

    @Test
    fun `clear resets bridge allowing re-registration`() {
        val d1 = CompletableDeferred<Result<String>>()
        GooglePayBridge.register(d1)
        GooglePayBridge.clear()

        val d2 = CompletableDeferred<Result<String>>()
        assertTrue(GooglePayBridge.register(d2))
    }

    @Test
    fun `complete after clear is a no-op`() {
        GooglePayBridge.clear()
        GooglePayBridge.complete("token") // no deferred registered — should not throw
    }

    @Test
    fun `cancel after clear is a no-op`() {
        GooglePayBridge.clear()
        GooglePayBridge.cancel() // no deferred registered — should not throw
    }

    @Test
    fun `second register returns false and original deferred is unchanged`() {
        val d1 = CompletableDeferred<Result<String>>()
        val d2 = CompletableDeferred<Result<String>>()
        GooglePayBridge.register(d1)
        val secondResult = GooglePayBridge.register(d2)

        assertFalse(secondResult)
        assertFalse(d1.isCompleted)
        assertFalse(d2.isCompleted)
    }

    @Test
    fun `complete delivers null for missing token`() {
        val d = CompletableDeferred<Result<String>>()
        GooglePayBridge.register(d)
        GooglePayBridge.complete("abc123")

        val result = d.getCompleted()
        assertNotNull(result.getOrNull())
        assertNull(result.exceptionOrNull())
    }
}
