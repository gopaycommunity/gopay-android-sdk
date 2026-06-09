package cz.gopay.sdk.ui

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

internal object GooglePayBridge {
    private val deferred = AtomicReference<CompletableDeferred<Result<String>>?>()

    fun register(d: CompletableDeferred<Result<String>>): Boolean = deferred.compareAndSet(null, d)

    fun complete(tokenJson: String) {
        deferred.getAndSet(null)?.complete(Result.success(tokenJson))
    }

    // cause=null → user dismissed the sheet; cause!=null → Google Pay returned an error
    fun cancel(cause: Throwable? = null) {
        deferred.getAndSet(null)?.complete(Result.failure(cause ?: UserCancelledThrowable()))
    }

    fun clear() { deferred.set(null) }

    // Sentinel to distinguish user-dismiss from a real error without relying on exception type
    internal class UserCancelledThrowable : Throwable("Google Pay sheet was dismissed by the user")
}
