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
import com.bradflaugher.aboutthataction.engine.Autopilot
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
        "phone" to "w411dp-h914dp-420dpi", // 1080x2400
        "small" to "w360dp-h800dp-xhdpi", // 720x1600
        "big" to "w411dp-h914dp-560dpi", // 1440x3200 (approx)
    )

    private val insets = PaddingValues(top = 32.dp, bottom = 16.dp)
    private val records = Records(bestScore = 184_250, bestFloor = 67, runs = 12)

    private val run = RunSummary(
        floor = 58, zone = Zone.METRO, score = 142_880, kills = 71, takedowns = 19, seconds = 734f,
        seedLabel = "48213377", newBestScore = false, newBestFloor = false,
        title = "CARDBOARD ENTHUSIAST", deathLine = "Steamed like a dumpling", quip = "I'm just 'bout that action, boss.",
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
                TitleScreen(s, records, insets, {}, {}, {}, {}, {})
            }
            shot("$device-title-intro", 450, world()) {
                TitleScreen(s, Records(), insets, {}, {}, {}, {}, {})
            }
            shot("$device-title-viper", 1800, world(Hero.VIPER)) {
                TitleScreen(s.copy(hero = Hero.VIPER), records, insets, {}, {}, {}, {}, {})
            }
            for (hero in Hero.entries) {
                shot("$device-heroes-${hero.name.lowercase()}", 1200, world(hero)) {
                    HeroPickerScreen(hero, insets, {}, {}, {})
                }
            }
            shot("$device-settings", 900, world()) {
                SettingsScreen(s, insets, {}, {})
            }
            shot("$device-settings-custom", 900, world()) {
                SettingsScreen(s.copy(preset = null, seedMode = SeedMode.CUSTOM, seedText = "BEASTMODE"), insets, {}, {})
            }
            shot("$device-pause", 700, world()) {
                PauseScreen(s, "48213377", Hero.ACE, insets, {}, {}, {}, {})
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
    }

    private fun world(hero: Hero = Hero.BEAST): World {
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
        val hud = !name.contains("title") && !name.contains("settings") && !name.contains("heroes")
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
            "phone-settings-custom" to "menu-settings",
            "phone-heroes-beast" to "menu-heroes",
        )
    }
}
