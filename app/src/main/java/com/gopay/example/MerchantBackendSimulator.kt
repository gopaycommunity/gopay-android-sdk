package com.gopay.example

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * DEMO ONLY.
 *
 * In a real app these calls happen on YOUR server, authenticated with merchant
 * `client_credentials`; the app never sees the client secret. The app receives only the
 * resulting `payment_id` + `payment_secret` and hands those to the SDK
 * (`GopaySDK.getInstance().startPaymentSession`). This object fakes that server so the
 * demo is self-contained.
 *
 * Mirrors the iOS example's `MerchantBackendSimulator`.
 */
object MerchantBackendSimulator {

    data class CreatedPayment(val paymentId: String, val paymentSecret: String)

    /** Authenticates as the merchant and creates a payment, returning the pair the SDK needs. */
    suspend fun createPayment(amount: Int, currency: String): CreatedPayment =
        withContext(Dispatchers.IO) {
            val token = merchantToken()
            createPaymentWithToken(token, amount, currency)
        }

    /**
     * Tokenizes a JWE-encrypted card via `POST /cards/tokens` (requires `card:write`) and returns
     * the resulting card token. In production the app sends the JWE to YOUR server and it does this.
     */
    suspend fun tokenizeCard(jwe: String): String = withContext(Dispatchers.IO) {
        val token = merchantToken()
        val conn = openConnection("cards/tokens").apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true
        }
        conn.outputStream.use { it.write(JSONObject().put("payload", jwe).toString().toByteArray()) }
        val resp = readResponse(conn, "tokenize card")
        resp.getString("token")
    }

    private fun merchantToken(): String {
        val credentials = "${DemoConfig.CLIENT_ID}:${DemoConfig.CLIENT_SECRET}"
        val basic = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        val conn = openConnection("oauth2/token").apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Basic $basic")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
            doOutput = true
        }
        val body = "grant_type=client_credentials&scope=payment:write%20payment:read%20card:write%20card:read"
        conn.outputStream.use { it.write(body.toByteArray()) }
        return readResponse(conn, "acquire merchant token").getString("access_token")
    }

    private fun createPaymentWithToken(token: String, amount: Int, currency: String): CreatedPayment {
        val conn = openConnection("eshops/${DemoConfig.GOID}/payments").apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true
        }
        val body = JSONObject()
            .put("amount", amount)
            .put("currency", currency)
            .put("order_number", "demo-${System.currentTimeMillis()}")
            .put("order_description", "GoPay SDK demo payment")
            .put("customer", JSONObject().put("email", "demo@example.com"))
            .put(
                "callback", JSONObject()
                    .put("notification_url", "https://example.com/gopay/notify")
                    .put("return_url", DemoConfig.CHARGE_RETURN_URL)
            )
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val resp = readResponse(conn, "create payment")
        val paymentId = resp.get("id").toString()
        val paymentSecret = resp.getString("payment_secret")
        return CreatedPayment(paymentId = paymentId, paymentSecret = paymentSecret)
    }

    private fun openConnection(path: String): HttpURLConnection =
        (URL(DemoConfig.BASE_URL + path).openConnection() as HttpURLConnection)

    private fun readResponse(conn: HttpURLConnection, action: String): JSONObject {
        val status = conn.responseCode
        val text = try {
            if (status in 200..299) conn.inputStream.bufferedReader().readText()
            else conn.errorStream?.bufferedReader()?.readText() ?: ""
        } catch (_: Exception) { "" }
        if (status !in 200..299) throw RuntimeException("Failed to $action: HTTP $status $text")
        return JSONObject(text)
    }
}
