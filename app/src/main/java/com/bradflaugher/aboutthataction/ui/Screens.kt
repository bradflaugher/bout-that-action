package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.Records
import com.bradflaugher.aboutthataction.SeedMode
import com.bradflaugher.aboutthataction.Settings
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Zone
import java.util.Locale
import kotlin.math.roundToInt

/** A finished run, for the game-over screen. */
data class RunSummary(
    val floor: Int,
    val zone: Zone,
    val score: Long,
    val kills: Int,
    val takedowns: Int,
    val seconds: Float,
    val seedLabel: String,
    val newBestScore: Boolean,
    val newBestFloor: Boolean,
)

private val scrim = Brush.verticalGradient(
    0f to Color(0xCC07060F), 0.35f to Color(0x3307060F), 0.6f to Color(0x3307060F), 1f to Color(0xEE07060F),
)

@Composable
private fun Logo() {
    val t = rememberInfiniteTransition(label = "logo")
    val glow by t.animateFloat(0.75f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow")
    val glitch by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3100)), label = "glitch")
    val jitter = if (glitch > 0.94f) ((glitch * 1000).roundToInt() % 7 - 3).toFloat() else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NeonText("'BOUT THAT", size = 30.sp, color = Neon.cyan.copy(alpha = glow), title = true, letterSpacing = 6.sp,
            modifier = Modifier.graphicsLayer { translationX = jitter * 3f })
        NeonText("ACTION", size = 62.sp, color = Neon.magenta.copy(alpha = glow), title = true, letterSpacing = 4.sp,
            modifier = Modifier.graphicsLayer { translationX = -jitter * 4f })
        NeonText("▼  an endless descent  ▼", size = 14.sp, color = Neon.lava, letterSpacing = 3.sp)
    }
}

@Composable
fun TitleScreen(
    settings: Settings,
    records: Records,
    insets: PaddingValues,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onPreset: (Difficulty.Preset) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(scrim).padding(insets).padding(20.dp)) {
        Column(Modifier.align(Alignment.TopCenter).padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo()
            if (records.runs > 0) {
                Spacer(Modifier.height(14.dp))
                NeonText("DEEPEST B${records.bestFloor}  ·  BEST ${"%,d".format(records.bestScore)}", size = 14.sp, color = Neon.gold)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Segmented(
                Difficulty.Preset.entries.toList(),
                settings.preset,
                label = { if (it == Difficulty.Preset.STRAIGHT_TO_HELL) "HELL" else it.label },
                color = Neon.magenta,
                onSelect = onPreset,
            )
            NeonText(
                (settings.preset?.blurb ?: "Custom difficulty curve") + "  ·  seed " + when (settings.seedMode) {
                    SeedMode.RANDOM -> "random"
                    SeedMode.DAILY -> "daily"
                    SeedMode.CUSTOM -> settings.seedText.ifBlank { "random" }
                },
                size = 13.sp, color = Neon.dim, align = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            NeonButton("DROP IN", Neon.magenta, Modifier.fillMaxWidth(), filled = true, height = 72.dp, onClick = onPlay)
            NeonButton("SETTINGS", Neon.cyan, Modifier.fillMaxWidth(), onClick = onSettings)
        }
    }
}

@Composable
fun PauseScreen(
    settings: Settings,
    seedLabel: String,
    insets: PaddingValues,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onQuit: () -> Unit,
    onSettings: (Settings) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color(0xB307060F)).padding(insets).padding(20.dp), contentAlignment = Alignment.Center) {
        Panel(Modifier.fillMaxWidth()) {
            NeonText("PAUSED", size = 36.sp, color = Neon.cyan, title = true, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            NeonText("seed $seedLabel", size = 13.sp, color = Neon.dim, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            NeonButton("RESUME", Neon.magenta, Modifier.fillMaxWidth(), filled = true, onClick = onResume)
            AudioAndControls(settings, onSettings)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton("RESTART", Neon.cyan, Modifier.weight(1f), onClick = onRestart)
                NeonButton("QUIT", Neon.lava, Modifier.weight(1f), onClick = onQuit)
            }
        }
    }
}

@Composable
private fun AudioAndControls(s: Settings, onChange: (Settings) -> Unit) {
    fun pct(v: Float) = "${(v * 100).roundToInt()}%"
    fun step(v: Float, d: Float) = ((v + d) * 10).roundToInt().coerceIn(0, 10) / 10f
    Stepper("Music", pct(s.musicVolume), onMinus = { onChange(s.copy(musicVolume = step(s.musicVolume, -0.1f))) },
        onPlus = { onChange(s.copy(musicVolume = step(s.musicVolume, 0.1f))) })
    Stepper("Sound FX", pct(s.sfxVolume), onMinus = { onChange(s.copy(sfxVolume = step(s.sfxVolume, -0.1f))) },
        onPlus = { onChange(s.copy(sfxVolume = step(s.sfxVolume, 0.1f))) })
    Toggle("Auto-fire", "Shoot anything in sight; taps still work", s.autoFire) { onChange(s.copy(autoFire = it)) }
    Toggle("Haptics", null, s.haptics) { onChange(s.copy(haptics = it)) }
    Toggle("Thumb guide", "Show a ring under your running thumb", s.touchGuide) { onChange(s.copy(touchGuide = it)) }
}

@Composable
fun GameOverScreen(run: RunSummary, insets: PaddingValues, onRetry: () -> Unit, onNewRun: () -> Unit, onTitle: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xC0100308)).padding(insets).padding(20.dp), contentAlignment = Alignment.Center) {
        Panel(Modifier.fillMaxWidth(), accent = Neon.lava) {
            NeonText("MISSION FAILED", size = 32.sp, color = Neon.lava, title = true, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            NeonText("B${run.floor}", size = 72.sp, color = Neon.text, title = true, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            NeonText(run.zone.title, size = 18.sp, color = Neon.magenta, align = TextAlign.Center, modifier = Modifier.fillMaxWidth(), letterSpacing = 4.sp)
            if (run.newBestFloor || run.newBestScore) {
                NeonText(if (run.newBestFloor) "★ NEW DEEPEST ★" else "★ NEW HIGH SCORE ★", size = 16.sp, color = Neon.gold,
                    align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Stat("SCORE", "%,d".format(run.score))
            Stat("KILLS", run.kills.toString())
            Stat("TAKEDOWNS", run.takedowns.toString())
            Stat("TIME", String.format(Locale.US, "%d:%02d", run.seconds.toInt() / 60, run.seconds.toInt() % 60))
            Stat("SEED", run.seedLabel)
            NeonButton("RETRY SEED", Neon.magenta, Modifier.fillMaxWidth(), filled = true, onClick = onRetry)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton("NEW RUN", Neon.cyan, Modifier.weight(1f), onClick = onNewRun)
                NeonButton("TITLE", Neon.dim, Modifier.weight(1f), onClick = onTitle)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        NeonText(label, size = 15.sp, color = Neon.dim, modifier = Modifier.weight(1f), letterSpacing = 2.sp)
        NeonText(value, size = 17.sp, color = Neon.text)
    }
}

@Composable
fun SettingsScreen(settings: Settings, insets: PaddingValues, onChange: (Settings) -> Unit, onBack: () -> Unit) {
    val s = settings
    Box(Modifier.fillMaxSize().background(Color(0xE607060F)).padding(insets)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NeonText("SETTINGS", size = 34.sp, color = Neon.cyan, title = true)

            SectionLabel("DIFFICULTY")
            Segmented(
                Difficulty.Preset.entries.toList<Difficulty.Preset?>() + null,
                s.preset,
                label = { if (it == null) "CUSTOM" else if (it == Difficulty.Preset.STRAIGHT_TO_HELL) "HELL" else it.label },
                color = Neon.magenta,
            ) { onChange(s.copy(preset = it, custom = it?.difficulty ?: s.custom)) }
            NeonText(s.preset?.blurb ?: "Shape your own curve:", size = 13.sp, color = Neon.dim)
            if (s.preset == null) {
                val c = s.custom
                fun set(d: Difficulty) = onChange(s.copy(custom = d))
                Stepper("Starting heat", "%.1f".format(c.start), Neon.magenta,
                    { set(c.copy(start = (c.start - 0.1f).coerceAtLeast(0f))) }, { set(c.copy(start = (c.start + 0.1f).coerceAtMost(5f))) })
                Stepper("Ramp", "×%.1f".format(c.ramp), Neon.magenta,
                    { set(c.copy(ramp = (c.ramp - 0.1f).coerceAtLeast(0f))) }, { set(c.copy(ramp = (c.ramp + 0.1f).coerceAtMost(4f))) })
                Stepper("Heat cap", "%.1f".format(c.cap), Neon.magenta,
                    { set(c.copy(cap = (c.cap - 0.5f).coerceAtLeast(0.5f))) }, { set(c.copy(cap = (c.cap + 0.5f).coerceAtMost(8f))) })
                Stepper("Hearts", "♥ ${c.hearts}", Neon.magenta,
                    { set(c.copy(hearts = (c.hearts - 1).coerceAtLeast(1))) }, { set(c.copy(hearts = (c.hearts + 1).coerceAtMost(9))) })
                val zones = Zone.entries.filter { it != Zone.ROOFTOP }
                val zi = zones.indexOfLast { c.startFloor >= it.startFloor }
                Stepper("Start at", if (c.startFloor == 0) "ROOF" else zones[zi].title.substringBefore(' '), Neon.magenta,
                    { set(c.copy(startFloor = if (zi <= 0) 0 else zones[zi - 1].startFloor)) },
                    { set(c.copy(startFloor = zones[(zi + 1).coerceAtMost(zones.lastIndex)].startFloor)) })
                HeatCurve(c)
            }

            SectionLabel("SEED")
            Segmented(SeedMode.entries.toList(), s.seedMode, label = { it.label }) { onChange(s.copy(seedMode = it)) }
            if (s.seedMode == SeedMode.CUSTOM) {
                BasicTextField(
                    value = s.seedText,
                    onValueChange = { onChange(s.copy(seedText = it.take(24))) },
                    singleLine = true,
                    textStyle = TextStyle(color = Neon.text, fontSize = 20.sp, fontFamily = Neon.mono),
                    cursorBrush = SolidColor(Neon.cyan),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().border(1.5.dp, Neon.cyan, RoundedCornerShape(10.dp)).padding(14.dp),
                    decorationBox = { inner ->
                        if (s.seedText.isEmpty()) NeonText("type any word or number", size = 18.sp, color = Neon.dim)
                        inner()
                    },
                )
            }
            NeonText(
                when (s.seedMode) {
                    SeedMode.RANDOM -> "A fresh building every run."
                    SeedMode.DAILY -> "Everyone gets the same building today (UTC)."
                    SeedMode.CUSTOM -> "Same seed + same difficulty = same building. Share it."
                },
                size = 13.sp, color = Neon.dim,
            )

            SectionLabel("AUDIO & CONTROLS")
            AudioAndControls(s, onChange)

            SectionLabel("HOW TO PLAY")
            HowToPlay()
            Spacer(Modifier.height(4.dp))
            NeonButton("BACK", Neon.cyan, Modifier.fillMaxWidth(), onClick = onBack)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** A little bar chart of heat by zone for a custom curve. */
@Composable
private fun HeatCurve(d: Difficulty) {
    val zones = Zone.entries.filter { it != Zone.ROOFTOP && it != Zone.VOID }
    Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        for (z in zones) {
            val heat = d.heat(z.startFloor + 5, z)
            val frac = (heat / 7f).coerceIn(0.04f, 1f)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().height((50 * frac).dp)
                        .background(Brush.verticalGradient(listOf(Neon.lava, Neon.magenta)), RoundedCornerShape(4.dp)),
                )
                NeonText(z.title.take(4), size = 9.sp, color = Neon.dim)
            }
        }
    }
}

@Composable
fun HowToPlay() {
    val rows = listOf(
        "DRAG ← →" to "Run. Hold to keep going; nudge back to turn. Lift to stop.",
        "TAP" to "Shoot. Auto-aims at the nearest threat, high or low.",
        "DOUBLE-TAP" to "Throw a grenade.",
        "SWIPE ↑" to "Jump. Clears low shots. Land on heads to stomp.",
        "SWIPE ↓" to "Hide: doorway, cardboard box, or ride an open elevator down. At a red INTEL door: pick a perk.",
        "WALK INTO" to "An enemy to choke him out instantly. Heavies only from behind.",
        "JUMP + TAP" to "Shoot out a ceiling light: it crushes whoever is below and darkens the floor.",
        "STAIRS" to "Run off the open end of a floor to go down. Keep going. Forever.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((k, v) in rows) {
            Row {
                NeonText(k, size = 13.sp, color = Neon.cyan, modifier = Modifier.weight(0.34f))
                NeonText(v, size = 13.sp, color = Neon.text, modifier = Modifier.weight(0.66f))
            }
        }
    }
}
