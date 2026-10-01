package com.bradflaugher.aboutthataction.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
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
import com.bradflaugher.aboutthataction.ChallengeLog
import com.bradflaugher.aboutthataction.engine.Challenge
import com.bradflaugher.aboutthataction.engine.ChallengeRun
import com.bradflaugher.aboutthataction.engine.Challenges
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Rule
import com.bradflaugher.aboutthataction.engine.Tier
import java.time.LocalDate
import java.util.Locale

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
    /** The line above the name: "TODAY · ELITE". */
    val kicker: String = "DAILY",
    /** The small word on the stub, over the number. */
    val stub: String = "NO.",
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
        TicketStub(card.stub, card.number, c)
        Column(Modifier.weight(1f).padding(start = Space.s), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Kicker(card.kicker, c.copy(alpha = 0.85f), Modifier.padding(end = Space.xxs))
                for (rule in chipsThatFit(card.rules)) RuleChip(rule, c)
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
private fun TicketStub(stub: String, number: Int, c: Color) {
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
        FitText(stub, 9.sp, c.copy(alpha = 0.75f), Modifier.fillMaxWidth(), title = false, letterSpacing = 1.5.sp, glow = 0f)
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

// ------------------------------------------------------------------ challenge data, for the menus

/** A rule in a word or two, for the board's narrow tag column. */
fun shortRule(r: Rule): String = when (r) {
    Rule.SILENT_ONLY -> "SILENT"
    Rule.GUNS_HOT_ONLY -> "GUNS HOT"
    Rule.ONE_HEART -> "ONE HEART"
    Rule.UNTOUCHED -> "UNTOUCHED"
}

/** "#0274". */
fun idLabel(id: Int): String = String.format(Locale.US, "#%04d", id)

/** Each tier's color: cool and easy up to hot and nasty. */
fun tierColor(t: Tier): Color = when (t) {
    Tier.ROOKIE -> Color(0xFF9AD8FF)
    Tier.PRO -> Neon.cyan
    Tier.ACE -> Color(0xFFB78CFF)
    Tier.ELITE -> Neon.lava
    Tier.LEGEND -> Neon.blood
}

/** "OCT 1": a short date for a day counted from 1970-01-01. */
fun shortDate(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return d.month.name.take(3) + " " + d.dayOfMonth
}

/** Today's daily number, never below 1 (a phone whose clock is set before day one still gets #1). */
fun dailyNumber(epochDay: Long): Long = Challenges.dailyNumber(epochDay).coerceAtLeast(1)

/**
 * The chips that fit beside the card's kicker: the first always, then more while the line
 * stays short (a perk chip like "STARTS WITH FLYING KICK" goes alone).
 */
fun chipsThatFit(chips: List<String>, budget: Int = 26): List<String> {
    val out = ArrayList<String>(2)
    var used = 0
    for (c in chips) {
        if (out.size == 2 || (out.isNotEmpty() && used + c.length > budget)) break
        out += c
        used += c.length + 2
    }
    return out
}

/** The title's card for today's [c], with the goal worded for who'd play it ([pick], if allowed). */
fun dailyCard(c: Challenge, epochDay: Long, log: ChallengeLog, pick: Hero = Hero.BULL): DailyCard = DailyCard(
    number = dailyNumber(epochDay).toInt(),
    name = c.name,
    goal = c.goalText(c.heroFor(pick)),
    rules = c.chips(),
    cleared = log.isCleared(c.id),
    kicker = "TODAY · " + c.tier.title,
    stub = "DAILY",
)

/**
 * Today on the device's own calendar (days since 1970-01-01), for the daily, as Compose state:
 * [refresh] it on resume and on every menu change and the daily follows the date across
 * midnight or a long nap in the background. [clock] is swappable for tests.
 */
class DayClock(private val clock: () -> Long = { LocalDate.now().toEpochDay() }) {
    var day by mutableLongStateOf(clock())
        private set

    /** Reads the date again; true if it moved. */
    fun refresh(): Boolean {
        val now = clock()
        if (now == day) return false
        day = now
        return true
    }
}

/** Today's challenge for this player: the same for everyone who's cleared the same ones before today. */
fun todaysChallenge(epochDay: Long, log: ChallengeLog): Challenge =
    Challenges.daily(epochDay) { id -> (log.clearedDay(id) ?: Long.MAX_VALUE) < epochDay }

/** "37/1,234 CLEARED": live challenges only (a retired clear stays in the log but not in the count). */
fun clearedCaption(log: ChallengeLog): String = "${grouped(log.clearedCount.toLong())}/${grouped(Challenges.size.toLong())} CLEARED"

/**
 * Why [h] can't play [c], to follow their name (null if they can): "can't take anyone down",
 * "is always GUNS HOT", "doesn't have FOX's moves".
 */
fun whyNot(c: Challenge, h: Hero): String? {
    if (c.allows(h)) return null
    val own = c.goal.hero
    return when {
        c.hero != null -> "sits this one out: it's ${c.hero.title}'s"
        own != null && own != h -> "doesn't have ${own.title}'s moves"
        c.goal.melee && !h.melee -> "can't take anyone down"
        (c.goal.sneak || Rule.SILENT_ONLY in c.rules) && !h.sneaks -> "is always GUNS HOT"
        else -> "sits this one out"
    }
}

/** Where a challenge stands, for pause and game over. */
data class ChallengeStatus(
    val id: Int,
    val name: String,
    val tier: Tier,
    val goal: String,
    /** "KILLS 12/30" for this run. */
    val hud: String,
    val fraction: Float,
    val cleared: Boolean,
    val failed: Boolean,
    /** "KILLS 18/30": the best ever, when there is one. */
    val best: String? = null,
    /** Cleared on an earlier run. */
    val clearedBefore: Boolean = false,
) {
    companion object {
        /** Where [run] stands, worded for [hero], who's playing it. */
        fun of(run: ChallengeRun, log: ChallengeLog, hero: Hero): ChallengeStatus {
            val c = run.challenge
            val best = maxOf(log.best(c.id), run.progress)
            return ChallengeStatus(
                id = c.id, name = c.name, tier = c.tier, goal = c.goalText(hero), hud = run.hudText(hero), fraction = run.fraction,
                cleared = run.cleared, failed = run.failed,
                best = if (best > 0) c.hudText(best, hero) else null,
                clearedBefore = log.isCleared(c.id) && !run.cleared,
            )
        }
    }
}

/** A gold progress bar (green once cleared). */
@Composable
fun GoalBar(fraction: Float, cleared: Boolean, modifier: Modifier = Modifier, failed: Boolean = false) {
    val c = when {
        cleared -> ClearedGreen
        failed -> Neon.blood
        else -> Neon.gold
    }
    Box(
        modifier.fillMaxWidth().height(6.dp).drawBehind {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(Neon.faint.copy(alpha = 0.45f), cornerRadius = r)
            val w = size.width * fraction.coerceIn(0f, 1f)
            if (w > 0f) {
                drawRoundRect(c.copy(alpha = 0.3f), Offset(0f, -2f), androidx.compose.ui.geometry.Size(w, size.height + 4f), r)
                drawRoundRect(c, size = androidx.compose.ui.geometry.Size(w, size.height), cornerRadius = r)
            }
        },
    )
}

/**
 * The run's challenge in a gold card: number, tier and name, the goal, and where it stands
 * (CLEARED, BUSTED or a bar). Pause and game over both use it.
 */
@Composable
fun ChallengeStatusCard(st: ChallengeStatus, modifier: Modifier = Modifier, big: Boolean = false) {
    val accent = when {
        st.cleared -> ClearedGreen
        st.failed -> Neon.blood
        else -> Neon.gold
    }
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val o = Shapes.small.createOutline(size, layoutDirection, this)
                drawOutline(o, Brush.verticalGradient(listOf(accent.copy(alpha = 0.16f), Neon.ink.copy(alpha = 0.85f))))
                drawOutline(o, accent.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
                cornerTicks(accent, 8.dp, 2.dp)
            }
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(idLabel(st.id) + "  ·  ", Neon.gold)
            FitText(st.tier.title, Type.micro, tierColor(st.tier), Modifier.weight(1f), title = false, letterSpacing = 3.sp, glow = 0f,
                alignment = Alignment.CenterStart)
            val tag = when {
                st.cleared -> "CLEARED"
                st.failed -> "BUSTED"
                st.clearedBefore -> "CLEARED BEFORE"
                else -> null
            }
            if (st.cleared) Box(Modifier.padding(end = Space.xxs).size(14.dp).drawBehind { check(ClearedGreen) })
            if (tag != null) {
                FitText(tag, Type.micro, if (st.failed) Neon.blood else ClearedGreen, Modifier.widthIn(max = 140.dp), title = false,
                    letterSpacing = 3.sp, glow = 0f, alignment = Alignment.CenterEnd)
            }
        }
        FitText(st.name, if (big) Type.headline else Type.title, Color.White, Modifier.fillMaxWidth(), letterSpacing = 2.sp,
            glow = 0.35f, alignment = Alignment.CenterStart)
        NeonText(st.goal, size = Type.small, color = Neon.soft, glow = 0f)
        GoalBar(st.fraction, st.cleared, Modifier.padding(top = Space.xxs), failed = st.failed)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FitText(st.hud, Type.small, accent, Modifier.weight(1f), title = false, letterSpacing = 1.5.sp, glow = 0.3f,
                alignment = Alignment.CenterStart)
            if (st.best != null && !st.cleared) {
                FitText("BEST " + st.best, Type.micro, Neon.dim, Modifier.weight(1f), title = false, letterSpacing = 1.5.sp, glow = 0f,
                    alignment = Alignment.CenterEnd)
            }
        }
    }
}

/**
 * One challenge on the board, compact: the tier stripe, number, name and goal, a hero badge,
 * and a check or a sliver of best progress.
 */
@Composable
fun ChallengeRow(c: Challenge, log: ChallengeLog, modifier: Modifier = Modifier, pick: Hero = Hero.BULL, onClick: () -> Unit) {
    val tc = tierColor(c.tier)
    val goal = c.goalText(c.heroFor(pick))
    val cleared = log.isCleared(c.id)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { contentDescription = "${idLabel(c.id)} ${c.name}, ${c.tier.title}. $goal" + if (cleared) ". Cleared" else "" }
            .drawBehind {
                val o = Shapes.small.createOutline(size, layoutDirection, this)
                drawOutline(o, if (pressed) tc.copy(alpha = 0.18f) else Neon.ink.copy(alpha = 0.78f))
                drawOutline(o, (if (cleared) ClearedGreen else tc).copy(alpha = if (cleared) 0.35f else 0.28f), style = Stroke(1.dp.toPx()))
                // the tier stripe
                drawRect(tc, Offset(0f, size.height * 0.2f), androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height * 0.6f))
            }
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .padding(start = Space.s, end = Space.s, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(52.dp)) {
            FitText(idLabel(c.id), Type.micro, Neon.dim, Modifier.fillMaxWidth(), title = false, letterSpacing = 1.sp, glow = 0f,
                alignment = Alignment.CenterStart)
            FitText(c.tier.title, 9.sp, tc, Modifier.fillMaxWidth(), title = false, letterSpacing = 1.sp, glow = 0f,
                alignment = Alignment.CenterStart)
        }
        Column(Modifier.weight(1f).padding(horizontal = Space.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FitText(c.name, Type.body, if (cleared) Neon.soft else Color.White, Modifier.fillMaxWidth(), letterSpacing = 1.5.sp,
                glow = 0f, alignment = Alignment.CenterStart)
            NeonText(goal, size = Type.small, color = Neon.dim, glow = 0f, maxLines = 1)
        }
        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val h = c.hero
            if (h != null) FitText(h.title, 9.sp, h.tint, Modifier.fillMaxWidth(), title = false, letterSpacing = 1.sp, glow = 0.4f,
                alignment = Alignment.CenterEnd)
            for (r in c.rules.take(if (h != null) 1 else 2)) {
                FitText(shortRule(r), 9.sp, Neon.gold.copy(alpha = 0.8f), Modifier.fillMaxWidth(), title = false, letterSpacing = 1.sp,
                    glow = 0f, alignment = Alignment.CenterEnd)
            }
            when {
                cleared -> Box(Modifier.size(16.dp).drawBehind { check(ClearedGreen) })
                log.best(c.id) > 0 -> GoalBar(log.best(c.id).toFloat() / c.target.coerceAtLeast(1), false, Modifier.width(44.dp))
            }
        }
    }
}

/**
 * The game over's ALSO CLEARED: the other challenges this run met on the side (the only place
 * they show: nothing pops up mid-run), the first couple by name and "+N" after. Tapping it
 * opens the board.
 */
@Composable
fun AlsoClearedCard(items: List<SideClear>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (items.isEmpty()) return
    val c = ClearedGreen
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shown = items.take(ALSO_SHOWN)
    val more = items.size - shown.size
    Column(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Also cleared ${items.size}: " + shown.joinToString(", ") { it.name } +
                    (if (more > 0) " and $more more" else "") + ". Opens the challenges board"
            }
            .drawBehind {
                val o = Shapes.small.createOutline(size, layoutDirection, this)
                drawOutline(o, Brush.verticalGradient(listOf(c.copy(alpha = if (pressed) 0.2f else 0.1f), Neon.ink.copy(alpha = 0.85f))))
                drawOutline(o, c.copy(alpha = 0.4f), style = Stroke(1.dp.toPx()))
            }
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(end = Space.xs).size(14.dp).drawBehind { check(c) })
            Kicker(if (items.size == 1) "ALSO CLEARED" else "ALSO CLEARED  ·  ${items.size}", c, Modifier.weight(1f))
            Box(Modifier.size(9.dp, 14.dp).drawBehind { chevronRight(c.copy(alpha = 0.7f)) })
        }
        // One line, so the run's own buttons stay on screen: the first few names, then a count.
        val line = shown.joinToString("  ·  ") { it.name } + if (more > 0) "  +$more" else ""
        FitText(line, Type.body, Color.White, Modifier.fillMaxWidth(), letterSpacing = 1.5.sp, glow = 0.2f,
            alignment = Alignment.CenterStart)
    }
}

private const val ALSO_SHOWN = 2
