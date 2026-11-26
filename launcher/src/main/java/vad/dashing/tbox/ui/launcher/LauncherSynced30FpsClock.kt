package vad.dashing.tbox.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Shared ~30 FPS VSYNC-quantized clock so multiple left-panel overlays
 * (e.g., [LauncherVirtualRoad] at 30 FPS and [LauncherPdcOverlay] at 15 FPS via
 * `tickDivisor = 2`) advance and invalidate Compose draw phases on the exact same
 * VSYNC frame instead of staggering into 45–60 FPS redraws.
 *
 * Key optimizations for low-power / Android 9 little-core rendering:
 * - Sleeps via [delay] between ticks instead of polling [withFrameNanos] at 60 Hz,
 *   so `Choreographer#doFrame` and `Recomposer:animation` stay completely idle on skipped VSYNCs.
 * - Dynamically scales [effectiveIntervalNs] by the minimum active listener `tickDivisor`
 *   (e.g., 15 FPS / ~64 ms sleep when only PDC is active).
 * - Enforces a monotonic wall-clock guard alongside VSYNC `frameNs` so delayed VSYNCs
 *   after a slow little-core `DrawFrame` cannot fire back-to-back ticks ~21 ms apart.
 * - Stops completely (0 frame callbacks) when zero listeners are registered.
 */
class LauncherSynced30FpsClock(
    internal val frameIntervalNs: Long = VIRTUAL_ROAD_FRAME_INTERVAL_NS,
    private val wallClockNanos: () -> Long = { System.nanoTime() },
) {
    private class ListenerEntry(
        val tickDivisor: Int,
        val onTick: (frameNs: Long, deltaNs: Long) -> Unit,
        var ticksSinceDispatch: Int = 0,
        var accumulatedDeltaNs: Long = 0L,
    )

    private val listeners = LinkedHashSet<ListenerEntry>()
    private val hasActiveListenersState = mutableStateOf(false)
    private var minActiveDivisor: Int = 1
    private var lastTickFrameNs: Long = 0L
    private var lastTickWallNs: Long = 0L

    val hasActiveListeners: Boolean get() = hasActiveListenersState.value

    internal val effectiveIntervalNs: Long
        get() = frameIntervalNs * minActiveDivisor

    fun registerListener(
        tickDivisor: Int = 1,
        onTick: (frameNs: Long, deltaNs: Long) -> Unit,
    ): () -> Unit {
        val entry = ListenerEntry(
            tickDivisor = tickDivisor.coerceAtLeast(1),
            onTick = onTick,
        )
        listeners.add(entry)
        recomputeListenerState()
        return {
            if (listeners.remove(entry)) {
                recomputeListenerState()
                if (listeners.isEmpty()) {
                    reset()
                }
            }
        }
    }

    private fun recomputeListenerState() {
        hasActiveListenersState.value = listeners.isNotEmpty()
        var minDiv = Int.MAX_VALUE
        for (entry in listeners) {
            if (entry.tickDivisor < minDiv) {
                minDiv = entry.tickDivisor
            }
        }
        minActiveDivisor = if (minDiv == Int.MAX_VALUE) 1 else minDiv
    }

    internal fun reset() {
        lastTickFrameNs = 0L
        lastTickWallNs = 0L
        for (entry in listeners) {
            entry.ticksSinceDispatch = 0
            entry.accumulatedDeltaNs = 0L
        }
    }

    /**
     * Returns how long [runWhileActive] can sleep before calling [withFrameNanos],
     * leaving [WAKE_LEAD_NS] margin to catch the exact target VSYNC without waking
     * `Choreographer` on intermediate skipped VSYNCs.
     */
    internal fun computeSleepBeforeNextFrameNs(wallNowNs: Long): Long {
        if (lastTickWallNs == 0L) return 0L
        val elapsedWallNs = (wallNowNs - lastTickWallNs).coerceAtLeast(0L)
        val remainingNs = effectiveIntervalNs - elapsedWallNs - WAKE_LEAD_NS
        return if (remainingNs > 0L) remainingNs else 0L
    }

    /**
     * Advances the clock on a VSYNC [frameNs] observed at monotonic [wallNowNs].
     * Returns `true` when both the VSYNC interval and monotonic wall-clock guard are
     * satisfied and at least one listener was notified synchronously.
     */
    internal fun stepFrame(
        frameNs: Long,
        wallNowNs: Long = frameNs,
    ): Boolean {
        if (lastTickFrameNs == 0L) {
            lastTickFrameNs = frameNs
            lastTickWallNs = wallNowNs
            return false
        }
        val targetIntervalNs = effectiveIntervalNs
        val deltaFrameNs = frameNs - lastTickFrameNs
        if (deltaFrameNs < targetIntervalNs) {
            return false
        }
        val wallMinIntervalNs = (targetIntervalNs - WALL_GUARD_SLACK_NS).coerceAtLeast(0L)
        val deltaWallNs = wallNowNs - lastTickWallNs
        if (deltaWallNs < wallMinIntervalNs) {
            return false
        }
        lastTickFrameNs = frameNs
        lastTickWallNs = wallNowNs
        if (listeners.isEmpty()) return false

        val stepTicks = minActiveDivisor
        val snapshot = listeners.toTypedArray()
        var dispatchedAny = false
        for (entry in snapshot) {
            entry.ticksSinceDispatch += stepTicks
            entry.accumulatedDeltaNs += deltaFrameNs
            if (entry.ticksSinceDispatch >= entry.tickDivisor) {
                val listenerDeltaNs = entry.accumulatedDeltaNs
                entry.ticksSinceDispatch = 0
                entry.accumulatedDeltaNs = 0L
                entry.onTick(frameNs, listenerDeltaNs)
                dispatchedAny = true
            }
        }
        return dispatchedAny
    }

    internal suspend fun runWhileActive() {
        reset()
        try {
            while (currentCoroutineContext().isActive) {
                val sleepNs = computeSleepBeforeNextFrameNs(wallClockNanos())
                if (sleepNs >= MIN_SLEEP_NS) {
                    delay(sleepNs / 1_000_000L)
                }
                withFrameNanos { frameNs ->
                    stepFrame(frameNs, wallClockNanos())
                }
            }
        } finally {
            reset()
        }
    }

    internal companion object {
        internal const val WAKE_LEAD_NS = 4_000_000L
        internal const val WALL_GUARD_SLACK_NS = 4_000_000L
        private const val MIN_SLEEP_NS = 1_000_000L
    }
}

/**
 * Isolated composable scope that runs [clock] while it has active listeners without
 * causing parent composables (like [LauncherLeftPanel]) to recompose on listener count changes.
 */
@Composable
internal fun LauncherSynced30FpsClockHost(clock: LauncherSynced30FpsClock) {
    val active = clock.hasActiveListeners
    LaunchedEffect(clock, active) {
        if (active) {
            clock.runWhileActive()
        }
    }
}

@Composable
internal fun rememberSynced30FpsClock(
    sharedClock: LauncherSynced30FpsClock?,
): LauncherSynced30FpsClock {
    if (sharedClock != null) return sharedClock
    val localClock = remember { LauncherSynced30FpsClock() }
    LauncherSynced30FpsClockHost(localClock)
    return localClock
}
