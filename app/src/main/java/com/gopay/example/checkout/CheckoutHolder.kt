package com.gopay.example.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/**
 * Keeps the checkout over a rotation: its [controller], with the method picked, the banner and
 * the result, and the [scope] its payments run in.
 *
 * A payment takes seconds, a 3DS challenge as long as the shopper takes, and a phone gets turned
 * in the meantime. Held in the composition, the controller and the coroutine of a payment went
 * with the Activity that was being recreated: the charge had gone out, the payment ended paid,
 * and the checkout came back empty, offering to pay again. Held here, both live until the
 * checkout is left for good; only then is the payment's coroutine cancelled and its session
 * closed. The SDK needs no Activity for any of it, so nothing here holds one.
 */
class CheckoutHolder : ViewModel() {

    val controller = CheckoutController()

    /** Outlives a recreated Activity; cancelled when the checkout is left for good. */
    val scope: CoroutineScope get() = viewModelScope

    override fun onCleared() {
        controller.finish()
    }
}
