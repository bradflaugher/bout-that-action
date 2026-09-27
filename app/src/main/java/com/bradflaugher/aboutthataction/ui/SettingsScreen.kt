package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.SeedMode
import com.bradflaugher.aboutthataction.Settings
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Zone
import java.util.Locale

@Composable
fun SettingsScreen(settings: Settings, insets: PaddingValues, onChange: (Settings) -> Unit, onBack: () -> Unit) {
    val s = settings
    Box(Modifier.fillMaxSize().veil(alpha = 0.9f).padding(insets)) {
        Column(Modifier.fillMaxSize()) {
            // Header bar stays put while the groups scroll under it.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton("Back", Neon.cyan, onBack) { chevronLeft(it) }
                Column(Modifier.padding(start = Space.m)) {
                    Kicker("LOADOUT", Neon.cyan.copy(alpha = 0.8f))
                    NeonText("SETTINGS", size = 28.sp, color = Color.White, title = true, letterSpacing = 3.sp, glow = 0.35f)
                }
            }
            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .drawBehind { drawRect(Brush.horizontalGradient(listOf(Neon.cyan.copy(alpha = 0.6f), Neon.magenta.copy(alpha = 0.3f), Color.Transparent))) },
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.m, vertical = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val groupMod = Modifier.fillMaxWidth().widthIn(max = 560.dp)
                Panel(groupMod.reveal(40, 12.dp, Motion.base + 80), accent = Neon.magenta) { DifficultyGroup(s, onChange) }
                Panel(groupMod.reveal(90, 12.dp, Motion.base + 80), accent = Neon.cyan) { SeedGroup(s, onChange) }
                Panel(groupMod.reveal(140, 12.dp, Motion.base + 80), accent = Neon.cyan) {
                    AudioAndControls(s, onChange, audioIndex = "03", controlsIndex = "04")
                }
                Panel(groupMod.reveal(190, 12.dp, Motion.base + 80), accent = Neon.lava) {
                    SectionHeader("05", "HOW TO PLAY", Neon.lava)
                    HowToPlay()
                }
                NeonButton("DONE", Neon.cyan, groupMod, onClick = onBack)
                Spacer(Modifier.height(Space.xs))
            }
        }
    }
}

@Composable
private fun DifficultyGroup(s: Settings, onChange: (Settings) -> Unit) {
    SectionHeader("01", "DIFFICULTY", Neon.magenta)
    Segmented(
        Difficulty.Preset.entries.toList<Difficulty.Preset?>() + null,
        s.preset,
        label = ::presetLabel,
        color = Neon.magenta,
    ) { onChange(s.copy(preset = it, custom = it?.difficulty ?: s.custom)) }
    val d = s.difficulty
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NeonText(s.preset?.blurb ?: "Shape your own curve", size = Type.small, color = Neon.soft, glow = 0f, modifier = Modifier.weight(1f))
        NeonText("♥ ${d.hearts}", size = Type.small, color = Neon.magenta, glow = 0.5f)
    }
    AnimatedVisibility(
        s.preset == null,
        enter = expandVertically(tween(Motion.base, easing = Motion.out)) + fadeIn(tween(Motion.base)),
        exit = shrinkVertically(tween(Motion.base, easing = Motion.out)) + fadeOut(tween(Motion.fast)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            val c = s.custom
            fun set(v: Difficulty) = onChange(s.copy(custom = v))
            HeatChart(c, Modifier.padding(vertical = Space.xs))
            Stepper("Starting heat", String.format(Locale.US, "%.1f", c.start), Neon.magenta,
                { set(c.copy(start = (c.start - 0.1f).coerceAtLeast(0f))) }, { set(c.copy(start = (c.start + 0.1f).coerceAtMost(5f))) })
            Stepper("Ramp", String.format(Locale.US, "×%.1f", c.ramp), Neon.magenta,
                { set(c.copy(ramp = (c.ramp - 0.1f).coerceAtLeast(0f))) }, { set(c.copy(ramp = (c.ramp + 0.1f).coerceAtMost(4f))) })
            Stepper("Heat cap", String.format(Locale.US, "%.1f", c.cap), Neon.magenta,
                { set(c.copy(cap = (c.cap - 0.5f).coerceAtLeast(0.5f))) }, { set(c.copy(cap = (c.cap + 0.5f).coerceAtMost(8f))) })
            Stepper("Hearts", "♥ ${c.hearts}", Neon.magenta,
                { set(c.copy(hearts = (c.hearts - 1).coerceAtLeast(1))) }, { set(c.copy(hearts = (c.hearts + 1).coerceAtMost(9))) })
            val zones = Zone.entries.filter { it != Zone.ROOFTOP }
            val zi = zones.indexOfLast { c.startFloor >= it.startFloor }
            Stepper("Start at", if (c.startFloor == 0) "ROOF" else zones[zi].title.substringAfterLast(' '), Neon.magenta,
                { set(c.copy(startFloor = if (zi <= 0) 0 else zones[zi - 1].startFloor)) },
                { set(c.copy(startFloor = zones[(zi + 1).coerceAtMost(zones.lastIndex)].startFloor)) })
        }
    }
}

@Composable
private fun SeedGroup(s: Settings, onChange: (Settings) -> Unit) {
    SectionHeader("02", "SEED", Neon.cyan)
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
    val rows = listOf(
        "DRAG ← →" to "Run. Hold to keep going; nudge back to turn. Lift to stop.",
        "TAP" to "Use what you're next to: a green passage door, a gold STASH door, an elevator (tap a landing to call the car).",
        "GRENADE" to "The lime button under the mode button throws one. It shows how many you have.",
        "SWIPE ↑" to "Jump. Clears low shots. Land on heads to stomp.",
        "SWIPE ↓" to "Hide in a doorway or pop the cardboard box. Swipe ↓ again to stand up.",
        "WALK INTO" to "An enemy to choke him out instantly. Heavies only from behind. Nappers from anywhere.",
        "THE BOX" to "Move it while a guard's looking and he comes over to check. Let him. (Heavies and ninjas kick it.)",
        "JUMP + TAP" to "Swat out the lamp overhead. The crash lures guards over to look; anyone right under it is out.",
        "ARRIVING" to "In SILENT you step into each new hallway hidden in the doorway. Tap or swipe ↑ to step out.",
        "MODE" to "The button under pause: GUNS HOT auto-fires at threats; SILENT never fires and quiet kills score double.",
        "GHOST" to "Leave a floor without anyone spotting you for a bonus. Double in SILENT.",
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
