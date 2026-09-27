package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Pick one of several options: a single track with a sliding lit indicator. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    color: Color = Neon.cyan,
    modifier: Modifier = Modifier,
    height: Dp = Space.touch,
    onSelect: (T) -> Unit,
) {
    val index = options.indexOf(selected)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val o = Shapes.small.createOutline(size, layoutDirection, this)
                drawOutline(o, Neon.ink.copy(alpha = 0.82f))
                drawOutline(o, Neon.line, style = Stroke(1.dp.toPx()))
                // separators
                val segW = size.width / options.size
                for (i in 1 until options.size) {
                    drawLine(Neon.line, Offset(segW * i, size.height * 0.28f), Offset(segW * i, size.height * 0.72f), 1.dp.toPx())
                }
            },
    ) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(segW * index.coerceAtLeast(0), spring(0.8f, Spring.StiffnessMediumLow), label = "seg")
        val shown by animateFloatAsState(if (index >= 0) 1f else 0f, tween(Motion.fast), label = "segShown")
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(segW)
                .fillMaxHeight()
                .padding(3.dp)
                .drawBehind {
                    if (shown <= 0f) return@drawBehind
                    val o = Shapes.small.createOutline(size, layoutDirection, this)
                    neonGlow(Shapes.small, color, 0.8f * shown, 6.dp)
                    drawOutline(o, Brush.verticalGradient(listOf(color.copy(alpha = 0.42f * shown), color.copy(alpha = 0.18f * shown))))
                    drawOutline(o, color.copy(alpha = shown), style = Stroke(1.5.dp.toPx()))
                },
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, o ->
                val on = i == index
                val c by animateColorAsState(if (on) Color.White else Neon.dim, tween(Motion.base), label = "segText")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(on, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) {
                            onSelect(o)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    NeonText(label(o), size = Type.small, color = c, align = TextAlign.Center,
                        letterSpacing = if (options.size > 4) 0.5.sp else 1.5.sp,
                        glow = if (on) 0.5f else 0f, maxLines = 1)
                }
            }
        }
    }
}

/** Label on the left, a "− value +" cluster on the right. */
@Composable
fun Stepper(
    label: String,
    value: String,
    color: Color = Neon.cyan,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = Space.touch), verticalAlignment = Alignment.CenterVertically) {
        NeonText(label, size = Type.body, color = Neon.soft, modifier = Modifier.weight(1f), glow = 0f)
        Row(
            Modifier.drawBehind {
                val o = Shapes.small.createOutline(size, layoutDirection, this)
                drawOutline(o, Neon.ink.copy(alpha = 0.82f))
                drawOutline(o, color.copy(alpha = 0.45f), style = Stroke(1.dp.toPx()))
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("−", "Decrease $label", color, onMinus)
            NeonText(value, size = Type.body, color = Color.White, align = TextAlign.Center, modifier = Modifier.width(76.dp),
                maxLines = 1, glow = 0.3f)
            StepButton("+", "Increase $label", color, onPlus)
        }
    }
}

@Composable
private fun StepButton(glyph: String, description: String, color: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .size(Space.touch)
            .pressScale(source, 0.86f)
            .semantics { contentDescription = description }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .drawBehind { if (pressed) drawRect(color.copy(alpha = 0.25f)) },
        contentAlignment = Alignment.Center,
    ) { NeonText(glyph, size = Type.headline, color = color) }
}

/**
 * A ten-bar level meter: tap or drag across it to set the value. Big, fast
 * and readable at a glance, for volumes.
 */
@Composable
fun LevelMeter(
    label: String,
    value: Float,
    color: Color = Neon.cyan,
    steps: Int = 10,
    onChange: (Float) -> Unit,
) {
    val latest by rememberUpdatedState(onChange)
    // The gesture handlers outlive recompositions: always compare against the current value.
    val current by rememberUpdatedState(value)
    val lit = (value * steps).roundToInt()
    fun set(x: Float, width: Int) {
        val v = ((x / width) * steps + 0.35f).toInt().coerceIn(0, steps) / steps.toFloat()
        if (v != current) latest(v)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.touch)
            .semantics { stateDescription = "${(value * 100).roundToInt()}%" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeonText(label, size = Type.body, color = Neon.soft, modifier = Modifier.width(96.dp), glow = 0f, maxLines = 1)
        Box(
            Modifier
                .weight(1f)
                .height(Space.touch)
                .pointerInput(steps) { detectTapGestures { set(it.x, size.width) } }
                .pointerInput(steps) { detectHorizontalDragGestures { change, _ -> set(change.position.x, size.width) } }
                .drawBehind {
                    val gap = 4.dp.toPx()
                    val w = (size.width - gap * (steps - 1)) / steps
                    val maxH = size.height * 0.62f
                    val base = size.height * 0.5f + maxH / 2
                    for (i in 0 until steps) {
                        val h = maxH * (0.35f + 0.65f * (i + 1) / steps)
                        val x = i * (w + gap)
                        val on = i < lit
                        val c = if (on) lerp(color, Neon.magenta, i / (steps - 1f)) else Neon.faint.copy(alpha = 0.55f)
                        if (on) drawRoundRect(c.copy(alpha = 0.25f), Offset(x - 2f, base - h - 2f), Size(w + 4f, h + 4f), CornerRadius(3f))
                        drawRoundRect(c, Offset(x, base - h), Size(w, h), CornerRadius(2f))
                    }
                },
        )
        NeonText(
            if (lit == 0) "OFF" else "${lit * 100 / steps}%",
            size = Type.body, color = if (lit == 0) Neon.dim else Color.White, align = TextAlign.End,
            modifier = Modifier.width(56.dp), glow = 0f,
        )
    }
}

/** A labelled switch; the whole row is the touch target. */
@Composable
fun Toggle(label: String, detail: String? = null, on: Boolean, color: Color = Neon.cyan, onChange: (Boolean) -> Unit) {
    val pos by animateFloatAsState(if (on) 1f else 0f, spring(0.7f, Spring.StiffnessMedium), label = "knob")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(on, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Switch) {
                onChange(it)
            }
            .padding(vertical = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            NeonText(label, size = Type.body, color = Neon.soft, glow = 0f)
            if (detail != null) NeonText(detail, size = Type.small, color = Neon.dim, glow = 0f)
        }
        Box(
            Modifier
                .padding(start = Space.m)
                .width(52.dp)
                .height(28.dp)
                .drawBehind {
                    val r = CornerRadius(size.height / 2)
                    val track = lerp(Neon.faint.copy(alpha = 0.35f), color.copy(alpha = 0.35f), pos)
                    if (pos > 0f) drawRoundRect(color.copy(alpha = 0.15f * pos), Offset(-4f, -4f), Size(size.width + 8f, size.height + 8f), CornerRadius(size.height))
                    drawRoundRect(track, cornerRadius = r)
                    drawRoundRect(lerp(Neon.faint, color, pos), cornerRadius = r, style = Stroke(1.5.dp.toPx()))
                    val rad = size.height * 0.34f
                    val cx = size.height / 2 + (size.width - size.height) * pos
                    drawCircle(lerp(Neon.dim, Color.White, pos), radius = rad, center = Offset(cx, size.height / 2))
                },
        )
    }
}
