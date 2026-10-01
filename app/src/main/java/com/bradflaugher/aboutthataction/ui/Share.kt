package com.bradflaugher.aboutthataction.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.engine.FloorLabel
import com.bradflaugher.aboutthataction.engine.SeedCode
import kotlinx.coroutines.delay

// Sharing needs no permission: the system share sheet and the clipboard do the work.

/** Opens the system share sheet with plain text. */
internal fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share your seed").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("'Bout That Action seed", text))
}

/** Whatever text is on the clipboard (Android shows its own "pasted" notice), or null. */
internal fun pasteText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()

/**
 * The brag for the share sheet: short, silly, and carrying everything a friend's PASTE
 * needs (the seed, the difficulty and the hero; see SeedCode.find).
 */
fun shareMessage(run: RunSummary): String {
    run.challenge?.let { c ->
        val what = "${idLabel(c.id)} ${c.name}"
        return if (c.cleared) "I cleared $what in 'Bout That Action. Your move."
        else "I got ${c.hud} on $what in 'Bout That Action. Your move."
    }
    val on = when {
        run.curve != null -> "on a custom curve (${SeedCode.curveTag(run.curve)})"
        run.difficulty.isBlank() || run.difficulty == "CUSTOM" -> "on a custom curve"
        else -> "on ${run.difficulty}"
    }
    val dare = listOf("Beat that.", "Your move.", "Bring a box.", "Mind the lamps.")[(run.floor and 0x7fffffff) % 4]
    return "I hit ${FloorLabel.of(run.floor)} as ${run.hero.title} $on in 'Bout That Action. Seed ${run.seedLabel}. $dare"
}

/**
 * The run's seed as a tappable chip: a kicker, the code in big mono, and a copy mark.
 * Tap to copy; it says COPIED for a moment.
 */
@Composable
fun SeedChip(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Neon.cyan,
    clip: String = label,
    height: Dp = 56.dp,
    valueSize: TextUnit = Type.title,
) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var copied by remember { mutableStateOf(0) }
    LaunchedEffect(copied) { if (copied > 0) { delay(1600); copied = 0 } }
    Row(
        modifier
            .heightIn(min = height)
            .pressScale(source, 0.97f)
            .semantics { contentDescription = "Seed $label. Copy" }
            .drawBehind {
                val o = Shapes.small.createOutline(size, layoutDirection, this)
                drawOutline(o, if (pressed) color.copy(alpha = 0.22f) else Neon.ink.copy(alpha = 0.82f))
                drawOutline(o, color.copy(alpha = 0.55f), style = Stroke(1.dp.toPx()))
            }
            .clickable(source, null, role = Role.Button) {
                copyText(context, clip)
                copied++
            }
            .padding(horizontal = Space.s, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                copied > 0,
                transitionSpec = { fadeIn(tween(Motion.fast)) togetherWith fadeOut(tween(Motion.fast)) },
                label = "copied",
            ) { done ->
                if (done) Kicker("COPIED", ClearedGreen) else Kicker("SEED · TAP TO COPY", Neon.dim)
            }
            FitText(label, valueSize, Color.White, title = false, letterSpacing = 3.sp, glow = 0.3f, alignment = Alignment.CenterStart)
        }
        Box(Modifier.padding(start = Space.xs).size(18.dp).drawBehind { copyMark(if (copied > 0) ClearedGreen else color) })
    }
}

/** Two overlapping sheets: copy. */
internal fun DrawScope.copyMark(color: Color) {
    val sw = 1.6.dp.toPx()
    val s = size.minDimension * 0.66f
    val r = CornerRadius(2.dp.toPx())
    drawRoundRect(color.copy(alpha = 0.55f), Offset(0f, 0f), Size(s, s), r, style = Stroke(sw))
    drawRoundRect(Neon.ink, Offset(size.width - s, size.height - s), Size(s, s), r)
    drawRoundRect(color, Offset(size.width - s, size.height - s), Size(s, s), r, style = Stroke(sw))
}

/** Three linked dots: share. */
internal fun DrawScope.shareMark(color: Color) {
    val w = size.width
    val h = size.height
    val r = size.minDimension * 0.14f
    val a = Offset(w * 0.78f, h * 0.2f)
    val b = Offset(w * 0.22f, h * 0.5f)
    val c = Offset(w * 0.78f, h * 0.8f)
    val sw = 1.8.dp.toPx()
    drawLine(color, a, b, sw)
    drawLine(color, b, c, sw)
    for (p in listOf(a, b, c)) drawCircle(color, r, p)
}
