package cz.gopay.sdk.modules.network

import cz.gopay.sdk.util.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the [TokenResponse] DTO returned by `POST /oauth2/token`: default values, value-type
 * semantics, and that Moshi maps the snake_case JSON fields onto its properties (the contract the
 * SDK actually relies on at runtime).
 */
class TokenResponseTest {

    @Test
    fun `optional fields default to null`() {
        val response = TokenResponse(accessToken = "jwt", tokenType = "Bearer")

        assertEquals("jwt", response.accessToken)
        assertEquals("Bearer", response.tokenType)
        assertNull(response.scope)
        assertNull(response.expiresIn)
    }

    @Test
    fun `value semantics - equals copy and toString`() {
        val base = TokenResponse("jwt", "Bearer", scope = "payments", expiresIn = 3600)

        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
        assertEquals(base.copy(expiresIn = 7200), TokenResponse("jwt", "Bearer", "payments", 7200))
        assert(base.toString().contains("jwt"))
    }

    @Test
    fun `moshi deserializes the oauth2 token schema`() {
        val json = """
            {"access_token":"abc.def.ghi","token_type":"Bearer","scope":"payment-all","expires_in":1800}
        """.trimIndent()

        val parsed = JsonUtils.fromJson<TokenResponse>(json)

        assertNotNull(parsed)
        assertEquals("abc.def.ghi", parsed!!.accessToken)
        assertEquals("Bearer", parsed.tokenType)
        assertEquals("payment-all", parsed.scope)
        assertEquals(1800L, parsed.expiresIn)
    }
}
