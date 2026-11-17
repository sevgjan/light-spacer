package io.github.sevgjan.spacer

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * The game's sounds, synthesised on the phone like an old arcade sound chip: pulse waves,
 * a triangle bass and a noise channel, with pitch sweeps. The melodies use the Japanese "in"
 * scale on D (D, E flat, G, A, B flat), which gives the jingles their colour.
 */
class Sfx(private val rate: Int) {
    private var lfsr = 0x7FFF

    // ---- Effects ------------------------------------------------------------------------------

    /** Your gun: a short, quiet blip that drops in pitch. */
    val shot = render(0.05f) { t, out -> out.pulse(t, sweep(t, 1760f, 880f, 0.05f), 0.25f, 0.13f * decay(t, 60f)) }

    /** A small enemy going pop: a noise crunch under a falling pulse. */
    val kill = render(0.16f) { t, out ->
        out.noise(t, 0.42f * decay(t, 22f), period = 2 + (t * 60).toInt())
        out.pulse(t, sweep(t, 660f, 110f, 0.12f), 0.5f, 0.2f * decay(t, 30f))
    }

    /** A big enemy: a longer, lower boom. */
    val killBig = render(0.32f) { t, out ->
        out.noise(t, 0.5f * decay(t, 10f), period = 4 + (t * 50).toInt())
        out.pulse(t, sweep(t, 330f, 55f, 0.25f), 0.5f, 0.24f * decay(t, 14f))
    }

    val pickup = arpeggio(listOf(74, 79, 81, 86, 91), 0.045f, duty = 0.25f, gain = 0.22f)

    val death = render(0.9f) { t, out ->
        out.noise(t, 0.55f * decay(t, 4.5f), period = 3 + (t * 40).toInt())
        out.pulse(t, sweep(t, 880f, 40f, 0.8f), 0.5f, 0.22f * decay(t, 4f))
    }

    /** Seeker: a rising zip as the missile leaves. */
    val seeker = render(0.2f) { t, out -> out.pulse(t, sweep(t, 220f, 1320f, 0.2f), 0.125f, 0.2f * decay(t, 8f)) }

    /** Lance: a buzzing beam that wobbles. */
    val lance = render(0.8f) { t, out ->
        val wobble = 1 + 0.03f * kotlin.math.sin(t * 2 * kotlin.math.PI.toFloat() * 14f)
        out.pulse(t, midi(57) * wobble, 0.125f, 0.16f * env(t, 0.01f, 0.8f))
        out.pulse(t, midi(69) * wobble * 1.005f, 0.5f, 0.07f * env(t, 0.01f, 0.8f))
    }

    /** Nova: a deep boom with a rumble. */
    val nova = render(0.9f) { t, out ->
        out.triangle(t, sweep(t, 160f, 30f, 0.6f), 0.5f * decay(t, 4f))
        out.noise(t, 0.35f * decay(t, 5f), period = 10 + (t * 30).toInt())
    }

    /** A two-tone siren in the scale, three times. */
    val warning = melody(
        lead = listOf(N(69, 2), N(63, 2), N(69, 2), N(63, 2), N(69, 2), N(63, 2)),
        bass = emptyList(), step = 0.11f, duty = 0.5f, gain = 0.2f,
    )

    val bossDown = run {
        val boom = render(1.0f) { t, out ->
            out.noise(t, 0.55f * decay(t, 3f), period = 5 + (t * 30).toInt())
            out.triangle(t, sweep(t, 120f, 30f, 0.8f), 0.45f * decay(t, 3f))
        }
        val fanfare = melody(
            lead = listOf(N(62, 1), N(67, 1), N(69, 1), N(74, 1), N(79, 1), N(81, 1), N(86, 4)),
            bass = listOf(N(38, 4), N(43, 4)), step = 0.07f, duty = 0.25f, gain = 0.2f,
        )
        mixAt(boom, fanfare, 0.45f)
    }

    val stageStart = melody(
        lead = listOf(N(62, 1), N(69, 1), N(70, 1), N(74, 3), N(0, 1), N(70, 1), N(74, 4)),
        bass = listOf(N(38, 4), N(45, 4), N(38, 4)), step = 0.085f, duty = 0.5f, gain = 0.2f,
    )

    val stageClear = melody(
        lead = listOf(N(74, 1), N(70, 1), N(69, 1), N(74, 1), N(79, 2), N(81, 2), N(86, 6)),
        bass = listOf(N(43, 4), N(45, 4), N(38, 6)), step = 0.08f, duty = 0.25f, gain = 0.2f,
    )

    val gameOver = melody(
        lead = listOf(N(74, 2), N(70, 2), N(69, 2), N(63, 3), N(62, 7)),
        bass = listOf(N(46, 4), N(45, 4), N(38, 8)), step = 0.12f, duty = 0.5f, gain = 0.2f,
    )

    val victory = melody(
        lead = listOf(N(62, 1), N(67, 1), N(69, 1), N(74, 2), N(69, 1), N(74, 1), N(79, 2), N(81, 2), N(86, 2), N(81, 1), N(86, 6)),
        bass = listOf(N(38, 4), N(43, 4), N(45, 4), N(38, 8)), step = 0.085f, duty = 0.25f, gain = 0.2f,
    )

    val toggleOn = arpeggio(listOf(81, 86), 0.06f, duty = 0.5f, gain = 0.2f)

    // ---- Chip voices --------------------------------------------------------------------------

    /** A note for [melody]: MIDI pitch (0 is a rest) and length in steps. */
    class N(val midi: Int, val steps: Int)

    private fun midi(note: Int): Float = 440f * 2f.pow((note - 69) / 12f)

    private fun decay(t: Float, speed: Float) = exp(-t * speed)

    /** Quick attack, held, then released over the last fifth. */
    private fun env(t: Float, attack: Float, length: Float): Float {
        val a = (t / attack).coerceAtMost(1f)
        val r = ((length - t) / (length * 0.2f)).coerceIn(0f, 1f)
        return a * r
    }

    /** Exponential slide from [from] to [to] Hz over [time] seconds. */
    private fun sweep(t: Float, from: Float, to: Float, time: Float): Float =
        from * (to / from).pow((t / time).coerceAtMost(1f))

    private inner class Out(val buf: FloatArray) {
        var i = 0
        private val phases = DoubleArray(4)
        private var slot = 0
        private var noiseHold = 0
        private var noiseValue = 0f

        fun begin(index: Int) { i = index; slot = 0 }

        private fun step(freq: Float): Double {
            val s = slot++
            phases[s] = (phases[s] + freq / rate) % 1.0
            return phases[s]
        }

        fun pulse(t: Float, freq: Float, duty: Float, gain: Float) {
            buf[i] += (if (step(freq) < duty) 1f else -1f) * gain
        }

        fun triangle(t: Float, freq: Float, gain: Float) {
            val p = step(freq).toFloat()
            // Stepped like a chip triangle: 16 levels.
            val v = ((if (p < 0.5f) 4 * p - 1 else 3 - 4 * p) * 8).toInt() / 8f
            buf[i] += v * gain
        }

        /** Chip noise: a 15-bit shift register, clocked every [period] samples (longer is lower). */
        fun noise(t: Float, gain: Float, period: Int) {
            if (noiseHold <= 0) {
                val bit = (lfsr xor (lfsr shr 1)) and 1
                lfsr = (lfsr shr 1) or (bit shl 14)
                noiseValue = if (lfsr and 1 == 1) 1f else -1f
                noiseHold = period
            }
            noiseHold--
            buf[i] += noiseValue * gain
        }
    }

    private fun render(seconds: Float, voice: (Float, Out) -> Unit): ShortArray {
        val out = Out(FloatArray((seconds * rate).toInt()))
        for (n in out.buf.indices) {
            out.begin(n)
            voice(n.toFloat() / rate, out)
        }
        return pcm(out.buf)
    }

    private fun arpeggio(notes: List<Int>, step: Float, duty: Float, gain: Float): ShortArray =
        render(step * notes.size + 0.08f) { t, out ->
            val k = (t / step).toInt().coerceAtMost(notes.lastIndex)
            val local = t - k * step
            val tail = if (k == notes.lastIndex) decay(local, 14f) else 1f
            out.pulse(t, midi(notes[k]), duty, gain * tail)
        }

    /** A pulse lead over a triangle bass, both staccato, in steps of [step] seconds. */
    private fun melody(lead: List<N>, bass: List<N>, step: Float, duty: Float, gain: Float): ShortArray {
        val leadEnd = lead.sumOf { it.steps }
        val bassEnd = bass.sumOf { it.steps }
        val seconds = maxOf(leadEnd, bassEnd) * step + 0.15f
        fun at(line: List<N>, t: Float): Pair<N, Float>? {
            var start = 0f
            for (n in line) {
                val len = n.steps * step
                if (t < start + len) return n to (t - start)
                start += len
            }
            return null
        }
        return render(seconds) { t, out ->
            at(lead, t)?.let { (n, local) ->
                if (n.midi > 0) {
                    // A slight drop in level after the attack, and a gap before the next note.
                    val len = n.steps * step
                    val level = if (local > len - 0.012f) 0f else 0.75f + 0.25f * decay(local, 20f)
                    out.pulse(t, midi(n.midi), duty, gain * level)
                } else {
                    out.pulse(t, 1f, duty, 0f)
                }
            } ?: out.pulse(t, 1f, duty, 0f)
            at(bass, t)?.let { (n, local) ->
                val len = n.steps * step
                val level = if (local > len - 0.02f) 0f else 1f
                out.triangle(t, midi(n.midi), gain * 1.6f * level)
            }
        }
    }

    private fun mixAt(a: ShortArray, b: ShortArray, offset: Float): ShortArray {
        val start = (offset * rate).toInt()
        val out = ShortArray(maxOf(a.size, start + b.size))
        for (i in out.indices) {
            val va = if (i < a.size) a[i].toInt() else 0
            val vb = if (i - start in b.indices) b[i - start].toInt() else 0
            out[i] = (va + vb).coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun pcm(buf: FloatArray): ShortArray {
        // Tiny fades at both ends so nothing clicks.
        val fade = (rate * 0.003f).toInt().coerceAtMost(buf.size / 2)
        for (k in 0 until fade) {
            val g = k.toFloat() / fade
            buf[k] *= g
            buf[buf.size - 1 - k] *= g
        }
        return ShortArray(buf.size) { i ->
            val x = buf[i]
            val y = x / (1 + abs(x) * 0.5f)
            (y * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
