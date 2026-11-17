package io.github.sevgjan.spacer

import java.io.File
import kotlin.test.Test

/**
 * Plays the whole game with an invincible bot and saves frames from each stage and boss fight to
 * build/art/scenes.pgm (two columns, a row per stage), for reviewing the game on screen.
 */
class SceneSheetTest {
    @Test
    fun renderScenes() {
        val g = Game(seed = 11)
        g.invincible = true
        val c = Controls()
        val screen = Screen()
        val frames = mutableListOf<ByteArray>()
        var stageTicks = 0
        var bossTicks = -1
        var ticks = 0
        while (g.phase != Phase.VICTORY && ticks < 120_000) {
            val target = g.boss?.takeIf { !it.entering }?.parts()?.lastOrNull { it.weak }?.let { it.y + it.sprite.h / 2f }
                ?: g.enemies.filter { !it.solid && it.x > g.playerX }.minByOrNull { it.x }?.cy ?: 32f
            c.dragY = (target - 3f - g.playerY).coerceIn(-1.2f, 1.2f)
            c.touchFire = true
            c.specialPresses = if (ticks % 200 == 0) 1 else 0
            g.step(c)
            ticks++
            if (g.events.removeAll { it is GameEvent.StageStart }) { stageTicks = 0; bossTicks = -1 }
            g.events.clear()
            stageTicks++
            if (g.boss != null && bossTicks < 0) bossTicks = 0
            if (bossTicks >= 0) bossTicks++
            if (stageTicks == 32 * TPS || bossTicks == Boss.ENTER + 6 * TPS) {
                g.render(screen)
                frames += screen.pixels.copyOf()
            }
        }
        val scale = 5
        val cols = 2
        val rows = (frames.size + 1) / 2
        val gap = 4
        val w = cols * (Screen.W * scale + gap)
        val h = rows * (Screen.H * scale + gap)
        val out = ByteArray(w * h) { 40 }
        frames.forEachIndexed { i, f ->
            val ox = (i % cols) * (Screen.W * scale + gap)
            val oy = (i / cols) * (Screen.H * scale + gap)
            for (y in 0 until Screen.H * scale) for (x in 0 until Screen.W * scale) {
                val px = f[(y / scale) * Screen.W + x / scale]
                val edge = x % scale == scale - 1 || y % scale == scale - 1
                out[(oy + y) * w + ox + x] = when {
                    edge -> 0
                    px == Screen.ON -> 0xFF.toByte()
                    px == Screen.DIM -> 80
                    else -> 18
                }
            }
        }
        File("build/art").mkdirs()
        File("build/art/scenes.pgm").outputStream().use { it.write("P5\n$w $h\n255\n".toByteArray()); it.write(out) }
    }
}
