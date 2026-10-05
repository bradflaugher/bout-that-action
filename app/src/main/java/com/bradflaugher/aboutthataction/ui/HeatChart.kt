package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Zone

/**
 * The custom heat curve from the roof to floor 200: an area chart over the
 * zone bands, with the run's start floor marked. Morphs when values change.
 */
@Composable
fun HeatChart(d: Difficulty, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val floors = 200
    val maxHeat = 7.5f
    val target = remember(d) { FloatArray(floors + 1) { f -> val g = f.coerceAtMost(floors - 1); d.heat(g, Zone.baseZoneOf(g)) } }
    // Morph from whatever is on screen to the new curve.
    val curve = remember { object { var from = target; var to = target } }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (curve.to !== target) {
            val v = morph.value
            val a = curve.from
            val b = curve.to
            curve.from = FloatArray(floors + 1) { a[it] + (b[it] - a[it]) * v }
            curve.to = target
            morph.snapTo(0f)
            morph.animateTo(1f, tween(Motion.slow, easing = Motion.out))
        }
    }
    val labelStyle = TextStyle(fontFamily = Neon.body, fontSize = 9.sp, color = Neon.dim, letterSpacing = 1.sp)
    val zones = Zone.entries.filter { it != Zone.ROOFTOP && it != Zone.VOID }
    val start = d.startFloor

    Canvas(
        modifier
            .fillMaxWidth()
            .height(148.dp)
            .semantics { contentDescription = "Heat curve chart" },
    ) {
        val left = 22.dp.toPx()
        val right = size.width
        val top = 10.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val w = right - left
        val h = bottom - top
        fun x(f: Float) = left + w * f / floors
        fun y(heat: Float) = bottom - h * (heat / maxHeat).coerceIn(0f, 1f)

        // Zone bands and labels.
        zones.forEachIndexed { i, z ->
            val x0 = x(z.startFloor.toFloat())
            val x1 = x((zones.getOrNull(i + 1)?.startFloor ?: floors).toFloat())
            val c = Neon.zone(z)
            drawRect(c.copy(alpha = if (i % 2 == 0) 0.07f else 0.035f), Offset(x0, top), androidx.compose.ui.geometry.Size(x1 - x0, h))
            drawRect(c.copy(alpha = 0.7f), Offset(x0, bottom + 2.dp.toPx()), androidx.compose.ui.geometry.Size(x1 - x0 - 2.dp.toPx(), 2.dp.toPx()))
            val label = measurer.measure(shortZone(z), labelStyle.copy(color = c.copy(alpha = 0.85f)))
            if (label.size.width < x1 - x0) {
                drawText(label, topLeft = Offset((x0 + x1) / 2 - label.size.width / 2, bottom + 6.dp.toPx()))
            }
        }

        // Heat reference lines.
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        for ((heat, name) in listOf(1f to "1", 3f to "3", 5f to "5", 7f to "7")) {
            drawLine(Neon.line, Offset(left, y(heat)), Offset(right, y(heat)), 1.dp.toPx(), pathEffect = dash)
            val l = measurer.measure(name, labelStyle)
            drawText(l, topLeft = Offset(left - l.size.width - 6.dp.toPx(), y(heat) - l.size.height / 2))
        }
        drawLine(Neon.faint, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())

        // The curve.
        val m = morph.value
        val line = Path()
        val area = Path()
        for (f in 0..floors) {
            val v = curve.from[f] + (curve.to[f] - curve.from[f]) * m
            val px = x(f.toFloat())
            val py = y(v)
            if (f == 0) {
                line.moveTo(px, py); area.moveTo(px, bottom); area.lineTo(px, py)
            } else {
                line.lineTo(px, py); area.lineTo(px, py)
            }
        }
        area.lineTo(x(floors.toFloat()), bottom)
        area.close()
        drawPath(area, Brush.verticalGradient(listOf(Neon.lava.copy(alpha = 0.55f), Neon.magenta.copy(alpha = 0.18f), Color.Transparent), top, bottom))
        drawPath(line, Neon.magenta.copy(alpha = 0.25f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line, Brush.horizontalGradient(listOf(Neon.gold, Neon.lava, Neon.blood), left, right),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Start floor marker; floors before it are skipped, so shade them out.
        if (start > 0) {
            val sx = x(start.toFloat())
            drawRect(Neon.night.copy(alpha = 0.6f), Offset(left, top), androidx.compose.ui.geometry.Size(sx - left, h))
        }
        val sx = x(start.toFloat())
        drawLine(Neon.cyan, Offset(sx, top), Offset(sx, bottom), 1.5.dp.toPx())
        drawCircle(Neon.cyan, 4.dp.toPx(), Offset(sx, y(target[start.coerceIn(0, floors)])))
        val sl = measurer.measure("START", labelStyle.copy(color = Neon.cyan))
        val lx = (sx + 4.dp.toPx()).coerceAtMost(right - sl.size.width)
        drawText(sl, topLeft = Offset(lx, top))
    }
}

private fun shortZone(z: Zone) = when (z) {
    Zone.TOWER -> "TWR"
    Zone.LABS -> "LAB"
    Zone.METRO -> "MTR"
    Zone.MINES -> "MINE"
    Zone.MAGMA -> "CORE"
    Zone.HELL -> "HELL"
    else -> ""
}
