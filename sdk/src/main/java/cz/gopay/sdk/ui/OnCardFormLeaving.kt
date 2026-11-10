package cz.gopay.sdk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Hands [flags] to [onLeave] when the card form leaves the composition: the flags as they are
 * at that moment, not as they were when the form arrived.
 *
 * The form parks them with the card when it leaves for a configuration change, so the errors a
 * submit or a field left behind brought up come back after a rotation. An effect that is keyed
 * to nothing keeps the lambda of its first composition, and a value that lambda captured would
 * be the one from before the user did anything; both are read here through state that follows
 * every recomposition.
 */
@Composable
internal fun OnCardFormLeaving(flags: CardFormFlags, onLeave: (CardFormFlags) -> Unit) {
    val currentFlags by rememberUpdatedState(flags)
    val currentOnLeave by rememberUpdatedState(onLeave)
    DisposableEffect(Unit) {
        onDispose { currentOnLeave(currentFlags) }
    }
}
