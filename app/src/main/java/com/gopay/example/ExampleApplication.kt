package com.gopay.example

import android.app.Application
import cz.gopay.sdk.GopaySDK

class ExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize the SDK once on app start, on the development environment. See DemoConfig.kt
        // for the environment/credential bundles and the runtime switcher (MainActivity's badge).
        val config = DemoConfig.buildConfig(DemoEnvironment.DEVELOPMENT)
        GopaySDK.initialize(config)

        println("✅ Gopay SDK initialized")
        println("Environment: ${config.environment}")
        println("Debug: ${config.debug}")
    }
}
