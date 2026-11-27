package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Shared ~30 FPS VSYNC-quantized clock so multiple left-panel renderers
 * (e.g., [LauncherVirtualRoad] at 30 FPS, [LauncherPdcOverlay] at 15 FPS via
 * `tickDivisor = 2`, the 3D Filament car model at 30/15 FPS, compass, tire inertia,
 * and status pulses) advance and render on the exact same VSYNC frame instead of
 * staggering into 45–60 FPS redraws.
 *
 * Key optimizations for low-power / Android 9 little-core rendering:
 * - Sleeps via interruptible [withTimeoutOrNull] between ticks instead of polling
 *   [withFrameNanos] at 60 Hz, so `Choreographer#doFrame` and `Recomposer:animation`
 *   stay completely idle on skipped VSYNCs.
 * - Dynamically scales [effectiveIntervalNs] by the minimum active listener `tickDivisor`
 *   (e.g., 15 FPS / ~64 ms sleep when only PDC or pulses are active) and wakes
 *   immediately when a listener escalates to a smaller divisor (e.g., 15 FPS -> 30 FPS).
 * - Enforces a monotonic wall-clock guard alongside VSYNC `frameNs` so delayed VSYNCs
 *   after a slow little-core `DrawFrame` cannot fire back-to-back ticks ~21 ms apart.
 * - Stops completely (0 frame callbacks) when zero listeners are registered.
 */
class LauncherSynced30FpsClock(
    internal val frameIntervalNs: Long = VIRTUAL_ROAD_FRAME_INTERVAL_NS,
    private val wallClockNanos: () -> Long = { System.nanoTime() },
) {
    interface ListenerHandle {
        fun setTickDivisor(newDivisor: Int)
        fun unregister()
    }

    private class ListenerEntry(
        var tickDivisor: Int,
        val onTick: (frameNs: Long, deltaNs: Long) -> Unit,
        var ticksSinceDispatch: Int = 0,
        var accumulatedDeltaNs: Long = 0L,
    )

    private val listeners = LinkedHashSet<ListenerEntry>()
    private val hasActiveListenersState = mutableStateOf(false)
    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)
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
        val handle = registerListenerWithHandle(tickDivisor, onTick)
        return handle::unregister
    }

    fun registerListenerWithHandle(
        tickDivisor: Int = 1,
        onTick: (frameNs: Long, deltaNs: Long) -> Unit,
    ): ListenerHandle {
        val entry = ListenerEntry(
            tickDivisor = tickDivisor.coerceAtLeast(1),
            onTick = onTick,
        )
        val prevMinDiv = minActiveDivisor
        val wasEmpty = listeners.isEmpty()
        listeners.add(entry)
        recomputeListenerState()
        if (!wasEmpty && minActiveDivisor < prevMinDiv) {
            wakeNow()
        }
        return object : ListenerHandle {
            override fun setTickDivisor(newDivisor: Int) {
                val coerced = newDivisor.coerceAtLeast(1)
                if (entry.tickDivisor == coerced) return
                if (!listeners.contains(entry)) return
                val previousMin = minActiveDivisor
                entry.tickDivisor = coerced
                recomputeListenerState()
                if (minActiveDivisor < previousMin) {
                    wakeNow()
                }
            }

            override fun unregister() {
                if (listeners.remove(entry)) {
                    recomputeListenerState()
                    if (listeners.isEmpty()) {
                        reset()
                    }
                }
            }
        }
    }

    internal fun wakeNow() {
        wakeSignal.trySend(Unit)
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
                wakeSignal.tryReceive()
                val sleepNs = computeSleepBeforeNextFrameNs(wallClockNanos())
                if (sleepNs >= MIN_SLEEP_NS) {
                    withTimeoutOrNull(sleepNs / 1_000_000L) {
                        wakeSignal.receive()
                    }
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
 * Provides the shared left-panel [LauncherSynced30FpsClock] to all nested overlays and widgets
 * so animations automatically phase-lock to the road (30 FPS) and PDC (15 FPS) without threading
 * a clock parameter through every composable.
 */
internal val LocalLauncherSynced30FpsClock = staticCompositionLocalOf<LauncherSynced30FpsClock?> { null }

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
    sharedClock: LauncherSynced30FpsClock? = null,
): LauncherSynced30FpsClock {
    val resolved = sharedClock ?: LocalLauncherSynced30FpsClock.current
    if (resolved != null) return resolved
    val localClock = remember { LauncherSynced30FpsClock() }
    LauncherSynced30FpsClockHost(localClock)
    return localClock
}

internal class SyncedFloatTweenDriver(
    initialValue: Float,
) {
    var startValue: Float = initialValue
        private set
    var endValue: Float = initialValue
        private set
    var currentValue: Float = initialValue
        private set
    private var elapsedNs: Long = 0L
    private var durationNs: Long = 0L
    private var easing: Easing = FastOutSlowInEasing
    var isFinished: Boolean = true
        private set

    fun retarget(
        newTarget: Float,
        durationMillis: Int,
        newEasing: Easing,
    ): Boolean {
        if (newTarget == endValue && (!isFinished || abs(newTarget - currentValue) <= 0.0001f)) {
            return !isFinished
        }
        if (durationMillis <= 0 || abs(newTarget - currentValue) <= 0.0001f) {
            startValue = newTarget
            endValue = newTarget
            currentValue = newTarget
            elapsedNs = 0L
            durationNs = 0L
            isFinished = true
            return false
        }
        startValue = currentValue
        endValue = newTarget
        elapsedNs = 0L
        durationNs = durationMillis.toLong() * 1_000_000L
        easing = newEasing
        isFinished = false
        return true
    }

    fun step(deltaNs: Long): Float {
        if (isFinished || durationNs <= 0L) {
            currentValue = endValue
            isFinished = true
            return currentValue
        }
        val clampedDeltaNs = deltaNs.coerceIn(0L, MAX_TWEEN_STEP_NS)
        elapsedNs += clampedDeltaNs
        val fraction = (elapsedNs.toFloat() / durationNs.toFloat()).coerceIn(0f, 1f)
        if (fraction >= 1f) {
            currentValue = endValue
            isFinished = true
        } else {
            val eased = easing.transform(fraction)
            currentValue = startValue + (endValue - startValue) * eased
        }
        return currentValue
    }

    companion object {
        private const val MAX_TWEEN_STEP_NS = 100_000_000L
    }
}

/**
 * Synced replacement for Compose `animateFloatAsState`: runs at 30 FPS (`tickDivisor = 1`)
 * or 15 FPS (`tickDivisor = 2`) on [LauncherSynced30FpsClock] and unregisters immediately
 * once settled (0 FPS when idle).
 */
@Composable
internal fun rememberSyncedFloatAsState(
    targetValue: Float,
    durationMillis: Int = 300,
    easing: Easing = FastOutSlowInEasing,
    tickDivisor: Int = 1,
    sharedClock: LauncherSynced30FpsClock? = null,
): State<Float> {
    val clock = rememberSynced30FpsClock(sharedClock)
    val state = remember { mutableFloatStateOf(targetValue) }
    val driver = remember { SyncedFloatTweenDriver(targetValue) }
    var isAnimating by remember { mutableStateOf(false) }

    SideEffect {
        val shouldAnimate = driver.retarget(targetValue, durationMillis, easing)
        if (!shouldAnimate && state.floatValue != driver.currentValue) {
            state.floatValue = driver.currentValue
        }
        if (isAnimating != shouldAnimate) {
            isAnimating = shouldAnimate
        }
    }

    DisposableEffect(clock, isAnimating, tickDivisor) {
        if (!isAnimating) return@DisposableEffect onDispose {}
        val unregister = clock.registerListener(tickDivisor = tickDivisor) { _, deltaNs ->
            val next = driver.step(deltaNs)
            state.floatValue = next
            if (driver.isFinished) {
                isAnimating = false
            }
        }
        onDispose { unregister() }
    }

    return state
}

/**
 * Synced replacement for Compose `animateDpAsState` backed by [LauncherSynced30FpsClock].
 */
@Composable
internal fun rememberSyncedDpAsState(
    targetValue: Dp,
    durationMillis: Int = 300,
    easing: Easing = FastOutSlowInEasing,
    tickDivisor: Int = 1,
    sharedClock: LauncherSynced30FpsClock? = null,
): State<Dp> {
    val floatState = rememberSyncedFloatAsState(
        targetValue = targetValue.value,
        durationMillis = durationMillis,
        easing = easing,
        tickDivisor = tickDivisor,
        sharedClock = sharedClock,
    )
    return remember(floatState) {
        derivedStateOf { floatState.value.dp }
    }
}

/**
 * Synced replacement for Compose `animateColorAsState` backed by [LauncherSynced30FpsClock].
 */
@Composable
internal fun rememberSyncedColorAsState(
    targetValue: Color,
    durationMillis: Int = 300,
    easing: Easing = FastOutSlowInEasing,
    tickDivisor: Int = PDC_CLOCK_TICK_DIVISOR,
    sharedClock: LauncherSynced30FpsClock? = null,
): State<Color> {
    val clock = rememberSynced30FpsClock(sharedClock)
    val state = remember { mutableStateOf(targetValue) }
    var fromColor by remember { mutableStateOf(targetValue) }
    var toColor by remember { mutableStateOf(targetValue) }
    val progressDriver = remember { SyncedFloatTweenDriver(1f) }
    var isAnimating by remember { mutableStateOf(false) }

    SideEffect {
        if (targetValue != toColor) {
            fromColor = state.value
            toColor = targetValue
            if (durationMillis <= 0) {
                state.value = targetValue
                progressDriver.retarget(1f, 0, easing)
                isAnimating = false
            } else {
                progressDriver.retarget(0f, 0, easing)
                val shouldAnimate = progressDriver.retarget(1f, durationMillis, easing)
                isAnimating = shouldAnimate
                if (!shouldAnimate) {
                    state.value = targetValue
                }
            }
        }
    }

    DisposableEffect(clock, isAnimating, tickDivisor) {
        if (!isAnimating) return@DisposableEffect onDispose {}
        val unregister = clock.registerListener(tickDivisor = tickDivisor) { _, deltaNs ->
            val progress = progressDriver.step(deltaNs)
            state.value = lerpColor(fromColor, toColor, progress)
            if (progressDriver.isFinished) {
                state.value = toColor
                isAnimating = false
            }
        }
        onDispose { unregister() }
    }

    return state
}

internal fun computeSyncedPulseValue(
    elapsedMs: Float,
    durationMillis: Int,
    initialValue: Float,
    targetValue: Float,
    easing: Easing = FastOutSlowInEasing,
): Float {
    if (durationMillis <= 0) return targetValue
    val periodMs = durationMillis * 2f
    val pos = ((elapsedMs % periodMs) + periodMs) % periodMs
    val rawFraction = if (pos <= durationMillis) {
        pos / durationMillis.toFloat()
    } else {
        (periodMs - pos) / durationMillis.toFloat()
    }
    val eased = easing.transform(rawFraction.coerceIn(0f, 1f))
    return initialValue + (targetValue - initialValue) * eased
}

/**
 * Synced replacement for `rememberInfiniteTransition().animateFloat(..., RepeatMode.Reverse)`.
 * Defaults to 15 FPS (`tickDivisor = 2`), phase-locked with PDC and road, and unregisters
 * completely (0 FPS) when [active] is `false`.
 */
@Composable
internal fun rememberSyncedPulseState(
    active: Boolean,
    initialValue: Float,
    targetValue: Float,
    durationMillis: Int,
    easing: Easing = FastOutSlowInEasing,
    tickDivisor: Int = PDC_CLOCK_TICK_DIVISOR,
    sharedClock: LauncherSynced30FpsClock? = null,
): State<Float> {
    val clock = rememberSynced30FpsClock(sharedClock)
    val state = remember { mutableFloatStateOf(initialValue) }

    DisposableEffect(clock, active, initialValue, targetValue, durationMillis, easing, tickDivisor) {
        if (!active) {
            state.floatValue = initialValue
            return@DisposableEffect onDispose {}
        }
        var elapsedMs = 0f
        state.floatValue = initialValue
        val unregister = clock.registerListener(tickDivisor = tickDivisor) { _, deltaNs ->
            val dtMs = (deltaNs / 1_000_000f).coerceIn(0f, 100f)
            elapsedMs += dtMs
            state.floatValue = computeSyncedPulseValue(
                elapsedMs = elapsedMs,
                durationMillis = durationMillis,
                initialValue = initialValue,
                targetValue = targetValue,
                easing = easing,
            )
        }
        onDispose { unregister() }
    }

    return state
}

/**
 * Lightweight 1D damped harmonic spring stepped synchronously on [LauncherSynced30FpsClock]
 * (30 FPS) to replace 60 FPS Compose `Animatable.animateTo(spring(...))` loops.
 */
internal class SyncedSpring1D(
    val stiffness: Float,
    dampingRatio: Float,
) {
    var position: Float = 0f
    var velocity: Float = 0f
    private val dampingCoeff: Float = 2f * dampingRatio * sqrt(stiffness)

    /** Returns `true` if still moving, `false` once snapped and settled at [target]. */
    fun step(target: Float, dtSec: Float): Boolean {
        val dt = dtSec.coerceIn(0f, 0.05f)
        if (dt <= 0f) return abs(target - position) >= SETTLE_EPSILON || abs(velocity) >= SETTLE_EPSILON
        val force = (target - position) * stiffness - velocity * dampingCoeff
        velocity += force * dt
        position += velocity * dt
        if (abs(target - position) < SETTLE_EPSILON && abs(velocity) < SETTLE_EPSILON) {
            position = target
            velocity = 0f
            return false
        }
        return true
    }

    companion object {
        private const val SETTLE_EPSILON = 0.05f
    }
}
