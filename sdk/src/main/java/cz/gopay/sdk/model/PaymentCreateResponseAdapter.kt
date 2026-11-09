package cz.gopay.sdk.model

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import cz.gopay.sdk.util.SdkLog
import java.lang.reflect.Type

/**
 * Reads the charge nested in a payment, [PaymentCreateResponse.charge], without a `state` as one
 * in a state this SDK does not know, [ChargeState.UNKNOWN], instead of failing the whole payment.
 *
 * The gateway puts only the charge's `id`, `state` and `href` in the payment, and the payment is
 * what [cz.gopay.sdk.session.PaymentSession.getPaymentOutcome] reads when the app does not know
 * where it stands: a charge missing its state there must not make the payment unreadable. The
 * outcome is then undecided. A charge read on its own, `GET /payments/{payment_id}/charge`, is
 * decoded strictly as before. The iOS SDK reads the nested charge the same way.
 *
 * It lives on [cz.gopay.sdk.modules.network.NetworkModule.moshi], like
 * [ChargePaymentResponseAdapter].
 */
internal class PaymentCreateResponseAdapter(
    private val delegate: JsonAdapter<PaymentCreateResponse>
) : JsonAdapter<PaymentCreateResponse>() {

    override fun fromJson(reader: JsonReader): PaymentCreateResponse? {
        val raw = reader.readJsonValue()
        @Suppress("UNCHECKED_CAST")
        val payment = raw as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val charge = payment?.get(CHARGE) as? Map<String, Any?>
        if (payment == null || charge == null || charge[STATE] != null) {
            return delegate.fromJsonValue(raw)
        }
        SdkLog.w("The charge in the payment came without a state, read as UNKNOWN")
        val tolerant = charge + (STATE to ChargeState.UNKNOWN.name)
        return delegate.fromJsonValue(payment + (CHARGE to tolerant))
    }

    override fun toJson(writer: JsonWriter, value: PaymentCreateResponse?) =
        delegate.toJson(writer, value)

    companion object Factory : JsonAdapter.Factory {
        private const val CHARGE = "charge"
        private const val STATE = "state"

        override fun create(
            type: Type,
            annotations: MutableSet<out Annotation>,
            moshi: Moshi
        ): JsonAdapter<*>? {
            if (annotations.isNotEmpty() || Types.getRawType(type) != PaymentCreateResponse::class.java) {
                return null
            }
            return PaymentCreateResponseAdapter(moshi.nextAdapter(this, type, annotations))
        }
    }
}
