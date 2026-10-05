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
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
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
import com.bradflaugher.aboutthataction.engine.Lesson
import com.bradflaugher.aboutthataction.ui.TextSizeScope
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
import com.bradflaugher.aboutthataction.ui.GameOverScreen
import com.bradflaugher.aboutthataction.ui.HelpScreen
import com.bradflaugher.aboutthataction.ui.HeroPickerScreen
import com.bradflaugher.aboutthataction.ui.Motion
import com.bradflaugher.aboutthataction.ui.PauseScreen
import com.bradflaugher.aboutthataction.ui.RunSummary
import com.bradflaugher.aboutthataction.ui.SettingsScreen
import com.bradflaugher.aboutthataction.ui.TitleScreen
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity(), GameView.Host {

    private enum class Screen { TITLE, CUSTOM, HEROES, SETTINGS, HELP, CHALLENGES, BRIEFING, PLAYING, PAUSED, GAME_OVER }

    private lateinit var prefs: Prefs
    private lateinit var sound: SoundEngine
    private lateinit var audio: AudioOutput
    private lateinit var haptics: Haptics
    private lateinit var gameView: GameView

    private var screen by mutableStateOf(Screen.TITLE)
    /** Where the hero picker goes back to: the title, or the CUSTOM screen that opened it. */
    private var heroesFrom = Screen.TITLE
    /** Where HOW TO PLAY goes back to: settings, the title's first-time card, or the pause menu. */
    private var helpFrom = Screen.SETTINGS
    /** The title's FIRST TIME HERE? card is still to show. */
    private var welcome by mutableStateOf(false)
    /** Lessons the guide has taught on this device (it won't teach them again), and whether the first run's walkthrough is done. */
    private var learned: Set<Lesson> = emptySet()
    private var walkthroughDone = false
    private var settings by mutableStateOf(Settings())
    private var records by mutableStateOf(Records())
    private var challengeLog by mutableStateOf(ChallengeLog())
    /**
     * Today (local calendar), for the daily: state, refreshed on every resume and every menu
     * change, so the daily turns over at midnight even if the app sat in the background.
     */
    private val dayClock = DayClock()
    private val day: Long get() = dayClock.day
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
        welcome = !prefs.loadIntroSeen()
        learned = prefs.loadLearned()
        zonesReached = prefs.loadZonesReached()
        walkthroughDone = prefs.loadWalkthroughDone()
        sound = SoundEngine()
        audio = AudioOutput(sound)
        haptics = Haptics(this)
        applySettings(settings)

        gameView = GameView(this, this)
        // (applySettings above ran before the view existed.)
        gameView.touchGuide = settings.touchGuide
        gameView.calm = settings.calm
        gameView.textScale = hudTextScale(settings)
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
            // Back to the title or the board (or anywhere): the daily follows the date, and a
            // screen left open past midnight turns the page on its own.
            LaunchedEffect(screen) {
                while (true) {
                    refreshDay()
                    delay(millisToMidnight())
                }
            }
            // Back on the title leaves the app, with the system's predictive back-to-home animation.
            BackHandler(enabled = screen != Screen.TITLE) {
                when (screen) {
                    Screen.PLAYING -> pause()
                    Screen.PAUSED -> resume()
                    Screen.HEROES -> heroesBack()
                    Screen.HELP -> { screen = helpFrom }
                    Screen.CUSTOM, Screen.CHALLENGES -> { screen = Screen.TITLE }
                    Screen.BRIEFING -> { screen = briefingFrom }
                    Screen.SETTINGS -> settingsBack()
                    Screen.GAME_OVER -> toTitle()
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
                // TEXT SIZE for the menus only: the game view under them never remounts when it changes.
                TextSizeScope(settings.textSize.scale) {
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
                            welcome = welcome,
                            onWelcomeDone = ::introSeen,
                            onHelp = {
                                introSeen()
                                openHelp(Screen.TITLE)
                            },
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
                        Screen.SETTINGS -> SettingsScreen(
                            settings, pad, ::updateSettings, onBack = ::settingsBack, onHelp = { openHelp(Screen.SETTINGS) },
                            jukebox = jukeboxUnlocked(),
                            playing = jukebox,
                            onJukebox = ::playJukebox,
                        )
                        Screen.HELP -> HelpScreen(pad, onBack = { screen = helpFrom }, onReplayTutorial = ::replayTutorial)
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
                            onHelp = { openHelp(Screen.PAUSED) },
                            onSkipTutorial = if (gameView.world?.guide?.walkthrough == true) ({
                                gameView.skipTutorial()
                                resume()
                            }) else null,
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
        // The game thread has stopped: save what the run has earned now (its best, and any clear
        // whose event is still queued for the main thread), in case the system never brings us back.
        gameView.world?.let { w -> if (!gameView.attract) updateChallenges(challengeLog.withRun(w, today())) }
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
        // Dropping in is answer enough to the title's first-time card.
        introSeen()
        // A run takes over the music: the jukebox (settings, or settings → HOW TO PLAY → REPLAY) stops.
        jukebox = null
        startAudio()
        runConfig = config
        runSeed = config.seed
        if (runSeedLabel.isEmpty()) runSeedLabel = SeedCode.labelOf(config.seed)
        musicZone = null
        musicAlert = AlertPhase.CALM
        sound.setAlert(AlertPhase.CALM)
        // Before the first frame's setZone, so the run opens in this hero's arrangement.
        sound.setHero(config.hero)
        // A first run from the roof gets the walkthrough (REPLAY TUTORIAL asks for it, and for every lesson again).
        val firstRun = settings.coach && !walkthroughDone && records.runs == 0 && config.challenge == null && config.difficulty.startFloor == 0
        val tutorial = config.tutorial || firstRun
        // Ones already cleared never side-clear again: ALSO CLEARED is always news.
        gameView.world = World(
            config.copy(
                silent = settings.silent, coach = settings.coach || config.tutorial, knownCleared = challengeLog.cleared.keys,
                tutorial = tutorial, learned = if (config.tutorial) emptySet() else learned,
            ),
        )
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

    /** Until just past the next local midnight (a beat late, so the new date has surely arrived). */
    private fun millisToMidnight(): Long {
        val now = ZonedDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return Duration.between(now, midnight).toMillis().coerceAtLeast(0L) + 1_000L
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

    /** The JUKEBOX in settings: a zone's track playing in the menus, or null for the title theme. */
    private var jukebox by mutableStateOf<Zone?>(null)
    /** Zones any run has reached (challenges too, which can start deep). */
    private var zonesReached by mutableStateOf<Set<Zone>>(emptySet())

    /** The tracks the JUKEBOX offers: the Neon Tower always, then every zone reached (or within the endless DEEPEST). */
    private fun jukeboxUnlocked(): Set<Zone> =
        Zone.entries.filter { it == Zone.TOWER || it in zonesReached || (it != Zone.ROOFTOP && records.bestFloor >= it.startFloor) }.toSet()

    private fun playJukebox(zone: Zone?) {
        jukebox = zone
        if (zone == null) {
            sound.playTitle()
        } else {
            // Calm, whatever the last run left the mix at: the menus never update it.
            sound.setAlert(AlertPhase.CALM)
            sound.setIntensity(0f)
            sound.setHero(settings.hero)
            sound.setZone(zone)
        }
    }

    /** Out of settings: the title theme comes back if the jukebox was playing something else. */
    private fun settingsBack() {
        if (jukebox != null) playJukebox(null)
        screen = Screen.TITLE
    }

    private fun openHelp(from: Screen) {
        helpFrom = from
        screen = Screen.HELP
    }

    private fun introSeen() {
        if (!welcome) return
        welcome = false
        prefs.saveIntroSeen()
    }

    /**
     * REPLAY TUTORIAL: a CHILL run from the roof with the walkthrough, every lesson taught again
     * and the coach tips back on.
     * From the pause menu it ends the run on screen, the way RESTART does.
     */
    private fun replayTutorial() {
        recordBest()
        if (!settings.coach) updateSettings(settings.copy(coach = true))
        val seed = Random.nextLong(SeedCode.LIMIT)
        runSeedLabel = SeedCode.labelOf(seed)
        runDifficulty = Difficulty.Preset.CHILL.label
        startRun(RunConfig(seed, Difficulty.Preset.CHILL.difficulty, coach = true, tutorial = true, hero = settings.hero))
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

    /** The system font size changed under a running app (fontScale is handled here, no restart): the HUD follows. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::gameView.isInitialized) gameView.textScale = hudTextScale(settings)
    }

    private val accessibility by lazy { getSystemService(AccessibilityManager::class.java) }

    private fun remember(set: Set<Lesson>) {
        if (set == learned) return
        learned = set
        prefs.saveLearned(set)
    }

    /** The HUD's text: the TEXT SIZE setting on top of the system font size (which the HUD's layout can take up to 1.3x of). */
    private fun hudTextScale(s: Settings): Float = (s.textSize.scale * resources.configuration.fontScale.coerceIn(1f, 1.3f)).coerceAtMost(1.3f)

    private fun updateSettings(s: Settings) {
        settings = s
        prefs.saveSettings(s)
        applySettings(s)
    }

    private fun applySettings(s: Settings) {
        sound.setMusicVolume(s.musicVolume)
        sound.setSfxVolume(s.sfxVolume)
        haptics.enabled = s.haptics
        if (::gameView.isInitialized) {
            gameView.touchGuide = s.touchGuide
            gameView.calm = s.calm
            gameView.textScale = hudTextScale(s)
        }
    }

    // ------------------------------------------------------------- GameView.Host

    override fun onGameEvent(event: GameEvent, world: World) {
        // (The demo, or a frame still in flight from the world DROP IN just replaced.)
        if (gameView.attract || world !== gameView.world) return
        sound.trigger(event)
        haptics.onEvent(event)
        // A clear counts the moment it happens, even if the run is quit straight after; a side
        // clear (another challenge met on the way) counts just the same.
        if (event is GameEvent.ChallengeCleared || event is GameEvent.SideCleared) {
            runOnUiThread { updateChallenges(challengeLog.withEvent(event, today())) }
        }
        when (event) {
            // The guide: what it taught is remembered (a screen reader hears each prompt from onFrame).
            is GameEvent.LessonTaught -> runOnUiThread { remember(learned + event.lesson) }
            is GameEvent.WalkthroughOver -> runOnUiThread {
                if (!walkthroughDone) {
                    walkthroughDone = true
                    prefs.saveWalkthroughDone()
                }
                if (event.skipped) {
                    // Skipped the roof's moves, not the tips: those still come once each while Coach tips is on.
                    remember(learned + Lesson.walkthrough)
                    Toast.makeText(this, "Tutorial skipped. Replay it any time from HOW TO PLAY.", Toast.LENGTH_SHORT).show()
                }
            }
            // The zone's music played: the JUKEBOX has it now (a Void zone counts as the zone it plays).
            is GameEvent.ZoneEntered -> {
                val zones = setOf(event.zone, world.musicZone)
                runOnUiThread { if (!zonesReached.containsAll(zones)) { zonesReached = zonesReached + zones; prefs.saveZonesReached(zonesReached) } }
            }
            else -> Unit
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
        // Every track the run plays goes on the JUKEBOX, the Void's borrowed zones too.
        val mz = world.musicZone
        if (mz !in zonesReached) runOnUiThread { if (mz !in zonesReached) { zonesReached = zonesReached + mz; prefs.saveZonesReached(zonesReached) } }
        speakGuide(world)
    }

    /** The guide's prompt as last read out to a screen reader (the lift step changes its words as the car comes). */
    private var spoken: String? = null

    /** With a screen reader on, reads each guide prompt out once, and again whenever its words change. */
    private fun speakGuide(world: World) {
        val g = world.guide
        val text = if (g.lesson != null && g.doneAt < 0f) g.text else null
        if (text === spoken) return
        spoken = text
        if (text != null && accessibility.isEnabled) {
            val words = g.kicker + ". " + text
            gameView.post { gameView.announceForAccessibility(words) }
        }
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
            curve = world.difficulty.takeIf { runDifficulty == "CUSTOM" && world.challenge == null },
            startSilent = world.config.silent.takeIf { world.hero.sneaks && world.challenge == null },
            challenge = status,
            // The toughest first: the card has room to name only a couple.
            alsoCleared = world.sideCleared.distinctBy { it.id }.sortedByDescending { it.tier }.map { SideClear(it.id, it.name, it.tier) },
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
        Screen.HELP -> when (helpFrom) {
            Screen.SETTINGS -> 2
            Screen.TITLE -> 1
            else -> -1
        }
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
    }
}
