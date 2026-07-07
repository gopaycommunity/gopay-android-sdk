package com.gopay.example

import android.app.Application
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.locales.GopayLocales

class ExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // For local development against the dev gateway. Production apps use Environment.PRODUCTION
        // (or SANDBOX during integration testing) and read clientId/shareableKey from a secure config.
        val config = GopayConfig(
            environment = Environment.DEVELOPMENT.create(DemoConfig.BASE_URL),
            clientId = DemoConfig.CLIENT_ID,
            shareableKey = DemoConfig.SHAREABLE_KEY,
            debug = true,
            requestTimeoutMs = 5_000,
            // Register a custom locale (code "xx") the form can select alongside the built-ins, and
            // leave `locale = null` so the default follows the device language (falling back to cs).
            customLocales = mapOf(
                "xx" to GopayLocales.EN.copy(
                    panLabel = "Yer card number",
                    expLabel = "Doom date",
                    cvvLabel = "Secret code"
                )
            ),
            errorCallback = { error ->
                println("Global SDK Error: [${error.errorCode}] ${error.message}")
                error.httpContext?.let { http ->
                    println("HTTP: ${http.statusCode} ${http.requestMethod} ${http.requestUrl}")
                    http.responseBody?.let { body -> println("Body: ${body.take(200)}…") }
                }
                error.additionalData?.let { data -> println("Additional: $data") }
            }
        )

        GopaySDK.initialize(config)

        println("✅ Gopay SDK initialized")
        println("Environment: ${config.environment}")
        println("Debug: ${config.debug}")
    }
}
