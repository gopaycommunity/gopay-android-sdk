package cz.gopay.sdk.model

import com.squareup.moshi.Json

/**
 * The GoPay client SDK that opens the client session for a payment, the spec's `Client-Type`.
 * This SDK always sends [SDK_ANDROID]; the iOS SDK sends [SDK_IOS].
 */
internal enum class ClientType {
    @Json(name = "SDK_BROWSER")
    SDK_BROWSER,

    @Json(name = "SDK_ANDROID")
    SDK_ANDROID,

    @Json(name = "SDK_IOS")
    SDK_IOS
}

/** Body of `POST /payments/{payment_id}/init-session`, the spec's `Init-Session-Request`. */
internal data class InitSessionRequest(
    @Json(name = "client_type") val clientType: ClientType,
    /** The SDK's version, `GopaySDK.version`; the spec allows 1 to 64 characters. */
    @Json(name = "client_version") val clientVersion: String
)
