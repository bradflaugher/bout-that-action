package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NeonText(
    text: String,
    size: TextUnit = Type.body,
    color: Color = Neon.text,
    title: Boolean = false,
    align: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = if (title) 1.5.sp else 0.5.sp,
    glow: Float = 0.55f,
    maxLines: Int = Int.MAX_VALUE,
    glowColor: Color = color,
    glowRadius: Float = 16f,
) {
    BasicText(
        text,
        modifier,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = if (title) Neon.title else Neon.mono,
            textAlign = align,
            letterSpacing = letterSpacing,
            shadow = if (glow > 0f) Shadow(glowColor.copy(alpha = glow), Offset.Zero, glowRadius) else null,
        ),
    )
}

/** A small uppercase kicker label. */
@Composable
fun Kicker(text: String, color: Color = Neon.dim, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    NeonText(text, size = Type.micro, color = color, letterSpacing = 3.sp, glow = 0f, align = align, modifier = modifier)
}

// ------------------------------------------------------------------ motion

/** Squish on press with a springy release. */
fun Modifier.pressScale(source: MutableInteractionSource, pressedScale: Float = 0.95f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        if (pressed) tween(60) else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}

/**
 * Staggered entrance: fade in and rise [rise] into place after [delayMs].
 * Every screen builds its reveal out of this so the rhythm is shared.
 */
fun Modifier.reveal(delayMs: Int, rise: Dp = 18.dp, durationMs: Int = Motion.slow): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(durationMs, delayMs, Motion.out)) }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * rise.toPx()
    }
}

// ------------------------------------------------------------------ drawing helpers

private fun DrawScope.shapeOutline(shape: Shape) = shape.createOutline(size, layoutDirection, this)

/** Layered strokes of falling alpha: a cheap, deterministic neon bloom around a shape. */
fun DrawScope.neonGlow(shape: Shape, color: Color, strength: Float = 1f, spread: Dp = 10.dp) {
    val o = shapeOutline(shape)
    val steps = 4
    for (i in steps downTo 1) {
        val w = spread.toPx() * i / steps * 2f
        drawOutline(o, color.copy(alpha = 0.06f * strength * (steps - i + 1) / steps * 2f), style = Stroke(w))
    }
}

/** Tactical corner brackets on the two square corners of a chamfered box. */
fun DrawScope.cornerTicks(color: Color, len: Dp = 10.dp, stroke: Dp = 2.dp) {
    val l = len.toPx()
    val s = stroke.toPx()
    val w = size.width
    val h = size.height
    // top-right
    drawLine(color, Offset(w - l, s / 2), Offset(w - s / 2, s / 2), s)
    drawLine(color, Offset(w - s / 2, s / 2), Offset(w - s / 2, l), s)
    // bottom-left
    drawLine(color, Offset(s / 2, h - l), Offset(s / 2, h - s / 2), s)
    drawLine(color, Offset(s / 2, h - s / 2), Offset(l, h - s / 2), s)
}

/** Faint CRT scanlines over whatever was drawn. */
fun Modifier.scanlines(alpha: Float = 0.08f, pitch: Dp = 3.dp): Modifier = drawWithContent {
    drawContent()
    val p = pitch.toPx()
    var y = 0f
    val c = Color.Black.copy(alpha = alpha)
    while (y < size.height) {
        drawRect(c, Offset(0f, y), Size(size.width, p / 2f))
        y += p
    }
}

// ------------------------------------------------------------------ buttons

enum class ButtonStyle { PRIMARY, SECONDARY, GHOST }

/**
 * The one button. PRIMARY is a lit neon slab with a sweeping sheen;
 * SECONDARY is an outlined tube; GHOST is text with a hairline.
 */
@Composable
fun NeonButton(
    label: String,
    color: Color = Neon.cyan,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.SECONDARY,
    height: Dp = 56.dp,
    textSize: TextUnit = if (style == ButtonStyle.PRIMARY) Type.headline else Type.title,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    /** A small second line under the label ("SEED · SOUND"); the button grows for big fonts. */
    caption: String? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = Shapes.button
    val sheen = if (style == ButtonStyle.PRIMARY) {
        val t = rememberInfiniteTransition(label = "sheen")
        t.animateFloat(
            0f, 1f,
            infiniteRepeatable(keyframes { durationMillis = 3200; 0f at 0; 0f at 1700; 1f at 2500 using Motion.inOut; 1f at 3200 }),
            label = "sheenX",
        ).value
    } else 0f
    Row(
        modifier
            .then(if (caption == null) Modifier.height(height.coerceAtLeast(Space.touch)) else Modifier.heightIn(min = height.coerceAtLeast(Space.touch)))
            .pressScale(source)
            .drawBehind {
                val o = shapeOutline(shape)
                when (style) {
                    ButtonStyle.PRIMARY -> {
                        neonGlow(shape, color, if (pressed) 1.6f else 1f, 12.dp)
                        drawOutline(
                            o,
                            Brush.verticalGradient(
                                listOf(lerp(color, Color.White, if (pressed) 0.25f else 0f), lerp(color, Neon.night, 0.5f)),
                            ),
                        )
                        clipPath(Path().apply { addOutline(o) }) {
                            // top highlight
                            drawRect(Color.White.copy(alpha = 0.22f), size = Size(size.width, 1.5.dp.toPx()), topLeft = Offset(0f, 1.dp.toPx()))
                            // sheen sweep
                            val band = size.width * 0.28f
                            val x = -band + (size.width + band * 2) * sheen
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                                    start = Offset(x - band, 0f), end = Offset(x, size.height),
                                ),
                                blendMode = BlendMode.Plus,
                            )
                            // fine inner scanlines
                            var y = 0f
                            val p = 3.dp.toPx()
                            while (y < size.height) {
                                drawRect(Color.Black.copy(alpha = 0.07f), Offset(0f, y), Size(size.width, p / 2))
                                y += p
                            }
                        }
                        drawOutline(o, lerp(color, Color.White, 0.45f), style = Stroke(1.5.dp.toPx()))
                    }
                    ButtonStyle.SECONDARY -> {
                        if (pressed) neonGlow(shape, color, 1.2f, 8.dp)
                        drawOutline(o, if (pressed) color.copy(alpha = 0.28f) else Neon.ink.copy(alpha = 0.78f))
                        drawOutline(o, color.copy(alpha = if (pressed) 1f else 0.85f), style = Stroke(1.5.dp.toPx()))
                        cornerTicks(color, 7.dp, 2.dp)
                    }
                    ButtonStyle.GHOST -> {
                        drawOutline(o, if (pressed) color.copy(alpha = 0.2f) else Neon.ink.copy(alpha = 0.5f))
                        drawOutline(o, color.copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
                    }
                }
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.m),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shrinks rather than truncates on narrow phones.
        val ink = if (style == ButtonStyle.PRIMARY) Color.White else color
        val spacing = if (style == ButtonStyle.PRIMARY) 4.sp else 2.5.sp
        val glow = if (style == ButtonStyle.PRIMARY) 0.35f else 0.5f
        if (caption == null) {
            FitText(label, textSize, ink, Modifier.weight(1f, fill = false), letterSpacing = spacing, glow = glow)
        } else {
            Column(
                Modifier.weight(1f, fill = false).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FitText(label, textSize, ink, letterSpacing = spacing, glow = glow)
                FitText(caption, Type.micro, ink.copy(alpha = 0.62f), title = false, letterSpacing = 2.sp, glow = 0f)
            }
        }
        trailing?.invoke(this)
    }
}

/** A square 48dp icon button drawn with lines (back chevron, etc.). */
@Composable
fun IconButton(description: String, color: Color = Neon.cyan, onClick: () -> Unit, icon: DrawScope.(Color) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .size(Space.touch)
            .pressScale(source, 0.9f)
            .semantics { contentDescription = description }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .drawBehind {
                val o = shapeOutline(Shapes.small)
                drawOutline(o, if (pressed) color.copy(alpha = 0.25f) else Neon.ink.copy(alpha = 0.7f))
                drawOutline(o, color.copy(alpha = 0.7f), style = Stroke(1.5.dp.toPx()))
                icon(color)
            },
    )
}

fun DrawScope.chevronLeft(color: Color) {
    val c = center
    val s = size.minDimension * 0.16f
    val w = 2.5.dp.toPx()
    drawLine(color, Offset(c.x + s * 0.5f, c.y - s), Offset(c.x - s * 0.5f, c.y), w)
    drawLine(color, Offset(c.x - s * 0.5f, c.y), Offset(c.x + s * 0.5f, c.y + s), w)
}

/**
 * A sub-screen's header bar: back chevron, kicker over a big title, an optional readout on the
 * right, and a glowing hairline under it. It stays put while the screen scrolls under it.
 */
@Composable
fun MenuHeader(
    kicker: String,
    title: String,
    accent: Color,
    onBack: () -> Unit,
    line: List<Color> = listOf(accent.copy(alpha = 0.6f), Neon.magenta.copy(alpha = 0.3f), Color.Transparent),
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton("Back", accent, onBack) { chevronLeft(it) }
        Column(Modifier.weight(1f).padding(start = Space.m)) {
            FitText(kicker, Type.micro, accent.copy(alpha = 0.8f), Modifier.fillMaxWidth(), title = false, letterSpacing = 3.sp,
                glow = 0f, alignment = Alignment.CenterStart)
            FitText(title, 28.sp, Color.White, Modifier.fillMaxWidth(), letterSpacing = 3.sp, glow = 0.35f, alignment = Alignment.CenterStart)
        }
        trailing()
    }
    Box(Modifier.fillMaxWidth().height(1.dp).drawBehind { drawRect(Brush.horizontalGradient(line)) })
}

// ------------------------------------------------------------------ surfaces

/** A chamfered glass panel with tactical corner ticks. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color = Neon.magenta,
    padding: Dp = Space.m,
    spacing: Dp = Space.s,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .drawBehind {
                val o = shapeOutline(Shapes.panel)
                drawOutline(o, Brush.verticalGradient(listOf(Neon.panelHi, Neon.panel)))
                drawOutline(
                    o,
                    Brush.verticalGradient(listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.12f), accent.copy(alpha = 0.35f))),
                    style = Stroke(1.dp.toPx()),
                )
                cornerTicks(accent, 14.dp, 2.dp)
            }
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** "01 ─ DIFFICULTY ───────" */
@Composable
fun SectionHeader(index: String, text: String, color: Color = Neon.magenta, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        NeonText(index, size = Type.micro, color = color, letterSpacing = 1.sp, glow = 0.6f)
        Box(Modifier.padding(horizontal = Space.xs).width(12.dp).height(1.dp).drawBehind { drawRect(color) })
        Kicker(text, Neon.soft)
        Box(
            Modifier.padding(start = Space.s).weight(1f).height(1.dp)
                .drawBehind { drawRect(Brush.horizontalGradient(listOf(Neon.line, Color.Transparent))) },
        )
    }
}

/** A thin divider hairline. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Neon.line) {
    Box(modifier.fillMaxWidth().height(1.dp).drawBehind { drawRect(color) })
}

/** A pulsing dot. */
@Composable
fun LiveDot(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(
        modifier.size(8.dp).drawBehind {
            drawCircle(color.copy(alpha = 0.25f * a), radius = size.minDimension)
            drawCircle(color.copy(alpha = a), radius = size.minDimension / 2)
        },
    )
}

/** Rounded rect helper used by charts and meters. */
internal fun DrawScope.bar(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float = 2f) {
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r))
}
