package cz.gopay.sdk.telemetry

import kotlin.math.roundToLong

/**
 * What the customer did with one visit to a card form, kept for the operational data only. The
 * browser SDK's form progress, step for step and message for message, so one query reads both;
 * it mirrors that code line for line, its dead branches included (a form that never reports its
 * progress, an error report without a trigger), which this form never takes. Mirrors the iOS
 * SDK's `CardFormProgress`.
 *
 * The form reports every change, each field moving between states, the errors growing and
 * shrinking as the customer types, validity flipping, and almost none of it is news. What is:
 * that they started, that a problem appeared, that they tried to pay, that the card became
 * complete. Those are the steps this reports; everything else only feeds the summary, which
 * answers the same questions in one event for when the steps did not all arrive.
 *
 * Each report carries its params and a short overview of the moment. Every input is a state, a
 * code or a count. Nothing here sees a keystroke. Not thread-safe: [CardFormSteps] guards it.
 */
internal class CardFormProgress {

    enum class Field(val value: String) { PAN("pan"), EXPIRY("expiry"), CVV("cvv") }

    /**
     * How far the customer has got with a field: nothing in it, something that is not yet a
     * value the form accepts, or one it does. A state, never a length or a digit count:
     * [PARTIAL] covers one digit and a full number that fails its check alike.
     */
    enum class State(val value: String) { EMPTY("empty"), PARTIAL("partial"), COMPLETE("complete") }

    /** Why a field fails: [REQUIRED] when it is empty, [PATTERN] for anything else. */
    enum class Code(val value: String) { REQUIRED("required"), PATTERN("pattern") }

    /** What ran the validation behind the errors: a submit, typing in a field, or leaving one. */
    enum class Trigger(val value: String) { SUBMIT("submit"), INPUT("input"), BLUR("blur") }

    /**
     * How a visit ended: the JWE was made ([SUBMIT], before the SDK empties the form), the last
     * submit failed to encrypt and nothing was typed after ([ENCRYPT_ERROR]), or the form left
     * the screen otherwise ([UNMOUNT]).
     */
    enum class Ended(val value: String) { UNMOUNT("unmount"), ENCRYPT_ERROR("encrypt-error"), SUBMIT("submit") }

    /** `PAYMENT_CARD.<step>`. */
    enum class Step(val value: String) {
        STARTED("started"),
        COMPLETED("completed"),
        FIELD_ERRORS("field-errors"),
        SUBMIT_REQUEST("submit-request"),
        INTERACTION("interaction")
    }

    /** The overview a step sends as `return_value`; the summary's is `<ended>: <Outcome>`. */
    enum class Message(val text: String) {
        STARTED_WHOLE("started with autofill or paste"),
        STARTED_TYPING("started typing"),
        CARD_COMPLETE("card complete"),
        NEW_FIELD_ERRORS("new field errors"),
        SUBMIT_ERRORS("submit errors"),
        SUBMIT_OK("submit ok"),
        PAY_PRESSED("pay pressed"),
        PAY_PRESSED_INCOMPLETE("pay pressed with incomplete card")
    }

    /** How far the visit got, in the summary's message. */
    enum class Outcome(val text: String) {
        COMPLETED("completed"),
        NOT_COMPLETED("not completed"),
        NEVER_STARTED("never started")
    }

    /** The state of all three fields. */
    data class Progress(val pan: State, val expiry: State, val cvv: State) {
        operator fun get(field: Field): State = when (field) {
            Field.PAN -> pan
            Field.EXPIRY -> expiry
            Field.CVV -> cvv
        }

        /** The whole card is one the form accepts. */
        val isComplete: Boolean get() = Field.values().all { get(it) == State.COMPLETE }

        companion object {
            /** The form before anyone touches it. */
            val UNTOUCHED = Progress(State.EMPTY, State.EMPTY, State.EMPTY)
        }
    }

    data class FieldError(val field: Field, val code: Code) {
        val key: String get() = pair(this.field, code.value)
    }

    /**
     * What one step sends: the params for queries, and an overview for people (`return_value`).
     * [trigger] is what raised a `field-errors`, which picks its budget; null for every other step.
     */
    class Report(val step: Step, val params: Map<String, Any?>, val message: String, val trigger: Trigger? = null)

    private var progress: Progress? = null
    private var started = false
    private var startedMs: Long? = null
    private var filledAtOnce = false
    private var valid = false
    private var becameValid = false
    private var firstValidMs: Long? = null
    private var validityFlips = 0
    private var submitRequests = 0
    private var invalidSubmitRequests = 0
    private var invalidSubmits = 0
    private var errorsRaised = 0

    /** `field:code` of the errors on show; the form starts with none. */
    private var shown: Set<String> = emptySet()
    private val codesSeen = HashSet<String>()

    /**
     * `started`: the first report of a field that is no longer empty, once per visit, with the
     * states it found. Null for every other change: those are typing, and only the state at the
     * end is kept.
     */
    fun progress(next: Progress, sinceReady: Long?): Report? {
        // Straight from empty to complete is a value that arrived whole: an autofill or a paste,
        // never digits typed one by one.
        val previous = progress ?: Progress.UNTOUCHED
        val jumped = Field.values().any { previous[it] == State.EMPTY && next[it] == State.COMPLETE }
        filledAtOnce = filledAtOnce || jumped
        progress = next
        if (started || Field.values().all { next[it] == State.EMPTY }) return null
        started = true
        startedMs = sinceReady
        return Report(
            Step.STARTED,
            linkedMapOf(
                "pan" to next.pan.value,
                "expiry" to next.expiry.value,
                "cvv" to next.cvv.value,
                "filled_at_once" to jumped
            ),
            (if (jumped) Message.STARTED_WHOLE else Message.STARTED_TYPING).text
        )
    }

    /**
     * `completed`: the first time the whole card is valid, once per visit. A card edited back to
     * invalid and completed again is counted, not reported.
     */
    fun validity(nextValid: Boolean, sinceReady: Long?): Report? {
        if (valid && !nextValid) validityFlips += 1
        valid = nextValid
        if (!nextValid || becameValid) return null
        becameValid = true
        firstValidMs = sinceReady
        val sinceStarted = firstValidMs?.let { first -> startedMs?.let { first - it } }
        return Report(Step.COMPLETED, linkedMapOf("since_started_ms" to sinceStarted), Message.CARD_COMPLETE.text)
    }

    /**
     * `field-errors`, or null when nothing went wrong that was not already showing. A submit is
     * always reported, clean or not: it is an attempt to pay. Otherwise only an error that was
     * not on show before. Errors going away while the customer fixes them are progress, not
     * problems.
     */
    fun fieldErrors(errors: List<FieldError>, trigger: Trigger?): Report? {
        val current = errors.map { it.key }.toCollection(LinkedHashSet())
        val raised = current.filter { it !in shown }
        shown = current
        errorsRaised += raised.size
        codesSeen.addAll(raised)
        if (trigger == Trigger.SUBMIT && errors.isNotEmpty()) invalidSubmits += 1
        if (trigger != Trigger.SUBMIT && raised.isEmpty()) return null
        val message = when {
            trigger != Trigger.SUBMIT -> Message.NEW_FIELD_ERRORS
            errors.isNotEmpty() -> Message.SUBMIT_ERRORS
            else -> Message.SUBMIT_OK
        }
        return Report(
            Step.FIELD_ERRORS,
            linkedMapOf(
                "errors" to describeErrors(errors),
                "raised" to raised.joinToString(","),
                "count" to errors.size,
                "trigger" to trigger?.value
            ),
            message.text,
            trigger
        )
    }

    /** `submit-request`: the integrator's pay button, `submitCardForm`. */
    fun submitRequest(): Report {
        submitRequests += 1
        if (!valid) invalidSubmitRequests += 1
        return Report(
            Step.SUBMIT_REQUEST,
            linkedMapOf("valid" to valid),
            (if (valid) Message.PAY_PRESSED else Message.PAY_PRESSED_INCOMPLETE).text
        )
    }

    /**
     * The integrator emptied the form in the middle of the visit (`clearCardFormData`): the
     * fields are untouched again, the card is not valid, and no error is on show, so one that
     * comes back is reported again. Not a validity flip: the customer did not edit the card. Not
     * in the browser SDK, whose page cannot empty the frame; the iOS SDK does the same.
     */
    fun cleared() {
        progress = Progress.UNTOUCHED
        valid = false
        shown = emptySet()
    }

    /** `interaction`: the whole visit, ended the way [ended] says. */
    fun summary(ended: Ended): Report {
        val outcome = when {
            becameValid -> Outcome.COMPLETED
            started || (progress == null && errorsRaised > 0) -> Outcome.NOT_COMPLETED
            else -> Outcome.NEVER_STARTED
        }
        return Report(
            Step.INTERACTION,
            linkedMapOf(
                "started_ms" to startedMs,
                "filled_at_once" to filledAtOnce,
                "became_valid" to becameValid,
                "valid_at_end" to valid,
                "first_valid_ms" to firstValidMs,
                // Valid and then not any more: a card edited after it was complete.
                "validity_flips" to validityFlips,
                "submit_requests" to submitRequests,
                "invalid_submit_requests" to invalidSubmitRequests,
                // A submit with the card incomplete, whether or not the form showed the errors.
                "invalid_submits" to invalidSubmits,
                "errors_raised" to errorsRaised,
                "error_codes_seen" to codesSeen.sorted().joinToString(","),
                "progress_at_end" to progress?.let(::describeProgress),
                "ended" to ended.value
            ),
            summaryMessage(ended, outcome)
        )
    }

    companion object {

        /** `pan:required`, `cvv:complete`: a field and one of its values, the one shape both take. */
        private fun pair(field: Field, value: String): String = "${field.value}:$value"

        private fun summaryMessage(ended: Ended, outcome: Outcome): String = "${ended.value}: ${outcome.text}"

        /**
         * The errors the fields in [fields] show in [progress], in the form's order: an empty field
         * is [Code.REQUIRED], one that is not complete [Code.PATTERN].
         */
        fun errorsOf(progress: Progress, fields: Set<Field>): List<FieldError> =
            Field.values().filter { it in fields }.mapNotNull { field ->
                when (progress[field]) {
                    State.EMPTY -> FieldError(field, Code.REQUIRED)
                    State.PARTIAL -> FieldError(field, Code.PATTERN)
                    State.COMPLETE -> null
                }
            }

        /** `pan:required,cvv:pattern`: one string, since a param is a primitive. */
        private fun describeErrors(errors: List<FieldError>): String = errors.joinToString(",") { it.key }

        /** `pan:complete,expiry:partial,cvv:empty`, in the form's own order. */
        private fun describeProgress(progress: Progress): String =
            Field.values().joinToString(",") { pair(it, progress[it].value) }

        private val states = State.values().map { it.value }.toSet()
        private val triggers = Trigger.values().map { it.value }.toSet()
        private val endings = Ended.values().map { it.value }.toSet()
        private val errorKeys = Field.values().flatMap { f -> Code.values().map { pair(f, it.value) } }.toSet()
        private val progressKeys = Field.values().flatMap { f -> State.values().map { pair(f, it.value) } }.toSet()
        private val messages = Message.values().map { it.text }.toSet() +
            Ended.values().flatMap { e -> Outcome.values().map { summaryMessage(e, it) } }

        private fun isState(value: Any?) = value is String && value in states
        private fun isFlag(value: Any?) = value is Boolean
        private fun isCount(value: Any?) = value is Int && value >= 0
        private fun isMs(value: Any?) = value == null || value is Long
        private fun isList(value: Any?, members: Set<String>) =
            value is String && (value.isEmpty() || value.split(',').all { it in members })

        /** What each key of a step's params may hold. Nothing else is ever sent. */
        private val allowed: Map<String, (Any?) -> Boolean> = mapOf(
            "pan" to ::isState,
            "expiry" to ::isState,
            "cvv" to ::isState,
            "filled_at_once" to ::isFlag,
            "since_started_ms" to ::isMs,
            "errors" to { v -> isList(v, errorKeys) },
            "raised" to { v -> isList(v, errorKeys) },
            "count" to ::isCount,
            "trigger" to { v -> v == null || (v is String && v in triggers) },
            "valid" to ::isFlag,
            "started_ms" to ::isMs,
            "became_valid" to ::isFlag,
            "valid_at_end" to ::isFlag,
            "first_valid_ms" to ::isMs,
            "validity_flips" to ::isCount,
            "submit_requests" to ::isCount,
            "invalid_submit_requests" to ::isCount,
            "invalid_submits" to ::isCount,
            "errors_raised" to ::isCount,
            "error_codes_seen" to { v -> isList(v, errorKeys) },
            // never empty: a summary always names the state of all three fields
            "progress_at_end" to { v -> v == null || (isList(v, progressKeys) && (v as String).isNotEmpty()) },
            "ended" to { v -> v is String && v in endings }
        )

        /**
         * [params] when every key is one a step sends and every value one that key may hold: a
         * state, a code, a trigger, a flag, a count or a time. Throws otherwise, so a value of the
         * card that found its way in drops the event instead of reaching the log.
         */
        fun checkParams(params: Map<String, Any?>): Map<String, Any?> {
            for ((key, value) in params) {
                val check = allowed[key] ?: throw IllegalArgumentException("card form param $key")
                require(check(value)) { "card form param $key out of range" }
            }
            return params
        }

        /** [message] when it is one a step sends; throws otherwise, like [checkParams]. */
        fun checkMessage(message: String): String {
            require(message in messages) { "card form message out of range" }
            return message
        }
    }
}

/**
 * The card form steps of one mounted [cz.gopay.sdk.ui.PaymentCardForm]: its current visit's
 * [CardFormProgress], its ready, and which fields count for the errors, sent through the latest
 * SDK instance.
 *
 * A visit ends when the JWE is made (its summary goes out then, with the card as it was, before
 * the SDK empties the form) or when the form leaves the screen. When the form stays and the SDK
 * empties it after the JWE, the next visit starts: a customer typing a second card after a
 * declined one gets their own `started` and their own summary. A visit nobody touched after that
 * reset sends no summary.
 *
 * The errors are the form's own rules, whatever its `validation` mode shows: a field counts once
 * the customer typed in it and left it, and every field counts from a submit on. So a card typed
 * through in one go raises nothing, and a field left half done raises `pattern` on leaving it.
 * Leaving a field is reported a frame late through [defer], and not at all when the form left the
 * screen in that frame, or when its window has no focus by then: a form going away takes the focus
 * with it a moment before it leaves, a host clears the focus before it navigates, a sheet as it
 * closes.
 *
 * The form calls in from the main thread, `submitCardForm` from wherever it runs; one lock keeps
 * the order. The fields' states are read inside it too, so every failure, reading them included,
 * is swallowed: this never reaches the form or the payment.
 */
internal class CardFormSteps(
    private val nanoTime: () -> Long = System::nanoTime,
    private val defer: (() -> Unit) -> Unit = { it() },
    private val hasWindowFocus: () -> Boolean = { true },
    private val send: (CardFormProgress.Report, Long?) -> Unit = { report, durationMs ->
        Telemetry.current?.cardFormStep(report, durationMs)
    }
) {
    private var tracker = CardFormProgress()
    private var readyAt: Long? = null
    private var reported = CardFormProgress.Progress.UNTOUCHED
    private val focused = HashSet<CardFormProgress.Field>()
    private val edited = HashSet<CardFormProgress.Field>()
    private val counted = HashSet<CardFormProgress.Field>()
    private var ended = CardFormProgress.Ended.UNMOUNT

    /** The JWE was made and the visit summarised; the SDK has yet to empty the form. */
    private var visitOver = false

    /** Whether the visit saw any input or submit; the first one is summarised regardless. */
    private var visitTouched = true
    private var unmounted = false

    /** Whole milliseconds since the form's ready, null before it. */
    private fun sinceReady(): Long? = readyAt?.let { ((nanoTime() - it) / 1_000_000.0).roundToLong() }

    private inline fun guarded(block: () -> Unit) {
        try {
            synchronized(this) { if (!unmounted) block() }
        } catch (e: Throwable) {
            // Logging is never worth a thrown error in a payment flow.
        }
    }

    private fun emit(report: CardFormProgress.Report?) {
        if (report != null) send(report, sinceReady())
    }

    /** A visit starts on an untouched form: one left alone is `never started`, its fields `empty`. */
    private fun prime() {
        tracker.progress(CardFormProgress.Progress.UNTOUCHED, sinceReady())
        tracker.validity(false, sinceReady())
    }

    /** The form is on screen and empty: what every step is timed from. */
    fun ready() = guarded {
        readyAt = nanoTime()
        prime()
    }

    /** The fields now hold [progress]; reported only when a state changed. */
    private fun report(progress: CardFormProgress.Progress, trigger: CardFormProgress.Trigger?) {
        if (progress == reported) return
        reported = progress
        emit(tracker.progress(progress, sinceReady()))
        emit(tracker.validity(progress.isComplete, sinceReady()))
        if (trigger != null) emit(tracker.fieldErrors(CardFormProgress.errorsOf(progress, counted), trigger))
    }

    /** The customer typed in [field], leaving the fields at [progress]. */
    fun input(field: CardFormProgress.Field, progress: () -> CardFormProgress.Progress) = guarded {
        if (visitOver) return@guarded
        visitTouched = true
        // Typing after a failed encryption: the visit no longer ends with that failure.
        ended = CardFormProgress.Ended.UNMOUNT
        edited.add(field)
        report(progress(), CardFormProgress.Trigger.INPUT)
    }

    /** [field] gained the focus. */
    fun focused(field: CardFormProgress.Field) = guarded { focused.add(field) }

    /** [field] lost the focus, the fields then at [progress]; reported a frame late, see the class. */
    fun blurred(field: CardFormProgress.Field, progress: () -> CardFormProgress.Progress) = guarded {
        if (!focused.remove(field)) return@guarded
        defer {
            guarded {
                if (visitOver || !hasWindowFocus()) return@guarded
                if (field in edited) counted.add(field)
                emit(tracker.fieldErrors(CardFormProgress.errorsOf(progress(), counted), CardFormProgress.Trigger.BLUR))
            }
        }
    }

    /**
     * The SDK emptied the fields. After the JWE that starts the next visit; otherwise it is the
     * integrator's `clearCardFormData`, and the visit goes on with nothing typed and nothing
     * counted.
     */
    fun emptied() = guarded {
        edited.clear()
        counted.clear()
        reported = CardFormProgress.Progress.UNTOUCHED
        if (visitOver) {
            tracker = CardFormProgress()
            visitOver = false
            visitTouched = false
            ended = CardFormProgress.Ended.UNMOUNT
            prime()
        } else {
            tracker.cleared()
        }
    }

    /** `submitCardForm` was called, before the card is checked. */
    fun submitRequested() = guarded {
        if (visitOver) return@guarded
        visitTouched = true
        emit(tracker.submitRequest())
    }

    /** The submit found the fields at [progress]: every field counts from here on. */
    fun submitted(progress: () -> CardFormProgress.Progress) = guarded {
        if (visitOver) return@guarded
        val found = progress()
        counted.addAll(CardFormProgress.Field.values())
        report(found, trigger = null)
        emit(tracker.fieldErrors(CardFormProgress.errorsOf(found, counted), CardFormProgress.Trigger.SUBMIT))
    }

    /** The submit failed to encrypt; the card stays in the form and can be submitted again. */
    fun encryptFailed() = guarded { if (!visitOver) ended = CardFormProgress.Ended.ENCRYPT_ERROR }

    /** The JWE exists and the form is about to be emptied: the visit ends, with the card as it is. */
    fun encrypted() = guarded {
        if (visitOver) return@guarded
        visitOver = true
        emit(tracker.summary(CardFormProgress.Ended.SUBMIT))
    }

    /** The form left the screen: the visit ends unless it already did, or nobody touched it. */
    fun unmounted() = guarded {
        unmounted = true
        if (!visitOver && visitTouched) emit(tracker.summary(ended))
    }
}
