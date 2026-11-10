package cz.gopay.sdk.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flag behind `ON_SUBMIT`: the form's own without a host value, the host's with one, as the
 * iOS form reads its `submitAttempted` binding.
 */
class SubmitAttemptedFlagTest {

    private val changes = mutableListOf<Boolean>()
    private val onChange: (Boolean) -> Unit = { changes += it }

    @Test
    fun `without a host value the form keeps the flag and still reports its changes`() {
        val flag = SubmitAttemptedFlag()
        assertFalse(flag.value(host = null))

        flag.set(true, host = null, onChange = onChange)
        assertTrue(flag.value(host = null))

        flag.set(false, host = null, onChange = onChange)
        assertFalse(flag.value(host = null))
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun `without a callback the form still keeps its own flag`() {
        val flag = SubmitAttemptedFlag()

        flag.set(true, host = null, onChange = null)

        assertTrue(flag.value(host = null))
    }

    @Test
    fun `the host's value is the flag, and the SDK asks for a change through the callback`() {
        val flag = SubmitAttemptedFlag()
        var host = false

        // submitCardForm: the SDK raises it, the host stores what it is told.
        flag.set(true, host, onChange = { host = it })
        assertTrue(flag.value(host))

        // The fields are emptied: the SDK lowers it.
        flag.set(false, host, onChange = { host = it })
        assertFalse(flag.value(host))
    }

    @Test
    fun `the host raises and lowers it itself`() {
        val flag = SubmitAttemptedFlag()

        assertTrue("raised by the host before any submit", flag.value(host = true))
        assertFalse("and lowered again", flag.value(host = false))
    }

    @Test
    fun `a host that does not store the change keeps the flag as it passed it`() {
        val flag = SubmitAttemptedFlag()

        flag.set(true, host = false, onChange = onChange)

        assertEquals(listOf(true), changes)
        assertFalse("the host's value stays the flag", flag.value(host = false))
    }

    @Test
    fun `the callback hears only a change of the flag the form shows`() {
        val flag = SubmitAttemptedFlag()

        flag.set(false, host = null, onChange = onChange)
        flag.set(true, host = true, onChange = onChange)
        flag.set(true, host = null, onChange = onChange)

        assertTrue("already false, already true as the host has it, already true", changes.isEmpty())
    }

    @Test
    fun `a flag parked across a rotation comes back, and goes when the form leaves`() {
        val flag = SubmitAttemptedFlag(initial = true)
        assertTrue(flag.value(host = null))

        flag.forget()

        assertFalse(flag.value(host = null))
    }
}
