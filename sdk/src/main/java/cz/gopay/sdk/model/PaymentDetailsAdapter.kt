package cz.gopay.sdk.model

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import cz.gopay.sdk.util.SdkLog
import java.lang.reflect.Type

/**
 * Reads the charge nested in a payment, [PaymentDetails.charge], without a `state` as one
 * in a state this SDK does not know, [ChargeState.UNKNOWN], instead of failing the whole payment.
 *
 * The gateway puts only the charge's `id`, `state` and `href` in the payment, and the payment is
 * what [cz.gopay.sdk.session.PaymentSession.getPaymentOutcome] reads when the app does not know
 * where it stands: a charge missing its state there must not make the payment unreadable. The
 * outcome is then undecided. A charge read on its own, `GET /payments/{payment_id}/charge`, is
 * decoded strictly as before. The iOS SDK reads the nested charge the same way.
 *
 * It lives on [cz.gopay.sdk.modules.network.NetworkModule.moshi].
 */
internal class PaymentDetailsAdapter(
    private val delegate: JsonAdapter<PaymentDetails>
) : JsonAdapter<PaymentDetails>() {

    override fun fromJson(reader: JsonReader): PaymentDetails? {
        // Looked at through a peek, so the usual payment is decoded by the delegate straight from
        // the reader, with its numbers as the model types them. Reading every payment into a
        // value tree first would turn each number into a Double on the way.
        if (!reader.peekJson().use(::hasChargeWithoutState)) return delegate.fromJson(reader)

        SdkLog.w("The charge in the payment came without a state, read as UNKNOWN")
        @Suppress("UNCHECKED_CAST")
        val payment = reader.readJsonValue() as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val charge = payment[CHARGE] as Map<String, Any?>
        return delegate.fromJsonValue(payment + (CHARGE to (charge + (STATE to ChargeState.UNKNOWN.name))))
    }

    /** Whether the payment in [peek] carries a `charge` object with no `state`, or a null one. */
    private fun hasChargeWithoutState(peek: JsonReader): Boolean {
        if (peek.peek() != JsonReader.Token.BEGIN_OBJECT) return false
        peek.beginObject()
        while (peek.hasNext()) {
            if (peek.nextName() != CHARGE || peek.peek() != JsonReader.Token.BEGIN_OBJECT) {
                peek.skipValue()
                continue
            }
            peek.beginObject()
            while (peek.hasNext()) {
                if (peek.nextName() == STATE && peek.peek() != JsonReader.Token.NULL) return false
                peek.skipValue()
            }
            return true
        }
        return false
    }

    override fun toJson(writer: JsonWriter, value: PaymentDetails?) =
        delegate.toJson(writer, value)

    companion object Factory : JsonAdapter.Factory {
        private const val CHARGE = "charge"
        private const val STATE = "state"

        override fun create(
            type: Type,
            annotations: MutableSet<out Annotation>,
            moshi: Moshi
        ): JsonAdapter<*>? {
            if (annotations.isNotEmpty() || Types.getRawType(type) != PaymentDetails::class.java) {
                return null
            }
            return PaymentDetailsAdapter(moshi.nextAdapter(this, type, annotations))
        }
    }
}
