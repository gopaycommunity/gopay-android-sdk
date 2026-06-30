package com.gopay.example

/**
 * DEMO ONLY — replace with your own merchant values.
 * Mirrors the iOS example's `DemoConfig` enum.
 */
object DemoConfig {
    const val BASE_URL = "https://gw.alpha8.dev.gopay.com/gp-gw/api/4.0/"
    const val CLIENT_ID = "SDK"
    /** Public shareable key — safe to ship in the app. */
    const val SHAREABLE_KEY = "sk_LVa7uzttHBap5cQkUbBrGNCF3ayJYKdt"
    /** Merchant secret — NEVER ship this in a real app. Used only by [MerchantBackendSimulator]. */
    const val CLIENT_SECRET = "cs_eT6mnvSX"
    const val GOID = "8761908826"
    /** Return URL intercepted by the SDK's 3DS WebView to detect flow completion. */
    const val CHARGE_RETURN_URL = "cz.gopay.sdk://payment/return"
}
