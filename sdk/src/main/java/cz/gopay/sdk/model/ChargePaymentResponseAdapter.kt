package cz.gopay.sdk.model

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

/**
 * Keeps a blank `return_url` out of a [ChargePaymentResponse]: it decodes as null, like a missing
 * one.
 *
 * `""` is the one value the nullable [ChargePaymentResponse.returnUrl] exists to keep out, because
 * `url.startsWith("")` matches every URL and a caller could not tell it from an address the
 * gateway meant. GoPay is removing `return_url` from the charge response, so a missing one is
 * expected and not reported; the field is deprecated.
 *
 * It lives on [cz.gopay.sdk.modules.network.NetworkModule.moshi], so a second Moshi would decode
 * the same response without it. [cz.gopay.sdk.util.JsonUtils] is the only other one and it never
 * decodes a gateway response.
 */
internal class ChargePaymentResponseAdapter(
    private val delegate: JsonAdapter<ChargePaymentResponse>
) : JsonAdapter<ChargePaymentResponse>() {

    override fun fromJson(reader: JsonReader): ChargePaymentResponse? {
        val raw = reader.readJsonValue()
        // A blank `return_url` is dropped before decoding: `""` is the one value the nullable
        // type exists to keep out, because `url.startsWith("")` matches every URL and the caller
        // cannot tell it from an address the gateway meant.
        val value = if (raw is Map<*, *> && (raw[RETURN_URL] as? String)?.isBlank() == true) {
            raw - RETURN_URL
        } else {
            raw
        }
        return delegate.fromJsonValue(value)
    }

    override fun toJson(writer: JsonWriter, value: ChargePaymentResponse?) =
        delegate.toJson(writer, value)

    companion object Factory : JsonAdapter.Factory {
        private const val RETURN_URL = "return_url"

        override fun create(
            type: Type,
            annotations: MutableSet<out Annotation>,
            moshi: Moshi
        ): JsonAdapter<*>? {
            if (annotations.isNotEmpty() || Types.getRawType(type) != ChargePaymentResponse::class.java) {
                return null
            }
            return ChargePaymentResponseAdapter(moshi.nextAdapter(this, type, annotations))
        }
    }
}
