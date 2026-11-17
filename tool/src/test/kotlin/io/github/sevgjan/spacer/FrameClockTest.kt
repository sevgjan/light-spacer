package io.github.sevgjan.spacer

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameClockTest {
    @Test
    fun `60 Hz with alternating timing jitter advances once per frame`() {
        val clock = FrameClock()
        assertEquals(0, clock.advance(0))
        repeat(10_000) { i ->
            val jitter = if (i % 2 == 0) -300_000L else 300_000L
            assertEquals(1, clock.advance((i + 1) * 1_000_000_000L / TPS + jitter))
        }
    }

    @Test
    fun `120 Hz does not double simulation speed`() {
        val clock = FrameClock()
        clock.advance(0)
        var ticks = 0
        repeat(1200) { i -> ticks += clock.advance((i + 1) * 1_000_000_000L / 120) }
        assertEquals(600, ticks)
    }

    @Test
    fun `30 Hz catches up with two ticks per frame`() {
        val clock = FrameClock()
        clock.advance(0)
        repeat(300) { i -> assertEquals(2, clock.advance((i + 1) * 1_000_000_000L / 30)) }
    }

    @Test
    fun `long stall has bounded catchup without a lasting backlog`() {
        val clock = FrameClock()
        clock.advance(0)
        assertEquals(4, clock.advance(2_000_000_000L))
        assertEquals(1, clock.advance(2_016_666_667L))
    }

    @Test
    fun `reset discards time spent paused`() {
        val clock = FrameClock()
        clock.advance(0)
        clock.advance(16_666_667L)
        clock.reset()
        assertEquals(0, clock.advance(8_000_000_000L))
        assertEquals(1, clock.advance(8_016_666_667L))
    }
}
