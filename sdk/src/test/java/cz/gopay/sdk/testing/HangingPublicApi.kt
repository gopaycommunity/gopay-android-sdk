package cz.gopay.sdk.testing

import cz.gopay.sdk.model.BrowserDataDetected
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.modules.network.LookupLimitSignal
import cz.gopay.sdk.modules.network.PublicApi
import kotlinx.coroutines.awaitCancellation
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * A gateway that never answers the BIN lookup: the request waits until it is cancelled. Keeps the
 * tag of the last lookup in [limitSignal], which says whether the lookup's own limit ended it.
 */
internal class HangingPublicApi : PublicApi {

    @Volatile
    var limitSignal: LookupLimitSignal? = null
        private set

    override suspend fun lookUpBin(body: RequestBody, limitSignal: LookupLimitSignal): Response<ResponseBody> {
        this.limitSignal = limitSignal
        awaitCancellation()
    }

    override suspend fun getPublicKey(): Response<Jwk> = throw UnsupportedOperationException()

    override suspend fun getBrowserData(userAgent: String?): Response<BrowserDataDetected> =
        throw UnsupportedOperationException()
}
