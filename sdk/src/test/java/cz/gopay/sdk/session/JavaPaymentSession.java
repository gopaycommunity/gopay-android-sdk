package cz.gopay.sdk.session;

import android.app.Activity;

import cz.gopay.sdk.model.BrowserData;
import cz.gopay.sdk.model.ChallengePreference;
import cz.gopay.sdk.model.ChargePaymentRequest;
import cz.gopay.sdk.model.ChargePaymentResponse;
import cz.gopay.sdk.model.GooglePayInfoResponse;
import cz.gopay.sdk.model.PaymentDetails;
import cz.gopay.sdk.model.PaymentOutcome;
import cz.gopay.sdk.model.QrCodeFormat;
import cz.gopay.sdk.model.QrPaymentDetails;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

/**
 * A session implemented in Java, as an app written in Java would for its tests. It compiles only
 * while the forms of 2.x with the Activity first are JVM default methods of the interface: it
 * implements none of them. See PaymentSessionApiTest.
 */
public final class JavaPaymentSession implements PaymentSessionApi {

    /** The redirect URL of the last verification. */
    public String verified;

    @Override
    public String getPaymentId() {
        return "pay-java";
    }

    @Override
    public Object getStatus(Continuation<? super PaymentDetails> continuation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getPaymentOutcome(Continuation<? super PaymentOutcome> continuation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object charge(ChargePaymentRequest request, Continuation<? super ChargePaymentResponse> continuation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object completeBrowserData(BrowserData browserData, Continuation<? super BrowserData> continuation) {
        return browserData;
    }

    @Override
    public Object getChargeState(Continuation<? super ChargePaymentResponse> continuation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object awaitChargeState(AwaitChargeOptions options, Continuation<? super ChargePaymentResponse> continuation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getQrPaymentInfo(QrCodeFormat format, Continuation<? super QrPaymentDetails> continuation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getWalletInfo(Continuation<? super GooglePayInfoResponse> continuation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object chargeWithWallet(
            BrowserData browserData,
            ChallengePreference challengePreference,
            Activity activity,
            Continuation<? super ChargePaymentResponse> continuation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object chargeWithCardToken(
            String cardToken,
            BrowserData browserData,
            ChallengePreference challengePreference,
            Activity activity,
            Continuation<? super ChargePaymentResponse> continuation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object chargeWithEncryptedCard(
            String payload,
            BrowserData browserData,
            ChallengePreference challengePreference,
            Activity activity,
            Continuation<? super ChargePaymentResponse> continuation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object handle3dsVerification(
            String redirectUrl, String returnUrl, Activity activity, Continuation<? super Unit> continuation) {
        verified = redirectUrl;
        return Unit.INSTANCE;
    }

    @Override
    public void close() {
    }
}
