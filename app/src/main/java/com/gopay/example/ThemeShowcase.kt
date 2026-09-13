package com.gopay.example

import android.content.Context
import cz.gopay.sdk.ui.PaymentCardFormTheme
import cz.gopay.sdk.ui.PaymentCardFormThemeJson
import org.json.JSONObject

/**
 * The demo's theme documents, in the order the picker shows them.
 *
 * Themes the card form from JSON documents, the way a host applies a theme its own backend sent
 * down. The documents live in `assets/theme-showcase.json`; the iOS demo ships the same file as
 * `ThemeShowcase.json`, so one document can be compared across Android, iOS and the web card form
 * whose parameter names they use.
 *
 * The two the GoPay web card form ships with, `Dark` and `Red`, written out key for key, plus
 * `Default`, which is an empty document and therefore renders the SDK defaults. The web demo
 * offers the same three, so the same theme can be compared across all three channels.
 */
class ThemeShowcase private constructor(
    /** Document names in file order, so the picker matches the iOS demo. */
    val names: List<String>,
    private val documents: Map<String, String>
) {
    /**
     * The named document applied over the SDK defaults, or the defaults when the name is unknown.
     *
     * Parsing never fails the form: a key the SDK cannot use drops on its own and is reported in
     * Logcat, which is the behavior a host wants for a document it did not write.
     */
    fun theme(name: String): PaymentCardFormTheme {
        val document = documents[name] ?: return PaymentCardFormTheme()
        return PaymentCardFormThemeJson.parse(document).toTheme()
    }

    companion object {
        private const val ASSET = "theme-showcase.json"

        fun load(context: Context): ThemeShowcase {
            val raw = runCatching {
                context.assets.open(ASSET).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return ThemeShowcase(emptyList(), emptyMap())

            val root = runCatching { JSONObject(raw) }.getOrNull()
                ?: return ThemeShowcase(emptyList(), emptyMap())
            val themes = root.optJSONObject("themes") ?: return ThemeShowcase(emptyList(), emptyMap())
            val order = root.optJSONArray("order") ?: return ThemeShowcase(emptyList(), emptyMap())

            val names = mutableListOf<String>()
            val documents = mutableMapOf<String, String>()
            for (i in 0 until order.length()) {
                val name = order.optString(i)
                val document = themes.optJSONObject(name) ?: continue
                names += name
                documents[name] = document.toString()
            }
            return ThemeShowcase(names, documents)
        }
    }
}
