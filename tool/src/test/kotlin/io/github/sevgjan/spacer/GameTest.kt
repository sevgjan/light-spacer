package io.github.sevgjan.spacer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameTest {
    /** Steers toward the nearest target in front of the ship, always firing, using specials as they come. */
    private fun bot(g: Game, c: Controls) {
        val ahead = g.enemies.filter { !it.solid && it.x > g.playerX && it.x < Screen.W }
            .minByOrNull { it.x }
        val bossTarget = g.boss?.takeIf { !it.entering && it.dying == 0 }?.parts()?.lastOrNull { it.weak }
        val targetY = when {
            bossTarget != null -> bossTarget.y + bossTarget.sprite.h / 2f
            ahead != null -> ahead.cy
            else -> 32f
        } - 3f
        c.dragY = (targetY - g.playerY).coerceIn(-1.2f, 1.2f)
        c.dragX = (10f - g.playerX).coerceIn(-1f, 1f)
        c.touchFire = true
        if (g.tick % 90 == 0L) c.specialPresses = 1
    }

    @Test
    fun `an invincible bot clears all five stages`() {
        val g = Game(seed = 42)
        g.invincible = true
        val c = Controls()
        val stagesSeen = mutableListOf<Int>()
        var ticks = 0
        var stageStart = 0
        var bossStart = -1
        while (g.phase != Phase.VICTORY && ticks < 120_000) {
            bot(g, c)
            g.step(c)
            if (g.boss != null && bossStart < 0) bossStart = ticks
            while (true) {
                val e = g.events.removeFirstOrNull() ?: break
                if (e is GameEvent.StageStart) { stagesSeen += e.stage; stageStart = ticks }
                if (e == GameEvent.BossDown) {
                    println("stage ${g.stageIndex + 1}: ${(ticks - stageStart) / TPS}s, boss fight ${(ticks - bossStart) / TPS}s, score ${g.score}")
                    bossStart = -1
                }
            }
            ticks++
        }
        assertEquals(Phase.VICTORY, g.phase, "stuck in stage ${g.stageIndex + 1} (${g.stage.name}), boss=${g.boss?.hp}")
        assertEquals(listOf(0, 1, 2, 3, 4), stagesSeen)
        assertTrue(g.score > 10_000, "score ${g.score}")
        // Roughly 1.5 to 3 minutes a stage.
        assertTrue(ticks in 5 * 60 * TPS / 2..5 * 4 * 60 * TPS, "took $ticks ticks")
    }

    @Test
    fun `an idle player eventually runs out of ships`() {
        val g = Game(seed = 3)
        val c = Controls()
        var ticks = 0
        while (g.phase == Phase.PLAY && ticks < 60 * 60 * TPS) { g.step(c); ticks++ }
        assertEquals(Phase.GAME_OVER, g.phase)
        assertEquals(0, g.lives)
        assertTrue(g.events.any { it is GameEvent.GameOver })
    }

    @Test
    fun `cave walls always leave a passage`() {
        for (stage in Stages.ALL) {
            val t = stage.terrain ?: continue
            var wx = 0f
            while (wx < stage.bossAt * SCROLL + 400) {
                val gap = Screen.PLAY_H - t.ceil(wx) - t.floor(wx)
                assertTrue(gap >= 30, "${stage.name}: gap $gap at $wx")
                wx += 1f
            }
        }
    }

    @Test
    fun `every stage has waves before its boss`() {
        for (stage in Stages.ALL) {
            assertTrue(stage.events.size >= 15, stage.name)
            assertTrue(stage.events.zipWithNext().all { (a, b) -> a.tick <= b.tick }, stage.name)
            assertTrue(stage.bossAt > stage.events.last().tick, stage.name)
            assertTrue(stage.bossAt in 50 * TPS..90 * TPS, "${stage.name}: ${stage.bossAt / TPS}s")
        }
    }

    @Test
    fun `fonts cover every word the game prints`() {
        val words = Stages.ALL.map { it.name } + listOf(
            "STAGE 0123456789", "WARNING", "STAGE CLEAR", "BONUS", "MISSION", "COMPLETE", "GAME OVER", "PAUSED",
            "1UP", "POWER", "SEEKER", "LANCE", "NOVA", "BEST", "×", "SPACER",
        )
        for (word in words) for (ch in word) {
            if (ch == ' ') continue
            assertTrue(Font3x5.glyph(ch) != null, "3x5 lacks '$ch'")
            if (ch != '×') assertTrue(Font5x7.glyph(ch) != null, "5x7 lacks '$ch'")
        }
    }

    @Test
    fun `the same seed plays out the same`() {
        fun run(): Int {
            val g = Game(seed = 9)
            val c = Controls()
            repeat(3000) { bot(g, c); g.step(c) }
            return g.score * 31 + g.enemies.size
        }
        assertEquals(run(), run())
    }

    @Test
    fun `pixel overlap ignores transparent corners`() {
        val a = Sprite.of("#..", "...", "...")
        val b = Sprite.of("...", "...", "..#")
        assertTrue(!Game.overlap(a, 0, 0, b, 0, 0))
        assertTrue(Game.overlap(a, 2, 2, b, 0, 0))
    }
}

class SfxTest {
    @Test
    fun `every sound is short, audible and never clips`() {
        val sfx = Sfx(48_000)
        val sounds = listOf(
            sfx.shot, sfx.kill, sfx.killBig,
            sfx.pickup, sfx.death, sfx.seeker, sfx.lance, sfx.nova, sfx.warning, sfx.bossDown,
            sfx.stageStart, sfx.stageClear, sfx.gameOver, sfx.victory, sfx.toggleOn,
        )
        for (s in sounds) {
            assertTrue(s.size in 2_000..48_000 * 4, "length ${s.size}")
            val peak = s.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue(peak in 2_000..32_000, "peak $peak")
        }
    }
}

/** Writes every sound to build/sfx/ as WAV, for listening on a computer. */
class SfxExportTest {
    @Test
    fun exportWavs() {
        val rate = 44_100
        val sfx = Sfx(rate)
        val all = mapOf(
            "01-shot" to sfx.shot, "02-kill" to sfx.kill, "03-kill-big" to sfx.killBig, "04-pickup" to sfx.pickup,
            "05-seeker" to sfx.seeker, "06-lance" to sfx.lance, "07-nova" to sfx.nova, "08-warning" to sfx.warning,
            "09-boss-down" to sfx.bossDown, "10-death" to sfx.death, "11-stage-start" to sfx.stageStart,
            "12-stage-clear" to sfx.stageClear, "13-game-over" to sfx.gameOver, "14-victory" to sfx.victory,
            "15-sound-on" to sfx.toggleOn,
        )
        val dir = java.io.File("build/sfx").apply { mkdirs() }
        for ((name, pcm) in all) {
            val data = java.nio.ByteBuffer.allocate(44 + pcm.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
            data.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            data.put("data".toByteArray()).putInt(pcm.size * 2)
            pcm.forEach { data.putShort(it) }
            java.io.File(dir, "$name.wav").writeBytes(data.array())
        }
    }
}
