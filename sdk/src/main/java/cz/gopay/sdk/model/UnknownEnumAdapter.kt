package cz.gopay.sdk.model

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import cz.gopay.sdk.util.SdkLog
import java.lang.reflect.Type

/**
 * Decodes a value of a gateway enum this SDK does not know as its `UNKNOWN` constant instead of
 * failing the whole response.
 *
 * The gateway adds states and schemes without notice. Without this, one new `state` would make
 * every `getChargeState()` throw
 * [cz.gopay.sdk.exception.GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR], even for a payment that
 * went through. The raw value goes to the debug log, so the integrator can see what the gateway
 * sent. Known values, `null` and encoding are left to Moshi's own enum adapter. Mirrors the iOS
 * `unknown` cases.
 *
 * The `UNKNOWN` constants are named in [Factory] rather than looked up by name, which R8 may
 * have changed in the host's build.
 */
internal class UnknownEnumAdapter<T : Enum<T>>(
    private val enumName: String,
    private val unknown: T,
    private val delegate: JsonAdapter<T>
) : JsonAdapter<T>() {

    override fun fromJson(reader: JsonReader): T? {
        if (reader.peek() != JsonReader.Token.STRING) return delegate.fromJson(reader)
        val path = reader.path
        val raw = reader.nextString()
        return try {
            delegate.fromJsonValue(raw)
        } catch (e: JsonDataException) {
            SdkLog.w(
                "Unknown $enumName value \"$raw\" at $path from the gateway, read as UNKNOWN; " +
                    "a newer SDK may know it"
            )
            unknown
        }
    }

    override fun toJson(writer: JsonWriter, value: T?) = delegate.toJson(writer, value)

    companion object Factory : JsonAdapter.Factory {
        /** Every enum the gateway sends back, with the constant an unknown value becomes. */
        private val UNKNOWN_BY_TYPE: Map<Class<*>, Enum<*>> = mapOf(
            ChargeState::class.java to ChargeState.UNKNOWN,
            ChargeActionType::class.java to ChargeActionType.UNKNOWN,
            Emv3dsState::class.java to Emv3dsState.UNKNOWN,
            CardScheme::class.java to CardScheme.UNKNOWN,
            PaymentState::class.java to PaymentState.UNKNOWN,
            Currency::class.java to Currency.UNKNOWN
        )

        override fun create(
            type: Type,
            annotations: MutableSet<out Annotation>,
            moshi: Moshi
        ): JsonAdapter<*>? {
            if (annotations.isNotEmpty()) return null
            val unknown = UNKNOWN_BY_TYPE[Types.getRawType(type)] ?: return null
            return adapterFor(unknown, moshi.nextAdapter<Any>(this, type, annotations))
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T : Enum<T>> adapterFor(unknown: Enum<*>, delegate: JsonAdapter<*>) =
            UnknownEnumAdapter(
                enumName = unknown.javaClass.simpleName,
                unknown = unknown as T,
                delegate = delegate as JsonAdapter<T>
            )
    }
}
