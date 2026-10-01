package com.bradflaugher.aboutthataction.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bradflaugher.aboutthataction.ChallengeLog
import com.bradflaugher.aboutthataction.engine.Challenge
import com.bradflaugher.aboutthataction.engine.Challenges
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Tier
import kotlin.random.Random

/** The board's filters. Null means "any". */
data class BoardFilter(val tier: Tier? = null, val hero: Hero? = null, val openOnly: Boolean = false) {
    fun matches(c: Challenge, log: ChallengeLog): Boolean =
        (tier == null || c.tier == tier) &&
            (hero == null || c.hero == hero || (c.hero == null && c.allows(hero))) &&
            (!openOnly || !log.isCleared(c.id))
}

/**
 * Every challenge, any day: today's pinned on top, then tier and hero filters, NOT CLEARED and
 * SURPRISE ME, and the whole catalog as compact rows (lazily laid out, so 1,000+ rows scroll fine).
 */
@Composable
fun ChallengesScreen(
    log: ChallengeLog,
    daily: Challenge,
    dailyCard: DailyCard,
    insets: PaddingValues,
    onOpen: (Challenge) -> Unit,
    onBack: () -> Unit,
    initial: BoardFilter = BoardFilter(),
    /** The player's hero, so goals read the way they'd play them (BULL stomps). */
    pick: Hero = Hero.BULL,
) {
    var tier by rememberSaveable { mutableStateOf(initial.tier?.ordinal ?: -1) }
    var hero by rememberSaveable { mutableStateOf(initial.hero?.ordinal ?: -1) }
    var openOnly by rememberSaveable { mutableStateOf(initial.openOnly) }
    val filter = BoardFilter(Tier.entries.getOrNull(tier), Hero.entries.getOrNull(hero), openOnly)
    val shown = remember(filter, log) { Challenges.active.filter { filter.matches(it, log) } }
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize().veil(alpha = 0.9f).padding(insets)) {
        Column(Modifier.fillMaxSize()) {
            MenuHeader(
                clearedCaption(log), "CHALLENGES", Neon.gold, onBack,
                line = listOf(Neon.gold.copy(alpha = 0.7f), Neon.magenta.copy(alpha = 0.3f), Color.Transparent),
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val w = Modifier.fillMaxWidth().widthIn(max = 560.dp)
                item(key = "daily") { DailyChallengeCard(dailyCard, w.reveal(40, 12.dp)) { onOpen(daily) } }
                item(key = "filters") {
                    Column(w.padding(top = Space.xs, bottom = Space.xxs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Segmented(listOf<Tier?>(null) + Tier.entries, Tier.entries.getOrNull(tier), label = { it?.title ?: "ALL" },
                            color = Tier.entries.getOrNull(tier)?.let(::tierColor) ?: Neon.gold) { tier = it?.ordinal ?: -1 }
                        Segmented(listOf<Hero?>(null) + Hero.entries, Hero.entries.getOrNull(hero), label = { it?.title ?: "ANY" },
                            color = Hero.entries.getOrNull(hero)?.tint ?: Neon.gold) { hero = it?.ordinal ?: -1 }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            Box(Modifier.weight(1.1f)) { Toggle("Not cleared", "${grouped(shown.size.toLong())} shown", openOnly, Neon.gold) { openOnly = it } }
                            NeonButton("SURPRISE ME", Neon.gold, Modifier.weight(1f), height = 48.dp, textSize = Type.small) {
                                val pool = shown.filter { !log.isCleared(it.id) }.ifEmpty { shown }
                                if (pool.isNotEmpty()) onOpen(pool[Random.nextInt(pool.size)])
                            }
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "none") {
                        NeonText("Nothing left here. You cleared the lot!", size = Type.body, color = ClearedGreen, glow = 0.3f,
                            align = TextAlign.Center, modifier = w.padding(Space.l))
                    }
                }
                items(shown, key = { it.id }, contentType = { "row" }) { c -> ChallengeRow(c, log, w, filter.hero ?: pick) { onOpen(c) } }
            }
        }
    }
}
