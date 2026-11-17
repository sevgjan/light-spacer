package io.github.sevgjan.spacer

/** A 1-bit image. Rows are written with `#` for a lit pixel and `.` for an unlit one. */
class Sprite(val w: Int, val h: Int, private val bits: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = bits[y * w + x]

    fun flippedV(): Sprite = Sprite(w, h, BooleanArray(w * h) { i -> bits[(h - 1 - i / w) * w + i % w] })

    fun flippedH(): Sprite = Sprite(w, h, BooleanArray(w * h) { i -> bits[(i / w) * w + (w - 1 - i % w)] })

    companion object {
        fun of(vararg rows: String): Sprite {
            require(rows.isNotEmpty()) { "empty sprite" }
            val w = rows[0].length
            require(rows.all { it.length == w }) { "ragged sprite: ${rows.map { it.length }}" }
            require(rows.all { row -> row.all { it == '#' || it == '.' } }) { "sprites use only # and ." }
            return Sprite(w, rows.size, BooleanArray(w * rows.size) { rows[it / w][it % w] == '#' })
        }
    }
}

/**
 * The game's 1-bit screen: [W] x [H] pixels. The top [HUD_H] rows are the status bar and the
 * playfield sits below it, so game code draws in playfield coordinates through [blit] with [PLAY_Y].
 */
class Screen {
    val pixels = ByteArray(W * H)

    fun clear() = pixels.fill(0)

    fun set(x: Int, y: Int, on: Boolean = true) {
        if (x !in 0 until W || y !in 0 until H) return
        pixels[y * W + x] = if (on) ON else OFF
    }

    /** Lights a pixel in the dim grey used for scenery, without dimming anything already lit. */
    fun dim(x: Int, y: Int) {
        if (x !in 0 until W || y !in 0 until H) return
        if (pixels[y * W + x] == OFF) pixels[y * W + x] = DIM
    }

    operator fun get(x: Int, y: Int): Boolean = x in 0 until W && y in 0 until H && pixels[y * W + x] == ON

    /** Draws [sprite]'s lit pixels. [dither] draws only every other one, used to show a hit. */
    fun blit(sprite: Sprite, x: Int, y: Int, invert: Boolean = false, dither: Boolean = false) {
        for (sy in 0 until sprite.h) for (sx in 0 until sprite.w) {
            if (!sprite[sx, sy]) continue
            if (dither && (sx + sy) % 2 == 0) continue
            set(x + sx, y + sy, !invert)
        }
    }

    fun fillRect(x: Int, y: Int, w: Int, h: Int, on: Boolean = true) {
        for (yy in y until y + h) for (xx in x until x + w) set(xx, yy, on)
    }

    fun hLine(x0: Int, x1: Int, y: Int, dotted: Boolean = false) {
        for (x in x0..x1) if (!dotted || (x + y) % 2 == 0) set(x, y)
    }

    fun rect(x: Int, y: Int, w: Int, h: Int) {
        hLine(x, x + w - 1, y)
        hLine(x, x + w - 1, y + h - 1)
        for (yy in y until y + h) {
            set(x, yy); set(x + w - 1, yy)
        }
    }

    /** Draws [text] in [font]; returns the x just past the last glyph. */
    fun text(text: String, x: Int, y: Int, font: PixelFont = Font3x5, invert: Boolean = false): Int {
        var cx = x
        for (ch in text) {
            val glyph = font.glyph(ch)
            if (glyph != null) blit(glyph, cx, y, invert)
            cx += font.advance(ch)
        }
        return cx
    }

    fun textCentered(text: String, y: Int, font: PixelFont = Font3x5, cx: Int = W / 2) {
        text(text, cx - font.width(text) / 2, y, font)
    }

    companion object {
        const val OFF: Byte = 0
        const val ON: Byte = 1
        /** Background scenery: stars and rock texture, so they never look like something to dodge. */
        const val DIM: Byte = 2
        const val W = 108
        const val H = 72
        const val HUD_H = 8
        const val PLAY_Y = HUD_H
        const val PLAY_H = H - HUD_H
    }
}

class PixelFont(private val glyphs: Map<Char, Sprite>, val height: Int, private val spacing: Int = 1) {
    fun glyph(ch: Char): Sprite? = glyphs[ch.uppercaseChar()]

    fun advance(ch: Char): Int = if (ch == ' ') height - 1 else (glyph(ch)?.w ?: 0) + spacing

    fun width(text: String): Int = text.sumOf { advance(it) } - spacing

    val chars: Set<Char> get() = glyphs.keys
}

private fun font(height: Int, spec: String): Map<Char, Sprite> {
    // Each glyph: a char line, then [height] rows.
    val lines = spec.trimIndent().lines().filter { it.isNotBlank() }
    require(lines.size % (height + 1) == 0) { "font spec is not a whole number of glyphs" }
    return lines.chunked(height + 1).associate { chunk ->
        chunk[0].trim().single() to Sprite.of(*chunk.drop(1).map { it.trim() }.toTypedArray())
    }
}

/** The status-bar font: digits, capitals and a few signs, 3 wide (M, N, W, X are 5) and 5 tall. */
val Font3x5 = PixelFont(
    font(
        5,
        """
        0
        ###
        #.#
        #.#
        #.#
        ###
        1
        .#
        ##
        .#
        .#
        .#
        2
        ###
        ..#
        ###
        #..
        ###
        3
        ###
        ..#
        .##
        ..#
        ###
        4
        #.#
        #.#
        ###
        ..#
        ..#
        5
        ###
        #..
        ###
        ..#
        ###
        6
        ###
        #..
        ###
        #.#
        ###
        7
        ###
        ..#
        .#.
        .#.
        .#.
        8
        ###
        #.#
        ###
        #.#
        ###
        9
        ###
        #.#
        ###
        ..#
        ###
        A
        .#.
        #.#
        ###
        #.#
        #.#
        B
        ##.
        #.#
        ##.
        #.#
        ##.
        C
        .##
        #..
        #..
        #..
        .##
        D
        ##.
        #.#
        #.#
        #.#
        ##.
        E
        ###
        #..
        ##.
        #..
        ###
        F
        ###
        #..
        ##.
        #..
        #..
        G
        .##
        #..
        #.#
        #.#
        .##
        H
        #.#
        #.#
        ###
        #.#
        #.#
        I
        ###
        .#.
        .#.
        .#.
        ###
        J
        ..#
        ..#
        ..#
        #.#
        .#.
        K
        #.#
        #.#
        ##.
        #.#
        #.#
        L
        #..
        #..
        #..
        #..
        ###
        M
        #...#
        ##.##
        #.#.#
        #...#
        #...#
        N
        #..#
        ##.#
        #.##
        #..#
        #..#
        O
        .#.
        #.#
        #.#
        #.#
        .#.
        P
        ##.
        #.#
        ##.
        #..
        #..
        Q
        .#.
        #.#
        #.#
        ##.
        .##
        R
        ##.
        #.#
        ##.
        #.#
        #.#
        S
        .##
        #..
        .#.
        ..#
        ##.
        T
        ###
        .#.
        .#.
        .#.
        .#.
        U
        #.#
        #.#
        #.#
        #.#
        ###
        V
        #.#
        #.#
        #.#
        #.#
        .#.
        W
        #...#
        #...#
        #.#.#
        ##.##
        #...#
        X
        #.#
        #.#
        .#.
        #.#
        #.#
        Y
        #.#
        #.#
        .#.
        .#.
        .#.
        Z
        ###
        ..#
        .#.
        #..
        ###
        .
        .
        .
        .
        .
        #
        :
        .
        #
        .
        #
        .
        -
        ...
        ...
        ###
        ...
        ...
        !
        #
        #
        #
        .
        #
        ×
        ...
        #.#
        .#.
        #.#
        ...
        /
        ..#
        ..#
        .#.
        #..
        #..
        """,
    ),
    height = 5,
)

/** The banner font, 5 x 7, for stage titles and messages. */
val Font5x7 = PixelFont(
    font(
        7,
        """
        0
        .###.
        #...#
        #..##
        #.#.#
        ##..#
        #...#
        .###.
        1
        ..#..
        .##..
        ..#..
        ..#..
        ..#..
        ..#..
        .###.
        2
        .###.
        #...#
        ....#
        ...#.
        ..#..
        .#...
        #####
        3
        #####
        ...#.
        ..#..
        ...#.
        ....#
        #...#
        .###.
        4
        ...#.
        ..##.
        .#.#.
        #..#.
        #####
        ...#.
        ...#.
        5
        #####
        #....
        ####.
        ....#
        ....#
        #...#
        .###.
        6
        ..##.
        .#...
        #....
        ####.
        #...#
        #...#
        .###.
        7
        #####
        ....#
        ...#.
        ..#..
        .#...
        .#...
        .#...
        8
        .###.
        #...#
        #...#
        .###.
        #...#
        #...#
        .###.
        9
        .###.
        #...#
        #...#
        .####
        ....#
        ...#.
        .##..
        A
        .###.
        #...#
        #...#
        #####
        #...#
        #...#
        #...#
        B
        ####.
        #...#
        #...#
        ####.
        #...#
        #...#
        ####.
        C
        .###.
        #...#
        #....
        #....
        #....
        #...#
        .###.
        D
        ###..
        #..#.
        #...#
        #...#
        #...#
        #..#.
        ###..
        E
        #####
        #....
        #....
        ####.
        #....
        #....
        #####
        F
        #####
        #....
        #....
        ####.
        #....
        #....
        #....
        G
        .###.
        #...#
        #....
        #.###
        #...#
        #...#
        .####
        H
        #...#
        #...#
        #...#
        #####
        #...#
        #...#
        #...#
        I
        .###.
        ..#..
        ..#..
        ..#..
        ..#..
        ..#..
        .###.
        J
        ..###
        ...#.
        ...#.
        ...#.
        ...#.
        #..#.
        .##..
        K
        #...#
        #..#.
        #.#..
        ##...
        #.#..
        #..#.
        #...#
        L
        #....
        #....
        #....
        #....
        #....
        #....
        #####
        M
        #...#
        ##.##
        #.#.#
        #.#.#
        #...#
        #...#
        #...#
        N
        #...#
        #...#
        ##..#
        #.#.#
        #..##
        #...#
        #...#
        O
        .###.
        #...#
        #...#
        #...#
        #...#
        #...#
        .###.
        P
        ####.
        #...#
        #...#
        ####.
        #....
        #....
        #....
        Q
        .###.
        #...#
        #...#
        #...#
        #.#.#
        #..#.
        .##.#
        R
        ####.
        #...#
        #...#
        ####.
        #.#..
        #..#.
        #...#
        S
        .####
        #....
        #....
        .###.
        ....#
        ....#
        ####.
        T
        #####
        ..#..
        ..#..
        ..#..
        ..#..
        ..#..
        ..#..
        U
        #...#
        #...#
        #...#
        #...#
        #...#
        #...#
        .###.
        V
        #...#
        #...#
        #...#
        #...#
        #...#
        .#.#.
        ..#..
        W
        #...#
        #...#
        #...#
        #.#.#
        #.#.#
        #.#.#
        .#.#.
        X
        #...#
        #...#
        .#.#.
        ..#..
        .#.#.
        #...#
        #...#
        Y
        #...#
        #...#
        #...#
        .#.#.
        ..#..
        ..#..
        ..#..
        Z
        #####
        ....#
        ...#.
        ..#..
        .#...
        #....
        #####
        !
        ..#..
        ..#..
        ..#..
        ..#..
        ..#..
        .....
        ..#..
        .
        .....
        .....
        .....
        .....
        .....
        .....
        ..#..
        -
        .....
        .....
        .....
        .###.
        .....
        .....
        .....
        """,
    ),
    height = 7,
)
