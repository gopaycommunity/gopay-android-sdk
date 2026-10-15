package cz.gopay.sdk.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card form sets FLAG_SECURE on its window while it is shown, and must leave the window as it
 * found it: off again after the last form leaves, still on when the host had set it.
 */
class SecureFlagHoldsTest {

    private class FakeWindow(override var isSecure: Boolean = false) : SecureFlagHolds.Target {
        val changes = mutableListOf<Boolean>()
        override val key: Any get() = this
        override fun applySecure(secure: Boolean) {
            isSecure = secure
            changes += secure
        }
    }

    private val holds = SecureFlagHolds()

    @Test
    fun `the form sets the flag and takes it off when it leaves`() {
        val window = FakeWindow()

        holds.acquire(window)
        assertTrue(window.isSecure)

        holds.release(window)
        assertFalse(window.isSecure)
        assertEquals(listOf(true, false), window.changes)
    }

    @Test
    fun `a window the host had secured stays secured`() {
        val window = FakeWindow(isSecure = true)

        holds.acquire(window)
        holds.release(window)

        assertTrue(window.isSecure)
        assertTrue(window.changes.isEmpty())
    }

    @Test
    fun `two forms in one window keep the flag until the last one leaves`() {
        val window = FakeWindow()

        holds.acquire(window)
        holds.acquire(window)
        holds.release(window)
        assertTrue(window.isSecure)

        holds.release(window)
        assertFalse(window.isSecure)
    }

    @Test
    fun `windows are counted apart and a stray release changes nothing`() {
        val sheet = FakeWindow()
        val activity = FakeWindow()

        holds.acquire(sheet)
        holds.release(activity)
        holds.acquire(activity)
        holds.release(sheet)

        assertFalse(sheet.isSecure)
        assertTrue(activity.isSecure)
    }
}
