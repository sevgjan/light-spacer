package io.github.sevgjan.spacer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.text.NumberFormat
import kotlin.math.floor

/** Finger travel to ship travel. 1 would move the ship exactly under the finger's path. */
private const val DRAG_GAIN = 1.5f

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, SpacerViewModel>(sealedActivity) {
    private val vibrator = sealedActivity.vibrator
    private val audio = DefaultLightAudio(sealedActivity)

    override val viewModelClass: Class<SpacerViewModel>
        get() = SpacerViewModel::class.java

    override fun createViewModel() = SpacerViewModel(
        ProgressStore(lightContext.dataStore),
        vibrator,
        SoundPlayer(audio),
    )

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val mode by viewModel.mode.collectAsState()
        viewModel.hapticsEnabled = LocalHapticsEnabled.current

        LaunchedEffect(mode) {
            // Nothing moves while paused, so the loop sleeps then.
            if (mode == Mode.PAUSED) return@LaunchedEffect
            viewModel.resetClock()
            while (true) withFrameNanos(viewModel::onFrame)
        }

        LightTheme(colors = themeColors) {
            val playing = mode == Mode.PLAYING
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .then(if (playing) Modifier.keepScreenOn().steering(viewModel) else Modifier),
            ) {
                Spacer(Modifier.height(0.5f.gridUnitsAsDp()))
                PixelDisplay(viewModel, Modifier.fillMaxWidth())
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (mode) {
                        Mode.TITLE -> TitleMenu()
                        Mode.PLAYING -> GamePad()
                        Mode.PAUSED -> PauseMenu()
                        Mode.OVER -> OverMenu()
                    }
                }
            }
        }
    }

    @Composable
    private fun TitleMenu() {
        val progress by viewModel.progress.collectAsState()
        MenuColumn {
            // Continuing from stage 1 would just be a new game.
            progress.checkpointStage?.takeIf { it > 0 }?.let { stage ->
                MenuItem("Continue · Stage ${stage + 1}", viewModel::continueGame)
            }
            MenuItem("New game", viewModel::newGame)
            MenuItem("How to play") { navigateTo({ HelpScreen(it) }) }
            MenuItem(if (progress.sound) "Sound on" else "Sound off", viewModel::toggleSound)
            Spacer(Modifier.weight(1f))
            // The best score is on the game screen above.
            if (progress.completed) {
                LightText(text = "Mission complete", variant = LightTextVariant.Detail, lighten = true)
            }
        }
    }

    @Composable
    private fun PauseMenu() {
        val progress by viewModel.progress.collectAsState()
        MenuColumn {
            MenuItem("Resume", viewModel::resume)
            MenuItem("Restart stage ${(progress.checkpointStage ?: 0) + 1}", viewModel::continueGame)
            MenuItem("Quit to title", viewModel::quitToTitle)
        }
    }

    @Composable
    private fun OverMenu() {
        val outcome by viewModel.outcome.collectAsState()
        val result = outcome ?: return
        MenuColumn {
            LightText(
                text = if (result.newBest) "New best · ${formatScore(result.score)}" else "Score ${formatScore(result.score)} · Best ${formatScore(result.best)}",
                variant = LightTextVariant.Subheading,
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )
            if (result.victory) {
                MenuItem("Play again", viewModel::newGame)
            } else {
                MenuItem("Try stage ${result.stage + 1} again", viewModel::continueGame)
            }
            MenuItem("Title", viewModel::quitToTitle)
        }
    }

    @Composable
    private fun GamePad() {
        val special by viewModel.specialState.collectAsState()
        val dragged by viewModel.hasDragged.collectAsState()
        val colors = LightThemeTokens.colors
        Box(Modifier.fillMaxSize()) {
            LightIcon(
                icon = LightIcons.PAUSE,
                size = 2f,
                contentDescription = "Pause",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 1f.gridUnitsAsDp(), top = 1f.gridUnitsAsDp())
                    .lightClickable(onClick = viewModel::pause),
            )
            if (!dragged) {
                LightText(
                    text = "Drag anywhere to fly",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 2f.gridUnitsAsDp()),
                )
            }
            val fireSize = 8f.gridUnitsAsDp()
            val specialSize = 5.5f.gridUnitsAsDp()
            HoldButton(
                size = fireSize,
                onPress = { viewModel.setFire(true) },
                onRelease = { viewModel.setFire(false) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.5f.gridUnitsAsDp(), bottom = 1.5f.gridUnitsAsDp()),
            ) { pressed ->
                LightText(text = "FIRE", variant = LightTextVariant.Button, color = if (pressed) colors.background else colors.content)
            }
            val ready = special.special != null && special.charges > 0
            HoldButton(
                size = specialSize,
                enabled = ready,
                onPress = viewModel::special,
                onRelease = {},
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.5f.gridUnitsAsDp() + fireSize + 0.5f.gridUnitsAsDp(), bottom = 1.5f.gridUnitsAsDp() + fireSize * 0.45f),
            ) { pressed ->
                val tint = if (pressed) colors.background else if (ready) colors.content else colors.contentSecondary
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixelIcon(special.special?.hud ?: Art.HUD_SEEKER, pixel = 5.dp, color = tint)
                    Spacer(Modifier.height(4.dp))
                    LightText(text = if (ready) "×${special.charges}" else "—", variant = LightTextVariant.Detail, color = tint)
                }
            }
        }
    }
}

/** Moves the ship by however far a finger slides, wherever it is on screen, except on the buttons. */
private fun Modifier.steering(viewModel: SpacerViewModel): Modifier = pointerInput(viewModel) {
    awaitPointerEventScope {
        var finger: PointerId? = null
        while (true) {
            val event = awaitPointerEvent()
            val gamePixel = size.width / Screen.W.toFloat()
            for (change in event.changes) {
                when {
                    // Buttons consume their own touches, so only free fingers steer.
                    finger == null && change.changedToDown() -> finger = change.id
                    change.id == finger && !change.pressed -> finger = null
                    change.id == finger -> {
                        val d = change.positionChange()
                        viewModel.drag(d.x / gamePixel * DRAG_GAIN, d.y / gamePixel * DRAG_GAIN)
                        change.consume()
                    }
                }
            }
        }
    }
}

/** The game's 1-bit screen, scaled up by a whole number so every pixel is crisp, with a faint LCD grid. */
@Composable
private fun PixelDisplay(viewModel: SpacerViewModel, modifier: Modifier = Modifier) {
    val colors = LightThemeTokens.colors
    val bitmap = remember { createBitmap(Screen.W, Screen.H) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val argb = remember { IntArray(Screen.W * Screen.H) }
    val lit = colors.content.toArgb()
    val unlit = lerp(colors.background, colors.content, 0.07f).toArgb()
    val dim = lerp(colors.background, colors.content, 0.3f).toArgb()
    val gap = colors.background
    Canvas(modifier.aspectRatio(Screen.W / Screen.H.toFloat()).drawWithCache {
        val scale = floor(size.width / Screen.W).toInt().coerceAtLeast(1)
        val w = scale * Screen.W
        val h = scale * Screen.H
        val ox = ((size.width - w) / 2).toInt()
        val oy = ((size.height - h) / 2).toInt()
        // Cache the unchanging grid geometry and submit it as one draw command.
        val grid = Path()
        if (scale >= 4) {
            for (x in 1 until Screen.W) {
                val left = (ox + x * scale - 1).toFloat()
                grid.addRect(Rect(left, oy.toFloat(), left + 1f, (oy + h).toFloat()))
            }
            for (y in 1 until Screen.H) {
                val top = (oy + y * scale - 1).toFloat()
                grid.addRect(Rect(ox.toFloat(), top, (ox + w).toFloat(), top + 1f))
            }
        }
        onDrawWithContent {
            drawContent()
            if (scale >= 4) drawPath(grid, gap)
        }
    }) {
        viewModel.frame.longValue // redraw on every new frame
        val pixels = viewModel.screen.pixels
        for (i in pixels.indices) {
            argb[i] = when (pixels[i]) {
                Screen.ON -> lit
                Screen.DIM -> dim
                else -> unlit
            }
        }
        bitmap.setPixels(argb, 0, Screen.W, 0, 0, Screen.W, Screen.H)
        val scale = floor(size.width / Screen.W).toInt().coerceAtLeast(1)
        val w = scale * Screen.W
        val h = scale * Screen.H
        val ox = ((size.width - w) / 2).toInt()
        val oy = ((size.height - h) / 2).toInt()
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(Screen.W, Screen.H),
            dstOffset = IntOffset(ox, oy),
            dstSize = IntSize(w, h),
            filterQuality = FilterQuality.None,
        )
    }
}

/** A sprite drawn at [pixel] size, for icons in the LightOS UI around the game. */
@Composable
fun PixelIcon(sprite: Sprite, pixel: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(pixel * sprite.w, pixel * sprite.h)) {
        val p = size.width / sprite.w
        for (y in 0 until sprite.h) for (x in 0 until sprite.w) {
            if (sprite[x, y]) drawRect(color, Offset(x * p, y * p), Size(p - p / 8f, p - p / 8f))
        }
    }
}

/** A round button that reports press and release, so FIRE can be held. Filled while pressed. */
@Composable
private fun HoldButton(
    size: Dp,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (pressed: Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val colors = LightThemeTokens.colors
    Box(
        modifier = modifier
            .size(size)
            .pointerInput(enabled) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent().changes.firstOrNull { it.changedToDown() } ?: continue
                        down.consume()
                        if (!enabled) continue
                        pressed = true
                        onPress()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                // Own only this finger; steering and a second button stay independent.
                                val held = event.changes.firstOrNull { it.id == down.id } ?: break
                                held.consume()
                                if (!held.pressed) break
                            }
                        } finally {
                            pressed = false
                            onRelease()
                        }
                    }
                }
            }
            .drawBehind {
                val stroke = 2.dp.toPx()
                if (pressed) {
                    drawCircle(colors.content)
                } else {
                    drawCircle(
                        if (enabled) colors.content else colors.contentSecondary,
                        radius = this.size.minDimension / 2 - stroke / 2,
                        style = Stroke(width = stroke),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content(pressed)
    }
}

@Composable
private fun MenuColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 2f.gridUnitsAsDp(), end = 2f.gridUnitsAsDp(), top = 0.5f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.Top,
    ) {
        content()
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    LightText(
        text = text,
        variant = LightTextVariant.Heading,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.15f.gridUnitsAsDp()),
    )
}

fun formatScore(score: Int): String = NumberFormat.getIntegerInstance().format(score)
