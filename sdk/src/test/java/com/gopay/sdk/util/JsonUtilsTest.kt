package com.gopay.sdk.util

import com.gopay.sdk.model.Jwk
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for JsonUtils utility class
 */
class JsonUtilsTest {

    @Test
    fun `toJson should serialize object to JSON string`() {
        // Given a JwkResponse object
        val jwkResponse = Jwk(
            kty = "RSA",
            kid = "test-key-id",
            use = "enc",
            alg = "RSA-OAEP-256",
            n = "test-modulus",
            e = "AQAB"
        )

        // When serializing to JSON
        val json = JsonUtils.toJson(jwkResponse, Jwk::class.java)

        // Then the result should be valid JSON containing the expected fields
        assertNotNull("JSON should not be null", json)
        assertTrue("JSON should contain kty", json!!.contains("\"kty\":\"RSA\""))
        assertTrue("JSON should contain kid", json.contains("\"kid\":\"test-key-id\""))
        assertTrue("JSON should contain use", json.contains("\"use\":\"enc\""))
        assertTrue("JSON should contain alg", json.contains("\"alg\":\"RSA-OAEP-256\""))
        assertTrue("JSON should contain n", json.contains("\"n\":\"test-modulus\""))
        assertTrue("JSON should contain e", json.contains("\"e\":\"AQAB\""))
    }
 
    @Test
    fun `fromJson should deserialize JSON string to object`() {
        // Given a valid JSON string
        val json = """
            {
                "kty": "RSA",
                "kid": "test-key-id",
                "use": "enc",
                "alg": "RSA-OAEP-256",
                "n": "test-modulus",
                "e": "AQAB"
            }
        """.trimIndent()

        // When deserializing from JSON
        val result: Jwk? = JsonUtils.fromJson(json)

        // Then the result should be a valid JwkResponse object
        assertNotNull("Result should not be null", result)
        assertEquals("RSA", result!!.kty)
        assertEquals("test-key-id", result.kid)
        assertEquals("enc", result.use)
        assertEquals("RSA-OAEP-256", result.alg)
        assertEquals("test-modulus", result.n)
        assertEquals("AQAB", result.e)
    }

    @Test
    fun `reified toJson should work with type inference`() {
        // Given a JwkResponse object
        val jwkResponse = Jwk(
            kty = "RSA",
            kid = "reified-test",
            use = "enc",
            alg = "RSA-OAEP-256",
            n = "reified-modulus",
            e = "AQAB"
        )

        // When using reified toJson
        val json = JsonUtils.toJson(jwkResponse)

        // Then the result should be valid JSON
        assertNotNull("JSON should not be null", json)
        assertTrue("JSON should contain reified-test", json!!.contains("\"kid\":\"reified-test\""))
    }

    @Test
    fun `reified fromJson should work with type inference`() {
        // Given a valid JSON string
        val json = """
            {
                "kty": "RSA",
                "kid": "reified-test",
                "use": "enc",
                "alg": "RSA-OAEP-256",
                "n": "reified-modulus",
                "e": "AQAB"
            }
        """.trimIndent()

        // When using reified fromJson
        val result: Jwk? = JsonUtils.fromJson(json)

        // Then the result should be a valid JwkResponse object
        assertNotNull("Result should not be null", result)
        assertEquals("reified-test", result!!.kid)
        assertEquals("reified-modulus", result.n)
    }

    @Test
    fun `toJson should handle null object gracefully`() {
        // When serializing null
        val nullObject: Jwk? = null
        val json = JsonUtils.toJson(nullObject)

        // Then it should return null
        assertNull("JSON should be null for null input", json)
    }

    @Test
    fun `fromJson should handle invalid JSON gracefully`() {
        // Given invalid JSON
        val invalidJson = "{ invalid json structure"

        // When deserializing invalid JSON
        val result: Jwk? = JsonUtils.fromJson(invalidJson)

        // Then it should return null
        assertNull("Result should be null for invalid JSON", result)
    }

    @Test
    fun `fromJson should handle empty string gracefully`() {
        // Given empty string
        val emptyJson = ""

        // When deserializing empty string
        val result: Jwk? = JsonUtils.fromJson(emptyJson)

        // Then it should return null
        assertNull("Result should be null for empty JSON", result)
    }

    @Test
    fun `fromJson should handle malformed JSON gracefully`() {
        // Given malformed JSON with missing closing brace
        val malformedJson = """{"kty":"RSA","kid":"test"""

        // When deserializing malformed JSON
        val result: Jwk? = JsonUtils.fromJson(malformedJson)

        // Then it should return null
        assertNull("Result should be null for malformed JSON", result)
    }

    @Test
    fun `toJson and fromJson should be symmetric`() {
        // Given a JwkResponse object
        val original = Jwk(
            kty = "RSA",
            kid = "symmetric-test",
            use = "enc",
            alg = "RSA-OAEP-256",
            n = "symmetric-modulus",
            e = "AQAB"
        )

        // When serializing and then deserializing
        val json = JsonUtils.toJson(original)
        val result = JsonUtils.fromJson<Jwk>(json!!)

        // Then the result should equal the original
        assertNotNull("Result should not be null", result)
        assertEquals("Objects should be equal", original, result)
    }

    @Test
    fun `should handle JSON with extra fields gracefully`() {
        // Given JSON with extra fields not in JwkResponse
        val jsonWithExtraFields = """
            {
                "kty": "RSA",
                "kid": "extra-fields-test",
                "use": "enc",
                "alg": "RSA-OAEP-256",
                "n": "extra-modulus",
                "e": "AQAB",
                "extra_field": "should_be_ignored",
                "another_extra": 123
            }
        """.trimIndent()

        // When deserializing JSON with extra fields
        val result: Jwk? = JsonUtils.fromJson(jsonWithExtraFields)

        // Then it should successfully parse the known fields
        assertNotNull("Result should not be null", result)
        assertEquals("extra-fields-test", result!!.kid)
        assertEquals("extra-modulus", result.n)
        // Extra fields should be ignored
    }

    @Test
    fun `should handle special characters in JSON strings`() {
        // Given a JwkResponse with special characters
        val specialResponse = Jwk(
            kty = "RSA",
            kid = "special-test-🔐-ñáéíóú",
            use = "enc",
            alg = "RSA-OAEP-256",
            n = "modulus-with-special-chars-测试",
            e = "AQAB"
        )

        // When serializing and deserializing
        val json = JsonUtils.toJson(specialResponse)
        val result = JsonUtils.fromJson<Jwk>(json!!)

        // Then special characters should be preserved
        assertNotNull("Result should not be null", result)
        assertEquals("Special characters should be preserved", specialResponse, result)
    }
} 