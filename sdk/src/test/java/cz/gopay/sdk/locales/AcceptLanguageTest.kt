package cz.gopay.sdk.locales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * The `Accept-Language` the SDK sends with `init-session`, built like the browser SDK's
 * `buildAcceptLanguage`; the inputs and outputs are that function's tests'
 * (`browser-sdk/tests/unit/browser-data.test.ts`) and the iOS SDK's (`InitSessionTests.swift`).
 */
class AcceptLanguageTest {

    @Test
    fun `a single language has no q-value`() {
        assertEquals("cs-CZ", acceptLanguageHeader(listOf("cs-CZ")))
    }

    @Test
    fun `the first language as it is, the next ones with falling q-values`() {
        assertEquals("cs-CZ,cs;q=0.9,en-GB;q=0.8", acceptLanguageHeader(listOf("cs-CZ", "cs", "en-GB")))
        assertEquals("cs-CZ,cs;q=0.9,en;q=0.8", acceptLanguageHeader(listOf("cs-CZ", "cs", "en")))
    }

    @Test
    fun `no q-value falls below 0_1`() {
        val languages = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l")

        assertEquals(
            "a,b;q=0.9,c;q=0.8,d;q=0.7,e;q=0.6,f;q=0.5,g;q=0.4,h;q=0.3,i;q=0.2,j;q=0.1,k;q=0.1,l;q=0.1",
            acceptLanguageHeader(languages)
        )
    }

    @Test
    fun `nothing is deduplicated`() {
        assertEquals("en,en;q=0.9", acceptLanguageHeader(listOf("en", "en")))
    }

    @Test
    fun `no languages send the device's current one alone`() {
        assertEquals("de-DE", acceptLanguageHeader(emptyList(), fallback = "de-DE"))
        assertEquals(languageTag(Locale.getDefault()), acceptLanguageHeader(emptyList()))
    }

    @Test
    fun `the device's current language falls back without its extensions too`() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("en-US-u-mu-celsius"))

            assertEquals("en-US", acceptLanguageHeader(emptyList()))
            assertEquals("en-US", acceptLanguageHeader(listOf("čeština")))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun `a tag a header cannot carry is left out`() {
        assertEquals("cs-CZ", acceptLanguageHeader(listOf("čeština", "cs-CZ", "en;q=1", "", "-cs", "cs--CZ", "de-")))
        assertEquals("weights follow what is left", "cs-CZ,en-GB;q=0.9", acceptLanguageHeader(listOf("x;y", "cs-CZ", "ü", "en-GB")))
        assertEquals("sk-SK", acceptLanguageHeader(listOf("čeština"), fallback = "sk-SK"))
        assertEquals("cs-CZ,en;q=0.9", acceptLanguageHeader(listOf("cs-CZ", "čeština", "", "en")))
        assertEquals("en-GB", acceptLanguageHeader(listOf("x y"), fallback = "en-GB"))
    }

    @Test
    fun `no header when even the fallback cannot go in one`() {
        assertNull(acceptLanguageHeader(listOf("čeština"), fallback = ""))
        assertNull(acceptLanguageHeader(emptyList(), fallback = "x y"))
    }

    @Test
    fun `a system language goes without its extensions`() {
        // Android 14's regional preferences, e.g. a temperature unit, as LocaleList returns them.
        assertEquals("en-US", languageTag(Locale.forLanguageTag("en-US-u-mu-celsius")))
        assertEquals("cs-CZ", languageTag(Locale.forLanguageTag("cs-CZ-u-fw-mon-mu-celsius")))
        assertEquals("de-DE", languageTag(Locale.forLanguageTag("de-DE")))
        assertEquals("sr-Latn-RS", languageTag(Locale.forLanguageTag("sr-Latn-RS-u-nu-latn")))
    }
}
