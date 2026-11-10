package cz.gopay.sdk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.locales.GopayLocaleStrings
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.telemetry.CardFormProgress
import cz.gopay.sdk.telemetry.CardFormSteps
import cz.gopay.sdk.telemetry.GwLoggerTelemetry
import cz.gopay.sdk.telemetry.Telemetry
import cz.gopay.sdk.ui.utils.CardNumberInputValidator
import cz.gopay.sdk.ui.utils.CardNumberMaskedVisualTransformation
import cz.gopay.sdk.ui.utils.CardNumberVisualTransformation
import cz.gopay.sdk.ui.utils.CardValidator
import cz.gopay.sdk.ui.utils.CvvMaskedVisualTransformation
import cz.gopay.sdk.ui.utils.CvvValidator
import cz.gopay.sdk.ui.utils.ExpirationDateVisualTransformation
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Input field configuration for custom labels and helper texts
 */
data class InputFieldConfig(
    val label: String,
    val helperText: String? = null,
    val errorText: String? = null,
    val placeholder: String? = null
)

/**
 * Configuration for all input fields in the payment form
 */
data class PaymentFormInputs(
    val cardNumber: InputFieldConfig = InputFieldConfig(label = "Card Number", placeholder = "1234 1234 1234 1234"),
    val expirationDate: InputFieldConfig = InputFieldConfig(label = "MM/YY", placeholder = "MM/YY"),
    val cvv: InputFieldConfig = InputFieldConfig(label = "CVV", placeholder = "123")
) {
    companion object {
        /**
         * Builds a [PaymentFormInputs] from a [GopayLocaleStrings], populating each field's label
         * and placeholder from the locale. Error / helper texts are left unset: the form's
         * `validation` mode decides whether it renders its own.
         */
        fun from(strings: GopayLocaleStrings): PaymentFormInputs = PaymentFormInputs(
            cardNumber = InputFieldConfig(label = strings.panLabel, placeholder = strings.panPlaceholder),
            expirationDate = InputFieldConfig(label = strings.expLabel, placeholder = strings.expPlaceholder),
            cvv = InputFieldConfig(label = strings.cvvLabel, placeholder = strings.cvvPlaceholder)
        )
    }
}

/**
 * A secure payment card form that handles card data input and JWE encryption.
 *
 * The form never exposes raw card data to the parent — it validates input and encrypts it into a
 * JWE that the parent forwards to its merchant backend. The backend (which holds merchant
 * credentials) then calls `POST /cards/tokens` to obtain the final card token.
 *
 * Submitting: the form keeps the SDK's copy of its fields under [formId], and
 * [GopaySDK.submitCardForm] encrypts that copy into the JWE, the model the iOS card form
 * uses. After a successful submit the SDK's copy is dropped **and the visible fields are
 * emptied**, so the card does not outlive the JWE made from it; a failed encryption leaves
 * everything as the user typed it, since nothing has been authorized yet. The copy also goes
 * when the form leaves the composition, and on demand through [GopaySDK.clearCardFormData].
 *
 * A configuration change, a rotation above all, recreates the activity and the form with it.
 * The form carries a half-typed card across: it parks the card in the SDK's memory while the
 * activity is recreated and the form that comes back takes it over at its first composition,
 * under the same [formId] (a generated one is kept in saved state, the card never is), in a
 * bottom sheet or dialog too. A card nobody takes over is dropped when the user leaves the screen
 * or it is destroyed for good, and with [GopaySDK.clearCardFormData]. After process death the
 * form is empty.
 *
 * Security:
 * - Unless the host app is debuggable, the form sets `FLAG_SECURE` on the window it is drawn in,
 *   a dialog or bottom sheet included, to prevent screen capture, and takes it off when it
 *   leaves. A window that had the flag before keeps it.
 * - Card data is validated locally and JWE-encrypted using the merchant's public key, fetched
 *   from `GET /cards/public-key` with shareable-key auth on every submit. The JWE's issue time
 *   is the gateway's, read from that response's `Date` header, not the device clock. Without a
 *   usable header the device clock is used, with a warning in the debug log, as in
 *   [GopaySDK.encryptCardData].
 * - Unless [allowsAutofill] is on, the fields are declared unfillable to autofill services, with
 *   no hints. The keyboard is asked not to learn from any of them, whatever [allowsAutofill] says.
 *   No field is announced as a password field, where a password manager would offer its logins;
 *   the CVV is masked on screen instead. The card number stays readable to the user and to
 *   screen readers.
 *
 * Localization: field labels and placeholders default to the locale resolved from [locale] /
 * [localeStrings] (falling back to the SDK-wide default, then the device language, then Czech).
 * Pass [inputFields] to override the resolved labels entirely.
 *
 * @param modifier Modifier for the form layout.
 * @param locale Locale code (e.g. `"cs"`, `"de"`) for the field labels. `null` uses the SDK default.
 *              Ignored when [localeStrings] or [inputFields] is supplied.
 * @param localeStrings Explicit locale strings to use, bypassing [locale] resolution.
 * @param inputFields Configuration for input field labels, helper texts, error states. When `null`
 *                    (default) the labels are derived from the resolved locale.
 * @param theme Theme configuration for customizing the form appearance.
 * @param validation Whether and when the form renders localized inline validation errors
 *                   beneath each field, from the resolved locale strings. The default,
 *                   [PaymentCardFormValidationDisplay.HIDDEN], keeps error display host-driven. An
 *                   error [inputFields] sets shows while the form has none of its own to show.
 * @param formId Identifies this form to [GopaySDK.submitCardForm] and
 *               [GopaySDK.clearCardFormData] when several are on screen; generated when null.
 *               A submit without an id reads the most recently active form.
 * @param allowsAutofill Whether autofill services may fill and save the card number and expiry
 *                       date. Off by default. The CVV is never offered to autofill.
 * @param onValidityChange Told whether the card the form holds passes its validation, once when
 *                         the form appears and on every change, so the host can enable its pay
 *                         button; the same the iOS form's `isValid` binding reports. Only the
 *                         form registered under [formId] reports, and `false` as it leaves.
 *                         Not across a configuration change: the form that comes back reports
 *                         the card it took over, and when none comes back within ten
 *                         seconds the card is dropped without a report, so after a rotation
 *                         the host clears a `true` it holds itself. Nothing about the card.
 */
@Composable
fun PaymentCardForm(
    modifier: Modifier = Modifier,
    locale: String? = null,
    localeStrings: GopayLocaleStrings? = null,
    inputFields: PaymentFormInputs? = null,
    theme: PaymentCardFormTheme = PaymentCardFormTheme(),
    validation: PaymentCardFormValidationDisplay = PaymentCardFormValidationDisplay.HIDDEN,
    formId: String? = null,
    allowsAutofill: Boolean = false,
    onValidityChange: ((Boolean) -> Unit)? = null
) {
    theme.WarnWhenTheHostThemeFightsDarkMode()

    val resolvedStrings = localeStrings ?: GopayLocales.resolve(locale)
    val fields = inputFields ?: PaymentFormInputs.from(resolvedStrings)
    // The view the form is drawn in, for FLAG_SECURE on its window, for the card form steps and
    // for the activity a rotation recreates.
    val view = LocalView.current
    // The id the SDK's copy is kept under: the host's, or one minted once for this composition
    // and kept across a configuration change, so the card parked under it finds its way back.
    val resolvedFormId = rememberSaveable(formId) { formId ?: UUID.randomUUID().toString() }
    // The departure below reads whichever id the form carries by then.
    val currentFormId by rememberUpdatedState(resolvedFormId)
    // This composition, as the store knows it: a second form under the same id, composed before
    // this one leaves, must not have its registration taken away by this one's departure.
    val owner = remember { Any() }
    // The card and flags a form under this id parked when the activity was recreated, taken once.
    val restored = remember {
        CardFormStore.takeParked(resolvedFormId, view.context.findActivity()?.let(CardFormStore.Screen::of))
    }
    // Store clean input values (digits only)
    var cardNumberDigits by remember { mutableStateOf(restored?.data?.cardNumberDigits ?: "") }
    var expirationDateDigits by remember { mutableStateOf(restored?.data?.expirationDigits ?: "") }
    var cvv by remember { mutableStateOf(restored?.data?.cvv ?: "") }
    var isCardNumberFocused by remember { mutableStateOf(false) }
    var isExpirationFocused by remember { mutableStateOf(false) }
    var isCvvFocused by remember { mutableStateOf(false) }
    // Whether the user has typed in each field and left it since, for LIVE; whether a submit
    // ran, for ON_SUBMIT and LIVE. A restored field counts as typed in, not as left.
    var cardNumberEdited by remember { mutableStateOf(cardNumberDigits.isNotEmpty()) }
    var expirationEdited by remember { mutableStateOf(expirationDateDigits.isNotEmpty()) }
    var cvvEdited by remember { mutableStateOf(cvv.isNotEmpty()) }
    // Parked with the card across a configuration change, so the errors a submit or a field left
    // behind brought up come back with it. Never in saved state: they would outlive the card, a
    // form a navigation library keeps or process death would show errors on empty fields.
    val restoredFlags = restored?.flags ?: CardFormFlags()
    var cardNumberLeft by remember { mutableStateOf(restoredFlags.cardNumberLeft) }
    var expirationLeft by remember { mutableStateOf(restoredFlags.expirationLeft) }
    var cvvLeft by remember { mutableStateOf(restoredFlags.cvvLeft) }
    var submitAttempted by remember { mutableStateOf(restoredFlags.submitAttempted) }
    // A field losing the focus because the activity is being recreated is not a field left.
    val changingConfigurations = { view.context.findActivity()?.isChangingConfigurations == true }
    val isCardNumberValid = CardValidator.validateCardNumber(cardNumberDigits).isValid

    // What the fields hold right now, as the SDK's copy stores it.
    val snapshot = { CardFormData(cardNumberDigits, expirationDateDigits, cvv) }
    val formData = snapshot()

    // Whether the card passes, told to the host once up front and whenever it flips, by the form
    // registered under the id only, as the iOS form writes its isValid binding. A form another
    // one under the same id has replaced stays silent; the registered one reports false as it
    // leaves, see the registration below.
    val isValid = formData.isValid
    val currentOnValidityChange by rememberUpdatedState(onValidityChange)
    LaunchedEffect(isValid, resolvedFormId) {
        if (CardFormStore.isRegistered(resolvedFormId, owner)) currentOnValidityChange?.invoke(isValid)
    }

    // How far the customer got, for the operational data: told each field's state on every edit
    // and focus change, never what the field holds. Kept for the whole time the form is on screen.
    // A field left is reported a frame late, through the view, and dropped if the form left with it
    // or its window has lost the focus by then.
    val steps = remember {
        CardFormSteps(defer = { block -> view.post { block() } }, hasWindowFocus = { view.hasWindowFocus() })
    }
    val reportInput = { field: CardFormProgress.Field -> steps.input(field) { snapshot().progress() } }
    val reportFocus = { field: CardFormProgress.Field, focused: Boolean ->
        if (focused) steps.focused(field) else steps.blurred(field) { snapshot().progress() }
    }

    // Reset function to clear all form fields. The edited, left and attempted flags go with the
    // digits, so the emptied fields come back pristine instead of lit up with "required".
    val resetForm = {
        cardNumberDigits = ""
        expirationDateDigits = ""
        cvv = ""
        isCardNumberFocused = false
        isExpirationFocused = false
        isCvvFocused = false
        cardNumberEdited = false
        expirationEdited = false
        cvvEdited = false
        cardNumberLeft = false
        expirationLeft = false
        cvvLeft = false
        submitAttempted = false
        steps.emptied()
    }

    // FocusRequesters for each field
    val cardNumberFocusRequester = remember { FocusRequester() }
    val expirationFocusRequester = remember { FocusRequester() }
    val cvvFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // The SDK's copy of the fields, under the form's id, for GopaySDK.submitCardForm. Registered
    // while the form is on screen, with the reset the SDK runs after a successful submit or
    // clearCardFormData, and synced with every edit below. Dropped when the form leaves; a form
    // leaving for a configuration change has parked its card first, see below.
    DisposableEffect(resolvedFormId) {
        CardFormStore.register(
            resolvedFormId,
            owner,
            CardFormHandlers(reset = resetForm, submitAttempted = { submitAttempted = true }, steps = steps)
        )
        CardFormStore.update(resolvedFormId, owner, snapshot())
        onDispose {
            val wasRegistered = CardFormStore.unregister(resolvedFormId, owner)
            // The host's pay button must not stay enabled for a form that is gone. Not for a
            // configuration change: the form that comes back reports the card it took over.
            if (wasRegistered && view.context.findActivity()?.isChangingConfigurations != true) {
                currentOnValidityChange?.invoke(false)
            }
        }
    }

    // Keep the window the form is drawn in, a dialog or bottom sheet included, out of screenshots
    // while the form is there. Not in a debuggable build of the host. See SecureWindow.
    DisposableEffect(view) {
        val protection = if (SecureWindow.shouldProtect(view.context)) {
            SecureWindow.protect(view)
        } else {
            null
        }
        onDispose { protection?.release() }
    }

    // Drop the card-data references when the form leaves the composition, so PAN/CVV don't
    // outlive their use (PCI DSS 4.0.1, req. 3.3.1; the iOS SDK clears its stored copy on
    // onDisappear for the same reason). Note this fires on ANY removal from composition —
    // hosts must not place the form in a recycling container (LazyColumn, pager), where
    // scrolling would discard the user's input; that also holds without this effect, since
    // the plain `remember` state above doesn't survive leaving the composition either. The one
    // departure the card survives is the activity's recreation, parked below.
    // Deliberately NOT cleared on a failed encryption: authorization hasn't happened yet,
    // and the user shouldn't have to retype the card after a network drop.
    // The same span is what the operational data reports as the form's ready and its unmount,
    // and what the card form steps are timed from and summarised over.
    DisposableEffect(Unit) {
        Telemetry.noteOrigin(view.context)
        steps.ready()
        Telemetry.current?.ready(GwLoggerTelemetry.Method.CARD, GwLoggerTelemetry.CARD_FORM_FLOW)
        onDispose {
            // Leaving because the activity is being recreated for a configuration change: the
            // card is parked for the form that comes back under the same id. Here, before the
            // fields are emptied below; effects are disposed in reverse order, so the
            // registration above is still in place.
            val activity = view.context.findActivity()
            if (activity?.isChangingConfigurations == true) {
                val flags = CardFormFlags(submitAttempted, cardNumberLeft, expirationLeft, cvvLeft)
                CardFormStore.park(currentFormId, owner, snapshot(), flags, activity)
            }
            Telemetry.current?.unmount(
                GwLoggerTelemetry.Method.CARD,
                chargeInFlight = Telemetry.runningCharges.get() > 0
            )
            steps.unmounted()
            // The card and what the user did with it; the form that comes back has its own copy.
            cardNumberDigits = ""
            expirationDateDigits = ""
            cvv = ""
            cardNumberLeft = false
            expirationLeft = false
            cvvLeft = false
            submitAttempted = false
        }
    }

    val errors = CardFormErrors.of(
        data = formData,
        strings = resolvedStrings,
        mode = validation,
        submitAttempted = submitAttempted,
        cardNumberLeft = cardNumberLeft,
        expirationLeft = expirationLeft,
        cvvLeft = cvvLeft
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.formBackgroundColor)
            .padding(theme.formPadding.orZero()),
        verticalArrangement = Arrangement.spacedBy(theme.groupSpacing.orZero())
    ) {
        // Card Number Input
        LabeledInputField(
            value = cardNumberDigits,
            onValueChange = { newValue ->
                val validated = CardNumberInputValidator.validateInput(newValue)
                cardNumberDigits = validated
                cardNumberEdited = true
                CardFormStore.update(resolvedFormId, owner, snapshot())
                reportInput(CardFormProgress.Field.PAN)
                if (CardNumberInputValidator.isValidLength(validated) && CardNumberInputValidator.isValidLuhn(validated)) {
                    coroutineScope.launch {
                        expirationFocusRequester.requestFocus()
                    }
                }
            },
            config = LabeledInputFieldConfig(
                label = fields.cardNumber.label,
                error = errors.cardNumber ?: fields.cardNumber.errorText,
                helperText = fields.cardNumber.helperText,
                placeholder = fields.cardNumber.placeholder,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = if (!isCardNumberFocused && isCardNumberValid) CardNumberMaskedVisualTransformation() else CardNumberVisualTransformation(),
                textFieldModifier = Modifier
                    .cardFieldSemantics(CardField.CARD_NUMBER, allowsAutofill)
                    .focusRequester(cardNumberFocusRequester)
                    .onFocusChanged {
                        if (CardFormErrors.isLeft(isCardNumberFocused, it.isFocused, cardNumberEdited, changingConfigurations())) {
                            cardNumberLeft = true
                        }
                        isCardNumberFocused = it.isFocused
                        reportFocus(CardFormProgress.Field.PAN, it.isFocused)
                    }
            ),
            theme = theme
        )
        // Expiration Date and CVV Row. The row settles how tall the label slot is for both
        // fields, so a label that wraps in one of them cannot push its input out of line.
        SharedLabelHeightRow(
            theme = theme,
            labels = listOf(fields.expirationDate.label, fields.cvv.label),
            horizontalSpacing = theme.groupSpacing.orZero(),
            modifier = Modifier.fillMaxWidth()
        ) { labelMinHeight ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.groupSpacing.orZero())
            ) {
                // Expiration Date
                LabeledInputField(
                    value = expirationDateDigits,
                    onValueChange = { newValue ->
                        val validated = ExpiryEntry.digits(newValue)
                        expirationDateDigits = validated
                        expirationEdited = true
                        CardFormStore.update(resolvedFormId, owner, snapshot())
                        reportInput(CardFormProgress.Field.EXPIRY)
                        if (validated.length == 4) {
                            coroutineScope.launch {
                                cvvFocusRequester.requestFocus()
                            }
                        }
                    },
                    config = LabeledInputFieldConfig(
                        label = fields.expirationDate.label,
                        error = errors.expiration ?: fields.expirationDate.errorText,
                        helperText = fields.expirationDate.helperText,
                        placeholder = fields.expirationDate.placeholder,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = ExpirationDateVisualTransformation(),
                        textFieldModifier = Modifier
                            .cardFieldSemantics(CardField.EXPIRATION, allowsAutofill)
                            .focusRequester(expirationFocusRequester)
                            .onFocusChanged {
                                if (CardFormErrors.isLeft(isExpirationFocused, it.isFocused, expirationEdited, changingConfigurations())) {
                                    expirationLeft = true
                                }
                                isExpirationFocused = it.isFocused
                                reportFocus(CardFormProgress.Field.EXPIRY, it.isFocused)
                            }
                    ),
                    modifier = Modifier.weight(1f),
                    theme = theme,
                    labelMinHeight = labelMinHeight
                )
                // CVV Input
                LabeledInputField(
                    value = cvv,
                    onValueChange = { newValue ->
                        val validated = CvvValidator.validateInput(newValue, cvv)
                        cvv = validated
                        cvvEdited = true
                        CardFormStore.update(resolvedFormId, owner, snapshot())
                        reportInput(CardFormProgress.Field.CVV)
                    },
                    config = LabeledInputFieldConfig(
                        label = fields.cvv.label,
                        error = errors.cvv ?: fields.cvv.errorText,
                        helperText = fields.cvv.helperText,
                        placeholder = fields.cvv.placeholder,
                        // NumberPassword: same numeric layout, but tells the IME not to cache or
                        // learn the CVV (keyboards may retain plain Number input for suggestions).
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = if (isCvvFocused) VisualTransformation.None else CvvMaskedVisualTransformation(),
                        textFieldModifier = Modifier
                            .cardFieldSemantics(CardField.CVV, allowsAutofill)
                            .focusRequester(cvvFocusRequester)
                            .onFocusChanged {
                                if (CardFormErrors.isLeft(isCvvFocused, it.isFocused, cvvEdited, changingConfigurations())) {
                                    cvvLeft = true
                                }
                                isCvvFocused = it.isFocused
                                reportFocus(CardFormProgress.Field.CVV, it.isFocused)
                            }
                    ),
                    modifier = Modifier.weight(1f),
                    theme = theme,
                    labelMinHeight = labelMinHeight
                )
            }
        }
    }
}
