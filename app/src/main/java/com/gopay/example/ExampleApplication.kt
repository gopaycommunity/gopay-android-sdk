package com.gopay.example

import android.app.Application
import cz.gopay.sdk.GopaySDK

class ExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize the SDK once on app start, on the development environment. See DemoConfig.kt
        // for the environment/credential bundles and the runtime switcher (MainActivity's badge).
        // An Application has no access to the launch intent, so a base URL passed as an intent
        // extra is applied a moment later by MainActivity, which re-initializes the SDK.
        val config = DemoConfig.buildConfig(DemoEnvironment.DEVELOPMENT)
        GopaySDK.initialize(config)

        println("✅ Gopay SDK initialized")
        println("Environment: ${config.environment}")
        println("Debug: ${config.debug}")
    }
}
