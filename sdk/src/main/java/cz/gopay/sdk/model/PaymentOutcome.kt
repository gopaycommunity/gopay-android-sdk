package cz.gopay.sdk.model

/**
 * What became of a payment, read in one call by
 * [cz.gopay.sdk.session.PaymentSession.getPaymentOutcome] from `GET /payments/{payment_id}`:
 * the [outcome], decided from the payment's state and its charge, with the [payment] it was
 * decided from. The iOS SDK returns the same outcome from the method of the same name.
 *
 * The one the SDK returns always has the outcome its rules give the payment. The constructor is
 * public so that a test can build the value a session returns, see
 * [cz.gopay.sdk.session.PaymentSessionApi]; it takes the [outcome] as given and does not check
 * it against the [payment]. The [charge] is always the payment's own. It is what the app knows,
 * not proof of payment: your backend settles the order from the notification GoPay sends it.
 */
class PaymentOutcome(
    val outcome: Outcome,
    /** The payment as `GET /payments/{payment_id}` returned it, the model of `getStatus()`. */
    val payment: PaymentDetails
) {
    /**
     * The payment's charge, when the gateway reports one; the same as `payment.charge`. The
     * gateway puts only its `id` and `state` here: read
     * [cz.gopay.sdk.session.PaymentSession.getChargeState] for its `failReason`.
     */
    val charge: ChargePaymentResponse?
        get() = payment.charge

    /** What became of the payment. */
    enum class Outcome {
        /**
         * The payment is paid: its state is `PAID`, `AUTHORIZED`, `REFUNDED` or
         * `PARTIALLY_REFUNDED`, whatever its charge says, or it is still open and its charge
         * `SUCCEEDED`.
         */
        PAID,

        /**
         * The charge ended `FAILED`. The payment is over: a new payment is needed to try again.
         */
        DECLINED,

        /**
         * Nothing was taken: the payment has no charge and is `CREATED` or
         * `PAYMENT_METHOD_CHOSEN`, so it can still be charged, or it is `CANCELED` or `TIMEOUTED`
         * without a failed charge, so a new payment is needed.
         */
        NOT_CHARGED,

        /**
         * Not decided yet. Either the charge is `REQUESTED`, `PROCESSING`, `ACTION_REQUIRED` or a
         * state this SDK does not know, so wait for it with
         * [cz.gopay.sdk.session.PaymentSession.awaitChargeState]; or the payment has no charge
         * and a state this SDK does not know, so read it again later or ask your backend.
         */
        UNDECIDED
    }

    override fun equals(other: Any?): Boolean =
        other is PaymentOutcome && outcome == other.outcome && payment == other.payment

    override fun hashCode(): Int = 31 * outcome.hashCode() + payment.hashCode()

    override fun toString(): String = "PaymentOutcome(outcome=$outcome, payment=$payment)"

    internal companion object {
        /**
         * The outcome of [payment], in this order:
         * 1. a payment `PAID`, `AUTHORIZED`, `REFUNDED` or `PARTIALLY_REFUNDED` is [Outcome.PAID],
         *    whatever its charge says: a refund presumes a payment;
         * 2. a payment `CANCELED` or `TIMEOUTED` is over: [Outcome.DECLINED] with a `FAILED`
         *    charge, otherwise [Outcome.NOT_CHARGED], since nothing was taken or it was released;
         * 3. any other payment goes by its charge: `SUCCEEDED` is [Outcome.PAID], `FAILED`
         *    [Outcome.DECLINED], the rest, `UNKNOWN` included, [Outcome.UNDECIDED];
         * 4. without a charge, a payment in a state this SDK does not know is
         *    [Outcome.UNDECIDED], `CREATED` and `PAYMENT_METHOD_CHOSEN` are [Outcome.NOT_CHARGED].
         *
         * The iOS SDK decides by the same rules.
         */
        fun of(payment: PaymentDetails): PaymentOutcome {
            val chargeState = payment.charge?.state
            val outcome = when (payment.state) {
                PaymentState.PAID, PaymentState.AUTHORIZED, PaymentState.REFUNDED,
                PaymentState.PARTIALLY_REFUNDED -> Outcome.PAID

                PaymentState.CANCELED, PaymentState.TIMEOUTED ->
                    if (chargeState == ChargeState.FAILED) Outcome.DECLINED else Outcome.NOT_CHARGED

                PaymentState.CREATED, PaymentState.PAYMENT_METHOD_CHOSEN, PaymentState.UNKNOWN ->
                    when (chargeState) {
                        ChargeState.SUCCEEDED -> Outcome.PAID
                        ChargeState.FAILED -> Outcome.DECLINED
                        ChargeState.REQUESTED, ChargeState.PROCESSING, ChargeState.ACTION_REQUIRED,
                        ChargeState.UNKNOWN -> Outcome.UNDECIDED
                        // No charge: a state this SDK does not know may be anything.
                        null -> if (payment.state == PaymentState.UNKNOWN) {
                            Outcome.UNDECIDED
                        } else {
                            Outcome.NOT_CHARGED
                        }
                    }
            }
            return PaymentOutcome(outcome, payment)
        }
    }
}
