package com.gopay.sdk.service

import com.gopay.sdk.GopaySDK
import com.gopay.sdk.config.Environment
import com.gopay.sdk.config.GopayConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaymentServiceTest {

    private lateinit var paymentService: PaymentService
    
    @Before
    fun setup() {
        // Initialize fresh service for each test
        paymentService = PaymentService()
    }
    
    @Test
    fun testProcessPaymentInSandbox() {
        // Initialize SDK with sandbox environment
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX))
        
        // In sandbox, all payments should succeed
        assertTrue(paymentService.processPayment("card", 100.0))
        assertTrue(paymentService.processPayment("bank", 100.0))
        assertTrue(paymentService.processPayment("wallet", 100.0))
        assertTrue(paymentService.processPayment("card", 0.0))
        assertTrue(paymentService.processPayment("card", -10.0))
    }
    
    @Test
    fun testProcessPaymentInDevelopment() {
        // Initialize SDK with development environment
        GopaySDK.initialize(GopayConfig(environment = Environment.DEVELOPMENT))
        
        // In development, bank transfers should fail
        assertTrue(paymentService.processPayment("card", 100.0))
        assertFalse(paymentService.processPayment("bank", 100.0))
        assertTrue(paymentService.processPayment("wallet", 100.0))
    }
    
    @Test
    fun testProcessPaymentInProduction() {
        // Initialize SDK with production environment
        GopaySDK.initialize(GopayConfig(environment = Environment.PRODUCTION))
        
        // In production, only positive amounts should succeed
        assertTrue(paymentService.processPayment("card", 100.0))
        assertTrue(paymentService.processPayment("bank", 50.0))
        assertTrue(paymentService.processPayment("wallet", 0.01))
        assertFalse(paymentService.processPayment("card", 0.0))
        assertFalse(paymentService.processPayment("bank", -10.0))
    }
    
    @Test
    fun testProcessPaymentInStaging() {
        // Initialize SDK with staging environment
        GopaySDK.initialize(GopayConfig(environment = Environment.STAGING))
        
        // In staging, only positive amounts should succeed
        assertTrue(paymentService.processPayment("card", 100.0))
        assertTrue(paymentService.processPayment("bank", 50.0))
        assertTrue(paymentService.processPayment("wallet", 0.01))
        assertFalse(paymentService.processPayment("card", 0.0))
        assertFalse(paymentService.processPayment("bank", -10.0))
    }
} 