package com.gopay.example

import cz.gopay.sdk.model.CardMetadata

/**
 * How the console prints the metadata `submitCardFormWithMetadata` returned: one line with the
 * fields the gateway sent, or [NO_METADATA]. The iOS demo prints the same line.
 */
object CardMetadataText {

    const val NO_METADATA = "cardMetadata: no metadata"

    fun line(metadata: CardMetadata?): String {
        if (metadata == null) return NO_METADATA
        val fields = listOfNotNull(
            "scheme=${metadata.scheme.name}",
            "corporate=${metadata.corporate}",
            metadata.brand?.let { "brand=$it" },
            metadata.serviceType?.let { "serviceType=${it.name}" },
            metadata.issuer?.let { "issuer=$it" },
            metadata.issuerCountry?.let { "issuerCountry=$it" }
        )
        return "cardMetadata: " + fields.joinToString(", ")
    }
}
