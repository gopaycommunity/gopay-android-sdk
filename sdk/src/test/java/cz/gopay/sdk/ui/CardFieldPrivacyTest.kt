package cz.gopay.sdk.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what the card form tells autofill and accessibility about each field, the contract the
 * iOS `GopayCardForm` keeps too.
 *
 * The semantics these decisions turn into need a composition, which a plain JVM unit test does
 * not have, so this covers the decisions; the semantics themselves were checked by hand, in the
 * accessibility tree and the keyboard's EditorInfo of the demo app on an emulator.
 */
class CardFieldPrivacyTest {

    @Test
    fun `no field is offered to autofill by default`() {
        CardField.values().forEach { field ->
            assertEquals(
                field.name,
                CardFieldAutofill.NONE,
                CardFieldPrivacy.autofillFor(field, allowsAutofill = false)
            )
        }
    }

    @Test
    fun `with autofill allowed the card number and the expiry are announced as such`() {
        assertEquals(
            CardFieldAutofill.CARD_NUMBER,
            CardFieldPrivacy.autofillFor(CardField.CARD_NUMBER, allowsAutofill = true)
        )
        assertEquals(
            CardFieldAutofill.EXPIRATION_DATE,
            CardFieldPrivacy.autofillFor(CardField.EXPIRATION, allowsAutofill = true)
        )
    }

    @Test
    fun `the CVV is never offered to autofill`() {
        assertEquals(
            CardFieldAutofill.NONE,
            CardFieldPrivacy.autofillFor(CardField.CVV, allowsAutofill = true)
        )
    }

    @Test
    fun `only the CVV is a secret field`() {
        assertTrue(CardFieldPrivacy.isSecret(CardField.CVV))
        assertFalse(CardFieldPrivacy.isSecret(CardField.CARD_NUMBER))
        assertFalse(CardFieldPrivacy.isSecret(CardField.EXPIRATION))
    }

    @Test
    fun `an expiry with a four-digit year keeps the month and the year's last two digits`() {
        assertEquals("1227", ExpiryEntry.digits("12/2027"))
        assertEquals("1227", ExpiryEntry.digits("12 / 2027"))
        assertEquals("0127", ExpiryEntry.digits("1/2027"))
        assertEquals("1227", ExpiryEntry.digits("122027"))
    }

    @Test
    fun `any other expiry input is read as before`() {
        assertEquals("1227", ExpiryEntry.digits("1227"))
        assertEquals("1227", ExpiryEntry.digits("12/27"))
        assertEquals("1227", ExpiryEntry.digits("12/271"))
        // A fifth digit typed into a full field is cut off.
        assertEquals("1227", ExpiryEntry.digits("12275"))
    }
}
