package com.gopay.sdk.exception

/**
 * Centralized error codes for the Gopay SDK.
 * All error codes follow the pattern: DOMAIN_XXX where XXX is a 3-digit number.
 */
object GopayErrorCodes {

    // ========================================
    // AUTHENTICATION ERRORS (AUTH_XXX)
    // ========================================
    
    /** Access token has expired and needs to be refreshed */
    const val AUTH_ACCESS_TOKEN_EXPIRED = "AUTH_001"
    
    /** Refresh token has expired, re-authentication required */
    const val AUTH_REFRESH_TOKEN_EXPIRED = "AUTH_002"
    
    /** Both access and refresh tokens are expired */
    const val AUTH_BOTH_TOKENS_EXPIRED = "AUTH_003"
    
    /** Failed to refresh the access token */
    const val AUTH_TOKEN_REFRESH_FAILED = "AUTH_004"
    
    /** Invalid authentication response format */
    const val AUTH_INVALID_RESPONSE = "AUTH_005"
    
    /** Cannot extract client ID from access token */
    const val AUTH_INVALID_CLIENT_ID = "AUTH_006"
    
    /** No access token or refresh token available */
    const val AUTH_NO_TOKENS_AVAILABLE = "AUTH_007"
    
    /** Authentication failed with invalid credentials */
    const val AUTH_INVALID_CREDENTIALS = "AUTH_008"

    // ========================================
    // NETWORK ERRORS (NETWORK_XXX)
    // ========================================
    
    /** Network connection timeout */
    const val NETWORK_TIMEOUT = "NETWORK_001"
    
    /** HTTP client error (4xx status codes) */
    const val NETWORK_CLIENT_ERROR = "NETWORK_002"
    
    /** HTTP server error (5xx status codes) */
    const val NETWORK_SERVER_ERROR = "NETWORK_003"
    
    /** No internet connection available */
    const val NETWORK_NO_CONNECTION = "NETWORK_004"
    
    /** SSL/TLS handshake failed */
    const val NETWORK_SSL_ERROR = "NETWORK_005"
    
    /** Certificate pinning validation failed */
    const val NETWORK_CERTIFICATE_PINNING_FAILED = "NETWORK_006"
    
    /** General network I/O error */
    const val NETWORK_IO_ERROR = "NETWORK_007"
    
    /** Request was cancelled */
    const val NETWORK_REQUEST_CANCELLED = "NETWORK_008"

    // ========================================
    // CONFIGURATION ERRORS (CONFIG_XXX)
    // ========================================
    
    /** SDK has not been initialized */
    const val CONFIG_SDK_NOT_INITIALIZED = "CONFIG_001"
    
    /** Invalid configuration parameter provided */
    const val CONFIG_INVALID_PARAMETER = "CONFIG_002"
    
    /** Missing required configuration parameter */
    const val CONFIG_MISSING_PARAMETER = "CONFIG_003"
    
    /** Cannot obtain Android application context */
    const val CONFIG_MISSING_CONTEXT = "CONFIG_004"
    
    /** Invalid environment configuration */
    const val CONFIG_INVALID_ENVIRONMENT = "CONFIG_005"
    
    /** Invalid API base URL */
    const val CONFIG_INVALID_BASE_URL = "CONFIG_006"

    // ========================================
    // PAYMENT ERRORS (PAYMENT_XXX)
    // ========================================
    
    /** Invalid payment method ID provided */
    const val PAYMENT_INVALID_METHOD_ID = "PAYMENT_001"
    
    /** Invalid payment amount (negative, zero, or too large) */
    const val PAYMENT_INVALID_AMOUNT = "PAYMENT_002"
    
    /** Payment processing failed */
    const val PAYMENT_PROCESSING_FAILED = "PAYMENT_003"
    
    /** Payment method not available */
    const val PAYMENT_METHOD_UNAVAILABLE = "PAYMENT_004"
    
    /** Insufficient funds for payment */
    const val PAYMENT_INSUFFICIENT_FUNDS = "PAYMENT_005"
    
    /** Payment was declined */
    const val PAYMENT_DECLINED = "PAYMENT_006"
    
    /** Payment timeout */
    const val PAYMENT_TIMEOUT = "PAYMENT_007"

    // ========================================
    // CARD ERRORS (CARD_XXX)
    // ========================================
    
    /** Card tokenization failed */
    const val CARD_TOKENIZATION_FAILED = "CARD_001"

    // ========================================
    // VALIDATION ERRORS (VALIDATION_XXX)
    // ========================================
    
    /** Invalid JWT token format */
    const val VALIDATION_INVALID_JWT_FORMAT = "VALIDATION_001"
    
    /** Missing required parameter */
    const val VALIDATION_MISSING_PARAMETER = "VALIDATION_002"
    
    /** Invalid parameter format */
    const val VALIDATION_INVALID_FORMAT = "VALIDATION_003"
    
    /** Parameter value out of allowed range */
    const val VALIDATION_OUT_OF_RANGE = "VALIDATION_004"
    
    /** Invalid email format */
    const val VALIDATION_INVALID_EMAIL = "VALIDATION_005"
    
    /** Invalid phone number format */
    const val VALIDATION_INVALID_PHONE = "VALIDATION_006"
    
    /** Invalid input data provided */
    const val VALIDATION_INVALID_INPUT = "VALIDATION_007"

    // ========================================
    // SECURITY ERRORS (SECURITY_XXX)
    // ========================================
    
    /** Certificate validation failed */
    const val SECURITY_CERTIFICATE_VALIDATION_FAILED = "SECURITY_001"
    
    /** SSL context initialization failed */
    const val SECURITY_SSL_CONTEXT_FAILED = "SECURITY_002"
    
    /** Trust manager configuration failed */
    const val SECURITY_TRUST_MANAGER_FAILED = "SECURITY_003"
    
    /** Security policy violation */
    const val SECURITY_POLICY_VIOLATION = "SECURITY_004"

    // ========================================
    // INTERNAL ERRORS (INTERNAL_XXX)
    // ========================================
    
    /** Unexpected internal error */
    const val INTERNAL_UNEXPECTED_ERROR = "INTERNAL_001"
    
    /** Feature not implemented yet */
    const val INTERNAL_NOT_IMPLEMENTED = "INTERNAL_002"
    
    /** Serialization/deserialization error */
    const val INTERNAL_SERIALIZATION_ERROR = "INTERNAL_003"
    
    /** Thread/concurrency error */
    const val INTERNAL_CONCURRENCY_ERROR = "INTERNAL_004"
} 