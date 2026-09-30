package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.audio.Scales.AEOLIAN
import com.bradflaugher.aboutthataction.audio.Scales.DORIAN
import com.bradflaugher.aboutthataction.audio.Scales.IONIAN
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.sqrt

/**
 * Every hero's own soundtrack: each zone's track (GUNS HOT and its SILENT sneak mix) in the
 * hero's genre, plus a signature theme for the hero picker.
 *
 * An arrangement keeps what makes a zone that zone — its key (and chords, except WOLF's
 * country, which plays in the parallel major), bars per chord, section plan, the ambience
 * (wind, rotor, VOID's glitches) and the intensity layering thresholds — and swaps in the
 * hero's band: tempo, patches, drum kit and every part, plus their signature motif (the
 * hero's leitmotif, transposed over every zone's chords). Each zone also tints the band:
 * cold zones darken filters, the deep ones add grit, the mines crush the drums.
 *
 *  - BULL: hip-hop. Sneaking is a dusty, swung boom-bap beat at ~86 BPM (a lo-fi, crushed
 *    kit, Rhodes 9th chords, a deep sine bass, a muted guitar lick and vinyl crackle); GUNS
 *    HOT is half-time trap at ~140 (sliding 808s, rolling hat triplets and 32nds, a
 *    snare-and-clap on three, a dark bell melody).
 *  - FOX: classical. Sneaking is a chamber piece on tiptoe: pizzicato cello and violins,
 *    soft bowed strings, a clarinet and a timpani; GUNS HOT is a baroque presto at ~156
 *    (spiccato cellos, running violin figures, harpsichord continuo, timpani, a solo violin).
 *  - WOLF: country. Sneaking is a slow shuffle (brushes, a two-beat bass, fingerpicked
 *    steel guitar, pedal steel and a harmonica); GUNS HOT is a train-beat hoedown at ~160
 *    (16th-note snare, boom-chick bass, banjo rolls, strummed acoustic and a fiddle).
 *  - HAWK: jungle drums. Sneaking is sparse hand drums, a shaker and a heartbeat frame
 *    drum under a marimba, a wooden flute and crickets; GUNS HOT is a war-drum ensemble at
 *    ~126 (big key-tuned toms in threes against the fours, log drums, shakers, a chant).
 *
 * Every melody is original. All of it is built once (at [SoundEngine] construction) and never
 * allocates while playing.
 */
internal object HeroSongs {

    // ---- Zone tint -------------------------------------------------------------------------

    /** How a zone colours any band: filter [dark]ness, added [grit], drum bus [crush]/[drive]. */
    private class Tint(val grit: Float, val dark: Float, val crush: Int, val drive: Float)

    private fun tint(z: Zone?): Tint = when (z) {
        Zone.ROOFTOP -> Tint(0f, 1f, 0, 0f)
        Zone.TOWER -> Tint(0f, 1.05f, 0, 0f)
        Zone.LABS -> Tint(0.1f, 0.8f, 0, 0f)
        Zone.METRO -> Tint(0.15f, 0.95f, 0, 0.1f)
        Zone.MINES -> Tint(0.35f, 0.75f, 2, 0.3f)
        Zone.MAGMA -> Tint(0.55f, 0.9f, 0, 0.25f)
        Zone.HELL -> Tint(0.9f, 1f, 0, 0.35f)
        Zone.VOID -> Tint(0.2f, 1f, 0, 0.1f)
        null -> Tint(0f, 1f, 0, 0f)
    }

    private fun Patch.tinted(t: Tint, role: Float): Patch = copyish(cutoff = cutoff * t.dark, drive = drive + t.grit * role)

    /** The octave that puts the lead's centre (tonic + octave) in [lo, lo + 11]. */
    private fun leadOctave(base: SongSpec, register: Int): Int {
        // VOID transposes up to half an octave either way: keep its lead centred.
        val lo = if (base.glitch) maxOf(register, 60) else register
        var k = 0
        while (base.tonic + k < lo) k += 12
        while (base.tonic + k > lo + 11) k -= 12
        return k
    }

    /** Drum level relative to the zone's own balance (dense, driven kits sit lower). */
    private fun zoneDrums(base: SongSpec): Float =
        sqrt(base.mix.drums / 0.57f).coerceIn(0.75f, 1.2f) * (if (base === Songs.hell) 0.65f else 1f)

    /**
     * [c] with its seventh (and [ninth]) added from [scale]: diatonic where the chord is, a
     * dim7/b9 where it's borrowed. Power and suspended chords are left alone.
     */
    private fun jazz(c: Chord, scale: IntArray, ninth: Boolean): Chord {
        val iv = c.intervals
        if (iv.size != 3 && iv.size != 4) return c
        if (iv.size == 3 && (iv[1] != 3 && iv[1] != 4)) return c
        val out = ArrayList<Int>()
        iv.forEach { out += it }
        if (iv.size == 3) {
            val cands = if (iv[1] == 3 && iv[2] == 6) intArrayOf(9, 10) else intArrayOf(10, 11)
            cands.firstOrNull { Scales.contains(scale, c.root + it) }?.let { out += it }
        }
        if (ninth) intArrayOf(14, 13).firstOrNull { Scales.contains(scale, c.root + it) }?.let { out += it }
        return Chord(c.root, out.toIntArray(), c.degree)
    }

    private fun jazz(p: Array<Chord>, scale: IntArray, ninth: Boolean) = Array(p.size) { jazz(p[it], scale, ninth) }

    // ---- Signatures (all original) -----------------------------------------------------------

    /** BULL: a lazy, head-nodding hook — a held fifth, a stumble down and a drop to the root. */
    private val bullSig = Motif("4:2 .:1 4:1 3:2 2:2 .:2 0:2 2:4")
    private val bullAns = Motif("0:2 .:2 1:2 2:2 4:4 2:4")

    /** FOX: a baroque run — up the scale in 16ths, a leap back and a turn home. */
    private val foxSig = Motif("0:1 1:1 2:1 3:1 4:2 2:2 3:1 2:1 1:1 0:1 1:2 .:2")
    private val foxAns = Motif("4:2 3:2 2:2 1:2 0:4 .:4")

    /** WOLF: a fiddle-and-harmonica lick — climb to the fifth, lean on it and walk home. */
    private val wolfSig = Motif("0:2 2:2 4:3 3:1 2:2 0:2 1:4")
    private val wolfAns = Motif("4:2 4:2 2:2 3:2 2:2 1:2 0:4")

    /** HAWK: a chant — the root twice, up to the fifth held, and down past the root. */
    private val hawkSig = Motif("0:2 0:2 2:4 1:2 0:2 -1:4")
    private val hawkAns = Motif("2:2 4:2 2:2 1:2 0:6 .:2")

    // ---- Tempos (each genre at its own speed, a notch quicker the deeper you go) -------------

    // Zones in order: ROOFTOP, TOWER, LABS, METRO, MINES, MAGMA, HELL, VOID.
    private val HOT_BPM = arrayOf(
        floatArrayOf(136f, 138f, 140f, 140f, 138f, 142f, 150f, 144f), // BULL: trap, half-time
        floatArrayOf(150f, 152f, 156f, 158f, 152f, 160f, 172f, 164f), // FOX: presto
        floatArrayOf(150f, 154f, 158f, 160f, 154f, 164f, 176f, 168f), // WOLF: train beat
        floatArrayOf(118f, 122f, 126f, 128f, 120f, 132f, 144f, 136f), // HAWK: war drums
    )
    private val SNEAK_BPM = arrayOf(
        floatArrayOf(84f, 85f, 86f, 87f, 86f, 88f, 92f, 90f), // BULL: boom-bap
        floatArrayOf(92f, 94f, 96f, 98f, 94f, 100f, 104f, 100f), // FOX: pizzicato
        floatArrayOf(78f, 80f, 82f, 84f, 80f, 86f, 92f, 88f), // WOLF: slow country shuffle
        floatArrayOf(88f, 90f, 92f, 94f, 90f, 96f, 102f, 98f), // HAWK: hand drums
    )

    // ---- Loudness trims (measured: each arrangement matches its zone's own track) ----------

    private val HOT_TRIM = arrayOf(
        floatArrayOf(0.86f, 0.88f, 0.89f, 0.94f, 1.00f, 0.98f, 1.10f, 0.89f), // BULL
        floatArrayOf(1.16f, 1.15f, 1.08f, 1.11f, 1.14f, 1.14f, 1.17f, 1.07f), // FOX
        floatArrayOf(1.22f, 1.16f, 1.19f, 1.19f, 1.20f, 1.20f, 1.29f, 1.12f), // WOLF
        floatArrayOf(0.87f, 0.87f, 0.86f, 0.91f, 0.97f, 0.94f, 1.10f, 0.89f), // HAWK
    )
    private val SNEAK_TRIM = arrayOf(
        floatArrayOf(0.83f, 0.82f, 0.80f, 0.80f, 0.85f, 0.81f, 0.81f, 0.87f), // BULL
        floatArrayOf(1.02f, 1.05f, 1.01f, 1.05f, 1.05f, 1.07f, 1.10f, 1.07f), // FOX
        floatArrayOf(1.01f, 1.01f, 1.05f, 1.06f, 1.17f, 1.00f, 1.11f, 1.12f), // WOLF
        floatArrayOf(0.89f, 0.88f, 0.84f, 0.89f, 0.93f, 0.93f, 0.91f, 0.92f), // HAWK
    )
    private val THEME_TRIM = floatArrayOf(0.83f, 1.11f, 1.16f, 0.83f)

    /** A drum tuned to the zone's key: its tonic, on or above MIDI note [lo]. */
    private fun keyed(base: SongSpec, lo: Int): Float = Dsp.midiToHz((lo + Math.floorMod(base.tonic - lo, 12)).toFloat())

    // ---- BULL: boom-bap when sneaking, trap when the guns come out -----------------------------

    private val rhodes = Patch(
        wave1 = Wave.SINE, wave2 = Wave.TRIANGLE, osc2Semi = 12f, osc2Level = 0.18f, detune = 0f, cutoff = 2600f,
        keyTrack = 0.2f, a = 0.003f, d = 2.2f, s = 0.25f, r = 0.5f, trem = 0.22f, tremRate = 4.5f, drive = 0.15f,
        gain = 0.15f, bright = 0.2f,
    )
    private val subBass = Patch(
        wave1 = Wave.SINE, wave2 = Wave.TRIANGLE, osc2Level = 0.12f, sub = 0.25f, cutoff = 420f, keyTrack = 0f,
        a = 0.004f, d = 0.5f, s = 0.45f, r = 0.15f, drive = 0.2f, gain = 0.36f, bright = 0.1f,
    )
    private val mutedGuitar = Patch(pluck = 0.35f, ring = 0.5f, cutoff = 2400f, a = 0.001f, d = 1f, s = 1f, r = 0.08f, gain = 0.45f, bright = 0.2f)
    private val whistle = Patch(
        wave1 = Wave.SINE, wave2 = Wave.TRIANGLE, osc2Semi = 12f, osc2Level = 0.15f, noise = 0.02f, cutoff = 3000f,
        a = 0.05f, d = 0.5f, s = 0.8f, r = 0.25f, glide = 0.04f, vibrato = 0.3f, gain = 0.13f, bright = 0.2f,
    )

    private fun bullSneak(base: SongSpec, t: Tint, name: String, bpm: Float) = base.derive(
        name = name, bpm = bpm, swing = 0.28f,
        progA = jazz(base.progA, base.scale, ninth = true), progB = jazz(base.progB, base.scale, ninth = true),
        drumsA = DrumPattern(kick = "X......X..X.....", snare = "....X.......X...", hat = "x.x.x.x.x.x.x.x.", perc = "..............o."),
        drumsB = DrumPattern(
            kick = "X.....X...X..X..", snare = "....X..o....X...", hat = "x.x.x.x.x.xox.x.", open = "..............x.",
        ),
        fill = DrumPattern(kick = "X......X..X.....", snare = "....X.......XoXo", hat = "x.x.x.x.x.x.x.x."),
        kit = DrumTuning(
            kickHi = 105f, kickLo = 44f, kickPitchDecay = 0.045f, kickDecay = 0.42f, kickClick = 0.12f, kickDrive = 0.35f,
            kickLevel = 0.95f, snareTone = 190f, snareNoiseHz = 3000f, snareDecay = 0.17f, snareToneMix = 0.5f,
            snareLevel = 0.7f, snareVerb = 0.18f, hatTone = 0.75f, hatDecay = 0.05f, openDecay = 0.25f, hatLevel = 0.2f,
            percHz = 1700f, percRatio = 1.5f, percDecay = 0.03f, percFm = 0.6f, percNoise = 0.3f, percLevel = 0.25f,
            drive = 0.25f + t.drive * 0.5f, crush = maxOf(3, t.crush),
        ),
        bassA = "R......R..R.....", bassB = "R.....R...F..R..",
        arpA = "......2...4.3...", arpB = "..3.2.......1.0.", arpGate = 1f,
        padRhythm = "x.......-.x...-.", padRhythmB = "x..x....-.x.....",
        leadTemplates = arrayOf(bullSig.rhythm, "x.......x.......", "x...........x..."),
        signature = bullSig, answer = bullAns, leadOctave = leadOctave(base, 60),
        pad = rhodes.tinted(t, 0.1f), bass = subBass, arp = mutedGuitar.tinted(t, 0.1f), lead = whistle,
        mix = Mix(
            pad = 1f, bass = 1f, arp = 1.1f, lead = 0.8f, drums = 0.6f, padVerb = 0.25f, arpDelay = 0.35f, arpVerb = 0.3f,
            leadDelay = 0.3f, leadVerb = 0.3f, padDuck = 0.2f, bassDuck = 0.1f, arpDuck = 0.1f, arpPan = -0.3f,
        ),
        crowd = 0f, vinyl = 0.06f,
    )

    private val eightOhEight = Patch(
        wave1 = Wave.SINE, wave2 = Wave.TRIANGLE, osc2Level = 0.15f, cutoff = 1400f, keyTrack = 0f, a = 0.002f,
        d = 1.6f, s = 0.45f, r = 0.12f, drive = 0.9f, glide = 0.06f, gain = 0.4f, bright = 0.2f,
    )
    private val darkStrings = Patch(
        wave1 = Wave.SAW, supersaw = true, detune = 0.12f, cutoff = 900f, q = 0.8f, envAmt = 0.6f, keyTrack = 0.1f,
        a = 0.6f, d = 1.5f, s = 0.85f, r = 1.2f, fa = 0.8f, fd = 2f, fs = 0.4f, gain = 0.1f, bright = 0.5f,
    )
    private val trapBell = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 19f, osc2Level = 0.3f, detune = 0f, cutoff = 5000f,
        keyTrack = 0f, a = 0.001f, d = 0.9f, s = 0f, r = 0.6f, gain = 0.17f, bright = 0.2f,
    )
    private val trapLead = Patch(
        wave1 = Wave.SQUARE, wave2 = Wave.SAW, osc2Level = 0.35f, detune = 0.08f, cutoff = 1600f, q = 1.1f, envAmt = 1f,
        a = 0.01f, d = 0.4f, s = 0.7f, r = 0.15f, fd = 0.3f, fs = 0.3f, glide = 0.05f, vibrato = 0.25f, gain = 0.14f,
        bright = 0.5f,
    )

    private fun bullHot(base: SongSpec, t: Tint, name: String, bpm: Float, hook: Melody? = null) = base.derive(
        name = name, bpm = bpm, swing = 0f,
        drumsA = DrumPattern(
            kick = "X.........X..X..", snare = "........X.......", clap = "........X.......", hat = "x.x.x.x.x.x.x.rr",
        ),
        drumsB = DrumPattern(
            kick = "X......X..X.....", snare = "........X.....o.", clap = "........X.......", hat = "x.xtx.x.x.x.tttt",
            open = "......x.........",
        ),
        fill = DrumPattern(kick = "X.........X.X.X.", snare = "........X.X.XXXX", clap = "........X.......", hat = "x.x.x.x.rrrrqqqq"),
        kit = DrumTuning(
            kickHi = 130f, kickLo = 40f, kickPitchDecay = 0.04f, kickDecay = 0.75f, kickClick = 0.3f, kickDrive = 0.5f,
            snareTone = 210f, snareNoiseHz = 5800f, snareDecay = 0.12f, snareToneMix = 0.35f, snareLevel = 0.75f,
            snareVerb = 0.25f, clapHz = 1350f, clapDecay = 0.16f, clapLevel = 0.75f, hatTone = 1.3f, hatDecay = 0.028f,
            openDecay = 0.18f, hatLevel = 0.3f, crashLevel = 0.3f, drive = t.drive * 0.6f, crush = t.crush,
        ),
        bassA = "R~~~~~~~~.R~~~O~", bassB = "R~~~~~.R~.F~~~~R", bassSlide = true,
        arpA = "0..2..4..3..2...", arpB = "4..3..2..0..1...", arpGate = 1.5f,
        padRhythm = "x...............",
        leadOctave = leadOctave(base, 58),
        leadTemplates = arrayOf(bullSig.rhythm, "x..x..x.x.......", "x.....x...x.x..."),
        motifSeed = base.motifSeed + 11, hook = hook, signature = bullSig, answer = bullAns,
        pad = darkStrings.tinted(t, 0.1f), bass = eightOhEight.tinted(t, 0.3f), arp = trapBell, lead = trapLead.tinted(t, 0.3f),
        mix = Mix(
            pad = 0.8f, bass = 0.85f, arp = 1.3f, lead = 1f, drums = 0.4f * zoneDrums(base), padVerb = 0.3f, arpDelay = 0.35f,
            arpVerb = 0.3f, leadDelay = 0.25f, leadVerb = 0.25f, padDuck = 0.3f, bassDuck = 0f, arpDuck = 0.1f, arpPan = 0.25f,
        ),
        crowd = 0f,
    )

    // ---- FOX: a string quartet tiptoeing on pizzicato, then a baroque presto -------------------

    private val pizzBass = Patch(pluck = 0.25f, ring = 0.6f, cutoff = 1400f, keyTrack = 0.3f, a = 0.001f, d = 1f, s = 1f, r = 0.1f, gain = 0.9f, bright = 0.1f)
    private val pizzViolins = Patch(pluck = 0.45f, ring = 0.4f, cutoff = 3500f, a = 0.001f, d = 1f, s = 1f, r = 0.08f, gain = 0.45f, bright = 0.1f)
    private val softStrings = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SAW, osc2Level = 0.8f, detune = 0.12f, cutoff = 1000f, q = 0.6f, envAmt = 0.4f,
        a = 0.8f, d = 1.5f, s = 0.85f, r = 1.2f, fa = 0.8f, fd = 1.5f, fs = 0.5f, vibrato = 0.12f, vibRate = 5f, gain = 0.07f,
        bright = 0.3f,
    )
    private val clarinet = Patch(
        wave1 = Wave.SQUARE, wave2 = Wave.SINE, osc2Semi = 12f, osc2Level = 0.12f, cutoff = 1600f, q = 0.7f, envAmt = 0.5f,
        a = 0.04f, d = 0.5f, s = 0.8f, r = 0.15f, fa = 0.04f, fd = 0.3f, fs = 0.5f, glide = 0.02f, vibrato = 0.12f,
        gain = 0.11f, bright = 0.3f,
    )

    private fun foxSneak(base: SongSpec, t: Tint, name: String, bpm: Float) = base.derive(
        name = name, bpm = bpm, swing = 0f,
        drumsA = DrumPattern(kick = "X.......x.......", tom = "..............1."),
        drumsB = DrumPattern(kick = "X.......x.......", tom = "1.......2.....1."),
        fill = DrumPattern(kick = "X.......x.......", snare = "........rrrrrrrr", tom = "........1.1.2.21"),
        kit = DrumTuning(
            kickHi = 85f, kickLo = 50f, kickPitchDecay = 0.06f, kickDecay = 0.5f, kickClick = 0.02f, kickDrive = 0f, kickLevel = 0.8f,
            snareTone = 180f, snareNoiseHz = 2500f, snareDecay = 0.22f, snareToneMix = 0.2f, snareLevel = 0.3f, snareVerb = 0.5f,
            tomHz = keyed(base, 41), tomDecay = 1.1f, tomBend = 0.08f, tomLevel = 0.6f, drive = 0f, crush = t.crush,
        ),
        bassA = "R...F...O...F...", bassB = "R.S.T...F.T.S.A.",
        arpA = "0.1.2...3.2.1...", arpB = "2.1.0...1.2.3.4.", arpGate = 1f,
        padRhythm = "x...............",
        leadTemplates = arrayOf(foxSig.rhythm, "x.......x.......", "x...........x..."),
        signature = foxSig, answer = foxAns, leadOctave = leadOctave(base, 58),
        pad = softStrings.tinted(t, 0f), bass = pizzBass, arp = pizzViolins, lead = clarinet,
        mix = Mix(
            pad = 0.7f, bass = 1f, arp = 1f, lead = 0.9f, drums = 0.8f, padVerb = 0.45f, arpDelay = 0.15f, arpVerb = 0.4f,
            leadDelay = 0.15f, leadVerb = 0.4f, padDuck = 0.1f, bassDuck = 0.05f, arpDuck = 0f, arpPan = 0.3f,
        ),
        crowd = 0f,
    )

    private val celloSpiccato = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SAW, osc2Level = 0.5f, detune = 0.08f, sub = 0.2f, cutoff = 650f, q = 0.8f,
        envAmt = 1.4f, keyTrack = 0.3f, a = 0.006f, d = 0.2f, s = 0.6f, r = 0.06f, fd = 0.12f, gain = 0.3f, bright = 0.4f,
    )
    private val violinSection = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SAW, osc2Level = 0.6f, detune = 0.1f, cutoff = 2600f, q = 0.7f, envAmt = 1.2f,
        a = 0.004f, d = 0.12f, s = 0.35f, r = 0.05f, fd = 0.08f, vibrato = 0.05f, gain = 0.13f, bright = 0.5f,
    )
    private val harpsichord = Patch(pluck = 0.9f, ring = 1.2f, cutoff = 5000f, keyTrack = 0f, a = 0.001f, d = 2f, s = 1f, r = 0.2f, gain = 0.24f, bright = 0.2f)
    private val soloViolin = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SAW, osc2Level = 0.2f, detune = 0.06f, noise = 0.02f, cutoff = 3200f, q = 0.8f,
        envAmt = 0.6f, a = 0.03f, d = 0.4f, s = 0.85f, r = 0.12f, fa = 0.03f, fd = 0.3f, fs = 0.5f, glide = 0.02f,
        vibrato = 0.35f, vibRate = 5.8f, gain = 0.15f, bright = 0.5f,
    )

    private fun foxHot(base: SongSpec, t: Tint, name: String, bpm: Float, hook: Melody? = null) = base.derive(
        name = name, bpm = bpm, swing = 0f,
        drumsA = DrumPattern(kick = "X.......X.......", tom = "1.......2......."),
        drumsB = DrumPattern(kick = "X...X...X...X...", tom = "1...2...1...2.21"),
        fill = DrumPattern(kick = "X.......X.......", snare = "rrrrrrrrrrrrrrrX", tom = "1.1.2.2.1.1.2.21"),
        kit = DrumTuning(
            kickHi = 90f, kickLo = 48f, kickPitchDecay = 0.05f, kickDecay = 0.45f, kickClick = 0.05f, kickDrive = 0.1f,
            kickLevel = 0.85f, snareTone = 200f, snareNoiseHz = 3800f, snareDecay = 0.16f, snareToneMix = 0.3f,
            snareLevel = 0.45f, snareVerb = 0.45f, tomHz = keyed(base, 41), tomDecay = 0.9f, tomBend = 0.06f, tomLevel = 0.8f,
            crashDecay = 2.4f, crashLevel = 0.3f, drive = t.drive * 0.3f, crush = t.crush,
        ),
        bassA = "R.R.R.R.O.O.R.R.", bassB = "R.R.F.F.O.O.F.A.",
        arpA = "0212021202120212", arpB = "3242324232423242", arpGate = 0.7f,
        padRhythm = "x.......x.......", padRhythmB = "x...x...x...x...",
        leadOctave = leadOctave(base, 62),
        leadTemplates = arrayOf(foxSig.rhythm, "x.x.x.x.x...x...", "x...x.x.x.x.x..."),
        motifSeed = base.motifSeed + 23, hook = hook, signature = foxSig, answer = foxAns,
        pad = harpsichord, bass = celloSpiccato.tinted(t, 0.2f), arp = violinSection.tinted(t, 0.1f), lead = soloViolin.tinted(t, 0.2f),
        mix = Mix(
            pad = 1f, bass = 1f, arp = 1.2f, lead = 1.2f, drums = 0.45f * zoneDrums(base), padVerb = 0.3f, arpDelay = 0.1f,
            arpVerb = 0.35f, leadDelay = 0.15f, leadVerb = 0.35f, padDuck = 0.1f, bassDuck = 0.1f, arpDuck = 0.05f, arpPan = 0.3f,
        ),
        crowd = 0f,
    )

    // ---- WOLF: a slow country shuffle, then a train-beat hoedown ---------------------------

    /** WOLF plays country in the zone's major key: I–IV–V (a V7) with a little ii and vi. */
    private fun country(vararg degrees: Int) = Array(degrees.size) {
        val d = degrees[it]
        Chord.diatonic(IONIAN, d, seventh = d == 4)
    }

    private val countryBass = Patch(
        wave1 = Wave.TRIANGLE, wave2 = Wave.SINE, osc2Semi = 12f, osc2Level = 0.2f, sub = 0.3f, cutoff = 700f,
        envAmt = 1.2f, a = 0.003f, d = 0.4f, s = 0.5f, r = 0.1f, fd = 0.15f, gain = 0.38f, bright = 0.3f,
    )
    private val steelGuitar = Patch(pluck = 0.7f, ring = 1.2f, cutoff = 4000f, a = 0.001f, d = 1f, s = 1f, r = 0.15f, gain = 0.2f, bright = 0.2f)
    private val pedalSteel = Patch(
        wave1 = Wave.SINE, wave2 = Wave.TRIANGLE, osc2Semi = 12f, osc2Level = 0.3f, detune = 0.04f, cutoff = 2000f,
        a = 0.5f, d = 1f, s = 0.9f, r = 1f, vibrato = 0.15f, vibRate = 4.5f, gain = 0.09f, bright = 0.2f,
    )
    private val harmonica = Patch(
        wave1 = Wave.PULSE, wave2 = Wave.SQUARE, pw = 0.3f, osc2Level = 0.3f, noise = 0.04f, cutoff = 2300f, q = 1f,
        envAmt = 0.6f, a = 0.03f, d = 0.4f, s = 0.8f, r = 0.15f, fa = 0.03f, fd = 0.3f, fs = 0.5f, glide = 0.03f,
        vibrato = 0.3f, vibRate = 5.5f, trem = 0.15f, tremRate = 6f, gain = 0.12f, bright = 0.3f,
    )

    private fun wolfSneak(base: SongSpec, t: Tint, name: String, bpm: Float) = base.derive(
        name = name, bpm = bpm, swing = 0.22f, scale = IONIAN, progA = country(0, 4, 3, 0), progB = country(3, 0, 1, 4),
        drumsA = DrumPattern(kick = "X.......X.......", snare = "....x.......x...", hat = "x.o.x.o.x.o.x.o."),
        drumsB = DrumPattern(
            kick = "X.....x.X.......", snare = "....x.......x.o.", hat = "x.o.x.o.x.o.x.o.", perc = "......x.......x.",
        ),
        fill = DrumPattern(kick = "X.......X.......", snare = "....x...x.x.xoxo", hat = "x.o.x.o.x.o.x.o."),
        kit = DrumTuning(
            kickHi = 100f, kickLo = 50f, kickDecay = 0.4f, kickClick = 0.05f, kickDrive = 0.1f, snareTone = 170f,
            snareNoiseHz = 2400f, snareDecay = 0.3f, snareToneMix = 0.05f, snareLevel = 0.45f, snareVerb = 0.3f,
            hatTone = 0.6f, hatDecay = 0.07f, hatLevel = 0.14f, percHz = 2000f, percRatio = 1.5f, percDecay = 0.03f,
            percFm = 0.5f, percNoise = 0.4f, percLevel = 0.2f, drive = t.drive * 0.3f, crush = t.crush,
        ),
        bassA = "R.......F.......", bassB = "R...T...F...A...",
        arpA = "0.3.2.4.1.3.2.4.", arpB = "0.4.2.3.1.4.2.3.", arpGate = 2f,
        padRhythm = "x...............",
        leadTemplates = arrayOf(wolfSig.rhythm, "x.......x.......", "x...........x..."),
        signature = wolfSig, answer = wolfAns, leadOctave = leadOctave(base, 60),
        pad = pedalSteel, bass = countryBass, arp = steelGuitar.tinted(t, 0.1f), lead = harmonica,
        mix = Mix(
            pad = 0.6f, bass = 0.9f, arp = 1.3f, lead = 0.9f, drums = 0.8f, padVerb = 0.35f, arpDelay = 0.2f, arpVerb = 0.3f,
            leadDelay = 0.2f, leadVerb = 0.3f, padDuck = 0.1f, bassDuck = 0.05f, arpDuck = 0f, arpPan = -0.25f,
        ),
        crowd = 0f,
    )

    private val boomChickBass = Patch(
        wave1 = Wave.TRIANGLE, wave2 = Wave.SQUARE, osc2Level = 0.2f, sub = 0.3f, cutoff = 900f, envAmt = 1.5f,
        a = 0.002f, d = 0.25f, s = 0.5f, r = 0.06f, fd = 0.12f, gain = 0.36f, bright = 0.4f,
    )
    private val banjo = Patch(pluck = 1f, ring = 0.5f, cutoff = 6000f, keyTrack = 0f, a = 0.001f, d = 1f, s = 1f, r = 0.05f, gain = 0.15f, bright = 0.1f)
    private val acousticStrum = Patch(pluck = 0.6f, ring = 0.9f, cutoff = 3500f, a = 0.001f, d = 1f, s = 1f, r = 0.08f, gain = 0.17f, bright = 0.3f)
    private val fiddle = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SAW, osc2Level = 0.3f, detune = 0.07f, noise = 0.015f, cutoff = 3400f, q = 0.9f,
        envAmt = 0.6f, a = 0.02f, d = 0.3f, s = 0.85f, r = 0.1f, fa = 0.02f, fd = 0.25f, fs = 0.5f, glide = 0.025f,
        vibrato = 0.3f, vibRate = 6f, drive = 0.1f, gain = 0.14f, bright = 0.5f,
    )

    private fun wolfHot(base: SongSpec, t: Tint, name: String, bpm: Float, hook: Melody? = null): SongSpec {
        // A theme brings its own (major) chords; a zone's track gets the country ones.
        val own = base.scale.contentEquals(IONIAN)
        return base.derive(
            name = name, bpm = bpm, swing = 0f, scale = IONIAN,
            progA = if (own) base.progA else country(0, 3, 0, 4), progB = if (own) base.progB else country(5, 3, 0, 4),
            drumsA = DrumPattern(kick = "X.......X.......", snare = "oxoxXoxooxoxXoxo"),
            drumsB = DrumPattern(kick = "X...X...X...X...", snare = "oxoxXoxoxoxoXoxx"),
            fill = DrumPattern(kick = "X.......X.......", snare = "oxoxXoxoXXXXXXXX", tom = "........3.3.2.1."),
            kit = DrumTuning(
                kickHi = 120f, kickLo = 50f, kickDecay = 0.3f, kickClick = 0.4f, kickDrive = 0.2f, snareTone = 190f,
                snareNoiseHz = 4200f, snareDecay = 0.09f, snareToneMix = 0.35f, snareLevel = 0.55f, snareVerb = 0.2f,
                tomHz = 110f, crashLevel = 0.3f, drive = t.drive * 0.5f, crush = t.crush,
            ),
            bassA = "R...F...R...F...", bassB = "R...F...O...A...",
            arpA = "3423423423423423", arpB = "2435243524352435", arpGate = 0.9f, arpCenter = base.arpCenter + 2,
            padRhythm = "..x.-.x.-.x.-.x.", padRhythmB = "x.x.-.x.x.x.-.x.",
            leadOctave = leadOctave(base, 62),
            leadTemplates = arrayOf(wolfSig.rhythm, "x.x.x...x.x.x...", "x...x.x.x..xx..."),
            motifSeed = base.motifSeed + 37, hook = hook, signature = wolfSig, answer = wolfAns,
            pad = acousticStrum, bass = boomChickBass.tinted(t, 0.2f), arp = banjo, lead = fiddle.tinted(t, 0.2f),
            mix = Mix(
                pad = 1.2f, bass = 1f, arp = 1.3f, lead = 1.2f, drums = 0.5f * zoneDrums(base), padVerb = 0.2f, arpDelay = 0.15f,
                arpVerb = 0.25f, leadDelay = 0.2f, leadVerb = 0.25f, padDuck = 0.15f, bassDuck = 0.15f, arpDuck = 0.05f, arpPan = -0.3f,
            ),
            crowd = 0f,
        )
    }

    // ---- HAWK: hand drums and a marimba in the dark, then war drums ---------------------------

    private val marimba = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 24f, osc2Level = 0.22f, detune = 0f, cutoff = 5000f,
        keyTrack = 0f, a = 0.001f, d = 0.45f, s = 0f, r = 0.3f, gain = 0.22f, bright = 0.1f,
    )
    private val jungleAir = Patch(
        wave1 = Wave.SAW, supersaw = true, detune = 0.1f, noise = 0.05f, cutoff = 600f, a = 2f, d = 1.5f, s = 0.8f, r = 2f,
        gain = 0.08f, bright = 0.1f,
    )
    private val woodFlute = Patch(
        wave1 = Wave.SINE, wave2 = Wave.TRIANGLE, osc2Semi = 12f, osc2Level = 0.12f, noise = 0.05f, cutoff = 2600f,
        a = 0.06f, d = 0.4f, s = 0.8f, r = 0.2f, glide = 0.05f, vibrato = 0.3f, vibRate = 5f, gain = 0.15f, bright = 0.2f,
    )

    private fun hawkSneak(base: SongSpec, t: Tint, name: String, bpm: Float) = base.derive(
        name = name, bpm = bpm, swing = 0.12f,
        drumsA = DrumPattern(kick = "X.x.....X.x.....", tom = "......1.....1.2.", perc = "...x.......x....", jingle = "..o...o...o...o."),
        drumsB = DrumPattern(kick = "X.x.....X.x.....", tom = "..1...1.2..1.2..", perc = "...x......x...x.", jingle = "o.o.o.o.o.o.o.o."),
        fill = DrumPattern(kick = "X.x.....X.x.....", tom = "........1.1.2.33", jingle = "oooooooooooooooo"),
        kit = DrumTuning(
            kickHi = 80f, kickLo = 42f, kickPitchDecay = 0.05f, kickDecay = 0.5f, kickClick = 0.03f, kickDrive = 0.15f,
            snareLevel = 0.3f, hatLevel = 0f, tomHz = keyed(base, 52), tomDecay = 0.2f, tomBend = 0.12f, tomLevel = 0.55f,
            percHz = 520f, percRatio = 1.5f, percDecay = 0.09f, percFm = 0.5f, percNoise = 0.2f, percLevel = 0.3f,
            jingleHz = 7500f, jingleDecay = 0.05f, jingleNoise = 1f, jingleLevel = 0.18f, drive = t.drive * 0.3f, crush = t.crush,
        ),
        bassA = "R~~~~~~~~~~~~~~~", bassB = "R~~~~~~~~~~~~~~~",
        arpA = "0...2...3.2.....", arpB = "2.0...3...1...0.", arpGate = 1f,
        padRhythm = "x...............",
        leadTemplates = arrayOf(hawkSig.rhythm, "x.......x.......", "x...........x..."),
        signature = hawkSig, answer = hawkAns, leadOctave = leadOctave(base, 57),
        pad = jungleAir.tinted(t, 0f),
        bass = Patch(wave1 = Wave.SINE, sub = 0.4f, cutoff = 300f, a = 0.6f, d = 1f, s = 1f, r = 1.2f, gain = 0.14f, bright = 0.1f),
        arp = marimba, lead = woodFlute,
        mix = Mix(
            pad = 0.6f, bass = 0.9f, arp = 1.3f, lead = 0.8f, drums = 0.65f, padVerb = 0.5f, arpDelay = 0.45f, arpVerb = 0.45f,
            leadDelay = 0.35f, leadVerb = 0.45f, padDuck = 0.15f, bassDuck = 0.1f, arpDuck = 0f, arpPan = 0.35f,
        ),
        crowd = 0f, jungle = 0.1f,
    )

    private val tribalBass = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SINE, osc2Semi = -12f, osc2Level = 0.5f, sub = 0.4f, cutoff = 280f, q = 1.1f,
        envAmt = 1.8f, keyTrack = 0.4f, a = 0.002f, d = 0.2f, s = 0.4f, r = 0.06f, fd = 0.1f, drive = 0.2f, gain = 0.32f,
        bright = 0.4f,
    )
    private val kalimba = marimba.copyish(gain = 0.2f)
    private val chantChoir = Patch(
        wave1 = Wave.SAW, wave2 = Wave.TRIANGLE, osc2Semi = 12f, osc2Level = 0.6f, detune = 0.1f, cutoff = 1000f, q = 0.9f,
        envAmt = 0.5f, a = 0.5f, d = 1f, s = 0.85f, r = 1f, vibrato = 0.1f, gain = 0.1f, bright = 0.4f,
    )
    private val chantLead = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SQUARE, osc2Level = 0.4f, detune = 0.06f, noise = 0.02f, cutoff = 1500f, q = 1.2f,
        envAmt = 0.8f, a = 0.03f, d = 0.4f, s = 0.8f, r = 0.15f, fa = 0.03f, fd = 0.3f, fs = 0.4f, glide = 0.03f,
        vibrato = 0.25f, vibRate = 5f, drive = 0.15f, gain = 0.15f, bright = 0.4f,
    )

    private fun hawkHot(base: SongSpec, t: Tint, name: String, bpm: Float, hook: Melody? = null) = base.derive(
        name = name, bpm = bpm, swing = 0f,
        drumsA = DrumPattern(
            kick = "X.....X...X.....", snare = "....x.......x...", tom = "1..2..1..2..1.3.", perc = "....x.......x...",
            jingle = "oxoxoxoxoxoxoxox",
        ),
        drumsB = DrumPattern(
            kick = "X..X..X..X..X.X.", snare = "....x.......x...", tom = "1.21..2.1.21.3.3", perc = "..x..x..x..x..x.",
            jingle = "oxoxoxoxoxoxoxox",
        ),
        fill = DrumPattern(kick = "X...X...X...X...", snare = "........rrrrrrrX", tom = "1.1.2.2.3.3.2.13"),
        kit = DrumTuning(
            kickHi = 95f, kickLo = 40f, kickPitchDecay = 0.06f, kickDecay = 0.6f, kickClick = 0.2f, kickDrive = 0.4f,
            snareTone = 200f, snareNoiseHz = 3000f, snareDecay = 0.12f, snareToneMix = 0.4f, snareLevel = 0.5f,
            tomHz = keyed(base, 36), tomDecay = 0.65f, tomBend = 0.35f, tomLevel = 0.95f,
            percHz = 380f, percRatio = 1.5f, percDecay = 0.1f, percFm = 0.7f, percNoise = 0.1f, percLevel = 0.38f,
            jingleHz = 7000f, jingleDecay = 0.045f, jingleNoise = 1f, jingleLevel = 0.2f,
            crashLevel = 0.3f, crashDecay = 3f, drive = t.drive, crush = t.crush,
        ),
        bassA = "R..R..R.R..R..R.", bassB = "R..R..R.O..R..F.",
        arpA = "0.2.0.3.0.2.4.2.", arpB = "3.2.4.2.3.2.0.2.", arpGate = 0.6f,
        padRhythm = "x...............",
        leadOctave = leadOctave(base, 55),
        leadTemplates = arrayOf(hawkSig.rhythm, "x...x.x.x.......", "x..x..x.x...x..."),
        motifSeed = base.motifSeed + 53, hook = hook, signature = hawkSig, answer = hawkAns,
        pad = chantChoir.tinted(t, 0.1f), bass = tribalBass.tinted(t, 0.2f), arp = kalimba, lead = chantLead.tinted(t, 0.2f),
        mix = Mix(
            pad = 0.8f, bass = 1.2f, arp = 1.4f, lead = 1f, drums = 0.42f * zoneDrums(base), padVerb = 0.4f, arpDelay = 0.25f,
            arpVerb = 0.35f, leadDelay = 0.2f, leadVerb = 0.35f, padDuck = 0.3f, bassDuck = 0.25f, arpDuck = 0.1f, arpPan = 0.3f,
        ),
        crowd = 0f, jungle = 0.03f,
    )

    // ---- Themes (hero picker) --------------------------------------------------------------

    private fun tri(scale: IntArray, vararg degrees: Int) = Array(degrees.size) { Chord.diatonic(scale, degrees[it]) }

    /** A theme's skeleton: key, chords, tempo and section plan; the hero's band fills it in. */
    private fun themeBase(name: String, bpm: Float, tonic: Int, scale: IntArray, progA: Array<Chord>, progB: Array<Chord>, seed: Long) =
        Songs.title.derive(
            name = name, bpm = bpm, tonic = tonic, scale = scale, progA = progA, progB = progB, motifSeed = seed,
            hook = null, bassCenter = 36 + Math.floorMod(tonic - 36, 12).coerceAtMost(6), padCenter = 62, arpCenter = 62,
        )

    /** "Heavy Hooves": D minor trap, i–VII–VI–VII, a slow synth hook over sliding 808s. */
    private val bullHook = Melody(
        arrayOf(
            "A4:3 A4:1 -:2 G4:2 F4:3 E4:1 D4:4",
            "G4:3 G4:1 -:2 F4:2 E4:3 D4:1 C4:4",
            "F4:3 F4:1 -:2 D4:2 F4:2 A4:2 Bb4:4",
            "C5:4 -:2 A4:2 G4:4 E4:4",
            "A4:3 A4:1 -:2 G4:2 F4:3 E4:1 D4:4",
            "G4:3 G4:1 -:2 F4:2 E4:3 D4:1 C4:4",
            "F4:3 F4:1 -:2 A4:2 Bb4:2 C5:2 D5:4",
            "E5:4 -:2 C5:2 D5:8",
        ),
    )

    /** "Presto in a Pressed Tux": C dorian, a violin running 16ths over harpsichord and timpani. */
    private val foxHook = Melody(
        arrayOf(
            "C5:1 D5:1 Eb5:1 D5:1 C5:2 G4:2 Eb5:1 F5:1 G5:1 F5:1 Eb5:2 C5:2",
            "A4:1 Bb4:1 C5:1 Bb4:1 A4:2 F4:2 C5:1 D5:1 Eb5:1 D5:1 C5:2 A4:2",
            "G5:2 F5:1 Eb5:1 D5:2 C5:2 Bb4:2 C5:2 D5:2 Eb5:2",
            "F5:4 C5:2 A4:2 F4:4 -:4",
            "C5:1 D5:1 Eb5:1 D5:1 C5:2 G4:2 Eb5:1 F5:1 G5:1 F5:1 Eb5:2 C5:2",
            "A4:1 Bb4:1 C5:1 Bb4:1 A4:2 F4:2 C5:1 D5:1 Eb5:1 D5:1 C5:2 A4:2",
            "G5:1 A5:1 Bb5:1 A5:1 G5:2 Eb5:2 F5:1 G5:1 A5:1 G5:1 F5:2 D5:2",
            "C5:2 A4:2 F4:2 A4:2 C5:8",
        ),
    )

    /** "Wrong Town, Right Boots": a D major fiddle hoedown on a train beat, I–IV–I–V. */
    private val wolfHook = Melody(
        arrayOf(
            "F#5:2 A5:2 F#5:2 D5:2 E5:2 F#5:2 E5:2 D5:2",
            "B4:2 D5:2 G5:4 F#5:2 E5:2 D5:4",
            "A4:2 D5:2 F#5:2 A5:2 B5:2 A5:2 F#5:4",
            "E5:4 C#5:2 E5:2 A4:8",
            "F#5:2 A5:2 F#5:2 D5:2 E5:2 F#5:2 E5:2 D5:2",
            "B4:2 D5:2 G5:4 F#5:2 E5:2 D5:4",
            "D5:2 F#5:2 A5:2 D6:2 B5:2 A5:2 F#5:4",
            "E5:2 F#5:2 E5:2 C#5:2 D5:8",
        ),
    )

    /** "Drums in the Canopy": a G minor chant over war drums, i–VI–VII–i. */
    private val hawkHook = Melody(
        arrayOf(
            "G4:4 G4:2 Bb4:2 D5:6 C5:2",
            "Bb4:4 G4:2 Bb4:2 Eb5:6 D5:2",
            "C5:4 A4:2 C5:2 F5:4 Eb5:2 D5:2",
            "D5:8 -:4 G4:2 A4:2",
            "G4:4 G4:2 Bb4:2 D5:6 C5:2",
            "Bb4:4 G4:2 Bb4:2 Eb5:6 D5:2",
            "A4:2 C5:2 F5:4 G5:2 F5:2 Eb5:2 D5:2",
            "G5:4 D5:4 G4:8",
        ),
    )

    private fun buildTheme(h: Hero): SongSpec {
        val t = tint(null)
        val trim = THEME_TRIM[h.ordinal]
        return when (h) {
            Hero.BULL -> bullHot(
                themeBase("bull-theme", 140f, 50, AEOLIAN, tri(AEOLIAN, 0, 6, 5, 6), arrayOf(Chord.diatonic(AEOLIAN, 3), Chord.diatonic(AEOLIAN, 0), Chord.diatonic(AEOLIAN, 5), Chord.of(AEOLIAN, 4, Quality.MAJ)), 2401),
                t, "bull-theme", 140f, bullHook,
            )
            Hero.FOX -> foxHot(
                themeBase("fox-theme", 156f, 48, DORIAN, tri(DORIAN, 0, 3, 0, 3), tri(DORIAN, 2, 3, 4, 0), 7007),
                t, "fox-theme", 156f, foxHook,
            )
            Hero.WOLF -> wolfHot(
                themeBase("wolf-theme", 160f, 50, IONIAN, tri(IONIAN, 0, 3, 0, 4), tri(IONIAN, 5, 3, 0, 4), 1988),
                t, "wolf-theme", 160f, wolfHook,
            )
            Hero.HAWK -> hawkHot(
                themeBase("hawk-theme", 124f, 55, AEOLIAN, tri(AEOLIAN, 0, 5, 6, 0), tri(AEOLIAN, 3, 5, 2, 6), 3161),
                t, "hawk-theme", 124f, hawkHook,
            )
        }.derive(gain = trim, fixedIntensity = 0.85f)
    }

    // ---- Tables ----------------------------------------------------------------------------

    private fun arrange(h: Hero, z: Zone, silent: Boolean): SongSpec {
        val base = Songs.forZone(z, silent)
        val t = tint(z)
        val name = if (silent) "${Songs.forZone(z).name}-${h.name.lowercase()}-sneak" else "${base.name}-${h.name.lowercase()}"
        val bpm = (if (silent) SNEAK_BPM else HOT_BPM)[h.ordinal][z.ordinal]
        val spec = when (h) {
            Hero.BULL -> if (silent) bullSneak(base, t, name, bpm) else bullHot(base, t, name, bpm)
            Hero.FOX -> if (silent) foxSneak(base, t, name, bpm) else foxHot(base, t, name, bpm)
            Hero.WOLF -> if (silent) wolfSneak(base, t, name, bpm) else wolfHot(base, t, name, bpm)
            Hero.HAWK -> if (silent) hawkSneak(base, t, name, bpm) else hawkHot(base, t, name, bpm)
        }
        return spec.derive(gain = (if (silent) SNEAK_TRIM else HOT_TRIM)[h.ordinal][z.ordinal])
    }

    private val hot = Array(Hero.entries.size) { h -> Array(Zone.entries.size) { z -> arrange(Hero.entries[h], Zone.entries[z], false) } }
    private val sneak = Array(Hero.entries.size) { h -> Array(Zone.entries.size) { z -> arrange(Hero.entries[h], Zone.entries[z], true) } }
    private val themes = Array(Hero.entries.size) { buildTheme(Hero.entries[it]) }

    val all: List<SongSpec> = hot.flatMap { it.toList() } + sneak.flatMap { it.toList() } + themes.toList()

    /** [zone]'s track in [hero]'s arrangement ([hero] null: the original soundtrack). */
    fun forZone(hero: Hero?, zone: Zone, silent: Boolean): SongSpec =
        if (hero == null) Songs.forZone(zone, silent) else (if (silent) sneak else hot)[hero.ordinal][zone.ordinal]

    /** [hero]'s signature theme (the hero picker). */
    fun theme(hero: Hero): SongSpec = themes[hero.ordinal]

    fun isTheme(s: SongSpec?): Boolean {
        for (t in themes) if (t === s) return true
        return false
    }
}

/** A copy of this spec with some fields replaced (arrangements are built from their zone's track). */
internal fun SongSpec.derive(
    name: String = this.name,
    bpm: Float = this.bpm,
    tonic: Int = this.tonic,
    scale: IntArray = this.scale,
    progA: Array<Chord> = this.progA,
    progB: Array<Chord> = this.progB,
    swing: Float = this.swing,
    drumsA: DrumPattern = this.drumsA,
    drumsB: DrumPattern = this.drumsB,
    fill: DrumPattern = this.fill,
    kit: DrumTuning = this.kit,
    bassA: String = this.bassA,
    bassB: String = this.bassB,
    arpA: String = this.arpA,
    arpB: String = this.arpB,
    arpGate: Float = this.arpGate,
    padRhythm: String = this.padRhythm,
    /** Defaults to [padRhythm] when that changes, else this spec's own. */
    padRhythmB: String? = null,
    bassCenter: Int = this.bassCenter,
    padCenter: Int = this.padCenter,
    arpCenter: Int = this.arpCenter,
    leadOctave: Int = this.leadOctave,
    leadTemplates: Array<String> = this.leadTemplates,
    motifSeed: Long = this.motifSeed,
    hook: Melody? = this.hook,
    pad: Patch = this.pad,
    bass: Patch = this.bass,
    arp: Patch = this.arp,
    lead: Patch = this.lead,
    mix: Mix = this.mix,
    fixedIntensity: Float = this.fixedIntensity,
    leadThreshold: Float = this.leadThreshold,
    padPower: Boolean = this.padPower,
    crowd: Float = this.crowd,
    jungle: Float = this.jungle,
    signature: Motif? = this.signature,
    answer: Motif? = this.answer,
    gain: Float = this.gain,
    wind: Float = this.wind,
    rotor: Float = this.rotor,
    bassSlide: Boolean = this.bassSlide,
    vinyl: Float = this.vinyl,
) = SongSpec(
    name = name, bpm = bpm, tonic = tonic, scale = scale, progA = progA, progB = progB, barsPerChord = barsPerChord,
    swing = swing, drumsA = drumsA, drumsB = drumsB, fill = fill, kit = kit, bassA = bassA, bassB = bassB,
    arpA = arpA, arpB = arpB, arpGate = arpGate, padRhythm = padRhythm, bassCenter = bassCenter, padCenter = padCenter,
    arpCenter = arpCenter, leadOctave = leadOctave, leadTemplates = leadTemplates, motifSeed = motifSeed, hook = hook,
    pad = pad, bass = bass, arp = arp, lead = lead, mix = mix, wind = wind, rotor = rotor, glitch = glitch,
    fixedIntensity = fixedIntensity, kickThreshold = kickThreshold, arpThreshold = arpThreshold,
    leadThreshold = leadThreshold, sections = sections, delayBeats = delayBeats,
    padRhythmB = padRhythmB ?: if (padRhythm == this.padRhythm) this.padRhythmB else padRhythm,
    padPower = padPower, crowd = crowd, jungle = jungle, signature = signature,
    answer = answer, gain = gain, bassSlide = bassSlide, vinyl = vinyl,
)
