package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.audio.Scales.AEOLIAN
import com.bradflaugher.aboutthataction.audio.Scales.DORIAN
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.sqrt

/**
 * Every hero's own soundtrack: each zone's track (GUNS HOT and its SILENT sneak mix) in the
 * hero's genre, plus a signature theme for the hero picker.
 *
 * An arrangement keeps what makes a zone that zone — its key and chords, bars per chord, section plan, the ambience
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
 *  - FOX: an early-90s street brawler. Sneaking is a late-night new-jack swing at ~106 BPM
 *    (swung 16ths, FM electric-piano 9ths, a round FM slap bass, a soft pad, a sultry FM lead;
 *    the B sections slip into a four-to-the-floor deep-house pulse); GUNS HOT is a breakbeat
 *    rave at ~136 (a chopped break, piano-house m7 stabs, a bouncing FM octave bass, FM arps,
 *    a bright FM-brass lead).
 *  - MONKEY: the circus band he ran away with, tight and in tune, both modes one song in the
 *    zone's key. GUNS HOT is a big-top electro-swing at ~150 BPM (swung 8ths, a stomping kick,
 *    snare and clap on 2 and 4, a tambourine, an oom-pah tuba that walks in B, brass "pah"s,
 *    a glockenspiel counter-line and a clean calliope with a gentle vibrato playing his tune);
 *    SILENT is a noir-circus tiptoe at 2/3 the tempo (the heartbeat under brushes and finger
 *    snaps, a pizzicato bass, a celesta, a warm pad and a clarinet carrying the same tune).
 *    Bongos and a woodblock for colour; a slide whistle (a "hoo" on tiptoe) only now and then,
 *    closing a phrase.
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

    /** FOX: a fighter's strut — up the chord in dotted steps, a flick of the sixth, a sly turn down. */
    private val foxSig = Motif("0:3 2:3 4:3 5:1 4:2 2:1 3:1 1:2")
    private val foxAns = Motif("4:3 2:3 7:3 6:1 4:2 3:1 2:1 0:2")

    /** FOX's sneak mix lets her lead surface as soon as the tension does. */
    private const val FOX_SNEAK_LEAD = 0.6f

    /** FOX's rave holds its kick and bass back (the break, stabs and arps tease) until the fight heats up, then drops. */
    private const val FOX_DROP = 0.5f

    /** MONKEY's swing: a tight shuffle hot, a lazy triplet on tiptoe. */
    private const val MONKEY_SWING = 0.6f
    private const val MONKEY_SNEAK_SWING = 0.667f

    /** HAWK: the doorbell — ding... dong, a deadpan "sign here" turn, and home, on time. */
    private val hawkSig = Motif("4:3 2:3 .:2 4:1 5:1 4:1 2:1 0:4")
    private val hawkAns = Motif("2:2 .:1 3:1 4:2 7:2 6:1 4:1 .:2 2:4")

    // ---- Layers: what heat adds -----------------------------------------------------------
    //
    // GUNS HOT sits near zero heat whenever nobody is shooting, so its calm bed has to be a
    // whole groove on its own: the drums and bass play from the first bar (a trap or rave drop
    // still holds the kick back, its bass line playing soft), and the hero's tune is there,
    // softer and darker. CAUTION brings the counter-line in and opens the filters; ALERT lands
    // the drop, the percussion and the lead at full voice. (MONKEY's band keeps its own plan.)

    /** Hot arrangements: kick and snare from zero heat. */
    private const val HOT_KICK = -0.25f
    private const val HOT_LEAD = 0.55f
    private const val HOT_LEAD_FLOOR = 0.5f
    /** A sneak mix's tune surfaces softly even calm (the hero's motif is always in the room). */
    private const val SNEAK_LEAD_FLOOR = 0.35f

    // ---- Tempos (each genre at its own speed, a notch quicker the deeper you go) -------------

    // Zones in order: ROOFTOP, TOWER, LABS, METRO, MINES, MAGMA, HELL, VOID.

    /** MONKEY's big-top swing, spread wide across the zones; his sneak is exactly 2/3 of it (two sneak beats to three hot ones). */
    private val MONKEY_HOT_BPM = floatArrayOf(138f, 144f, 147f, 150f, 141f, 153f, 159f, 156f)

    private val HOT_BPM = arrayOf(
        floatArrayOf(140f, 142f, 144f, 144f, 140f, 146f, 150f, 148f), // BULL: heavy trap, half-time
        floatArrayOf(132f, 134f, 136f, 136f, 132f, 138f, 142f, 138f), // FOX: breakbeat rave
        floatArrayOf(114f, 116f, 118f, 120f, 114f, 120f, 125f, 122f), // HAWK: delivery-van funk
        MONKEY_HOT_BPM, // MONKEY: big-top swing
    )
    private val SNEAK_BPM = arrayOf(
        floatArrayOf(74f, 75f, 76f, 77f, 74f, 78f, 82f, 80f), // BULL: heavy boom-bap
        floatArrayOf(102f, 104f, 106f, 106f, 102f, 108f, 112f, 108f), // FOX: late-night swing
        floatArrayOf(90f, 92f, 94f, 96f, 92f, 96f, 100f, 98f), // HAWK: elevator bossa
        FloatArray(8) { MONKEY_HOT_BPM[it] * 2f / 3f }, // MONKEY: noir-circus tiptoe, hot x 2/3 exactly
    )


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
        wave1 = Wave.SAW, supersaw = true, detune = 0.1f, cutoff = 480f, q = 0.9f, keyTrack = 0.1f, a = 0.5f, d = 1.5f,
        s = 0.85f, r = 1.4f, vibrato = 0.05f, vibRate = 0.6f, gain = 0.09f, bright = 0.3f,
    )
    /**
     * Deep sub: a sine, dying between kicks, with a quiet saw an octave up under a low filter:
     * its harmonics are what a phone speaker (nothing below ~150 Hz) hears as the bass line.
     */
    private val deepSub = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SAW, osc2Semi = 12f, osc2Level = 0.16f, detune = 0f, cutoff = 700f, keyTrack = 0f, a = 0.004f, d = 0.6f,
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
        arpA = "0.......2..1....", arpB = "0..0....4..2..1.", arpGate = 1f, arpCenter = base.arpCenter - 12,
        padRhythm = "x...............",
        leadTemplates = arrayOf(bullSig.rhythm, "x.......x.......", "x...........x..."),
        signature = bullSig, answer = bullAns, leadOctave = leadOctave(base, 57),
        pad = shadowPad.tinted(t, 0.1f), bass = deepSub, arp = darkKeys.tinted(t, 0.1f), lead = shadowLead,
        mix = Mix(
            pad = 1.5f, bass = 0.95f, arp = 1.9f, lead = 1.15f, drums = 0.62f, padVerb = 0.35f, arpDelay = 0.3f, arpVerb = 0.3f,
            leadDelay = 0.3f, leadVerb = 0.3f, padDuck = 0.35f, bassDuck = 0f, arpDuck = 0.1f, arpPan = -0.2f,
        ),
        crowd = 0f, vinyl = 0.03f, leadFloor = SNEAK_LEAD_FLOOR,
    )

    /**
     * The star: a distorted 808 that punches in sharp, holds, and glides between tied notes. A
     * quiet saw an octave up gives the distortion harmonics to bite on, so the line still reads
     * on a phone speaker.
     */
    private val eightOhEight = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SAW, osc2Semi = 12f, osc2Level = 0.3f, detune = 0f, cutoff = 2400f, keyTrack = 0f, a = 0.001f,
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
            pad = 1.5f, bass = 1.25f, arp = 1.6f, lead = 1.7f, drums = 0.45f * zoneDrums(base), padVerb = 0.35f, arpDelay = 0.4f,
            arpVerb = 0.35f, leadDelay = 0.25f, leadVerb = 0.25f, padDuck = 0.4f, bassDuck = 0.12f, arpDuck = 0.1f, arpPan = 0.25f,
        ),
        crowd = 0f, dropThreshold = 0.45f, dropTease = 0.33f, kickThreshold = HOT_KICK, arpThreshold = 0.3f, leadThreshold = HOT_LEAD, leadFloor = HOT_LEAD_FLOOR,
    )

    // ---- FOX: a late-night swing groove on FM keys, then a breakbeat rave --------------------

    /** A round FM slap bass: the modulator's bark on the attack settles into a warm sine. */
    private val foxSlap = Patch(
        wave1 = Wave.SINE, fm = 2.4f, fmRatio = 1f, fmDecay = 0.22f, fmSustain = 0.12f, sub = 0.3f, cutoff = 1800f,
        keyTrack = 0.2f, a = 0.002f, d = 0.35f, s = 0.3f, r = 0.08f, drive = 0.15f, gain = 0.34f, bright = 0.2f,
        pitchEnv = 0.6f, pitchDecay = 0.012f,
    )
    /** An FM electric piano: a mellow body and a glassy tine that rings off the attack. */
    private val foxKeys = Patch(
        wave1 = Wave.SINE, fm = 1.5f, fmRatio = 1f, fmDecay = 1.1f, fmSustain = 0.2f, fm2 = 0.8f, fmRatio2 = 14f,
        fmDecay2 = 0.07f, cutoff = 5000f, keyTrack = 0f, a = 0.002f, d = 1.8f, s = 0.2f, r = 0.4f, trem = 0.18f,
        tremRate = 4f, gain = 0.1f, bright = 0.2f,
    )
    /** A soft, slowly swelling pad: two sines beating with a breath of FM. */
    private val foxVelvet = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Level = 0.6f, detune = 0.1f, fm = 0.8f, fmRatio = 2f, fmDecay = 3f,
        fmSustain = 0.6f, cutoff = 2400f, keyTrack = 0f, a = 0.5f, d = 1f, s = 0.9f, r = 1.2f, vibrato = 0.08f,
        gain = 0.07f, bright = 0.2f,
    )
    /** A sultry FM lead: breathy at the front, a little feedback reed, a slow vibrato. */
    private val foxSultry = Patch(
        wave1 = Wave.SINE, fm = 1.4f, fmRatio = 1f, fmDecay = 0.6f, fmSustain = 0.55f, fmFeedback = 0.3f, noise = 0.01f,
        cutoff = 3500f, keyTrack = 0f, a = 0.03f, d = 0.5f, s = 0.8f, r = 0.25f, glide = 0.05f, vibrato = 0.3f,
        vibRate = 5.2f, gain = 0.13f, bright = 0.3f,
    )

    private fun foxSneak(base: SongSpec, t: Tint, name: String, bpm: Float) = base.derive(
        name = name, bpm = bpm, swing = 0.3f, leadThreshold = FOX_SNEAK_LEAD, leadFloor = SNEAK_LEAD_FLOOR,
        progA = jazz(base.progA, base.scale, ninth = true), progB = jazz(base.progB, base.scale, ninth = true),
        // A: a swung new-jack beat; B: the kick goes four-to-the-floor under open hats (deep house).
        drumsA = DrumPattern(
            kick = "X.....X...X..x..", snare = "....X.......X...", clap = "....x.......x...", hat = "x.xox.xox.xox.xo",
            perc = "..........o...x.",
        ),
        drumsB = DrumPattern(
            kick = "X...X...X...X...", snare = "....x.......x...", clap = "....X.......X...", hat = "x.o.x.o.x.o.x.o.",
            open = "..x...x...x...x.",
        ),
        fill = DrumPattern(kick = "X.....X...X.....", snare = "....X.....o.XoXX", clap = "....x...........", hat = "x.xox.xox.xoxxxx"),
        kit = DrumTuning(
            kickHi = 120f, kickLo = 48f, kickPitchDecay = 0.04f, kickDecay = 0.38f, kickClick = 0.15f, kickDrive = 0.2f,
            kickLevel = 0.95f, snareTone = 200f, snareNoiseHz = 3600f, snareDecay = 0.12f, snareToneMix = 0.4f,
            snareLevel = 0.5f, snareVerb = 0.3f, snareGate = 0.1f, clapHz = 1200f, clapDecay = 0.14f, clapLevel = 0.5f,
            hatTone = 1.1f, hatDecay = 0.04f, openDecay = 0.2f, hatLevel = 0.36f, percHz = 1900f, percRatio = 1.5f,
            percDecay = 0.03f, percFm = 0.6f, percNoise = 0.3f, percLevel = 0.25f, drive = t.drive * 0.4f, crush = t.crush,
        ),
        bassA = "R.....R.O..R..F.", bassB = "R.....R.T..R.S.A",
        // The soft pad breathes the chord's inner voices (third, seventh, fifth) under the keys.
        arpA = "1~~~~~~~~~~~3~~~", arpB = "2~~~~~~~3~~~~~~~", arpGate = 1.2f, arpCenter = base.arpCenter - 10,
        padRhythm = "x......x..x.....", padRhythmB = "x..x......x..x..",
        leadTemplates = arrayOf(foxSig.rhythm, "x.....x...x.....", "x..x....x......."),
        signature = foxSig, answer = foxAns, leadOctave = leadOctave(base, 60),
        pad = foxKeys.tinted(t, 0.1f), bass = foxSlap, arp = foxVelvet.tinted(t, 0f), lead = foxSultry,
        mix = Mix(
            pad = 1.35f, bass = 1f, arp = 1f, lead = 1.1f, drums = 0.5f, padVerb = 0.35f, arpDelay = 0.2f, arpVerb = 0.45f,
            leadDelay = 0.35f, leadVerb = 0.35f, padDuck = 0.15f, bassDuck = 0.05f, arpDuck = 0.1f, arpPan = -0.25f,
        ),
        crowd = 0f,
    )

    /** A driving FM bass: a hard-edged attack with feedback grit, bouncing octaves. */
    private val foxRaveBass = Patch(
        wave1 = Wave.SINE, fm = 3.2f, fmRatio = 1f, fmDecay = 0.18f, fmSustain = 0.3f, fmFeedback = 0.25f, sub = 0.4f,
        cutoff = 2400f, keyTrack = 0.2f, a = 0.002f, d = 0.2f, s = 0.6f, r = 0.05f, drive = 0.3f, gain = 0.3f, bright = 0.4f,
    )
    /** A bright house piano for the chord stabs. */
    private val foxPiano = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 12f, osc2Level = 0.25f, fm = 2.4f, fmRatio = 1f, fmDecay = 0.5f,
        fmSustain = 0.15f, fm2 = 1.2f, fmRatio2 = 14f, fmDecay2 = 0.05f, cutoff = 7000f, keyTrack = 0f, a = 0.001f,
        d = 0.9f, s = 0f, r = 0.2f, gain = 0.15f, bright = 0.3f,
    )
    /** Rave arps: a bell-bright FM pluck. */
    private val foxPluck = Patch(
        wave1 = Wave.SINE, fm = 2.5f, fmRatio = 3f, fmDecay = 0.15f, cutoff = 6000f, keyTrack = 0f, a = 0.001f,
        d = 0.25f, s = 0f, r = 0.1f, gain = 0.18f, bright = 0.3f,
    )
    /** FM brass: feedback turns the sine into a bright, sawtooth-ish horn that opens up. */
    private val foxBrass = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SAW, osc2Level = 0.15f, detune = 0.08f, fm = 2.4f, fmRatio = 1f, fmDecay = 0.35f,
        fmSustain = 0.55f, fmFeedback = 0.45f, cutoff = 3800f, q = 0.8f, envAmt = 0.8f, a = 0.015f, d = 0.4f, s = 0.8f,
        r = 0.15f, fd = 0.3f, glide = 0.03f, vibrato = 0.25f, vibRate = 5.6f, gain = 0.14f, bright = 0.5f,
    )

    private fun foxHot(base: SongSpec, t: Tint, name: String, bpm: Float, hook: Melody? = null) = base.derive(
        name = name, bpm = bpm, swing = 0f,
        progA = jazz(base.progA, base.scale, ninth = false), progB = jazz(base.progB, base.scale, ninth = false),
        // A: a chopped breakbeat; B: a four-to-the-floor kick under the break, claps and open hats.
        drumsA = DrumPattern(
            kick = "X.....X...X..X..", snare = "....X..o.o..X..o", hat = "x.x.x.xox.x.x.xo", open = "..............x.",
        ),
        drumsB = DrumPattern(
            kick = "X...X...X...X...", snare = "....X..o.o..X.o.", clap = "....X.......X...", hat = "xoxoxoxoxoxoxoxo",
            open = "..x...x...x...x.", perc = "...x...x...x...x",
        ),
        fill = DrumPattern(
            kick = "X..X..X...X.X...", snare = "X.oXX.oX.rrrXXXX", clap = "............X...", hat = "x.x.x.x.x.x.q.q.",
        ),
        kit = DrumTuning(
            kickHi = 150f, kickLo = 46f, kickPitchDecay = 0.035f, kickDecay = 0.34f, kickClick = 0.35f, kickDrive = 0.35f,
            snareTone = 210f, snareNoiseHz = 4500f, snareDecay = 0.15f, snareToneMix = 0.5f, snareLevel = 0.8f,
            snareVerb = 0.22f, clapHz = 1250f, clapDecay = 0.15f, clapLevel = 0.6f, hatTone = 1.2f, hatDecay = 0.03f,
            openDecay = 0.16f, hatLevel = 0.3f, percHz = 820f, percRatio = 1.41f, percDecay = 0.07f, percFm = 1f,
            percNoise = 0.05f, percLevel = 0.22f, crashLevel = 0.3f, drive = 0.2f + t.drive * 0.5f, crush = maxOf(2, t.crush),
        ),
        bassA = "R.O.R.O.R.O.R.O.", bassB = "R.O.R.OFR.O.R.OA",
        arpA = "0242024202420242", arpB = "3424342434243424", arpGate = 0.6f,
        padRhythm = "x..x...x..x...x.", padRhythmB = "x..x..x.x..x..x.",
        leadOctave = leadOctave(base, 62),
        leadTemplates = arrayOf(foxSig.rhythm, "x.x...x.x...x...", "x..x..x.x.x....."),
        motifSeed = base.motifSeed + 23, hook = hook, signature = foxSig, answer = foxAns,
        pad = foxPiano.tinted(t, 0.1f), bass = foxRaveBass.tinted(t, 0.2f), arp = foxPluck.tinted(t, 0.1f), lead = foxBrass.tinted(t, 0.2f),
        mix = Mix(
            pad = 0.9f, bass = 1f, arp = 1f, lead = 1.1f, drums = 0.42f * zoneDrums(base), padVerb = 0.3f, arpDelay = 0.35f,
            arpVerb = 0.3f, leadDelay = 0.25f, leadVerb = 0.3f, padDuck = 0.35f, bassDuck = 0.25f, arpDuck = 0.2f, arpPan = 0.3f,
        ),
        crowd = 0f, dropThreshold = FOX_DROP, dropTease = 0.75f, kickThreshold = HOT_KICK, arpThreshold = 0.3f, leadThreshold = HOT_LEAD, leadFloor = HOT_LEAD_FLOOR,
    )

    // ---- MONKEY: a noir-circus tiptoe when sneaking, a big-top swing when the guns come out ----

    /**
     * Swing-era chords from [p]: every chord keeps its tones and gains a diatonic flat seventh
     * (m7, dominant 7th, half-diminished), or a sixth where the scale's seventh is major (a
     * major 6th chord, never a major 7th: a close-voiced maj7 grinds a semitone against its root).
     */
    private fun swingChords(p: Array<Chord>, scale: IntArray) = Array(p.size) {
        val c = p[it]
        val iv = c.intervals
        if (iv.size != 3 || (iv[1] != 3 && iv[1] != 4)) {
            c
        } else {
            val add = intArrayOf(10, 9).firstOrNull { a -> Scales.contains(scale, c.root + a) && !(iv[1] == 3 && iv[2] == 6 && a == 9) }
            if (add == null) c else Chord(c.root, intArrayOf(iv[0], iv[1], iv[2], add), c.degree)
        }
    }

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * A hand-written hook in [Motif] notation (scale degrees above each bar's chord root, one
     * string a bar), played over [spec]'s A chords the way the [Composer] plays a motif:
     * transposed diatonically, with the downbeats, the half-bar and every long note on the chord.
     * So one tune (MONKEY's leitmotif) sits in every zone's key and chords.
     */
    private fun degreeHook(spec: SongSpec, bars: Array<String>): Melody = Melody(
        Array(bars.size) { b ->
            val chord = spec.progA[(b / spec.barsPerChord) % spec.progA.size]
            val cd = if (chord.degree > 3) chord.degree - 7 else chord.degree
            var step = 0
            bars[b].trim().split(Regex("\\s+")).joinToString(" ") { tok ->
                val (d, len) = tok.split(':')
                val l = len.toInt()
                val at = step
                step += l
                if (d == ".") return@joinToString "-:$l"
                var semis = Scales.note(spec.scale, cd + d.toInt())
                if ((at % 8 == 0 || l >= 4) && !chord.containsPc(semis)) {
                    semis += intArrayOf(-1, 1, -2, 2).firstOrNull { o -> chord.containsPc(semis + o) } ?: 0
                }
                semis = Composer.chromaticSnap(semis, chord, spec.scale)
                while (semis > 24) semis -= 12
                while (semis < -7) semis += 12
                var n = spec.tonic + spec.leadOctave + semis
                while (n > spec.leadCeiling) n -= 12
                "${NOTE_NAMES[n % 12]}${n / 12 - 1}:$l"
            }
        },
    )

    /** MONKEY: "Mon-key busi-ness!": the fifth twice, a cheeky lean on the sixth, back, down to the third; a pickup. */
    private val monkeySig = Motif("4:2 4:2 5:2 4:2 2:4 .:2 0:2")
    /** The bridge answers it with long notes, stepping down from the octave. */
    private val monkeyAns = Motif("7:4 6:2 5:2 4:4 2:2 1:2")

    /** The eight-bar A strain: the motif twice, a lift, a half-close; twice more, a climb to the top and home. */
    private val MONKEY_HOOK = arrayOf(
        "4:2 4:2 5:2 4:2 2:4 .:2 0:2",
        "4:2 4:2 5:2 4:2 2:4 .:2 0:2",
        "2:2 4:2 7:4 6:2 4:2 5:2 4:2",
        "2:4 1:2 0:6 .:2 0:2",
        "4:2 4:2 5:2 4:2 2:4 .:2 0:2",
        "4:2 4:2 5:2 7:2 9:4 .:2 7:2",
        "7:2 6:2 5:2 6:2 4:2 5:2 4:2 2:2",
        "2:2 1:2 0:8 .:4",
    )
    /** The A2 strain: the motif reaches up this time, and the climb holds the top before it comes home. */
    private val MONKEY_HOOK_A2 = arrayOf(
        "4:2 4:2 5:2 4:2 2:4 .:2 0:2",
        "4:2 4:2 5:2 4:2 7:4 .:2 4:2",
        "5:2 4:2 2:2 4:2 7:6 .:2",
        "6:2 5:2 4:2 2:2 1:4 .:2 4:2",
        "4:2 4:2 5:2 4:2 2:4 .:2 0:2",
        "4:2 4:2 5:2 7:2 9:4 .:2 9:2",
        "7:4 6:2 5:2 4:2 5:2 4:2 2:2",
        "4:2 2:2 0:8 .:4",
    )

    /** MONKEY's lead never climbs past C6 (a calliope up there just shrieks). */
    private const val MONKEY_CEILING = 84

    /** [spec] carrying MONKEY's tune: the hand-written strains in A and A2 (VOID's reharmonising keeps to the motifs). */
    private fun monkeyTune(spec: SongSpec): SongSpec =
        if (spec.glitch) spec.derive(hook = null, hookA2 = null)
        else spec.derive(hook = degreeHook(spec, MONKEY_HOOK), hookA2 = degreeHook(spec, MONKEY_HOOK_A2))

    private val MONKEY_TEMPLATES = arrayOf(monkeySig.rhythm, "x.x.x...x...x...", "x...x.x.x.x.....")

    /** A tuba: a round, warm brass "oom" (a saw softened by its own sine), tongued, dead in tune. */
    private val tuba = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SINE, osc2Level = 0.6f, detune = 0f, sub = 0.25f, cutoff = 360f, q = 0.75f,
        envAmt = 1.2f, keyTrack = 0.5f, a = 0.014f, d = 0.25f, s = 0.55f, r = 0.08f, fa = 0.012f, fd = 0.14f, fs = 0.2f,
        drive = 0.1f, gain = 0.36f, bright = 0.3f,
    )
    /** The band's brass "pah" on the backbeat: two saws a few cents apart (a section, not a chorus), a quick bite. */
    private val bigTopBrass = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SAW, osc2Level = 0.7f, detune = 0.03f, cutoff = 1000f, q = 0.8f, envAmt = 1.4f,
        keyTrack = 0.3f, a = 0.012f, d = 0.16f, s = 0.45f, r = 0.07f, fa = 0.01f, fd = 0.12f, fs = 0.3f, drive = 0.1f,
        gain = 0.09f, bright = 0.4f,
    )
    /** A glockenspiel: a struck steel bar, its fundamental and a soft two-octave partial, ringing short. */
    private val glockenspiel = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 24f, osc2Level = 0.18f, detune = 0f, cutoff = 7000f,
        keyTrack = 0f, a = 0.001f, d = 0.45f, s = 0f, r = 0.35f, gain = 0.12f, bright = 0.1f,
    )
    /** A xylophone: a dry wooden bar, its twelfth and a knock (bones, in HELL: a danse macabre). */
    private val xylophone = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 19f, osc2Level = 0.22f, detune = 0f, noise = 0.01f, cutoff = 7000f,
        keyTrack = 0f, a = 0.001f, d = 0.18f, s = 0f, r = 0.12f, gain = 0.14f, bright = 0.1f,
    )
    /** An accordion, in tune (no musette beating): a reedy pulse with its octave reed, held notes breathing. */
    private val accordion = Patch(
        wave1 = Wave.PULSE, pw = 0.35f, wave2 = Wave.SAW, osc2Semi = 12f, osc2Level = 0.25f, detune = 0f, cutoff = 2200f,
        q = 0.7f, keyTrack = 0.3f, a = 0.02f, d = 0.3f, s = 0.7f, r = 0.1f, gain = 0.07f, bright = 0.3f,
    )
    /**
     * The calliope, clean: a flue pipe (a triangle) with a quiet octave pipe exactly in tune above
     * it, a breath of air, and one gentle vibrato (no tremolo fighting it).
     */
    private val calliope = Patch(
        wave1 = Wave.TRIANGLE, wave2 = Wave.SQUARE, osc2Semi = 12f, osc2Level = 0.14f, detune = 0f, noise = 0.004f,
        cutoff = 2600f, q = 0.7f, envAmt = 0.3f, keyTrack = 0.4f, a = 0.012f, d = 0.25f, s = 0.85f, r = 0.09f, fa = 0.01f,
        fd = 0.2f, fs = 0.7f, vibrato = 0.08f, vibRate = 5.5f, gain = 0.14f, bright = 0.4f,
    )

    /** A pizzicato upright: a dark plucked string with a round body, dying away. */
    private val pizzBass = Patch(
        pluck = 0.25f, ring = 0.6f, wave2 = Wave.SINE, osc2Level = 0.3f, detune = 0f, cutoff = 900f, keyTrack = 0.2f,
        a = 0.001f, d = 0.7f, s = 0f, r = 0.1f, gain = 0.42f, bright = 0.1f,
    )
    /** A celesta, like a music box that works: a sine bar and its octave, perfectly steady. */
    private val celesta = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 12f, osc2Level = 0.22f, detune = 0f, cutoff = 6000f,
        keyTrack = 0f, a = 0.001f, d = 0.9f, s = 0f, r = 0.6f, gain = 0.12f, bright = 0.1f,
    )
    /** A warm pad under it all: a soft triangle with a hint of saw, slow to swell. */
    private val warmPad = Patch(
        wave1 = Wave.TRIANGLE, wave2 = Wave.SAW, osc2Level = 0.25f, detune = 0.02f, cutoff = 900f, q = 0.6f,
        keyTrack = 0.2f, a = 0.9f, d = 1.5f, s = 0.85f, r = 1.5f, gain = 0.07f, bright = 0.2f,
    )
    /** The clarinet that carries his tune on tiptoe: a hollow, woody square, breathy at the front, a slow vibrato. */
    private val clarinet = Patch(
        wave1 = Wave.SQUARE, wave2 = Wave.TRIANGLE, osc2Level = 0.3f, detune = 0f, noise = 0.006f, cutoff = 1800f, q = 0.7f,
        envAmt = 0.5f, keyTrack = 0.6f, a = 0.03f, d = 0.3f, s = 0.8f, r = 0.12f, fa = 0.03f, fd = 0.25f, fs = 0.6f,
        vibrato = 0.06f, vibRate = 5f, gain = 0.12f, bright = 0.3f,
    )
    /** Down in the deep zones a muted trumpet takes the tune: a brassy saw through a nasal, resonant mute. */
    private val mutedTrumpet = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SQUARE, osc2Level = 0.2f, detune = 0f, noise = 0.004f, cutoff = 1300f, q = 1.8f,
        envAmt = 0.8f, keyTrack = 0.5f, a = 0.02f, d = 0.3f, s = 0.75f, r = 0.1f, fa = 0.02f, fd = 0.2f, fs = 0.4f,
        vibrato = 0.07f, vibRate = 5.2f, gain = 0.1f, bright = 0.3f,
    )

    /**
     * How each zone dresses MONKEY's band, so the eight don't sound like one song reskinned: who
     * plays the counter-line hot (and its part), whether the kick stomps four to the floor, who
     * carries the tune on tiptoe and what the celesta plays.
     */
    private class MonkeyZone(
        val counter: Patch, val arpA: String, val arpB: String, val arpGate: Float, val stomp: Boolean,
        val sneakLead: Patch, val celA: String, val celB: String,
    )

    private fun monkeyZone(z: Zone?): MonkeyZone {
        // Counter-lines: glockenspiel chimes in the tune's gaps, swung runs in B; accordion held
        // chord tones; xylophone skitters.
        val chimes = arrayOf("......4.....6...", "0.2.4.6.5.4.2.4.")
        val bells = arrayOf("..........4.6...", "4.2.0.2.4.6.4.2.")
        val reeds = arrayOf("....2~~~....3~~~", "2~~~3~~~4~~~3~~~")
        val bones = arrayOf("..........2.4.6.", "0.2.4.2.6.4.2.4.")
        val tinkle = arrayOf("............4.2.", "..4...2...4...3.")
        val twinkle = arrayOf("..........6.4...", "..6...4...2...4.")
        fun mz(c: Patch, p: Array<String>, gate: Float, stomp: Boolean, lead: Patch, cel: Array<String>) =
            MonkeyZone(c, p[0], p[1], gate, stomp, lead, cel[0], cel[1])
        return when (z) {
            Zone.ROOFTOP, null -> mz(glockenspiel, chimes, 1f, false, clarinet, tinkle)
            Zone.TOWER -> mz(glockenspiel, bells, 1f, false, clarinet, twinkle)
            Zone.LABS -> mz(xylophone, bells, 1f, false, clarinet, tinkle)
            Zone.METRO -> mz(accordion, reeds, 1f, false, mutedTrumpet, twinkle)
            Zone.MINES -> mz(xylophone, bones, 1f, false, mutedTrumpet, tinkle)
            Zone.MAGMA -> mz(accordion, reeds, 1f, true, mutedTrumpet, twinkle)
            Zone.HELL -> mz(xylophone, bones, 1f, true, mutedTrumpet, tinkle)
            Zone.VOID -> mz(glockenspiel, chimes, 1f, false, clarinet, twinkle)
        }
    }

    private fun monkeyHot(base: SongSpec, t: Tint, name: String, bpm: Float, z: Zone?): SongSpec {
        val band = monkeyZone(z)
        // Upstairs the kick stomps on 1 and 3; in the deep zones it's four to the floor.
        val kickA = if (band.stomp) "X...X...X...X.x." else "X.......X.....x."
        val kickB = if (band.stomp) "X...X...X...X.x." else "X.....x.X.....x."
        val spec = base.derive(
            name = name, bpm = bpm, swing = 0f, swing8 = MONKEY_SWING, chromaticSnap = true,
            progA = swingChords(base.progA, base.scale), progB = swingChords(base.progB, base.scale),
            // Electro-swing: the kick, snare and clap on 2 and 4, swung 8th hats leaning on the "and",
            // a tambourine on the backbeat; bongos join in B.
            drumsA = DrumPattern(
                kick = kickA, snare = "....X.......X...", clap = "....x.......x...", hat = "o.x.o.x.o.x.o.x.",
                jingle = "....x.......x...", perc = "..........o.....",
            ),
            drumsB = DrumPattern(
                kick = kickB, snare = "....X.o.....X.o.", clap = "....x.......x...", hat = "o.x.o.x.o.x.o.x.",
                open = "..............x.", tom = "..1...2...1.2.1.", jingle = "....x.......x...", perc = "..o.......o.....",
            ),
            fill = DrumPattern(
                kick = "X.......X.......", snare = "....X...X.X.X.XX", clap = "....x...........", hat = "x.x.x.x.........",
                tom = "........3.3.2.1.",
            ),
            kit = DrumTuning(
                kickHi = 125f, kickLo = 48f, kickPitchDecay = 0.035f, kickDecay = 0.3f, kickClick = 0.2f, kickDrive = 0.2f,
                kickLevel = if (band.stomp) 0.6f else 0.8f, snareTone = 190f, snareNoiseHz = 3400f, snareDecay = 0.13f,
                snareToneMix = 0.45f, snareLevel = 0.65f, snareVerb = 0.22f, clapHz = 1300f, clapDecay = 0.12f, clapLevel = 0.45f,
                hatTone = 1f, hatDecay = 0.03f, openDecay = 0.16f, hatLevel = 0.26f,
                tomHz = keyed(base, 60), tomDecay = 0.12f, tomBend = 0.08f, tomLevel = 0.35f,
                percHz = keyed(base, 72), percRatio = 1f, percDecay = 0.04f, percFm = 0.4f, percNoise = 0.15f, percLevel = 0.25f,
                jingleHz = 6000f, jingleDecay = 0.1f, jingleNoise = 0.4f, jingleLevel = 0.22f,
                crashLevel = 0.22f, crashDecay = 1.6f, drive = t.drive * 0.4f, crush = t.crush,
            ),
            // Oom-pah in A (a chromatic step into every bar), a walking tuba in B.
            bassA = "R.......F.....A.", bassB = "R...T...F...A...", bassCenter = base.bassCenter + 7,
            arpA = band.arpA, arpB = band.arpB, arpGate = band.arpGate, arpCenter = base.arpCenter + 12,
            padRhythm = "....x.-.....x.-.", padRhythmB = "....x.-.x.-.x.-.",
            leadOctave = leadOctave(base, 58), leadCeiling = MONKEY_CEILING,
            // The band plays from the first calm bar (kick, tambourine, tuba, brass and the tune);
            // heat brings the snare and clap in by CAUTION with the counter-line, then brightens
            // it, opens the hats, adds the woodblock and lets the whistle out.
            kickThreshold = 0f, arpThreshold = 0.3f, leadThreshold = -1f,
            leadTemplates = MONKEY_TEMPLATES, motifSeed = base.motifSeed + 37, signature = monkeySig, answer = monkeyAns,
            pad = bigTopBrass.tinted(t, 0.1f), bass = tuba.tinted(t, 0.15f), arp = band.counter.tinted(t, 0f), lead = calliope.tinted(t, 0.05f),
            mix = Mix(
                pad = 1.2f, bass = 0.9f, arp = 1.2f, lead = 1.2f, drums = 0.5f * zoneDrums(base), padVerb = 0.25f, arpDelay = 0.2f,
                arpVerb = 0.3f, leadDelay = 0.15f, leadVerb = 0.25f, padDuck = 0.2f, bassDuck = 0.1f, arpDuck = 0.05f, arpPan = -0.3f,
            ),
            // A slide whistle up an octave, now and then, closing a phrase.
            crowd = 0f, jungle = 0f, slideWhistle = 0.035f, whistleFrom = 69, whistleRange = 2f, whistleEvery = 4,
        )
        return monkeyTune(spec)
    }

    private fun monkeySneak(base: SongSpec, t: Tint, name: String, bpm: Float, z: Zone): SongSpec {
        val band = monkeyZone(z)
        val spec = base.derive(
            name = name, bpm = bpm, swing = 0f, swing8 = MONKEY_SNEAK_SWING, chromaticSnap = true,
            progA = swingChords(base.progA, base.scale), progB = swingChords(base.progB, base.scale),
            // The heartbeat under brushes (swung 8th swishes) and finger snaps on 2 and 4; soft
            // bongos in B, a woodblock tick-tock as the guards get suspicious.
            drumsA = DrumPattern(kick = "X.x.....X.x.....", snare = "....x.......x...", jingle = "x.o.x.o.x.o.x.o.", perc = "......o.......o."),
            drumsB = DrumPattern(
                kick = "X.x.....X.x.....", snare = "....x.......x...", jingle = "x.o.x.o.x.o.x.o.", tom = "......1.......2.",
                perc = "..o...o...o...o.",
            ),
            fill = DrumPattern(kick = "X.x.....X.x.....", snare = "....x.......x...", jingle = "x.o.x.o.x.o.x.o.", tom = "........1.2.1.3."),
            kit = DrumTuning(
                kickHi = 95f, kickLo = 46f, kickPitchDecay = 0.04f, kickDecay = 0.32f, kickClick = 0.03f, kickDrive = 0.05f,
                kickLevel = 0.8f, snareTone = 1400f, snareNoiseHz = 2600f, snareDecay = 0.045f, snareToneMix = 0.02f,
                snareLevel = 0.3f, snareVerb = 0.3f, hatLevel = 0f,
                tomHz = keyed(base, 55), tomDecay = 0.14f, tomBend = 0.06f, tomLevel = 0.3f,
                percHz = keyed(base, 72), percRatio = 1f, percDecay = 0.04f, percFm = 0.4f, percNoise = 0.1f, percLevel = 0.15f,
                jingleHz = 3800f, jingleDecay = 0.07f, jingleNoise = 1f, jingleLevel = 0.1f, drive = t.drive * 0.2f, crush = t.crush,
            ),
            // The pizzicato bass tiptoes in A and walks in B.
            bassA = "R.....F.R.......", bassB = "R...T...F...A...", bassCenter = base.bassCenter + 5,
            arpA = band.celA, arpB = band.celB, arpGate = 1.5f, arpCenter = base.arpCenter + 12,
            padRhythm = "x...............",
            leadOctave = leadOctave(base, 58), leadCeiling = MONKEY_CEILING, leadThreshold = -1f,
            leadTemplates = MONKEY_TEMPLATES, signature = monkeySig, answer = monkeyAns,
            pad = warmPad.tinted(t, 0f), bass = pizzBass, arp = celesta, lead = band.sneakLead,
            mix = Mix(
                pad = 0.8f, bass = 1.5f, arp = 2f, lead = if (band.sneakLead === clarinet) 1f else 1.5f, drums = 0.7f, padVerb = 0.45f,
                arpDelay = 0.35f, arpVerb = 0.4f, leadDelay = 0.2f, leadVerb = 0.35f, padDuck = 0.1f, bassDuck = 0f, arpDuck = 0f, arpPan = 0.3f,
            ),
            // A monkey's "hoo" (the slide whistle up a fifth), rarely, closing a phrase once the guards stir.
            crowd = 0f, jungle = 0f, slideWhistle = 0.025f, whistleFrom = 62, whistleRange = 1.5f, whistleEvery = 4,
        )
        return monkeyTune(spec)
    }

    // ---- HAWK: elevator bossa nova on the quiet, 70s delivery-van funk on the clock --------

    /** Bossa nylon guitar: a soft, dark thumb-and-fingers pluck that rings to the next chord. */
    private val nylonGuitar = Patch(pluck = 0.22f, ring = 1.4f, cutoff = 2600f, keyTrack = 0.1f, a = 0.001f, d = 1f, s = 1f, r = 0.12f, gain = 0.17f, bright = 0.1f)
    /** Vibraphone: a sine bar with its 4th-harmonic partial, a long ring and the motor's tremolo. */
    private val vibraphone = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 24f, osc2Level = 0.16f, detune = 0f, cutoff = 6000f,
        keyTrack = 0f, a = 0.002f, d = 1.6f, s = 0f, r = 0.9f, trem = 0.35f, tremRate = 5.2f, gain = 0.24f, bright = 0.1f,
    )
    /** A round upright bass under the guitar; the string's growl (a soft saw an octave up) carries it on a phone. */
    private val uprightBass = Patch(
        wave1 = Wave.TRIANGLE, wave2 = Wave.SAW, osc2Semi = 12f, osc2Level = 0.2f, detune = 0f, sub = 0.1f, cutoff = 800f,
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
        crowd = 0f, jungle = 0f, kickThreshold = HOT_KICK, arpThreshold = 0.25f, leadThreshold = HOT_LEAD, leadFloor = HOT_LEAD_FLOOR,
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

    /** "Ponytail of Doom": an E dorian rave anthem, i–VII–III–IV, FM brass over piano stabs. */
    private val foxHook = Melody(
        arrayOf(
            "E5:3 B4:3 E5:2 G5:1 F#5:1 E5:2 D5:2 B4:2",
            "D5:3 A4:3 D5:2 F#5:1 E5:1 D5:2 C#5:2 A4:2",
            "B4:2 D5:2 G5:3 F#5:1 G5:2 A5:2 B5:4",
            "A5:3 -:1 C#5:2 E5:2 A5:2 G5:2 F#5:2 E5:2",
            "E5:3 B4:3 E5:2 G5:1 F#5:1 E5:2 D5:2 B4:2",
            "D5:3 A4:3 D5:2 F#5:1 E5:1 D5:2 C#5:2 A4:2",
            "G5:2 A5:2 B5:3 A5:1 G5:2 F#5:2 D5:2 B4:2",
            "C#6:3 A5:3 E5:2 A5:8",
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
        return when (h) {
            Hero.BULL -> bullHot(
                themeBase("bull-theme", 145f, 50, AEOLIAN, tri(AEOLIAN, 0, 6, 5, 6), arrayOf(Chord.diatonic(AEOLIAN, 3), Chord.diatonic(AEOLIAN, 0), Chord.diatonic(AEOLIAN, 5), Chord.of(AEOLIAN, 4, Quality.MAJ)), 2401),
                t, "bull-theme", 145f, bullHook,
            )
            Hero.FOX -> foxHot(
                themeBase("fox-theme", 136f, 52, DORIAN, tri(DORIAN, 0, 6, 2, 3), tri(DORIAN, 3, 4, 2, 6), 7007),
                t, "fox-theme", 136f, foxHook,
            )
            // "Monkey Business": G minor big-top swing round the circle, i–iv–VII–III; the bridge turns on a D7.
            Hero.MONKEY -> monkeyHot(
                themeBase(
                    "monkey-theme", 150f, 55, AEOLIAN, tri(AEOLIAN, 0, 3, 6, 2),
                    arrayOf(Chord.diatonic(AEOLIAN, 5), Chord.diatonic(AEOLIAN, 3), Chord.of(AEOLIAN, 4, Quality.MAJ), Chord.diatonic(AEOLIAN, 0)), 1988,
                ),
                t, "monkey-theme", 150f, null,
            )
            Hero.HAWK -> hawkHot(
                themeBase("hawk-theme", 118f, 52, DORIAN, tri(DORIAN, 0, 3, 0, 3), tri(DORIAN, 2, 3, 6, 4), 3161),
                t, "hawk-theme", 118f, hawkHook,
            )
        }.derive(fixedIntensity = 0.85f)
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
            Hero.MONKEY -> if (silent) monkeySneak(base, t, name, bpm, z) else monkeyHot(base, t, name, bpm, z)
            Hero.HAWK -> if (silent) hawkSneak(base, t, name, bpm) else hawkHot(base, t, name, bpm)
        }
        return spec
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
    leadFloor: Float = this.leadFloor,
    kickThreshold: Float = this.kickThreshold,
    arpThreshold: Float = this.arpThreshold,
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
    whistleFrom: Int = this.whistleFrom,
    whistleRange: Float = this.whistleRange,
    dropThreshold: Float = this.dropThreshold,
    dropTease: Float = this.dropTease,
    whistleEvery: Int = this.whistleEvery,
    swing8: Float = this.swing8,
    chromaticSnap: Boolean = this.chromaticSnap,
    hookA2: Melody? = this.hookA2,
    leadCeiling: Int = this.leadCeiling,
    tempoLock: Float = this.tempoLock,
) = SongSpec(
    name = name, bpm = bpm, tonic = tonic, scale = scale, progA = progA, progB = progB, barsPerChord = barsPerChord,
    swing = swing, drumsA = drumsA, drumsB = drumsB, fill = fill, kit = kit, bassA = bassA, bassB = bassB,
    arpA = arpA, arpB = arpB, arpGate = arpGate, padRhythm = padRhythm, bassCenter = bassCenter, padCenter = padCenter,
    arpCenter = arpCenter, leadOctave = leadOctave, leadTemplates = leadTemplates, motifSeed = motifSeed, hook = hook,
    pad = pad, bass = bass, arp = arp, lead = lead, mix = mix, wind = wind, rotor = rotor, glitch = glitch,
    fixedIntensity = fixedIntensity, kickThreshold = kickThreshold, arpThreshold = arpThreshold,
    leadThreshold = leadThreshold, leadFloor = leadFloor, sections = sections, delayBeats = delayBeats,
    padRhythmB = padRhythmB ?: if (padRhythm == this.padRhythm) this.padRhythmB else padRhythm,
    padPower = padPower, crowd = crowd, jungle = jungle, signature = signature,
    answer = answer, gain = gain, bassSlide = bassSlide, vinyl = vinyl, dropThreshold = dropThreshold, dropTease = dropTease,
    slideWhistle = slideWhistle,
    whistleFrom = whistleFrom, whistleRange = whistleRange, whistleEvery = whistleEvery, swing8 = swing8,
    chromaticSnap = chromaticSnap, hookA2 = hookA2, leadCeiling = leadCeiling, tempoLock = tempoLock,
)
