package com.bradflaugher.aboutthataction.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.bradflaugher.aboutthataction.AndroidGfx
import com.bradflaugher.aboutthataction.Records
import com.bradflaugher.aboutthataction.SeedMode
import com.bradflaugher.aboutthataction.Settings
import com.bradflaugher.aboutthataction.ChallengeLog
import com.bradflaugher.aboutthataction.engine.Autopilot
import com.bradflaugher.aboutthataction.engine.Challenge
import com.bradflaugher.aboutthataction.engine.Challenges
import com.bradflaugher.aboutthataction.engine.Tier
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.RunConfig
import com.bradflaugher.aboutthataction.engine.World
import com.bradflaugher.aboutthataction.engine.Zone
import com.bradflaugher.aboutthataction.render.Renderer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the real Compose menus over a real game frame, headless, with
 * Robolectric native graphics. Only runs via `./gradlew :app:menuShots`
 * (excluded from the normal unit-test run), writing PNGs to app/build/menushots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class MenuShotsTest {
    private val outDir = File(System.getProperty("ata.menushots") ?: "build/menushots")

    private val devices = listOf(
        // -Pphone=<qualifiers> swaps the phone's size (e.g. w411dp-h731dp-420dpi, 9:16 for the Play listing).
        "phone" to (System.getProperty("ata.menushots.phone")?.takeIf { it.isNotBlank() } ?: "w411dp-h914dp-420dpi"), // 1080x2400
        "small" to "w360dp-h800dp-xhdpi", // 720x1600
        "big" to "w411dp-h914dp-560dpi", // 1440x3200 (approx)
    )

    private val insets = PaddingValues(top = 32.dp, bottom = 16.dp)
    private val records = Records(bestScore = 184_250, bestFloor = 67, runs = 12)

    /** A custom run on a friend's seed. */
    private val seeded = Settings(preset = null, seedMode = SeedMode.CUSTOM, seedText = "K7QM2XAB")

    /** A custom curve with a long LIVE FEED summary. */
    private val hellish = Difficulty(start = 1.2f, ramp = 2.4f, cap = 6f, hearts = 2, startFloor = 75)

    /** A sample daily challenge card (the card is display data; any challenge fills it). */
    /** A player a couple of weeks in: a few dozen cleared, some progress elsewhere. */
    private val day = Challenges.FIRST_DAY + 12
    private val log = ChallengeLog(
        cleared = (1..Challenges.size step 41).associateWith { day - 1 - it % 9 },
        best = (3..Challenges.size step 17).associateWith { 1 + it % 7 },
    )
    private val today = todaysChallenge(day, log)
    private val daily = dailyCard(today, day, log)
    private val caption = clearedCaption(log)

    /** A briefing with a set hero and a rule, and one with a hero pick and someone ruled out. */
    private val forced = Challenges.all.firstOrNull { it.hero != null && it.rules.isNotEmpty() && it.startFloor > 0 }
        ?: Challenges.all.first { it.hero != null && it.rules.isNotEmpty() }
    private val open = Challenges.all.firstOrNull { it.hero == null && it.heroes.size == 3 && it.rules.size == 2 }
        ?: Challenges.all.first { it.hero == null && it.heroes.size in 2..3 && it.rules.isNotEmpty() }

    /** One whose goal counts a hero perk, so it starts with it: the longest chip there is. */
    private val perky = Challenges.all.first { it.startPerk != null && it.chips().any { c -> c.length >= 20 } }

    private fun status(c: Challenge, progress: Int, cleared: Boolean, best: Int = 0) = ChallengeStatus(
        id = c.id, name = c.name, tier = c.tier, goal = c.goalText(c.heroFor(Hero.BULL)), hud = c.hudText(progress, c.heroFor(Hero.BULL)),
        fraction = (progress.toFloat() / c.target).coerceAtMost(1f), cleared = cleared, failed = false,
        best = if (best > 0) c.hudText(best, c.heroFor(Hero.BULL)) else null,
    )

    /** What an endless run ticked off on the side: five, so the card shows three and "+2 MORE". */
    private val alsoCleared = Challenges.all.filter { it.preset == com.bradflaugher.aboutthataction.engine.Difficulty.Preset.AGENT && it.startFloor == 0 }
        .take(5).map { SideClear(it.id, it.name, it.tier) }

    private val run = RunSummary(
        floor = 58, zone = Zone.METRO, score = 142_880, kills = 71, takedowns = 19, seconds = 734f,
        seedLabel = "K7QM 2XAB", newBestScore = false, newBestFloor = false,
        title = "CARDBOARD ENTHUSIAST", deathLine = "Steamed like a dumpling", quip = "Cardboard remains undefeated.",
        highlights = listOf("BEST COMBO" to "7x", "GHOST FLOORS" to "9", "BOX'D" to "12", "NIGHT NIGHTS" to "3", "CLOSE CALLS" to "14"),
    )

    @Test
    fun renderMenus() {
        val all = System.getProperty("ata.menushots.all") != null
        for ((device, qualifiers) in devices) {
            if (device != "phone" && !all) continue
            RuntimeEnvironment.setQualifiers(qualifiers)
            val s = Settings()
            shot("$device-title", 1800, world()) {
                TitleScreen(s, records, insets, {}, {}, {}, {}, {}, daily = daily, challengesCaption = caption)
            }
            shot("$device-title-intro", 450, world()) {
                TitleScreen(s, Records(), insets, {}, {}, {}, {}, {}, daily = daily, challengesCaption = caption)
            }
            shot("$device-title-welcome", 1800, world()) {
                TitleScreen(s, Records(), insets, {}, {}, {}, {}, {}, daily = daily, challengesCaption = caption, welcome = true)
            }
            shot("$device-help", 900, world()) {
                HelpScreen(insets, {}, {})
            }
            shot("$device-title-hawk", 1800, world(Hero.HAWK)) {
                TitleScreen(s.copy(hero = Hero.HAWK), records, insets, {}, {}, {}, {}, {}, daily = daily, challengesCaption = caption)
            }
            shot("$device-title-brutal", 1800, world()) {
                TitleScreen(s.copy(preset = Difficulty.Preset.BRUTAL), records, insets, {}, {}, {}, {}, {}, daily = daily, challengesCaption = caption)
            }
            shot("$device-title-custom", 1800, world(Hero.FOX)) {
                TitleScreen(s.copy(preset = null, custom = hellish, hero = Hero.FOX), records, insets, {}, {}, {}, {}, {})
            }
            shot("$device-title-daily", 1800, world()) {
                TitleScreen(s, records, insets, {}, {}, {}, {}, {}, daily = daily, challengesCaption = caption)
            }
            shot("$device-title-daily-cleared", 1800, world()) {
                TitleScreen(s, records, insets, {}, {}, {}, {}, {}, daily = daily.copy(cleared = true, clearedToday = true), challengesCaption = caption)
            }
            shot("$device-title-daily-best", 1800, world()) {
                TitleScreen(s, records, insets, {}, {}, {}, {}, {}, daily = daily.copy(best = "2/5", bestFraction = 0.4f), challengesCaption = caption)
            }
            shot("$device-custom", 900, world()) {
                CustomScreen(s.copy(preset = null, custom = Difficulty.Preset.BRUTAL.difficulty.copy(hearts = 3)), insets, {}, {}, {}, {})
            }
            shot("$device-custom-seed", 900, world()) {
                CustomScreen(seeded, insets, {}, {}, {}, {}, scrollToSeed = true)
            }
            shot("$device-title-seed", 1800, world()) {
                TitleScreen(seeded, records, insets, {}, {}, {}, {}, {})
            }
            shot("$device-custom-hell", 900, world(Hero.MONKEY)) {
                CustomScreen(s.copy(preset = null, custom = Difficulty.Preset.STRAIGHT_TO_HELL.difficulty, hero = Hero.MONKEY), insets, {}, {}, {}, {})
            }
            for (hero in Hero.entries) {
                shot("$device-heroes-${hero.name.lowercase()}", 1200, world(hero)) {
                    HeroPickerScreen(hero, insets, {}, {}, {})
                }
            }
            shot("$device-settings", 900, world()) {
                SettingsScreen(s, insets, {}, {})
            }

            shot("$device-board", 900, world()) {
                ChallengesScreen(log, today, daily, insets, {}, {})
            }
            shot("$device-board-filtered", 900, world()) {
                ChallengesScreen(log, today, daily, insets, {}, {}, initial = BoardFilter(Tier.LEGEND, Hero.FOX))
            }
            shot("$device-briefing", 900, world()) {
                BriefingScreen(open, Hero.MONKEY, log.withBest(open.id, open.target / 2), insets, onPickHero = {}, onPlay = {}, onBack = {})
            }
            shot("$device-briefing-forced", 900, world(forced.hero!!)) {
                BriefingScreen(forced, Hero.BULL, log.withClear(forced.id, day - 3), insets, onPickHero = {}, onPlay = {}, onBack = {})
            }
            shot("$device-briefing-daily", 900, world()) {
                BriefingScreen(today, Hero.BULL, log, insets, dailyNumber = Challenges.dailyNumber(day), onPickHero = {}, onPlay = {}, onBack = {})
            }
            shot("$device-briefing-perk", 900, world(perky.heroFor(Hero.BULL))) {
                BriefingScreen(perky, Hero.BULL, log, insets, onPickHero = {}, onPlay = {}, onBack = {})
            }
            shot("$device-title-perk", 1800, world(perky.heroFor(Hero.BULL))) {
                TitleScreen(s.copy(hero = perky.heroFor(Hero.BULL)), records, insets, {}, {}, {}, {}, {},
                    daily = dailyCard(perky, day, log, perky.heroFor(Hero.BULL)), challengesCaption = caption)
            }
            shot("$device-pause-challenge", 700, world()) {
                PauseScreen(s, "#0001", Hero.FOX, insets, {}, {}, {}, {}, challenge = status(open, open.target / 3, false, open.target / 2))
            }
            shot("$device-gameover-cleared", 3500, world()) {
                GameOverScreen(run.copy(seedLabel = idLabel(today.id), challenge = status(today, today.target + 2, true)), insets, {}, {}, {}, records)
            }
            shot("$device-gameover-challenge", 3500, world()) {
                GameOverScreen(run.copy(seedLabel = idLabel(open.id), challenge = status(open, open.target / 3, false, open.target / 2)), insets, {}, {}, {}, records)
            }
            shot("$device-gameover-also", 3500, world()) {
                GameOverScreen(run.copy(alsoCleared = alsoCleared), insets, {}, {}, {}, records)
            }
            shot("$device-gameover-also-challenge", 3500, world()) {
                GameOverScreen(run.copy(seedLabel = idLabel(today.id), challenge = status(today, today.target + 2, true), alsoCleared = alsoCleared.take(2)),
                    insets, {}, {}, {}, records)
            }
            shot("$device-pause", 700, world()) {
                PauseScreen(s, "K7QM 2XAB", Hero.FOX, insets, {}, {}, {}, {})
            }
            shot("$device-gameover", 3500, world()) {
                GameOverScreen(run, insets, {}, {}, {}, records)
            }
            shot("$device-gameover-mid", 900, world()) {
                GameOverScreen(run, insets, {}, {}, {})
            }
            shot("$device-gameover-tower", 3500, world()) {
                GameOverScreen(run.copy(floor = 13, zone = Zone.TOWER, score = 18_450, kills = 9, takedowns = 2, seconds = 96f), insets, {}, {}, {}, records)
            }
            shot("$device-gameover-best", 3500, world()) {
                GameOverScreen(run.copy(floor = 188, zone = Zone.HELL, newBestFloor = true, newBestScore = true), insets, {}, {}, {})
            }
        }
        // The smallest phone at a big font size: nothing may wrap or clip (the title scrolls).
        if (all || only != null) {
            RuntimeEnvironment.setQualifiers(devices.first { it.first == "small" }.second)
            RuntimeEnvironment.setFontScale(1.5f)
            val s = Settings()
            shot("font-title", 1800, world()) {
                TitleScreen(s.copy(preset = Difficulty.Preset.BRUTAL), records, insets, {}, {}, {}, {}, {}, daily = daily, challengesCaption = caption)
            }
            shot("font-title-custom", 1800, world()) {
                TitleScreen(s.copy(preset = null, custom = hellish), records, insets, {}, {}, {}, {}, {})
            }
            shot("font-custom", 900, world()) {
                CustomScreen(s.copy(preset = null), insets, {}, {}, {}, {})
            }
            shot("font-board", 900, world()) {
                ChallengesScreen(log, today, daily, insets, {}, {})
            }
            shot("font-briefing", 900, world()) {
                BriefingScreen(open, Hero.MONKEY, log, insets, onPickHero = {}, onPlay = {}, onBack = {})
            }
            shot("font-gameover-cleared", 3500, world()) {
                GameOverScreen(run.copy(challenge = status(today, today.target, true)), insets, {}, {}, {}, records)
            }
            shot("font-settings", 900, world()) {
                SettingsScreen(s, insets, {}, {})
            }
            shot("font-help", 900, world()) {
                HelpScreen(insets, {}, {})
            }
            shot("font-title-welcome", 1800, world()) {
                TitleScreen(s, Records(), insets, {}, {}, {}, {}, {}, daily = daily, challengesCaption = caption, welcome = true)
            }
            RuntimeEnvironment.setFontScale(1f)
        }
        // TEXT SIZE at LARGER on the smallest phone (and on top of a big system font): nothing may clip.
        if (all || only != null) {
            RuntimeEnvironment.setQualifiers(devices.first { it.first == "small" }.second)
            for (scale in listOf(1f, 1.3f)) {
                RuntimeEnvironment.setFontScale(scale)
                val tag = if (scale == 1f) "larger" else "larger-font"
                val s = Settings(textSize = com.bradflaugher.aboutthataction.TextSize.LARGER)
                val k = s.textSize.scale
                shot("textsize-$tag-title", 1800, world()) {
                    TextSizeScope(k) { TitleScreen(s, records, insets, {}, {}, {}, {}, {}, daily = daily.copy(best = "2/5", bestFraction = 0.4f), challengesCaption = caption) }
                }
                shot("textsize-$tag-settings", 900, world()) { TextSizeScope(k) { SettingsScreen(s, insets, {}, {}) } }
                shot("textsize-$tag-help", 900, world()) { TextSizeScope(k) { HelpScreen(insets, {}, {}) } }
                shot("textsize-$tag-board", 900, world()) { TextSizeScope(k) { ChallengesScreen(log, today, daily, insets, {}, {}) } }
                shot("textsize-$tag-pause", 700, world()) { TextSizeScope(k) { PauseScreen(s, "K7QM 2XAB", Hero.FOX, insets, {}, {}, {}, {}, onSkipTutorial = {}) } }
                shot("textsize-$tag-gameover", 3500, world()) { TextSizeScope(k) { GameOverScreen(run, insets, {}, {}, {}, records) } }
                shot("textsize-$tag-briefing", 900, world()) {
                    TextSizeScope(k) { BriefingScreen(open, Hero.MONKEY, log, insets, onPickHero = {}, onPlay = {}, onBack = {}) }
                }
            }
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    private fun world(hero: Hero = Hero.BULL): World {
        val seed = 7L
        val w = World(RunConfig(seed, Difficulty.Preset.AGENT.difficulty, hero = hero))
        val pilot = Autopilot(seed)
        repeat(120 * 9) {
            pilot.act(w, 1f / 120f)
            w.step(1f / 120f)
        }
        return w
    }

    private val only = System.getProperty("ata.menushots.only")?.takeIf { it.isNotBlank() }

    private fun shot(name: String, millis: Long, world: World, content: @Composable () -> Unit) {
        if (only != null && only.split(',').none { name.contains(it) }) return
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        // Robolectric's native hardware renderer is glacial; draw in software.
        // (Window.adjustLayoutParamsForSubWindow re-adds the flag from this field.)
        ReflectionHelpers.setField(android.view.Window::class.java, activity.window, "mHardwareAccelerated", false)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        controller.start().resume().visible()
        val dm = activity.resources.displayMetrics
        val bg = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
        val gfx = AndroidGfx(activity)
        gfx.begin(Canvas(bg))
        val density = dm.density
        val hud = !name.contains("title") && !name.contains("settings") && !name.contains("heroes") && !name.contains("custom") && !name.contains("board") && !name.contains("briefing") && !name.contains("help")
        Renderer().render(gfx, world, world.time, 32 * density, 16 * density, showHud = hud)
        // Drive Compose from our own frame clock. Robolectric's Choreographer hands
        // out frames without advancing time, so infinite animations never let idle end.
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(Dispatchers.Main + clock + Job())
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        val composeView = ComposeView(activity).apply {
            setParentCompositionContext(recomposer)
            setContent {
                Box(Modifier.fillMaxSize()) {
                    Image(bg.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    content()
                }
            }
        }
        activity.setContentView(composeView)
        val looper = shadowOf(Looper.getMainLooper())
        val view = activity.window.decorView
        looper.idle()
        // Software rasterizing every frame is slow; keep the window hidden while
        // time advances and draw it once at the end.
        view.visibility = View.INVISIBLE
        val started = System.nanoTime()
        var t = 0L
        while (t <= millis) {
            clock.sendFrame(t * 1_000_000L)
            looper.idle()
            t += 16
        }
        println("$name: advanced ${millis}ms in ${(System.nanoTime() - started) / 1_000_000}ms")
        view.measure(
            View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val out = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(out))
        outDir.mkdirs()
        val f = File(outDir, "$name.png")
        FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("wrote $f (${out.width}x${out.height})")
        // The README shows three of these; refresh them in docs/screenshots at half width.
        val docName = README_SHOTS[name]
        val docs = System.getProperty("ata.menushots.docs")
        if (docName != null && docs != null) {
            val w = 540
            val small = Bitmap.createScaledBitmap(out, w, out.height * w / out.width, true)
            val d = File(docs, "$docName.png")
            FileOutputStream(d).use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("wrote $d (${small.width}x${small.height})")
        }
        recomposer.cancel()
        scope.cancel()
        controller.pause().stop().destroy()
    }

    private companion object {
        /** Menu shots the README embeds, by render name. */
        val README_SHOTS = mapOf(
            "phone-title" to "menu-title",
            "phone-gameover-best" to "menu-gameover",
            "phone-settings" to "menu-settings",
            "phone-help" to "menu-help",
            "phone-heroes-bull" to "menu-heroes",
            "phone-custom" to "menu-custom",
            "phone-custom-seed" to "menu-seed",
            "phone-board" to "menu-challenges",
            "phone-briefing" to "menu-briefing",
            "phone-gameover-cleared" to "menu-cleared",
            "phone-gameover-also" to "menu-alsocleared",
        )
    }
}
