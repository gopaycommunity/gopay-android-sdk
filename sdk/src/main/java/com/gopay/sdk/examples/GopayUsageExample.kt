package com.gopay.sdk.examples

import com.gopay.sdk.GopaySDK
import com.gopay.sdk.config.Environment
import com.gopay.sdk.config.GopayConfig

/**
 * Example showing how to initialize and use the GopaySDK with different environments.
 */
object GopayUsageExample {

    /**
     * Setup the SDK with sandbox environment
     */
    fun setupSandboxSdk() {
        val config = GopayConfig(
            environment = Environment.SANDBOX,
            debugLoggingEnabled = true
        )
        
        GopaySDK.initialize(config)
    }
    
    /**
     * Setup the SDK with production environment
     */
    fun setupProductionSdk() {
        val config = GopayConfig(
            environment = Environment.PRODUCTION,
            requestTimeoutMs = 60000 // 60 seconds timeout
        )
        
        GopaySDK.initialize(config)
    }
    
    /**
     * Example of using the SDK after initialization
     */
    fun useSdk() {
        // Check if SDK is initialized
        if (!GopaySDK.isInitialized()) {
            setupSandboxSdk() // Initialize with sandbox for testing
        }
        
        // Get the SDK instance
        val sdk = GopaySDK.getInstance()
        
        // Test the SDK
        val greeting = sdk.helloWorld("Developer")
        println(greeting)
        
        // Get available payment methods
        val paymentMethods = sdk.getPaymentMethods()
        println("Available payment methods:")
        paymentMethods.forEach { method ->
            println("- ${method.name}: ${method.description}")
        }
        
        // Process a payment
        val paymentSuccess = sdk.processPayment("card", 100.0)
        println("Payment ${if (paymentSuccess) "successful" else "failed"}")
        
        // Check current environment
        val environment = sdk.config.environment
        println("Currently using ${environment.name} environment")
        println("API Base URL: ${sdk.config.apiBaseUrl}")
    }
    
    /**
     * Main function for running the example
     */
    @JvmStatic
    fun main(args: Array<String>) {
        useSdk()
    }
} 