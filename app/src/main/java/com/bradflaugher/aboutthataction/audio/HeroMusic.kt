package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.audio.Scales.AEOLIAN
import com.bradflaugher.aboutthataction.audio.Scales.DORIAN
import com.bradflaugher.aboutthataction.audio.Scales.HARMONIC_MINOR
import com.bradflaugher.aboutthataction.audio.Scales.IONIAN
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.sqrt

/**
 * Every hero's own soundtrack: each zone's track (GUNS HOT and its SILENT sneak mix) in the
 * hero's genre, plus a signature theme for the hero picker.
 *
 * An arrangement keeps what makes a zone that zone — its key (and chords, except LION's
 * circus, which recasts them in the harmonic minor sneaking and the parallel major hot), bars per chord, section plan, the ambience
 * (wind, rotor, VOID's glitches) and the intensity layering thresholds — and swaps in the
 * hero's band: tempo, patches, drum kit and every part, plus their signature motif (the
 * hero's leitmotif, transposed over every zone's chords). Each zone also tints the band:
 * cold zones darken filters, the deep ones add grit, the mines crush the drums.
 *
 *  - BULL: heavy hip-hop. Sneaking is a slow, menacing boom-bap head-nod at ~76 BPM (a fat,
 *    driven, dusty kick, a big cracking snare-and-clap, a deep sub, a dark felt-piano riff over
 *    a low string bed); GUNS HOT is heavy half-time trap at ~144 (distorted 808s that punch
 *    in and glide, a hard kick, snare-and-clap on three, hat rolls in 32nds, triplets and
 *    buzzes, dark bells and brass stabs) with a real drop: until the fight heats up the kick
 *    and 808 hold back, then a beat of silence under a reversed cymbal and it all lands.
 *  - FOX: classical. Sneaking is a chamber piece on tiptoe: pizzicato cello and violins,
 *    soft bowed strings, a clarinet and a timpani; GUNS HOT is a baroque presto at ~156
 *    (spiccato cellos, running violin figures, harpsichord continuo, timpani, a solo violin).
 *  - LION: circus. Sneaking is a creepy clown march on tiptoe at ~92 in the harmonic minor
 *    (a staccato bassoon oom-pah, pizzicato "pah" chords, a music box, temple blocks, a bulb
 *    horn honking as tension creeps in, and a wobbly, seasick calliope carrying his theme);
 *    GUNS HOT is a screaming circus galop at ~180 in the major (a steam calliope, tuba
 *    oom-pah, band-organ chords, snare rolls, cymbal crashes, xylophone runs and a slide
 *    whistle up through every fill).
 *  - HAWK: the courier's radio. Sneaking is elevator-muzak bossa nova at ~94 BPM (nylon
 *    guitar comping 7th/9th chords, vibraphone, a cross-stick clave, a shaker, a brushed kick
 *    and upright bass, a soft flute); GUNS HOT is 70s delivery-van funk at ~118 (a ghost-noted
 *    breakbeat, popping slap bass, a wah clavinet in 16ths, horn stabs, a cop-show synth lead,
 *    congas and cowbell as the heat climbs).
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

    /** BULL: a menacing hook — the fifth leaning on the flat sixth, then a slow fall to the root. */
    private val bullSig = Motif("4:3 5:1 4:2 .:2 2:3 1:1 0:4")
    private val bullAns = Motif("0:2 0:1 .:1 2:2 4:2 5:3 4:1 2:4")

    /** FOX: a baroque run — up the scale in 16ths, a leap back and a turn home. */
    private val foxSig = Motif("0:1 1:1 2:1 3:1 4:2 2:2 3:1 2:1 1:1 0:1 1:2 .:2")
    private val foxAns = Motif("4:2 3:2 2:2 1:2 0:4 .:4")

    /** LION: a showman's flourish — tiptoe up the chord to the octave, a twirl and a bow. */
    private val lionSig = Motif("2:1 .:1 4:1 .:1 7:3 6:1 4:1 5:1 4:1 3:1 2:2 1:1 -3:1")
    private val lionAns = Motif("4:1 3:1 4:1 .:1 2:1 1:1 2:1 .:1 0:1 .:1 4:1 .:1 0:4")

    /** HAWK: the doorbell — ding... dong, a deadpan "sign here" turn, and home, on time. */
    private val hawkSig = Motif("4:3 2:3 .:2 4:1 5:1 4:1 2:1 0:4")
    private val hawkAns = Motif("2:2 .:1 3:1 4:2 7:2 6:1 4:1 .:2 2:4")

    // ---- Tempos (each genre at its own speed, a notch quicker the deeper you go) -------------

    // Zones in order: ROOFTOP, TOWER, LABS, METRO, MINES, MAGMA, HELL, VOID.
    private val HOT_BPM = arrayOf(
        floatArrayOf(140f, 142f, 144f, 144f, 140f, 146f, 150f, 148f), // BULL: heavy trap, half-time
        floatArrayOf(150f, 152f, 156f, 158f, 152f, 160f, 172f, 164f), // FOX: presto
        floatArrayOf(172f, 176f, 178f, 180f, 174f, 182f, 190f, 184f), // LION: circus galop
        floatArrayOf(114f, 116f, 118f, 120f, 114f, 120f, 125f, 122f), // HAWK: delivery-van funk
    )
    private val SNEAK_BPM = arrayOf(
        floatArrayOf(74f, 75f, 76f, 77f, 74f, 78f, 82f, 80f), // BULL: heavy boom-bap
        floatArrayOf(92f, 94f, 96f, 98f, 94f, 100f, 104f, 100f), // FOX: pizzicato
        floatArrayOf(88f, 90f, 92f, 94f, 90f, 96f, 100f, 96f), // LION: tiptoe clown march
        floatArrayOf(90f, 92f, 94f, 96f, 92f, 96f, 100f, 98f), // HAWK: elevator bossa
    )

    // ---- Loudness trims (measured: each arrangement matches its zone's own track) ----------

    private val HOT_TRIM = arrayOf(
        floatArrayOf(0.78f, 0.78f, 0.77f, 0.79f, 0.79f, 0.82f, 0.86f, 0.75f), // BULL
        floatArrayOf(1.16f, 1.15f, 1.08f, 1.11f, 1.14f, 1.14f, 1.17f, 1.07f), // FOX
        floatArrayOf(1.38f, 1.32f, 1.34f, 1.34f, 1.36f, 1.41f, 1.46f, 1.23f), // LION
        floatArrayOf(0.98f, 0.93f, 0.95f, 0.95f, 0.99f, 0.96f, 1.09f, 0.92f), // HAWK
    )
    private val SNEAK_TRIM = arrayOf(
        floatArrayOf(0.75f, 0.71f, 0.70f, 0.70f, 0.72f, 0.74f, 0.70f, 0.73f), // BULL
        floatArrayOf(1.02f, 1.05f, 1.01f, 1.05f, 1.05f, 1.07f, 1.10f, 1.07f), // FOX
        floatArrayOf(1.08f, 1.09f, 1.05f, 1.11f, 1.21f, 1.14f, 1.16f, 1.15f), // LION
        floatArrayOf(0.93f, 0.90f, 0.92f, 0.89f, 0.91f, 0.94f, 0.92f, 0.93f), // HAWK
    )
    private val THEME_TRIM = floatArrayOf(0.73f, 1.11f, 1.33f, 0.91f)

    /** A drum tuned to the zone's key: its tonic, on or above MIDI note [lo]. */
    private fun keyed(base: SongSpec, lo: Int): Float = Dsp.midiToHz((lo + Math.floorMod(base.tonic - lo, 12)).toFloat())

    // ---- BULL: heavy boom-bap when sneaking, heavy trap when the guns come out ----------------

    /** A dusty felt piano: a dark strike that dies away, with a hint of tape wow. */
    private val darkKeys = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SAW, osc2Level = 0.14f, detune = 0.03f, cutoff = 1250f, q = 0.7f, envAmt = 1f,
        keyTrack = 0.3f, a = 0.002f, d = 1.6f, s = 0f, r = 0.5f, fd = 0.35f, vibrato = 0.05f, vibRate = 0.8f, gain = 0.2f,
        bright = 0.3f,
    )
    /** A low, dark string bed under the beat: the menace in the room. */
    private val shadowPad = Patch(
        wave1 = Wave.SAW, supersaw = true, detune = 0.1f, cutoff = 480f, q = 0.9f, keyTrack = 0.1f, a = 1.2f, d = 1.5f,
        s = 0.85f, r = 1.4f, vibrato = 0.05f, vibRate = 0.6f, gain = 0.09f, bright = 0.3f,
    )
    /** Deep sub: a sine with a touch of grit (so a phone speaker hears it), dying between kicks. */
    private val deepSub = Patch(
        wave1 = Wave.SINE, wave2 = Wave.TRIANGLE, osc2Level = 0.1f, cutoff = 600f, keyTrack = 0f, a = 0.004f, d = 0.6f,
        s = 0.3f, r = 0.18f, drive = 0.5f, gain = 0.42f, bright = 0.1f, pitchEnv = 3f, pitchDecay = 0.02f,
    )
    private val shadowLead = Patch(
        wave1 = Wave.SINE, wave2 = Wave.TRIANGLE, osc2Semi = 12f, osc2Level = 0.18f, noise = 0.015f, cutoff = 2000f,
        a = 0.06f, d = 0.5f, s = 0.8f, r = 0.3f, glide = 0.06f, vibrato = 0.35f, vibRate = 4.5f, gain = 0.12f, bright = 0.2f,
    )

    private fun bullSneak(base: SongSpec, t: Tint, name: String, bpm: Float) = base.derive(
        name = name, bpm = bpm, swing = 0.2f,
        progA = jazz(base.progA, base.scale, ninth = false), progB = jazz(base.progB, base.scale, ninth = false),
        drumsA = DrumPattern(
            kick = "X......x..X.....", snare = "....X.......X...", clap = "....X.......X...", hat = "x.o.x.o.x.o.x.oo",
            perc = "..............o.",
        ),
        drumsB = DrumPattern(
            kick = "X......x..X..x..", snare = "....X.......X...", clap = "....X.......X...", hat = "x.o.x.oox.o.x.o.",
            open = "..............x.", perc = "......o.......o.",
        ),
        fill = DrumPattern(kick = "X......x..X.....", snare = "....X.......X.oX", clap = "....X.......X...", hat = "x.o.x.o.x.o.x.o."),
        kit = DrumTuning(
            kickHi = 110f, kickLo = 45f, kickPitchDecay = 0.05f, kickDecay = 0.55f, kickClick = 0.1f, kickDrive = 0.4f,
            kickLevel = 1f, snareTone = 180f, snareNoiseHz = 2400f, snareDecay = 0.24f, snareToneMix = 0.6f,
            snareLevel = 1f, snareVerb = 0.2f, clapHz = 1000f, clapDecay = 0.22f, clapLevel = 0.6f,
            hatTone = 0.7f, hatDecay = 0.04f, openDecay = 0.22f, hatLevel = 0.12f,
            percHz = 1700f, percRatio = 1.5f, percDecay = 0.03f, percFm = 0.6f, percNoise = 0.3f, percLevel = 0.22f,
            drive = 0.25f + t.drive * 0.5f, crush = maxOf(2, t.crush), busCutoff = 6500f * t.dark,
        ),
        bassA = "R......R..R.....", bassB = "R......R..R..F..",
        arpA = "0.......2..1....", arpB = "0..0....4..2..1.", arpGate = 1f, arpCenter = base.arpCenter - 17,
        padRhythm = "x...............",
        leadTemplates = arrayOf(bullSig.rhythm, "x.......x.......", "x...........x..."),
        signature = bullSig, answer = bullAns, leadOctave = leadOctave(base, 57),
        pad = shadowPad.tinted(t, 0.1f), bass = deepSub, arp = darkKeys.tinted(t, 0.1f), lead = shadowLead,
        mix = Mix(
            pad = 0.8f, bass = 1.1f, arp = 1.4f, lead = 0.8f, drums = 0.75f, padVerb = 0.35f, arpDelay = 0.3f, arpVerb = 0.3f,
            leadDelay = 0.3f, leadVerb = 0.3f, padDuck = 0.35f, bassDuck = 0f, arpDuck = 0.1f, arpPan = -0.2f,
        ),
        crowd = 0f, vinyl = 0.03f,
    )

    /** The star: a distorted 808 that punches in sharp, holds, and glides between tied notes. */
    private val eightOhEight = Patch(
        wave1 = Wave.SINE, cutoff = 2400f, keyTrack = 0f, a = 0.001f,
        d = 2.2f, s = 0.4f, r = 0.09f, drive = 1.1f, glide = 0.18f, gain = 0.42f, bright = 0.15f,
        pitchEnv = 12f, pitchDecay = 0.018f,
    )
    /** Dark brass-and-choir stabs: a detuned stack with a brassy filter bite. */
    private val darkBrass = Patch(
        wave1 = Wave.SAW, supersaw = true, detune = 0.16f, cutoff = 520f, q = 0.9f, envAmt = 1.6f, keyTrack = 0.2f,
        a = 0.012f, d = 0.5f, s = 0.65f, r = 0.4f, fa = 0.008f, fd = 0.3f, fs = 0.25f, vibrato = 0.06f, gain = 0.11f,
        bright = 0.5f,
    )
    private val trapBell = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 19f, osc2Level = 0.3f, detune = 0f, cutoff = 5000f,
        keyTrack = 0f, a = 0.001f, d = 0.9f, s = 0f, r = 0.6f, gain = 0.17f, bright = 0.2f,
    )
    private val trapLead = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SQUARE, osc2Semi = -12f, osc2Level = 0.4f, detune = 0.1f, cutoff = 1100f, q = 1.2f,
        envAmt = 1.2f, a = 0.008f, d = 0.4f, s = 0.7f, r = 0.2f, fd = 0.3f, fs = 0.3f, glide = 0.07f, vibrato = 0.3f,
        vibRate = 5f, drive = 0.2f, gain = 0.13f, bright = 0.5f,
    )

    private fun bullHot(base: SongSpec, t: Tint, name: String, bpm: Float, hook: Melody? = null) = base.derive(
        name = name, bpm = bpm, swing = 0f,
        drumsA = DrumPattern(
            kick = "X.........X..X..", snare = "........X.......", clap = "........X.......",
            hat = "x.x.x.xrx.x.x.x.", hat2 = "x.x.x.x.x.x.yzyz",
        ),
        drumsB = DrumPattern(
            kick = "X......X..X..X..", snare = "........X.....o.", clap = "........X.......",
            hat = "x.xtx.x.x.x.q.x.", hat2 = "x.x.x.x.yzyzx.ww", open = "......x.........",
        ),
        fill = DrumPattern(
            kick = "X.........X.X.X.", snare = "........X.X.rrrr", clap = "........X.......", hat = "x.x.x.x.x.x.wwww",
        ),
        kit = DrumTuning(
            kickHi = 200f, kickLo = 52f, kickPitchDecay = 0.022f, kickDecay = 0.2f, kickClick = 0.6f, kickDrive = 0.6f,
            snareTone = 200f, snareNoiseHz = 4800f, snareDecay = 0.16f, snareToneMix = 0.45f, snareLevel = 0.8f,
            snareVerb = 0.3f, clapHz = 1300f, clapDecay = 0.2f, clapLevel = 0.8f, hatTone = 1.4f, hatDecay = 0.03f,
            openDecay = 0.2f, hatLevel = 0.75f, crashLevel = 0.32f, crashDecay = 2.2f, drive = t.drive * 0.6f, crush = t.crush,
        ),
        bassA = "R~~~~~~~~.R~~O~~", bassB = "R~~~~~~O~.R~~~F~", bassSlide = true,
        arpA = "0..2..4..3..2...", arpB = "4..3..2..0..1...", arpGate = 1.5f, arpCenter = base.arpCenter + 12,
        padRhythm = "x.-.......x.-...", padRhythmB = "x...............",
        leadOctave = leadOctave(base, 58),
        leadTemplates = arrayOf(bullSig.rhythm, "x..x..x.x.......", "x.....x...x.x..."),
        motifSeed = base.motifSeed + 11, hook = hook, signature = bullSig, answer = bullAns,
        pad = darkBrass.tinted(t, 0.1f), bass = eightOhEight.tinted(t, 0.3f), arp = trapBell, lead = trapLead.tinted(t, 0.3f),
        mix = Mix(
            pad = 1.2f, bass = 1.6f, arp = 1.2f, lead = 0.9f, drums = 0.45f * zoneDrums(base), padVerb = 0.35f, arpDelay = 0.4f,
            arpVerb = 0.35f, leadDelay = 0.25f, leadVerb = 0.25f, padDuck = 0.4f, bassDuck = 0.12f, arpDuck = 0.1f, arpPan = 0.25f,
        ),
        crowd = 0f, dropThreshold = 0.45f,
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

    // ---- LION: a tiptoeing clown march, then a screaming circus galop ------------------------

    /**
     * LION's sneak is a creepy carnival: the zone's chords recast in its harmonic minor (the
     * raised seventh, an augmented III, a V7 and a diminished vii that leans on the tonic).
     */
    private fun carnival(p: Array<Chord>) = Array(p.size) {
        val d = p[it].degree
        Chord.diatonic(HARMONIC_MINOR, d, seventh = d == 4 || d == 6)
    }

    /** LION's galop is in the zone's parallel major: I–IV–V7 with a vi and a ii. */
    private fun circus(vararg degrees: Int) = Array(degrees.size) {
        val d = degrees[it]
        Chord.diatonic(IONIAN, d, seventh = d == 4)
    }

    /** A bassoon on tiptoe: a reedy, nasal staccato. */
    private val bassoon = Patch(
        wave1 = Wave.PULSE, wave2 = Wave.SAW, pw = 0.28f, osc2Level = 0.35f, detune = 0.05f, sub = 0.2f, cutoff = 700f,
        q = 1.3f, envAmt = 1.1f, keyTrack = 0.4f, a = 0.008f, d = 0.18f, s = 0.55f, r = 0.07f, fa = 0.006f, fd = 0.12f,
        fs = 0.25f, gain = 0.3f, bright = 0.2f,
    )
    /** Pizzicato "pah" chords: short, soft plucks. */
    private val pizzPah = Patch(pluck = 0.4f, ring = 0.35f, cutoff = 3000f, keyTrack = 0.2f, a = 0.001f, d = 1f, s = 1f, r = 0.08f, gain = 0.26f, bright = 0.15f)
    /** A music box: a bright tine and a glassy partial two octaves up, ringing away. */
    private val musicBox = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 24f, osc2Level = 0.28f, detune = 0.12f, cutoff = 7000f,
        keyTrack = 0f, a = 0.001f, d = 0.9f, s = 0f, r = 0.6f, gain = 0.13f, bright = 0.1f,
    )
    /** A wheezy little calliope, seasick: sour pipes, a slow wobble in the pitch. */
    private val wobblyCalliope = Patch(
        wave1 = Wave.TRIANGLE, wave2 = Wave.SQUARE, osc2Semi = 12f, osc2Level = 0.16f, detune = 0.22f, noise = 0.03f,
        cutoff = 2400f, q = 0.8f, a = 0.02f, d = 0.35f, s = 0.55f, r = 0.2f, glide = 0.012f, vibrato = 0.4f, vibRate = 3.3f,
        trem = 0.2f, tremRate = 5.5f, gain = 0.13f, bright = 0.2f,
    )

    private fun lionKit(base: SongSpec, t: Tint, hot: Boolean) = DrumTuning(
        kickHi = if (hot) 115f else 88f, kickLo = if (hot) 55f else 48f, kickPitchDecay = 0.05f, kickDecay = if (hot) 0.3f else 0.38f,
        kickClick = if (hot) 0.08f else 0.03f, kickDrive = if (hot) 0.15f else 0f, kickLevel = if (hot) 0.6f else 0.95f,
        // A tight marching snare (brushed-soft when sneaking).
        snareTone = keyed(base, 60), snareNoiseHz = 5200f, snareDecay = if (hot) 0.1f else 0.07f, snareToneMix = 0.12f,
        snareLevel = if (hot) 0.75f else 0.22f, snareVerb = 0.2f,
        // The "hats" are a pair of clash cymbals on the backbeat.
        hatTone = 0.6f, hatDecay = 0.05f, openDecay = 0.3f, hatLevel = 0.32f, crashDecay = 1.4f, crashLevel = 0.28f,
        // Temple blocks, tuned to the key; the perc is a clown's bulb horn.
        tomHz = keyed(base, 72), tomDecay = 0.07f, tomBend = 0.04f, tomLevel = 0.45f,
        percHz = keyed(base, 57) * 1.5f, percRatio = 1f, percDecay = 0.16f, percFm = 2.2f, percNoise = 0.08f,
        percLevel = if (hot) 0.32f else 0.3f, drive = t.drive * 0.4f, crush = t.crush,
    )

    private fun lionSneak(base: SongSpec, t: Tint, name: String, bpm: Float) = base.derive(
        name = name, bpm = bpm, swing = 0f, scale = HARMONIC_MINOR, progA = carnival(base.progA), progB = carnival(base.progB),
        drumsA = DrumPattern(kick = "X.......X.......", tom = "..3.......3.....", perc = "..............o."),
        drumsB = DrumPattern(kick = "X.......X.......", tom = "..3...2...3...1.", perc = "......o.......x."),
        fill = DrumPattern(kick = "X.......X.......", snare = "........o.o.rrrr", tom = "..3...2...3.2.1."),
        kit = lionKit(base, t, hot = false),
        bassA = "R...F...R...F...", bassB = "R...F...R...A...", bassCenter = base.bassCenter + 12,
        arpA = "......3.......2.", arpB = "..4...3.......2.", arpGate = 1f, arpCenter = base.arpCenter + 12,
        padRhythm = "..x-..x-..x-..x-",
        leadTemplates = arrayOf(lionSig.rhythm, "x...x...x.......", "x.x.x.......x..."),
        signature = lionSig, answer = lionAns, leadOctave = leadOctave(base, 60), leadThreshold = -0.3f,
        pad = pizzPah.tinted(t, 0.05f), bass = bassoon.tinted(t, 0.1f), arp = musicBox, lead = wobblyCalliope.tinted(t, 0.1f),
        mix = Mix(
            pad = 1f, bass = 0.85f, arp = 1.6f, lead = 1.3f, drums = 0.75f, padVerb = 0.3f, arpDelay = 0.45f, arpVerb = 0.4f,
            leadDelay = 0.2f, leadVerb = 0.35f, padDuck = 0.1f, bassDuck = 0.05f, arpDuck = 0f, arpPan = 0.3f,
        ),
        crowd = 0f,
    )

    /** A tuba: a round, brassy "oom", tongued short. */
    private val tuba = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SQUARE, osc2Level = 0.3f, detune = 0.04f, sub = 0.35f, cutoff = 380f, q = 0.9f,
        envAmt = 1.6f, keyTrack = 0.5f, a = 0.012f, d = 0.2f, s = 0.6f, r = 0.07f, fa = 0.01f, fd = 0.12f, fs = 0.15f,
        drive = 0.15f, gain = 0.34f, bright = 0.3f,
    )
    /** A band organ's "pah": reedy pipes, pumped. */
    private val organPah = Patch(
        wave1 = Wave.SQUARE, wave2 = Wave.SAW, osc2Semi = 12f, osc2Level = 0.3f, detune = 0.08f, cutoff = 2200f, q = 0.8f,
        envAmt = 0.8f, a = 0.004f, d = 0.12f, s = 0.6f, r = 0.06f, fd = 0.08f, trem = 0.15f, tremRate = 7f, gain = 0.075f,
        bright = 0.4f,
    )
    /** A xylophone: a hard mallet, the bar's twelfth ringing over its fundamental, dead in a moment. */
    private val xylophone = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 19f, osc2Level = 0.4f, detune = 0f, noise = 0.04f, cutoff = 9000f,
        keyTrack = 0f, a = 0.001f, d = 0.2f, s = 0f, r = 0.15f, gain = 0.2f, bright = 0.2f,
    )
    /** A steam calliope: shrill whistle pipes, a touch out of tune, hissing and pumping. */
    private val calliope = Patch(
        wave1 = Wave.TRIANGLE, wave2 = Wave.SQUARE, osc2Semi = 12f, osc2Level = 0.3f, detune = 0.16f, noise = 0.035f,
        cutoff = 3600f, q = 0.9f, envAmt = 0.4f, a = 0.012f, d = 0.3f, s = 0.8f, r = 0.1f, fa = 0.01f, fd = 0.2f, fs = 0.6f,
        vibrato = 0.22f, vibRate = 6.5f, trem = 0.25f, tremRate = 7.5f, gain = 0.15f, bright = 0.5f,
    )

    private fun lionHot(base: SongSpec, t: Tint, name: String, bpm: Float, hook: Melody? = null): SongSpec {
        // A theme brings its own (major) chords; a zone's track gets the circus ones.
        val own = base.scale.contentEquals(IONIAN)
        return base.derive(
            name = name, bpm = bpm, swing = 0f, scale = IONIAN,
            progA = if (own) base.progA else circus(0, 3, 4, 0), progB = if (own) base.progB else circus(5, 1, 4, 0),
            drumsA = DrumPattern(kick = "X...x...X...x...", snare = "..x...x...x...x.", open = "....x.......x...", perc = "..............x."),
            drumsB = DrumPattern(
                kick = "X...x...X...x.x.", snare = "..x...x.rrrrx.x.", open = "....x.......x...", perc = "......x.......x.",
                crash = "X...............",
            ),
            fill = DrumPattern(kick = "X...x...X.X.X.X.", snare = "rrrrrrrrrrrrrrrX", tom = "........3.2.1.3."),
            kit = lionKit(base, t, hot = true),
            bassA = "R...F...R...F...", bassB = "R...T...F...A...",
            arpA = "........01234567", arpB = "7654321001234567", arpGate = 1f,
            padRhythm = "..x-..x-..x-..x-", padRhythmB = "..x-..x-..x-x-x-",
            leadOctave = leadOctave(base, 62),
            leadTemplates = arrayOf(lionSig.rhythm, "x.x.x.x.x...x...", "x..x..x.x.x.x..."),
            motifSeed = base.motifSeed + 37, hook = hook, signature = lionSig, answer = lionAns,
            pad = organPah.tinted(t, 0.1f), bass = tuba.tinted(t, 0.2f), arp = xylophone, lead = calliope.tinted(t, 0.15f),
            mix = Mix(
                pad = 1.4f, bass = 0.95f, arp = 1.2f, lead = 1.35f, drums = 0.5f * zoneDrums(base), padVerb = 0.2f, arpDelay = 0.1f,
                arpVerb = 0.3f, leadDelay = 0.15f, leadVerb = 0.3f, padDuck = 0.1f, bassDuck = 0.1f, arpDuck = 0.05f, arpPan = -0.3f,
            ),
            crowd = 0f, slideWhistle = 0.1f,
        )
    }

    // ---- HAWK: elevator bossa nova on the quiet, 70s delivery-van funk on the clock --------

    /** Bossa nylon guitar: a soft, dark thumb-and-fingers pluck that rings to the next chord. */
    private val nylonGuitar = Patch(pluck = 0.22f, ring = 1.4f, cutoff = 2600f, keyTrack = 0.1f, a = 0.001f, d = 1f, s = 1f, r = 0.12f, gain = 0.17f, bright = 0.1f)
    /** Vibraphone: a sine bar with its 4th-harmonic partial, a long ring and the motor's tremolo. */
    private val vibraphone = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 24f, osc2Level = 0.16f, detune = 0f, cutoff = 6000f,
        keyTrack = 0f, a = 0.002f, d = 1.6f, s = 0f, r = 0.9f, trem = 0.35f, tremRate = 5.2f, gain = 0.24f, bright = 0.1f,
    )
    /** A round upright bass under the guitar. */
    private val uprightBass = Patch(
        wave1 = Wave.TRIANGLE, wave2 = Wave.SINE, osc2Semi = 12f, osc2Level = 0.2f, sub = 0.1f, cutoff = 700f,
        keyTrack = 0.2f, envAmt = 0.8f, a = 0.005f, d = 0.7f, s = 0.35f, r = 0.12f, fd = 0.12f, gain = 0.3f, bright = 0.1f,
    )
    /** The soft flute that carries HAWK's tune: breathy, tongued notes, a slow vibrato. */
    private val softFlute = Patch(
        wave1 = Wave.SINE, wave2 = Wave.TRIANGLE, osc2Semi = 12f, osc2Level = 0.1f, noise = 0.07f, cutoff = 3000f,
        a = 0.045f, d = 0.5f, s = 0.8f, r = 0.18f, vibrato = 0.25f, vibRate = 4.8f, gain = 0.12f, bright = 0.2f,
    )

    private fun hawkSneak(base: SongSpec, t: Tint, name: String, bpm: Float) = base.derive(
        name = name, bpm = bpm, swing = 0.06f,
        progA = jazz(base.progA, base.scale, ninth = true), progB = jazz(base.progB, base.scale, ninth = true),
        // A brushed kick and upright bass on the bossa "boom... ba-boom", a cross-stick clave, a shaker.
        drumsA = DrumPattern(kick = "X..o....X..o....", snare = "x..x..x...x..x..", perc = "..x...x...x...x.", jingle = "ooxoooxoooxoooxo"),
        drumsB = DrumPattern(kick = "X..o....X..o..o.", snare = "x..x...x..x..x..", perc = "..x...x...x...x.", jingle = "ooxoooxoooxoooxo"),
        fill = DrumPattern(kick = "X..o....X..o....", snare = "x..x..x...x.oxox", jingle = "ooxoooxoxxxxxxxx"),
        kit = DrumTuning(
            kickHi = 92f, kickLo = 48f, kickPitchDecay = 0.04f, kickDecay = 0.28f, kickClick = 0.02f, kickDrive = 0f,
            kickLevel = 0.8f, snareTone = 880f, snareNoiseHz = 3200f, snareDecay = 0.03f, snareToneMix = 0.9f,
            snareLevel = 0.3f, snareVerb = 0.3f, hatLevel = 0f,
            percHz = 2900f, percRatio = 1.41f, percDecay = 0.35f, percFm = 0.35f, percNoise = 0.02f, percLevel = 0.1f,
            jingleHz = 6200f, jingleDecay = 0.035f, jingleNoise = 1f, jingleLevel = 0.16f, drive = t.drive * 0.2f, crush = t.crush,
        ),
        bassA = "R~~~~~D~D~~~~~R~", bassB = "R~~~~~D~D~~~~~A~",
        arpA = "....4.......2...", arpB = "..3.....4...2...", arpGate = 2f,
        // The guitar comps the chords on the syncopations; the B sections open up.
        padRhythm = "x..x..x...x..x..", padRhythmB = "x..x...x..x..x..",
        leadTemplates = arrayOf(hawkSig.rhythm, "x.......x.......", "x...........x..."),
        signature = hawkSig, answer = hawkAns, leadOctave = leadOctave(base, 64),
        // Muzak never stops for anybody: the flute plays even when nobody's looking.
        leadThreshold = -1f,
        pad = nylonGuitar.tinted(t, 0.05f), bass = uprightBass, arp = vibraphone, lead = softFlute,
        mix = Mix(
            pad = 1.2f, bass = 1.05f, arp = 1.3f, lead = 1.15f, drums = 0.65f, padVerb = 0.3f, arpDelay = 0.3f, arpVerb = 0.45f,
            leadDelay = 0.25f, leadVerb = 0.4f, padDuck = 0.05f, bassDuck = 0.05f, arpDuck = 0f, arpPan = 0.35f,
        ),
        crowd = 0f, jungle = 0f,
    )

    /** Funk bass: a thumbed note with a bright, snappy attack (the octaves pop). */
    private val slapBass = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SQUARE, osc2Level = 0.4f, sub = 0.35f, cutoff = 520f, q = 1.2f, envAmt = 2.6f,
        keyTrack = 0.6f, a = 0.001f, d = 0.25f, s = 0.4f, r = 0.05f, fd = 0.08f, drive = 0.35f, gain = 0.42f, bright = 0.3f,
    )
    /** Clavinet through an envelope filter: a narrow pulse with a resonant "quack" on every hit. */
    private val wahClav = Patch(
        wave1 = Wave.PULSE, wave2 = Wave.SAW, pw = 0.22f, osc2Semi = 12f, osc2Level = 0.2f, detune = 0.03f, cutoff = 700f,
        q = 2.6f, envAmt = 2.6f, keyTrack = 0.3f, a = 0.001f, d = 0.18f, s = 0.2f, r = 0.04f, fa = 0.012f, fd = 0.11f,
        drive = 0.2f, gain = 0.2f, bright = 0.4f,
    )
    /** The horn section: brassy detuned saws that swell open and bite. */
    private val hornSection = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SAW, osc2Level = 0.8f, detune = 0.09f, cutoff = 900f, q = 0.9f, envAmt = 1.8f,
        keyTrack = 0.2f, a = 0.012f, d = 0.25f, s = 0.65f, r = 0.07f, fa = 0.025f, fd = 0.2f, fs = 0.35f, drive = 0.2f,
        gain = 0.15f, bright = 0.4f,
    )
    /** The cop-show lead: a squelchy, gliding mono synth with a wide vibrato. */
    private val copLead = Patch(
        wave1 = Wave.SAW, wave2 = Wave.PULSE, pw = 0.3f, osc2Level = 0.45f, detune = 0.1f, cutoff = 2000f, q = 1.3f,
        envAmt = 1.1f, a = 0.006f, d = 0.35f, s = 0.75f, r = 0.12f, fd = 0.25f, fs = 0.4f, glide = 0.03f, vibrato = 0.3f,
        vibRate = 6f, drive = 0.2f, gain = 0.14f, bright = 0.5f,
    )

    private fun hawkHot(base: SongSpec, t: Tint, name: String, bpm: Float, hook: Melody? = null) = base.derive(
        name = name, bpm = bpm, swing = 0.1f,
        progA = jazz(base.progA, base.scale, ninth = false), progB = jazz(base.progB, base.scale, ninth = false),
        // A tight breakbeat with ghost notes; congas and a cowbell come in as the heat climbs.
        drumsA = DrumPattern(
            kick = "X.X.......X..X..", snare = "....X..o.o..X..o", hat = "xoxoxoxoxoxoxoxo", open = "..............x.",
            tom = "......1..2....1.", perc = "x...x...x...x.x.",
        ),
        drumsB = DrumPattern(
            kick = "X..X..X...X.X...", snare = "....X..o.o.oX.o.", hat = "xoxoxoxoxoxoxoxo", open = "......x.......x.",
            tom = "...1..2..2.1..2.", perc = "x.x.x...x.x.x.x.",
        ),
        fill = DrumPattern(kick = "X.X.......X.....", snare = "....X..oX.oXoXXX", hat = "xoxoxoxox.......", tom = "........2.2.1.1."),
        kit = DrumTuning(
            kickHi = 125f, kickLo = 55f, kickPitchDecay = 0.03f, kickDecay = 0.2f, kickClick = 0.12f, kickDrive = 0.25f, kickLevel = 0.85f,
            snareTone = 195f, snareNoiseHz = 3000f, snareDecay = 0.12f, snareToneMix = 0.55f, snareLevel = 0.65f,
            snareVerb = 0.18f, hatTone = 1f, hatDecay = 0.035f, openDecay = 0.2f, hatLevel = 0.34f,
            tomHz = keyed(base, 57), tomDecay = 0.22f, tomBend = 0.1f, tomLevel = 0.5f,
            percHz = 560f, percRatio = 1.5f, percDecay = 0.12f, percFm = 0.35f, percNoise = 0.05f, percLevel = 0.22f,
            crashLevel = 0.28f, drive = t.drive * 0.5f, crush = t.crush,
        ),
        bassA = "R..r..R.O..R.Or.", bassB = "R..rR..O..R.7.OA",
        arpA = "0.20.2120.20.212", arpB = "1.31.3231.31.323", arpGate = 0.5f,
        padRhythm = "......x-.....x-.", padRhythmB = "x-..x-....x-x-..", padCenter = base.padCenter + 5,
        leadOctave = leadOctave(base, 60),
        leadTemplates = arrayOf(hawkSig.rhythm, "x..x..x.x.......", "x.x...x...x.x..."),
        motifSeed = base.motifSeed + 53, hook = hook, signature = hawkSig, answer = hawkAns,
        pad = hornSection.tinted(t, 0.1f), bass = slapBass.tinted(t, 0.08f), arp = wahClav.tinted(t, 0.2f), lead = copLead.tinted(t, 0.2f),
        mix = Mix(
            pad = 1.45f, bass = 1.45f, arp = 1.45f, lead = 1.45f, drums = 0.65f * zoneDrums(base), padVerb = 0.25f, arpDelay = 0.1f,
            arpVerb = 0.15f, leadDelay = 0.25f, leadVerb = 0.3f, padDuck = 0.1f, bassDuck = 0.15f, arpDuck = 0.1f, arpPan = -0.3f,
        ),
        crowd = 0f, jungle = 0f,
    )

    // ---- Themes (hero picker) --------------------------------------------------------------

    private fun tri(scale: IntArray, vararg degrees: Int) = Array(degrees.size) { Chord.diatonic(scale, degrees[it]) }

    /** A theme's skeleton: key, chords, tempo and section plan; the hero's band fills it in. */
    private fun themeBase(name: String, bpm: Float, tonic: Int, scale: IntArray, progA: Array<Chord>, progB: Array<Chord>, seed: Long) =
        Songs.title.derive(
            name = name, bpm = bpm, tonic = tonic, scale = scale, progA = progA, progB = progB, motifSeed = seed,
            hook = null, bassCenter = 36 + Math.floorMod(tonic - 36, 12).coerceAtMost(6), padCenter = 62, arpCenter = 62,
        )

    /** "Horns Down": D minor heavy trap, i–VII–VI–VII, a fifth leaning on its flat sixth over gliding 808s. */
    private val bullHook = Melody(
        arrayOf(
            "A4:3 Bb4:1 A4:2 -:2 F4:3 E4:1 D4:4",
            "G4:3 A4:1 G4:2 -:2 E4:3 D4:1 C4:4",
            "F4:2 F4:1 -:1 D4:2 F4:2 Bb4:3 A4:1 F4:4",
            "E4:3 F4:1 E4:2 D4:2 C4:8",
            "A4:3 Bb4:1 A4:2 -:2 D5:3 C5:1 A4:4",
            "G4:3 A4:1 G4:2 -:2 C5:3 Bb4:1 G4:4",
            "F4:2 Bb4:2 D5:2 F5:2 E5:3 D5:1 Bb4:4",
            "C5:3 D5:1 E5:2 -:2 D5:8",
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

    /** "Runaway Big Top": a D major galop for a steam calliope, I–IV–V–I, ending on a ta-da. */
    private val lionHook = Melody(
        arrayOf(
            "F#5:1 -:1 A5:1 -:1 D6:3 C#6:1 A5:1 B5:1 A5:1 G5:1 F#5:2 E5:1 A4:1",
            "G5:1 -:1 B5:1 -:1 D6:3 C#6:1 B5:1 C#6:1 B5:1 A5:1 G5:2 F#5:1 E5:1",
            "E5:1 F#5:1 G5:1 A5:1 B5:1 C#6:1 D6:1 E6:1 C#6:2 A5:2 E5:2 C#5:2",
            "D5:2 -:2 A5:2 -:2 D6:4 -:4",
            "F#5:1 -:1 A5:1 -:1 D6:3 C#6:1 A5:1 B5:1 A5:1 G5:1 F#5:2 E5:1 A4:1",
            "G5:1 A5:1 B5:1 C#6:1 D6:2 B5:2 G5:2 B5:2 D6:2 B5:2",
            "C#6:1 -:1 A5:1 -:1 E5:1 -:1 A5:1 -:1 C#6:1 D6:1 C#6:1 B5:1 A5:1 G5:1 F#5:1 E5:1",
            "D5:1 F#5:1 A5:1 D6:1 -:4 A5:2 D6:6",
        ),
    )

    /** "Parcel in Pursuit": an E dorian cop-show funk on an Em7–A7 vamp; the doorbell, then the chase. */
    private val hawkHook = Melody(
        arrayOf(
            "B4:3 G4:3 -:2 B4:1 C#5:1 B4:1 G4:1 E4:4",
            "E5:3 C#5:3 -:2 E5:1 F#5:1 E5:1 C#5:1 A4:4",
            "G5:2 -:1 F#5:1 E5:2 D5:2 E5:1 -:1 G5:1 A5:1 B5:4",
            "A5:2 G5:1 E5:1 -:2 C#5:2 D5:1 C#5:1 A4:2 -:4",
            "B4:3 G4:3 -:2 B4:1 C#5:1 B4:1 G4:1 E4:4",
            "E5:3 C#5:3 -:2 E5:1 F#5:1 E5:1 C#5:1 A4:4",
            "E5:1 G5:1 A5:1 B5:1 D6:2 B5:2 -:1 A5:1 G5:1 E5:1 G5:2 A5:2",
            "C#5:2 E5:2 G5:1 E5:3 -:2 B4:1 -:1 E5:4",
        ),
    )

    private fun buildTheme(h: Hero): SongSpec {
        val t = tint(null)
        val trim = THEME_TRIM[h.ordinal]
        return when (h) {
            Hero.BULL -> bullHot(
                themeBase("bull-theme", 145f, 50, AEOLIAN, tri(AEOLIAN, 0, 6, 5, 6), arrayOf(Chord.diatonic(AEOLIAN, 3), Chord.diatonic(AEOLIAN, 0), Chord.diatonic(AEOLIAN, 5), Chord.of(AEOLIAN, 4, Quality.MAJ)), 2401),
                t, "bull-theme", 145f, bullHook,
            )
            Hero.FOX -> foxHot(
                themeBase("fox-theme", 156f, 48, DORIAN, tri(DORIAN, 0, 3, 0, 3), tri(DORIAN, 2, 3, 4, 0), 7007),
                t, "fox-theme", 156f, foxHook,
            )
            Hero.LION -> lionHot(
                themeBase("lion-theme", 178f, 50, IONIAN, circus(0, 3, 4, 0), circus(5, 1, 4, 0), 1988),
                t, "lion-theme", 178f, lionHook,
            )
            Hero.HAWK -> hawkHot(
                themeBase("hawk-theme", 118f, 52, DORIAN, tri(DORIAN, 0, 3, 0, 3), tri(DORIAN, 2, 3, 6, 4), 3161),
                t, "hawk-theme", 118f, hawkHook,
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
            Hero.LION -> if (silent) lionSneak(base, t, name, bpm) else lionHot(base, t, name, bpm)
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
    slideWhistle: Float = this.slideWhistle,
    dropThreshold: Float = this.dropThreshold,
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
    answer = answer, gain = gain, bassSlide = bassSlide, vinyl = vinyl, dropThreshold = dropThreshold,
    slideWhistle = slideWhistle,
)
