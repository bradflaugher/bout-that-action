package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import kotlin.math.floor
import kotlin.math.sin

/** Seconds since this composable entered, ticking every frame. Read it only in draw lambdas. */
@Composable
fun rememberClock(): State<Float> = produceState(0f) {
    val start = withFrameNanos { it }
    while (true) withFrameNanos { value = (it - start) / 1e9f }
}

/** Cheap deterministic hash in [0, 1). */
internal fun hash01(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0x7fffffff) / 2147483647f
}

/**
 * The title lockup: "'BOUT THAT" as a cyan tube over "ACTION" as a magenta
 * neon sign. Powers on with a stutter, hums, has a failing letter, splits its
 * color channels, and now and then tears into glitch slices. All Canvas.
 */
@Composable
fun NeonLogo(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val density = LocalDensity.current
    val clock = rememberClock()
    val power = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        power.animateTo(1f, keyframes {
            durationMillis = 1100
            0f at 0
            0.9f at 70
            0.1f at 130
            0.0f at 260
            1f at 320
            0.35f at 380
            1f at 470
            0.6f at 700
            1f at 760
        })
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val big = remember(widthPx) {
            val probe = measurer.measure("ACTION", actionStyle(100.sp))
            val size = 100f * (widthPx * 0.86f) / probe.size.width
            measurer.measure("ACTION", actionStyle(size.sp), constraints = Constraints())
        }
        val small = remember(widthPx) {
            val probe = measurer.measure("'BOUT THAT", topStyle(100.sp))
            val size = 100f * (widthPx * 0.62f) / probe.size.width
            measurer.measure("'BOUT THAT", topStyle(size.sp), constraints = Constraints())
        }
        val gap = with(density) { (-4).dp.toPx() }
        val totalPx = small.size.height + gap + big.size.height
        val h = with(density) { totalPx.toDp() } + 8.dp

        Spacer(
            Modifier
                .fillMaxWidth()
                .height(h)
                .drawBehind {
                    val t = clock.value
                    val p = power.value
                    val hum = 0.93f + 0.07f * sin(t * 9f) * sin(t * 2.3f)
                    val topY = 4.dp.toPx()
                    val bigY = topY + small.size.height + gap
                    val bigX = (size.width - big.size.width) / 2f
                    val smallX = (size.width - small.size.width) / 2f

                    // Bloom behind the sign.
                    val bloomC = Offset(size.width / 2, bigY + big.size.height / 2)
                    scale(1f, 0.42f, pivot = bloomC) {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Neon.magenta.copy(alpha = 0.26f * p), Neon.magenta.copy(alpha = 0.08f * p), Color.Transparent),
                                center = bloomC, radius = size.width * 0.6f,
                            ),
                            radius = size.width * 0.6f, center = bloomC,
                        )
                    }

                    // 'BOUT THAT: filled cyan tube, a beat behind the main sign.
                    val topPower = ((p - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    drawText(small, color = Neon.cyan.copy(alpha = 0.9f * topPower * hum), topLeft = Offset(smallX, topY),
                        shadow = Shadow(Neon.cyan.copy(alpha = 0.9f * topPower), Offset.Zero, 22f), drawStyle = Stroke(2.dp.toPx()))
                    // ('BOUT THAT is a tube too; the white core follows.)
                    drawText(small, color = lerp(Neon.cyan, Color.White, 0.6f).copy(alpha = topPower * hum),
                        topLeft = Offset(smallX, topY), drawStyle = Stroke(0.8.dp.toPx()))

                    // ACTION, with a failing "I" (index 3).
                    val glitchCycle = t % 4.7f
                    val glitching = glitchCycle > 4.35f && glitchCycle < 4.52f
                    val split = (if (glitching) 7.dp.toPx() else 2.dp.toPx()) * p
                    val failing = run {
                        val c = t % 6.1f
                        if (c > 4.8f && c < 5.6f) (if (hash01(floor(t * 18f).toInt()) > 0.45f) 0.15f else 1f) else 1f
                    }
                    val iBox = letterSpan(big, 3)

                    fun pass(dx: Float, dy: Float, alpha: (Float) -> Float, body: DrawScope.(Float) -> Unit) {
                        val bounds = listOf(
                            Rect(0f, 0f, bigX + iBox.first, size.height) to 1f,
                            Rect(bigX + iBox.first, 0f, bigX + iBox.second, size.height) to failing,
                            Rect(bigX + iBox.second, 0f, size.width, size.height) to 1f,
                        )
                        for ((r, a) in bounds) {
                            clipRect(r.left, r.top, r.right, r.bottom) {
                                translate(dx, dy) { body(alpha(a)) }
                            }
                        }
                    }

                    fun sign(ox: Float) {
                        val origin = Offset(bigX + ox, bigY)
                        // chromatic channels
                        pass(-split, 0f, { it * p * 0.55f }) { a ->
                            drawText(big, color = Neon.cyan.copy(alpha = a), topLeft = origin, blendMode = BlendMode.Plus, drawStyle = Stroke(3.dp.toPx()))
                        }
                        pass(split, 0f, { it * p * 0.55f }) { a ->
                            drawText(big, color = Neon.blood.copy(alpha = a), topLeft = origin, blendMode = BlendMode.Plus, drawStyle = Stroke(3.dp.toPx()))
                        }
                        // the sign face: tinted glass fill, tube outline with halo, hot white core
                        pass(0f, 0f, { it * p * hum }) { a ->
                            // Explicit styles everywhere: drawText otherwise keeps the last pass's style.
                            drawText(big, color = Neon.magenta.copy(alpha = 0.14f * a), topLeft = origin, drawStyle = Fill,
                                shadow = Shadow(Neon.magenta.copy(alpha = 0.8f * a), Offset.Zero, 48f))
                            drawText(big, color = Neon.magenta.copy(alpha = a), topLeft = origin,
                                drawStyle = Stroke(3.dp.toPx()), shadow = Shadow(Neon.magenta.copy(alpha = a), Offset.Zero, 20f))
                            drawText(big, color = lerp(Neon.hotPink, Color.White, 0.65f).copy(alpha = a), topLeft = origin,
                                drawStyle = Stroke(1.dp.toPx()))
                        }
                    }

                    if (glitching) {
                        // Tear the sign into horizontal slices shifted sideways.
                        val slices = 5
                        val sh = big.size.height / slices
                        for (i in 0 until slices) {
                            val off = (hash01(i * 31 + floor(t * 30f).toInt()) - 0.5f) * 24.dp.toPx()
                            clipRect(0f, bigY + i * sh, size.width, bigY + (i + 1) * sh) { sign(off) }
                        }
                    } else {
                        sign(0f)
                    }

                    // Scanlines across the whole lockup.
                    val pitch = 3.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawRect(Color.Black.copy(alpha = 0.22f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, pitch * 0.45f))
                        y += pitch
                    }
                },
        )
    }
}

private fun actionStyle(size: androidx.compose.ui.unit.TextUnit) =
    TextStyle(fontFamily = Neon.title, fontSize = size, letterSpacing = 0.04.em)

private fun topStyle(size: androidx.compose.ui.unit.TextUnit) =
    TextStyle(fontFamily = Neon.title, fontSize = size, letterSpacing = 0.22.em)

/** Horizontal span (left, right) of the character at [index], inside the layout. */
private fun letterSpan(layout: TextLayoutResult, index: Int): Pair<Float, Float> {
    val b = layout.getBoundingBox(index)
    return b.left to b.right
}
