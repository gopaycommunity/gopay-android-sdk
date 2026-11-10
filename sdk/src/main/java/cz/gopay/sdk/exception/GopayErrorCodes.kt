package cz.gopay.sdk.exception

/**
 * Centralized error codes for the Gopay SDK.
 * All error codes follow the pattern: DOMAIN_XXX where XXX is a 3-digit number.
 */
object GopayErrorCodes {

    // ========================================
    // AUTHENTICATION ERRORS (AUTH_XXX)
    // ========================================

    /** Payment-scoped JWT expired and could not be re-acquired from cached payment credentials */
    const val AUTH_PAYMENT_TOKEN_EXPIRED = "AUTH_009"

    /** payment_id / payment_secret pair was rejected by /oauth2/token */
    const val AUTH_PAYMENT_CREDENTIALS_INVALID = "AUTH_010"

    /** clientId / shareableKey missing from GopayConfig but required by the requested operation */
    const val AUTH_SHAREABLE_KEY_MISSING = "AUTH_011"

    /** A PaymentSession for the given payment_id already exists — close it before starting another */
    const val AUTH_PAYMENT_SESSION_ALREADY_EXISTS = "AUTH_012"

    /** Operation invoked on a PaymentSession that has been closed */
    const val AUTH_PAYMENT_SESSION_CLOSED = "AUTH_013"

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

    /** General network I/O error */
    const val NETWORK_IO_ERROR = "NETWORK_007"

    // ========================================
    // CONFIGURATION ERRORS (CONFIG_XXX)
    // ========================================

    /** SDK has not been initialized */
    const val CONFIG_SDK_NOT_INITIALIZED = "CONFIG_001"

    /** Invalid API base URL */
    const val CONFIG_INVALID_BASE_URL = "CONFIG_006"

    // ========================================
    // PAYMENT ERRORS (PAYMENT_XXX)
    // ========================================

    /** Payment processing failed */
    const val PAYMENT_PROCESSING_FAILED = "PAYMENT_003"

    /** Payment method not available */
    const val PAYMENT_METHOD_UNAVAILABLE = "PAYMENT_004"

    /**
     * The charge ended `FAILED`. Thrown by [cz.gopay.sdk.session.PaymentSession.awaitChargeState]
     * only, with the charge on [GopaySDKException.chargeState] and the gateway's `fail_reason`,
     * when there is one, in the message. A new payment is needed to try again: a payment is
     * charged once.
     */
    const val PAYMENT_DECLINED = "PAYMENT_006"

    /**
     * The charge stayed undecided, `REQUESTED`, `PROCESSING`, `UNKNOWN` or `ACTION_REQUIRED`
     * without a new address, for the whole initial timeout of
     * [cz.gopay.sdk.session.PaymentSession.awaitChargeState]; the state the deadline read
     * returned is on [GopaySDKException.chargeState]. Not a failed payment: the gateway may
     * still decide it. Read `getChargeState()` or wait for the payment notification on your
     * backend before offering a new payment. Never passed to `errorCallback`, like
     * [PAYMENT_CANCELLED]: nothing failed.
     */
    const val PAYMENT_TIMEOUT = "PAYMENT_007"

    /** A 3DS verification is already in progress; only one can run at a time */
    const val PAYMENT_VERIFICATION_IN_PROGRESS = "PAYMENT_008"

    /** A Google Pay sheet is already in progress; only one can run at a time */
    const val PAYMENT_GOOGLE_PAY_IN_PROGRESS = "PAYMENT_009"

    /**
     * The 3DS challenge never reached the user: the page could not be loaded, e.g. the redirect
     * URL is dead or is not a web address, or no app on the device took the hand-off the
     * challenge asked for.
     */
    const val PAYMENT_VERIFICATION_UNREACHABLE = "PAYMENT_010"

    /**
     * The payment window was closed before it finished: the Google Pay sheet closed without
     * paying, or the 3DS challenge closed before it returned, or the challenge page failed after
     * it had started rendering. Not a failed payment; after the 3DS challenge the charge was
     * already sent, so read `getChargeState()` before offering it again. Never passed to
     * `errorCallback`. A cancelled calling coroutine is not this code, it stays a
     * `CancellationException`.
     */
    const val PAYMENT_CANCELLED = "PAYMENT_011"

    /**
     * The gateway answered HTTP 404 for the payment: the payment does not exist, the session's
     * token has no access to it, or, from a charge read, the payment has no charge yet. Thrown by
     * [cz.gopay.sdk.session.PaymentSession.getStatus],
     * [cz.gopay.sdk.session.PaymentSession.getPaymentOutcome],
     * [cz.gopay.sdk.session.PaymentSession.getChargeState],
     * [cz.gopay.sdk.session.PaymentSession.awaitChargeState] and by the charge request of every
     * charge call. A 404 from any other request stays [NETWORK_CLIENT_ERROR], the wallet info that
     * a wallet charge reads first included. The status and the gateway's error are on
     * [GopaySDKException.httpContext].
     */
    const val PAYMENT_NOT_FOUND = "PAYMENT_012"

    /**
     * The gateway answered a charge with HTTP 409: the payment already has a charge, and a
     * payment is charged once. Thrown by [cz.gopay.sdk.session.PaymentSession.charge] and every
     * `chargeWith*` call. The first charge stands: read it with
     * [cz.gopay.sdk.session.PaymentSession.getChargeState] instead of charging again. The status
     * and the gateway's error are on [GopaySDKException.httpContext].
     */
    const val PAYMENT_CHARGE_ALREADY_EXISTS = "PAYMENT_013"

    // ========================================
    // VALIDATION ERRORS (VALIDATION_XXX)
    // ========================================

    /** Invalid input data provided */
    const val VALIDATION_INVALID_INPUT = "VALIDATION_007"

    // ========================================
    // INTERNAL ERRORS (INTERNAL_XXX)
    // ========================================

    /** Unexpected internal error */
    const val INTERNAL_UNEXPECTED_ERROR = "INTERNAL_001"

    /** Serialization/deserialization error */
    const val INTERNAL_SERIALIZATION_ERROR = "INTERNAL_003"
}
