package io.github.sevgjan.spacer

import android.view.KeyEvent
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightVibrationUsage
import com.thelightphone.sdk.LightVibrationWaveform
import com.thelightphone.sdk.LightVibrator
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.vibrationAmplitudesOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

enum class Mode { TITLE, PLAYING, PAUSED, OVER }

/** The special weapon shown on the special button. */
data class SpecialState(val special: Special?, val charges: Int)

/** How a run ended, for the menu under the game screen. */
data class Outcome(val victory: Boolean, val score: Int, val best: Int, val newBest: Boolean, val stage: Int)

class SpacerViewModel(
    private val store: ProgressStore,
    private val vibrator: LightVibrator,
    private val sound: SoundPlayer,
) : LightViewModel<Unit>() {
    val mode = MutableStateFlow(Mode.TITLE)
    val progress = MutableStateFlow(Progress())
    val specialState = MutableStateFlow(SpecialState(null, 0))
    val outcome = MutableStateFlow<Outcome?>(null)
    /** Whether the player has dragged yet this session; the hint goes away after. */
    val hasDragged = MutableStateFlow(false)

    /** Bumped whenever [screen] holds a new picture, so the display redraws. */
    val frame = mutableLongStateOf(0L)
    val screen = Screen()
    val controls = Controls()
    var hapticsEnabled = false

    private val title = Title()
    private var game: Game? = null
    private val clock = FrameClock()

    init {
        viewModelScope.launch {
            progress.value = store.load()
            if (progress.value.sound) prepareSound()
        }
    }

    private fun prepareSound() {
        viewModelScope.launch(Dispatchers.Default) { sound.prepare() }
    }

    private fun sfx(pick: (Sfx) -> ShortArray) {
        if (progress.value.sound) sound.play(pick)
    }

    fun toggleSound() {
        val on = !progress.value.sound
        saveProgress { it.copy(sound = on) }
        if (on) {
            viewModelScope.launch(Dispatchers.Default) {
                sound.prepare()
                sound.play { it.toggleOn }
            }
        } else {
            sound.stop()
        }
    }

    override fun onCleared() {
        sound.release()
        super.onCleared()
    }

    // ---- Frame loop ---------------------------------------------------------------------------

    fun resetClock() = clock.reset()

    /** Fixed game ticks, rounded to the nearest tick to absorb small display-clock jitter. */
    fun onFrame(nanos: Long) {
        val steps = clock.advance(nanos)
        repeat(steps) { tick() }
        if (steps > 0) redraw()
    }

    private fun tick() {
        when (mode.value) {
            Mode.TITLE -> title.step()
            Mode.PLAYING, Mode.OVER -> game?.let { g ->
                g.step(controls)
                handleEvents(g)
                val previous = specialState.value
                if (g.special != previous.special || g.charges != previous.charges) {
                    specialState.value = SpecialState(g.special, g.charges)
                }
            }
            Mode.PAUSED -> Unit
        }
    }

    private fun redraw() {
        val g = game
        if (mode.value == Mode.TITLE || g == null) {
            title.render(screen, progress.value.best)
        } else {
            g.render(screen)
            if (mode.value == Mode.PAUSED) drawPaused()
        }
        frame.longValue++
    }

    private fun drawPaused() {
        val text = "PAUSED"
        val w = Font5x7.width(text) + 8
        val x = (Screen.W - w) / 2
        val y = Screen.PLAY_Y + (Screen.PLAY_H - 13) / 2
        screen.fillRect(x, y, w, 13, on = false)
        screen.rect(x, y, w, 13)
        screen.textCentered(text, y + 3, Font5x7)
    }

    // ---- Game events --------------------------------------------------------------------------

    private fun handleEvents(g: Game) {
        var killed = false
        var bigKill = false
        var shot = false
        while (true) {
            when (val e = g.events.removeFirstOrNull() ?: break) {
                is GameEvent.StageStart -> {
                    saveProgress { it.copy(checkpointStage = e.stage, checkpointScore = e.score) }
                    sfx { it.stageStart }
                }
                is GameEvent.Kill -> { killed = true; bigKill = bigKill || e.big }
                GameEvent.Shot -> shot = true
                GameEvent.Pickup -> { buzz { vibrator.click() }; sfx { it.pickup } }
                is GameEvent.SpecialFired -> sfx {
                    when (e.special) {
                        Special.SEEKER -> it.seeker
                        Special.LANCE -> it.lance
                        Special.NOVA -> it.nova
                    }
                }
                GameEvent.Warning -> sfx { it.warning }
                GameEvent.Death -> { buzz { vibrator.vibrate(180.milliseconds) }; sfx { it.death } }
                GameEvent.BossDown -> { buzz { vibrator.vibrate(BOSS_DOWN, LightVibrationUsage.Touch) }; sfx { it.bossDown } }
                GameEvent.StageClear -> sfx { it.stageClear }
                GameEvent.GameOver -> { finish(g, victory = false); sfx { it.gameOver } }
                GameEvent.Victory -> { finish(g, victory = true); sfx { it.victory } }
            }
        }
        // However many things happen in one tick, each kind of sound plays once.
        if (shot && progress.value.sound) sound.shot()
        if (killed) sfx { if (bigKill) it.killBig else it.kill }
    }

    private fun finish(g: Game, victory: Boolean) {
        val previous = progress.value
        val best = maxOf(previous.best, g.score)
        outcome.value = Outcome(victory, g.score, best, g.score > previous.best, g.stageIndex)
        controls.releaseAll()
        mode.value = Mode.OVER
        saveProgress {
            if (victory) it.copy(best = best, checkpointStage = null, checkpointScore = 0, completed = true)
            else it.copy(best = best)
        }
    }

    /** Vibration is a call into the system, so it runs off the game thread like the sound. */
    private fun buzz(block: () -> Unit) {
        if (hapticsEnabled) viewModelScope.launch(Dispatchers.Default) { block() }
    }

    private fun saveProgress(change: (Progress) -> Progress) {
        val next = change(progress.value)
        progress.value = next
        viewModelScope.launch { store.save(next) }
    }

    // ---- Menu actions -------------------------------------------------------------------------

    fun newGame() = start(Game(0, 0, seed = System.nanoTime()))

    /** Restarts from the beginning of the last stage reached, with the score held then. */
    fun continueGame() {
        val p = progress.value
        val stage = p.checkpointStage ?: return newGame()
        start(Game(stage, p.checkpointScore, seed = System.nanoTime()))
    }

    private fun start(g: Game) {
        game = g
        outcome.value = null
        controls.releaseAll()
        handleEvents(g)
        specialState.value = SpecialState(g.special, g.charges)
        resetClock()
        mode.value = Mode.PLAYING
    }

    fun pause() {
        if (mode.value != Mode.PLAYING) return
        controls.releaseAll()
        sound.stop()
        mode.value = Mode.PAUSED
        game?.let { g -> saveProgress { it.copy(best = maxOf(it.best, g.score)) } }
        redraw()
    }

    fun resume() {
        if (mode.value != Mode.PAUSED) return
        controls.releaseAll()
        resetClock()
        mode.value = Mode.PLAYING
    }

    fun quitToTitle() {
        game?.let { g -> saveProgress { it.copy(best = maxOf(it.best, g.score)) } }
        game = null
        outcome.value = null
        controls.releaseAll()
        resetClock()
        mode.value = Mode.TITLE
    }

    // ---- Touch --------------------------------------------------------------------------------

    fun drag(dx: Float, dy: Float) {
        if (mode.value != Mode.PLAYING) return
        controls.dragX += dx
        controls.dragY += dy
        if (!hasDragged.value && (dx != 0f || dy != 0f)) hasDragged.value = true
    }

    fun setFire(down: Boolean) {
        controls.touchFire = down && mode.value == Mode.PLAYING
    }

    fun special() {
        if (mode.value != Mode.PLAYING) return
        controls.specialPresses++
        buzz { vibrator.click() }
    }

    // ---- Lifecycle and keys -------------------------------------------------------------------

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) = pause()

    override fun onAppPause() = pause()

    override fun onBackPressed(): Boolean = when (mode.value) {
        Mode.PLAYING -> { pause(); true }
        Mode.PAUSED -> { resume(); true }
        Mode.OVER -> { quitToTitle(); true }
        Mode.TITLE -> false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = key(keyCode, down = true)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = key(keyCode, down = false)

    /**
     * Hardware controls while playing: the volume keys steer up and down, the
     * camera button fires (either stage of it). Arrow keys, space and X do the same on an emulator.
     * Keys are left alone outside a game, so volume and camera behave as usual there.
     */
    private fun key(keyCode: Int, down: Boolean): Boolean {
        val m = mode.value
        if (m != Mode.PLAYING) {
            // Don't let the camera button jump out of a paused game.
            return m == Mode.PAUSED && keyCode in CAMERA_KEYS
        }
        val c = controls
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_UP -> c.keyUp = down
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> c.keyDown = down
            KeyEvent.KEYCODE_DPAD_LEFT -> c.keyLeft = down
            KeyEvent.KEYCODE_DPAD_RIGHT -> c.keyRight = down
            in CAMERA_KEYS, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER -> c.keyFire = down
            KeyEvent.KEYCODE_X -> if (down) c.specialPresses++
            else -> return false
        }
        return true
    }

    private companion object {
        val CAMERA_KEYS = setOf(KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_FOCUS)
        val BOSS_DOWN = LightVibrationWaveform(
            durations = listOf(60, 40, 60, 40, 220).map { it.milliseconds },
            amplitudes = vibrationAmplitudesOf(255, 0, 200, 0, 140),
        )
    }
}
