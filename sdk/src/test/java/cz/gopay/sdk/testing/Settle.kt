package cz.gopay.sdk.testing

import org.junit.Assert.assertTrue
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/*
 * Waiting for a state instead of pausing for a fixed time. The GitHub runner is many times slower
 * than a laptop, and a pause that is plenty here is too short there, so a test waits for what it
 * is about to check, up to a limit far above what a loaded runner needs. The limit only matters
 * when the state never comes: the test then checks it as before and fails there.
 *
 * The limit is wall clock time, so it is generous: a state that does arrive costs nothing extra.
 * The iOS SDK's tests wait the same way (sdkTests/Settle.swift).
 */

/** How long a test waits for a state before it checks it anyway. */
internal const val SETTLE_TIMEOUT_MS = 30_000L

private const val POLL_MS = 10L

/**
 * Polls [condition] until it holds or [timeoutMs] has passed, and returns whether it held. Runs
 * on the calling thread, so it also serves a fake transport that waits for its call.
 *
 * It checks nothing itself: the caller asserts the state after it, or uses [awaitSettled].
 */
internal fun settle(timeoutMs: Long = SETTLE_TIMEOUT_MS, condition: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
    while (true) {
        if (condition()) return true
        if (System.nanoTime() - deadline >= 0) return false
        Thread.sleep(POLL_MS)
    }
}

/** Waits for [condition] and fails the test with [message] if it never holds. */
internal fun awaitSettled(message: String, timeoutMs: Long = SETTLE_TIMEOUT_MS, condition: () -> Boolean) {
    assertTrue("$message: not reached within $timeoutMs ms", settle(timeoutMs, condition))
}

/**
 * Lets every task already on [threads] run to its end and takes no new one, for the checks that
 * something did not happen: once the pool has terminated, nothing it ran can still report,
 * call back or die. Time plays no part in it but as the limit, so a slow runner cannot cut it
 * short. The pool is done afterwards; a test that goes on sending needs [awaitSettled] instead.
 */
internal fun drain(threads: ExecutorService, timeoutMs: Long = SETTLE_TIMEOUT_MS) {
    threads.shutdown()
    assertTrue(
        "background work still running after $timeoutMs ms",
        threads.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)
    )
}
