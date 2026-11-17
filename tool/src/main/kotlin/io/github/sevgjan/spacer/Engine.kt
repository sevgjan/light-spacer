package io.github.sevgjan.spacer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Ticks per second. The game always steps at this rate, whatever the display does. */
const val TPS = 60

/** Terrain moves one pixel every this many ticks: a steady beat, never a stutter. */
const val SCROLL_TICKS = 3

/** Terrain movement per tick, in pixels. */
const val SCROLL = 1f / SCROLL_TICKS

/**
 * The nearest speed that steps on a steady beat. At 10x scale every game pixel is a visible jump,
 * so a speed like 0.75 px/tick (steps 1, 1, 2 ticks apart) reads as jitter. Whole numbers, halves,
 * thirds and a quarter repeat within a few ticks.
 */
fun even(v: Float): Float {
    val a = abs(v)
    if (a < 0.125f) return v
    var best = EVEN_SPEEDS[0]
    for (e in EVEN_SPEEDS) if (abs(e - a) < abs(best - a)) best = e
    return if (v < 0) -best else best
}

private val EVEN_SPEEDS = floatArrayOf(1f / 4, 1f / 3, 1f / 2, 2f / 3, 1f, 4f / 3, 3f / 2, 5f / 3, 2f, 5f / 2, 3f)

/**
 * The pixel a position falls on. Rounds down for negatives too (toInt() rounds them toward zero,
 * which held things an extra beat at the left edge), and absorbs float drift from thirds.
 */
fun pix(v: Float): Int = floor(v + 0.01f).toInt()

enum class Special(val hud: Sprite) { SEEKER(Art.HUD_SEEKER), LANCE(Art.HUD_LANCE), NOVA(Art.HUD_NOVA) }

enum class Drop(val sprite: Sprite) {
    LIFE(Art.PICK_LIFE), POWER(Art.PICK_POWER), SEEKER(Art.PICK_SEEKER), LANCE(Art.PICK_LANCE), NOVA(Art.PICK_NOVA)
}

enum class Kind(val frames: List<Sprite>, val hp: Int, val points: Int, val frameTicks: Int = 12) {
    DART(Art.DART, 1, 50, 6),
    WISP(Art.WISP, 1, 60, 14),
    HORNET(Art.HORNET, 2, 90, 5),
    TURRET(Art.TURRET, 3, 100),
    MINE(Art.MINE, 3, 80, 20),
    SPLITTER(Art.SPLITTER, 6, 150, 16),
    ROCK(listOf(Art.ROCK_BIG), Int.MAX_VALUE, 0),
    PEBBLE(listOf(Art.ROCK_SMALL), Int.MAX_VALUE, 0),
    CARRIER(Art.CARRIER, 3, 120, 10),
    DRONE(Art.DRONE, 1, 30, 4),
}

enum class Phase { PLAY, CLEAR, GAME_OVER, VICTORY }

sealed interface GameEvent {
    data class StageStart(val stage: Int, val score: Int) : GameEvent
    data object Pickup : GameEvent
    data class Kill(val big: Boolean) : GameEvent
    data object Shot : GameEvent
    data class SpecialFired(val special: Special) : GameEvent
    data object Warning : GameEvent
    data object StageClear : GameEvent
    data object Death : GameEvent
    data object BossDown : GameEvent
    data object GameOver : GameEvent
    data object Victory : GameEvent
}

/** What the player is doing this tick. The UI writes it; [Game.step] reads and clears the one-shot parts. */
class Controls {
    /** Finger movement since the last tick, already in game pixels. */
    var dragX = 0f
    var dragY = 0f
    var touchFire = false
    var keyFire = false
    var keyUp = false
    var keyDown = false
    var keyLeft = false
    var keyRight = false
    var specialPresses = 0

    val firing get() = touchFire || keyFire

    fun releaseAll() {
        touchFire = false; keyFire = false; keyUp = false; keyDown = false; keyLeft = false; keyRight = false
        dragX = 0f; dragY = 0f; specialPresses = 0
    }
}

class Enemy(val kind: Kind, var x: Float, var y: Float) {
    var hp = kind.hp
    var t = 0
    var vx = -0.7f
    var vy = 0f
    var baseY = y
    var amp = 0f
    var freq = 0.05f
    var phase = 0f
    var drop: Drop? = null
    var flash = 0
    var ceiling = false
    var fireIn = 90
    var muzzle = 0
    /** Fixed world column for wall-mounted enemies, independent of subpixel scrolling. */
    var wallX: Float? = null
    var dead = false
    val solid get() = kind == Kind.ROCK || kind == Kind.PEBBLE

    val sprite: Sprite
        get() = when (kind) {
            Kind.TURRET -> TURRETS[if (ceiling) 1 else 0][if (muzzle > 0) 1 else 0]
            else -> kind.frames[(t / kind.frameTicks) % kind.frames.size]
        }

    val cx get() = x + sprite.w / 2f
    val cy get() = y + sprite.h / 2f

    private companion object {
        val TURRETS = listOf(Art.TURRET, Art.TURRET.map { it.flippedV() })
    }
}

class Shot(var x: Float, var y: Float, val vy: Float = 0f) { var dead = false }
class Pellet(var x: Float, var y: Float, val vx: Float, val vy: Float) { var dead = false }
class Missile(var x: Float, var y: Float, var vx: Float, var vy: Float) { var life = 4 * TPS; var dead = false }
class Pickup(var x: Float, var y: Float, val drop: Drop) { var t = 0; var dead = false }
class Blast(val x: Int, val y: Int) { var t = 0 }
class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Int)
class Nova(val x: Float, val y: Float) { var r = 0f; val hit = HashSet<Any>() }
class Banner(val title: String, val sub: String?, var ticks: Int, val blink: Boolean = false)

/** One rectangle of a boss, in playfield pixels. Shots on a part that isn't [weak] are deflected. */
class Part(val sprite: Sprite, val x: Int, val y: Int, val weak: Boolean, val tag: Int = 0)

class Game(startStage: Int = 0, startScore: Int = 0, seed: Long = 1L) {
    val rng = Random(seed)
    var stageIndex = startStage; private set
    val stage: Stage get() = Stages.ALL[stageIndex]
    var score = startScore; private set
    var lives = 3; private set
    var power = 1; private set
    var special: Special? = Special.SEEKER; private set
    var charges = 3; private set
    var phase = Phase.PLAY; private set

    var tick = 0L; private set
    var stageTick = 0; private set
    var scroll = 0f; private set
    private var scrollTicks = 0

    // Player
    var px = -14f; private set
    var py = 28f; private set
    var invuln = 0; private set
    var entering = ENTRY_TICKS; private set
    var dead = false; private set
    private var deadTimer = 0
    private var cooldown = 0
    private var exitSpeed = 0f

    val enemies = ArrayList<Enemy>()
    val shots = ArrayList<Shot>()
    val pellets = ArrayList<Pellet>()
    val missiles = ArrayList<Missile>()
    val pickups = ArrayList<Pickup>()
    val blasts = ArrayList<Blast>()
    val particles = ArrayList<Particle>()
    var boss: Boss? = null; private set
    private var warning = 0
    var lance = 0; private set
    var nova: Nova? = null; private set
    var shake = 0; private set
    var banner: Banner? = null; private set
    private var clearTimer = 0
    private var bonusLeft = 0
    private var nextEvent = 0
    private val stars = List(28) { i -> floatArrayOf(rng.nextFloat() * Screen.W, rng.nextFloat() * Screen.PLAY_H, if (i < 18) 0.125f else 0.25f) }

    val events = ArrayDeque<GameEvent>()

    init {
        beginStage()
    }

    // ---- Stage flow ---------------------------------------------------------------------------

    private fun beginStage() {
        enemies.clear(); shots.clear(); pellets.clear(); missiles.clear(); pickups.clear()
        boss = null; warning = 0; lance = 0; nova = null
        stageTick = 0; scroll = 0f; scrollTicks = 0; nextEvent = 0
        px = -14f; py = 28f; entering = ENTRY_TICKS; invuln = 0; exitSpeed = 0f
        phase = Phase.PLAY
        banner = Banner("STAGE ${stageIndex + 1}", stage.name, 170)
        events += GameEvent.StageStart(stageIndex, score)
    }

    fun step(c: Controls) {
        tick++
        if (shake > 0) shake--
        banner?.let { if (--it.ticks <= 0) banner = null }
        updateStars()
        when (phase) {
            Phase.PLAY -> playTick(c)
            Phase.CLEAR -> clearTick()
            Phase.GAME_OVER, Phase.VICTORY -> worldTick()
        }
        c.dragX = 0f; c.dragY = 0f; c.specialPresses = 0
    }

    private fun playTick(c: Controls) {
        stageTick++
        scrollTicks++
        // Division keeps whole steps exact (3k / 3f == k), so the walls never drift off the beat.
        scroll = scrollTicks / SCROLL_TICKS.toFloat()
        runScript()
        if (stage.debris && boss == null && warning == 0 && stageTick % 75 == 0) driftRock()
        updatePlayer(c)
        worldTick()
        if (dead && --deadTimer <= 0) respawnOrEnd()
    }

    private fun runScript() {
        val script = stage.events
        while (nextEvent < script.size && script[nextEvent].tick <= stageTick) {
            script[nextEvent].action(this)
            nextEvent++
        }
        if (boss == null && warning == 0 && stageTick >= stage.bossAt && nextEvent >= script.size) {
            warning = WARNING_TICKS
            banner = Banner("WARNING", null, WARNING_TICKS, blink = true)
            events += GameEvent.Warning
        }
        if (warning > 0 && --warning == 0) boss = Boss.create(stage.boss, this)
    }

    /** Terrain is finished scrolling in by the time the boss arrives. */
    private val terrainEnd get() = stage.bossAt * SCROLL + Screen.W - 40f

    private fun clearTick() {
        clearTimer++
        if (clearTimer < 100) {
            // The ship leaves to the right, gathering speed.
            exitSpeed = min(exitSpeed + 0.06f, 3f)
            px += exitSpeed
        }
        if (clearTimer == 110) {
            bonusLeft = 1000 * (stageIndex + 1) + 200 * lives
            banner = Banner("STAGE CLEAR", "BONUS $bonusLeft", 170)
            events += GameEvent.StageClear
        }
        if (clearTimer > 120 && bonusLeft > 0) {
            val chunk = min(bonusLeft, 50)
            bonusLeft -= chunk; score += chunk
        }
        worldTick()
        if (clearTimer >= 290) {
            if (stageIndex == Stages.ALL.lastIndex) {
                phase = Phase.VICTORY
                banner = Banner("MISSION", "COMPLETE", Int.MAX_VALUE)
                events += GameEvent.Victory
            } else {
                stageIndex++
                beginStage()
            }
        }
    }

    private fun respawnOrEnd() {
        if (lives <= 0) {
            phase = Phase.GAME_OVER
            banner = Banner("GAME OVER", null, Int.MAX_VALUE)
            events += GameEvent.GameOver
            return
        }
        dead = false
        px = -14f; py = 28f; entering = ENTRY_TICKS; invuln = INVULN_TICKS
    }

    // ---- Player -------------------------------------------------------------------------------

    private fun updatePlayer(c: Controls) {
        if (dead) return
        if (invuln > 0) invuln--
        if (entering > 0) {
            entering--
            px += (10f - px) * 0.08f
            return
        }
        var dx = c.dragX
        var dy = c.dragY
        if (c.keyUp) dy -= KEY_SPEED
        if (c.keyDown) dy += KEY_SPEED
        if (c.keyLeft) dx -= KEY_SPEED
        if (c.keyRight) dx += KEY_SPEED
        px = (px + dx).coerceIn(0f, MAX_X)
        py = (py + dy).coerceIn(0f, (Screen.PLAY_H - Art.SHIP.h).toFloat())

        if (cooldown > 0) cooldown--
        if (c.firing && cooldown == 0) fire()
        repeat(c.specialPresses) { useSpecial() }
    }

    private fun fire() {
        val limit = if (power >= 2) 8 else 4
        if (shots.size >= limit) return
        val nose = px + 11f
        when (power) {
            1 -> shots += Shot(nose, py + 3f)
            2 -> { shots += Shot(nose - 2, py + 1f); shots += Shot(nose - 2, py + 5f) }
            else -> { shots += Shot(nose, py + 3f); shots += Shot(nose - 3, py + 1f, -1f / 3); shots += Shot(nose - 3, py + 5f, 1f / 3) }
        }
        cooldown = when (power) { 1 -> 10; 2 -> 9; else -> 8 }
        events += GameEvent.Shot
    }

    private fun useSpecial() {
        val kind = special ?: return
        if (charges <= 0 || lance > 0 || dead || entering > 0) return
        charges--
        events += GameEvent.SpecialFired(kind)
        when (kind) {
            Special.SEEKER -> missiles += Missile(px + 8f, py + 2f, 1.2f, 0f)
            Special.LANCE -> lance = LANCE_TICKS
            Special.NOVA -> nova = Nova(px + 6f, py + 3f)
        }
        if (charges == 0) special = null
    }

    /** For tests: the ship can't be hit. */
    internal var invincible = false

    private fun killPlayer() {
        if (invincible || dead || invuln > 0 || entering > 0 || phase != Phase.PLAY) return
        dead = true
        deadTimer = 100
        lives--
        power = max(1, power - 1)
        lance = 0
        shake = 14
        explode(px + 6f, py + 3f, 22, big = true)
        pellets.clear()
        events += GameEvent.Death
    }

    // ---- World --------------------------------------------------------------------------------

    private fun worldTick() {
        updateEnemies()
        boss?.let { updateBoss(it) }
        updateShots()
        updateMissiles()
        updatePellets()
        updateLance()
        updateNova()
        updatePickups()
        updateEffects()
        if (phase == Phase.PLAY && !dead) checkPlayerHit()
        enemies.removeAll { it.dead }
        shots.removeAll { it.dead }
        pellets.removeAll { it.dead }
        missiles.removeAll { it.dead }
        pickups.removeAll { it.dead }
    }

    private fun updateStars() {
        val speed = if (phase == Phase.CLEAR) 1f + exitSpeed else 1f
        for (s in stars) {
            s[0] -= s[2] * speed
            if (s[0] < 0) { s[0] += Screen.W; s[1] = rng.nextFloat() * Screen.PLAY_H }
        }
    }

    private fun updateEnemies() {
        val level = 1f + stageIndex * 0.07f
        for (e in enemies) {
            e.t++
            if (e.flash > 0) e.flash--
            if (e.muzzle > 0) e.muzzle--
            when (e.kind) {
                Kind.DART, Kind.CARRIER, Kind.ROCK, Kind.PEBBLE -> { e.x += even(e.vx * level); e.y += e.vy }
                Kind.WISP -> {
                    e.x += even(e.vx * level)
                    e.y = e.baseY + e.amp * sin(e.t * e.freq + e.phase)
                }
                Kind.HORNET -> {
                    if (e.t < 40) {
                        e.x += even(e.vx * 1.6f)
                    } else {
                        val target = py + 3f - e.sprite.h / 2f
                        e.y += (target - e.y).coerceIn(-0.45f, 0.45f)
                        e.vx = max(e.vx - 0.025f, -1.9f)
                        e.x += even(e.vx * level)
                    }
                }
                Kind.TURRET -> {
                    val wallX = e.wallX ?: (e.x + scroll - SCROLL).also { e.wallX = it }
                    e.x = floor(wallX) - floor(scroll)
                    val column = pix(e.x) + e.sprite.w / 2
                    e.y = if (e.ceiling) ceilAt(column).toFloat() else (Screen.PLAY_H - floorAt(column) - e.sprite.h).toFloat()
                    if (e.x in 8f..100f && !dead && --e.fireIn <= 0) {
                        val bx = e.cx
                        val by = if (e.ceiling) e.y + e.sprite.h else e.y
                        aim(bx, by, 0.75f * level)
                        e.muzzle = 6
                        e.fireIn = 110 - stageIndex * 8 + rng.nextInt(30)
                    }
                }
                Kind.MINE -> {
                    e.x += even(e.vx * level)
                    e.y = e.baseY + 3f * sin(e.t * 0.04f + e.phase)
                }
                Kind.SPLITTER -> {
                    e.x += even(e.vx * level)
                    e.y = e.baseY + e.amp * sin(e.t * 0.03f)
                    if (e.x in 20f..104f && --e.fireIn <= 0) {
                        aim(e.x, e.cy, 0.7f * level)
                        e.fireIn = 120
                    }
                }
                Kind.DRONE -> {
                    e.x += even(e.vx * level)
                    val target = py + 3f - 1f
                    e.y += (target - e.y).coerceIn(-0.25f, 0.25f)
                }
            }
            val s = e.sprite
            if (e.x < -s.w - 4 || e.x > Screen.W + 80 || e.y < -40 || e.y > Screen.PLAY_H + 40) e.dead = true
        }
    }

    private fun updateBoss(b: Boss) {
        b.t++
        if (b.flash > 0) b.flash--
        if (b.dying > 0) {
            b.dying++
            if (b.dying % 5 == 0) {
                val parts = b.parts()
                if (parts.isNotEmpty()) {
                    val p = parts[rng.nextInt(parts.size)]
                    explode(p.x + rng.nextFloat() * p.sprite.w, p.y + rng.nextFloat() * p.sprite.h, 6)
                }
                shake = 4
            }
            if (b.dying > BOSS_DEATH_TICKS) {
                val parts = b.parts()
                for (p in parts) explode(p.x + p.sprite.w / 2f, p.y + p.sprite.h / 2f, 14, big = true)
                shake = 20
                score += 2000 * (stageIndex + 1)
                boss = null
                pellets.clear()
                for (e in enemies) if (!e.solid) { e.dead = true; explode(e.cx, e.cy, 4) }
                events += GameEvent.BossDown
                phase = Phase.CLEAR
                clearTimer = 0
                lance = 0
            }
            return
        }
        b.update()
    }

    private fun updateShots() {
        for (s in shots) {
            s.x += SHOT_SPEED
            s.y += s.vy
            if (s.x > Screen.W || s.y < -2 || s.y > Screen.PLAY_H + 2) { s.dead = true; continue }
            if (hitsTerrain(pix(s.x) + 3, pix(s.y))) { s.dead = true; spark(s.x + 3, s.y); continue }
            val sx = pix(s.x)
            val sy = pix(s.y)
            for (e in enemies) {
                if (e.dead) continue
                if (sx + 4 > e.x && sx < e.x + e.sprite.w && sy >= pix(e.y) - 1 && sy <= pix(e.y) + e.sprite.h) {
                    s.dead = true
                    damage(e, 1)
                    break
                }
            }
            if (!s.dead) boss?.let { b -> if (hitBoss(b, sx, sy, 4, 1, 1)) s.dead = true }
        }
    }

    private fun updateMissiles() {
        for (m in missiles) {
            m.life--
            if (m.life <= 0) { m.dead = true; continue }
            val target = seekTarget(m.x, m.y)
            val speed = min(hypot(m.vx, m.vy) + 0.06f, 2.3f)
            var angle = atan2(m.vy, m.vx)
            if (target != null) {
                val want = atan2(target.second - m.y, target.first - m.x)
                var diff = want - angle
                while (diff > PI) diff -= (2 * PI).toFloat()
                while (diff < -PI) diff += (2 * PI).toFloat()
                angle += diff.coerceIn(-0.09f, 0.09f)
            }
            m.vx = cos(angle) * speed
            m.vy = sin(angle) * speed
            m.x += m.vx
            m.y += m.vy
            if (tick % 3 == 0L) particles += Particle(m.x, m.y + 1, -0.2f, 0f, 10)
            if (m.x > Screen.W + 4 || m.x < -8 || m.y < -8 || m.y > Screen.PLAY_H + 8) { m.dead = true; continue }
            if (hitsTerrain(pix(m.x) + 2, pix(m.y) + 1)) { m.dead = true; explode(m.x + 2, m.y + 1, 4); continue }
            val mx = pix(m.x)
            val my = pix(m.y)
            for (e in enemies) {
                if (e.dead) continue
                if (mx + 4 > e.x && mx < e.x + e.sprite.w && my + 3 > e.y && my < e.y + e.sprite.h) {
                    m.dead = true
                    explode(m.x + 3, m.y + 1, 6)
                    damage(e, SEEKER_DAMAGE)
                    break
                }
            }
            if (!m.dead) boss?.let { b ->
                if (hitBoss(b, mx, my, 4, 3, SEEKER_DAMAGE)) { m.dead = true; explode(m.x + 3, m.y + 1, 6) }
            }
        }
    }

    private fun seekTarget(x: Float, y: Float): Pair<Float, Float>? {
        var best: Pair<Float, Float>? = null
        var bestD = Float.MAX_VALUE
        for (e in enemies) {
            if (e.dead || e.solid || e.x > Screen.W - 2) continue
            val d = hypot(e.cx - x, e.cy - y) + if (e.cx < x) 40f else 0f
            if (d < bestD) { bestD = d; best = e.cx to e.cy }
        }
        boss?.takeIf { it.dying == 0 && !it.entering }?.parts()?.firstOrNull { it.weak }?.let { p ->
            val cx = p.x + p.sprite.w / 2f
            val cy = p.y + p.sprite.h / 2f
            val d = hypot(cx - x, cy - y)
            if (d < bestD) best = cx to cy
        }
        return best
    }

    private fun updatePellets() {
        for (p in pellets) {
            p.x += p.vx
            p.y += p.vy
            if (p.x < -4 || p.x > Screen.W + 4 || p.y < -4 || p.y > Screen.PLAY_H + 4) p.dead = true
            else if (hitsTerrain(pix(p.x) + 1, pix(p.y) + 1)) p.dead = true
        }
    }

    private fun updateLance() {
        if (lance <= 0) return
        lance--
        val y = pix(py + 3f)
        val x0 = pix(px + 12f)
        val end = lanceEnd(x0, y)
        for (p in pellets) if (p.x + 3 >= x0 && p.x <= end && abs(p.y + 1 - y) <= 2) p.dead = true
        if (lance % 3 != 0) return
        // By index: a splitter killed here adds darts to the list.
        for (i in 0 until enemies.size) {
            val e = enemies[i]
            if (e.dead || e.solid) continue
            if (e.x <= end && e.x + e.sprite.w >= x0 && y + 1 >= e.y && y - 1 <= e.y + e.sprite.h) damage(e, 1)
        }
        boss?.let { b -> hitBoss(b, x0, y - 1, end - x0, 3, 1, pierce = true) }
    }

    /** Where the lance stops: the first rock or wall in its row, or the screen edge. */
    fun lanceEnd(x0: Int, y: Int): Int {
        var end = Screen.W
        for (e in enemies) {
            if (!e.solid || e.dead) continue
            if (e.x + e.sprite.w > x0 && y >= e.y && y < e.y + e.sprite.h) end = min(end, pix(e.x))
        }
        for (x in x0 until end) if (hitsTerrain(x, y)) return x
        return end
    }

    private fun updateNova() {
        val n = nova ?: return
        n.r += 2.2f
        if (n.r > NOVA_RADIUS) { nova = null; return }
        for (p in pellets) if (hypot(p.x + 1 - n.x, p.y + 1 - n.y) < n.r) p.dead = true
        // By index: a splitter killed here adds darts to the list.
        for (i in 0 until enemies.size) {
            val e = enemies[i]
            if (e.dead || e.solid || e in n.hit) continue
            if (hypot(e.cx - n.x, e.cy - n.y) < n.r + e.sprite.w / 2f) { n.hit += e; damage(e, NOVA_DAMAGE) }
        }
        boss?.let { b ->
            if (b.dying > 0 || b.entering || b in n.hit) return@let
            val p = b.parts().firstOrNull { it.weak } ?: return@let
            if (hypot(p.x + p.sprite.w / 2f - n.x, p.y + p.sprite.h / 2f - n.y) < n.r + p.sprite.w / 2f) {
                n.hit += b
                b.damage(p, NOVA_DAMAGE)
            }
        }
    }

    private fun updatePickups() {
        for (p in pickups) {
            p.t++
            p.x -= 0.25f
            p.y += 0.15f * sin(p.t * 0.06f)
            if (p.x < -8) { p.dead = true; continue }
            if (!dead && entering == 0 && phase == Phase.PLAY &&
                p.x < px + Art.SHIP.w && p.x + 7 > px && p.y < py + Art.SHIP.h && p.y + 7 > py
            ) {
                p.dead = true
                collect(p.drop)
            }
        }
    }

    private fun collect(drop: Drop) {
        events += GameEvent.Pickup
        when (drop) {
            Drop.LIFE -> if (lives < MAX_LIVES) lives++ else score += 1000
            Drop.POWER -> if (power < 3) power++ else score += 500
            Drop.SEEKER, Drop.LANCE, Drop.NOVA -> {
                val kind = Special.valueOf(drop.name)
                if (special == kind) charges = min(charges + 3, 9) else { special = kind; charges = 3 }
            }
        }
        // A short label, unless a stage or warning banner is showing.
        if (banner.let { it == null || it.ticks <= 50 }) {
            val label = when (drop) {
                Drop.LIFE -> "1UP"; Drop.POWER -> "POWER"; Drop.SEEKER -> "SEEKER"; Drop.LANCE -> "LANCE"; Drop.NOVA -> "NOVA"
            }
            banner = Banner(label, null, 50)
        }
    }

    private fun updateEffects() {
        for (b in blasts) b.t++
        blasts.removeAll { it.t >= Art.BLAST.size * 5 }
        for (p in particles) { p.x += p.vx; p.y += p.vy; p.life-- }
        particles.removeAll { it.life <= 0 }
    }

    private fun checkPlayerHit() {
        if (invuln > 0 || entering > 0) return
        val ship = Art.SHIP
        val sx = pix(px)
        val sy = pix(py)
        for (p in pellets) {
            if (overlap(ship, sx, sy, Art.PELLET, pix(p.x), pix(p.y))) { p.dead = true; killPlayer(); return }
        }
        for (e in enemies) {
            if (overlap(ship, sx, sy, e.sprite, pix(e.x), pix(e.y))) {
                if (!e.solid) damage(e, 99)
                killPlayer(); return
            }
        }
        boss?.takeIf { it.dying == 0 }?.parts()?.forEach { part ->
            if (overlap(ship, sx, sy, part.sprite, part.x, part.y)) { killPlayer(); return }
        }
        for (y in 0 until ship.h) for (x in 0 until ship.w) {
            if (ship[x, y] && hitsTerrain(sx + x, sy + y)) { killPlayer(); return }
        }
    }

    // ---- Damage -------------------------------------------------------------------------------

    private fun damage(e: Enemy, amount: Int) {
        if (e.solid) { spark(e.x, e.cy); return }
        e.hp -= amount
        e.flash = 3
        if (e.hp > 0) return
        e.dead = true
        score += e.kind.points
        events += GameEvent.Kill(big = e.sprite.w > 8)
        explode(e.cx, e.cy, if (e.sprite.w > 8) 10 else 6)
        e.drop?.let { pickups += Pickup(e.cx - 3, e.cy - 3, it) }
        when (e.kind) {
            Kind.SPLITTER -> for (dir in listOf(-0.45f, 0.45f)) {
                spawn(Kind.DART, e.x + 2, e.cy - 2) { vx = -0.9f; vy = dir }
            }
            Kind.MINE -> for (i in 0 until 6) {
                val a = i * PI.toFloat() / 3f + 0.5f
                pellets += Pellet(e.cx - 1, e.cy - 1, cos(a) * 0.6f, sin(a) * 0.6f)
            }
            else -> Unit
        }
    }

    /** Tests a shot rectangle against the boss. Returns true if the shot was stopped. */
    private fun hitBoss(b: Boss, x: Int, y: Int, w: Int, h: Int, dmg: Int, pierce: Boolean = false): Boolean {
        if (b.dying > 0) return false
        for (part in b.parts()) {
            if (x + w <= part.x || x >= part.x + part.sprite.w || y + h <= part.y || y >= part.y + part.sprite.h) continue
            if (b.entering || !b.weakAt(part, x, y, w, h)) {
                if (!pierce) spark(part.x.toFloat(), y.toFloat())
                return !pierce
            }
            b.damage(part, dmg)
            return !pierce
        }
        return false
    }

    // ---- Spawning (used by stage scripts and bosses) ------------------------------------------

    /** Background wreckage for debris stages: one rock or pebble at a random height. */
    private fun driftRock() {
        val big = rng.nextInt(3) == 0
        spawn(if (big) Kind.ROCK else Kind.PEBBLE, Screen.W + 4f, 2f + rng.nextFloat() * (Screen.PLAY_H - 12)) {
            vx = -(0.38f + rng.nextFloat() * 0.2f)
            vy = (rng.nextFloat() - 0.5f) * 0.06f
        }
    }

    fun spawn(kind: Kind, x: Float, y: Float, init: Enemy.() -> Unit = {}): Enemy {
        val e = Enemy(kind, x, y)
        e.baseY = y
        e.init()
        enemies += e
        return e
    }

    /** A pellet from (x, y) toward the player, turned by [offset] radians. */
    fun aim(x: Float, y: Float, speed: Float, offset: Float = 0f) {
        val a = atan2(py + 3f - y, px + 6f - x) + offset
        pellets += Pellet(x - 1, y - 1, cos(a) * speed, sin(a) * speed)
    }

    fun ring(x: Float, y: Float, count: Int, speed: Float, start: Float = 0f) {
        for (i in 0 until count) {
            val a = start + i * 2 * PI.toFloat() / count
            pellets += Pellet(x - 1, y - 1, cos(a) * speed, sin(a) * speed)
        }
    }

    val playerX get() = px
    val playerY get() = py

    fun explode(x: Float, y: Float, bits: Int, big: Boolean = false) {
        blasts += Blast(x.roundToInt() - 3, y.roundToInt() - 3)
        if (big) {
            blasts += Blast(x.roundToInt() - 6, y.roundToInt() - 5)
            blasts += Blast(x.roundToInt(), y.roundToInt() - 1)
        }
        repeat(bits) {
            val a = rng.nextFloat() * 2 * PI.toFloat()
            val s = 0.3f + rng.nextFloat() * (if (big) 1.1f else 0.7f)
            particles += Particle(x, y, cos(a) * s, sin(a) * s, 12 + rng.nextInt(16))
        }
    }

    fun spark(x: Float, y: Float) {
        repeat(2) { particles += Particle(x, y, -0.3f - rng.nextFloat() * 0.5f, (rng.nextFloat() - 0.5f) * 0.8f, 6) }
    }

    fun shakeScreen(ticks: Int) { shake = max(shake, ticks) }

    // ---- Terrain ------------------------------------------------------------------------------

    fun ceilAt(sx: Int): Int {
        val t = stage.terrain ?: return 0
        val wx = floor(scroll) + sx
        return (t.ceil(wx) * terrainRamp(wx)).toInt()
    }

    fun floorAt(sx: Int): Int {
        val t = stage.terrain ?: return 0
        val wx = floor(scroll) + sx
        return (t.floor(wx) * terrainRamp(wx)).toInt()
    }

    private fun terrainRamp(wx: Float): Float {
        val inRamp = ((wx - Screen.W) / 120f).coerceIn(0f, 1f)
        val outRamp = ((terrainEnd - wx) / 120f).coerceIn(0.25f, 1f)
        return inRamp * outRamp
    }

    fun hitsTerrain(x: Int, y: Int): Boolean {
        if (stage.terrain == null || x !in 0 until Screen.W) return false
        return y < ceilAt(x) || y >= Screen.PLAY_H - floorAt(x)
    }

    // ---- Drawing ------------------------------------------------------------------------------

    fun render(s: Screen) {
        s.clear()
        val ox = if (shake > 0) (if (shake % 4 < 2) 1 else -1) else 0
        val oy = if (shake > 0) (if (shake % 3 == 0) 1 else 0) else 0
        val y0 = Screen.PLAY_Y + oy

        for (star in stars) {
            val x = pix(star[0]) + ox
            val y = pix(star[1])
            if (star[2] > 0.2f || (x + tick / 30) % 7 != 0L) s.dim(x, y0 + y)
        }
        drawTerrain(s, ox, y0)
        for (p in pickups) {
            if (p.t > 420 && (p.t / 4) % 2 == 0) continue
            s.blit(p.drop.sprite, pix(p.x) + ox, y0 + pix(p.y))
        }
        for (e in enemies) {
            s.blit(e.sprite, pix(e.x) + ox, y0 + pix(e.y), dither = e.flash > 0)
            // A carrier's light blinks so it reads as "something inside".
            if (e.kind == Kind.CARRIER && (tick / 8) % 2 == 0L) s.set(pix(e.x) + 4 + ox, y0 + pix(e.y) - 2)
        }
        boss?.let { b ->
            // Only the part that can be hurt shows the hit, so it's clear where shots count.
            for (p in b.parts()) s.blit(p.sprite, p.x + ox, y0 + p.y, dither = b.flash > 0 && p.weak && (b.flash / 2) % 2 == 0)
        }
        for (p in pellets) s.blit(Art.PELLET, pix(p.x) + ox, y0 + pix(p.y))
        for (sh in shots) s.blit(Art.SHOT, pix(sh.x) + ox, y0 + pix(sh.y))
        for (m in missiles) s.blit(Art.SEEKER, pix(m.x) + ox, y0 + pix(m.y))
        drawLance(s, ox, y0)
        nova?.let { drawCircle(s, it.x + ox, y0 + it.y, it.r, dotted = false); drawCircle(s, it.x + ox, y0 + it.y, it.r - 4, dotted = true) }
        drawPlayer(s, ox, y0)
        for (p in particles) if (p.life > 4 || p.life % 2 == 0) s.set(pix(p.x) + ox, y0 + pix(p.y))
        for (b in blasts) s.blit(Art.BLAST[min(b.t / 5, Art.BLAST.lastIndex)], b.x + ox, y0 + b.y)
        drawHud(s)
        drawBanner(s)
    }

    private fun drawPlayer(s: Screen, ox: Int, y0: Int) {
        if (dead || phase == Phase.GAME_OVER) return
        if (phase == Phase.CLEAR && px > Screen.W) return
        if (invuln > 0 && (invuln / 3) % 2 == 0) return
        val x = pix(px) + ox
        val y = y0 + pix(py)
        s.blit(Art.SHIP, x, y)
        val flame = Art.FLAME[((tick / 3) % 2).toInt()]
        s.blit(flame, x - flame.w, y + 2)
        if (phase == Phase.CLEAR) for (i in 1..(exitSpeed * 3).toInt()) s.set(x - flame.w - i * 2, y + 3)
    }

    private fun drawLance(s: Screen, ox: Int, y0: Int) {
        if (lance <= 0 || dead) return
        val y = pix(py + 3f)
        val x0 = pix(px + 12f)
        val end = lanceEnd(x0, y)
        for (x in x0 until end) {
            s.set(x + ox, y0 + y)
            if ((x + tick) % 2 == 0L) { s.set(x + ox, y0 + y - 1); s.set(x + ox, y0 + y + 1) }
            if (lance > LANCE_TICKS - 6 && (x + tick) % 3 == 0L) { s.set(x + ox, y0 + y - 2); s.set(x + ox, y0 + y + 2) }
        }
        // Flare at the impact point.
        if (end < Screen.W) for (d in -2..2) s.set(end + ox - abs(d), y0 + y + d)
    }

    private fun drawCircle(s: Screen, cx: Float, cy: Float, r: Float, dotted: Boolean) {
        if (r <= 0) return
        val steps = (r * 6).toInt().coerceAtLeast(12)
        for (i in 0 until steps) {
            if (dotted && i % 3 != 0) continue
            val a = i * 2 * PI / steps
            val x = (cx + cos(a) * r).roundToInt()
            val y = (cy + sin(a) * r).roundToInt()
            if (y >= Screen.PLAY_Y) s.set(x, y)
        }
    }

    private fun drawTerrain(s: Screen, ox: Int, y0: Int) {
        if (stage.terrain == null) return
        val ceil = IntArray(Screen.W + 2) { ceilAt(it - 1) }
        val floor = IntArray(Screen.W + 2) { floorAt(it - 1) }
        val shift = floor(scroll).toInt()
        for (x in 0 until Screen.W) {
            val wx = x + shift
            val c = ceil[x + 1]
            // The edge joins up with its neighbours so steep slopes stay a solid line.
            val cEdge = min(c, min(ceil[x], ceil[x + 2]))
            for (y in 0 until c) {
                if (y >= cEdge - 1) s.set(x + ox, y0 + y) else if (speckle(wx, y, c - y)) s.dim(x + ox, y0 + y)
            }
            val f = floor[x + 1]
            val fEdge = min(f, min(floor[x], floor[x + 2]))
            for (d in 0 until f) {
                val y = y0 + Screen.PLAY_H - 1 - d
                if (d >= fEdge - 1) s.set(x + ox, y) else if (speckle(wx, d + 101, f - d)) s.dim(x + ox, y)
            }
        }
    }

    /** Rock texture, fixed to the world so it scrolls with the walls: denser just under the edge. */
    private fun speckle(wx: Int, y: Int, depth: Int): Boolean {
        var h = wx * 73856093 xor y * 19349663
        h = (h xor (h ushr 13)) * 1274126177
        val r = (h ushr 24) and 0xFF
        return r < if (depth <= 2) 120 else 36
    }

    private fun drawHud(s: Screen) {
        s.fillRect(0, 0, Screen.W, Screen.HUD_H, on = false)
        for (i in 0 until min(lives, MAX_LIVES)) s.blit(Art.HUD_LIFE, 1 + i * 7, 1)
        special?.let { sp ->
            s.blit(sp.hud, 38, 1)
            s.text("×$charges", 44, 1)
        }
        boss?.takeIf { !it.entering && it.dying == 0 }?.let { b ->
            s.rect(57, 1, 25, 5)
            val w = (23f * b.hpFraction).roundToInt().coerceIn(0, 23)
            s.fillRect(58, 2, w, 3)
        }
        val text = score.toString().padStart(6, '0')
        s.text(text, Screen.W - Font3x5.width(text) - 1, 1)
        s.hLine(0, Screen.W - 1, Screen.HUD_H - 1, dotted = true)
    }

    private fun drawBanner(s: Screen) {
        val b = banner ?: return
        if (b.blink && (b.ticks / 12) % 2 == 0) return
        val titleW = Font5x7.width(b.title)
        val subW = b.sub?.let { Font3x5.width(it) } ?: 0
        val boxW = max(titleW, subW) + 8
        val boxH = if (b.sub != null) 7 + 4 + 5 + 6 else 7 + 6
        val bx = (Screen.W - boxW) / 2
        val by = Screen.PLAY_Y + (Screen.PLAY_H - boxH) / 2
        s.fillRect(bx, by, boxW, boxH, on = false)
        s.textCentered(b.title, by + 3, Font5x7)
        b.sub?.let { s.textCentered(it, by + 3 + 7 + 4, Font3x5) }
        if (b.blink) {
            s.hLine(bx, bx + boxW - 1, by, dotted = true)
            s.hLine(bx, bx + boxW - 1, by + boxH - 1, dotted = true)
        }
    }

    companion object {
        const val ENTRY_TICKS = 60
        const val INVULN_TICKS = 150
        const val WARNING_TICKS = 150
        const val LANCE_TICKS = 50
        const val BOSS_DEATH_TICKS = 110
        const val MAX_LIVES = 5
        const val MAX_X = 70f
        const val SHOT_SPEED = 2.5f
        const val KEY_SPEED = 1f
        const val SEEKER_DAMAGE = 6
        const val NOVA_DAMAGE = 8
        const val NOVA_RADIUS = 72f

        fun overlap(a: Sprite, ax: Int, ay: Int, b: Sprite, bx: Int, by: Int): Boolean {
            val x0 = max(ax, bx)
            val x1 = min(ax + a.w, bx + b.w)
            val y0 = max(ay, by)
            val y1 = min(ay + a.h, by + b.h)
            if (x0 >= x1 || y0 >= y1) return false
            for (y in y0 until y1) for (x in x0 until x1) if (a[x - ax, y - ay] && b[x - bx, y - by]) return true
            return false
        }
    }
}
