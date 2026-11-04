package cz.gopay.sdk.telemetry

import cz.gopay.sdk.telemetry.CardFormProgress.Code
import cz.gopay.sdk.telemetry.CardFormProgress.Ended
import cz.gopay.sdk.telemetry.CardFormProgress.Field
import cz.gopay.sdk.telemetry.CardFormProgress.FieldError
import cz.gopay.sdk.telemetry.CardFormProgress.Progress
import cz.gopay.sdk.telemetry.CardFormProgress.State
import cz.gopay.sdk.telemetry.CardFormProgress.Step
import cz.gopay.sdk.telemetry.CardFormProgress.Trigger
import cz.gopay.sdk.ui.CardFormData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The card form steps: the tracker, ported from the browser SDK with its tests, the form's own
 * rules for which errors count, and the guard that keeps every value but a state, a code, a
 * count or a time out of the params.
 */
class CardFormProgressTest {

    private fun progress(pan: State, expiry: State, cvv: State) = Progress(pan, expiry, cvv)

    private val e = State.EMPTY
    private val p = State.PARTIAL
    private val c = State.COMPLETE

    private fun errors(vararg entries: Pair<Field, Code>) = entries.map { FieldError(it.first, it.second) }

    // The tracker, as the browser SDK tests it.

    @Test
    fun `marks a start that arrived whole as filled at once`() {
        val tracker = CardFormProgress()
        val started = tracker.progress(progress(c, e, e), 10)!!

        assertEquals(Step.STARTED, started.step)
        assertEquals(mapOf("pan" to "complete", "expiry" to "empty", "cvv" to "empty", "filled_at_once" to true), started.params)
        assertEquals("started with autofill or paste", started.message)
    }

    @Test
    fun `does not call digits typed one by one a fill at once`() {
        val tracker = CardFormProgress()
        val started = tracker.progress(progress(p, e, e), 10)!!
        assertNull(tracker.progress(progress(c, p, e), 20))
        assertNull(tracker.progress(progress(c, c, p), 30))

        assertEquals(false, started.params["filled_at_once"])
        assertEquals("started typing", started.message)
        assertEquals(false, tracker.summary(Ended.UNMOUNT).params["filled_at_once"])
    }

    @Test
    fun `reports the card completed once, with how long it took from the start`() {
        val tracker = CardFormProgress()
        tracker.progress(progress(p, e, e), 100)
        val completed = tracker.validity(true, 500)!!
        assertNull(tracker.validity(false, 550))
        assertNull(tracker.validity(true, 550))

        assertEquals(Step.COMPLETED, completed.step)
        assertEquals(mapOf("since_started_ms" to 400L), completed.params)
        assertEquals("card complete", completed.message)
    }

    @Test
    fun `reports field errors only for a submit or a problem that was not showing`() {
        val tracker = CardFormProgress()
        val reports = listOf(
            tracker.fieldErrors(errors(Field.PAN to Code.PATTERN), Trigger.BLUR),
            // The same problem again, and then fixed: nothing new went wrong.
            tracker.fieldErrors(errors(Field.PAN to Code.PATTERN), Trigger.INPUT),
            tracker.fieldErrors(errors(), Trigger.INPUT),
            // Every submit is an attempt to pay, the same errors or not.
            tracker.fieldErrors(errors(Field.PAN to Code.PATTERN, Field.CVV to Code.REQUIRED), Trigger.SUBMIT),
            tracker.fieldErrors(errors(Field.PAN to Code.PATTERN, Field.CVV to Code.REQUIRED), Trigger.SUBMIT),
            tracker.fieldErrors(errors(Field.CVV to Code.REQUIRED), Trigger.INPUT),
            tracker.fieldErrors(errors(Field.CVV to Code.REQUIRED, Field.EXPIRY to Code.PATTERN), Trigger.INPUT),
            tracker.fieldErrors(errors(), Trigger.SUBMIT)
        ).filterNotNull()

        assertEquals(
            listOf(
                mapOf("errors" to "pan:pattern", "raised" to "pan:pattern", "count" to 1, "trigger" to "blur") to "new field errors",
                mapOf("errors" to "pan:pattern,cvv:required", "raised" to "pan:pattern,cvv:required", "count" to 2, "trigger" to "submit") to "submit errors",
                mapOf("errors" to "pan:pattern,cvv:required", "raised" to "", "count" to 2, "trigger" to "submit") to "submit errors",
                mapOf("errors" to "cvv:required,expiry:pattern", "raised" to "expiry:pattern", "count" to 2, "trigger" to "input") to "new field errors",
                mapOf("errors" to "", "raised" to "", "count" to 0, "trigger" to "submit") to "submit ok"
            ),
            reports.map { it.params to it.message }
        )
    }

    @Test
    fun `does not report the absence of errors the form started with`() {
        val tracker = CardFormProgress()
        assertNull(tracker.fieldErrors(errors(), Trigger.BLUR))
        assertNull(tracker.fieldErrors(errors(), Trigger.INPUT))
    }

    @Test
    fun `reports a submit with the validity it was asked on`() {
        val tracker = CardFormProgress()
        val first = tracker.submitRequest()
        tracker.validity(true, 10)
        val second = tracker.submitRequest()

        assertEquals(
            listOf(
                mapOf("valid" to false) to "pay pressed with incomplete card",
                mapOf("valid" to true) to "pay pressed"
            ),
            listOf(first, second).map { it.params to it.message }
        )
    }

    @Test
    fun `summarises the visit with the counts and timings of all of it`() {
        val tracker = CardFormProgress()
        tracker.progress(Progress.UNTOUCHED, 0)
        tracker.fieldErrors(errors(Field.PAN to Code.REQUIRED, Field.EXPIRY to Code.REQUIRED, Field.CVV to Code.REQUIRED), Trigger.SUBMIT)
        tracker.progress(progress(c, c, p), 1_000)
        tracker.fieldErrors(errors(Field.CVV to Code.PATTERN), Trigger.BLUR)
        tracker.submitRequest()
        tracker.progress(progress(c, c, c), 2_000)
        tracker.validity(true, 2_000)
        tracker.validity(false, 2_100)
        tracker.validity(true, 2_200)
        tracker.submitRequest()
        val summary = tracker.summary(Ended.UNMOUNT)

        assertEquals("unmount: completed", summary.message)
        assertEquals(Step.INTERACTION, summary.step)
        assertEquals(
            mapOf(
                "started_ms" to 1_000L,
                "filled_at_once" to true,
                "became_valid" to true,
                "valid_at_end" to true,
                "first_valid_ms" to 2_000L,
                "validity_flips" to 1,
                "submit_requests" to 2,
                "invalid_submit_requests" to 1,
                "invalid_submits" to 1,
                "errors_raised" to 4,
                "error_codes_seen" to "cvv:pattern,cvv:required,expiry:required,pan:required",
                "progress_at_end" to "pan:complete,expiry:complete,cvv:complete",
                "ended" to "unmount"
            ),
            summary.params
        )
    }

    @Test
    fun `still calls a form that only ever reported an empty submit never started`() {
        val tracker = CardFormProgress()
        tracker.progress(Progress.UNTOUCHED, 0)
        tracker.fieldErrors(errors(Field.PAN to Code.REQUIRED), Trigger.SUBMIT)

        assertEquals("unmount: never started", tracker.summary(Ended.UNMOUNT).message)
    }

    // The form's states and errors.

    @Test
    fun `describes each field by its state, a full number that fails its check as partial`() {
        assertEquals(Progress.UNTOUCHED, CardFormData().progress())
        assertEquals(progress(p, p, p), CardFormData("4", "1", "1").progress())
        // Sixteen digits that fail the Luhn check, a month 13, a year gone by.
        assertEquals(progress(p, p, c), CardFormData("4444444444444444", "1328", "123").progress())
        assertEquals(p, CardFormData("", "1220", "").progress().expiry)
        assertEquals(progress(c, c, c), CardFormData("4444444444444448", "1228", "123").progress())
    }

    @Test
    fun `calls an empty field required and any other incomplete one pattern, in the form's order`() {
        assertEquals(
            errors(Field.PAN to Code.PATTERN, Field.CVV to Code.REQUIRED),
            CardFormProgress.errorsOf(progress(p, p, e), setOf(Field.CVV, Field.PAN))
        )
        assertEquals(emptyList<FieldError>(), CardFormProgress.errorsOf(progress(c, e, p), setOf(Field.PAN)))
    }

    // The guard.

    @Test
    fun `lets through every params and message a step can send`() {
        val tracker = CardFormProgress()
        val reports = listOfNotNull(
            tracker.progress(progress(c, p, e), 1),
            tracker.validity(true, 2),
            tracker.fieldErrors(errors(Field.PAN to Code.PATTERN, Field.EXPIRY to Code.REQUIRED), Trigger.INPUT),
            tracker.fieldErrors(errors(Field.CVV to Code.REQUIRED), null),
            tracker.submitRequest(),
            CardFormProgress().summary(Ended.ENCRYPT_ERROR),
            tracker.summary(Ended.SUBMIT)
        )
        assertEquals(7, reports.size)
        reports.forEach {
            CardFormProgress.checkParams(it.params)
            CardFormProgress.checkMessage(it.message)
        }
    }

    @Test
    fun `refuses a value of the card, a length or a key it does not know`() {
        val planted = listOf(
            mapOf("pan" to "4444444444444448"),
            mapOf("cvv" to "123"),
            mapOf("expiry" to 1228),
            mapOf("errors" to "pan:4444444444444448"),
            mapOf("raised" to "pan:required,cvv:123"),
            mapOf("error_codes_seen" to "4444"),
            mapOf("progress_at_end" to "pan:16"),
            mapOf("progress_at_end" to ""),
            mapOf("count" to "16"),
            mapOf("count" to -1),
            mapOf("since_started_ms" to "400"),
            mapOf("trigger" to "keyup"),
            mapOf("ended" to "leave"),
            mapOf("valid" to "true"),
            mapOf("pan_length" to 16),
            mapOf("card_number" to "4444444444444448")
        )
        for (params in planted) {
            assertThrows("$params", IllegalArgumentException::class.java) { CardFormProgress.checkParams(params) }
        }
        for (message in listOf("started typing 4444444444444448", "leave: completed", "unmount: 16 digits", "")) {
            assertThrows(message, IllegalArgumentException::class.java) { CardFormProgress.checkMessage(message) }
        }
    }

    // One mounted form.

    private var clock = 0L
    private val sent = mutableListOf<Triple<Step, Long?, CardFormProgress.Report>>()

    /** What the form posts to the next frame; run by [nextFrame]. */
    private val frame = mutableListOf<() -> Unit>()
    private fun record(report: CardFormProgress.Report, ms: Long?) {
        sent.add(Triple(report.step, ms, report))
    }
    private val steps = CardFormSteps(nanoTime = { clock }, defer = { frame.add(it) }, send = ::record)

    private fun nextFrame() {
        while (frame.isNotEmpty()) frame.removeAt(0)()
    }

    private fun at(ms: Long) {
        clock = ms * 1_000_000
    }

    private val names get() = sent.map { it.first.value }
    private val summaries get() = sent.filter { it.first == Step.INTERACTION }.map { it.third }
    private fun card(pan: String = "", exp: String = "", cvv: String = "") = CardFormData(pan, exp, cvv).progress()
    private fun type(field: Field, pan: String = "", exp: String = "", cvv: String = "") =
        steps.input(field) { card(pan, exp, cvv) }
    private fun leave(field: Field, pan: String = "", exp: String = "", cvv: String = "") {
        steps.blurred(field) { card(pan, exp, cvv) }
        nextFrame()
    }

    @Test
    fun `an empty submit, a paste and a pay read submit errors, started, completed, a clean submit and the summary`() {
        steps.ready()
        at(1_000)
        steps.submitRequested()
        steps.submitted { card() }
        at(3_000)
        // A pasted number, then the rest typed.
        type(Field.PAN, "4444444444444448")
        type(Field.EXPIRY, "4444444444444448", "1")
        type(Field.EXPIRY, "4444444444444448", "12")
        type(Field.EXPIRY, "4444444444444448", "1228")
        type(Field.CVV, "4444444444444448", "1228", "1")
        at(5_000)
        type(Field.CVV, "4444444444444448", "1228", "123")
        at(6_000)
        steps.submitRequested()
        steps.submitted { card("4444444444444448", "1228", "123") }
        steps.encrypted()
        // The SDK empties the form after the JWE, and the form then leaves untouched: one summary.
        steps.emptied()
        steps.unmounted()

        assertEquals(
            listOf(
                "submit-request", "field-errors", "started", "field-errors", "field-errors", "completed",
                "submit-request", "field-errors", "interaction"
            ),
            names
        )
        val reports = sent.map { it.third }
        assertEquals("pay pressed with incomplete card", reports[0].message)
        assertEquals("submit errors", reports[1].message)
        assertEquals("pan:required,expiry:required,cvv:required", reports[1].params["errors"])
        assertEquals(true, reports[2].params["filled_at_once"])
        // After a submit every field counts: starting the expiry and the CVV turns required into pattern.
        assertEquals(listOf("expiry:pattern", "cvv:pattern"), listOf(reports[3], reports[4]).map { it.params["raised"] })
        assertEquals(listOf(Trigger.INPUT, Trigger.INPUT), listOf(reports[3], reports[4]).map { it.trigger })
        assertEquals(mapOf("since_started_ms" to 2_000L), reports[5].params)
        assertEquals("pay pressed", reports[6].message)
        assertEquals("submit ok", reports[7].message)
        assertEquals(Trigger.SUBMIT, reports[7].trigger)
        assertEquals("submit: completed", reports[8].message)
        assertEquals("submit", reports[8].params["ended"])
        assertEquals(1, reports[8].params["invalid_submits"])
        assertEquals(0, reports[8].params["validity_flips"])
        // The card as it was when the JWE was made, not the emptied form.
        assertEquals("pan:complete,expiry:complete,cvv:complete", reports[8].params["progress_at_end"])
        assertEquals(true, reports[8].params["valid_at_end"])
        assertEquals(listOf(1_000L, 1_000L, 3_000L, 3_000L, 3_000L, 5_000L, 6_000L, 6_000L, 6_000L), sent.map { it.second })
    }

    @Test
    fun `a second card typed into the form after a declined one is a visit of its own`() {
        steps.ready()
        type(Field.PAN, "4444444444444448", "1228", "123")
        steps.submitRequested()
        steps.submitted { card("4444444444444448", "1228", "123") }
        steps.encrypted()
        steps.emptied()
        // Declined: the customer types another card into the emptied form.
        at(10_000)
        type(Field.PAN, "4")
        at(12_000)
        type(Field.PAN, "4444444444444448", "1228", "123")
        steps.submitRequested()
        steps.submitted { card("4444444444444448", "1228", "123") }
        steps.encrypted()
        steps.emptied()
        steps.unmounted()

        assertEquals(
            listOf(
                "started", "completed", "submit-request", "field-errors", "interaction",
                "started", "completed", "submit-request", "field-errors", "interaction"
            ),
            names
        )
        val second = summaries[1]
        assertEquals("submit: completed", second.message)
        assertEquals(10_000L, second.params["started_ms"])
        assertEquals(12_000L, second.params["first_valid_ms"])
        assertEquals(1, second.params["submit_requests"])
        // Typed from the emptied form: the new visit does not start from the old card.
        assertEquals("started typing", sent[5].third.message)
    }

    @Test
    fun `a new visit after the JWE is summarised at the unmount once something happened in it`() {
        steps.ready()
        type(Field.PAN, "4444444444444448", "1228", "123")
        steps.encrypted()
        steps.emptied()
        type(Field.PAN, "44")
        steps.unmounted()

        assertEquals(listOf("submit: completed", "unmount: not completed"), summaries.map { it.message })
    }

    @Test
    fun `a form left untouched, or only submitted empty, never started, with every field empty at the end`() {
        steps.ready()
        steps.submitRequested()
        steps.submitted { card() }
        steps.unmounted()

        val summary = summaries.single()
        assertEquals("unmount: never started", summary.message)
        assertEquals("pan:empty,expiry:empty,cvv:empty", summary.params["progress_at_end"])
        assertEquals(1, summary.params["invalid_submits"])
    }

    @Test
    fun `starting to type and leaving reads started and the summary, typing through raises nothing`() {
        steps.ready()
        at(400)
        steps.focused(Field.PAN)
        type(Field.PAN, "4")
        type(Field.PAN, "44")
        type(Field.PAN, "4444444444444448")
        // The form moves the focus on by itself once the number is complete.
        leave(Field.PAN, "4444444444444448")
        steps.focused(Field.EXPIRY)
        type(Field.EXPIRY, "4444444444444448", "1")
        at(900)
        steps.unmounted()
        steps.unmounted()

        assertEquals(listOf("started", "interaction"), names)
        assertEquals("started typing", sent[0].third.message)
        assertEquals(400L, sent[0].second)
        assertEquals("unmount: not completed", sent[1].third.message)
        assertEquals("pan:complete,expiry:partial,cvv:empty", sent[1].third.params["progress_at_end"])
        assertEquals(900L, sent[1].second)
    }

    @Test
    fun `a field left half done raises pattern on leaving it, a field never typed in nothing`() {
        steps.ready()
        steps.focused(Field.CVV)
        leave(Field.CVV)
        steps.focused(Field.PAN)
        type(Field.PAN, "444")
        leave(Field.PAN, "444")
        // Back to it and fixed: an error going away is not news.
        steps.focused(Field.PAN)
        type(Field.PAN, "4444444444444448")
        leave(Field.PAN, "4444444444444448")
        // Typed in and emptied again, then left: required.
        steps.focused(Field.EXPIRY)
        type(Field.EXPIRY, "4444444444444448", "1")
        type(Field.EXPIRY, "4444444444444448", "")
        leave(Field.EXPIRY, "4444444444444448", "")

        val errors = sent.filter { it.first == Step.FIELD_ERRORS }.map { it.third.params }
        assertEquals(
            listOf(
                mapOf("errors" to "pan:pattern", "raised" to "pan:pattern", "count" to 1, "trigger" to "blur"),
                mapOf("errors" to "expiry:required", "raised" to "expiry:required", "count" to 1, "trigger" to "blur")
            ),
            errors
        )
    }

    @Test
    fun `a field the form takes away in the frame it leaves is no blur`() {
        steps.ready()
        steps.focused(Field.PAN)
        type(Field.PAN, "444")
        // The focus goes with the form, which leaves before the next frame.
        steps.blurred(Field.PAN) { card("444") }
        steps.unmounted()
        nextFrame()

        assertEquals(listOf("started", "interaction"), names)
        assertEquals(0, summaries.single().params["errors_raised"])
    }

    @Test
    fun `a field left while the window loses the focus is no blur, one left in a focused window is`() {
        var windowFocused = true
        val windowed = CardFormSteps(
            nanoTime = { clock },
            defer = { frame.add(it) },
            hasWindowFocus = { windowFocused },
            send = ::record
        )
        windowed.ready()
        windowed.focused(Field.PAN)
        windowed.input(Field.PAN) { card("444") }
        // The host clears the focus and navigates, or a sheet closes: no focus by the next frame.
        windowed.blurred(Field.PAN) { card("444") }
        windowFocused = false
        nextFrame()
        assertEquals(listOf("started"), names)
        // Back in a focused window, the customer leaves the field for another one.
        windowFocused = true
        windowed.focused(Field.PAN)
        windowed.blurred(Field.PAN) { card("444") }
        nextFrame()

        assertEquals(
            listOf("pan:pattern" to Trigger.BLUR),
            sent.filter { it.first == Step.FIELD_ERRORS }.map { it.third.params["raised"] to it.third.trigger }
        )
    }

    @Test
    fun `a failed encryption is how the visit ended only when nothing was typed after it`() {
        steps.ready()
        type(Field.PAN, "4444444444444448", "1228", "123")
        steps.submitRequested()
        steps.submitted { card("4444444444444448", "1228", "123") }
        steps.encryptFailed()
        steps.unmounted()
        assertEquals("encrypt-error: completed", summaries.single().message)

        sent.clear()
        val edited = CardFormSteps(nanoTime = { clock }, send = ::record)
        edited.ready()
        edited.input(Field.PAN) { card("4444444444444448", "1228", "123") }
        edited.encryptFailed()
        // The customer changed the card after the failure and left.
        edited.input(Field.CVV) { card("4444444444444448", "1228", "12") }
        edited.unmounted()
        assertEquals("unmount: completed", summaries.single().message)

        sent.clear()
        val retried = CardFormSteps(nanoTime = { clock }, send = ::record)
        retried.ready()
        retried.input(Field.PAN) { card("4444444444444448", "1228", "123") }
        retried.encryptFailed()
        retried.encrypted()
        retried.unmounted()
        assertEquals("submit: completed", summaries.single().message)
    }

    @Test
    fun `a form the integrator empties is untouched again, with no flip and its errors raised anew`() {
        steps.ready()
        type(Field.PAN, "4444444444444448", "1228", "123")
        steps.submitRequested()
        // clearCardFormData while the customer is still on the form.
        steps.emptied()
        steps.submitRequested()
        steps.submitted { card() }
        steps.emptied()
        steps.submitted { card() }
        steps.unmounted()

        val reports = sent.map { it.third }
        assertEquals(listOf(true, false), reports.filter { it.step == Step.SUBMIT_REQUEST }.map { it.params["valid"] })
        val required = "pan:required,expiry:required,cvv:required"
        assertEquals(
            listOf(required, required),
            reports.filter { it.step == Step.FIELD_ERRORS }.map { it.params["raised"] }
        )
        val summary = summaries.single()
        assertEquals(0, summary.params["validity_flips"])
        assertEquals("pan:empty,expiry:empty,cvv:empty", summary.params["progress_at_end"])
        assertEquals(false, summary.params["valid_at_end"])
    }

    @Test
    fun `a form the SDK emptied counts no field until it is typed in and left again`() {
        steps.ready()
        type(Field.PAN, "444")
        steps.submitRequested()
        steps.submitted { card("444") }
        steps.emptied()
        type(Field.PAN, "4")
        steps.submitRequested()
        steps.unmounted()

        val reports = sent.map { it.third }
        // Both presses on an incomplete card, the second after the SDK emptied the form.
        assertEquals(listOf(false, false), reports.filter { it.step == Step.SUBMIT_REQUEST }.map { it.params["valid"] })
        // Typing again after the reset raises no required for the fields not touched since: the
        // submit's errors are the only ones.
        assertEquals(
            listOf("pan:pattern,expiry:required,cvv:required" to Trigger.SUBMIT),
            reports.filter { it.step == Step.FIELD_ERRORS }.map { it.params["errors"] to it.trigger }
        )
    }

    @Test
    fun `never lets a failure out of a step, sending or reading the fields`() {
        var attempts = 0
        val failing = CardFormSteps(nanoTime = { clock }) { _, _ ->
            attempts++
            throw IllegalStateException("no telemetry")
        }
        failing.ready()
        failing.input(Field.PAN) { card("4444444444444448") }
        failing.input(Field.EXPIRY) { throw IllegalStateException("no state") }
        failing.submitRequested()
        failing.submitted { card("4444444444444448") }
        failing.unmounted()

        // started, the press, the submit's errors and the summary were each attempted and swallowed.
        assertEquals(4, attempts)
    }
}
