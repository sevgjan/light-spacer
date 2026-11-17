package io.github.sevgjan.spacer

import kotlin.math.sin
import kotlin.random.Random

/** The title screen's picture: the logo over a moving starfield, with the ship cruising below. */
class Title {
    private val rng = Random(7)
    private val stars = List(40) { floatArrayOf(rng.nextFloat() * Screen.W, rng.nextFloat() * Screen.H, STAR_SPEEDS[rng.nextInt(STAR_SPEEDS.size)]) }
    private val logo: Sprite = buildLogo("SPACER")
    var tick = 0L
        private set

    fun step() {
        tick++
        for (s in stars) {
            s[0] -= s[2]
            if (s[0] < 0) { s[0] += Screen.W; s[1] = rng.nextFloat() * Screen.H }
        }
    }

    fun render(screen: Screen, best: Int) {
        screen.clear()
        for (s in stars) {
            val x = pix(s[0])
            screen.dim(x, pix(s[1]))
            // Fast stars streak.
            if (s[2] > 0.6f) screen.dim(x + 1, pix(s[1]))
        }
        val lx = (Screen.W - logo.w) / 2
        val ly = 12
        screen.fillRect(lx - 3, ly - 3, logo.w + 6, logo.h + 6, on = false)
        screen.blit(logo, lx, ly)
        // Speed lines trailing the logo.
        for (i in 0 until 4) {
            val y = ly + 3 + i * 4
            val len = 6 + ((tick / 4 + i * 3) % 5).toInt()
            screen.hLine(lx - 4 - len, lx - 5, y)
        }
        val shipY = 46 + (3 * sin(tick * 0.05)).toInt()
        val shipX = 20 + (6 * sin(tick * 0.021)).toInt()
        screen.fillRect(shipX - 6, shipY - 1, Art.SHIP.w + 8, Art.SHIP.h + 2, on = false)
        screen.blit(Art.SHIP, shipX, shipY)
        val flame = Art.FLAME[((tick / 3) % 2).toInt()]
        screen.blit(flame, shipX - flame.w, shipY + 2)
        if (best > 0) {
            val text = "BEST " + best.toString().padStart(6, '0')
            val w = Font3x5.width(text)
            screen.fillRect(Screen.W - w - 3, Screen.H - 8, w + 3, 8, on = false)
            screen.text(text, Screen.W - w - 1, Screen.H - 6)
        }
    }

    private companion object {
        /** Steady-beat speeds (see [even]); the fastest ones streak. */
        val STAR_SPEEDS = floatArrayOf(0.25f, 0.25f, 0.5f, 0.5f, 1f)

        /** The word in the banner font at double size. */
        fun buildLogo(word: String): Sprite {
            val font = Font5x7
            val scale = 2
            val gap = 2
            val h = font.height * scale
            val w = word.sumOf { (font.glyph(it)?.w ?: 0) * scale + gap } - gap
            val bits = Array(h) { CharArray(w) { '.' } }
            var cx = 0
            for (ch in word) {
                val g = font.glyph(ch) ?: continue
                for (gy in 0 until g.h) for (gx in 0 until g.w) {
                    if (!g[gx, gy]) continue
                    for (sy in 0 until scale) for (sx in 0 until scale) bits[gy * scale + sy][cx + gx * scale + sx] = '#'
                }
                cx += g.w * scale + gap
            }
            return Sprite.of(*bits.map { String(it) }.toTypedArray())
        }
    }
}
