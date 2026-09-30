package cz.gopay.sdk.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import cz.gopay.sdk.modules.network.NetworkModule
import cz.gopay.sdk.util.SdkLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class ChargeModelsTest {

    private val moshi = Moshi.Builder()
        .add(ChargePaymentResponseAdapter.Factory)
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(ChargePaymentResponse::class.java)
    private val defaultWarnSink = SdkLog.warnSink

    /** Every warning SdkLog emitted during a test; the sink is swapped in [collectWarnings]. */
    private val warnings = mutableListOf<String>()

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

    // The shape the gateway actually returns in `Payment-Details.charge` on
    // GET /payments/{id}: no return_url, although its own spec marks the field required.
    private val jsonWithoutReturnUrl = """
        {
          "id": "charge-abc-123",
          "state": "PROCESSING",
          "href": "https://gate.gopay.cz/api/payments/pay-1/charge"
        }
    """.trimIndent()

    // Same shape, but with the key present and null. Moshi tells the two apart; the fallback
    // must not.
    private val jsonWithNullReturnUrl = """
        {
          "id": "charge-abc-123",
          "state": "PROCESSING",
          "return_url": null
        }
    """.trimIndent()

    private val jsonWithBlankReturnUrl = """
        {
          "id": "charge-abc-123",
          "state": "PROCESSING",
          "return_url": ""
        }
    """.trimIndent()

    // The shape GET /payments/{id} actually returns: the charge nested in the payment, with no
    // return_url on it. This is the response the tolerance was written for.
    private val jsonPaymentWithNestedCharge = """
        {
          "id": "pay-1",
          "order_number": "order-1",
          "state": "PAYMENT_METHOD_CHOSEN",
          "amount": 100,
          "currency": "CZK",
          "customer": { "email": "buyer@example.com" },
          "gw_url": "https://gw.sandbox.gopay.com/gw/v3/pay-1",
          "charge": {
            "id": "charge-abc-123",
            "state": "PROCESSING",
            "href": "https://gate.gopay.cz/api/payments/pay-1/charge"
          }
        }
    """.trimIndent()

    @Before
    fun collectWarnings() {
        // No Android runtime here, so the real sink would write nowhere; collecting instead is
        // what lets a test say whether the omission was reported.
        SdkLog.warnSink = { warnings.add(it) }
    }

    @After
    fun restoreLogSink() {
        SdkLog.warnSink = defaultWarnSink
    }

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
    fun `decodes a charge response that omits return_url`() {
        val resp = adapter.fromJson(jsonWithoutReturnUrl)!!

        assertNull(resp.returnUrl)
        assertEquals("charge-abc-123", resp.id)
        assertEquals(ChargeState.PROCESSING, resp.state)
    }

    @Test
    fun `decodes a charge response whose return_url is explicitly null`() {
        val resp = adapter.fromJson(jsonWithNullReturnUrl)!!

        assertNull(resp.returnUrl)
        assertEquals("charge-abc-123", resp.id)
        assertEquals(ChargeState.PROCESSING, resp.state)
    }

    @Test
    fun `the SDK Moshi instance decodes a payment whose nested charge has no return_url`() {
        // Deliberately NetworkModule.moshi, not a Moshi built here: this is the instance every
        // SDK call decodes with.
        val paymentAdapter = NetworkModule.moshi.adapter(PaymentCreateResponse::class.java)

        val payment = paymentAdapter.fromJson(jsonPaymentWithNestedCharge)!!

        assertEquals("pay-1", payment.id)
        assertNotNull("the charge must survive the missing field", payment.charge)
        assertNull(payment.charge!!.returnUrl)
        assertEquals(ChargeState.PROCESSING, payment.charge!!.state)
        // The gateway documents that it omits the field here, so reporting it would name a
        // state the gateway itself calls normal, on every status read. iOS stays quiet too.
        assertTrue("a documented omission must not be reported", warnings.isEmpty())
    }

    @Test
    fun `the SDK Moshi instance reports a bare charge without return_url`() {
        // Pins the registration on the instance every SDK call decodes with: the nullable field
        // would decode without the adapter, so the warning is the only thing that fails if the
        // factory is dropped from NetworkModule.
        val sdkAdapter = NetworkModule.moshi.adapter(ChargePaymentResponse::class.java)

        val resp = sdkAdapter.fromJson(jsonWithoutReturnUrl)!!

        assertNull(resp.returnUrl)
        assertEquals("charge-abc-123", resp.id)
        assertEquals("the omission must be reported", 1, warnings.size)
        assertTrue(warnings.single().contains("return_url"))
    }

    @Test
    fun `warns when return_url is explicitly null`() {
        adapter.fromJson(jsonWithNullReturnUrl)

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("return_url"))
    }

    @Test
    fun `a blank return_url decodes as null and is reported`() {
        // `""` is the value the nullable type exists to keep out: `url.startsWith("")` matches
        // every URL, so a caller who trusted it would finish the verification on its first page.
        val resp = adapter.fromJson(jsonWithBlankReturnUrl)!!

        assertNull(resp.returnUrl)
        assertEquals("charge-abc-123", resp.id)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("return_url"))
    }

    @Test
    fun `does not warn when return_url is present`() {
        adapter.fromJson(jsonWithoutAction)

        assertTrue(warnings.isEmpty())
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
