package cz.gopay.sdk.session

import android.app.Activity
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.ChargeAction
import cz.gopay.sdk.model.ChargeActionType
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.TokenResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * `awaitChargeState` on the session itself: the options are checked before anything goes out,
 * and the manual mode reads the charge through the session's own endpoint. The loop's behaviour
 * is covered in [ChargePollerTest].
 */
class PaymentSessionAwaitChargeTest {

    @Test
    fun `automatic mode without an activity is refused before the first read`() {
        val api = mock<PaymentApi>()

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session(api).awaitChargeState() }
        }

        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, e.errorCode)
        verifyNoInteractions(api)
    }

    @Test
    fun `a non-positive interval or timeout is refused before the first read`() {
        val api = mock<PaymentApi>()
        val bad = listOf(
            AwaitChargeOptions(intervalMs = 0, threeDSMode = ThreeDSMode.MANUAL),
            AwaitChargeOptions(initialTimeoutMs = -1, threeDSMode = ThreeDSMode.MANUAL),
            AwaitChargeOptions(intervalMs = 0, activity = mock<Activity>())
        )

        for (options in bad) {
            val e = assertThrows(options.toString(), GopaySDKException::class.java) {
                runBlocking { session(api).awaitChargeState(options) }
            }
            assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, e.errorCode)
        }
        verifyNoInteractions(api)
    }

    @Test
    fun `manual mode reads the charge through the session until it succeeds`() = runTest {
        val api = mock<PaymentApi> {
            onBlocking { getChargeState(any()) }
                .thenReturn(Response.success(charge(ChargeState.PROCESSING)))
                .thenReturn(Response.success(charge(ChargeState.SUCCEEDED)))
        }
        val seen = mutableListOf<ChargeState>()

        val result = session(api).awaitChargeState(
            AwaitChargeOptions(threeDSMode = ThreeDSMode.MANUAL, onStateChange = { seen.add(it.state) })
        )

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertEquals(listOf(ChargeState.PROCESSING, ChargeState.SUCCEEDED), seen)
        verify(api, times(2)).getChargeState("pay-1")
    }

    @Test
    fun `a read that fails ends the call with the session's own error`() {
        val api = mock<PaymentApi> {
            onBlocking { getChargeState(any()) }.thenReturn(Response.error(503, "".toResponseBody()))
        }

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking {
                session(api).awaitChargeState(AwaitChargeOptions(threeDSMode = ThreeDSMode.MANUAL))
            }
        }

        assertEquals(GopayErrorCodes.NETWORK_SERVER_ERROR, e.errorCode)
        runBlocking { verify(api, times(1)).getChargeState("pay-1") }
    }

    @Test
    fun `automatic mode hands a new address to handle3dsVerification, whose error ends the call`() {
        // A redirect the challenge WebView cannot load is refused by handle3dsVerification before
        // it touches the activity, so the glue is exercised without a screen.
        val api = mock<PaymentApi> {
            onBlocking { getChargeState(any()) }.thenReturn(
                Response.success(
                    ChargePaymentResponse(
                        id = "ch-1",
                        state = ChargeState.ACTION_REQUIRED,
                        action = ChargeAction(actionType = ChargeActionType.EMV3DS, redirectUrl = "intent://x")
                    )
                )
            )
        }
        val activity = mock<Activity>()
        val events = mutableListOf<String>()

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking {
                session(api).awaitChargeState(
                    AwaitChargeOptions(
                        activity = activity,
                        onStateChange = { events.add("state:${it.state}") },
                        onActionRequired = { events.add("action:$it") }
                    )
                )
            }
        }

        assertEquals(GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE, e.errorCode)
        assertEquals(listOf("state:ACTION_REQUIRED", "action:intent://x"), events)
        runBlocking { verify(api, times(1)).getChargeState("pay-1") }
        verifyNoMoreInteractions(api)
        verifyNoInteractions(activity)
    }

    private fun charge(state: ChargeState) = ChargePaymentResponse(id = "ch-1", state = state)

    /** A live session, opened the way the SDK opens one, over a faked payment API. */
    private suspend fun session(api: PaymentApi): PaymentSession =
        PaymentSession.Factory(
            authApi = object : AuthApi {
                override suspend fun token(
                    authorization: String,
                    grantType: String,
                    scope: String?
                ): Response<TokenResponse> =
                    Response.success(TokenResponse(accessToken = "jwt", tokenType = "Bearer"))
            },
            paymentApiBuilder = { api },
            publicApi = { throw UnsupportedOperationException() }
        ).create(paymentId = "pay-1", paymentSecret = "secret", scope = PaymentSession.DEFAULT_SCOPE) {}
}
