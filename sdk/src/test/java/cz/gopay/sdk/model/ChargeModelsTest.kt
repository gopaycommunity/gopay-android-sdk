package cz.gopay.sdk.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeModelsTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ChargePaymentResponse::class.java)

    // Mirrors the GET /payments/{id}/charge response shape the server actually sends
    // when charge is ACTION_REQUIRED and 3DS is required.
    private val jsonWithAction = """
        {
          "id": "charge-abc-123",
          "state": "ACTION_REQUIRED",
          "return_url": "cz.gopay.sdk://payment/return",
          "action": {
            "action_type": "EMV3DS",
            "state": "CREATED",
            "redirect_url": "https://3ds.example.com/challenge"
          }
        }
    """.trimIndent()

    // Minimal response without action — returned when charge is still PROCESSING or SUCCEEDED
    private val jsonWithoutAction = """
        {
          "id": "charge-abc-123",
          "state": "PROCESSING",
          "return_url": "cz.gopay.sdk://payment/return"
        }
    """.trimIndent()

    // Response with payment_instrument but no details — spec marks details as optional
    private val jsonWithInstrumentNoDetails = """
        {
          "id": "charge-abc-123",
          "state": "ACTION_REQUIRED",
          "return_url": "cz.gopay.sdk://payment/return",
          "payment_instrument": {
            "payment_instrument": "PAYMENT_CARD"
          },
          "action": {
            "action_type": "EMV3DS",
            "state": "CREATED",
            "redirect_url": "https://3ds.example.com/challenge"
          }
        }
    """.trimIndent()

    // Response with payment_instrument with details AND action
    private val jsonWithInstrumentAndAction = """
        {
          "id": "charge-abc-123",
          "state": "ACTION_REQUIRED",
          "return_url": "cz.gopay.sdk://payment/return",
          "payment_instrument": {
            "payment_instrument": "PAYMENT_CARD",
            "details": {
              "input_type": "CARD_TOKEN",
              "masked_pan": "444444******4448"
            }
          },
          "action": {
            "action_type": "EMV3DS",
            "state": "CREATED",
            "redirect_url": "https://3ds.example.com/challenge"
          }
        }
    """.trimIndent()

    @Test
    fun `parses action correctly when present`() {
        val resp = adapter.fromJson(jsonWithAction)!!
        assertNotNull("action must not be null when server sends it", resp.action)
        assertEquals(ChargeActionType.EMV3DS, resp.action!!.actionType)
        assertEquals(Emv3dsState.CREATED, resp.action!!.state)
        assertEquals("https://3ds.example.com/challenge", resp.action!!.redirectUrl)
        assertEquals(ChargeState.ACTION_REQUIRED, resp.state)
        assertEquals("charge-abc-123", resp.id)
    }

    @Test
    fun `action is null when server omits it`() {
        val resp = adapter.fromJson(jsonWithoutAction)!!
        assertNull(resp.action)
        assertEquals(ChargeState.PROCESSING, resp.state)
    }

    @Test
    fun `parses correctly when payment_instrument lacks details`() {
        // Validates that a missing `details` field doesn't break parsing of the whole response,
        // including the action field that follows it in the JSON.
        val resp = adapter.fromJson(jsonWithInstrumentNoDetails)!!
        assertNotNull("action must survive even when payment_instrument.details is absent", resp.action)
        assertEquals(ChargeActionType.EMV3DS, resp.action!!.actionType)
    }

    @Test
    fun `parses correctly when both instrument and action are present`() {
        val resp = adapter.fromJson(jsonWithInstrumentAndAction)!!
        assertNotNull(resp.paymentInstrument)
        assertEquals("PAYMENT_CARD", resp.paymentInstrument!!.paymentInstrument)
        assertNotNull(resp.action)
        assertEquals(ChargeActionType.EMV3DS, resp.action!!.actionType)
    }

    @Test
    fun `encryptedCard request serializes to ENCRYPTED_CARD input shape`() {
        val requestAdapter = moshi.adapter(ChargePaymentRequest::class.java)
        val request = ChargePaymentRequest.encryptedCard(
            payload = "jwe.compact.string",
            browserData = BrowserData(
                language = "cs-CZ",
                timezone = -60,
                screenWidth = 1170,
                screenHeight = 2532,
                colorDepth = 24
            ),
            challengePreference = ChallengePreference.AUTO
        )

        // Re-parse the produced JSON into a generic map so the assertions read the wire shape,
        // not the Kotlin model. (org.json is stubbed in plain JVM unit tests, so use Moshi.)
        val mapType = Types.newParameterizedType(
            Map::class.java, String::class.java, Any::class.java
        )
        val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)
        val json = mapAdapter.fromJson(requestAdapter.toJson(request))!!

        // The deployed gateway rejects a request-level return_url, so charges must not send one.
        assertFalse("return_url must not be sent on a charge", json.containsKey("return_url"))
        @Suppress("UNCHECKED_CAST")
        val instrument = json["payment_instrument"] as Map<String, Any?>
        assertEquals("PAYMENT_CARD", instrument["payment_instrument"])
        assertEquals("AUTO", instrument["challenge_preference"])

        @Suppress("UNCHECKED_CAST")
        val input = instrument["input"] as Map<String, Any?>
        assertEquals("ENCRYPTED_CARD", input["input_type"])
        assertEquals("jwe.compact.string", input["payload"])
        // Fields from other variants must be omitted for an ENCRYPTED_CARD input.
        assertFalse("card_token must be absent", input.containsKey("card_token"))

        @Suppress("UNCHECKED_CAST")
        val browser = instrument["browser_data"] as Map<String, Any?>
        // Moshi decodes JSON numbers as Double into a generic Any map.
        assertEquals(1170.0, browser["screen_width"])
        assertEquals(24.0, browser["color_depth"])
    }
}
