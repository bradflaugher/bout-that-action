package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.bradflaugher.aboutthataction.SeedMode
import com.bradflaugher.aboutthataction.Settings
import com.bradflaugher.aboutthataction.engine.SeedCode
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
    /** Open scrolled down to the SEED section (for a pasted seed, and the screenshots). */
    scrollToSeed: Boolean = false,
) {
    val c = settings.custom
    val scroll = rememberScrollState()
    if (scrollToSeed) {
        LaunchedEffect(Unit) {
            snapshotFlow { scroll.maxValue }.first { it in 1 until Int.MAX_VALUE }
            scroll.scrollTo(scroll.maxValue)
        }
    }
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
                    .verticalScroll(scroll)
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
                Panel(groupMod.reveal(140, 12.dp, Motion.base + 80), accent = Neon.cyan) {
                    SeedGroup(settings, onChange)
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

/**
 * RANDOM, or SET SEED: type a code, or PASTE a friend's brag. A pasted message that names a
 * difficulty (or a hero) brings that along too, so you're on the very same run.
 */
@Composable
private fun SeedGroup(s: Settings, onChange: (Settings) -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    SectionHeader("03", "SEED", Neon.cyan)
    Segmented(SeedMode.entries.toList(), s.seedMode, label = { it.label }) { note = null; onChange(s.copy(seedMode = it)) }
    AnimatedVisibility(
        s.seedMode == SeedMode.CUSTOM,
        enter = expandVertically(tween(Motion.base, easing = Motion.out)) + fadeIn(tween(Motion.base)),
        exit = shrinkVertically(tween(Motion.base, easing = Motion.out)) + fadeOut(tween(Motion.fast)),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = Space.xxs), horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically) {
            SeedField(s.seedText, Modifier.weight(1f)) { note = null; onChange(s.copy(seedText = it)) }
            NeonButton("PASTE", Neon.cyan, Modifier.width(104.dp), height = 56.dp) {
                val shared = pasteText(context)?.let(SeedCode::find)
                val code = shared?.code
                if (code == null) {
                    note = "No seed code on the clipboard."
                } else {
                    val curve = shared.curve ?: shared.preset?.difficulty ?: s.custom
                    onChange(s.copy(preset = null, seedMode = SeedMode.CUSTOM, seedText = code,
                        custom = curve, hero = shared.hero ?: s.hero))
                    note = listOfNotNull("Loaded ${SeedCode.pretty(code)}", shared.preset?.let(::presetLabel),
                        shared.curve?.let { "their curve" }, shared.hero?.title)
                        .joinToString(" · ") + "."
                }
            }
        }
    }
    val code = SeedCode.decode(s.seedText) != null
    NeonText(
        note ?: when {
            s.seedMode == SeedMode.RANDOM -> "A fresh building every run. Its code is on the game-over card."
            s.seedText.isBlank() -> "Type a code, or paste a friend's brag."
            code -> "Same seed + same curve = same building."
            else -> "Codes are 8 characters. No I, O, 0 or 1."
        },
        size = Type.small, color = if (note != null) Neon.cyan else Neon.dim, glow = 0f,
    )
}

@Composable
internal fun SeedField(text: String, modifier: Modifier = Modifier, onText: (String) -> Unit) {
    val focus = LocalFocusManager.current
    BasicTextField(
        value = text,
        // Only code characters get in: a typo can't make a seed nobody else can type.
        onValueChange = { v -> onText(SeedCode.normalize(v).filter { it in SeedCode.ALPHABET }.take(SeedCode.LENGTH)) },
        visualTransformation = CodeSpacing,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontFamily = Neon.mono, letterSpacing = 2.sp),
        cursorBrush = SolidColor(Neon.cyan),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier,
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
                    if (text.isEmpty()) NeonText("K7QM 2XAB", size = Type.body, color = Neon.faint, glow = 0f, letterSpacing = 2.sp)
                    inner()
                }
                // A whole code gets a check; until then, how far along it is.
                if (SeedCode.decode(text) != null) {
                    Box(Modifier.size(18.dp).drawBehind { check(ClearedGreen) })
                } else {
                    NeonText("${text.length}/${SeedCode.LENGTH}", size = Type.micro, color = Neon.dim, glow = 0f)
                }
            }
        },
    )
}


/** Shows a code as "K7QM 2XAB" while it's stored as "K7QM2XAB". */
private object CodeSpacing : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val t = text.text
        if (t.length <= 4) return TransformedText(text, OffsetMapping.Identity)
        val out = t.substring(0, 4) + " " + t.substring(4)
        return TransformedText(AnnotatedString(out), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset <= 4) offset else offset + 1
            override fun transformedToOriginal(offset: Int) = if (offset <= 4) offset else offset - 1
        })
    }
}
