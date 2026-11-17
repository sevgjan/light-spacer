package io.github.sevgjan.spacer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.thelightphone.sdk.audio.LightAudio
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Plays [Sfx] through one long-lived audio stream, mixed in software on its own thread.
 *
 * The game thread only adds a sound to a queue, which takes microseconds and never calls into
 * the system's audio service. Starting an AudioTrack per sound did, several times a second, and
 * any slow call stalled the game. The stream opens once, stays open while sounds keep coming,
 * and pauses itself after a short silence.
 *
 * The sounds use the sonification usage. On the Light Phone that stream follows the ringer volume
 * and goes quiet in silent mode. Like a key click, it takes no audio focus, so music keeps
 * playing underneath.
 */
class SoundPlayer(audio: LightAudio) {
    private val rate = audio.capabilities.sampleRate.takeIf { it > 0 } ?: 44_100

    @Volatile
    var sfx: Sfx? = null
        private set

    private val lock = ReentrantLock()
    private val wake = lock.newCondition()

    // Guarded by [lock]: what the game asked for since the mixer last looked.
    private val queued = ArrayList<ShortArray>()
    private var queuedShot: ShortArray? = null
    private var silenceRequested = false
    private var released = false
    private var thread: Thread? = null

    /** Builds the sounds; slow enough (a fraction of a second) that callers run it in the background. */
    fun prepare() {
        if (sfx == null) sfx = Sfx(rate)
    }

    fun play(pick: (Sfx) -> ShortArray) {
        val s = sfx ?: return
        val pcm = pick(s)
        if (pcm.isEmpty()) return
        submit { queued += pcm }
    }

    /** The gun has its own channel, so each shot restarts the last one and never cuts off an explosion. */
    fun shot() {
        val s = sfx ?: return
        submit { queuedShot = s.shot }
    }

    /** Silences everything now (pause, sound switched off). */
    fun stop() = lock.withLock {
        queued.clear()
        queuedShot = null
        silenceRequested = true
        wake.signal()
    }

    fun release() = lock.withLock {
        released = true
        wake.signal()
    }

    private inline fun submit(add: () -> Unit) = lock.withLock {
        if (released) return
        add()
        if (thread == null) {
            thread = Thread(::mixLoop, "spacer-audio").apply { isDaemon = true; start() }
        }
        wake.signal()
    }

    // ---- Mixer thread -------------------------------------------------------------------------

    private class Voice(val pcm: ShortArray) { var pos = 0 }

    private fun mixLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val track = openTrack() ?: return lock.withLock { thread = null }
        val voices = ArrayList<Voice>(MAX_VOICES)
        var shotVoice: Voice? = null
        val mix = IntArray(CHUNK)
        val out = ShortArray(CHUNK)
        var playing = false
        var silentChunks = 0
        try {
            while (true) {
                // Take new requests; sleep while there's nothing to play.
                lock.withLock {
                    while (true) {
                        if (released) return
                        if (silenceRequested) {
                            silenceRequested = false
                            voices.clear(); shotVoice = null
                            if (playing) { track.pause(); track.flush(); playing = false }
                        }
                        for (pcm in queued) {
                            if (voices.size >= MAX_VOICES) voices.removeAt(0)
                            voices += Voice(pcm)
                        }
                        queued.clear()
                        queuedShot?.let { shotVoice = Voice(it) }
                        queuedShot = null
                        val busy = voices.isNotEmpty() || shotVoice != null
                        if (busy || (playing && silentChunks < IDLE_CHUNKS)) break
                        if (playing) { track.pause(); track.flush(); playing = false }
                        wake.await()
                    }
                }
                if (!playing) {
                    track.play()
                    playing = true
                    silentChunks = 0
                }

                // Mix one small chunk. Silence keeps flowing for a moment after the last sound,
                // so rapid fire doesn't pause and restart the stream between shots.
                mix.fill(0)
                var any = false
                shotVoice?.let { if (addInto(mix, it)) shotVoice = null; any = true }
                val iter = voices.iterator()
                while (iter.hasNext()) {
                    any = true
                    if (addInto(mix, iter.next())) iter.remove()
                }
                silentChunks = if (any) 0 else silentChunks + 1
                for (i in 0 until CHUNK) out[i] = mix[i].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                if (track.write(out, 0, CHUNK, AudioTrack.WRITE_BLOCKING) < 0) return
            }
        } finally {
            runCatching { track.stop() }
            track.release()
            lock.withLock { thread = null }
        }
    }

    /** Adds the next chunk of [v] into [mix]; returns true when the sound has finished. */
    private fun addInto(mix: IntArray, v: Voice): Boolean {
        val n = minOf(CHUNK, v.pcm.size - v.pos)
        for (i in 0 until n) mix[i] += v.pcm[v.pos + i]
        v.pos += n
        return v.pos >= v.pcm.size
    }

    private fun openTrack(): AudioTrack? {
        val format = AudioFormat.Builder()
            .setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(min, CHUNK * 2 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        }.getOrNull() ?: return null
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return null
        }
        return track
    }

    private companion object {
        /** Frames mixed at a time: about 5 ms at 48 kHz. */
        const val CHUNK = 256
        const val MAX_VOICES = 8
        /** About a second of silence before the stream pauses. */
        const val IDLE_CHUNKS = 190
    }
}
