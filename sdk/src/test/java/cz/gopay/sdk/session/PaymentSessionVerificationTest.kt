package cz.gopay.sdk.session

import android.app.Activity
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.TokenResponse
import cz.gopay.sdk.ui.PaymentVerificationBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import retrofit2.Response

/**
 * `handle3dsVerification` refuses a redirect URL the challenge WebView could never load before
 * it touches the bridge or the activity, so the caller gets PAYMENT_010 straight away instead of
 * waiting on a challenge that never opened, and a verification already in flight is not what
 * gets reported.
 */
class PaymentSessionVerificationTest {

    private val activity = mock<Activity>()

    @Test
    fun `a non-web redirect URL is refused as unreachable before the bridge is consulted`() =
        refusesBeforeTheBridge("bankid://auth")

    @Test
    fun `an empty redirect URL is refused the same way`() = refusesBeforeTheBridge("")

    private fun refusesBeforeTheBridge(redirectUrl: String) {
        // With the bridge already taken, a check that ran after it would report IN_PROGRESS.
        val owner = PaymentVerificationBridge.register(CompletableDeferred())!!
        try {
            val e = assertThrows(GopaySDKException::class.java) {
                runBlocking { session().handle3dsVerification(activity, redirectUrl) }
            }

            assertEquals(GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE, e.errorCode)
            assertSame("the in-flight verification must be left alone", owner, PaymentVerificationBridge.currentOwner())
            verifyNoInteractions(activity)
        } finally {
            PaymentVerificationBridge.clear(owner)
        }
    }

    /** A live session, opened the way the SDK opens one. */
    private fun session(): PaymentSession = runBlocking {
        PaymentSession.Factory(
            authApi = object : AuthApi {
                override suspend fun token(
                    authorization: String,
                    grantType: String,
                    scope: String?
                ): Response<TokenResponse> =
                    Response.success(TokenResponse(accessToken = "jwt", tokenType = "Bearer"))
            },
            paymentApiBuilder = { mock<PaymentApi>() },
            publicApi = { throw UnsupportedOperationException() }
        ).create(paymentId = "pay-1", paymentSecret = "secret", scope = PaymentSession.DEFAULT_SCOPE) {}
    }
}
