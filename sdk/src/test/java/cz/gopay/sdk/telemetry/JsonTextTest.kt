package cz.gopay.sdk.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** JSON read and written back the way a browser's `JSON.parse` and `JSON.stringify` do it. */
class JsonTextTest {

    private fun roundTrip(text: String) = JsonText.stringify(JsonText.parse(text))

    @Test
    fun `escapes what JSON stringify escapes and nothing else`() {
        assertEquals(
            "\"q\\\" b\\\\ \\b\\f\\n\\r\\t \\u0001 / é € 😀\"",
            JsonText.stringify("q\" b\\ \b\u000C\n\r\t \u0001 / é € 😀")
        )
    }

    @Test
    fun `writes a lone surrogate as an escape, so a cut pair stays valid UTF-8`() {
        assertEquals("\"a\\ud83d\"", JsonText.stringify("a\uD83D"))
        assertEquals("\"\\ude00b\"", JsonText.stringify("\uDE00b"))
    }

    @Test
    fun `writes numbers as a browser does`() {
        val cases = mapOf(
            "1" to "1",
            "-0" to "0",
            "1.50" to "1.5",
            "1e2" to "100",
            "1E+2" to "100",
            "0.000001" to "0.000001",
            "1e-7" to "1e-7",
            "1.5e400" to "null",
            "123456789012345678901" to "123456789012345680000",
            "12345678901234567890" to "12345678901234567000",
            "1e21" to "1e+21",
            "9007199254740993" to "9007199254740992"
        )
        for ((literal, expected) in cases) assertEquals(literal, expected, roundTrip(literal))
    }

    @Test
    fun `refuses trailing content, as JSON parse does`() {
        assertThrows(Exception::class.java) { JsonText.parse("{\"a\":1} x") }
        assertThrows(Exception::class.java) { JsonText.parse("{\"a\":1}{}") }
    }

    @Test
    fun `keeps the first place and the last value of a repeated key`() {
        assertEquals("{\"a\":3,\"b\":2}", roundTrip("{\"a\":1,\"b\":2,\"a\":3}"))
    }

    @Test
    fun `keeps the structure and the order of what it read`() {
        val text = "{\"z\":[1,true,null,\"s\",{\"y\":{}}],\"a\":[]}"
        assertEquals(text, roundTrip(text))
    }
}
