package cz.gopay.sdk.config

/** The settings the SDK builds its HTTP client from. */
internal data class NetworkConfig(
    /** Base URL for the API */
    val baseUrl: String,

    /** Read and write timeout in milliseconds */
    val readTimeoutMs: Long = 30_000,

    /** Connect timeout in milliseconds */
    val connectTimeoutMs: Long = 15_000,

    /** Whether to enable logging for HTTP requests/responses */
    val enableLogging: Boolean = false
)
