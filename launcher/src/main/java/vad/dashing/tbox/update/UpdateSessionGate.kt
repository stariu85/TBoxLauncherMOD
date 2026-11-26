package vad.dashing.tbox.update

import java.util.concurrent.atomic.AtomicBoolean

object UpdateSessionGate {
    private val started = AtomicBoolean(false)

    var checkStartedThisSession: Boolean
        get() = started.get()
        set(value) {
            started.set(value)
        }

    fun tryBeginSessionCheck(): Boolean = started.compareAndSet(false, true)
}
