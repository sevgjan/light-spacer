package io.github.sevgjan.spacer


class Spawn(val tick: Int, val action: (Game) -> Unit)

class Stage(
    val name: String,
    val boss: BossKind,
    val terrain: Terrain?,
    val events: List<Spawn>,
    /** The tick the boss warning starts, once the last wave is out. */
    val bossAt: Int,
    /** Rocks drift through the whole stage, not just in scripted waves. */
    val debris: Boolean = false,
)

/**
 * Cave walls: a smooth ridge from value noise with a little jagged detail on top, as thickness in
 * pixels from the top ([ceil]) and the bottom ([floor]) of the playfield at world column `wx`.
 */
class Terrain(private val seed: Int, private val maxCeil: Int, private val maxFloor: Int) {
    fun ceil(wx: Float): Float = 2f + (maxCeil - 2) * noise(wx, seed)
    fun floor(wx: Float): Float = 2f + (maxFloor - 2) * noise(wx, seed + 7919)

    private fun noise(wx: Float, s: Int): Float {
        val k = wx / KNOT
        val i = kotlin.math.floor(k).toInt()
        val f = k - i
        val smooth = f * f * (3 - 2 * f)
        val ridge = hash(i, s) + (hash(i + 1, s) - hash(i, s)) * smooth
        val jag = hash(kotlin.math.floor(wx / 3f).toInt(), s + 13) * 0.14f
        return (ridge * 0.86f + jag).coerceIn(0f, 1f)
    }

    private fun hash(i: Int, s: Int): Float {
        var x = i * 374761393 + s * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0xFFFF) / 65535f
    }

    private companion object {
        const val KNOT = 18f
    }
}

private class Script {
    var tick = 0
    val events = mutableListOf<Spawn>()

    fun after(seconds: Float, action: Game.() -> Unit) {
        tick += (seconds * TPS).toInt()
        events += Spawn(tick, action)
    }
}

private fun stage(name: String, boss: BossKind, terrain: Terrain? = null, debris: Boolean = false, block: Script.() -> Unit): Stage {
    val script = Script().apply(block)
    return Stage(name, boss, terrain, script.events, script.tick + 5 * TPS, debris)
}

// ---- Wave patterns ----------------------------------------------------------------------------

/** Just past the right edge. */
private const val R = 110f

private fun Game.darts(n: Int, y: Float, gap: Float = 10f, vy: Float = 0f, speed: Float = 0.75f) =
    repeat(n) { spawn(Kind.DART, R + it * gap, y) { vx = -speed; this.vy = vy } }

/** Five darts in a V pointing at the player. */
private fun Game.vee(y: Float) {
    val slots = listOf(0f to 0f, 9f to -6f, 9f to 6f, 18f to -12f, 18f to 12f)
    for ((dx, dy) in slots) spawn(Kind.DART, R + dx, y + dy) { vx = -0.8f }
}

private fun Game.wisps(n: Int, y: Float, amp: Float = 10f, gap: Float = 12f) =
    repeat(n) { i -> spawn(Kind.WISP, R + i * gap, y) { vx = -0.45f; this.amp = amp; freq = 0.05f; phase = i * 0.9f } }

private fun Game.hornets(vararg ys: Float) =
    ys.forEachIndexed { i, y -> spawn(Kind.HORNET, R + i * 14, y) { vx = -0.9f } }

private fun Game.turrets(n: Int, ceiling: Boolean, gap: Float = 26f) =
    repeat(n) { i -> spawn(Kind.TURRET, R + i * gap, 0f) { this.ceiling = ceiling; fireIn = 30 + i * 25 } }

private fun Game.mines(vararg ys: Float) =
    ys.forEachIndexed { i, y -> spawn(Kind.MINE, R + i * 16, y) { vx = -0.3f; phase = i * 1.7f } }

private fun Game.splitter(y: Float, delay: Float = 0f) =
    spawn(Kind.SPLITTER, R + delay, y) { vx = -0.32f; amp = 6f; fireIn = 70 }

private fun Game.carrier(y: Float, drop: Drop) =
    spawn(Kind.CARRIER, R, y) { vx = -0.5f; this.drop = drop }

private fun Game.drones(n: Int, y: Float) =
    repeat(n) { i -> spawn(Kind.DRONE, R + i * 7, y + if (i % 2 == 0) -3 else 3) { vx = -0.85f } }

/** Drifting wreckage: blocks shots and the lance, and can't be destroyed. */
private fun Game.rocks(n: Int) = repeat(n) { i ->
    val big = rng.nextInt(3) != 0
    val y = 2f + rng.nextFloat() * (Screen.PLAY_H - 12)
    spawn(if (big) Kind.ROCK else Kind.PEBBLE, R + i * 22 + rng.nextFloat() * 10, y) {
        vx = -(0.42f + rng.nextFloat() * 0.25f)
        vy = (rng.nextFloat() - 0.5f) * 0.08f
    }
}

// ---- The five stages ----------------------------------------------------------------------------

object Stages {
    val ALL: List<Stage> = listOf(
        stage("OUTER BELT", BossKind.WARDEN) {
            after(2.5f) { darts(5, 14f) }
            after(3.5f) { darts(5, 42f) }
            after(3.5f) { wisps(4, 28f, amp = 12f) }
            after(4f) { vee(32f) }
            after(3f) { darts(4, 8f, vy = 0.12f); darts(4, 50f, vy = -0.12f) }
            after(3.5f) { carrier(30f, Drop.POWER) }
            after(3f) { wisps(5, 18f, amp = 8f) }
            after(3.5f) { wisps(5, 44f, amp = 8f) }
            after(3f) { hornets(16f, 46f) }
            after(3.5f) { darts(6, 30f, gap = 8f) }
            after(3f) { vee(16f) }
            after(2.5f) { vee(46f) }
            after(3.5f) { carrier(20f, Drop.SEEKER); darts(3, 48f) }
            after(3f) { hornets(10f, 30f, 50f) }
            after(4f) { wisps(6, 32f, amp = 18f, gap = 10f) }
            after(3.5f) { mines(20f, 44f) }
            after(3f) { darts(5, 12f); darts(5, 52f) }
            after(3.5f) { hornets(20f, 40f) }
            after(3f) { vee(30f) }
        },
        stage("FROST CAVES", BossKind.COIL, Terrain(seed = 2, maxCeil = 15, maxFloor = 16)) {
            after(3f) { wisps(4, 30f, amp = 6f) }
            after(3f) { turrets(2, ceiling = false) }
            after(3f) { darts(5, 28f) }
            after(3f) { turrets(2, ceiling = true) }
            after(2.5f) { hornets(26f, 36f) }
            after(3.5f) { carrier(30f, Drop.LANCE) }
            after(3f) { turrets(3, ceiling = false, gap = 22f) }
            after(2f) { wisps(5, 32f, amp = 7f) }
            after(3.5f) { vee(31f) }
            after(3f) { turrets(2, ceiling = true); darts(4, 34f) }
            after(3.5f) { hornets(24f, 30f, 40f) }
            after(3.5f) { mines(26f, 38f) }
            after(3f) { carrier(32f, Drop.LIFE) }
            after(3f) { turrets(2, ceiling = false); turrets(2, ceiling = true, gap = 30f) }
            after(3.5f) { darts(6, 30f, gap = 8f) }
            after(3f) { wisps(6, 30f, amp = 8f, gap = 10f) }
            after(3.5f) { hornets(26f, 34f, 30f) }
            after(3f) { vee(32f) }
        },
        stage("WRECK FIELD", BossKind.TWINS, debris = true) {
            after(2.5f) { rocks(3) }
            after(2f) { darts(5, 20f) }
            after(2.5f) { rocks(3); mines(40f) }
            after(3f) { splitter(28f) }
            after(3f) { rocks(4) }
            after(2.5f) { carrier(14f, Drop.NOVA) }
            after(2f) { darts(5, 44f, vy = -0.15f) }
            after(3f) { mines(14f, 32f, 50f) }
            after(3f) { rocks(4); hornets(30f) }
            after(3f) { splitter(16f); splitter(44f, delay = 20f) }
            after(3.5f) { carrier(36f, Drop.POWER) }
            after(2.5f) { rocks(5) }
            after(3f) { wisps(5, 28f, amp = 14f) }
            after(3f) { vee(20f) }
            after(2.5f) { vee(44f) }
            after(3f) { rocks(4); mines(24f, 40f) }
            after(3.5f) { splitter(30f); hornets(10f, 52f) }
            after(3f) { rocks(5) }
            after(3f) { darts(6, 14f); darts(6, 50f) }
        },
        stage("THE HIVE", BossKind.HIVE, Terrain(seed = 4, maxCeil = 13, maxFloor = 13)) {
            after(2.5f) { drones(6, 30f) }
            after(3f) { turrets(2, ceiling = false) }
            after(2.5f) { hornets(24f, 40f) }
            after(3f) { drones(8, 26f) }
            after(2.5f) { carrier(32f, Drop.SEEKER) }
            after(3f) { splitter(30f) }
            after(3.5f) { turrets(2, ceiling = true); drones(6, 36f) }
            after(3f) { wisps(6, 32f, amp = 9f) }
            after(3.5f) { hornets(22f, 32f, 42f) }
            after(3f) { carrier(30f, Drop.LIFE) }
            after(3f) { splitter(24f); splitter(40f, delay = 24f) }
            after(3.5f) { drones(10, 32f) }
            after(3f) { turrets(3, ceiling = false); turrets(3, ceiling = true) }
            after(3.5f) { vee(32f) }
            after(3f) { mines(26f, 40f) }
            after(3f) { hornets(24f, 30f, 36f, 42f) }
            after(3f) { drones(10, 28f) }
        },
        stage("THE CORE", BossKind.CORE, Terrain(seed = 5, maxCeil = 13, maxFloor = 13)) {
            after(2.5f) { darts(6, 30f, gap = 8f) }
            after(2.5f) { turrets(2, ceiling = false); turrets(2, ceiling = true) }
            after(3f) { carrier(32f, Drop.POWER) }
            after(2.5f) { hornets(22f, 42f) }
            after(2.5f) { wisps(6, 32f, amp = 10f) }
            after(3f) { splitter(32f) }
            after(3f) { drones(8, 26f) }
            after(2.5f) { mines(24f, 40f) }
            after(3f) { carrier(30f, Drop.NOVA) }
            after(2.5f) { vee(32f) }
            after(2.5f) { hornets(20f, 32f, 44f) }
            after(3f) { turrets(3, ceiling = false); darts(5, 28f) }
            after(3f) { splitter(26f); splitter(38f, delay = 22f) }
            after(3.5f) { carrier(32f, Drop.LIFE) }
            after(2.5f) { drones(10, 32f) }
            after(3f) { hornets(22f, 28f, 34f, 40f, 46f) }
            after(3f) { wisps(6, 32f, amp = 12f); turrets(2, ceiling = true) }
            after(3.5f) { vee(30f) }
            after(2.5f) { drones(8, 36f) }
        },
    )
}
