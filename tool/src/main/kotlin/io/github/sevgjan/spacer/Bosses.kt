package io.github.sevgjan.spacer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class BossKind { WARDEN, COIL, TWINS, HIVE, CORE }

/**
 * A stage boss. It slides in from the right (untouchable while [entering]), fights, and when its
 * health runs out the game plays [dying] out and removes it.
 */
abstract class Boss(protected val game: Game, val maxHp: Int) {
    var hp = maxHp
    var t = 0
    var flash = 0
    var dying = 0
    val entering get() = t < ENTER

    /** 0 while sliding in, 1 once in place; eases out. */
    protected val arrival: Float
        get() {
            val k = (t / ENTER.toFloat()).coerceIn(0f, 1f)
            return 1f - (1f - k) * (1f - k)
        }

    open val hpFraction: Float get() = hp.toFloat() / maxHp
    protected val angry get() = hpFraction < 0.5f
    protected val level get() = 1f + game.stageIndex * 0.06f

    abstract fun parts(): List<Part>
    abstract fun update()

    /** Whether a shot rectangle overlapping [part] hurts. By default, any hit on a weak part does. */
    open fun weakAt(part: Part, x: Int, y: Int, w: Int, h: Int): Boolean = part.weak

    open fun damage(part: Part, amount: Int) {
        if (dying > 0) return
        hp -= amount
        flash = 4
        if (hp <= 0) { hp = 0; dying = 1 }
    }

    protected fun slideX(target: Float) = target + (1f - arrival) * 60f

    companion object {
        const val ENTER = 140

        fun create(kind: BossKind, game: Game): Boss = when (kind) {
            BossKind.WARDEN -> Warden(game)
            BossKind.COIL -> Coil(game)
            BossKind.TWINS -> Twins(game)
            BossKind.HIVE -> Hive(game)
            BossKind.CORE -> Core(game)
        }
    }
}

/** Stage 1: bobs at the right edge, fires spreads from its forward guns and aimed shots from its core. */
private class Warden(game: Game) : Boss(game, 90) {
    private val sprite = Art.WARDEN
    private var x = 0f
    private var y = 0f

    override fun parts() = listOf(Part(sprite, x.toInt(), y.toInt(), weak = true))

    override fun update() {
        x = slideX(80f)
        y = (Screen.PLAY_H - sprite.h) / 2f + 17f * sin(t * 0.018f)
        if (entering) return
        val f = t - ENTER
        if (f % (if (angry) 70 else 90) == 0) {
            val n = if (angry) 5 else 3
            for (i in 0 until n) game.aim(x + 1f, y + 6f, 0.85f * level, (i - (n - 1) / 2f) * 0.22f)
        }
        if (f % 55 == 27) game.aim(x + 11f, y + 6f, 1.0f * level)
        if (hpFraction < 0.3f && f % 160 == 80) {
            game.spawn(Kind.DART, x + 18f, y - 6f) { vx = -0.9f }
            game.spawn(Kind.DART, x + 18f, y + sprite.h + 1f) { vx = -0.9f }
        }
    }
}

/** Stage 2: a serpent. Only the head can be hurt; the armoured body trails along the head's path. */
private class Coil(game: Game) : Boss(game, 60) {
    private val segments = 7
    private var clock = 0f

    private fun headAt(time: Float): Pair<Float, Float> {
        val enter = ((ENTER - time) / ENTER).coerceIn(0f, 1f)
        val x = 70f + 20f * sin(time * 0.017f) + enter * enter * 70f
        val y = 26f + 21f * sin(time * 0.029f + 1f)
        return x to y
    }

    override fun parts(): List<Part> {
        val list = ArrayList<Part>(segments + 1)
        for (i in segments downTo 1) {
            val (sx, sy) = headAt(clock - i * 13f)
            list += Part(Art.COIL_SEGMENT, sx.toInt() + 3, sy.toInt() + 1, weak = false, tag = i)
        }
        val (hx, hy) = headAt(clock)
        val open = !entering && ((t - ENTER) % 75) in 55..74
        list += Part(Art.COIL_HEAD[if (open) 1 else 0], hx.toInt(), hy.toInt(), weak = true)
        return list
    }

    override fun update() {
        clock += if (angry) 1.45f else 1f
        if (entering) return
        val f = t - ENTER
        if (f % 75 == 70) {
            val (hx, hy) = headAt(clock)
            val n = if (angry) 5 else 3
            for (i in 0 until n) game.aim(hx + 1f, hy + 6f, 0.85f * level, (i - (n - 1) / 2f) * 0.2f)
        }
    }
}

/** Stage 3: two pods that take turns firing bursts, then cross fire together. Both must fall. */
private class Twins(game: Game) : Boss(game, 84) {
    private val podHp = intArrayOf(42, 42)
    override val hpFraction get() = (podHp[0] + podHp[1]).toFloat() / maxHp
    private val frames = listOf(Art.TWIN, Art.TWIN.map { it.flippedV() })
    private var burstLeft = 0
    private var burstPod = 0

    private fun podY(i: Int): Float {
        val swing = 0.5f - 0.5f * cos(t * 0.022f + i * PI.toFloat())
        return if (i == 0) 2f + 16f * swing else Screen.PLAY_H - 13f - 16f * swing
    }

    private fun podX(i: Int) = slideX(86f + 4f * sin(t * 0.04f + i * 2f))

    private fun eyeOpen(i: Int) = burstLeft > 0 && burstPod == i || (t - ENTER) % 240 in 200..239

    override fun parts(): List<Part> = (0..1).filter { podHp[it] > 0 }.map { i ->
        Part(frames[i][if (eyeOpen(i)) 1 else 0], podX(i).toInt(), podY(i).toInt(), weak = true, tag = i)
    }

    override fun damage(part: Part, amount: Int) {
        if (dying > 0) return
        val i = part.tag
        if (podHp[i] <= 0) return
        podHp[i] -= amount
        flash = 4
        if (podHp[i] <= 0) {
            podHp[i] = 0
            game.explode(podX(i) + 7, podY(i) + 5, 16, big = true)
            game.shakeScreen(12)
        }
        hp = podHp[0] + podHp[1]
        if (hp <= 0) dying = 1
    }

    override fun update() {
        if (entering) return
        val f = t - ENTER
        val alive = (0..1).filter { podHp[it] > 0 }
        if (alive.isEmpty()) return
        val alone = alive.size == 1
        val every = if (alone) 45 else 70
        if (burstLeft == 0 && f % every == 0) {
            burstPod = if (alone) alive[0] else (f / every) % 2
            burstLeft = 4
        }
        if (burstLeft > 0 && f % 6 == 0) {
            burstLeft--
            game.aim(podX(burstPod), podY(burstPod) + 5f, 1.0f * level, (burstLeft - 1.5f) * 0.06f)
        }
        if (!alone && f % 240 == 239) {
            // Cross fire: each pod sprays toward the other half of the screen.
            for (i in 0..1) for (k in 0 until 5) {
                // The top pod sprays down and left, the bottom one up and left.
                val dir = if (i == 0) 1 else -1
                val a = PI.toFloat() - dir * (0.15f + k * 0.12f)
                val speed = 0.8f * level
                game.pellets += Pellet(podX(i), podY(i) + 5f, cos(a) * speed, sin(a) * speed)
            }
        }
    }
}

/** Stage 4: a hive. Its hatch opens on a cycle, letting out drones and exposing the core inside. */
private class Hive(game: Game) : Boss(game, 64) {
    private val period = 210
    private var x = 0f
    private var y = 0f
    private val open get() = !entering && ((t - ENTER) % period) >= 110

    override fun parts() = listOf(Part(Art.HIVE[if (open) 1 else 0], x.toInt(), y.toInt(), weak = true))

    /** Open, the hatch is hurt by anything in its rows; nothing stands in front of it. */
    override fun weakAt(part: Part, x: Int, y: Int, w: Int, h: Int): Boolean {
        if (!open) return false
        val hy = part.y + 11
        return y + h > hy && y < hy + 8
    }

    override fun update() {
        x = slideX(79f)
        y = (Screen.PLAY_H - Art.HIVE[0].h) / 2f + 3f * sin(t * 0.02f)
        if (entering) return
        val f = (t - ENTER) % period
        if (open && f % 24 == 0) game.spawn(Kind.DRONE, x + 4f, y + 13f) { vx = -0.8f }
        if (f == 110 || f == 0 && angry) game.ring(x + 13f, y + 15f, if (angry) 12 else 8, 0.6f * level, (t % 7) * 0.1f)
        if (!open && f % 50 == 25) game.aim(x, y + 15f, 0.9f * level)
    }
}

/** Stage 5: the core. Shuttered it fires rings; open it is exposed but fires back harder. */
private class Core(game: Game) : Boss(game, 170) {
    private val period = 300
    private var x = 0f
    private var y = 0f
    private var spiral = 0f
    private val open get() = !entering && ((t - ENTER) % period) >= 140

    override fun parts() = listOf(Part(Art.CORE[if (open) 1 else 0], x.toInt(), y.toInt(), weak = true))

    override fun weakAt(part: Part, x: Int, y: Int, w: Int, h: Int) = open

    override fun update() {
        x = slideX(76f)
        y = (Screen.PLAY_H - Art.CORE[0].h) / 2f + 4f * sin(t * 0.015f)
        if (entering) return
        val f = (t - ENTER) % period
        val cx = x + 14f
        val cy = y + 14f
        if (!open) {
            if (hpFraction < 0.3f) {
                if (f % 4 == 0) {
                    spiral += 0.47f
                    game.pellets += Pellet(cx - 1, cy - 1, cos(spiral) * 0.7f, sin(spiral) * 0.7f)
                }
            } else if (f % 45 == 0) {
                game.ring(cx, cy, 10, 0.6f * level, f * 0.03f)
            }
        } else {
            if (f % 28 == 0) for (i in -1..1) game.aim(x + 2f, cy, 1.0f * level, i * 0.18f)
            if (hpFraction < 0.6f && f % 60 == 30) {
                game.spawn(Kind.DRONE, x - 2f, cy - 10f) { vx = -0.9f }
                game.spawn(Kind.DRONE, x - 2f, cy + 8f) { vx = -0.9f }
            }
        }
        if (f == 140) game.shakeScreen(6)
    }
}
