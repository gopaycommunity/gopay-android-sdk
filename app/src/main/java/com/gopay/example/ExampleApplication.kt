package com.gopay.example

import android.app.Application
import com.gopay.sdk.GopaySDK
import com.gopay.sdk.config.Environment
import com.gopay.sdk.config.GopayConfig

class ExampleApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize the Gopay SDK with global configuration
        val config = GopayConfig(
            environment = Environment.DEVELOPMENT.create("https://localhost:8080"),
            debug = true,
            requestTimeoutMs = 5000,
            errorCallback = { error ->
                // Global error reporting - in a real app you might want to:
                // - Log to analytics (Firebase, etc.)
                // - Send to crash reporting (Crashlytics, etc.)
                // - Store for debugging purposes
                println("Global SDK Error: [${error.errorCode}] ${error.message}")
                
                error.httpContext?.let { httpContext ->
                    println("HTTP Details: ${httpContext.statusCode} ${httpContext.requestMethod} ${httpContext.requestUrl}")
                    httpContext.responseBody?.let { body ->
                        println("Response Body: ${body.take(200)}...")
                    }
                }
                
                error.additionalData?.let { data ->
                    println("Additional Data: $data")
                }
            }
        )
        
        // Initialize SDK - context is handled automatically
        GopaySDK.initialize(config)
        
        println("✅ Gopay SDK initialized successfully!")
        println("Environment: ${config.environment}")
        println("Debug Logging: ${config.debug}")
        println("Request Timeout: ${config.requestTimeoutMs}ms")
    }
} 