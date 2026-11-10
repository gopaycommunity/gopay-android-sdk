package cz.gopay.sdk.util

import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.modules.network.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `toJson should handle null object gracefully`() {
        // When serializing null
        val nullObject: Jwk? = null
        val json = JsonUtils.toJson(nullObject)

        // Then it should return null
        assertNull("JSON should be null for null input", json)
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

        // When serializing, and reading it back with the SDK's own Moshi
        val json = JsonUtils.toJson(specialResponse)
        val result = NetworkModule.moshi.adapter(Jwk::class.java).fromJson(json!!)

        // Then special characters should be preserved
        assertNotNull("Result should not be null", result)
        assertEquals("Special characters should be preserved", specialResponse, result)
    }
} 