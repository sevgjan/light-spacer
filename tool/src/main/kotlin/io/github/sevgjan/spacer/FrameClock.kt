package io.github.sevgjan.spacer

/** Keeps fixed simulation ticks aligned with vsync without skip/double pairs from tiny jitter. */
internal class FrameClock {
    private var lastNanos: Long? = null
    private var pending = 0L

    fun reset() {
        lastNanos = null
        pending = 0L
    }

    fun advance(nanos: Long): Int {
        val previous = lastNanos
        lastNanos = nanos
        if (previous == null) return 0
        pending += (nanos - previous).coerceIn(0L, 100_000_000L)
        // Carry fractional time (including a small debt) rather than changing game speed.
        val requested = ((pending + TICK_NANOS / 2) / TICK_NANOS).toInt().coerceAtLeast(0)
        val steps = requested.coerceAtMost(4)
        pending = if (requested > 4) 0L else pending - steps * TICK_NANOS
        return steps
    }

    private companion object {
        const val TICK_NANOS = (1_000_000_000L + TPS / 2) / TPS
    }
}
