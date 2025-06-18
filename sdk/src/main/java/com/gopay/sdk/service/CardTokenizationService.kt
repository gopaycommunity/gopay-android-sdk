package com.gopay.sdk.service

import com.gopay.sdk.model.CardData
import com.gopay.sdk.model.CardTokenRequest
import com.gopay.sdk.model.CardTokenResponse
import com.gopay.sdk.modules.network.GopayApiService
import com.gopay.sdk.storage.TokenStorage

/**
 * High-level service for card tokenization that handles the complete flow:
 * 1. Ensures public key is available
 * 2. Encrypts card data using JWE
 * 3. Calls GoPay tokenization API
 * Uses consolidated GopayApiService with automatic authorization via AuthenticationInterceptor
 */
class CardTokenizationService(
    private val apiService: GopayApiService,
    private val encryptionService: EncryptionService,
    private val publicKeyService: PublicKeyService,
    private val tokenStorage: TokenStorage
) {

    /**
     * Tokenizes a card by encrypting card data and calling GoPay API
     * Authorization is automatically handled by AuthenticationInterceptor
     * 
     * @param cardData The card information to tokenize
     * @param permanent Whether to save the card for permanent usage (default: false)
     * @return CardTokenResponse containing token and card metadata
     * @throws IllegalStateException if no access token is available
     * @throws Exception for encryption, network, or API errors
     */
    suspend fun tokenizeCard(
        cardData: CardData, 
        permanent: Boolean = false
    ): CardTokenResponse {
        
        // Ensure we have a public key (fetch if needed)
        if (!publicKeyService.isPublicKeyAvailable()) {
            publicKeyService.fetchAndStorePublicKey()
        }
        
        // Create JWE encrypted payload
        val jwePayload = encryptionService.createJweEncryptedPayload(cardData)
        
        // Create request
        val request = CardTokenRequest(
            payload = jwePayload,
            permanent = permanent
        )
        
        // Call API - AuthenticationInterceptor handles authorization automatically
        val response = apiService.createCardToken(request)
        
        if (!response.isSuccessful) {
            throw Exception("Card tokenization failed: ${response.code()} ${response.message()}")
        }
        
        return response.body()
            ?: throw Exception("Empty response body from card tokenization")
    }

    /**
     * Validates card data before tokenization
     * 
     * @param cardData The card data to validate
     * @throws IllegalArgumentException for invalid card data
     */
    fun validateCardData(cardData: CardData) {
        require(cardData.cardPan.isNotBlank()) { "Card PAN cannot be empty" }
        require(cardData.cardPan.length in 13..19) { "Card PAN must be 13-19 digits" }
        require(cardData.cardPan.all { it.isDigit() }) { "Card PAN must contain only digits" }
        
        require(cardData.expMonth.isNotBlank()) { "Expiration month cannot be empty" }
        require(cardData.expMonth.matches(Regex("^(0[1-9]|1[0-2])$"))) { "Expiration month must be 01-12" }
        
        require(cardData.expYear.isNotBlank()) { "Expiration year cannot be empty" }
        require(cardData.expYear.matches(Regex("^[0-9]{2,4}$"))) { "Expiration year must be 2-4 digits" }
        
        require(cardData.cvv.isNotBlank()) { "CVV cannot be empty" }
        require(cardData.cvv.matches(Regex("^[0-9]{3,4}$"))) { "CVV must be 3-4 digits" }
    }

    /**
     * Tokenizes a card with validation
     * 
     * @param cardData The card information to tokenize
     * @param permanent Whether to save the card for permanent usage (default: false)
     * @return CardTokenResponse containing token and card metadata
     * @throws IllegalArgumentException for invalid card data
     * @throws IllegalStateException if no access token is available
     * @throws Exception for encryption, network, or API errors
     */
    suspend fun tokenizeCardWithValidation(
        cardData: CardData,
        permanent: Boolean = false
    ): CardTokenResponse {
        validateCardData(cardData)
        return tokenizeCard(cardData, permanent)
    }
} 