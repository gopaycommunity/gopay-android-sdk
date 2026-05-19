package cz.gopay.sdk.ui

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

internal object PaymentVerificationBridge {
    private val deferred = AtomicReference<CompletableDeferred<Boolean>?>()

    fun register(d: CompletableDeferred<Boolean>): Boolean =
        deferred.compareAndSet(null, d)

    fun complete() { deferred.getAndSet(null)?.complete(true) }
    fun cancel()   { deferred.getAndSet(null)?.cancel() }
    fun clear()    { deferred.set(null) }
}
