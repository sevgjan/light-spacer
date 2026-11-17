package io.github.sevgjan.spacer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

class HelpScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<Unit>(sealedActivity) {
    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        LightTheme(colors = themeColors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("How to play"),
                )
                // No end padding: the scroll view already keeps a 2-unit gutter on the right for its bar.
                LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 2f.gridUnitsAsDp())) {
                    Section("Fly") {
                        Paragraph("Slide a finger anywhere on the screen and the ship follows the slide, so your finger never covers it. Hold FIRE to keep shooting.")
                        Paragraph("On the phone you can also steer up and down with the volume keys, and fire with the camera button.")
                    }
                    Section("Special weapons") {
                        Paragraph("The small button fires your special weapon. You start with three seekers.")
                        Item(Art.HUD_SEEKER, "Seeker", "A missile that finds the nearest enemy.")
                        Item(Art.HUD_LANCE, "Lance", "A beam along your line that cuts through everything except rock.")
                        Item(Art.HUD_NOVA, "Nova", "A shockwave around the ship that clears enemy fire.")
                    }
                    Section("Pickups") {
                        Paragraph("Shoot down the carriers with the blinking light to get these.")
                        Item(Art.PICK_POWER, "Power", "Two guns, then three. You lose one level when you're hit.")
                        Item(Art.PICK_LIFE, "Extra ship", "Up to five.")
                        Item(Art.PICK_SEEKER, "Weapon", "Three charges of a special. More of the same adds three.")
                    }
                    Section("The run") {
                        Paragraph("Five stages, each ending with a guardian. Lose every ship and you can try the stage again from its start, with the score you had then.")
                        Paragraph("The game pauses itself when you leave it.")
                    }
                    Spacer(Modifier.height(2f.gridUnitsAsDp()))
                }
            }
        }
    }

    @Composable
    private fun Section(title: String, content: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(top = 1.5f.gridUnitsAsDp())) {
            LightText(text = title, variant = LightTextVariant.Subheading)
            Spacer(Modifier.height(0.5f.gridUnitsAsDp()))
            content()
        }
    }

    @Composable
    private fun Paragraph(text: String) {
        LightText(text = text, variant = LightTextVariant.Paragraph, modifier = Modifier.padding(bottom = 0.6f.gridUnitsAsDp()))
    }

    @Composable
    private fun Item(icon: Sprite, name: String, text: String) {
        Row(Modifier.fillMaxWidth().padding(vertical = 0.4f.gridUnitsAsDp()), verticalAlignment = Alignment.Top) {
            Box(Modifier.width(2f.gridUnitsAsDp()).padding(top = 4.dp), contentAlignment = Alignment.CenterStart) {
                PixelIcon(icon, pixel = if (icon.w > 5) 3.dp else 4.dp, color = LightThemeTokens.colors.content)
            }
            Column(Modifier.weight(1f)) {
                LightText(text = name, variant = LightTextVariant.Paragraph)
                LightText(text = text, variant = LightTextVariant.Paragraph, lighten = true)
            }
        }
    }
}
