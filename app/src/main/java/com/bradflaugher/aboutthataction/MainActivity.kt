package com.bradflaugher.aboutthataction

import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.bradflaugher.aboutthataction.audio.AudioOutput
import com.bradflaugher.aboutthataction.audio.SoundEngine
import com.bradflaugher.aboutthataction.engine.Autopilot
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.RunConfig
import com.bradflaugher.aboutthataction.engine.World
import com.bradflaugher.aboutthataction.engine.Zone
import com.bradflaugher.aboutthataction.ui.GameOverScreen
import com.bradflaugher.aboutthataction.ui.Motion
import com.bradflaugher.aboutthataction.ui.PauseScreen
import com.bradflaugher.aboutthataction.ui.RunSummary
import com.bradflaugher.aboutthataction.ui.SettingsScreen
import com.bradflaugher.aboutthataction.ui.TitleScreen
import kotlin.random.Random

class MainActivity : ComponentActivity(), GameView.Host {

    private enum class Screen { TITLE, SETTINGS, PLAYING, PAUSED, GAME_OVER }

    private lateinit var prefs: Prefs
    private lateinit var sound: SoundEngine
    private lateinit var audio: AudioOutput
    private lateinit var haptics: Haptics
    private lateinit var gameView: GameView

    private var screen by mutableStateOf(Screen.TITLE)
    private var settings by mutableStateOf(Settings())
    private var records by mutableStateOf(Records())
    private var lastRun by mutableStateOf<RunSummary?>(null)
    private var insetTop by mutableStateOf(0)
    private var insetBottom by mutableStateOf(0)

    private var runSeed = 0L
    private var runSeedLabel = ""
    private var runConfig: RunConfig? = null

    // Music state, driven from the game thread and reset on the main thread between runs.
    @Volatile private var musicZone: Zone? = null
    @Volatile private var musicSlowMo = false
    private var musicFrame = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        prefs = Prefs(this)
        settings = prefs.loadSettings()
        records = prefs.loadRecords()
        sound = SoundEngine()
        audio = AudioOutput(sound)
        haptics = Haptics(this)
        applySettings(settings)

        gameView = GameView(this, this)
        gameView.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            insetTop = bars.top
            insetBottom = bars.bottom
            gameView.topInset = bars.top.toFloat()
            gameView.bottomInset = bars.bottom.toFloat()
            v.onApplyWindowInsets(insets)
        }
        showAttract()
        if (intent.getBooleanExtra(EXTRA_AUTOSTART, false)) startRun()

        setContent {
            val density = LocalDensity.current
            val pad = with(density) { PaddingValues(top = insetTop.toDp(), bottom = insetBottom.toDp()) }
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { gameView }, modifier = Modifier.fillMaxSize())
                AnimatedContent(
                    targetState = screen,
                    // Full size even while PLAYING shows nothing, so menus never grow from 0×0.
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { menuTransition(initialState, targetState) },
                    contentAlignment = Alignment.Center,
                    label = "screen",
                ) { target ->
                    when (target) {
                        Screen.TITLE -> TitleScreen(
                            settings, records, pad,
                            onPlay = ::startRun,
                            onSettings = { screen = Screen.SETTINGS },
                            onPreset = { updateSettings(settings.copy(preset = it, custom = it.difficulty)) },
                        )
                        Screen.SETTINGS -> SettingsScreen(settings, pad, ::updateSettings) { screen = Screen.TITLE }
                        Screen.PAUSED -> PauseScreen(
                            settings, runSeedLabel, pad,
                            onResume = ::resume,
                            onRestart = { runConfig?.let { startRun(it) } },
                            onQuit = ::toTitle,
                            onSettings = ::updateSettings,
                        )
                        Screen.GAME_OVER -> lastRun?.let { run ->
                            GameOverScreen(
                                run, pad,
                                onRetry = { runConfig?.let { startRun(it) } },
                                onNewRun = ::startRun,
                                onTitle = ::toTitle,
                                records = records,
                            )
                        }
                        Screen.PLAYING -> Unit
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    Screen.PLAYING -> pause()
                    Screen.PAUSED -> resume()
                    Screen.SETTINGS, Screen.GAME_OVER -> toTitle()
                    Screen.TITLE -> finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        audio.start()
    }

    override fun onPause() {
        if (screen == Screen.PLAYING) pause()
        audio.pause()
        super.onPause()
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }

    /** The world on screen, for instrumented tests. */
    val currentWorld: World? get() = gameView.world
    val isPlaying: Boolean get() = screen == Screen.PLAYING

    // ------------------------------------------------------------- flow

    private fun showAttract(music: Boolean = true) {
        val seed = Random.nextLong()
        gameView.attract = true
        gameView.paused = false
        gameView.autopilot = Autopilot(seed)
        gameView.world = World(RunConfig(seed, Difficulty.Preset.CHILL.difficulty))
        musicZone = null
        endSlowMo()
        if (music) sound.playTitle()
    }

    private fun startRun() {
        val s = settings
        val seed = s.newSeed { Random.nextLong(100_000_000L) }
        runSeedLabel = when (s.seedMode) {
            SeedMode.DAILY -> "DAILY " + java.time.LocalDate.now(java.time.ZoneOffset.UTC)
            SeedMode.CUSTOM -> s.seedText.ifBlank { seed.toString() }.uppercase()
            SeedMode.RANDOM -> seed.toString()
        }
        startRun(RunConfig(seed, s.difficulty, silent = s.silent))
    }

    private fun startRun(config: RunConfig) {
        runConfig = config
        runSeed = config.seed
        if (runSeedLabel.isEmpty()) runSeedLabel = config.seed.toString()
        musicZone = null
        gameView.world = World(config.copy(silent = settings.silent))
        gameView.attract = false
        gameView.paused = false
        screen = Screen.PLAYING
        sound.setPaused(false)
    }

    private fun pause() {
        if (screen != Screen.PLAYING) return
        gameView.paused = true
        sound.setPaused(true)
        screen = Screen.PAUSED
    }

    private fun resume() {
        gameView.paused = false
        sound.setPaused(false)
        screen = Screen.PLAYING
    }

    private fun toTitle() {
        sound.setPaused(false)
        screen = Screen.TITLE
        showAttract()
    }

    private fun updateSettings(s: Settings) {
        settings = s
        prefs.saveSettings(s)
        applySettings(s)
    }

    private fun applySettings(s: Settings) {
        sound.setMusicVolume(s.musicVolume)
        sound.setSfxVolume(s.sfxVolume)
        haptics.enabled = s.haptics
        if (::gameView.isInitialized) gameView.touchGuide = s.touchGuide
    }

    // ------------------------------------------------------------- GameView.Host

    override fun onGameEvent(event: GameEvent, world: World) {
        if (gameView.attract) return
        sound.trigger(event)
        haptics.onEvent(event)
        // The GUNS HOT / SILENT choice sticks between runs.
        if (event is GameEvent.ModeToggled) runOnUiThread { if (settings.silent != event.silent) updateSettings(settings.copy(silent = event.silent)) }
    }

    override fun onFrame(world: World) {
        if (gameView.attract) return
        if (world.musicZone != musicZone) {
            musicZone = world.musicZone
            sound.setZone(world.musicZone)
        }
        if (world.slowMo != musicSlowMo) {
            musicSlowMo = world.slowMo
            sound.setSlowMo(musicSlowMo)
        }
        if (++musicFrame % 15 == 0) sound.setIntensity(world.intensity)
    }

    /** A run can end mid bullet-time; don't let the menus play slowed down. */
    private fun endSlowMo() {
        musicSlowMo = false
        sound.setSlowMo(false)
    }

    override fun onDemoOver() {
        if (gameView.attract && screen != Screen.PLAYING) showAttract(music = false)
    }

    override fun onPauseRequested() {
        pause()
    }

    override fun onGameOver(world: World) {
        if (screen != Screen.PLAYING) return
        val old = records
        val newBestScore = world.score > old.bestScore
        val newBestFloor = world.deepest > old.bestFloor
        records = Records(maxOf(old.bestScore, world.score), maxOf(old.bestFloor, world.deepest), old.runs + 1)
        prefs.saveRecords(records)
        lastRun = RunSummary(
            floor = world.deepest,
            zone = world.zone,
            score = world.score,
            kills = world.kills,
            takedowns = world.takedowns,
            seconds = world.time,
            seedLabel = runSeedLabel,
            newBestScore = newBestScore,
            newBestFloor = newBestFloor,
        )
        endSlowMo()
        sound.gameOver()
        screen = Screen.GAME_OVER
    }

    /** Presentation only: how one menu hands over to the next. Snappy, never floaty. */
    private fun menuTransition(from: Screen, to: Screen): ContentTransform {
        val quick = tween<Float>(Motion.fast)
        val base = tween<Float>(Motion.base, easing = Motion.out)
        return when {
            // Into play: get out of the way immediately.
            to == Screen.PLAYING -> fadeIn(quick) togetherWith fadeOut(tween(Motion.fast)) + scaleOut(tween(Motion.fast), 1.04f)
            // Settings slides in over the title and back out.
            to == Screen.SETTINGS -> (slideInHorizontally(tween(Motion.base, easing = Motion.out)) { it / 5 } + fadeIn(base)) togetherWith
                fadeOut(quick)
            from == Screen.SETTINGS -> fadeIn(base) togetherWith
                (slideOutHorizontally(tween(Motion.base, easing = Motion.out)) { it / 5 } + fadeOut(quick))
            // Pause pops in; game over has its own staged entrance.
            to == Screen.PAUSED -> (fadeIn(quick) + scaleIn(tween(Motion.base, easing = Motion.out), 0.94f)) togetherWith fadeOut(quick)
            else -> fadeIn(base) togetherWith fadeOut(quick)
        }
    }

    companion object {
        /** Launch straight into a run (used by the emulator smoke test). */
        const val EXTRA_AUTOSTART = "autostart"
    }
}
