package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.AndroidGfx
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Perk
import com.bradflaugher.aboutthataction.render.HeroPortrait
import com.bradflaugher.aboutthataction.render.HudIcons
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

internal val Hero.tint: Color get() = Color(color)

/** Each hero's three STASH-only perks, in declaration order. */
internal val Hero.perks: List<Perk> get() = Perk.entries.filter { it.hero == this }

// ------------------------------------------------------------------ figure

/**
 * The real in-game figure of [hero], standing on the bottom of this box and idling:
 * drawn through [HeroPortrait] on the native canvas, every frame. [height] is the
 * figure's share of the box height and [foot] where its feet land (0 top, 1 bottom).
 */
@Composable
fun HeroFigure(hero: Hero, modifier: Modifier = Modifier, height: Float = 0.62f, foot: Float = 0.9f, time: () -> Float) {
    val context = LocalContext.current
    val gfx = remember(context) { AndroidGfx(context) }
    Canvas(modifier.clipToBounds()) {
        val t = time()
        drawIntoCanvas { c ->
            gfx.begin(c.nativeCanvas)
            HeroPortrait.draw(gfx, hero, size.width / 2f, size.height * foot, size.height * height, t)
        }
    }
}

/** A perk's HUD icon, drawn by the same painter as the in-game cards. */
@Composable
private fun PerkIcon(perk: Perk, color: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val gfx = remember(context) { AndroidGfx(context) }
    Canvas(modifier) {
        drawIntoCanvas { c ->
            gfx.begin(c.nativeCanvas)
            HudIcons.perk(gfx, perk, size.width / 2f, size.height / 2f, size.minDimension * 0.62f, color.toArgb())
        }
    }
}

/**
 * The hero's stage: a glow from behind, a synthwave floor running toward you, a
 * spotlight pool at the feet and a slow hologram sweep. [footY] is where the feet land.
 */
private fun DrawScope.heroStage(c: Color, t: Float, footY: Float, sweep: Boolean = true) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Neon.night, lerp(c, Neon.night, 0.82f), Neon.night)))
    // back glow
    drawRect(
        Brush.radialGradient(
            listOf(c.copy(alpha = 0.38f), c.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(w / 2f, footY - h * 0.34f), radius = maxOf(w, h) * 0.52f,
        ),
    )
    // the floor: a horizon, rays to a vanishing point, rungs rolling toward you
    val horizon = footY - h * 0.05f
    val floorH = h - horizon
    if (floorH > 2f) {
        drawRect(
            Brush.verticalGradient(listOf(c.copy(alpha = 0.16f), Color.Transparent), startY = horizon, endY = h),
            topLeft = Offset(0f, horizon), size = Size(w, floorH),
        )
        val line = c.copy(alpha = 0.34f)
        val sw = 1.dp.toPx()
        drawLine(c.copy(alpha = 0.7f), Offset(0f, horizon), Offset(w, horizon), sw)
        val vx = w / 2f
        for (i in -7..7) {
            val x = vx + i * w * 0.16f
            drawLine(line, Offset(vx + i * w * 0.02f, horizon), Offset(x + i * w * 0.14f, h), sw)
        }
        val roll = (t * 0.45f) % 1f
        for (i in 0 until 6) {
            val k = (i + roll) / 6f
            val y = horizon + floorH * k * k
            drawLine(c.copy(alpha = 0.10f + 0.3f * k), Offset(0f, y), Offset(w, y), sw)
        }
    }
    // spotlight pool
    val poolW = w * 0.62f
    drawOval(
        Brush.radialGradient(listOf(c.copy(alpha = 0.45f), Color.Transparent), center = Offset(w / 2f, footY), radius = poolW / 2f),
        topLeft = Offset(w / 2f - poolW / 2f, footY - h * 0.045f), size = Size(poolW, h * 0.09f),
    )
    if (sweep) {
        val y = h * (1.2f - ((t * 0.22f) % 1f) * 1.4f)
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.14f), Color.Transparent), startY = y - h * 0.08f, endY = y + h * 0.08f),
            topLeft = Offset(0f, y - h * 0.08f), size = Size(w, h * 0.16f), blendMode = BlendMode.Plus,
        )
    }
}

// ------------------------------------------------------------------ title bar

/**
 * The title screen's "who's dropping in" bar: the hero standing in a tiny stage,
 * their name in their colour, and a tap to change.
 */
@Composable
fun HeroBar(hero: Hero, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = hero.tint
    val clock = rememberClock()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            // Grows with big font settings instead of clipping the name.
            .heightIn(min = 64.dp)
            .height(IntrinsicSize.Min)
            .pressScale(source, 0.97f)
            .semantics { contentDescription = "Playing as ${hero.title}. ${hero.tagline} Change hero" }
            .drawBehind {
                val o = Shapes.button.createOutline(size, layoutDirection, this)
                drawOutline(o, Brush.horizontalGradient(listOf(c.copy(alpha = if (pressed) 0.34f else 0.2f), Neon.ink.copy(alpha = 0.82f))))
                drawOutline(o, c.copy(alpha = 0.75f), style = Stroke(1.5.dp.toPx()))
                cornerTicks(c, 7.dp, 2.dp)
            }
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .padding(start = 6.dp, end = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(vertical = 6.dp)
                .width(52.dp)
                .fillMaxHeight()
                .clip(Shapes.small)
                .drawBehind { heroStage(c, clock.value, size.height * 0.92f, sweep = false) },
        ) {
            HeroFigure(hero, Modifier.fillMaxSize(), height = 0.84f, foot = 0.92f) { clock.value }
        }
        Column(Modifier.weight(1f).padding(start = Space.s)) {
            FitText("PLAYING AS", Type.micro, Neon.dim, Modifier.fillMaxWidth(), title = false, letterSpacing = 3.sp, glow = 0f,
                alignment = Alignment.CenterStart)
            FitText(hero.title, Type.headline, c, Modifier.fillMaxWidth(), letterSpacing = 3.sp, glow = 0.7f, alignment = Alignment.CenterStart)
        }
        Column(horizontalAlignment = Alignment.End) {
            Kicker("CHANGE", c.copy(alpha = 0.9f), align = TextAlign.End)
            Kicker("HERO", c.copy(alpha = 0.9f), align = TextAlign.End)
        }
        Box(
            Modifier.padding(start = Space.xs).size(14.dp, 24.dp).drawBehind {
                val sw = 2.5.dp.toPx()
                drawLine(c, Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.5f), sw)
                drawLine(c, Offset(size.width * 0.8f, size.height * 0.5f), Offset(size.width * 0.2f, size.height * 0.8f), sw)
            },
        )
    }
}

// ------------------------------------------------------------------ picker

/**
 * Pick who drops in. A roster strip on top, then one big swipeable card per hero:
 * the figure on its own stage, name, trait, a joke and their three STASH-only perks.
 * The pick sticks as soon as a card settles ([onPick]); [onPlay] drops straight in.
 */
@Composable
fun HeroPickerScreen(
    hero: Hero,
    insets: PaddingValues,
    onPick: (Hero) -> Unit,
    onPlay: () -> Unit,
    onBack: () -> Unit,
) {
    val heroes = Hero.entries
    val pager = rememberPagerState(initialPage = hero.ordinal) { heroes.size }
    val scope = rememberCoroutineScope()
    val clock = rememberClock()
    val pick by rememberUpdatedState(onPick)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { pick(heroes[it]) } }
    val shown = heroes[pager.currentPage]
    val accent by animateColorAsState(shown.tint, tween(Motion.base), label = "heroAccent")

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .veil(alpha = 0.86f)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.4f), radius = size.maxDimension * 0.6f),
                )
            }
            .scanlines(0.06f)
            .padding(insets),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Short phones trade the flavor line and some type size for a bigger stage.
        val compact = maxHeight < 760.dp
        Column(
            Modifier.fillMaxSize().widthIn(max = 480.dp).padding(horizontal = Space.m, vertical = Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            // header
            Row(Modifier.fillMaxWidth().reveal(0, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton("Back", Neon.cyan, onBack) { chevronLeft(it) }
                Column(Modifier.weight(1f).padding(start = Space.s)) {
                    Kicker("WHO'S DROPPING IN?", accent.copy(alpha = 0.85f))
                    FitText("PICK A HERO", 26.sp, Color.White, Modifier.fillMaxWidth(), glow = 0.35f, alignment = Alignment.CenterStart)
                }
                NeonText(String.format(Locale.US, "%02d", pager.currentPage + 1), size = Type.headline, color = accent, title = true, glow = 0.6f)
                NeonText("/%02d".format(Locale.US, heroes.size), size = Type.small, color = Neon.dim, glow = 0f,
                    modifier = Modifier.padding(start = 2.dp, top = 6.dp))
            }

            // roster
            Row(Modifier.fillMaxWidth().reveal(80, 10.dp), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                heroes.forEachIndexed { i, h ->
                    RosterTile(h, selected = i == pager.currentPage, Modifier.weight(1f).height(if (compact) 60.dp else 76.dp), clock::value) {
                        scope.launch { pager.animateScrollToPage(i) }
                    }
                }
            }

            // the cards
            HorizontalPager(
                pager,
                Modifier.fillMaxWidth().weight(1f).reveal(160, 18.dp),
                pageSpacing = Space.m,
                beyondViewportPageCount = 1,
                key = { heroes[it].name },
            ) { page ->
                HeroCard(
                    heroes[page],
                    Modifier.fillMaxSize().graphicsLayer {
                        // Read in the layer, not in composition: it changes every frame of a swipe.
                        val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                        val k = abs(off).coerceAtMost(1f)
                        alpha = 1f - 0.55f * k
                        scaleX = 1f - 0.06f * k
                        scaleY = 1f - 0.06f * k
                    },
                    clock::value,
                    compact,
                    onPrev = if (page > 0) ({ scope.launch { pager.animateScrollToPage(page - 1) } }) else null,
                    onNext = if (page < heroes.lastIndex) ({ scope.launch { pager.animateScrollToPage(page + 1) } }) else null,
                )
            }

            NeonButton(
                "PLAY AS ${shown.title}", accent, Modifier.fillMaxWidth().reveal(240, 16.dp),
                style = ButtonStyle.PRIMARY, height = 64.dp, textSize = 22.sp,
                trailing = { DropChevrons() },
                onClick = {
                    // A tap mid-swipe plays the hero on screen, not the one it came from.
                    if (pager.currentPage != pager.settledPage) pick(shown)
                    onPlay()
                },
            )
        }
    }
}

@Composable
private fun RosterTile(hero: Hero, selected: Boolean, modifier: Modifier, time: () -> Float, onClick: () -> Unit) {
    val c = hero.tint
    val on by animateFloatAsState(if (selected) 1f else 0f, tween(Motion.base, easing = Motion.out), label = "rosterOn")
    Column(
        modifier
            .semantics { contentDescription = "${hero.title}: ${hero.tagline}" }
            .selectable(selected, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick)
            .drawBehind {
                val o = Shapes.small.createOutline(size, layoutDirection, this)
                if (on > 0f) neonGlow(Shapes.small, c, 0.7f * on, 6.dp)
                drawOutline(o, Brush.verticalGradient(listOf(lerp(Neon.ink, c, 0.08f + 0.22f * on), Neon.ink.copy(alpha = 0.9f))))
                drawOutline(o, lerp(Neon.line, c, on), style = Stroke((1f + 0.5f * on).dp.toPx()))
            }
            .clip(Shapes.small)
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeroFigure(
            hero,
            Modifier.fillMaxWidth().weight(1f).graphicsLayer { alpha = 0.5f + 0.5f * on },
            height = 0.86f, foot = 0.97f, time = time,
        )
        // Shrinks to the tile if a name is ever too long for a narrow phone.
        FitText(hero.title, Type.micro, lerp(Neon.dim, c, on), Modifier.fillMaxWidth().padding(horizontal = 4.dp), letterSpacing = 1.5.sp, glow = 0.5f * on)
    }
}

@Composable
private fun HeroCard(hero: Hero, modifier: Modifier, time: () -> Float, compact: Boolean, onPrev: (() -> Unit)?, onNext: (() -> Unit)?) {
    val c = hero.tint
    Column(
        modifier.drawBehind {
            val o = Shapes.panel.createOutline(size, layoutDirection, this)
            drawOutline(o, Brush.verticalGradient(listOf(Neon.panelHi, Neon.panel)))
            drawOutline(o, Brush.verticalGradient(listOf(c.copy(alpha = 0.9f), c.copy(alpha = 0.15f), c.copy(alpha = 0.4f))), style = Stroke(1.dp.toPx()))
            cornerTicks(c, 14.dp, 2.dp)
        },
    ) {
        // the stage
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 120.dp)
                .padding(1.dp)
                .clip(CutCornerShape(topStart = 20.dp))
                .drawBehind { heroStage(c, time(), size.height * 0.9f) }
                .semantics { contentDescription = "${hero.title}, ${hero.tagline}" },
        ) {
            // the name, huge and faint, behind the figure
            FitText(hero.title, 140.sp, c.copy(alpha = 0.13f), Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = Space.l),
                letterSpacing = 0.sp, glow = 0f)
            HeroFigure(hero, Modifier.fillMaxSize(), height = if (compact) 0.7f else 0.64f, foot = 0.9f, time = time)
            Kicker(String.format(Locale.US, "No. %02d", hero.ordinal + 1), c.copy(alpha = 0.8f), Modifier.align(Alignment.TopStart).padding(Space.m))
            Kicker("ON DUTY", Neon.dim, Modifier.align(Alignment.TopEnd).padding(Space.m), TextAlign.End)
            onPrev?.let { StageArrow(Alignment.CenterStart, "Previous hero", c, left = true, onClick = it) }
            onNext?.let { StageArrow(Alignment.CenterEnd, "Next hero", c, left = false, onClick = it) }
        }

        // who they are, at the tallest hero's height: every card's stage is the same size
        Tallest(hero.ordinal, Modifier.fillMaxWidth()) {
            for (h in Hero.entries) HeroInfo(h, compact, if (h == hero) Modifier else Modifier.clearAndSetSemantics {})
        }
    }
}

/** The card's text: name, tagline, trait, the joke and the three hero-only perks. */
@Composable
private fun HeroInfo(hero: Hero, compact: Boolean, modifier: Modifier = Modifier) {
    val c = hero.tint
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.m).padding(top = Space.xxs, bottom = if (compact) Space.s else Space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        FitText(hero.title, if (compact) 30.sp else 44.sp, c, Modifier.fillMaxWidth(), letterSpacing = 6.sp, glow = 0.9f)
        NeonText(hero.tagline.uppercase(Locale.US), size = Type.small, color = Neon.soft, letterSpacing = 2.sp, glow = 0f,
            align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Trait(hero, Modifier.padding(top = if (compact) 0.dp else Space.xs))
        if (!compact) {
            NeonText("\u201C${hero.flavor}\u201D", size = Type.small, color = Neon.dim, glow = 0f, align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Space.xxs))
        }
        SectionHeader("//", "HERO-ONLY PERKS", c)
        for (p in hero.perks) PerkRow(p, c)
    }
}

/**
 * Measures every child but places only child [shown], at the tallest child's height. The
 * others are never drawn; they only set the size.
 */
@Composable
private fun Tallest(shown: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val h = placeables.maxOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, h) { placeables[shown].place(0, 0) }
    }
}

@Composable
private fun BoxScope.StageArrow(align: Alignment, description: String, c: Color, left: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .align(align)
            .size(Space.touch, 72.dp)
            .semantics { contentDescription = description }
            .clickable(remember { MutableInteractionSource() }, null, role = Role.Button, onClick = onClick)
            .drawBehind {
                val s = size.width * 0.16f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val d = if (left) 1f else -1f
                val sw = 2.5.dp.toPx()
                val col = c.copy(alpha = 0.7f)
                drawLine(col, Offset(cx + s * 0.5f * d, cy - s), Offset(cx - s * 0.5f * d, cy), sw)
                drawLine(col, Offset(cx - s * 0.5f * d, cy), Offset(cx + s * 0.5f * d, cy + s), sw)
            },
    )
}

@Composable
private fun Trait(hero: Hero, modifier: Modifier = Modifier) {
    val c = hero.tint
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val o = Shapes.small.createOutline(size, layoutDirection, this)
                drawOutline(o, c.copy(alpha = 0.12f))
                drawOutline(o, c.copy(alpha = 0.45f), style = Stroke(1.dp.toPx()))
            }
            .padding(horizontal = Space.s, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Kicker("TRAIT", c)
        NeonText(hero.trait, size = Type.body, color = Neon.text, glow = 0f, modifier = Modifier.padding(start = Space.s))
    }
}

@Composable
private fun PerkRow(perk: Perk, c: Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.xxs).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PerkChip(perk, c, 36.dp)
        Column(Modifier.weight(1f).padding(start = Space.s)) {
            NeonText(perk.title, size = Type.body, color = c, title = true, letterSpacing = 1.5.sp, glow = 0.4f, maxLines = 1)
            NeonText(perk.blurb, size = Type.small, color = Neon.soft, glow = 0f)
        }
    }
}

@Composable
private fun PerkChip(perk: Perk, c: Color, size: Dp) {
    Box(
        Modifier.size(size).drawBehind {
            val o = Shapes.chip.createOutline(this.size, layoutDirection, this)
            drawOutline(o, Brush.verticalGradient(listOf(c.copy(alpha = 0.26f), Neon.ink)))
            drawOutline(o, c.copy(alpha = 0.7f), style = Stroke(1.dp.toPx()))
        },
    ) {
        PerkIcon(perk, c, Modifier.fillMaxSize())
    }
}
