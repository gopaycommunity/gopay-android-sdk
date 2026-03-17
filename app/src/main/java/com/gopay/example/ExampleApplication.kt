package com.gopay.example

import android.app.Application
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig

class ExampleApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize the Gopay SDK with global configuration
        val config = GopayConfig(
            environment = Environment.DEVELOPMENT.create("https://gw.alpha8.dev.gopay.com/gp-gw/api/4.0"),
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

        // Example usage of createPayment API (for demonstration purposes only)
        // In a real application, this should be called from a coroutine scope.
        /*
        val sdk = GopaySDK.getInstance()
        val request = PaymentCreateRequest(
            amount = 10000, // in cents
            currency = Currency.CZK,
            orderNumber = "2025010199",
            orderDescription = "Test order from ExampleApplication",
            customer = PaymentCustomer(
                email = "john.doe@example.com",
                firstName = "John",
                lastName = "Doe"
            ),
            callback = PaymentCallback(
                notificationUrl = "https://example.com/notify",
                returnUrl = "https://example.com/return"
            )
        )

        // Example coroutine usage:
        // CoroutineScope(Dispatchers.IO).launch {
        //     try {
        //         val response = sdk.createPayment(goid = "123456", request = request)
        //         println("Created payment with ID: ${response.id}, gwUrl: ${response.gwUrl}")
        //     } catch (e: Exception) {
        //         println("Failed to create payment: ${e.message}")
        //     }
        // }
        */
    }
} 