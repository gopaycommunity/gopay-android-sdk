package cz.gopay.sdk.modules.network

import cz.gopay.sdk.exception.GopaySDKException
import java.io.IOException

/**
 * Carries a [GopaySDKException] out of an OkHttp interceptor.
 *
 * OkHttp lets only an [IOException] leave an interceptor. Anything else in an asynchronous call,
 * which is how Retrofit runs every `suspend` endpoint, fails the call and is then rethrown on the
 * dispatcher thread, where nothing catches it and the host process dies. [SessionAuthInterceptor]
 * therefore throws this wrapper, and [apiCall] hands the [sdkException] inside back to the caller.
 */
internal class SdkIOException(val sdkException: GopaySDKException) :
    IOException(sdkException.message, sdkException)
