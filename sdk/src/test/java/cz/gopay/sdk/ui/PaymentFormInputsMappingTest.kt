package cz.gopay.sdk.ui

import cz.gopay.sdk.locales.GopayLocales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentFormInputsMappingTest {

    @Test
    fun from_englishLocale_mapsLabelsAndPlaceholders() {
        val inputs = PaymentFormInputs.from(GopayLocales.EN)

        assertEquals("Card number", inputs.cardNumber.label)
        assertEquals("1234 5678 9012 3456", inputs.cardNumber.placeholder)
        assertEquals("Expiration date", inputs.expirationDate.label)
        assertEquals("MM/YY", inputs.expirationDate.placeholder)
        assertEquals("CVV", inputs.cvv.label)
        assertEquals("123", inputs.cvv.placeholder)
    }

    @Test
    fun from_czechLocale_mapsLocalizedLabels() {
        val inputs = PaymentFormInputs.from(GopayLocales.CS)

        assertEquals("Číslo karty", inputs.cardNumber.label)
        assertEquals("Platnost", inputs.expirationDate.label)
        assertEquals("MM/RR", inputs.expirationDate.placeholder)
    }

    @Test
    fun from_leavesErrorAndHelperTextUnset() {
        val inputs = PaymentFormInputs.from(GopayLocales.CS)

        assertNull(inputs.cardNumber.errorText)
        assertNull(inputs.cardNumber.helperText)
        assertNull(inputs.expirationDate.errorText)
        assertNull(inputs.cvv.errorText)
    }
}
