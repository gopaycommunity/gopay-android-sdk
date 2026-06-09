package com.gopay.example

import android.app.Application
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig

class ExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // For local development against the dev gateway. Production apps use Environment.PRODUCTION
        // (or SANDBOX during integration testing) and read clientId/shareableKey from a secure config.
        val config = GopayConfig(
            environment = Environment.DEVELOPMENT.create("https://stoplight.io/mocks/gopay-api/merchant-v4/10757016"),
            clientId = "SDK",
            shareableKey = "FILLINYOURPASSWORD",
            debug = true,
            requestTimeoutMs = 5_000,
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
