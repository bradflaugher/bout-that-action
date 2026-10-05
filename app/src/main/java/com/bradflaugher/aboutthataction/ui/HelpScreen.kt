package com.bradflaugher.aboutthataction.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Common questions, answered short. Keep them true to the game (and to docs/CONTROLS.md). */
internal val FAQ = listOf(
    "How do I go down?" to "Only elevators go down. A landing with a ride down has cyan lights and ▼ chevrons: tap it to call the car, tap again to ride. No lift in this hallway? Take a green passage door to the next one.",
    "Why did the guard find my box?" to "He watched you hide, or you moved the box while he was looking. Sit still and a box is just a box.",
    "Why won't my gun fire?" to "You're in SILENT. Tap the mode button under pause for GUNS HOT. Even then it only fires at threats, never at a turned back.",
    "The STASH door says LOCKED." to "Someone in this hallway is hunting you. Lose them, or take them out, and it opens.",
    "What's the seed code for?" to "Every run has one. Share it from the game over screen; a friend PASTEs it on CUSTOM RUN and drops into the very same building.",
    "Do challenges expire?" to "Never. Every one is playable any day from the board, and any run that meets one ticks it off. The daily is just the same pick for everyone.",
    "Does it need the internet?" to "No. No network permission, no ads, no accounts. Your settings and records stay on this device.",
    "Too much flashing or shaking?" to "Turn on CALM SCREEN in settings (or on the pause menu): no screen shake, softer flashes, steady lights.",
    "Found a bug? Got an idea?" to "Tap FEEDBACK below. It opens a new GitHub issue in your browser.",
)

/**
 * HOW TO PLAY: the controls, a short FAQ, the tutorial again (a CHILL run from the roof with the
 * coach tips on), and the share, rate and feedback links. Reached from settings, the pause menu and
 * the title's first-time card.
 */
@Composable
fun HelpScreen(
    insets: PaddingValues,
    onBack: () -> Unit,
    onReplayTutorial: () -> Unit,
) {
    Box(Modifier.fillMaxSize().veil(alpha = 0.92f).padding(insets)) {
        Column(Modifier.fillMaxSize()) {
            MenuHeader("FIELD MANUAL", "HOW TO PLAY", Neon.lava, onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.m, vertical = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val groupMod = Modifier.fillMaxWidth().widthIn(max = 560.dp)
                Panel(groupMod.reveal(40, 12.dp, Motion.base + 80), accent = Neon.magenta) {
                    SectionHeader("01", "TUTORIAL", Neon.magenta)
                    NeonText(
                        "The roof billboard shows the moves, and coach tips pop up the first time each one would help.",
                        size = Type.small, color = Neon.soft, glow = 0f,
                    )
                    NeonButton(
                        "REPLAY TUTORIAL", Neon.magenta, Modifier.fillMaxWidth(), style = ButtonStyle.PRIMARY, height = 60.dp,
                        textSize = Type.title, caption = "CHILL · FROM THE ROOF · TIPS ON", onClick = onReplayTutorial,
                    )
                }
                Panel(groupMod.reveal(90, 12.dp, Motion.base + 80), accent = Neon.cyan) {
                    SectionHeader("02", "CONTROLS", Neon.cyan)
                    HowToPlay()
                }
                Panel(groupMod.reveal(140, 12.dp, Motion.base + 80), accent = Neon.gold) {
                    SectionHeader("03", "FAQ", Neon.gold)
                    Faq()
                }
                Panel(groupMod.reveal(190, 12.dp, Motion.base + 80), accent = Neon.hotPink) {
                    SectionHeader("04", "MORE", Neon.hotPink)
                    MoreLinks()
                }
                NeonButton("DONE", Neon.lava, groupMod, onClick = onBack)
                PrivacyLink()
                Spacer(Modifier.height(Space.xs))
            }
        }
    }
}

@Composable
private fun Faq() {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        for ((q, a) in FAQ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NeonText(q.uppercase(), size = Type.body, color = Neon.gold, glow = 0.25f, letterSpacing = 1.sp,
                    modifier = Modifier.semantics { heading() })
                NeonText(a, size = Type.small, color = Neon.soft, glow = 0f)
            }
        }
    }
}

/**
 * The title's one-time card for a first launch: the four gestures in one glance, GOT IT, and
 * HOW TO PLAY. It sits where the records go (there are none yet) and never blocks DROP IN.
 */
@Composable
internal fun WelcomeCard(modifier: Modifier = Modifier, onDone: () -> Unit, onHelp: () -> Unit) {
    Panel(modifier.fillMaxWidth(), accent = Neon.cyan, padding = Space.s, spacing = Space.xs) {
        NeonText("FIRST TIME HERE?", size = Type.title, color = Color.White, title = true, glow = 0.35f, letterSpacing = 2.sp,
            modifier = Modifier.semantics { heading() })
        for ((k, v) in WELCOME_ROWS) {
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                FitText(k, Type.body, Neon.cyan, Modifier.weight(0.45f), title = false, letterSpacing = 1.sp, glow = 0.3f,
                    alignment = Alignment.CenterStart)
                FitText(v, Type.body, Neon.text, Modifier.weight(0.55f), title = false, letterSpacing = 1.sp, glow = 0f,
                    alignment = Alignment.CenterStart)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.xxs), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            NeonButton("GOT IT", Neon.cyan, Modifier.weight(1f), height = Space.touch, textSize = Type.body, onClick = onDone)
            NeonButton("HOW TO PLAY", Neon.lava, Modifier.weight(1f), height = Space.touch, textSize = Type.body, onClick = onHelp)
        }
    }
}

private val WELCOME_ROWS = listOf(
    "DRAG ← →" to "RUN",
    "SWIPE ↑" to "JUMP",
    "SWIPE ↓" to "HIDE / BOX",
    "TAP" to "DOORS & LIFTS",
)
