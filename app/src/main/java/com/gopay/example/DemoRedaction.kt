package com.gopay.example

/**
 * What the demo never shows in a response it prints: the payment's secret and the customer's
 * e-mail and phone number. A console and a "developer details" panel get copied into tickets
 * and screenshots, and none of the three belongs there. The iOS demo hides the same keys.
 *
 * The value is replaced by `(hidden)` wherever the key appears, at any depth, in either spelling
 * the demo prints: a JSON body (`"email":"…"`) or a model's `toString()` (`email=…`).
 */
object DemoRedaction {

    const val HIDDEN = "(hidden)"

    private val KEYS = listOf("payment_secret", "paymentSecret", "email", "phone_number", "phoneNumber")

    // The key, quoted or not, then `:` or `=`, then the value: a quoted string, or everything up
    // to the next separator of a toString() or a JSON body. A value already hidden is left alone.
    private val PATTERN = Regex(
        "(\"?\\b(?:" + KEYS.joinToString("|") + ")\\b\"?\\s*[:=]\\s*)(?!\\(hidden\\))" +
            "(\"(?:[^\"\\\\]|\\\\.)*\"|[^,\\s)}\\]]+)"
    )

    fun hide(text: String): String = PATTERN.replace(text) { match ->
        val value = match.groupValues[2]
        // An absent value says nothing about the customer; leave it readable.
        if (value == "null") match.value else match.groupValues[1] + HIDDEN
    }
}
