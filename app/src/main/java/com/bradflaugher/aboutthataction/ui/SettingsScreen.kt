package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.SeedMode
import com.bradflaugher.aboutthataction.Settings

@Composable
fun SettingsScreen(settings: Settings, insets: PaddingValues, onChange: (Settings) -> Unit, onBack: () -> Unit) {
    val s = settings
    Box(Modifier.fillMaxSize().veil(alpha = 0.9f).padding(insets)) {
        Column(Modifier.fillMaxSize()) {
            // Header bar stays put while the groups scroll under it.
            MenuHeader("LOADOUT", "SETTINGS", Neon.cyan, onBack)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.m, vertical = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val groupMod = Modifier.fillMaxWidth().widthIn(max = 560.dp)
                // (The difficulty curve lives on its own CUSTOM screen, off the title.)
                Panel(groupMod.reveal(40, 12.dp, Motion.base + 80), accent = Neon.cyan) { SeedGroup(s, onChange) }
                Panel(groupMod.reveal(90, 12.dp, Motion.base + 80), accent = Neon.cyan) {
                    AudioAndControls(s, onChange, audioIndex = "02", controlsIndex = "03")
                }
                Panel(groupMod.reveal(140, 12.dp, Motion.base + 80), accent = Neon.lava) {
                    SectionHeader("04", "HOW TO PLAY", Neon.lava)
                    HowToPlay()
                }
                NeonButton("DONE", Neon.cyan, groupMod, onClick = onBack)
                PrivacyLink()
                Spacer(Modifier.height(Space.xs))
            }
        }
    }
}

/** The Play privacy policy, opened in the browser (the game itself has no network access). */
@Composable
private fun PrivacyLink() {
    val uri = LocalUriHandler.current
    NeonText(
        "PRIVACY POLICY",
        size = Type.micro,
        color = Neon.dim,
        glow = 0f,
        letterSpacing = 2.sp,
        modifier = Modifier
            .clickable(role = Role.Button) { uri.openUri(PRIVACY_POLICY_URL) }
            .padding(Space.s),
    )
}

const val PRIVACY_POLICY_URL = "https://bradflaugher.com/privacy/bout-that-action/"

@Composable
private fun SeedGroup(s: Settings, onChange: (Settings) -> Unit) {
    SectionHeader("01", "SEED", Neon.cyan)
    Segmented(SeedMode.entries.toList(), s.seedMode, label = { it.label }) { onChange(s.copy(seedMode = it)) }
    AnimatedVisibility(
        s.seedMode == SeedMode.CUSTOM,
        enter = expandVertically(tween(Motion.base, easing = Motion.out)) + fadeIn(tween(Motion.base)),
        exit = shrinkVertically(tween(Motion.base, easing = Motion.out)) + fadeOut(tween(Motion.fast)),
    ) {
        SeedField(s.seedText) { onChange(s.copy(seedText = it.take(24))) }
    }
    NeonText(
        when (s.seedMode) {
            SeedMode.RANDOM -> "A fresh building every run."
            SeedMode.DAILY -> "Everyone gets the same building today (UTC)."
            SeedMode.CUSTOM -> "Same seed + same difficulty = same building. Share it."
        },
        size = Type.small, color = Neon.dim, glow = 0f,
    )
}

@Composable
private fun SeedField(text: String, onText: (String) -> Unit) {
    val focus = LocalFocusManager.current
    BasicTextField(
        value = text,
        onValueChange = onText,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontFamily = Neon.mono, letterSpacing = 2.sp),
        cursorBrush = SolidColor(Neon.cyan),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(top = Space.xxs),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .drawBehind {
                        val o = Shapes.small.createOutline(size, layoutDirection, this)
                        drawOutline(o, Neon.ink.copy(alpha = 0.9f))
                        drawOutline(o, Neon.cyan.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
                    }
                    .padding(horizontal = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeonText("#", size = 20.sp, color = Neon.cyan, modifier = Modifier.padding(end = Space.s))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) NeonText("ANY WORD OR NUMBER", size = Type.body, color = Neon.faint, glow = 0f, letterSpacing = 2.sp)
                    inner()
                }
                NeonText("${text.length}/24", size = Type.micro, color = Neon.dim, glow = 0f)
            }
        },
    )
}

@Composable
fun HowToPlay() {
    // Same order as the rooftop billboard: the gestures, then the two buttons, then the tricks.
    val rows = listOf(
        "DRAG ← →" to "Run. Hold to keep going; nudge back to turn. Lift to stop.",
        "SWIPE ↑" to "Jump. Clears low shots. Land on a head to daze him (BULL flattens him; drones break). MONKEY just hops off.",
        "SWIPE ↓" to "Hide in a doorway or pop the cardboard box. Swipe ↓ again to stand up. Hide before they see you: a guard who watches you go comes and finds you.",
        "TAP" to "Use what you're next to: a green passage door, a gold STASH door (locked while anyone's hunting you), an elevator (tap a landing to call the car).",
        "MODE" to "The button under pause. GUNS HOT auto-fires at threats; SILENT never fires (MONKEY still shoots back) and quiet kills score double. Flip it any time; it sticks between runs.",
        "GRENADE" to "The lime button under the mode button throws one, in either mode. It shows how many you have.",
        "WALK INTO" to "An enemy's back to take him down instantly. Face to face only if he's napping, dazed or walks into your box (FOX, and BULL with STIFF ARM, anytime). Heavies only from behind (or napping). MONKEY has no takedowns: his gun does it.",
        "THE BOX" to "Move it while a guard's looking and he comes over to check. Let him. (Heavies and ninjas kick it.)",
        "JUMP + TAP" to "Swat out the lamp overhead. The crash lures guards over to look; anyone right under it is out.",
        "ARRIVING" to "In SILENT you step into each new hallway hidden in the doorway. Tap or swipe ↑ to step out.",
        "GHOST" to "Leave a floor without anyone spotting you for a bonus. Double in SILENT.",
        "HEROES" to "Four of them, all free. Tap the hero bar on the title to swap. Each has a trait and three perks of their own.",
        "DOWN" to "Only elevators go down, and only some hallways have one. Find it. Keep going, or take a break. It'll wait.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        for ((k, v) in rows) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .width(104.dp)
                        .drawBehind {
                            val o = Shapes.chip.createOutline(size, layoutDirection, this)
                            drawOutline(o, Neon.cyan.copy(alpha = 0.1f))
                            drawOutline(o, Neon.cyan.copy(alpha = 0.45f), style = Stroke(1.dp.toPx()))
                        }
                        .padding(horizontal = Space.xs, vertical = Space.xxs),
                    contentAlignment = Alignment.Center,
                ) {
                    NeonText(k, size = Type.micro, color = Neon.cyan, letterSpacing = 1.sp, glow = 0.3f, maxLines = 1)
                }
                NeonText(v, size = Type.small, color = Neon.soft, glow = 0f, modifier = Modifier.padding(start = Space.s).weight(1f))
            }
        }
    }
}
