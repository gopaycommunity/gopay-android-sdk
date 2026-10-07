package cz.gopay.sdk.service

import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.modules.network.PublicApi
import cz.gopay.sdk.modules.network.apiCall
import cz.gopay.sdk.util.HttpDate
import cz.gopay.sdk.util.SdkLog

/**
 * Backs [cz.gopay.sdk.GopaySDK.encryptCardData], which the card form calls too.
 *
 * Every call fetches the merchant's key from `GET /cards/public-key` again, with no cache, and
 * takes the JWE `iat` from that response's `Date` header, so the token carries the gateway's
 * clock rather than the device's. Without a usable header it falls back to the device clock and
 * logs a warning while debug logging is on; a payment is never blocked over it. Concurrent calls
 * each make their own request. Mirrors the iOS `GopaySDK.encryptCardData(_:)`.
 */
internal class CardEncryption(
    private val publicApi: () -> PublicApi,
    private val encryptionService: EncryptionService = EncryptionService(),
    private val deviceClockSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) {

    /**
     * @param clientId read after the key fetch: without it the public API itself fails first
     *        with [cz.gopay.sdk.exception.GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING].
     */
    suspend fun encrypt(cardData: CardData, clientId: String?): String {
        var dateHeader: String? = null
        val jwk = apiCall("fetch public key") {
            publicApi().getPublicKey().also { dateHeader = it.headers()[DATE_HEADER] }
        }
        val issuedAt = HttpDate.parseSeconds(dateHeader) ?: run {
            SdkLog.w(MISSING_DATE_WARNING)
            deviceClockSeconds()
        }
        val id = requireNotNull(clientId) {
            "GopayConfig.clientId must be set to encrypt card data"
        }
        return encryptionService.createJweEncryptedPayload(cardData, jwk, id, issuedAt)
    }

    companion object {
        private const val DATE_HEADER = "Date"

        /** Mirrors the iOS `GopaySDK.missingDateWarning`, word for word. */
        const val MISSING_DATE_WARNING =
            "GET /cards/public-key came back without a usable Date header, " +
                "using the device clock for the JWE iat"
    }
}
