package cz.gopay.sdk.model

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import cz.gopay.sdk.util.SdkLog
import java.lang.reflect.Type

/**
 * Reports a [ChargePaymentResponse] that came back without its required `return_url`.
 *
 * The spec marks `return_url` required on `Payment-Charge-Status-Response`, but the gateway
 * omits it from the `charge` block of `GET /payments/{payment_id}`, which returns only
 * `{id, state, href}`. [ChargePaymentResponse.returnUrl] is nullable for that reason, so the
 * decode itself needs no help; this adapter exists so the omission does not pass in silence.
 *
 * ### Only the charge endpoints' own response
 *
 * The nested block is the one case the gateway documents itself, so a warning there names a
 * state the gateway calls normal and the integrator can do nothing about; it would be noise in
 * every status read. Only a response decoded at the root of the document is reported, which is
 * what `GET /payments/{payment_id}/charge` and `POST /payments/{payment_id}/charge` return.
 * Mirrors the iOS `decoder.codingPath.isEmpty`.
 *
 * ### Why an adapter
 *
 * Everywhere else in the SDK a tolerated field is just a nullable constructor parameter, which
 * is what the field is now. That alone would be silent, and a required field quietly going
 * missing is how this stayed invisible until a payment failed in the field. The warning is a
 * quirk of one endpoint's wire format rather than a property of the type, and it is the
 * endpoint that should carry the note.
 *
 * The cost is that the warning lives on [cz.gopay.sdk.modules.network.NetworkModule.moshi]
 * instead of the type, so a second Moshi would decode the same response without it.
 * [cz.gopay.sdk.util.JsonUtils] is the only other one and it decodes theme documents and JWE
 * payloads, never a gateway response, so there is no second decode path to keep in step.
 */
internal class ChargePaymentResponseAdapter(
    private val delegate: JsonAdapter<ChargePaymentResponse>
) : JsonAdapter<ChargePaymentResponse>() {

    override fun fromJson(reader: JsonReader): ChargePaymentResponse? {
        // Read before the value: readJsonValue moves the reader on, and the path is what says
        // whether this is the charge endpoints' own response or the block nested in a payment.
        val isRootResponse = reader.path == ROOT_PATH
        val raw = reader.readJsonValue()
        // A blank `return_url` is dropped before decoding: `""` is the one value the nullable
        // type exists to keep out, because `url.startsWith("")` matches every URL and the caller
        // cannot tell it from an address the gateway meant.
        val value = if (raw is Map<*, *> && (raw[RETURN_URL] as? String)?.isBlank() == true) {
            raw - RETURN_URL
        } else {
            raw
        }
        // The value, not the key: an explicit `"return_url": null` says as little as an omitted
        // field does, and both reach the caller as null.
        if (isRootResponse && value is Map<*, *> && value[RETURN_URL] == null) {
            SdkLog.w(
                "Charge response has no usable \"$RETURN_URL\"; decoding it as null. The field " +
                    "is required on Payment-Charge-Status-Response."
            )
        }
        return delegate.fromJsonValue(value)
    }

    override fun toJson(writer: JsonWriter, value: ChargePaymentResponse?) =
        delegate.toJson(writer, value)

    companion object Factory : JsonAdapter.Factory {
        private const val RETURN_URL = "return_url"

        /** What `JsonReader.path` reads as at the root of a document. */
        private const val ROOT_PATH = "$"

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
