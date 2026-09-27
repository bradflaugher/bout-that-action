package com.bradflaugher.aboutthataction.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.R

object Neon {
    val night = Color(0xFF07060F)
    val panel = Color(0xE60C0A1C)
    val magenta = Color(0xFFFF2E88)
    val cyan = Color(0xFF21E6FF)
    val lava = Color(0xFFFF6A1A)
    val gold = Color(0xFFFFD23F)
    val text = Color(0xFFF2EEFF)
    val dim = Color(0xFF8C86B0)

    val title = FontFamily(Font(R.font.audiowide))
    val mono = FontFamily(Font(R.font.share_tech_mono))
}

@Composable
fun NeonText(
    text: String,
    size: TextUnit = 16.sp,
    color: Color = Neon.text,
    title: Boolean = false,
    align: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = if (title) 1.5.sp else 0.5.sp,
) {
    BasicText(
        text,
        modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = if (title) Neon.title else Neon.mono,
            textAlign = align,
            letterSpacing = letterSpacing,
            shadow = androidx.compose.ui.graphics.Shadow(color.copy(alpha = 0.6f), Offset.Zero, 18f),
        ),
    )
}

/** A chunky glowing outline button that squishes when pressed. */
@Composable
fun NeonButton(
    label: String,
    color: Color = Neon.cyan,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    height: Dp = 60.dp,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .height(height)
            .scale(if (pressed) 0.96f else 1f)
            .drawBehind {
                val r = CornerRadius(14.dp.toPx())
                for (i in 3 downTo 1) {
                    drawRoundRect(color.copy(alpha = 0.07f * i), cornerRadius = r, style = Stroke(width = (i * 5).dp.toPx()))
                }
                drawRoundRect(
                    if (filled || pressed) color.copy(alpha = if (pressed) 0.45f else 0.28f) else Neon.night.copy(alpha = 0.7f),
                    cornerRadius = r,
                )
                drawRoundRect(color, cornerRadius = r, style = Stroke(width = 2.dp.toPx()))
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        NeonText(label, size = 20.sp, color = if (filled) Neon.text else color, title = true)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, accent: Color = Neon.magenta, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(Neon.panel, RoundedCornerShape(20.dp))
            .border(1.5.dp, Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.15f))), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, color: Color = Neon.magenta) {
    NeonText(text, size = 13.sp, color = color, letterSpacing = 3.sp, modifier = Modifier.padding(top = 6.dp))
}

/** Pick one of several options, as a row of segments. */
@Composable
fun <T> Segmented(options: List<T>, selected: T?, label: (T) -> String, color: Color = Neon.cyan, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            val on = o == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .background(if (on) color.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(10.dp))
                    .border(1.5.dp, if (on) color else Neon.dim.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable(role = Role.RadioButton) { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) {
                NeonText(label(o), size = 13.sp, color = if (on) Neon.text else Neon.dim, align = TextAlign.Center)
            }
        }
    }
}

/** "−  value  +" for thumb-friendly numeric settings. */
@Composable
fun Stepper(label: String, value: String, color: Color = Neon.cyan, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NeonText(label, size = 16.sp, modifier = Modifier.weight(1f))
        StepButton("−", color, onMinus)
        NeonText(value, size = 18.sp, color = color, align = TextAlign.Center, modifier = Modifier.width(84.dp))
        StepButton("+", color, onPlus)
    }
}

@Composable
private fun StepButton(glyph: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .border(1.5.dp, color, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { NeonText(glyph, size = 22.sp, color = color) }
}

@Composable
fun Toggle(label: String, detail: String? = null, on: Boolean, color: Color = Neon.cyan, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!on) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            NeonText(label, size = 16.sp)
            if (detail != null) NeonText(detail, size = 12.sp, color = Neon.dim)
        }
        Box(
            Modifier
                .width(56.dp)
                .height(30.dp)
                .drawBehind {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(if (on) color.copy(alpha = 0.35f) else Neon.dim.copy(alpha = 0.15f), cornerRadius = r)
                    drawRoundRect(if (on) color else Neon.dim, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
                    val cx = if (on) size.width - size.height / 2 else size.height / 2
                    drawCircle(if (on) color else Neon.dim, radius = size.height * 0.34f, center = Offset(cx, size.height / 2))
                },
        )
    }
}
