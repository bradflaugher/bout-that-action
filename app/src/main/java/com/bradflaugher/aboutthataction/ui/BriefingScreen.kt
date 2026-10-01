package com.bradflaugher.aboutthataction.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.ChallengeLog
import com.bradflaugher.aboutthataction.engine.Challenge
import com.bradflaugher.aboutthataction.engine.Hero

/**
 * A challenge's briefing: the job in big type, its rules spelled out, who can go (and who
 * can't, and why), where it starts, how you've done, and a sticky DROP IN.
 */
@Composable
fun BriefingScreen(
    c: Challenge,
    pick: Hero,
    log: ChallengeLog,
    insets: PaddingValues,
    /** Today's number when this is today's daily, for the kicker. */
    dailyNumber: Long? = null,
    onPickHero: (Hero) -> Unit,
    onPlay: () -> Unit,
    onBack: () -> Unit,
) {
    val tc = tierColor(c.tier)
    val hero = c.heroFor(pick)
    Box(Modifier.fillMaxSize().veil(alpha = 0.9f).padding(insets)) {
        Column(Modifier.fillMaxSize()) {
            MenuHeader(
                (if (dailyNumber != null) "DAILY #$dailyNumber  ·  " else "") + idLabel(c.id) + "  ·  " + c.tier.title,
                c.name, Neon.gold, onBack,
                line = listOf(Neon.gold.copy(alpha = 0.7f), tc.copy(alpha = 0.35f), Color.Transparent),
            )
            val groupMod = Modifier.fillMaxWidth().widthIn(max = 560.dp)
            // The briefing sits centred in the room above the footer (and scrolls when it can't).
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(Space.m),
                    verticalArrangement = Arrangement.spacedBy(Space.m, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Panel(groupMod.reveal(40, 12.dp, Motion.base + 80), accent = Neon.gold, spacing = Space.xs) {
                        SectionHeader("01", "THE JOB", Neon.gold)
                        NeonText(c.goalText(hero), size = Type.headline, color = Color.White, title = true, letterSpacing = 1.sp, glow = 0.35f,
                            modifier = Modifier.padding(vertical = Space.xxs))
                        Row(Modifier.fillMaxWidth().padding(top = Space.xxs), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            Fact("STARTS", if (c.startFloor == 0) "ROOFTOP" else c.startZone.title, Neon.zone(c.startZone), Modifier.weight(1f))
                            Fact("HEAT", presetLabel(c.preset), Neon.magenta, Modifier.weight(1f))
                            Fact("TIER", c.tier.title, tc, Modifier.weight(1f))
                        }
                        for (r in c.rules) RuleRow(r.title, r.blurb)
                        // A perk goal hands you the perk up front.
                        c.startPerk?.let { RuleRow(it.title, "Yours from the start. " + it.blurb, hero.tint) }
                    }
                    Panel(groupMod.reveal(90, 12.dp, Motion.base + 80), accent = Neon.cyan, spacing = Space.xs) {
                        SectionHeader("02", "YOUR FILE", Neon.cyan)
                        val day = log.clearedDay(c.id)
                        val best = log.best(c.id)
                        when {
                            day != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(18.dp).drawBehind { check(ClearedGreen) })
                                NeonText("  CLEARED " + shortDate(day), size = Type.body, color = ClearedGreen, letterSpacing = 2.sp, glow = 0.4f)
                            }
                            best > 0 -> Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                                NeonText("BEST  " + c.hudText(best, hero), size = Type.body, color = Neon.gold, letterSpacing = 1.5.sp, glow = 0.3f)
                                GoalBar(best.toFloat() / c.target.coerceAtLeast(1), false)
                            }
                            else -> NeonText("Not tried yet.", size = Type.body, color = Neon.soft, glow = 0f)
                        }
                        NeonText("Same building for everyone. Clearing it doesn't end the run: keep going for score.",
                            size = Type.small, color = Neon.dim, glow = 0f)
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .drawBehind { drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Neon.gold.copy(alpha = 0.45f), Color.Transparent))) },
            )
            // Sticky, like CUSTOM RUN's: who goes, and the way down.
            Column(
                Modifier.fillMaxWidth().drawBehind { drawRect(Neon.night.copy(alpha = 0.6f)) }
                    .padding(horizontal = Space.m).padding(top = Space.s, bottom = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(groupMod.reveal(140, 12.dp), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    val allowed = c.heroes
                    if (c.hero != null || allowed.size == 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Kicker("WHO GOES  ", Neon.dim)
                            NeonText(hero.title, size = Type.headline, color = hero.tint, title = true, letterSpacing = 3.sp, glow = 0.7f)
                            NeonText(if (c.hero != null) "  this one's theirs" else "  the only one who can", size = Type.small,
                                color = Neon.dim, glow = 0f, modifier = Modifier.weight(1f))
                        }
                    } else {
                        Kicker("WHO GOES", Neon.dim)
                        Segmented(allowed, hero, label = { it.title }, color = hero.tint) { onPickHero(it) }
                    }
                    if (c.hero == null) {
                        for (h in Hero.entries) {
                            val why = whyNot(c, h) ?: continue
                            // One plain sentence ("MONKEY is always GUNS HOT."), so it wraps cleanly at any font size.
                            NeonText(h.title + " " + why + ".", size = Type.small, color = h.tint.copy(alpha = 0.75f), glow = 0f)
                        }
                    }
                }
                NeonButton(
                    "DROP IN", Neon.magenta, groupMod.reveal(180, 12.dp).padding(top = Space.xxs),
                    style = ButtonStyle.PRIMARY, height = 72.dp, textSize = 26.sp,
                    trailing = { DropChevrons() },
                    onClick = onPlay,
                )
            }
        }
    }
}

/** A small labelled fact tile: "STARTS / DEEP METRO". */
@Composable
private fun Fact(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .drawBehind {
                val o = Shapes.chip.createOutline(size, layoutDirection, this)
                drawOutline(o, Neon.ink.copy(alpha = 0.8f))
                drawOutline(o, color.copy(alpha = 0.4f), style = Stroke(1.dp.toPx()))
            }
            .padding(horizontal = Space.xs, vertical = 6.dp),
    ) {
        Kicker(label, Neon.dim)
        FitText(value, Type.small, color, Modifier.fillMaxWidth(), title = false, letterSpacing = 1.sp, glow = 0.3f,
            alignment = Alignment.CenterStart)
    }
}

/** A rule spelled out: its chip, then what it means. */
@Composable
private fun RuleRow(title: String, blurb: String, color: Color = Neon.gold) {
    Row(Modifier.fillMaxWidth().padding(top = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(112.dp)) { RuleChip(title, color) }
        NeonText(blurb, size = Type.small, color = Neon.soft, glow = 0f, align = TextAlign.Start, modifier = Modifier.weight(1f))
    }
}
