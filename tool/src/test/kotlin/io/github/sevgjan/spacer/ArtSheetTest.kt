package io.github.sevgjan.spacer

import java.io.File
import kotlin.test.Test

/** Renders every sprite and both fonts to build/art/sheet.pgm, for reviewing the art by eye. */
class ArtSheetTest {
    @Test
    fun renderSheet() {
        val sprites = buildList {
            add("ship" to Art.SHIP); Art.FLAME.forEach { add("flame" to it) }
            add("shot" to Art.SHOT); add("seeker" to Art.SEEKER); add("pellet" to Art.PELLET)
            Art.DART.forEach { add("dart" to it) }; Art.WISP.forEach { add("wisp" to it) }
            Art.HORNET.forEach { add("hornet" to it) }; Art.TURRET.forEach { add("turret" to it) }
            Art.MINE.forEach { add("mine" to it) }; Art.SPLITTER.forEach { add("splitter" to it) }
            add("rock" to Art.ROCK_BIG); add("rock" to Art.ROCK_SMALL)
            Art.CARRIER.forEach { add("carrier" to it) }; Art.DRONE.forEach { add("drone" to it) }
            add("warden" to Art.WARDEN); Art.COIL_HEAD.forEach { add("coil" to it) }; add("seg" to Art.COIL_SEGMENT)
            Art.TWIN.forEach { add("twin" to it) }; Art.HIVE.forEach { add("hive" to it) }; Art.CORE.forEach { add("core" to it) }
            add("life" to Art.PICK_LIFE); add("power" to Art.PICK_POWER); add("seek" to Art.PICK_SEEKER)
            add("lance" to Art.PICK_LANCE); add("nova" to Art.PICK_NOVA)
            add("h-life" to Art.HUD_LIFE); add("h-seek" to Art.HUD_SEEKER); add("h-lance" to Art.HUD_LANCE); add("h-nova" to Art.HUD_NOVA)
            Art.BLAST.forEach { add("blast" to it) }
        }
        val scale = 8
        val cols = 160
        // Lay sprites out in rows on a 1-bit canvas `cols` wide.
        val placed = mutableListOf<Triple<Sprite, Int, Int>>()
        var x = 2; var y = 2; var rowH = 0
        for ((_, s) in sprites) {
            if (x + s.w + 2 > cols) { x = 2; y += rowH + 3; rowH = 0 }
            placed += Triple(s, x, y); x += s.w + 3; rowH = maxOf(rowH, s.h)
        }
        y += rowH + 4
        val lines = listOf(
            Font3x5 to "0123456789 ABCDEFGHIJKLM", Font3x5 to "NOPQRSTUVWXYZ .:-!×/",
            Font5x7 to "0123456789 ABCDEFGHI", Font5x7 to "JKLMNOPQRSTU", Font5x7 to "VWXYZ !.-",
        )
        val textY = y
        val rows = textY + lines.sumOf { it.first.height + 2 } + 2
        val screen = Array(rows) { BooleanArray(cols) }
        for ((s, px, py) in placed) for (sy in 0 until s.h) for (sx in 0 until s.w) if (s[sx, sy]) screen[py + sy][px + sx] = true
        var ty = textY
        for ((font, text) in lines) {
            var cx = 2
            for (ch in text) {
                font.glyph(ch)?.let { g -> for (gy in 0 until g.h) for (gx in 0 until g.w) if (g[gx, gy]) screen[ty + gy][cx + gx] = true }
                cx += font.advance(ch)
            }
            ty += font.height + 2
        }
        val w = cols * scale
        val h = rows * scale
        val bytes = ByteArray(w * h)
        for (py in 0 until h) for (px in 0 until w) {
            val gap = px % scale == scale - 1 || py % scale == scale - 1
            bytes[py * w + px] = when {
                gap -> 0
                screen[py / scale][px / scale] -> 0xFF.toByte()
                else -> 22
            }
        }
        File("build/art").mkdirs()
        File("build/art/sheet.pgm").outputStream().use { out ->
            out.write("P5\n$w $h\n255\n".toByteArray())
            out.write(bytes)
        }
    }
}
