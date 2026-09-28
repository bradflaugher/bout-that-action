package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.Records
import com.bradflaugher.aboutthataction.engine.FloorLabel
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/** 0 → 1 after [delayMs], eased out. For count-ups and one-shot reveals. */
@Composable
private fun progress(delayMs: Int, durationMs: Int): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(durationMs, delayMs, Motion.out)) }
    return a.value
}

@Composable
fun GameOverScreen(
    run: RunSummary,
    insets: PaddingValues,
    onRetry: () -> Unit,
    onNewRun: () -> Unit,
    onTitle: () -> Unit,
    records: Records? = null,
) {
    val zoneColor = Neon.zone(run.zone)
    val newBest = run.newBestFloor || run.newBestScore
    val clock = rememberClock()
    val flash = remember { Animatable(1f) }
    LaunchedEffect(Unit) { flash.animateTo(0f, tween(420, easing = LinearEasing)) }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Color(0xFF0C0208).copy(alpha = 0.9f))
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Neon.blood.copy(alpha = 0.22f)),
                        center = center, radius = size.maxDimension * 0.62f,
                    ),
                )
                // the hit flash
                if (flash.value > 0f) drawRect(Neon.blood.copy(alpha = 0.45f * flash.value), blendMode = BlendMode.Plus)
            }
            .scanlines(0.1f)
            .padding(insets),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.m, vertical = Space.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            MissionFailed()
            if (run.quip.isNotBlank()) {
                NeonText("\u201C${run.quip}\u201D", size = Type.small, color = Neon.soft, glow = 0f, align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().reveal(520, 6.dp))
            }

            Panel(Modifier.fillMaxWidth().reveal(260, 24.dp), accent = zoneColor, padding = Space.m, spacing = Space.xxs) {
                Kicker("DEEPEST FLOOR", Neon.soft, Modifier.fillMaxWidth(), TextAlign.Center)
                // With a highlights block to fit, the depth numeral steps down a size.
                DepthReveal(run, zoneColor, newBest, compact = run.highlights.isNotEmpty()) { clock.value }
                NeonText(run.zone.title, size = Type.title, color = zoneColor, title = true, letterSpacing = 4.sp,
                    align = TextAlign.Center, modifier = Modifier.fillMaxWidth().reveal(1100, 8.dp))
                NeonText(run.zone.subtitle.uppercase(Locale.US), size = Type.micro, color = Neon.dim, letterSpacing = 2.sp, glow = 0f,
                    align = TextAlign.Center, modifier = Modifier.fillMaxWidth().reveal(1180, 8.dp))
                if (newBest) {
                    NewBestBanner(run, Modifier.padding(top = Space.xs)) { clock.value }
                } else if (records != null && records.bestFloor > 0) {
                    NeonText("PERSONAL BEST  ${FloorLabel.of(records.bestFloor)}  ·  ${grouped(records.bestScore)}", size = Type.micro,
                        color = Neon.gold.copy(alpha = 0.75f), letterSpacing = 1.5.sp, glow = 0f, align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.xxs).reveal(1250, 6.dp))
                }
                if (run.title.isNotBlank() || run.deathLine.isNotBlank()) {
                    Hairline(Modifier.padding(vertical = Space.xs))
                    if (run.title.isNotBlank()) {
                        Kicker("PLAYSTYLE", Neon.dim, Modifier.fillMaxWidth(), TextAlign.Center)
                        NeonText(run.title, size = Type.body, color = Neon.gold, letterSpacing = 2.sp, glow = 0.5f, align = TextAlign.Center,
                            maxLines = 1, modifier = Modifier.fillMaxWidth().reveal(1300, 6.dp))
                    }
                    if (run.deathLine.isNotBlank()) {
                        NeonText(run.deathLine, size = Type.small, color = Neon.soft, glow = 0f, align = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = Space.xxs).reveal(1380, 6.dp))
                    }
                }
                Hairline(Modifier.padding(vertical = Space.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    StatTile("SCORE", run.score, Modifier.weight(1f), 600, if (run.newBestScore) Neon.gold else Color.White) { grouped(it) }
                    StatTile("KILLS", run.kills.toLong(), Modifier.weight(1f), 700) { it.toString() }
                }
                Row(Modifier.padding(top = Space.xxs), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    StatTile("TAKEDOWNS", run.takedowns.toLong(), Modifier.weight(1f), 800) { it.toString() }
                    StatTile("TIME", run.seconds.toLong(), Modifier.weight(1f), 900) {
                        String.format(Locale.US, "%d:%02d", it / 60, it % 60)
                    }
                }
                // The hero and seed ride along as the grid's last cells (filling an odd row's gap).
                Highlights(run.highlights + ("HERO" to run.hero.title) + ("SEED" to run.seedLabel), run.hero.tint, Modifier.padding(top = Space.xs).reveal(1000, 6.dp))
            }

            NeonButton("RETRY SEED", Neon.magenta, Modifier.fillMaxWidth().padding(top = Space.xxs).reveal(700, 16.dp),
                style = ButtonStyle.PRIMARY, height = 56.dp, onClick = onRetry)
            Row(Modifier.reveal(780, 16.dp), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                NeonButton("NEW RUN", Neon.cyan, Modifier.weight(1f), onClick = onNewRun)
                NeonButton("TITLE", Neon.soft, Modifier.weight(1f), style = ButtonStyle.GHOST, onClick = onTitle)
            }
        }
    }
}

/** The run's highlights, two to a row: label on the left, value on the right. */
@Composable
private fun Highlights(items: List<Pair<String, String>>, heroColor: Color, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (pair in items.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                for ((label, value) in pair) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Kicker(label, Neon.dim, Modifier.weight(1f))
                        NeonText(value, size = Type.small, color = when (label) {
                            "SEED" -> Neon.soft
                            "HERO" -> heroColor
                            else -> Color.White
                        }, glow = if (label == "HERO") 0.5f else 0.2f, maxLines = 1)
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** "MISSION FAILED" slams down from oversized, channels split, then settles. */
@Composable
private fun MissionFailed() {
    val slam = progress(0, 380)
    val bar = progress(200, 420)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.graphicsLayer { alpha = bar }, verticalAlignment = Alignment.CenterVertically) {
            LiveDot(Neon.blood)
            Kicker("SIGNAL LOST", Neon.blood, Modifier.padding(start = Space.xs))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.xs)
                .graphicsLayer {
                    val s = 1.7f - 0.7f * slam
                    scaleX = s
                    scaleY = s
                    alpha = (slam * 2.2f).coerceAtMost(1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            val split = (1f - slam) * 14f + 2f
            FitText("MISSION FAILED", 40.sp, Neon.cyan.copy(alpha = 0.5f), Modifier.graphicsLayer { translationX = -split }, glow = 0f)
            FitText("MISSION FAILED", 40.sp, Neon.blood.copy(alpha = 0.6f), Modifier.graphicsLayer { translationX = split }, glow = 0f)
            FitText("MISSION FAILED", 40.sp, Color(0xFFFF4A5A), glow = 1f)
        }
        Box(
            Modifier
                .padding(top = Space.xs)
                .fillMaxWidth(0.7f)
                .height(2.dp)
                .graphicsLayer { scaleX = bar }
                .drawBehind { drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Neon.blood, Color.Transparent))) },
        )
    }
}

/** The floor number counts up to the depth reached, then pops. Gold rays behind a new best. */
@Composable
private fun DepthReveal(run: RunSummary, color: Color, newBest: Boolean, compact: Boolean = false, time: () -> Float) {
    val count = run {
        val a = remember { Animatable(0f) }
        // Even pacing so the readout visibly ticks down the building, then brakes.
        LaunchedEffect(Unit) { a.animateTo(1f, tween(1100, 420, CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))) }
        a.value
    }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1520)
        pop.snapTo(1.12f)
        pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }
    // Count down the building like an elevator readout: ROOF, 49F … 1F, B0 … the floor reached.
    val label = FloorLabel.of((run.floor * count).roundToInt())
    val done = count >= 1f
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 84.dp else 112.dp)
            .drawBehind {
                if (!newBest || !done) return@drawBehind
                val t = time()
                val c = center
                rotate(t * 12f, c) {
                    val rays = 14
                    val r = size.width * 0.6f
                    for (i in 0 until rays) {
                        val a = (i * 360f / rays) * (Math.PI / 180f).toFloat()
                        val a2 = a + 0.1f
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(c.x, c.y)
                            lineTo(c.x + cos(a) * r, c.y + sin(a) * r)
                            lineTo(c.x + cos(a2) * r, c.y + sin(a2) * r)
                            close()
                        }
                        drawPath(path, Brush.radialGradient(listOf(Neon.gold.copy(alpha = 0.28f), Color.Transparent), c, r))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // One size for the whole count, fitted to the widest label it will show.
        val avail = with(density) { maxWidth.toPx() } * 0.92f
        val full = if (compact) 66.sp else 88.sp
        val fitted = remember(run.floor, avail, compact) {
            val widest = listOf(FloorLabel.of(run.floor), "ROOF", "88F").maxOf {
                measurer.measure(it, TextStyle(fontFamily = Neon.title, fontSize = full, letterSpacing = 2.sp)).size.width
            }
            if (widest <= avail) full else full * (avail / widest)
        }
        NeonText(
            label,
            size = fitted,
            color = if (done) Color.White else lerp(color, Color.White, 0.5f),
            title = true,
            align = TextAlign.Center,
            letterSpacing = 2.sp,
            glow = 1f,
            glowColor = color,
            glowRadius = 40f,
            modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}

@Composable
private fun NewBestBanner(run: RunSummary, modifier: Modifier = Modifier, time: () -> Float) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1550)
        appear.animateTo(1f, spring(0.55f, Spring.StiffnessMediumLow))
    }
    val text = when {
        run.newBestFloor && run.newBestScore -> "NEW DEEPEST + HIGH SCORE"
        run.newBestFloor -> "NEW DEEPEST"
        else -> "NEW HIGH SCORE"
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(Space.touch)
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * appear.value
                scaleY = 0.6f + 0.4f * appear.value
            }
            .drawBehind {
                val o = Shapes.button.createOutline(size, layoutDirection, this)
                neonGlow(Shapes.button, Neon.gold, 1.2f, 10.dp)
                drawOutline(o, Brush.verticalGradient(listOf(Neon.gold.copy(alpha = 0.32f), Neon.gold.copy(alpha = 0.12f))))
                drawOutline(o, Neon.gold, style = Stroke(1.5.dp.toPx()))
                // sweeping shimmer
                val t = (time() % 1.8f) / 1.8f
                val band = size.width * 0.25f
                val x = -band + (size.width + 2 * band) * t
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.3f), Color.Transparent), x - band, x + band),
                    blendMode = BlendMode.Plus,
                    topLeft = Offset(0f, 0f), size = Size(size.width, size.height),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        FitText("★  $text  ★", Type.body, Neon.gold, Modifier.padding(horizontal = Space.m), title = false, letterSpacing = 2.sp, glow = 0.8f)
    }
}

@Composable
private fun StatTile(
    label: String,
    value: Long,
    modifier: Modifier,
    delayMs: Int,
    color: Color = Color.White,
    format: (Long) -> String,
) {
    val p = progress(delayMs, 900)
    Column(
        modifier
            .graphicsLayer {
                alpha = (p * 3f).coerceAtMost(1f)
                translationY = (1f - (p * 2f).coerceAtMost(1f)) * 10.dp.toPx()
            }
            .drawBehind {
                drawRect(Neon.ink.copy(alpha = 0.6f))
                drawRect(color.copy(alpha = 0.7f), size = Size(2.dp.toPx(), size.height))
            }
            .padding(horizontal = Space.s, vertical = Space.xxs),
    ) {
        Kicker(label, Neon.dim)
        NeonText(format((value * p).roundToLong()), size = 18.sp, color = color, maxLines = 1, glow = 0.3f)
    }
}
