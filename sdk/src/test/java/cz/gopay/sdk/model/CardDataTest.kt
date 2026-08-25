package cz.gopay.sdk.model

import cz.gopay.sdk.util.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the masked toString overrides (GPMOB-140): the data-class defaults would print the
 * full PAN and CVV into any log statement that touches these models.
 */
class CardDataTest {

    @Test
    fun cardData_toString_masksPanAndCvv() {
        val data = CardData(cardPan = "4444333322221111", expMonth = "12", expYear = "2030", cvv = "999")

        val printed = data.toString()

        assertFalse("PAN must not leak into toString", printed.contains("4444333322221111"))
        assertFalse("CVV must not leak into toString", printed.contains("999"))
        assertEquals("CardData(cardPan=****1111, expMonth=12, expYear=2030, cvv=***)", printed)
    }

    @Test
    fun cardData_toString_masksShortPanEntirely() {
        // A malformed short "PAN" must not be echoed via takeLast — mask it whole.
        val data = CardData(cardPan = "1234", expMonth = "12", expYear = "2030", cvv = "999")

        val printed = data.toString()

        assertFalse("Short PAN must not leak into toString", printed.contains("1234"))
        assertEquals("CardData(cardPan=****, expMonth=12, expYear=2030, cvv=***)", printed)
    }

    @Test
    fun cardData_moshiJson_stillCarriesTheFullPan() {
        // The mask lives in toString only. If someone ever "fixes a leak" by masking the
        // serializer, the JWE plaintext breaks and every payment fails — this guards that.
        val data = CardData(cardPan = "4444333322221111", expMonth = "12", expYear = "2030", cvv = "999")

        val json = requireNotNull(JsonUtils.toJson(data))

        assertTrue("JSON must carry the full PAN", json.contains("\"card_pan\":\"4444333322221111\""))
        assertTrue("JSON must carry the CVV", json.contains("\"cvv\":\"999\""))
    }

    @Test
    fun cardJwePayload_toString_masksPanAndCvv() {
        val payload = CardJwePayload(
            cardPan = "4444333322221111",
            expMonth = "12",
            expYear = "2030",
            cvv = "999",
            clientId = "SDK",
            iat = 1_000L,
            exp = 1_600L,
            jti = "android-1"
        )

        val printed = payload.toString()

        assertFalse("PAN must not leak into toString", printed.contains("4444333322221111"))
        assertFalse("CVV must not leak into toString", printed.contains("999"))
        assertEquals(
            "CardJwePayload(cardPan=****1111, expMonth=12, expYear=2030, cvv=***, " +
                "clientId=SDK, iat=1000, exp=1600, jti=android-1)",
            printed
        )
    }
}
