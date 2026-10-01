package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.Settings
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Zone
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * CUSTOM RUN: shape your own heat curve and drop straight in. Start from any preset's curve
 * (HELL included), tweak the five knobs while the chart morphs, pick who goes, DROP IN.
 */
@Composable
fun CustomScreen(
    settings: Settings,
    insets: PaddingValues,
    onChange: (Settings) -> Unit,
    onHeroes: () -> Unit,
    onPlay: () -> Unit,
    onBack: () -> Unit,
) {
    val c = settings.custom
    fun set(d: Difficulty) = onChange(settings.copy(preset = null, custom = d))
    val template = Difficulty.Preset.entries.firstOrNull { it.difficulty == c }
    Box(Modifier.fillMaxSize().veil(alpha = 0.9f).padding(insets)) {
        Column(Modifier.fillMaxSize()) {
            MenuHeader(
                "DIFFICULTY", "CUSTOM RUN", Neon.magenta, onBack,
                line = listOf(Neon.magenta.copy(alpha = 0.7f), Neon.lava.copy(alpha = 0.3f), Color.Transparent),
            ) { FeelsLike(c, Modifier.padding(start = Space.s)) }

            val groupMod = Modifier.fillMaxWidth().widthIn(max = 560.dp)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.m, vertical = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Panel(groupMod.reveal(40, 12.dp, Motion.base + 80), accent = Neon.magenta) {
                    SectionHeader("01", "START FROM", Neon.magenta)
                    Segmented(
                        Difficulty.Preset.entries.toList(), template, label = ::presetLabel, color = Neon.magenta,
                    ) { set(it.difficulty) }
                    NeonText(
                        template?.let { "${it.blurb}." } ?: "Your own curve. Tap one to start over from it.",
                        size = Type.small, color = if (template == null) Neon.hotPink else Neon.dim, glow = 0f,
                    )
                }
                Panel(groupMod.reveal(90, 12.dp, Motion.base + 80), accent = Neon.lava) {
                    SectionHeader("02", "HEAT CURVE", Neon.lava)
                    HeatChart(c, Modifier.padding(top = Space.xxs, bottom = Space.xs))
                    CurveSteppers(c, ::set)
                }
                Spacer(Modifier.height(Space.xxs))
            }

            // Sticky: who's going, and the way down.
            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .drawBehind { drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Neon.magenta.copy(alpha = 0.45f), Color.Transparent))) },
            )
            Column(
                Modifier.fillMaxWidth().drawBehind { drawRect(Neon.night.copy(alpha = 0.6f)) }
                    .padding(horizontal = Space.m).padding(top = Space.s, bottom = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeroBar(settings.hero, groupMod.reveal(140, 12.dp), onHeroes)
                NeonButton(
                    "DROP IN", Neon.magenta, groupMod.reveal(180, 12.dp),
                    style = ButtonStyle.PRIMARY, height = 72.dp, textSize = 26.sp,
                    trailing = { DropChevrons() },
                    onClick = onPlay,
                )
            }
        }
    }
}

/** The five knobs of the curve. */
@Composable
private fun CurveSteppers(c: Difficulty, set: (Difficulty) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
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

/** "FEELS LIKE / BRUTAL+": the curve's bite next to the presets', in one glance. */
@Composable
private fun FeelsLike(d: Difficulty, modifier: Modifier = Modifier) {
    val (label, color) = feelsLike(d)
    Column(modifier.widthIn(min = 84.dp, max = 128.dp), horizontalAlignment = Alignment.End) {
        FitText("FEELS LIKE", Type.micro, Neon.dim, title = false, letterSpacing = 3.sp, glow = 0f, alignment = Alignment.CenterEnd)
        AnimatedContent(
            label to color,
            transitionSpec = { fadeIn(tween(Motion.base)) togetherWith fadeOut(tween(Motion.fast)) },
            contentAlignment = Alignment.CenterEnd,
            label = "feels",
        ) { (l, col) ->
            FitText(l, Type.title, col, letterSpacing = 1.5.sp, glow = 0.7f, alignment = Alignment.CenterEnd)
        }
    }
}

/**
 * How hard a curve bites, as a rough number: the mean heat over the first 60 floors of the
 * run (zone bonuses included), scaled by how many hits you can take.
 */
internal fun bite(d: Difficulty): Float {
    var sum = 0f
    for (f in d.startFloor until d.startFloor + 60) sum += d.heat(f, Zone.baseZoneOf(f))
    return sum / 60f * sqrt(3f / d.hearts.coerceAtLeast(1))
}

/** The nearest preset to a curve's [bite], with a + when it's hotter still; and its color. */
internal fun feelsLike(d: Difficulty): Pair<String, Color> {
    val v = bite(d)
    val refs = Difficulty.Preset.entries.map { it to bite(it.difficulty) }.sortedBy { it.second }
    fun tint(p: Difficulty.Preset) = when (p) {
        Difficulty.Preset.CHILL -> Neon.cyan
        Difficulty.Preset.AGENT -> Neon.magenta
        Difficulty.Preset.BRUTAL -> Neon.lava
        Difficulty.Preset.STRAIGHT_TO_HELL -> Neon.blood
    }
    refs.firstOrNull { abs(v - it.second) <= it.second * 0.12f }?.let { return presetLabel(it.first) to tint(it.first) }
    val (lowest, low) = refs.first()
    if (v < low) return (if (v < low * 0.6f) "A STROLL" else presetLabel(lowest)) to tint(lowest)
    val (top, high) = refs.last()
    if (v > high) return (if (v > high * 1.3f) "UNHINGED" else presetLabel(top) + "+") to Neon.blood
    val below = refs.last { it.second < v }.first
    return presetLabel(below) + "+" to tint(below)
}
