package com.bradflaugher.aboutthataction.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What the title's DAILY CHALLENGE card shows: plain display data, so the card
 * doesn't care where challenges come from.
 */
data class DailyCard(
    /** The challenge's catalog number (#37). */
    val number: Int,
    val name: String,
    /** One line: "Take down 12 guards". */
    val goal: String,
    /** At most a couple of tiny chips: "SILENT ONLY", "ONE HEART". */
    val rules: List<String> = emptyList(),
    val cleared: Boolean = false,
)

/** Cleared, in the menus: the Black Labs green. */
internal val ClearedGreen = Color(0xFF5CFFB0)

/**
 * Today's challenge on the title, in the hero bar's footprint and silhouette but gold:
 * a numbered ticket stub, the name and goal, its rules as chips, and CLEARED once it's done.
 */
@Composable
fun DailyChallengeCard(card: DailyCard, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Neon.gold
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .pressScale(source, 0.97f)
            .semantics {
                contentDescription = "Daily challenge ${card.number}, ${card.name}. ${card.goal}" +
                    (if (card.cleared) ". Cleared" else "")
            }
            .drawBehind {
                val o = Shapes.button.createOutline(size, layoutDirection, this)
                drawOutline(o, Brush.horizontalGradient(listOf(c.copy(alpha = if (pressed) 0.3f else 0.16f), Neon.ink.copy(alpha = 0.84f))))
                drawOutline(o, c.copy(alpha = if (card.cleared) 0.45f else 0.75f), style = Stroke(1.5.dp.toPx()))
                cornerTicks(c, 7.dp, 2.dp)
            }
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .padding(start = 6.dp, end = Space.m, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TicketStub(card.number, c)
        Column(Modifier.weight(1f).padding(start = Space.s), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Kicker("DAILY", c.copy(alpha = 0.85f), Modifier.padding(end = Space.xxs))
                for (rule in card.rules.take(2)) RuleChip(rule, c)
            }
            FitText(card.name, Type.title, if (card.cleared) Neon.soft else Color.White, Modifier.fillMaxWidth(),
                letterSpacing = 2.sp, glow = 0.3f, alignment = Alignment.CenterStart)
            FitText(card.goal, Type.small, Neon.soft, Modifier.fillMaxWidth(), title = false, letterSpacing = 0.5.sp, glow = 0f,
                alignment = Alignment.CenterStart)
        }
        if (card.cleared) {
            Column(Modifier.padding(start = Space.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(20.dp).drawBehind { check(ClearedGreen) })
                Kicker("CLEARED", ClearedGreen, align = TextAlign.Center)
            }
        } else {
            Box(Modifier.padding(start = Space.xs).size(14.dp, 24.dp).drawBehind { chevronRight(c) })
        }
    }
}

/** "NO. 37" on a little gold stub. */
@Composable
private fun TicketStub(number: Int, c: Color) {
    Column(
        Modifier
            .width(52.dp)
            .heightIn(min = 52.dp)
            .drawBehind {
                val o = Shapes.small.createOutline(size, layoutDirection, this)
                drawOutline(o, c.copy(alpha = 0.12f))
                drawOutline(o, c.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))
                // perforation down the right edge
                val r = 1.2.dp.toPx()
                var y = 6.dp.toPx()
                while (y < size.height - 4.dp.toPx()) {
                    drawCircle(c.copy(alpha = 0.45f), r, Offset(size.width - 5.dp.toPx(), y))
                    y += 5.dp.toPx()
                }
            }
            .padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NeonText("NO.", size = 9.sp, color = c.copy(alpha = 0.75f), letterSpacing = 1.5.sp, glow = 0f)
        FitText(number.toString(), Type.title, c, Modifier.fillMaxWidth(), letterSpacing = 0.5.sp, glow = 0.6f)
    }
}

/** A tiny outlined rule tag: "SILENT ONLY". */
@Composable
fun RuleChip(text: String, color: Color = Neon.gold) {
    Box(
        Modifier
            .drawBehind {
                val o = Shapes.chip.createOutline(size, layoutDirection, this)
                drawOutline(o, color.copy(alpha = 0.12f))
                drawOutline(o, color.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))
            }
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        NeonText(text, size = 9.sp, color = color, letterSpacing = 1.sp, glow = 0f, maxLines = 1)
    }
}

/** A right-pointing chevron filling the box (the hero bar's "go" mark). */
internal fun DrawScope.chevronRight(color: Color) {
    val sw = 2.5.dp.toPx()
    drawLine(color, Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.5f), sw)
    drawLine(color, Offset(size.width * 0.8f, size.height * 0.5f), Offset(size.width * 0.2f, size.height * 0.8f), sw)
}

/** A drawn check mark (the fonts' ✓ glyphs vary by device). */
internal fun DrawScope.check(color: Color) {
    val sw = 2.5.dp.toPx()
    val w = size.width
    val h = size.height
    drawLine(color.copy(alpha = 0.3f), Offset(w * 0.15f, h * 0.55f), Offset(w * 0.4f, h * 0.8f), sw * 2.4f)
    drawLine(color.copy(alpha = 0.3f), Offset(w * 0.4f, h * 0.8f), Offset(w * 0.88f, h * 0.22f), sw * 2.4f)
    drawLine(color, Offset(w * 0.15f, h * 0.55f), Offset(w * 0.4f, h * 0.8f), sw)
    drawLine(color, Offset(w * 0.4f, h * 0.8f), Offset(w * 0.88f, h * 0.22f), sw)
}
