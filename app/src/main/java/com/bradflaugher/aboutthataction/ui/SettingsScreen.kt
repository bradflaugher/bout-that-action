package com.bradflaugher.aboutthataction.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.Settings
import com.bradflaugher.aboutthataction.engine.FloorLabel
import com.bradflaugher.aboutthataction.engine.Zone

@Composable
fun SettingsScreen(
    settings: Settings,
    insets: PaddingValues,
    onChange: (Settings) -> Unit,
    onBack: () -> Unit,
    onHelp: () -> Unit = {},
    /** The JUKEBOX: zones whose track you've unlocked (reached), the one playing, and play one (null: the title theme). */
    jukebox: Set<Zone> = emptySet(),
    playing: Zone? = null,
    onJukebox: (Zone?) -> Unit = {},
) {
    val s = settings
    Box(Modifier.fillMaxSize().veil(alpha = 0.9f).padding(insets)) {
        Column(Modifier.fillMaxSize()) {
            // Header bar stays put while the groups scroll under it.
            MenuHeader("LOADOUT", "SETTINGS", Neon.cyan, onBack)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.m, vertical = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val groupMod = Modifier.fillMaxWidth().widthIn(max = 560.dp)
                // (The difficulty curve and the seed live on the CUSTOM RUN screen, off the title.)
                Panel(groupMod.reveal(40, 12.dp, Motion.base + 80), accent = Neon.cyan) {
                    AudioAndControls(s, onChange, audioIndex = "01", controlsIndex = "02")
                }
                Panel(groupMod.reveal(70, 12.dp, Motion.base + 80), accent = Neon.magenta) {
                    Jukebox(jukebox, playing, onJukebox)
                }
                Panel(groupMod.reveal(90, 12.dp, Motion.base + 80), accent = Neon.lava) {
                    SectionHeader("04", "HELP & MORE", Neon.lava)
                    NeonButton("HOW TO PLAY", Neon.lava, Modifier.fillMaxWidth(), caption = "CONTROLS · FAQ · TUTORIAL", onClick = onHelp)
                    MoreLinks()
                }
                NeonButton("DONE", Neon.cyan, groupMod, onClick = onBack)
                PrivacyLink()
                Spacer(Modifier.height(Space.xs))
            }
        }
    }
}

/**
 * The JUKEBOX: every zone's track, once you've been there (the deeper zones stay ??? until you
 * reach them). Tap one to hear it here, played the way your hero plays it; tap it again, or
 * leave settings, for the title theme.
 */
@Composable
private fun Jukebox(unlocked: Set<Zone>, playing: Zone?, onPlay: (Zone?) -> Unit) {
    SectionHeader("03", "JUKEBOX", Neon.magenta)
    NeonText("Every zone has its own track. Reach a zone to unlock it here.", size = Type.small, color = Neon.soft, glow = 0f)
    val zones = Zone.entries.filter { it != Zone.ROOFTOP }
    for (row in zones.chunked(2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            for (z in row) {
                val open = z in unlocked
                val on = z == playing
                NeonButton(
                    if (open) z.title else "???", if (open) Neon.zone(z) else Neon.faint,
                    Modifier.weight(1f).semantics {
                        contentDescription = if (open) "Play the ${z.title} track" + (if (on) ", playing" else "") else "Locked: reach ${FloorLabel.of(z.startFloor)}"
                    },
                    style = if (on) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, height = 52.dp, textSize = Type.body,
                    caption = if (on) "PLAYING · TAP TO STOP" else if (open) z.subtitle.substringBefore(" ·").uppercase() else "REACH " + FloorLabel.of(z.startFloor),
                    onClick = { if (open) onPlay(if (on) null else z) },
                )
            }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

/**
 * SHARE, RATE and FEEDBACK, side by side: one hands the Play link to the share sheet, one opens the
 * game's Play page, one opens a GitHub issue. Only ever on a tap; nothing here prompts or reminds.
 */
@Composable
internal fun MoreLinks() {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        NeonButton("SHARE", Neon.gold, Modifier.weight(1f), caption = "THE GAME", onClick = { shareApp(context) })
        NeonButton(
            "RATE", Neon.cyan, Modifier.weight(1f).semantics { contentDescription = RATE_DESCRIPTION },
            caption = "ON PLAY", onClick = { rateApp(context) },
        )
        NeonButton("FEEDBACK", Neon.hotPink, Modifier.weight(1f), caption = "BUGS · IDEAS", onClick = { sendFeedback(context) })
    }
}

/** The Play privacy policy, opened in the browser (the game itself has no network access). */
@Composable
internal fun PrivacyLink() {
    val context = LocalContext.current
    Box(
        Modifier
            .heightIn(min = Space.touch)
            .clickable(role = Role.Button) { openUrl(context, PRIVACY_POLICY_URL) }
            .padding(horizontal = Space.s),
        contentAlignment = Alignment.Center,
    ) {
        NeonText("PRIVACY POLICY", size = Type.micro, color = Neon.soft, glow = 0f, letterSpacing = 2.sp)
    }
}

@Composable
fun HowToPlay() {
    // Same order as the rooftop billboard: the gestures, then the two buttons, then the tricks.
    val rows = listOf(
        "DRAG ← →" to "Run. Hold to keep going; nudge back to turn. Lift to stop.",
        "SWIPE ↑" to "Jump. Clears low shots. Land on a head to daze him (BULL flattens him; drones break). MONKEY just hops off.",
        "SWIPE ↓" to "Hide in a doorway or pop the cardboard box. Swipe ↓ again to stand up. Hide before they see you: a guard who watches you go comes and finds you.",
        "TAP" to "Use what you're next to: a green passage door, a gold STASH door (locked while anyone's hunting you), an elevator (tap a landing to call the car).",
        "MODE" to "The button under pause. GUNS HOT auto-fires at threats; SILENT never fires and quiet kills score double. Flip it any time; it sticks between runs. MONKEY is always GUNS HOT.",
        "GRENADE" to "The lime button under the mode button throws one, in either mode. It shows how many you have.",
        "WALK INTO" to "An enemy's back to take him down instantly. Face to face only if he's napping, dazed or walks into your box (FOX, and BULL with STIFF ARM, anytime). Heavies only from behind (or napping). MONKEY has no takedowns: his gun shoots turned backs instead.",
        "THE BOX" to "Move it while a guard's looking and he comes over to check. Let him. (Heavies and ninjas kick it.)",
        "JUMP + TAP" to "Swat out the lamp overhead. The crash lures guards over to look; anyone right under it is out.",
        "ARRIVING" to "In SILENT you step into each new hallway hidden in the doorway. Tap or swipe ↑ to step out.",
        "GHOST" to "Leave a floor without anyone spotting you for a bonus. Double in SILENT.",
        "HEROES" to "Four of them, all free. Tap the hero bar on the title to swap. Each has a trait and three perks of their own.",
        "DOWN" to "Only elevators go down, and only some hallways have one. Find it. Keep going, or take a break. It'll wait.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        for ((k, v) in rows) {
            // One stop for a screen reader: the gesture, then what it does.
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .width(104.dp)
                        .drawBehind {
                            val o = Shapes.chip.createOutline(size, layoutDirection, this)
                            drawOutline(o, Neon.cyan.copy(alpha = 0.1f))
                            drawOutline(o, Neon.cyan.copy(alpha = 0.45f), style = Stroke(1.dp.toPx()))
                        }
                        .padding(horizontal = Space.xs, vertical = Space.xxs),
                    contentAlignment = Alignment.Center,
                ) {
                    // Shrinks rather than clipping at big font sizes.
                    FitText(k, Type.micro, Neon.cyan, letterSpacing = 1.sp, glow = 0.3f, title = false)
                }
                NeonText(v, size = Type.small, color = Neon.soft, glow = 0f, modifier = Modifier.padding(start = Space.s).weight(1f))
            }
        }
    }
}
