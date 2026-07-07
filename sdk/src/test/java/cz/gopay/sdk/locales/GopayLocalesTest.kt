package cz.gopay.sdk.locales

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class GopayLocalesTest {

    @After
    fun tearDown() {
        // Keep global state clean for other tests.
        GopayLocales.clearCustom()
        GopayLocales.setDefaultLocale(null)
    }

    @Test
    fun resolve_knownCode_returnsThatLocale() {
        assertSame(GopayLocales.DE, GopayLocales.resolve("de"))
        assertSame(GopayLocales.CS, GopayLocales.resolve("cs"))
    }

    @Test
    fun resolve_codeIsCaseAndRegionInsensitive() {
        assertSame(GopayLocales.DE, GopayLocales.resolve("DE"))
        assertSame(GopayLocales.DE, GopayLocales.resolve("de-DE"))
        assertSame(GopayLocales.DE, GopayLocales.resolve("de_AT"))
    }

    @Test
    fun resolve_unknownCode_fallsBackToCzech() {
        assertSame(GopayLocales.CS, GopayLocales.resolve("xx"))
    }

    @Test
    fun resolve_nullPreferred_usesSdkDefaultWhenSet() {
        GopayLocales.setDefaultLocale("fr")
        assertSame(GopayLocales.FR, GopayLocales.resolve(null))
    }

    @Test
    fun resolve_explicitPreferred_beatsSdkDefault() {
        GopayLocales.setDefaultLocale("fr")
        assertSame(GopayLocales.IT, GopayLocales.resolve("it"))
    }

    @Test
    fun resolve_noPreferredNoDefault_usesSystemLanguageWhenKnown() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMAN)
            assertSame(GopayLocales.DE, GopayLocales.resolve(null))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun resolve_unknownSystemLanguage_fallsBackToCzech() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ja")) // no built-in Japanese locale
            assertSame(GopayLocales.CS, GopayLocales.resolve(null))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun register_customLocale_isResolvable() {
        val custom = GopayLocales.EN.copy(panLabel = "Custom PAN")
        GopayLocales.register("xx", custom)

        assertSame(custom, GopayLocales.resolve("xx"))
    }

    @Test
    fun register_customLocale_overridesBuiltInOfSameCode() {
        val overridden = GopayLocales.DE.copy(panLabel = "Overridden")
        GopayLocales.register("de", overridden)

        assertSame(overridden, GopayLocales.resolve("de"))
    }

    @Test
    fun registerAll_registersEveryEntry() {
        GopayLocales.registerAll(
            mapOf(
                "xx" to GopayLocales.EN.copy(panLabel = "XX"),
                "yy" to GopayLocales.EN.copy(panLabel = "YY"),
            )
        )

        assertEquals("XX", GopayLocales.resolve("xx").panLabel)
        assertEquals("YY", GopayLocales.resolve("yy").panLabel)
    }

    @Test
    fun clearCustom_removesCustomButKeepsBuiltIns() {
        GopayLocales.register("xx", GopayLocales.EN)
        GopayLocales.clearCustom()

        assertSame(GopayLocales.CS, GopayLocales.resolve("xx")) // custom gone -> fallback
        assertSame(GopayLocales.DE, GopayLocales.resolve("de")) // built-in intact
    }

    @Test
    fun builtIn_containsExpectedTwentyLocales() {
        val expected = setOf(
            "bg", "cs", "de", "en", "es", "et", "fr", "hr", "hu", "it",
            "lt", "lv", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "uk"
        )
        assertEquals(expected, GopayLocales.builtIn.keys)
    }

    @Test
    fun builtIn_everyLocaleHasAllFieldsNonBlank() {
        GopayLocales.builtIn.forEach { (code, s) ->
            val fields = listOf(
                s.panLabel, s.panPlaceholder, s.expLabel, s.expPlaceholder,
                s.cvvLabel, s.cvvPlaceholder, s.pay, s.panErrorPattern,
                s.expErrorPattern, s.cvvErrorPattern, s.patternErrorMessage,
                s.requiredErrorMessage
            )
            fields.forEach { value ->
                assertFalse("Locale '$code' has a blank field", value.isBlank())
            }
        }
    }

    @Test
    fun defaultLocaleConstant_hasBuiltInEntry() {
        assertNotNull(GopayLocales.builtIn[GopayLocales.DEFAULT_LOCALE])
        assertTrue(GopayLocales.DEFAULT_LOCALE == "cs")
    }
}
