package com.gopay.example.checkout

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.exception.HttpErrorContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * After a charge call fails, the demo reads the payment instead of offering a new one whenever a
 * charge may still be on it (GPMOB-199): the gateway has one already, it answered with a 5xx, or
 * no answer arrived at all. Anything else is a failure the user may retry.
 */
class ChargeMayExistTest {

    private fun error(code: String, status: Int? = null) = GopaySDKException(
        errorCode = code,
        message = code,
        httpContext = status?.let { HttpErrorContext(statusCode = it) }
    )

    @Test
    fun `a charge the gateway already has may exist`() {
        assertTrue(error(GopayErrorCodes.PAYMENT_CHARGE_ALREADY_EXISTS, 409).chargeMayExist())
    }

    @Test
    fun `a 5xx may have left a charge`() {
        assertTrue(error(GopayErrorCodes.NETWORK_SERVER_ERROR, 503).chargeMayExist())
    }

    @Test
    fun `a transport failure without an answer may have left a charge`() {
        TRANSPORT_CODES.forEach { code -> assertTrue(code, error(code).chargeMayExist()) }
    }

    @Test
    fun `a transport code with an HTTP status does not count`() {
        TRANSPORT_CODES.forEach { code -> assertFalse(code, error(code, 200).chargeMayExist()) }
    }

    @Test
    fun `a refused charge or a missing payment leaves no charge`() {
        listOf(
            GopayErrorCodes.NETWORK_CLIENT_ERROR to 400,
            GopayErrorCodes.PAYMENT_NOT_FOUND to 404,
            GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED to null,
            GopayErrorCodes.VALIDATION_INVALID_INPUT to null,
            GopayErrorCodes.PAYMENT_CANCELLED to null
        ).forEach { (code, status) -> assertFalse(code, error(code, status).chargeMayExist()) }
    }
}
