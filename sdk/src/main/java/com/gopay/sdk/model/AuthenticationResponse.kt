package com.gopay.sdk.model

/**
 * Represents the authentication response from GoPay API
 * Based on the OAuth2 authentication endpoint response structure
 * 
 * @param accessToken JWT access token for API authentication
 * @param tokenType Type of the token (typically "bearer")
 * @param refreshToken Opaque string token used to obtain new access tokens
 * @param scope Space-separated list of granted scopes (optional)
 */
data class AuthenticationResponse(
    val accessToken: String,
    val tokenType: String,
    val refreshToken: String,
    val scope: String? = null
) {
    /**
     * Creates an AuthenticationResponse from the internal AuthResponse
     */
    companion object {
        fun fromAuthResponse(authResponse: com.gopay.sdk.modules.network.AuthResponse): AuthenticationResponse {
            return AuthenticationResponse(
                accessToken = authResponse.access_token,
                tokenType = authResponse.token_type,
                refreshToken = authResponse.refresh_token,
                scope = authResponse.scope
            )
        }
    }
    
    /**
     * Converts this AuthenticationResponse to the internal AuthResponse format
     */
    fun toAuthResponse(): com.gopay.sdk.modules.network.AuthResponse {
        return com.gopay.sdk.modules.network.AuthResponse(
            access_token = accessToken,
            token_type = tokenType,
            refresh_token = refreshToken,
            scope = scope
        )
    }
} 