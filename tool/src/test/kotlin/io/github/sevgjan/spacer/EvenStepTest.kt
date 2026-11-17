package io.github.sevgjan.spacer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

/** Things should step one game pixel on a steady beat, never 3, 3, 3, 2 ticks apart. */
class EvenStepTest {
    /** Ticks between pixel changes for something moving at [v] px/tick from [x0]. */
    private fun intervals(v: Float, x0: Float, ticks: Int = 900): List<Int> {
        var x = x0
        var last = pix(x)
        var since = 0
        val out = ArrayList<Int>()
        repeat(ticks) {
            x += v
            since++
            val p = pix(x)
            if (p != last) { out += since; since = 0; last = p }
        }
        return out.drop(1)
    }

    /** The interval pattern repeats every [maxPeriod] steps or fewer. */
    private fun assertSteady(v: Float, x0: Float, maxPeriod: Int = 3) {
        val iv = intervals(v, x0)
        assertTrue("no movement at $v", iv.size > 5)
        val period = (1..maxPeriod).firstOrNull { p -> iv.indices.drop(p).all { iv[it] == iv[it - p] } }
        assertTrue("speed $v from $x0 steps unevenly: ${iv.take(30)}", period != null)
    }

    @Test
    fun everyEnemySpeedStepsSteadily() {
        // Every vx in the stage scripts and bosses, at every stage's speed-up, both directions.
        val speeds = listOf(0.3f, 0.32f, 0.38f, 0.45f, 0.5f, 0.58f, 0.63f, 0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 1.44f, 1.9f)
        for (stage in 0 until 5) {
            val level = 1f + stage * 0.07f
            for (v in speeds) for (x0 in listOf(110f, 117.3f, -3.6f)) {
                assertSteady(even(-v * level), x0)
                assertSteady(even(v * level), x0)
            }
        }
    }

    @Test
    fun shotsAndPickupsStepSteadily() {
        assertSteady(Game.SHOT_SPEED, 23.7f, maxPeriod = 2)
        assertSteady(-0.25f, 90f, maxPeriod = 1)
        assertSteady(0.125f, 50f, maxPeriod = 1)
    }

    @Test
    fun wallsMoveOnePixelEveryThreeTicks() {
        val game = Game(1, 0, seed = 3)
        game.invincible = true
        val c = Controls()
        var last = floor(game.scroll).toInt()
        var since = 0
        val gaps = HashSet<Int>()
        repeat(3000) {
            game.step(c)
            since++
            val now = floor(game.scroll).toInt()
            if (now != last) { gaps += since; since = 0; last = now }
            if (game.phase != Phase.PLAY) return@repeat
        }
        assertEquals(setOf(SCROLL_TICKS), gaps)
    }

    @Test
    fun negativePositionsRoundDown() {
        assertEquals(-1, pix(-0.5f))
        assertEquals(0, pix(0.5f))
        assertEquals(3, pix(2.999f)) // float drift from thirds counts as the whole pixel
    }
}
