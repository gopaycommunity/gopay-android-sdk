package cz.gopay.sdk.locales

import java.util.Locale

/**
 * Registry and resolver for [GopayLocaleStrings] used by the payment card form.
 *
 * Ships 20 built-in translations keyed by ISO 639-1 language code. Host apps can add their own
 * with [register] (or via `GopayConfig.customLocales`) and select a locale explicitly, or rely on
 * the default resolution: the SDK-wide preferred locale (set from `GopayConfig.locale`), else the
 * device language, ultimately falling back to Czech ([DEFAULT_LOCALE]).
 */
object GopayLocales {

    /** Language code used as the final fallback when nothing else matches. */
    const val DEFAULT_LOCALE = "cs"

    // Placeholders / CVV label are not localized in the shared web locale set.
    private const val PAN_PLACEHOLDER = "1234 5678 9012 3456"
    private const val CVV_LABEL = "CVV"
    private const val CVV_PLACEHOLDER = "123"

    val CS = GopayLocaleStrings(
        panLabel = "Číslo karty",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Platnost",
        expPlaceholder = "MM/RR",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Zaplatit",
        panErrorPattern = "Zadali jste nesprávné číslo karty",
        expErrorPattern = "Zadejte číslo měsíce a poslední dvě čísla roku expirace vaší karty.",
        cvvErrorPattern = "CVC/CVV musí obsahovat 3 číslice",
        patternErrorMessage = "Hodnota je v nesprávném tvaru",
        requiredErrorMessage = "Toto pole je povinné",
    )

    val EN = GopayLocaleStrings(
        panLabel = "Card number",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Expiration date",
        expPlaceholder = "MM/YY",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Pay",
        panErrorPattern = "You entered a wrong card number",
        expErrorPattern = "Enter the month number and the last two digits of your card's expiration year.",
        cvvErrorPattern = "CVV must be 3 digits",
        patternErrorMessage = "Value is in wrong format",
        requiredErrorMessage = "This field is required",
    )

    val DE = GopayLocaleStrings(
        panLabel = "Kartennummer",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Gültigkeit",
        expPlaceholder = "MM/JJ",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Bezahlen",
        panErrorPattern = "Sie haben eine falsche Kartennummer eingegeben",
        expErrorPattern = "Geben Sie die Monatsnummer und die letzten beiden Ziffern des Ablaufes Ihrer Karte ein.",
        cvvErrorPattern = "CVC/CVV muss 3 Ziffern enthalten",
        patternErrorMessage = "Falsches Format",
        requiredErrorMessage = "Dieses Feld ist erforderlich",
    )

    val ES = GopayLocaleStrings(
        panLabel = "Número de Tarjeta",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Fecha de vencimiento",
        expPlaceholder = "MM/AA",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Pagar",
        panErrorPattern = "Ingresó un número de tarjeta incorrecto",
        expErrorPattern = "Ingrese el número de mes y los dos últimos dígitos del año de vencimiento de su tarjeta.",
        cvvErrorPattern = "El CVC/CVV debe tener 3 dígitos",
        patternErrorMessage = "Formato de valor incorrecto",
        requiredErrorMessage = "Este campo es requerido",
    )

    val FR = GopayLocaleStrings(
        panLabel = "Numéro de carte",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Validité",
        expPlaceholder = "MM/AA",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Payer",
        panErrorPattern = "Vous avez écrit un numéro de carte incorrect",
        expErrorPattern = "Écrivez le numéro du mois et les deux derniers numéros de l'année d'expiration de votre carte.",
        cvvErrorPattern = "CVC/CVV doit contenir 3 chiffres",
        patternErrorMessage = "Format erroné",
        requiredErrorMessage = "Ce champ est obligatoire",
    )

    val IT = GopayLocaleStrings(
        panLabel = "Numero di carta",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Validità",
        expPlaceholder = "MM/AA",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Paga",
        panErrorPattern = "Ha inserito numero di carta non corretto",
        expErrorPattern = "Inserire il numero di mese e ultimi due numeri della scadenza della vostra carta.",
        cvvErrorPattern = "CVC/CVV deve contenere 3 numeri",
        patternErrorMessage = "Formato errato",
        requiredErrorMessage = "Questo campo è obbligatorio",
    )

    val NL = GopayLocaleStrings(
        panLabel = "Kaartnummer",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Vervaldatum",
        expPlaceholder = "MM/JJ",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Betalen",
        panErrorPattern = "U hebt een onjuist kaartnummer ingevoerd",
        expErrorPattern = "Voer het maandnummer in en de laatste twee cijfers van het vervaljaar van uw kaart.",
        cvvErrorPattern = "CVC/CVV moet uit 3 cijfers bestaan",
        patternErrorMessage = "De waarde is in een onjuist formaat",
        requiredErrorMessage = "Dit veld is verplicht",
    )

    val PL = GopayLocaleStrings(
        panLabel = "Numer karty",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Ważność",
        expPlaceholder = "MM/RR",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Zapłać",
        panErrorPattern = "Podałeś nieprawidłowy numer karty",
        expErrorPattern = "Wprowadź numer miesiąca i dwie ostatnie cyfry roku ważności karty.",
        cvvErrorPattern = "Numer CVC/CVV musi zawierać 3 cyfry",
        patternErrorMessage = "Niewłaściwy format",
        requiredErrorMessage = "To pole jest obowiązkowe",
    )

    val PT = GopayLocaleStrings(
        panLabel = "Número do cartão",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Data de validade",
        expPlaceholder = "MM/AA",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Pagar",
        panErrorPattern = "Introduziu um número de cartão errado",
        expErrorPattern = "Introduza o número do mês e os dois últimos dígitos do ano de validade do seu cartão.",
        cvvErrorPattern = "O CVC/CVV deve ter 3 dígitos",
        patternErrorMessage = "Formato errado do valor",
        requiredErrorMessage = "Este campo é obrigatório",
    )

    val BG = GopayLocaleStrings(
        panLabel = "Номер на картата",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Валидност",
        expPlaceholder = "MM/RR",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Плати",
        panErrorPattern = "Въвели сте неправилен номер на картата",
        expErrorPattern = "Въведете номера на месеца и последните две числа на годината на изтичане на вашата карта.",
        cvvErrorPattern = "CVC/CVV трябва да съдържа 3 цифри",
        patternErrorMessage = "Неправилен формат",
        requiredErrorMessage = "Това поле е задължително",
    )

    val UK = GopayLocaleStrings(
        panLabel = "Номер картки",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Термін дії",
        expPlaceholder = "ММ/РР",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Сплатити",
        panErrorPattern = "Ви ввели неправильний номер картки",
        expErrorPattern = "Введіть місяць та дві останні цифри року закінчення терміну дії вашої картки.",
        cvvErrorPattern = "CVC/CVV повинен містити 3 цифри",
        patternErrorMessage = "Невірний формат",
        requiredErrorMessage = "Це поле є обов'язковим",
    )

    val RU = GopayLocaleStrings(
        panLabel = "Номер карты",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Срок действия",
        expPlaceholder = "ММ/ГГ",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Оплатить",
        panErrorPattern = "Вы ввели неправильный номер карты",
        expErrorPattern = "Введите номер месяца и последние два года срока действия Вашей карты.",
        cvvErrorPattern = "CVC/CVV должен содержать 3 цифры",
        patternErrorMessage = "Неверный формат",
        requiredErrorMessage = "Это поле является обязательным",
    )

    val ET = GopayLocaleStrings(
        panLabel = "Kaardi number",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Aegumiskuupäev",
        expPlaceholder = "KK/AA",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Maksa",
        panErrorPattern = "Sisestasite vale kaardi numbri",
        expErrorPattern = "Sisestage oma kaardi aegumiskuu number ja aegumisaasta kaks viimast numbrit.",
        cvvErrorPattern = "CVC/CVV peab koosnema kolmest numbrist",
        patternErrorMessage = "Vale väärtuse vorming",
        requiredErrorMessage = "See väli on nõutav",
    )

    val HR = GopayLocaleStrings(
        panLabel = "Broj kartice",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Važenje",
        expPlaceholder = "MM/GG",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Plati",
        panErrorPattern = "Unijeli ste pogrešan broj kartice",
        expErrorPattern = "Upišite broj mjeseca i zadnja dva broja prestanka važenja vaše kartice.",
        cvvErrorPattern = "CVC/CVV mora sadržati 3 broja",
        patternErrorMessage = "Neispravan format",
        requiredErrorMessage = "Ovo polje je obvezno",
    )

    val HU = GopayLocaleStrings(
        panLabel = "Kártyaszám",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Érvényesség",
        expPlaceholder = "HH/ÉÉ",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Fizet",
        panErrorPattern = "Hibás kártyaszám",
        expErrorPattern = "Írja be a kártya lejáratának hónapját, valamint az év utolsó két számjegyét.",
        cvvErrorPattern = "A CVC/CVV -nek 3 számjegyből kell állnia",
        patternErrorMessage = "Hibás formátum",
        requiredErrorMessage = "Kötelező mező",
    )

    val LT = GopayLocaleStrings(
        panLabel = "Kortelės numeris",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Galiojimo data",
        expPlaceholder = "MM/YY",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Moketi",
        panErrorPattern = "Įvedėte neteisingą kortelės numerį",
        expErrorPattern = "Įveskite mėnesio numerį ir paskutinius du kortelės galiojimo metų skaitmenis.",
        cvvErrorPattern = "CVC/CVV sudarytas iš 3 skaitmenų",
        patternErrorMessage = "Neteisingas vertės formatas",
        requiredErrorMessage = "Šį lauką būtina užpildyti",
    )

    val LV = GopayLocaleStrings(
        panLabel = "Kartes numurs",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Derīguma termiņš",
        expPlaceholder = "MM/GG",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Maksāt",
        panErrorPattern = "Jūs ievadījāt nepareizu kartes numuru",
        expErrorPattern = "Ievadiet mēneša numuru un pēdējos divus ciparus no jūsu kartes derīguma termiņa beigu gada.",
        cvvErrorPattern = "CVC/CVV jābūt 3 cipariem",
        patternErrorMessage = "Nepareizs vērtības formāts",
        requiredErrorMessage = "Šis lauks ir nepieciešams",
    )

    val RO = GopayLocaleStrings(
        panLabel = "Numărul cardului",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Valabilitate",
        expPlaceholder = "LL/AA",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Plătește",
        panErrorPattern = "Ați introdus un număr de card incorect",
        expErrorPattern = "Introduceți numărul lunii și ultimele două cifre ale anului de expirare a cardului.",
        cvvErrorPattern = "CVC/CVV trebuie să conțină 3 cifre",
        patternErrorMessage = "Format greșit",
        requiredErrorMessage = "Acest câmp este obligatoriu",
    )

    val SK = GopayLocaleStrings(
        panLabel = "Číslo karty",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Platnosť",
        expPlaceholder = "MM/RR",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Zaplatiť",
        panErrorPattern = "Zadali ste nesprávne číslo karty",
        expErrorPattern = "Zadajte číslo mesiaca a posledné dve čísla roku exspirácie vašej karty.",
        cvvErrorPattern = "CVC/CVV musí obsahovať 3 číslice",
        patternErrorMessage = "Chybný formát",
        requiredErrorMessage = "Toto pole je povinné",
    )

    val SL = GopayLocaleStrings(
        panLabel = "Številka kartice",
        panPlaceholder = PAN_PLACEHOLDER,
        expLabel = "Veljavnost",
        expPlaceholder = "MM/LL",
        cvvLabel = CVV_LABEL,
        cvvPlaceholder = CVV_PLACEHOLDER,
        pay = "Plačaj",
        panErrorPattern = "Vnesli ste napačno številko kartice",
        expErrorPattern = "Vnesite številko meseca in zadnji dve številki leta poteka veljavnosti svoje kartice.",
        cvvErrorPattern = "CVC/CVV mora vsebovati 3 številke",
        patternErrorMessage = "Napačna oblika",
        requiredErrorMessage = "To polje je obvezno",
    )

    /** All built-in locales keyed by ISO 639-1 language code. */
    val builtIn: Map<String, GopayLocaleStrings> = mapOf(
        "bg" to BG,
        "cs" to CS,
        "de" to DE,
        "en" to EN,
        "es" to ES,
        "et" to ET,
        "fr" to FR,
        "hr" to HR,
        "hu" to HU,
        "it" to IT,
        "lt" to LT,
        "lv" to LV,
        "nl" to NL,
        "pl" to PL,
        "pt" to PT,
        "ro" to RO,
        "ru" to RU,
        "sk" to SK,
        "sl" to SL,
        "uk" to UK,
    )

    /** Host-registered custom locales; take priority over [builtIn] for the same code. */
    private val custom: MutableMap<String, GopayLocaleStrings> = mutableMapOf()

    /** SDK-wide preferred locale code, set from `GopayConfig.locale`. `null` means "use system". */
    @Volatile
    private var defaultLocaleCode: String? = null

    /**
     * Registers (or overrides) a locale under [code]. A registered locale shadows any built-in of
     * the same code. Codes are matched case-insensitively on the language part only.
     */
    fun register(code: String, strings: GopayLocaleStrings) {
        custom[normalize(code)] = strings
    }

    /** Registers all entries of [locales]. Convenience for `GopayConfig.customLocales`. */
    fun registerAll(locales: Map<String, GopayLocaleStrings>) {
        locales.forEach { (code, strings) -> register(code, strings) }
    }

    /** Removes all host-registered custom locales. Built-ins are unaffected. */
    fun clearCustom() {
        custom.clear()
    }

    /** All selectable locale codes — built-in plus host-registered custom — sorted alphabetically. */
    fun availableCodes(): List<String> = (builtIn.keys + custom.keys).toSortedSet().toList()

    /** Sets the SDK-wide preferred locale code (`null` = use the device language). */
    fun setDefaultLocale(code: String?) {
        defaultLocaleCode = code?.let(::normalize)
    }

    /** The device language code, e.g. `"cs"` for a Czech device. */
    fun systemLanguage(): String = normalize(Locale.getDefault().language)

    /**
     * Resolves the [GopayLocaleStrings] to use, in priority order:
     * 1. [preferred] if non-null and known,
     * 2. the SDK-wide default ([setDefaultLocale]) if known,
     * 3. the device language ([systemLanguage]) if known,
     * 4. [DEFAULT_LOCALE] (Czech).
     *
     * A code is "known" if a custom locale or a built-in exists for its language part.
     */
    fun resolve(preferred: String? = null): GopayLocaleStrings {
        return lookup(preferred)
            ?: lookup(defaultLocaleCode)
            ?: lookup(systemLanguage())
            ?: builtIn.getValue(DEFAULT_LOCALE)
    }

    /** Returns the locale for [code] (custom first, then built-in), or `null` if none matches. */
    private fun lookup(code: String?): GopayLocaleStrings? {
        val key = code?.let(::normalize) ?: return null
        return custom[key] ?: builtIn[key]
    }

    /** Reduces a locale tag to its lowercase language part, e.g. `"cs-CZ"` / `"cs_CZ"` -> `"cs"`. */
    private fun normalize(code: String): String =
        code.trim().substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
}
