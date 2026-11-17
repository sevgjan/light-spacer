package io.github.sevgjan.spacer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.math.floor

class TurretTest {
    @Test
    fun `a cave world column keeps its shape as it scrolls across screen columns`() {
        val game = Game(startStage = 1)
        game.invincible = true
        val controls = Controls()
        repeat(600) { game.step(controls) }
        val shift = floor(game.scroll).toInt()
        val ceiling = (0..100).map { game.ceilAt(it) }
        val floorHeights = (0..100).map { game.floorAt(it) }
        repeat(100) {
            game.step(controls)
            val moved = floor(game.scroll).toInt() - shift
            for (x in moved..100) {
                assertEquals(ceiling[x], game.ceilAt(x - moved), "ceiling changed shape at $x")
                assertEquals(floorHeights[x], game.floorAt(x - moved), "floor changed shape at $x")
            }
        }
    }

    @Test
    fun `wall turrets stay at a fixed height while the cave scrolls`() {
        for (ceiling in listOf(false, true)) {
            for (column in 25..95) {
                val game = Game(startStage = 1, seed = 42)
                game.invincible = true
                val controls = Controls()
                repeat(600) { game.step(controls) }
                val turret = game.spawn(Kind.TURRET, column.toFloat(), 0f) {
                    this.ceiling = ceiling
                }
                game.step(controls)
                val height = turret.y
                repeat(40) {
                    game.step(controls)
                    assertEquals(height, turret.y, "turret jitters at column $column, ceiling=$ceiling")
                }
            }
        }
    }
}
