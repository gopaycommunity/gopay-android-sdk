package cz.gopay.sdk.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the card form parks when it leaves for a rotation: the flags of the moment it leaves. A
 * form that parked the flags of its first composition came back from a rotation without its
 * errors, although the user had submitted it. Run on the Compose runtime alone, with an applier
 * that builds no UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnCardFormLeavingTest {

    /** A composition has to apply its changes to something; here to nothing. */
    private object NoUi : AbstractApplier<Unit>(Unit) {
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    @Test
    fun `a form that leaves hands over the flags of that moment, not of its first composition`() = runTest {
        val frames = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frames)
        val recomposing = launch(frames) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(NoUi, recomposer)
        var attempted by mutableStateOf(false)
        var cvvLeft by mutableStateOf(false)
        val left = mutableListOf<CardFormFlags>()

        composition.setContent {
            OnCardFormLeaving(CardFormFlags(submitAttempted = attempted, cvvLeft = cvvLeft)) { left += it }
        }
        runCurrent()

        // The user leaves the CVV field and submits; the form recomposes with both flags up.
        attempted = true
        cvvLeft = true
        Snapshot.sendApplyNotifications()
        runCurrent()
        frames.sendFrame(0)
        runCurrent()

        // The rotation takes the form out of the composition.
        composition.dispose()

        assertEquals(listOf(CardFormFlags(submitAttempted = true, cvvLeft = true)), left)
        recomposer.cancel()
        recomposing.join()
    }

    @Test
    fun `a form that leaves untouched hands over what it arrived with`() = runTest {
        val frames = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frames)
        val recomposing = launch(frames) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(NoUi, recomposer)
        val left = mutableListOf<CardFormFlags>()

        composition.setContent { OnCardFormLeaving(CardFormFlags(expirationLeft = true)) { left += it } }
        runCurrent()
        composition.dispose()

        assertEquals(listOf(CardFormFlags(expirationLeft = true)), left)
        recomposer.cancel()
        recomposing.join()
    }
}
