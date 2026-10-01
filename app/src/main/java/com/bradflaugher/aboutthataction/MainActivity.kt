package com.bradflaugher.aboutthataction

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.bradflaugher.aboutthataction.audio.AudioOutput
import com.bradflaugher.aboutthataction.audio.SoundEngine
import com.bradflaugher.aboutthataction.engine.AlertPhase
import com.bradflaugher.aboutthataction.engine.Autopilot
import com.bradflaugher.aboutthataction.engine.Challenge
import com.bradflaugher.aboutthataction.engine.Difficulty
import com.bradflaugher.aboutthataction.engine.GameEvent
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.RunConfig
import com.bradflaugher.aboutthataction.engine.RunReport
import com.bradflaugher.aboutthataction.engine.SeedCode
import com.bradflaugher.aboutthataction.engine.World
import com.bradflaugher.aboutthataction.engine.Zone
import com.bradflaugher.aboutthataction.ui.BriefingScreen
import com.bradflaugher.aboutthataction.ui.ChallengeStatus
import com.bradflaugher.aboutthataction.ui.ChallengesScreen
import com.bradflaugher.aboutthataction.ui.SideClear
import com.bradflaugher.aboutthataction.ui.DayClock
import com.bradflaugher.aboutthataction.ui.CustomScreen
import com.bradflaugher.aboutthataction.ui.clearedCaption
import com.bradflaugher.aboutthataction.ui.dailyCard
import com.bradflaugher.aboutthataction.ui.dailyNumber
import com.bradflaugher.aboutthataction.ui.idLabel
import com.bradflaugher.aboutthataction.ui.presetLabel
import com.bradflaugher.aboutthataction.ui.todaysChallenge
import java.time.LocalDate
import com.bradflaugher.aboutthataction.ui.GameOverScreen
import com.bradflaugher.aboutthataction.ui.HeroPickerScreen
import com.bradflaugher.aboutthataction.ui.Motion
import com.bradflaugher.aboutthataction.ui.PauseScreen
import com.bradflaugher.aboutthataction.ui.RunSummary
import com.bradflaugher.aboutthataction.ui.SettingsScreen
import com.bradflaugher.aboutthataction.ui.TitleScreen
import kotlin.random.Random

class MainActivity : ComponentActivity(), GameView.Host {

    private enum class Screen { TITLE, CUSTOM, HEROES, SETTINGS, CHALLENGES, BRIEFING, PLAYING, PAUSED, GAME_OVER }

    private lateinit var prefs: Prefs
    private lateinit var sound: SoundEngine
    private lateinit var audio: AudioOutput
    private lateinit var haptics: Haptics
    private lateinit var gameView: GameView

    private var screen by mutableStateOf(Screen.TITLE)
    /** Where the hero picker goes back to: the title, or the CUSTOM screen that opened it. */
    private var heroesFrom = Screen.TITLE
    private var settings by mutableStateOf(Settings())
    private var records by mutableStateOf(Records())
    private var challengeLog by mutableStateOf(ChallengeLog())
    /**
     * Today (local calendar), for the daily: state, refreshed on every resume and every menu
     * change, so the daily turns over at midnight even if the app sat in the background.
     */
    private val dayClock = DayClock()
    private val day: Long get() = dayClock.day
    /** World time of the last side-clear chime (game thread only). */
    private var sideChimeAt = -9f
    /** The challenge on the BRIEFING screen, and where its back button goes. */
    private var briefing by mutableStateOf<Challenge?>(null)
    private var briefingFrom = Screen.TITLE
    private var lastRun by mutableStateOf<RunSummary?>(null)
    private var insetTop by mutableStateOf(0)
    private var insetBottom by mutableStateOf(0)

    private var runSeed = 0L
    private var runSeedLabel = ""
    private var runDifficulty = ""
    private var runConfig: RunConfig? = null

    // Music state, driven from the game thread and reset on the main thread between runs.
    @Volatile private var musicZone: Zone? = null
    @Volatile private var musicSilent = false
    @Volatile private var musicAlert = AlertPhase.CALM
    @Volatile private var musicSlowMo = false
    private var musicFrame = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = Prefs(this)
        settings = prefs.loadSettings()
        records = prefs.loadRecords()
        challengeLog = prefs.loadChallenges()
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
            // Back on the title leaves the app, with the system's predictive back-to-home animation.
            // Back to the title or the board (or anywhere): the daily follows the date.
            LaunchedEffect(screen) { refreshDay() }
            BackHandler(enabled = screen != Screen.TITLE) {
                when (screen) {
                    Screen.PLAYING -> pause()
                    Screen.PAUSED -> resume()
                    Screen.HEROES -> heroesBack()
                    Screen.CUSTOM, Screen.CHALLENGES -> { screen = Screen.TITLE }
                    Screen.BRIEFING -> { screen = briefingFrom }
                    Screen.SETTINGS, Screen.GAME_OVER -> toTitle()
                    Screen.TITLE -> Unit
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize().background(Color(NIGHT))) {
                // The hallway always fills the game's width, so a window that isn't tall enough
                // (a landscape tablet, a desktop window) plays in a centred portrait column.
                val tall = maxHeight >= maxWidth * MIN_ASPECT
                AndroidView(
                    factory = { gameView },
                    modifier = if (tall) Modifier.fillMaxSize() else Modifier.align(Alignment.Center).fillMaxHeight().width(maxHeight / MIN_ASPECT),
                )
                AnimatedContent(
                    targetState = screen,
                    // Full size even while PLAYING shows nothing, so menus never grow from 0×0.
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { menuTransition(initialState, targetState) },
                    contentAlignment = Alignment.Center,
                    label = "screen",
                ) { target ->
                    val daily = todaysChallenge(day, challengeLog)
                    when (target) {
                        Screen.TITLE -> TitleScreen(
                            settings, records, pad,
                            onPlay = ::startRun,
                            onSettings = { screen = Screen.SETTINGS },
                            // The custom curve is kept while a preset is picked, so CUSTOM comes back as left.
                            onPreset = { updateSettings(settings.copy(preset = it)) },
                            onCustom = {
                                updateSettings(settings.copy(preset = null))
                                screen = Screen.CUSTOM
                            },
                            onHeroes = { openHeroes(Screen.TITLE) },
                            onChallenges = { screen = Screen.CHALLENGES },
                            daily = dailyCard(daily, day, challengeLog, settings.hero),
                            onDaily = { openBriefing(daily, Screen.TITLE) },
                            challengesCaption = clearedCaption(challengeLog),
                        )
                        Screen.CHALLENGES -> ChallengesScreen(
                            challengeLog, daily, dailyCard(daily, day, challengeLog, settings.hero), pad,
                            onOpen = { openBriefing(it, Screen.CHALLENGES) },
                            onBack = { screen = Screen.TITLE },
                            pick = settings.hero,
                        )
                        Screen.BRIEFING -> briefing?.let { c ->
                            BriefingScreen(
                                c, settings.hero, challengeLog, pad,
                                dailyNumber = if (c.id == daily.id) dailyNumber(day) else null,
                                onPickHero = { h ->
                                    if (h != settings.hero) {
                                        updateSettings(settings.copy(hero = h))
                                        showAttract(music = false)
                                    }
                                },
                                onPlay = { startChallenge(c) },
                                onBack = { screen = briefingFrom },
                            )
                        }
                        Screen.CUSTOM -> CustomScreen(
                            settings, pad,
                            // A pasted brag can switch heroes: the demo behind the title stars them too.
                            onChange = { s ->
                                val heroChanged = s.hero != settings.hero
                                updateSettings(s)
                                if (heroChanged) showAttract(music = false)
                            },
                            onHeroes = { openHeroes(Screen.CUSTOM) },
                            onPlay = ::startRun,
                            onBack = { screen = Screen.TITLE },
                        )
                        Screen.HEROES -> HeroPickerScreen(
                            settings.hero, pad,
                            onPick = ::pickHero,
                            onPlay = ::startRun,
                            onBack = ::heroesBack,
                        )
                        Screen.SETTINGS -> SettingsScreen(settings, pad, ::updateSettings) { screen = Screen.TITLE }
                        Screen.PAUSED -> PauseScreen(
                            settings, runSeedLabel, runConfig?.hero ?: settings.hero, pad,
                            onResume = ::resume,
                            onRestart = {
                                recordBest()
                                runConfig?.let { startRun(it) }
                            },
                            onQuit = {
                                recordBest()
                                toTitle()
                            },
                            onSettings = ::updateSettings,
                            challenge = gameView.world?.let { w -> w.challenge?.let { ChallengeStatus.of(it, challengeLog, w.hero) } },
                        )
                        Screen.GAME_OVER -> lastRun?.let { run ->
                            GameOverScreen(
                                run, pad,
                                onRetry = { runConfig?.let { startRun(it) } },
                                onNewRun = ::startRun,
                                onTitle = ::toTitle,
                                records = records,
                                onBoard = { toMenu(Screen.CHALLENGES) },
                            )
                        }
                        Screen.PLAYING -> Unit
                    }
                }
            }
        }
        // After setContent: API 37's PhoneWindow.getInsetsController() dereferences the decor
        // view without a null check, so asking for it before the decor exists crashes.
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        refreshDay()
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        startAudio()
        gameView.onHostResume()
    }

    override fun onPause() {
        if (screen == Screen.PLAYING) pause()
        gameView.onHostPause()
        audio.pause()
        resumed = false
        unregisterReceiver(noisy)
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
        super.onPause()
    }

    /** The notification shade or the other app in split screen took focus: the run shouldn't play on unseen. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) pause()
    }

    // ------------------------------------------------------------- audio focus

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private var hasFocus = false
    /** Between onResume and onPause (the lifecycle only reports RESUMED after onResume returns). */
    private var resumed = false

    /**
     * Plays only with audio focus. Asking again after a loss is how RESUME and the menus win it
     * back; while a call holds it, the request is delayed and the gain callback starts playback.
     */
    private fun startAudio() {
        if (!resumed) return
        if (!hasFocus) hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (hasFocus) audio.start()
    }

    /** A call, an alarm or another app's audio: pause the run and go quiet until it's over. */
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        hasFocus = false
                        pause()
                        audio.pause()
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        hasFocus = true
                        startAudio()
                    }
                }
            }
            .build()
    }

    /** Headphones unplugged: pause and go silent until the player picks it back up. */
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            pause()
            audio.pause()
        }
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }

    /** The world on screen, for instrumented tests. */
    val currentWorld: World? get() = gameView.world
    val framesDrawn: Long get() = gameView.framesDrawn
    val isPlaying: Boolean get() = screen == Screen.PLAYING

    // ------------------------------------------------------------- flow

    private fun showAttract(music: Boolean = true) {
        val seed = Random.nextLong()
        gameView.attract = true
        gameView.paused = false
        gameView.autopilot = Autopilot(seed)
        // The demo stars whoever you'll drop in as.
        gameView.world = World(RunConfig(seed, Difficulty.Preset.CHILL.difficulty, coach = false, hero = settings.hero))
        musicZone = null
        musicAlert = AlertPhase.CALM
        sound.setAlert(AlertPhase.CALM)
        endSlowMo()
        if (music) sound.playTitle()
    }

    private fun startRun() {
        val s = settings
        // 40 bits, so every run has a shareable code.
        val seed = s.newSeed { Random.nextLong(SeedCode.LIMIT) }
        runSeedLabel = SeedCode.labelOf(seed)
        runDifficulty = s.difficultyName
        startRun(RunConfig(seed, s.difficulty, silent = s.silent, coach = s.coach, hero = s.hero))
    }

    private fun startRun(config: RunConfig) {
        startAudio()
        runConfig = config
        runSeed = config.seed
        sideChimeAt = -9f
        if (runSeedLabel.isEmpty()) runSeedLabel = SeedCode.labelOf(config.seed)
        musicZone = null
        musicAlert = AlertPhase.CALM
        sound.setAlert(AlertPhase.CALM)
        // Before the first frame's setZone, so the run opens in this hero's arrangement.
        sound.setHero(config.hero)
        // Ones already cleared never side-clear again: a toast is always news.
        gameView.world = World(config.copy(silent = settings.silent, coach = settings.coach, knownCleared = challengeLog.cleared.keys))
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
        startAudio()
        gameView.paused = false
        sound.setPaused(false)
        screen = Screen.PLAYING
    }

    private fun toTitle() = toMenu(Screen.TITLE)

    /** Out of a run to a menu, with the demo back behind it. */
    private fun toMenu(to: Screen) {
        startAudio()
        sound.setPaused(false)
        screen = to
        showAttract()
    }

    // ------------------------------------------------------------- challenges

    /** Today, on the device's own calendar (days since 1970-01-01): the daily turns over at local midnight. */
    private fun today(): Long = LocalDate.now().toEpochDay()

    /** Picks up a new day (midnight passed, or the clock moved): the title's daily follows. */
    private fun refreshDay() {
        dayClock.refresh()
    }

    private fun openBriefing(c: Challenge, from: Screen) {
        briefing = c
        briefingFrom = from
        screen = Screen.BRIEFING
    }

    /** A challenge run: its own building, curve and start, as the picked hero (if they're allowed). */
    private fun startChallenge(c: Challenge) {
        runSeedLabel = idLabel(c.id)
        runDifficulty = presetLabel(c.preset)
        startRun(c.runConfig(settings.hero, settings.coach))
    }

    private fun updateChallenges(log: ChallengeLog) {
        if (log == challengeLog) return
        challengeLog = log
        prefs.saveChallenges(log)
    }

    /** The best progress of the run on screen, if it's a challenge run (at game over, quit and restart). */
    private fun recordBest(world: World? = gameView.world) {
        val run = world?.challenge ?: return
        if (world.config.challenge == null || gameView.attract) return
        updateChallenges(challengeLog.withBest(run.challenge.id, run.progress))
    }

    /** A hero card settled on the picker: remember them, play their theme, star them in the demo. */
    private fun pickHero(hero: Hero) {
        if (screen != Screen.HEROES) return
        if (hero != settings.hero) {
            updateSettings(settings.copy(hero = hero))
            showAttract(music = false)
        }
        sound.playHeroTheme(hero)
    }

    private fun openHeroes(from: Screen) {
        heroesFrom = from
        screen = Screen.HEROES
    }

    /** Back from the picker to whichever menu opened it, with the title music back on. */
    private fun heroesBack() {
        screen = heroesFrom
        sound.playTitle()
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
        // Side clears come in bunches: one soft chime for the bunch.
        val chime = event !is GameEvent.SideCleared || world.time - sideChimeAt > SIDE_CHIME_GAP
        if (event is GameEvent.SideCleared && chime) sideChimeAt = world.time
        if (chime) sound.trigger(event)
        haptics.onEvent(event)
        // A clear counts the moment it happens, even if the run is quit straight after; a side
        // clear (another challenge met on the way) counts just the same.
        if (event is GameEvent.ChallengeCleared || event is GameEvent.SideCleared) {
            runOnUiThread { updateChallenges(challengeLog.withEvent(event, today())) }
        }
        // The GUNS HOT / SILENT choice sticks between runs.
        if (event is GameEvent.ModeToggled) runOnUiThread { if (settings.silent != event.silent) updateSettings(settings.copy(silent = event.silent)) }
    }

    override fun onFrame(world: World) {
        if (gameView.attract) return
        // SILENT's sneak mix plays only while all's calm; spotted, it's the zone's full track.
        val sneaking = world.silent && world.alertPhase == AlertPhase.CALM
        if (world.musicZone != musicZone || sneaking != musicSilent) {
            musicZone = world.musicZone
            musicSilent = sneaking
            sound.setZone(world.musicZone, sneaking)
        }
        if (world.alertPhase != musicAlert) {
            musicAlert = world.alertPhase
            sound.setAlert(world.alertPhase)
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
        val ch = world.challenge
        // Challenge runs bring their own curve and start floor, so they don't touch the endless records.
        val newBestScore = ch == null && world.score > old.bestScore
        val newBestFloor = ch == null && world.deepest > old.bestFloor
        if (ch == null) {
            records = Records(maxOf(old.bestScore, world.score), maxOf(old.bestFloor, world.deepest), old.runs + 1)
            prefs.saveRecords(records)
        }
        // The status reads the log from before this run, so "best" compares against earlier runs.
        val status = ch?.let { ChallengeStatus.of(it, challengeLog, world.hero) }
        recordBest(world)
        val report = RunReport.of(world)
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
            title = report.title,
            deathLine = report.deathLine,
            quip = report.quip,
            highlights = report.highlights,
            hero = world.hero,
            difficulty = runDifficulty,
            challenge = status,
            alsoCleared = world.sideCleared.map { SideClear(it.id, it.name, it.tier) },
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
            // Settings, CUSTOM and the hero picker slide in over the menu below them and back out.
            menuDepth(from) >= 0 && menuDepth(to) > menuDepth(from) -> (slideInHorizontally(tween(Motion.base, easing = Motion.out)) { it / 5 } + fadeIn(base)) togetherWith
                fadeOut(quick)
            menuDepth(from) > menuDepth(to) && menuDepth(to) >= 0 -> fadeIn(base) togetherWith
                (slideOutHorizontally(tween(Motion.base, easing = Motion.out)) { it / 5 } + fadeOut(quick))
            // Pause pops in; game over has its own staged entrance.
            to == Screen.PAUSED -> (fadeIn(quick) + scaleIn(tween(Motion.base, easing = Motion.out), 0.94f)) togetherWith fadeOut(quick)
            else -> fadeIn(base) togetherWith fadeOut(quick)
        }
    }

    /** How deep a menu sits under the title (-1 for screens outside the menu stack). */
    private fun menuDepth(s: Screen): Int = when (s) {
        Screen.TITLE -> 0
        Screen.SETTINGS, Screen.CUSTOM, Screen.CHALLENGES -> 1
        Screen.BRIEFING -> if (briefingFrom == Screen.CHALLENGES) 2 else 1
        Screen.HEROES -> if (heroesFrom == Screen.CUSTOM) 2 else 1
        else -> -1
    }

    companion object {
        /** Launch straight into a run (used by the emulator smoke test). */
        const val EXTRA_AUTOSTART = "autostart"

        /** The squattest window (height / width) the game plays in; wider ones get side bars. */
        private const val MIN_ASPECT = 1.6f
        private const val NIGHT = 0xFF07060F
        /** World seconds between side-clear chimes. */
        private const val SIDE_CHIME_GAP = 0.5f
    }
}
