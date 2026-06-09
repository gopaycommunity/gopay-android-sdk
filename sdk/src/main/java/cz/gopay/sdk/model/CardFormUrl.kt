package cz.gopay.sdk.model

import com.squareup.moshi.Json

/**
 * Response body for `GET /cards/card-form-url`.
 * Maps to components.schemas.Card-Form-URL in Payments.yaml.
 */
data class CardFormUrl(
    @Json(name = "card_form_url")
    val cardFormUrl: String
)
