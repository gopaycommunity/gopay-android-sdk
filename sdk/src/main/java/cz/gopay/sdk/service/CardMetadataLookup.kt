package cz.gopay.sdk.service

import com.squareup.moshi.JsonReader
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.CardMetadata
import cz.gopay.sdk.model.CardScheme
import cz.gopay.sdk.model.CardServiceType
import cz.gopay.sdk.modules.network.LookupLimitSignal
import cz.gopay.sdk.modules.network.PublicApi
import cz.gopay.sdk.modules.network.SdkIOException
import cz.gopay.sdk.modules.network.transportError
import cz.gopay.sdk.util.SdkLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.IOException

/**
 * Backs the metadata half of [cz.gopay.sdk.GopaySDK.submitCardFormWithMetadata]: asks
 * `POST /cards/bin-lookup` what the gateway knows about a card from its first eight digits, the
 * BIN, with the shareable key like the public key fetch.
 *
 * The lookup never fails: no answer, an HTTP error (a 404 is the gateway not knowing the BIN), an
 * answer that cannot be read and one that takes longer than [timeoutMs] are all "no metadata",
 * null, with a warning in the debug log. Nothing is reported through `errorCallback`. Only a
 * cancellation of the caller passes through.
 *
 * It does not go through `apiCall`, which every other gateway call uses: that turns a failure
 * into a [GopaySDKException] with the request and the response body in it, for a caller to
 * handle. Here no failure reaches a caller, and only its code is kept, for the warning.
 *
 * The BIN is card data. It goes into the request body and nowhere else: not into the warning,
 * not into an error, and the SDK keeps the body out of its operational data and masks it in the
 * debug HTTP log. Mirrors the iOS SDK's lookup.
 *
 * @param timeoutMs the limit on the whole lookup, not on its idle time; 3 seconds, the browser
 *        SDK's.
 */
internal class CardMetadataLookup(
    private val publicApi: () -> PublicApi,
    private val timeoutMs: Long = TIMEOUT_MS
) {

    /** The metadata of the card whose number is [cardNumber], digits only, or null. */
    suspend fun lookUp(cardNumber: String): CardMetadata? {
        val bin = cardNumber.take(BIN_LENGTH)
        if (bin.length != BIN_LENGTH || !bin.all { it in '0'..'9' }) return null
        val answer = try {
            ask(bin)
        } catch (e: CancellationException) {
            // The caller's cancellation passes through. One that came from anywhere else, the
            // HTTP client's own for one, is only a lookup that did not answer.
            currentCoroutineContext().ensureActive()
            Answer.none(CANCELLED)
        } catch (e: Throwable) {
            Answer.none(codeOf(e))
        }
        answer.failure?.let { SdkLog.w(warning(it)) }
        return answer.metadata
    }

    /**
     * One request, ended at [timeoutMs]. The request runs beside the wait for it, so the limit
     * can mark it ([LookupLimitSignal]) before it cancels it: the operational data then reports
     * a lookup the limit ended and stays silent about one the caller cancelled. The report comes
     * from the HTTP client's interceptor, so a request the limit ends while it still waits in
     * the client's queue, behind five others to the same host, is not reported at all.
     */
    private suspend fun ask(bin: String): Answer = supervisorScope {
        val signal = LookupLimitSignal()
        // Written by hand: a model would be one more class for R8 to keep, for eight digits.
        val body = "{\"bin\":\"$bin\"}".toRequestBody(JSON)
        val request = async { publicApi().lookUpBin(body, signal) }
        val response = withTimeoutOrNull(timeoutMs) { request.await() }
        when {
            response == null -> {
                // A caller cancelled at the same moment wins: its cancellation goes out and the
                // request is not marked, so nothing is reported.
                ensureActive()
                signal.limitReached = true
                request.cancel()
                Answer.none("no answer in $timeoutMs ms")
            }
            !response.isSuccessful -> Answer.none("HTTP ${response.code()}")
            else -> read(response.body()?.string())?.let { Answer(it, failure = null) }
                ?: Answer.none(UNREADABLE)
        }
    }

    /** The metadata, or why there is none, as the warning says it. */
    private class Answer(val metadata: CardMetadata?, val failure: String?) {
        companion object {
            fun none(failure: String) = Answer(metadata = null, failure = failure)
        }
    }

    companion object {
        /** How many digits of the card number the gateway is asked about. */
        const val BIN_LENGTH = 8

        const val TIMEOUT_MS = 3_000L

        private const val UNREADABLE = "the answer could not be read"

        /** The type's name, as the iOS SDK names its `CancellationError` here. */
        private const val CANCELLED = "CancellationException"

        private const val MAX_TEXT_LENGTH = 200

        private val JSON = "application/json".toMediaType()

        private val ALPHA_3 = Regex("[A-Z]{3}")

        /**
         * The same text as the iOS SDK's warning. The reasons are the same too, but for a
         * failure that is neither the network's nor the SDK's: see [codeOf].
         */
        fun warning(reason: String): String =
            "POST /cards/bin-lookup gave no card metadata ($reason), returning the payload without it"

        /**
         * A failure as the SDK's code for it, the reason the iOS SDK prints too: `NETWORK_004`
         * for no connection, `AUTH_011` without the shareable key. Never the failure's own
         * message, which is not the SDK's to print. Anything else is `INTERNAL_001` here, where
         * the iOS SDK prints the type's name: a host's R8 renames the classes of an Android app.
         */
        private fun codeOf(e: Throwable): String = when (e) {
            is GopaySDKException -> e.code
            is SdkIOException -> e.sdkException.code
            is IOException -> transportError("look up card metadata", e).code
            else -> GopayErrorCodes.INTERNAL_UNEXPECTED_ERROR
        }

        /**
         * The gateway's answer as [CardMetadata], read as strictly as the browser SDK's
         * `toCardMetadata` does. Without a `scheme` of VISA or MASTERCARD, or without a boolean
         * `corporate`, there is no metadata at all. An optional field counts only in its
         * documented shape: `brand` and `issuer` a non-empty text of at most 200 characters,
         * `service_type` DEBIT or CREDIT, `issuer_country` three capital letters; anything else
         * leaves that field out. No other field of the answer is passed on. A body with
         * anything after its one JSON value is not an answer.
         */
        fun read(body: String?): CardMetadata? {
            val json = try {
                JsonReader.of(Buffer().writeUtf8(body ?: return null)).use { reader ->
                    reader.readJsonValue().also {
                        if (reader.peek() != JsonReader.Token.END_DOCUMENT) return null
                    }
                }
            } catch (e: Exception) {
                return null
            }
            val fields = json as? Map<*, *> ?: return null
            val scheme = when (fields["scheme"]) {
                "VISA" -> CardScheme.VISA
                "MASTERCARD" -> CardScheme.MASTERCARD
                else -> return null
            }
            val corporate = fields["corporate"] as? Boolean ?: return null
            return CardMetadata(
                scheme = scheme,
                corporate = corporate,
                brand = text(fields["brand"]),
                serviceType = when (fields["service_type"]) {
                    "DEBIT" -> CardServiceType.DEBIT
                    "CREDIT" -> CardServiceType.CREDIT
                    else -> null
                },
                issuer = text(fields["issuer"]),
                issuerCountry = (fields["issuer_country"] as? String)?.takeIf { ALPHA_3.matches(it) }
            )
        }

        private fun text(value: Any?): String? =
            (value as? String)?.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT_LENGTH }
    }
}
