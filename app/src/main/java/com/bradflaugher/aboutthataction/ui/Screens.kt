package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.Records
import com.bradflaugher.aboutthataction.Settings
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.FloorLabel
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.SeedCode
import com.bradflaugher.aboutthataction.engine.Zone
import java.util.Locale

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
    /** How you played ("CARDBOARD ENTHUSIAST"), from [com.bradflaugher.aboutthataction.engine.RunReport]. */
    val title: String = "",
    /** What got you ("Steamed like a dumpling"). */
    val deathLine: String = "",
    /** The sign-off ("Cardboard remains undefeated."). */
    val quip: String = "",
    /** Label to value: best combo, ghost floors, box ambushes... only the non-zero ones. */
    val highlights: List<Pair<String, String>> = emptyList(),
    /** Who ran it. */
    val hero: Hero = Hero.BULL,
    /** The difficulty by name ("AGENT", "HELL", "CUSTOM"), for the share message. */
    val difficulty: String = "",
    /** How the run did on its challenge, for a challenge run. */
    val challenge: ChallengeStatus? = null,
    /** Other challenges the run met on the side, in the order they cleared. */
    val alsoCleared: List<SideClear> = emptyList(),
)

/** A challenge cleared on the side, for the game over's ALSO CLEARED list. */
data class SideClear(val id: Int, val name: String, val tier: com.bradflaugher.aboutthataction.engine.Tier)

internal fun grouped(n: Long): String = String.format(Locale.US, "%,d", n)

/** A preset's short name for a chip: STRAIGHT TO HELL is just HELL there. */
internal fun presetLabel(p: Difficulty.Preset?): String = when (p) {
    null -> "CUSTOM"
    Difficulty.Preset.STRAIGHT_TO_HELL -> "HELL"
    else -> p.label
}

/**
 * One line of display text that shrinks to fit its width instead of wrapping. It measures the
 * real text (so big, non-linearly scaled font settings fit too), and its tracking shrinks with
 * the glyphs. No subcomposition, so it works inside intrinsically measured rows.
 */
@Composable
fun FitText(
    text: String,
    maxSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    title: Boolean = true,
    letterSpacing: TextUnit = 2.sp,
    glow: Float = 0.6f,
    alignment: Alignment = Alignment.Center,
) {
    val align = when (alignment) {
        Alignment.CenterStart, Alignment.TopStart, Alignment.BottomStart -> TextAlign.Start
        Alignment.CenterEnd, Alignment.TopEnd, Alignment.BottomEnd -> TextAlign.End
        else -> TextAlign.Center
    }
    Box(modifier, contentAlignment = alignment) {
        BasicText(
            text,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = maxSize, stepSize = 0.25.sp),
            style = TextStyle(
                color = color,
                fontFamily = if (title) Neon.title else Neon.mono,
                textAlign = align,
                // In em, so the tracking scales with whatever size fits.
                letterSpacing = (letterSpacing.value / maxSize.value).em,
                shadow = if (glow > 0f) Shadow(color.copy(alpha = glow), Offset.Zero, 16f) else null,
            ),
        )
    }
}

// ------------------------------------------------------------------ backdrops

/** Darkens top and bottom for legibility and leaves the middle for the live demo. */
private fun Modifier.titleScrim(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(0f to Neon.night.copy(alpha = 0.92f), 0.34f to Neon.night.copy(alpha = 0.35f), 0.46f to Color.Transparent))
    drawRect(Brush.verticalGradient(0.52f to Color.Transparent, 0.66f to Neon.night.copy(alpha = 0.7f), 0.8f to Neon.night.copy(alpha = 0.9f), 1f to Neon.night))
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Neon.night.copy(alpha = 0.55f)),
            center = Offset(size.width / 2, size.height * 0.5f), radius = size.maxDimension * 0.7f,
        ),
    )
}

/** A dim veil over a frozen game. */
internal fun Modifier.veil(tint: Color = Neon.night, alpha: Float = 0.78f): Modifier = drawBehind {
    drawRect(tint.copy(alpha = alpha))
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
            center = center, radius = size.maxDimension * 0.65f,
        ),
    )
}

// ------------------------------------------------------------------ title

/** The title's difficulty row: the everyday presets, then CUSTOM (null), which opens its own screen. */
private val TITLE_ROW: List<Difficulty.Preset?> = Settings.TITLE_PRESETS + null

/** The LIVE FEED line: the preset's blurb, or a compact read of the custom curve. */
internal fun difficultyBlurb(s: Settings): String = s.preset?.blurb ?: customSummary(s.custom, s.setSeed)

/** "♥3 · ramp ×1.0 · from the roof", or "♥3 · from the roof · seed K7QM 2XAB": the custom run in one short line. */
internal fun customSummary(d: Difficulty, seed: Long? = null): String {
    val from = if (d.startFloor == 0) "the roof" else Zone.baseZoneOf(d.startFloor).title.lowercase(Locale.US)
    if (seed != null) return String.format(Locale.US, "♥%d · from %s · seed %s", d.hearts, from, SeedCode.labelOf(seed))
    return String.format(Locale.US, "♥%d · ramp ×%.1f · from %s", d.hearts, d.ramp, from)
}

@Composable
fun TitleScreen(
    settings: Settings,
    records: Records,
    insets: PaddingValues,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onPreset: (Difficulty.Preset) -> Unit,
    onCustom: () -> Unit,
    onHeroes: () -> Unit,
    onChallenges: () -> Unit = {},
    /** Today's challenge, once there is one to show; it slots in between the hero bar and DROP IN. */
    daily: DailyCard? = null,
    onDaily: () -> Unit = {},
    /** Under CHALLENGES, e.g. "37/1,234 CLEARED". */
    challengesCaption: String? = "PICK A MISSION",
) {
    // Logo up top, controls down by the thumbs; at big font sizes or in a short window, it scrolls.
    BoxWithConstraints(Modifier.fillMaxSize().titleScrim().padding(insets).padding(horizontal = Space.l)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(top = Space.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NeonLogo()
                Tagline(Modifier.reveal(700).padding(top = Space.xs))
                if (records.runs > 0) {
                    Row(
                        Modifier.reveal(850).padding(top = Space.m),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        RecordChip("DEEPEST", FloorLabel.of(records.bestFloor), Modifier.weight(1f, fill = false))
                        RecordChip("BEST", grouped(records.bestScore), Modifier.weight(1f, fill = false))
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(top = Space.l, bottom = Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Row(Modifier.fillMaxWidth().reveal(250), verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(Neon.blood)
                    Kicker("LIVE FEED", Neon.blood.copy(alpha = 0.9f), Modifier.padding(start = Space.xs))
                    // One line, always: a long blurb (or a big font) shrinks instead of wrapping.
                    FitText(
                        difficultyBlurb(settings).uppercase(Locale.US), Type.micro,
                        if (settings.preset == null) Neon.hotPink else Neon.dim,
                        Modifier.weight(1f).padding(start = Space.m),
                        title = false, letterSpacing = 3.sp, glow = 0f, alignment = Alignment.CenterEnd,
                    )
                }
                // The three everyday presets, then CUSTOM, which opens the curve editor.
                Segmented(
                    TITLE_ROW,
                    settings.preset,
                    label = { it?.label ?: "CUSTOM" },
                    color = Neon.magenta,
                    modifier = Modifier.reveal(300),
                ) { if (it != null) onPreset(it) else onCustom() }
                HeroBar(settings.hero, Modifier.reveal(340), onHeroes)
                if (daily != null) DailyChallengeCard(daily, Modifier.reveal(370), onDaily)
                NeonButton(
                    "DROP IN", Neon.magenta, Modifier.fillMaxWidth().reveal(400).padding(top = Space.xxs),
                    style = ButtonStyle.PRIMARY, height = 72.dp, textSize = 26.sp,
                    trailing = { DropChevrons() },
                    onClick = onPlay,
                )
                Row(Modifier.fillMaxWidth().reveal(460), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    NeonButton("SETTINGS", Neon.cyan, Modifier.weight(1f), height = 56.dp, caption = "SOUND · CONTROLS", onClick = onSettings)
                    NeonButton("CHALLENGES", Neon.gold, Modifier.weight(1f), height = 56.dp, caption = challengesCaption, onClick = onChallenges)
                }
            }
        }
    }
}

@Composable
private fun Tagline(modifier: Modifier = Modifier) = BoxWithConstraints(modifier.fillMaxWidth()) {
    val cap = maxWidth * 0.8f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).drawBehind { drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Neon.lava))) })
        FitText("AN ENDLESS DESCENT", Type.small, Neon.lava, Modifier.widthIn(max = cap).padding(horizontal = Space.s),
            title = false, letterSpacing = 4.sp, glow = 0.7f)
        Box(Modifier.weight(1f).height(1.dp).drawBehind { drawRect(Brush.horizontalGradient(listOf(Neon.lava, Color.Transparent))) })
    }
}

@Composable
private fun RecordChip(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .drawBehind {
                val o = Shapes.chip.createOutline(size, layoutDirection, this)
                drawOutline(o, Neon.ink.copy(alpha = 0.75f))
                drawOutline(o, Neon.gold.copy(alpha = 0.45f), style = Stroke(1.dp.toPx()))
            }
            .padding(horizontal = Space.s, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FitText(label, Type.micro, Neon.gold.copy(alpha = 0.7f), Modifier.weight(1f, fill = false), title = false, letterSpacing = 3.sp, glow = 0f)
        FitText(value, Type.body, Neon.gold, Modifier.weight(1f, fill = false).padding(start = Space.xs), title = false, letterSpacing = 0.5.sp)
    }
}

/** Three chevrons cascading downward: this button takes you down. */
@Composable
internal fun RowScope.DropChevrons() {
    val t = rememberInfiniteTransition(label = "chev")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
    Box(
        Modifier.padding(start = Space.s).size(22.dp, 30.dp).drawBehind {
            val w = size.width
            val step = size.height / 3.4f
            for (i in 0 until 3) {
                val k = (phase * 3 - i).let { ((it % 3) + 3) % 3 } // 0..3
                val a = (1f - k / 3f).coerceIn(0.15f, 1f)
                val y = step * i + step * 0.6f
                val sw = 2.5.dp.toPx()
                val c = Color.White.copy(alpha = a)
                drawLine(c, Offset(w * 0.1f, y), Offset(w * 0.5f, y + step * 0.6f), sw)
                drawLine(c, Offset(w * 0.5f, y + step * 0.6f), Offset(w * 0.9f, y), sw)
            }
        },
    )
}

// ------------------------------------------------------------------ pause

@Composable
fun PauseScreen(
    settings: Settings,
    seedLabel: String,
    hero: Hero,
    insets: PaddingValues,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onQuit: () -> Unit,
    onSettings: (Settings) -> Unit,
    /** The run's challenge, if it's a challenge run: shown instead of the seed (the challenge is the seed). */
    challenge: ChallengeStatus? = null,
) {
    Box(
        Modifier.fillMaxSize().veil().scanlines(0.06f).padding(insets).padding(horizontal = Space.m),
        contentAlignment = Alignment.Center,
    ) {
        Panel(
            Modifier.fillMaxWidth().widthIn(max = 440.dp).verticalScroll(rememberScrollState()),
            accent = Neon.cyan,
            padding = Space.l,
            spacing = Space.s,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row {
                        Kicker(hero.title, hero.tint)
                        Kicker("  ·  MISSION ON HOLD", Neon.cyan.copy(alpha = 0.8f))
                    }
                    NeonText("PAUSED", size = Type.display, color = Color.White, title = true, letterSpacing = 3.sp, glow = 0.4f)
                }
            }
            if (challenge != null) ChallengeStatusCard(challenge)
            else SeedChip(seedLabel, Modifier.fillMaxWidth(), height = 52.dp, valueSize = Type.body)
            NeonButton("RESUME", Neon.magenta, Modifier.fillMaxWidth().padding(top = Space.xs), style = ButtonStyle.PRIMARY, height = 64.dp,
                onClick = onResume)
            AudioAndControls(settings, onSettings)
            Row(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                NeonButton("RESTART", Neon.cyan, Modifier.weight(1f), onClick = onRestart)
                NeonButton("QUIT", Neon.lava, Modifier.weight(1f), onClick = onQuit)
            }
        }
    }
}

/** Volumes and toggles, shared by pause and settings. */
@Composable
internal fun AudioAndControls(s: Settings, onChange: (Settings) -> Unit, audioIndex: String = "//", controlsIndex: String = "//") {
    SectionHeader(audioIndex, "AUDIO", Neon.cyan)
    LevelMeter("MUSIC", s.musicVolume) { onChange(s.copy(musicVolume = it)) }
    LevelMeter("SOUND FX", s.sfxVolume) { onChange(s.copy(sfxVolume = it)) }
    SectionHeader(controlsIndex, "CONTROLS", Neon.cyan)
    Toggle("Haptics", "Feel hits and pickups", s.haptics) { onChange(s.copy(haptics = it)) }
    Toggle("Thumb guide", "Ring under your running thumb", s.touchGuide) { onChange(s.copy(touchGuide = it)) }
    Toggle("Coach tips", "A one-line hint the first time each move would help", s.coach) { onChange(s.copy(coach = it)) }
}
