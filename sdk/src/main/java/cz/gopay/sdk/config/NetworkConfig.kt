package cz.gopay.sdk.config

import okhttp3.CertificatePinner
import okhttp3.Interceptor
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Configuration class for SDK network settings
 * Allows customization of SSL/TLS, certificate pinning, and other HTTP client settings
 */
internal data class NetworkConfig(
    /** Base URL for the API */
    val baseUrl: String,
    
    /** Read and write timeout in milliseconds */
    val readTimeoutMs: Long = 30_000,
    
    /** Connect timeout in milliseconds */
    val connectTimeoutMs: Long = 15_000,
    
    /** Whether to enable logging for HTTP requests/responses */
    val enableLogging: Boolean = false,
    
    /** Optional custom SSLSocketFactory (for using custom certificates, etc.) */
    val sslSocketFactory: SSLSocketFactory? = null,
    
    /** Corresponding X509TrustManager, required if you supply sslSocketFactory */
    val trustManager: X509TrustManager? = null,
    
    /** Tables of hostname→pinned SHA-256 hashes for certificate pinning */
    val certificatePinner: CertificatePinner? = null,
    
    /** Custom OkHttp Interceptors to add to the client */
    val interceptors: List<Interceptor> = emptyList()
) 