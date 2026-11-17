package io.github.sevgjan.spacer

/**
 * Every picture in the game, drawn by hand for Spacer. Enemies face left (toward the player),
 * and two-frame sprites alternate to animate.
 */
object Art {
    // ---- Player -------------------------------------------------------------------------------

    val SHIP = Sprite.of(
        "...###......",
        "#..#####....",
        "###########.",
        "############",
        "#########...",
        "#..##.......",
        "...#........",
    )

    /** Engine flame, drawn just behind the ship's tail on row 3. */
    val FLAME = listOf(
        Sprite.of(
            "....",
            ".###",
            "....",
        ),
        Sprite.of(
            "..#.",
            "####",
            "..#.",
        ),
    )

    val SHOT = Sprite.of("####")

    val SEEKER = Sprite.of(
        "##..",
        "####",
        "##..",
    )

    // ---- Enemy fire ---------------------------------------------------------------------------

    val PELLET = Sprite.of(
        ".#.",
        "###",
        ".#.",
    )

    // ---- Enemies ------------------------------------------------------------------------------

    val DART = listOf(
        Sprite.of(
            ".....##",
            "...###.",
            "#######",
            "...###.",
            ".....##",
        ),
        Sprite.of(
            "....##.",
            "...###.",
            "#######",
            "...###.",
            "....##.",
        ),
    )

    val WISP = listOf(
        Sprite.of(
            "..###..",
            ".##.##.",
            "#######",
            ".#.#.#.",
            "#..#..#",
            ".#.#.#.",
        ),
        Sprite.of(
            "..###..",
            ".##.##.",
            "#######",
            ".#.#.#.",
            ".#.#.#.",
            "#..#..#",
        ),
    )

    val HORNET = listOf(
        Sprite.of(
            "...##.##",
            "....####",
            ".####...",
            "##.#####",
            ".#######",
            "..#.#.#.",
        ),
        Sprite.of(
            "........",
            "....####",
            ".####.##",
            "##.#####",
            ".#######",
            ".#.#.#..",
        ),
    )

    /** Floor turret; the ceiling one is this flipped. Frame 1 is the muzzle flash. */
    val TURRET = listOf(
        Sprite.of(
            "...#...",
            "...#...",
            "..###..",
            ".#####.",
            "##.#.##",
            "#######",
        ),
        Sprite.of(
            "..###..",
            "...#...",
            "..###..",
            ".#####.",
            "##.#.##",
            "#######",
        ),
    )

    val MINE = listOf(
        Sprite.of(
            "...#...",
            ".#####.",
            ".##.##.",
            "###.###",
            ".##.##.",
            ".#####.",
            "...#...",
        ),
        Sprite.of(
            "#.....#",
            ".#####.",
            ".##.##.",
            ".#...#.",
            ".##.##.",
            ".#####.",
            "#.....#",
        ),
    )

    val SPLITTER = listOf(
        Sprite.of(
            "....####...",
            "..##....##.",
            ".#..####..#",
            "####....###",
            "####....###",
            ".#..####..#",
            "..##....##.",
            "....####...",
        ),
        Sprite.of(
            "....####...",
            "..##....##.",
            ".#..####..#",
            "###.....###",
            "###.....###",
            ".#..####..#",
            "..##....##.",
            "....####...",
        ),
    )

    val ROCK_BIG = Sprite.of(
        "...####..",
        ".##....#.",
        "#...#...#",
        "#..#....#",
        "#.....#.#",
        ".#...#..#",
        "..#####..",
    )

    val ROCK_SMALL = Sprite.of(
        ".###.",
        "#..##",
        "#...#",
        ".###.",
    )

    /** Blinks its belly light; drops a pickup when shot down. */
    val CARRIER = listOf(
        Sprite.of(
            "...####..",
            ".##....#.",
            "#########",
            "#.#.#.#.#",
            "#########",
            "..#...#..",
        ),
        Sprite.of(
            "...####..",
            ".##.##.#.",
            "#########",
            ".#.#.#.##",
            "#########",
            "..#...#..",
        ),
    )

    val DRONE = listOf(
        Sprite.of(
            ".##..",
            "#####",
            ".##..",
        ),
        Sprite.of(
            "..#..",
            "#####",
            "..#..",
        ),
    )

    // ---- Bosses -------------------------------------------------------------------------------

    /** Stage 1: an armoured gunship that bobs at the right edge. */
    val WARDEN = Sprite.of(
        "..........####..........",
        ".......####..####.......",
        "....###..........###....",
        "..##....######......#...",
        "##..##.#......#.....####",
        "#....#.#.####.#.....#..#",
        "######.#.#..#.#.....####",
        "#....#.#.####.#.....#..#",
        "##..##.#......#.....####",
        "..##....######......#...",
        "....###..........###....",
        ".......####..####.......",
        "..........####..........",
    )

    /** Stage 2: the head of a serpent whose body trails behind it. */
    val COIL_HEAD = listOf(
        Sprite.of(
            "...########.",
            "..#........#",
            ".#..##.....#",
            "#..#..#....#",
            "#...##.....#",
            "#..........#",
            "########...#",
            "#.#.#.#....#",
            ".##########.",
        ),
        Sprite.of(
            "...########.",
            "..#........#",
            ".#..##.....#",
            "#..####....#",
            "#...##.....#",
            "########...#",
            "........#..#",
            "#.#.#.#..#.#",
            ".#########..",
        ),
    )

    val COIL_SEGMENT = Sprite.of(
        "..###..",
        ".#...#.",
        "#.###.#",
        "#.#.#.#",
        "#.###.#",
        ".#...#.",
        "..###..",
    )

    /** Stage 3: one of two pods; the lower one is drawn flipped. */
    val TWIN = listOf(
        Sprite.of(
            ".....#######...",
            "...##.......##.",
            "..#..#######..#",
            ".#..#.......#.#",
            "#..#..#####..##",
            "####..#...#..##",
            "#..#..#####..##",
            ".#..#.......#.#",
            "..#..#######..#",
            "...##.......##.",
            ".....#######...",
        ),
        Sprite.of(
            ".....#######...",
            "...##.......##.",
            "..#..#######..#",
            ".#..#.......#.#",
            "#..#..#####..##",
            "#####.#####..##",
            "#..#..#####..##",
            ".#..#.......#.#",
            "..#..#######..#",
            "...##.......##.",
            ".....#######...",
        ),
    )

    /** Stage 4: a hive with a hatch that opens to release drones and expose its core. */
    val HIVE = listOf(
        hive(open = false),
        hive(open = true),
    )

    /** Stage 5: the core. Frame 0 shuttered, frame 1 open. */
    val CORE = listOf(
        core(open = false),
        core(open = true),
    )

    // ---- Pickups ------------------------------------------------------------------------------

    private fun boxed(vararg glyph: String): Sprite {
        val rows = mutableListOf("#######")
        for (g in glyph) rows += "#$g#"
        rows += "#######"
        return Sprite.of(*rows.toTypedArray())
    }

    val PICK_LIFE = boxed(
        ".....",
        ".#.#.",
        ".###.",
        "..#..",
        ".....",
    )
    val PICK_POWER = boxed(
        ".##..",
        ".#.#.",
        ".##..",
        ".#...",
        ".....",
    )
    val PICK_SEEKER = boxed(
        ".....",
        ".##..",
        ".###.",
        ".##..",
        ".....",
    )
    val PICK_LANCE = boxed(
        ".....",
        ".....",
        "#####",
        ".....",
        ".....",
    )
    val PICK_NOVA = boxed(
        "..#..",
        ".#.#.",
        "#...#",
        ".#.#.",
        "..#..",
    )

    // ---- Status bar icons (5 tall) ------------------------------------------------------------

    val HUD_LIFE = Sprite.of(
        "..##..",
        "#.###.",
        "######",
        "#####.",
        "#.#...",
    )
    val HUD_SEEKER = Sprite.of(
        ".....",
        "##...",
        "####.",
        "##...",
        ".....",
    )
    val HUD_LANCE = Sprite.of(
        ".....",
        ".....",
        "#####",
        ".....",
        ".....",
    )
    val HUD_NOVA = Sprite.of(
        "..#..",
        ".#.#.",
        "#...#",
        ".#.#.",
        "..#..",
    )

    // ---- Explosions ---------------------------------------------------------------------------

    val BLAST = listOf(
        Sprite.of(
            ".......",
            ".......",
            "..#.#..",
            "...#...",
            "..#.#..",
            ".......",
            ".......",
        ),
        Sprite.of(
            ".......",
            ".#...#.",
            "..###..",
            "..#.#..",
            "..###..",
            ".#...#.",
            ".......",
        ),
        Sprite.of(
            "#..#..#",
            ".#...#.",
            "...#...",
            "#.#.#.#",
            "...#...",
            ".#...#.",
            "#..#..#",
        ),
        Sprite.of(
            "#.....#",
            ".......",
            ".......",
            "#.....#",
            ".......",
            ".......",
            "#.....#",
        ),
    )

    // ---- Helpers ------------------------------------------------------------------------------

    private fun hive(open: Boolean): Sprite {
        val w = 26
        val h = 30
        val rows = (0 until h).map { y ->
            buildString {
                for (x in 0 until w) {
                    val dx = (x - 12.5) / 13.0
                    val dy = (y - 14.5) / 15.0
                    val d = dx * dx + dy * dy
                    val edge = d in 0.80..1.0
                    // Staggered cells inside the shell.
                    val comb = d < 0.80 && (y % 4 == 1 || (x + (y / 4 % 2) * 2) % 4 == 0)
                    // The hatch: a box on the left-middle where drones come out.
                    val inHatch = x in 3..11 && y in 11..18
                    val hatchFrame = inHatch && (x == 3 || x == 11 || y == 11 || y == 18)
                    val hatchFill = inHatch && !hatchFrame && if (open) {
                        (x in 6..8 && y in 13..16) && !(x == 7 && y in 14..15)
                    } else {
                        (x + y) % 2 == 0
                    }
                    append(if (edge || (comb && !inHatch) || hatchFrame || hatchFill) '#' else '.')
                }
            }
        }
        return Sprite.of(*rows.toTypedArray())
    }

    private fun core(open: Boolean): Sprite {
        val rows = mutableListOf<String>()
        val r = 14.0
        val cx = 13.5
        val cy = 13.5
        for (y in 0 until 28) {
            val sb = StringBuilder()
            for (x in 0 until 28) {
                val dx = x - cx
                val dy = y - cy
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                val ring = d in (r - 1.6)..(r - 0.4)
                val inner = d in (r - 4.6)..(r - 3.6)
                val ribs = d in (r - 3.6)..(r - 1.6) && (x + y) % 3 == 0
                val pupil = if (open) d < 3.2 || (d in 5.4..6.4) else kotlin.math.abs(dy) < 0.8 && d < r - 4.6
                val lid = !open && (kotlin.math.abs(dy) in 2.5..3.4) && d < r - 4.6 && x % 2 == 0
                sb.append(if (ring || inner || ribs || pupil || lid) '#' else '.')
            }
            rows += sb.toString()
        }
        return Sprite.of(*rows.toTypedArray())
    }

    private fun fit(row: String, w: Int) = row.take(w).padEnd(w, '.')
}
