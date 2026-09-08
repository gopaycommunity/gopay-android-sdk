package com.gopay.example

import android.app.Application
import cz.gopay.sdk.GopaySDK

class ExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize the SDK once on app start. See DemoConfig.kt for the values and the runtime
        // switcher (MainActivity's badge).
        val config = DemoConfig.buildConfig(DemoConfig.environment)
        GopaySDK.initialize(config)

        println("✅ Gopay SDK initialized")
        println("Environment: ${config.environment}")
        println("Debug: ${config.debug}")
    }
}
