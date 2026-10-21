package cz.gopay.sdk.ui

import cz.gopay.sdk.model.CardData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The SDK's copy of the forms on screen, which `submitCardForm(formId)` reads. */
@OptIn(ExperimentalCoroutinesApi::class)
class CardFormStoreTest {

    private val a = CardFormData("4532015112830366", "1230", "123")
    private val b = CardFormData("4444444444444448", "1228", "321")
    private val owner = Any()

    @Before
    fun setUp() {
        // The handlers run on the main thread; on the JVM that is this one.
        Dispatchers.setMain(Dispatchers.Unconfined)
        CardFormStore.clear(null)
    }

    @After
    fun clean() {
        CardFormStore.unregister("a", owner)
        CardFormStore.unregister("b", owner)
        Dispatchers.resetMain()
    }

    @Test
    fun `a submit without an id reads the most recently active form`() {
        CardFormStore.update("a", a)
        CardFormStore.update("b", b)
        assertEquals("b" to b, CardFormStore.resolve(null))

        CardFormStore.update("a", a)
        assertEquals("a" to a, CardFormStore.resolve(null))
        assertEquals("b" to b, CardFormStore.resolve("b"))
        assertEquals(listOf("b", "a"), CardFormStore.storedFormIds().toList())
    }

    @Test
    fun `a form that is not on screen resolves to no data`() {
        assertEquals(null to null, CardFormStore.resolve(null))
        assertEquals("x" to null, CardFormStore.resolve("x"))
    }

    @Test
    fun `clearing a form drops its data and empties its fields through the handler`() {
        var resets = 0
        CardFormStore.register("a", owner, CardFormHandlers(reset = { resets++ }, submitAttempted = {}))
        CardFormStore.update("a", a)
        CardFormStore.update("b", b)

        CardFormStore.clear("a")

        assertEquals(1, resets)
        assertEquals(setOf("b"), CardFormStore.storedFormIds())
        assertEquals("the other form is now the most recent", "b" to b, CardFormStore.resolve(null))

        CardFormStore.clear(null)
        assertEquals(2, resets)
        assertTrue(CardFormStore.storedFormIds().isEmpty())
    }

    @Test
    fun `a sync that lands between clear and its reset does not bring the card back`() {
        // The reset hops to the main thread; a keystroke in between syncs the old fields again.
        // The hop drops the data once more before the reset runs, so the store ends empty.
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        var resets = 0
        CardFormStore.register("a", owner, CardFormHandlers(reset = { resets++ }, submitAttempted = {}))
        CardFormStore.update("a", a)

        CardFormStore.clear("a")
        assertTrue("dropped at the call", CardFormStore.storedFormIds().isEmpty())
        CardFormStore.update("a", a)
        assertEquals("the keystroke in between", setOf("a"), CardFormStore.storedFormIds())

        main.scheduler.runCurrent()

        assertEquals(1, resets)
        assertTrue("dropped again before the reset", CardFormStore.storedFormIds().isEmpty())
    }

    @Test
    fun `a form leaving the screen takes its data with it and calls nothing back`() {
        var resets = 0
        CardFormStore.register("a", owner, CardFormHandlers(reset = { resets++ }, submitAttempted = {}))
        CardFormStore.update("a", a)

        CardFormStore.unregister("a", owner)

        assertEquals(0, resets)
        assertNull(CardFormStore.resolve("a").second)
        CardFormStore.clear("a")
        assertEquals("the handler is gone too", 0, resets)
    }

    @Test
    fun `a form leaving takes only its own registration, not a newer form's under the same id`() {
        // A screen transition composes the new form before the old one leaves: the old one's
        // departure must not wipe the new one's data or handlers, or the card would stay on
        // screen after the JWE and ON_SUBMIT would never show.
        val resets = mutableListOf<String>()
        val first = Any()
        val second = Any()
        CardFormStore.register("a", first, CardFormHandlers(reset = { resets += "first" }, submitAttempted = {}))
        CardFormStore.update("a", a)
        CardFormStore.register("a", second, CardFormHandlers(reset = { resets += "second" }, submitAttempted = {}))
        CardFormStore.update("a", b)

        CardFormStore.unregister("a", first)

        assertEquals("the newer form's data stays", "a" to b, CardFormStore.resolve("a"))
        CardFormStore.clear("a")
        assertEquals("and the newer form is the one reset", listOf("second"), resets)
        CardFormStore.unregister("a", second)
        assertNull(CardFormStore.resolve("a").second)
    }

    @Test
    fun `a submit attempt reaches the form it was made for`() {
        val attempted = mutableListOf<String>()
        CardFormStore.register("a", owner, CardFormHandlers(reset = {}, submitAttempted = { attempted += "a" }))
        CardFormStore.register("b", owner, CardFormHandlers(reset = {}, submitAttempted = { attempted += "b" }))

        CardFormStore.submitAttempted("b")
        CardFormStore.submitAttempted(null)
        CardFormStore.submitAttempted("missing")

        assertEquals(listOf("b"), attempted)
    }

    @Test
    fun `the stored digits become the card encryptCardData takes, with the four-digit year`() {
        assertEquals(CardData("4532015112830366", "12", "2030", "123"), a.toCardData())
        assertEquals("12/30", a.formattedExpiration())
        assertTrue(a.isValid)
        assertTrue(CardFormData().isEmpty)
        assertTrue(!CardFormData(cvv = "1").isEmpty)
        assertNull("no expiration parses to no card", CardFormData("4532015112830366", "", "123").toCardData())
    }
}
