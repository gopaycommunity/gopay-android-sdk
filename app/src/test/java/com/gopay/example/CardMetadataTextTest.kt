package com.gopay.example

import cz.gopay.sdk.model.CardMetadata
import cz.gopay.sdk.model.CardScheme
import cz.gopay.sdk.model.CardServiceType
import org.junit.Assert.assertEquals
import org.junit.Test

/** The line the console prints for the metadata of `submitCardFormWithMetadata`; the iOS demo prints the same. */
class CardMetadataTextTest {

    @Test
    fun `no metadata is said as such`() {
        assertEquals("cardMetadata: no metadata", CardMetadataText.line(null))
    }

    @Test
    fun `full metadata is printed field by field, in the gateway's spelling`() {
        val metadata = CardMetadata(
            scheme = CardScheme.VISA,
            corporate = false,
            brand = "GOLD",
            serviceType = CardServiceType.DEBIT,
            issuer = "Example Bank",
            issuerCountry = "CZE"
        )

        assertEquals(
            "cardMetadata: scheme=VISA, corporate=false, brand=GOLD, serviceType=DEBIT, " +
                "issuer=Example Bank, issuerCountry=CZE",
            CardMetadataText.line(metadata)
        )
    }

    @Test
    fun `a field the gateway did not send is left out`() {
        assertEquals(
            "cardMetadata: scheme=MASTERCARD, corporate=true",
            CardMetadataText.line(CardMetadata(scheme = CardScheme.MASTERCARD, corporate = true))
        )
    }
}
