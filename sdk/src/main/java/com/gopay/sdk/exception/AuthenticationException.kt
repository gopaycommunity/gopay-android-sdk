package com.gopay.sdk.exception

/**
 * Base exception for authentication-related errors
 */
open class AuthenticationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when both access and refresh tokens are expired or invalid
 */
class UnauthenticatedException(
    message: String = "Authentication failed: both access and refresh tokens are expired or invalid",
    cause: Throwable? = null
) : AuthenticationException(message, cause)

/**
 * Exception thrown when token refresh fails
 */
class TokenRefreshException(
    message: String = "Failed to refresh authentication token",
    cause: Throwable? = null
) : AuthenticationException(message, cause) 