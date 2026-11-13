package cz.gopay.sdk.ui.utils

/**
 * The card brands the card form accepts, and the one place their patterns live. A number that
 * matches none of them is refused like one that fails the Luhn check.
 *
 * The patterns are GoPay's, taken as they are, with `\d` written as `[0-9]`: Android's regex
 * engine is ICU, where `\d` matches every Unicode digit. The order matters, Visa Electron is
 * tried before Visa. Every brand has 16 digits, the only length the form takes. GoPay may refine
 * the patterns; replacing them is a change of this file and its test.
 *
 * The iOS SDK holds the same patterns in its `CardBrand`, in `CardBrands.swift`.
 */
internal enum class CardBrand(pattern: String) {
    VISA_ELECTRON("^4(026|17500|405|508|844|91[37])"),
    VISA("^4"),
    MASTERCARD(
        "^(5[1-5]|2(22[1-9][0-9]{2}|2[3-9][0-9]{3}|[3-6][0-9]{4}|7[0-1][0-9]{3}|720[0-9]{2}))"
    ),
    MAESTRO(
        "^(5(0|[6-9])|6(00|010|011[156]|0117[0-3]|0117[56]|0118[0-5]|01[2-9]|0[2-9]|1|2[01]|" +
            "220[0-9]|221[0-1]|2212[0-5]|2292[6-9]|229[3-9][0-9]|2[3-9]|3[0-9]|4[0-3]|[6-9]))"
    );

    private val regex = Regex(pattern)

    fun matches(digits: String): Boolean = regex.containsMatchIn(digits)

    companion object {
        /** The first brand, in declaration order, whose pattern [digits] start with, or null. */
        fun of(digits: String): CardBrand? = values().firstOrNull { it.matches(digits) }
    }
}
