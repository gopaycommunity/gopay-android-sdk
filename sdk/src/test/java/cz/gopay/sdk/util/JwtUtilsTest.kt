package cz.gopay.sdk.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class JwtUtilsTest {

    @Test
    fun `the expiry is the exp claim, in seconds`() {
        assertEquals(1_900_000_000L, JwtUtils.expirationSecondsOrZero(createTestJwtToken(exp = 1_900_000_000L)))
    }

    @Test
    fun `a token without exp, or one that cannot be read, has no known expiry`() {
        assertEquals(0L, JwtUtils.expirationSecondsOrZero(createTestJwtToken(sub = "client")))
        assertEquals(0L, JwtUtils.expirationSecondsOrZero("not.a.jwt"))
        assertEquals(0L, JwtUtils.expirationSecondsOrZero("only-one-part"))
    }

    /**
     * Helper method to create a test JWT token with specified claims
     */
    private fun createTestJwtToken(
        exp: Long? = null,
        sub: String? = null
    ): String {
        // Create header
        val header = """{"alg":"HS256","typ":"JWT"}"""
        val encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
        
        // Create payload - ensure it's valid JSON even when empty
        val payloadParts = mutableListOf<String>()
        exp?.let { payloadParts.add("\"exp\":$it") }
        sub?.let { payloadParts.add("\"sub\":\"$it\"") }
        
        val payload = if (payloadParts.isEmpty()) "{}" else "{${payloadParts.joinToString(",")}}"
        val encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        
        // Create signature (dummy for testing)
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString("dummy_signature".toByteArray())
        
        return "$encodedHeader.$encodedPayload.$signature"
    }
} 