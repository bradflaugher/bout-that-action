package com.bradflaugher.aboutthataction.audio

import com.bradflaugher.aboutthataction.audio.Scales.AEOLIAN
import com.bradflaugher.aboutthataction.audio.Scales.DORIAN
import com.bradflaugher.aboutthataction.audio.Scales.IONIAN
import com.bradflaugher.aboutthataction.engine.Hero
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.sqrt

/**
 * Every hero's own soundtrack: each zone's track (GUNS HOT and its SILENT sneak mix) in the
 * hero's arrangement, plus a signature theme for the hero picker.
 *
 * An arrangement keeps what makes a zone that zone — key, mode, chords, bars per chord,
 * section plan, the kick's skeleton, the ambience (wind, rotor, VOID's glitches) and the
 * intensity layering thresholds — and swaps in the hero's band: patches, drum kit, the
 * snare/hat/percussion parts, bass/arp/pad rows, swing, tempo nudge and their signature
 * motif (the hero's leitmotif, transposed over every zone's chords). Each zone also tints
 * the band: cold zones darken filters, the deep ones add grit, the mines crush the drums.
 *
 *  - BULL: stadium marching-band funk. Drumline snare with rolls, claps, tambourine, a
 *    boomy 808 kick and bass, a brass section stabbing chords, a bell lyre, a trumpet
 *    shout for a hook and a crowd that roars into every fill.
 *  - FOX: spy jazz / surf. Swung ride and brushes, a walking upright bass, lush 7th/9th
 *    chords on strings, a tremolo vibraphone and a twangy, tremolo-picked surf guitar.
 *  - BADGER: 80s action rock on the worst Christmas ever. Gated-reverb snare, big toms,
 *    sleigh bells, palm-muted power chords, tubular bells and an overdriven guitar lead
 *    whose hook is Beethoven's "Ode to Joy" (public domain); his sneak mix tiptoes on
 *    pizzicato under the "Shchedryk" bell ostinato (Leontovych, public domain).
 *  - VIPER: 80s action-movie / tactical-espionage score. Military snare cadences and rolls,
 *    taiko-like war toms, log drums and a shaker, a low pulsing stealth bassline, dark synth
 *    pads, brass stabs and a heroic minor-key French horn call. His sneak mix is pure
 *    tension: a ticking clock, a low drone, a distant war drum, sparse plucks and crickets. (BADGER is
 *    the rock band — guitars, gated snare, sleigh bells; VIPER is the orchestra and drums.)
 *
 * All of it is built once (at [SoundEngine] construction) and never allocates while playing.
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

    /** A base kick decay scaled for a boomier kit, kept tight on fast tracks. */
    private fun boom(base: SongSpec, mul: Float, max: Float): Float = (base.kit.kickDecay * mul).coerceIn(0.2f, max)

    /** Drum level relative to the zone's own balance (dense, driven kits sit lower). */
    private fun zoneDrums(base: SongSpec): Float =
        sqrt(base.mix.drums / 0.57f).coerceIn(0.75f, 1.2f) * (if (base === Songs.hell) 0.65f else 1f)

    /** [m] with its channel levels scaled (a hero's sneak mix, rebalanced for their band). */
    private fun scaled(
        m: Mix,
        pad: Float = 1f,
        bass: Float = 1f,
        arp: Float = 1f,
        lead: Float = 1f,
        drums: Float = 1f,
        arpDelay: Float = m.arpDelay,
        padDuck: Float = m.padDuck,
        bassDuck: Float = m.bassDuck,
        arpDuck: Float = m.arpDuck,
    ) = Mix(
        pad = m.pad * pad, bass = m.bass * bass, arp = m.arp * arp, lead = m.lead * lead, drums = m.drums * drums,
        padVerb = m.padVerb, arpDelay = arpDelay, arpVerb = m.arpVerb, leadDelay = m.leadDelay, leadVerb = m.leadVerb,
        padDuck = padDuck, bassDuck = bassDuck, arpDuck = arpDuck, arpPan = m.arpPan,
    )

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

    // ---- Signatures (original, except BADGER's public-domain quotes) ------------------------

    /** BULL: a horn-section shout — octave hit, bounce, and a tumble down to the third. */
    private val bullSig = Motif("7:2 .:1 7:1 4:2 6:1 7:1 .:2 4:2 3:2 2:2")
    private val bullAns = Motif("4:1 4:1 .:2 2:2 4:2 .:2 0:2 2:4")

    /** FOX: a slinky surf-spy descent from the fifth, a breath, and home. */
    private val foxSig = Motif("4:3 3:1 2:2 1:2 .:2 2:2 0:4")
    private val foxAns = Motif("0:2 2:2 4:2 6:6 .:4")

    /** BADGER: the head and tail of Beethoven's "Ode to Joy" (1824). */
    private val badgerSig = Motif("2:2 2:2 3:2 4:2 4:2 3:2 2:2 1:2")
    private val badgerAns = Motif("0:2 0:2 1:2 2:2 2:3 1:1 1:4")

    /** BADGER sneaking: the four-note "Shchedryk" bell ostinato (Leontovych, 1916), twice. */
    private val badgerBells = Motif("2:2 1:1 2:1 0:2 2:2 1:1 2:1 0:2 .:4")

    /** VIPER: a horn call — the root twice, a leap to the fifth held, and a turn back to the third. */
    private val viperSig = Motif("0:3 0:1 4:6 3:1 2:1 1:2 2:2")
    private val viperAns = Motif("4:2 5:2 4:2 2:2 0:4 .:4")

    // ---- Loudness trims (measured: each arrangement matches its zone's own track) ----------

    // Zones in order: ROOFTOP, TOWER, LABS, METRO, MINES, MAGMA, HELL, VOID.
    private val HOT_TRIM = arrayOf(
        floatArrayOf(1.06f, 1.00f, 0.94f, 0.98f, 1.00f, 1.01f, 1.11f, 0.98f), // BULL
        floatArrayOf(0.97f, 0.87f, 0.89f, 0.85f, 0.85f, 0.88f, 0.90f, 0.82f), // FOX
        floatArrayOf(1.12f, 1.05f, 1.02f, 1.04f, 1.04f, 1.05f, 1.12f, 1.00f), // BADGER
        floatArrayOf(0.89f, 0.83f, 0.79f, 0.84f, 0.85f, 0.87f, 0.94f, 0.85f), // VIPER
    )
    private val SNEAK_TRIM = arrayOf(
        floatArrayOf(0.99f, 0.97f, 0.98f, 0.98f, 1.00f, 0.97f, 0.97f, 0.98f), // BULL
        floatArrayOf(0.97f, 0.95f, 1.00f, 0.94f, 1.07f, 0.97f, 1.01f, 0.95f), // FOX
        floatArrayOf(1.01f, 0.99f, 0.98f, 1.01f, 1.01f, 0.99f, 0.99f, 1.02f), // BADGER
        floatArrayOf(1.01f, 1.01f, 1.01f, 1.03f, 1.03f, 1.01f, 1.00f, 1.06f), // VIPER
    )
    private val THEME_TRIM = floatArrayOf(1.06f, 0.85f, 1.03f, 0.9f)

    // ---- BULL -----------------------------------------------------------------------------

    private fun bullKit(base: SongSpec, t: Tint) = DrumTuning(
        kickHi = 150f, kickLo = 40f, kickPitchDecay = 0.06f, kickDecay = boom(base, 1.4f, 0.65f), kickClick = 0.35f,
        kickDrive = 0.5f, snareTone = 240f, snareNoiseHz = 5600f, snareDecay = 0.13f, snareToneMix = 0.45f,
        snareLevel = 0.85f, snareVerb = 0.35f, clapHz = 1300f, clapDecay = 0.2f, clapLevel = 0.6f,
        tomHz = 130f, tomLevel = 0.55f, hatLevel = 0.3f, jingleHz = 6500f, jingleDecay = 0.12f, jingleNoise = 0.65f,
        jingleLevel = 0.22f, crashLevel = 0.36f, drive = t.drive, crush = t.crush,
    )

    private val bullBass = Patch(
        wave1 = Wave.SINE, wave2 = Wave.TRIANGLE, osc2Level = 0.35f, sub = 0.35f, cutoff = 1200f, q = 0.8f,
        envAmt = 1f, keyTrack = 0.3f, a = 0.002f, d = 0.6f, s = 0.6f, r = 0.12f, fd = 0.2f, drive = 0.9f,
        glide = 0.04f, gain = 0.36f, bright = 0.4f,
    )
    private val bullBrass = Patch(
        wave1 = Wave.SAW, supersaw = true, detune = 0.09f, cutoff = 700f, q = 0.9f, envAmt = 2.4f, keyTrack = 0.3f,
        a = 0.012f, d = 0.35f, s = 0.65f, r = 0.14f, fa = 0.035f, fd = 0.3f, fs = 0.35f, fr = 0.15f,
        drive = 0.25f, gain = 0.13f, bright = 0.6f,
    )
    private val bellLyre = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 17.57f, osc2Level = 0.35f, detune = 0f, cutoff = 6000f,
        keyTrack = 0f, a = 0.001f, d = 0.5f, s = 0f, r = 0.4f, gain = 0.15f, bright = 0.3f,
    )
    private val trumpet = Patch(
        wave1 = Wave.SAW, wave2 = Wave.PULSE, pw = 0.4f, osc2Level = 0.5f, detune = 0.06f, cutoff = 1100f, q = 1f,
        envAmt = 1.8f, a = 0.015f, d = 0.25f, s = 0.75f, r = 0.12f, fa = 0.04f, fd = 0.25f, fs = 0.4f,
        glide = 0.02f, vibrato = 0.22f, vibRate = 5.2f, drive = 0.3f, gain = 0.17f, bright = 0.6f,
    )

    private fun bullHot(base: SongSpec, t: Tint, name: String, hook: Melody? = null): SongSpec {
        val hell = base === Songs.hell
        val zd = zoneDrums(base)
        return base.derive(
            name = name, swing = maxOf(base.swing, 0.06f),
            drumsA = DrumPattern(
                kick = base.drumsA.kick, snare = if (hell) base.drumsA.snare else "....X..o.o..X..r",
                clap = "....X.......X...", hat = "x.xox.xox.xox.xo", open = "......x.........",
                jingle = "....x.......x...",
            ),
            drumsB = DrumPattern(
                kick = base.drumsB.kick, snare = if (hell) base.drumsB.snare else "o.o.X.oo.oo.X.rr",
                clap = "....X..X....X...", hat = "x.x.x.x.x.x.x.x.", tom = "..........3.2...",
                jingle = "..x...x...x...x.",
            ),
            fill = DrumPattern(kick = "X.......X.......", snare = "rrrrXrrrXrXrXXXX", tom = "..3.2.1.....3.1."),
            kit = bullKit(base, t),
            bassA = "R..R..O.R.rR..F.", bassB = "R.rR..O.R.rRO.Fo",
            arpA = "0.2.4...2.4.5...", arpB = "5.4.3.2.4.3.2.1.", arpGate = 0.5f, arpCenter = base.arpCenter + 5,
            padRhythm = "x.-...x.-.x.-...", padRhythmB = "x.......x.-.x.-.",
            leadOctave = leadOctave(base, 58),
            leadTemplates = arrayOf(bullSig.rhythm, "x.x...x.x.x...x.", "x..x..x...x.x..."),
            motifSeed = base.motifSeed + 11, hook = hook, signature = bullSig, answer = bullAns,
            pad = bullBrass.tinted(t, 0.3f), bass = bullBass.tinted(t, 0.4f), arp = bellLyre.tinted(t, 0f),
            lead = trumpet.tinted(t, 0.5f),
            mix = Mix(
                pad = 1.6f, bass = 0.85f, arp = 1.35f, lead = 1.1f, drums = 0.43f * zd, padVerb = 0.2f, arpDelay = 0.3f,
                arpVerb = 0.25f, leadDelay = 0.18f, leadVerb = 0.2f, padDuck = 0.35f, bassDuck = 0.3f, arpDuck = 0.15f, arpPan = 0.3f,
            ),
            crowd = 0.3f,
        )
    }

    private fun bullSneak(base: SongSpec, t: Tint, name: String) = base.derive(
        name = name,
        drumsA = DrumPattern(kick = base.drumsA.kick, snare = "..............r.", hat = "x...x...x...x..."),
        drumsB = DrumPattern(kick = base.drumsB.kick, snare = "......o.......rr", hat = "x.o.x.o.x.o.x.o.", tom = "........1......."),
        fill = DrumPattern(kick = base.fill.kick, snare = "........rrrrrrrr", hat = "x.o.x.o.x.o.oooo"),
        kit = DrumTuning(
            kickHi = 110f, kickLo = 42f, kickPitchDecay = 0.05f, kickDecay = 0.45f, kickClick = 0.05f, kickDrive = 0.1f,
            snareTone = 220f, snareNoiseHz = 5000f, snareDecay = 0.1f, snareToneMix = 0.35f, snareLevel = 0.35f,
            snareVerb = 0.5f, hatTone = 1.1f, hatDecay = 0.03f, hatLevel = 0.14f, tomHz = 80f, tomLevel = 0.4f,
            drive = t.drive * 0.5f, crush = t.crush,
        ),
        arpA = "2.0...........0.", arpB = "..3.2.......1...", arpGate = 1f,
        leadTemplates = arrayOf(bullSig.rhythm, "x.......x.......", "x...........x..."),
        signature = bullSig, answer = bullAns,
        pad = Patch(
            wave1 = Wave.SAW, supersaw = true, detune = 0.07f, cutoff = 520f, q = 0.7f, envAmt = 0.8f, a = 1.2f, d = 1.5f,
            s = 0.85f, r = 1.6f, fa = 1.2f, fd = 2f, fs = 0.5f, gain = 0.1f, bright = 0.2f,
        ).tinted(t, 0.1f),
        bass = Patch(wave1 = Wave.SINE, sub = 0.5f, cutoff = 300f, a = 0.5f, d = 1f, s = 1f, r = 1.2f, drive = 0.3f, gain = 0.3f, bright = 0.2f),
        arp = Patch(
            wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 24f, osc2Level = 0.18f, detune = 0f, cutoff = 4000f,
            a = 0.001f, d = 0.6f, s = 0f, r = 0.5f, gain = 0.16f, bright = 0.1f,
        ),
        lead = Patch(
            wave1 = Wave.SQUARE, wave2 = Wave.SAW, osc2Level = 0.3f, cutoff = 900f, q = 1.2f, envAmt = 0.8f, a = 0.06f,
            d = 0.5f, s = 0.7f, r = 0.5f, fa = 0.06f, fd = 0.4f, fs = 0.3f, glide = 0.05f, vibrato = 0.25f, gain = 0.12f, bright = 0.3f,
        ),
        leadOctave = leadOctave(base, 58),
        mix = scaled(base.mix, pad = 0.6f, bass = 1.1f, drums = 0.9f),
        crowd = 0.12f,
    )

    // ---- FOX -------------------------------------------------------------------------------

    private fun foxKit(base: SongSpec, t: Tint) = DrumTuning(
        kickHi = 120f, kickLo = 50f, kickDecay = boom(base, 1f, 0.45f), kickClick = 0.2f, kickDrive = 0.1f, kickLevel = 0.8f,
        snareTone = 200f, snareNoiseHz = 3400f, snareDecay = 0.16f, snareToneMix = 0.35f, snareLevel = 0.6f, snareVerb = 0.5f,
        hatTone = 0.72f, hatDecay = 0.11f, openDecay = 0.5f, hatLevel = 0.28f, tomHz = 110f, crashDecay = 2.2f,
        crashLevel = 0.25f, drive = t.drive * 0.6f, crush = t.crush,
    )

    private val upright = Patch(
        wave1 = Wave.TRIANGLE, wave2 = Wave.SINE, osc2Semi = 12f, osc2Level = 0.2f, sub = 0.4f, cutoff = 650f, q = 1.1f,
        envAmt = 1.6f, keyTrack = 0.4f, a = 0.003f, d = 0.35f, s = 0.4f, r = 0.15f, fd = 0.12f, gain = 0.4f, bright = 0.3f,
    )
    private val strings = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SAW, osc2Level = 0.8f, detune = 0.14f, cutoff = 1400f, q = 0.6f, envAmt = 0.6f,
        keyTrack = 0.1f, a = 0.35f, d = 1.5f, s = 0.85f, r = 0.9f, fa = 0.4f, fd = 1.5f, fs = 0.4f, gain = 0.09f, bright = 0.7f,
    )
    private val vibes = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 24f, osc2Level = 0.25f, detune = 0f, cutoff = 6000f, keyTrack = 0f,
        a = 0.002f, d = 1.4f, s = 0f, r = 1f, trem = 0.45f, tremRate = 5.5f, gain = 0.17f, bright = 0.2f,
    )
    private val surfGuitar = Patch(
        wave1 = Wave.SAW, wave2 = Wave.PULSE, pw = 0.3f, osc2Level = 0.45f, detune = 0.04f, cutoff = 1500f, q = 1.6f,
        envAmt = 2.2f, keyTrack = 0.4f, a = 0.002f, d = 0.35f, s = 0.45f, r = 0.25f, fa = 0.001f, fd = 0.12f, fs = 0.2f,
        vibrato = 0.28f, vibRate = 6.2f, trem = 0.3f, tremRate = 7.5f, drive = 0.35f, gain = 0.18f, bright = 0.5f,
    )

    private fun foxHot(base: SongSpec, t: Tint, name: String, hook: Melody? = null): SongSpec {
        val hell = base === Songs.hell
        return base.derive(
            name = name, swing = maxOf(base.swing, 0.18f),
            progA = jazz(base.progA, base.scale, ninth = true), progB = jazz(base.progB, base.scale, ninth = true),
            drumsA = DrumPattern(
                kick = base.drumsA.kick, snare = if (hell) base.drumsA.snare else "....X..o..o.X..o",
                hat = "X...x..xX...x..x",
            ),
            drumsB = DrumPattern(
                kick = base.drumsB.kick, snare = if (hell) base.drumsB.snare else "..o.X.o...o.X.oo",
                hat = "X.x.x.x.X.x.x.x.", open = "..............x.",
            ),
            fill = DrumPattern(kick = "X.......X.......", snare = "....X..oX.oXX.XX", tom = "........3.3.2.1."),
            kit = foxKit(base, t),
            bassA = "R...T...F...A...", bassB = "R...S...T...A...",
            arpA = "0..2..4..3..1...", arpB = "4..3..2..1..0.2.", arpGate = 1.2f,
            padRhythm = "x...............", padRhythmB = "x.....x.........",
            leadOctave = leadOctave(base, 54),
            leadTemplates = arrayOf(foxSig.rhythm, "x..x..x.x.......", "x.x...x..x.x...."),
            motifSeed = base.motifSeed + 23, hook = hook, signature = foxSig, answer = foxAns,
            pad = strings.tinted(t, 0.1f), bass = upright.tinted(t, 0.2f), arp = vibes, lead = surfGuitar.tinted(t, 0.6f),
            mix = Mix(
                pad = 1f, bass = 1f, arp = 1.25f, lead = 1.55f, drums = 0.67f * zoneDrums(base), padVerb = 0.35f,
                arpDelay = 0.3f, arpVerb = 0.35f, leadDelay = 0.3f, leadVerb = 0.45f, padDuck = 0.2f, bassDuck = 0.15f,
                arpDuck = 0.1f, arpPan = 0.35f,
            ),
        )
    }

    private fun foxSneak(base: SongSpec, t: Tint, name: String) = base.derive(
        name = name, swing = 0.25f,
        progA = jazz(base.progA, base.scale, ninth = true), progB = jazz(base.progB, base.scale, ninth = true),
        drumsA = DrumPattern(kick = base.drumsA.kick, snare = "o.o.x.o.o.o.x.oo", hat = "x...x..xx...x..x"),
        drumsB = DrumPattern(kick = base.drumsB.kick, snare = "o.o.x.o.o.o.x.o.", hat = "x.o.x..xx.o.x..x"),
        fill = DrumPattern(kick = base.fill.kick, snare = "o.o.o.o.x.o.xoxo", hat = "x...x..xx...x..x"),
        kit = DrumTuning(
            kickHi = 120f, kickLo = 50f, kickPitchDecay = 0.05f, kickDecay = 0.3f, kickClick = 0.1f, kickDrive = 0.1f,
            snareTone = 180f, snareNoiseHz = 2800f, snareDecay = 0.28f, snareToneMix = 0.1f, snareLevel = 0.25f,
            snareVerb = 0.55f, hatTone = 0.7f, hatDecay = 0.08f, hatLevel = 0.13f, drive = 0f, crush = t.crush,
        ),
        bassA = "R...T...F...A...", bassB = "R...F...O...a...",
        padRhythm = "x.....x.........",
        arpA = "......2.......4.", arpB = "..3.........1...", arpGate = 3f,
        leadTemplates = arrayOf(foxSig.rhythm, "x.......x.......", "x...........x..."),
        signature = foxSig, answer = foxAns, leadOctave = leadOctave(base, 58),
        mix = scaled(base.mix, pad = 0.65f, bass = 2.5f),
        pad = Patch(
            wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 12f, osc2Level = 0.3f, detune = 0.03f, cutoff = 3000f,
            a = 0.005f, d = 2.5f, s = 0.3f, r = 1.2f, trem = 0.3f, tremRate = 4.5f, gain = 0.14f, bright = 0.2f,
        ),
        bass = upright.copyish(gain = 0.26f, cutoff = 500f),
        arp = vibes.copyish(gain = 0.15f),
        lead = Patch(
            wave1 = Wave.SQUARE, wave2 = Wave.SAW, osc2Level = 0.25f, cutoff = 1200f, q = 1f, envAmt = 0.6f, a = 0.04f,
            d = 0.5f, s = 0.7f, r = 0.4f, fa = 0.05f, fd = 0.4f, fs = 0.3f, glide = 0.04f, vibrato = 0.3f, gain = 0.12f, bright = 0.3f,
        ),
    )

    // ---- BADGER -----------------------------------------------------------------------------

    private fun badgerKit(base: SongSpec, t: Tint) = DrumTuning(
        kickHi = 155f, kickLo = 48f, kickDecay = boom(base, 1.2f, 0.55f), kickClick = 0.7f, kickDrive = 0.35f,
        snareTone = 175f, snareNoiseHz = 3000f, snareDecay = 0.16f, snareToneMix = 0.6f, snareLevel = 0.8f,
        snareVerb = 0.35f, snareGate = 0.2f, tomHz = 92f, tomLevel = 0.75f, hatLevel = 0.3f,
        jingleHz = 4800f, jingleDecay = 0.2f, jingleNoise = 0.35f, jingleLevel = 0.26f,
        crashLevel = 0.38f, crashDecay = 2f, drive = t.drive, crush = t.crush,
    )

    private val rockBass = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SQUARE, osc2Level = 0.3f, sub = 0.5f, cutoff = 420f, q = 1f, envAmt = 2.2f,
        a = 0.002f, d = 0.25f, s = 0.6f, r = 0.06f, fd = 0.12f, drive = 0.5f, gain = 0.28f, bright = 0.6f,
    )
    private val powerChords = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SAW, osc2Level = 0.8f, detune = 0.1f, cutoff = 1800f, q = 0.9f, envAmt = 1f,
        keyTrack = 0.2f, a = 0.002f, d = 0.25f, s = 0.55f, r = 0.08f, fd = 0.2f, fs = 0.3f, drive = 1.8f, gain = 0.1f, bright = 0.6f,
    )
    private val tubularBells = Patch(
        wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 17.57f, osc2Level = 0.45f, detune = 0f, cutoff = 7000f,
        keyTrack = 0f, a = 0.001f, d = 1.8f, s = 0f, r = 1.5f, gain = 0.13f, bright = 0.1f,
    )
    private val rockLead = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SQUARE, osc2Level = 0.4f, detune = 0.08f, cutoff = 2400f, q = 1.2f, envAmt = 1f,
        a = 0.004f, d = 0.4f, s = 0.8f, r = 0.2f, fd = 0.3f, fs = 0.3f, glide = 0.05f, vibrato = 0.45f, vibRate = 5.8f,
        drive = 1.5f, gain = 0.14f, bright = 0.6f,
    )

    private fun badgerHot(base: SongSpec, t: Tint, name: String, hook: Melody? = null): SongSpec {
        val hell = base === Songs.hell
        return base.derive(
            name = name,
            drumsA = DrumPattern(
                kick = base.drumsA.kick, snare = if (hell) base.drumsA.snare else "....X.......X...",
                hat = "x.x.x.x.x.x.x.x.", jingle = "X...x...X...x...",
            ),
            drumsB = DrumPattern(
                kick = base.drumsB.kick, snare = if (hell) base.drumsB.snare else "....X.......X...",
                hat = "x.x.x.x.x.x.x.x.", open = "..............x.", jingle = "x.x.x.x.x.x.x.x.",
            ),
            fill = DrumPattern(kick = "X.......X.......", snare = "....X...........", tom = "........33221.1."),
            kit = badgerKit(base, t),
            bassA = "R.R.R.R.R.R.O.F.", bassB = "R.rrR.rrR.rrO.rr",
            arpA = "0.......4.......", arpB = "3...2...1...0...", arpGate = 2f,
            padRhythm = "x-x-x-x-x-x-x-x-", padRhythmB = "x.......x.....-.", padPower = true,
            padCenter = base.padCenter - 7,
            leadOctave = leadOctave(base, 60),
            leadTemplates = arrayOf(badgerSig.rhythm, "x...x.x.x...x...", "x.x.x...x..xx..."),
            motifSeed = base.motifSeed + 37, hook = hook, signature = badgerSig, answer = badgerAns,
            pad = powerChords.tinted(t, 0.3f), bass = rockBass.tinted(t, 0.3f), arp = tubularBells, lead = rockLead.tinted(t, 0.4f),
            mix = Mix(
                pad = 2.5f, bass = 1f, arp = 1.35f, lead = 1.45f, drums = 0.48f * zoneDrums(base), padVerb = 0.15f,
                arpDelay = 0.2f, arpVerb = 0.4f, leadDelay = 0.25f, leadVerb = 0.25f, padDuck = 0.3f, bassDuck = 0.3f,
                arpDuck = 0.1f, arpPan = 0.3f,
            ),
        )
    }

    private fun badgerSneak(base: SongSpec, t: Tint, name: String) = base.derive(
        name = name,
        drumsA = DrumPattern(kick = base.drumsA.kick, hat = "x...o...x...o...", jingle = "....o.......o..."),
        drumsB = DrumPattern(kick = base.drumsB.kick, snare = "............o...", hat = "x.o.x.o.x.o.x.o.", jingle = "..o...o...o...o."),
        fill = DrumPattern(kick = base.fill.kick, tom = "..........1.1.11", hat = "x.o.x.o.x.o.oooo"),
        kit = DrumTuning(
            kickHi = 105f, kickLo = 52f, kickPitchDecay = 0.03f, kickDecay = 0.55f, kickClick = 0.05f, kickDrive = 0.15f,
            snareLevel = 0.4f, snareVerb = 0.5f, tomHz = 75f, tomLevel = 0.45f, hatTone = 1.1f, hatDecay = 0.03f,
            hatLevel = 0.14f, jingleHz = 4800f, jingleDecay = 0.25f, jingleNoise = 0.35f, jingleLevel = 0.2f,
            drive = t.drive * 0.5f, crush = t.crush,
        ),
        arpA = "0...4...2...4...", arpB = "0...3...2...1...", arpGate = 0.5f,
        leadTemplates = arrayOf(badgerBells.rhythm, "x.......x.......", "x...........x..."),
        signature = badgerBells, answer = null, leadThreshold = 0.35f, leadOctave = leadOctave(base, 62),
        mix = scaled(base.mix, pad = 0.6f, bass = 1.5f, arp = 2f, drums = 0.8f),
        pad = Patch(
            wave1 = Wave.SAW, supersaw = true, detune = 0.1f, cutoff = 600f, q = 0.75f, envAmt = 0.9f, a = 2f, d = 1.5f,
            s = 0.8f, r = 2.4f, fa = 0.5f, fd = 1.8f, fs = 0.3f, fr = 0.8f, gain = 0.1f, keyTrack = 0.1f, bright = 0.2f,
        ).tinted(t, 0.1f),
        bass = Patch(wave1 = Wave.SAW, sub = 0.6f, cutoff = 300f, a = 1f, d = 1f, s = 1f, r = 1.5f, gain = 0.2f, bright = 0.2f),
        arp = Patch(
            wave1 = Wave.TRIANGLE, wave2 = Wave.SAW, osc2Level = 0.2f, cutoff = 1500f, envAmt = 1.5f, a = 0.001f, d = 0.25f,
            s = 0f, r = 0.2f, fd = 0.1f, gain = 0.2f, bright = 0.2f,
        ),
        lead = Patch(
            wave1 = Wave.SINE, wave2 = Wave.SINE, osc2Semi = 24f, osc2Level = 0.25f, detune = 0f, cutoff = 6000f,
            keyTrack = 0f, a = 0.001f, d = 0.9f, s = 0f, r = 0.8f, gain = 0.15f, bright = 0.1f,
        ),
    )

    // ---- VIPER -----------------------------------------------------------------------------

    /**
     * War drums tuned to the zone's tonic (C2..B2): the low and high drums (a fourth under,
     * a fifth over) land on the key's fifth, so the toms never fight the harmony.
     */
    private fun warDrum(base: SongSpec): Float = Dsp.midiToHz((36 + Math.floorMod(base.tonic, 12)).toFloat())

    private fun viperKit(base: SongSpec, t: Tint) = DrumTuning(
        kickHi = 120f, kickLo = 42f, kickPitchDecay = 0.05f, kickDecay = boom(base, 1.3f, 0.6f), kickClick = 0.3f,
        kickDrive = 0.3f, snareTone = 210f, snareNoiseHz = 5200f, snareDecay = 0.14f, snareToneMix = 0.35f,
        snareLevel = 0.7f, snareVerb = 0.4f, hatTone = 0.9f, hatDecay = 0.03f, hatLevel = 0.18f,
        tomHz = warDrum(base), tomLevel = 0.95f,
        percHz = 330f, percRatio = 1.5f, percDecay = 0.12f, percFm = 0.6f, percNoise = 0.05f, percLevel = 0.35f,
        jingleHz = 7000f, jingleDecay = 0.06f, jingleNoise = 0.95f, jingleLevel = 0.16f,
        crashLevel = 0.32f, crashDecay = 2.4f, drive = t.drive, crush = t.crush,
    )

    private val stealthBass = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SINE, osc2Semi = -12f, osc2Level = 0.5f, sub = 0.4f, cutoff = 260f, q = 1.2f,
        envAmt = 2f, keyTrack = 0.4f, a = 0.002f, d = 0.18f, s = 0.35f, r = 0.06f, fd = 0.1f, drive = 0.2f,
        gain = 0.32f, bright = 0.5f,
    )
    private val darkPad = Patch(
        wave1 = Wave.SAW, supersaw = true, detune = 0.12f, cutoff = 750f, q = 0.8f, envAmt = 0.8f, keyTrack = 0.1f,
        a = 0.8f, d = 1.5f, s = 0.85f, r = 1.2f, fa = 1f, fd = 2f, fs = 0.4f, fr = 1f, gain = 0.13f, bright = 0.6f,
    )
    private val hornStab = Patch(
        wave1 = Wave.SAW, wave2 = Wave.SAW, osc2Level = 0.7f, detune = 0.1f, cutoff = 600f, q = 0.9f, envAmt = 2.6f,
        keyTrack = 0.3f, a = 0.01f, d = 0.3f, s = 0.4f, r = 0.2f, fa = 0.03f, fd = 0.25f, fs = 0.2f,
        gain = 0.16f, bright = 0.6f,
    )
    private val frenchHorn = Patch(
        wave1 = Wave.SAW, wave2 = Wave.TRIANGLE, osc2Level = 0.6f, detune = 0.05f, cutoff = 1100f, q = 0.8f, envAmt = 1.2f,
        keyTrack = 0.4f, a = 0.045f, d = 0.5f, s = 0.85f, r = 0.3f, fa = 0.08f, fd = 0.5f, fs = 0.4f, glide = 0.03f,
        vibrato = 0.2f, vibRate = 5f, drive = 0.1f, gain = 0.19f, bright = 0.5f,
    )

    private fun viperHot(base: SongSpec, t: Tint, name: String, hook: Melody? = null): SongSpec {
        val hell = base === Songs.hell
        return base.derive(
            name = name,
            drumsA = DrumPattern(
                kick = base.drumsA.kick, snare = if (hell) base.drumsA.snare else "....X..r..r.X.rr",
                hat = "..x...x...x...x.", tom = "1.....1.......2.", perc = "..x..x....x..x..",
                jingle = "o.o.o.o.o.o.o.o.",
            ),
            drumsB = DrumPattern(
                kick = base.drumsB.kick, snare = if (hell) base.drumsB.snare else "r.rrX.r.r.rrX.rr",
                tom = "1..1..2.1..1.3.2", perc = "x..x..x...x..x..", jingle = "oooooooooooooooo",
            ),
            fill = DrumPattern(kick = "X.......X.......", snare = "rrrrrrrrrrrrXXXX", tom = "1...1...2.2.3.31"),
            kit = viperKit(base, t),
            bassA = "R..rR..rR..rR.dr", bassB = "R.rRr.rRR.rRr.dO",
            arpA = "0...........2.4.", arpB = "4.2.0.......2.4.", arpGate = 0.8f,
            padRhythm = "x...............",
            leadOctave = leadOctave(base, 55),
            leadTemplates = arrayOf(viperSig.rhythm, "x...x.x.x.......", "x..x..x.x...x..."),
            motifSeed = base.motifSeed + 53, hook = hook, signature = viperSig, answer = viperAns,
            pad = darkPad.tinted(t, 0.1f), bass = stealthBass.tinted(t, 0.2f), arp = hornStab.tinted(t, 0.2f),
            lead = frenchHorn.tinted(t, 0.2f),
            mix = Mix(
                pad = 0.95f, bass = 1.5f, arp = 2.1f, lead = 0.8f, drums = 0.5f * zoneDrums(base), padVerb = 0.4f,
                arpDelay = 0.15f, arpVerb = 0.4f, leadDelay = 0.2f, leadVerb = 0.4f, padDuck = 0.35f, bassDuck = 0.3f,
                arpDuck = 0.1f, arpPan = 0.25f,
            ),
        )
    }

    private fun viperSneak(base: SongSpec, t: Tint, name: String) = base.derive(
        name = name,
        drumsA = DrumPattern(kick = base.drumsA.kick, hat = "x.x.x.x.x.x.x.x.", tom = "..............1.", perc = "...x..........x."),
        drumsB = DrumPattern(
            kick = base.drumsB.kick, snare = "............o...", hat = "x.x.x.x.x.x.x.x.", tom = "1.......1.......",
            perc = "...x......x...x.", jingle = "..o...o...o...o.",
        ),
        fill = DrumPattern(kick = base.fill.kick, snare = "........rrrrrrrr", hat = "x.x.x.x.x.x.x.x.", tom = "1.......1...1.1."),
        kit = DrumTuning(
            kickHi = 110f, kickLo = 45f, kickPitchDecay = 0.05f, kickDecay = 0.4f, kickClick = 0.08f, kickDrive = 0.15f,
            snareTone = 200f, snareNoiseHz = 4500f, snareDecay = 0.1f, snareLevel = 0.35f, snareVerb = 0.5f,
            hatTone = 1.4f, hatDecay = 0.012f, hatLevel = 0.2f, tomHz = warDrum(base), tomLevel = 0.5f,
            percHz = 900f, percRatio = 1.5f, percDecay = 0.04f, percFm = 0.5f, percNoise = 0.1f, percLevel = 0.25f,
            jingleHz = 7000f, jingleDecay = 0.05f, jingleNoise = 0.95f, jingleLevel = 0.1f,
            drive = t.drive * 0.5f, crush = t.crush,
        ),
        arpA = "......2.........", arpB = "..3.........1...", arpGate = 2f,
        leadTemplates = arrayOf(viperSig.rhythm, "x.......x.......", "x...........x..."),
        signature = viperSig, answer = viperAns, leadOctave = leadOctave(base, 55),
        pad = darkPad.copyish(cutoff = 500f, a = 2.5f, r = 2.4f, gain = 0.1f).tinted(t, 0f),
        bass = Patch(wave1 = Wave.SAW, sub = 0.8f, cutoff = 200f, q = 1f, a = 1f, d = 1f, s = 1f, r = 1.5f, gain = 0.26f, bright = 0.2f),
        arp = Patch(
            wave1 = Wave.SAW, wave2 = Wave.TRIANGLE, osc2Semi = 12f, osc2Level = 0.4f, cutoff = 900f, q = 1.4f, envAmt = 2.5f,
            a = 0.001f, d = 0.18f, s = 0f, r = 0.15f, fd = 0.08f, gain = 0.2f, bright = 0.1f,
        ),
        lead = frenchHorn.copyish(cutoff = 800f, gain = 0.13f),
        mix = scaled(base.mix, pad = 0.5f, bass = 1.2f, arp = 3.5f, drums = 0.88f, arpDelay = 0.7f),
        jungle = 0.08f,
    )

    // ---- Themes (hero picker) --------------------------------------------------------------

    private fun tri(scale: IntArray, vararg degrees: Int) = Array(degrees.size) { Chord.diatonic(scale, degrees[it]) }

    /** A theme's skeleton: key, chords, tempo and section plan; the hero's band fills it in. */
    private fun themeBase(name: String, bpm: Float, tonic: Int, scale: IntArray, progA: Array<Chord>, progB: Array<Chord>, seed: Long) =
        Songs.title.derive(
            name = name, bpm = bpm, tonic = tonic, scale = scale, progA = progA, progB = progB, motifSeed = seed,
            hook = null, bassCenter = 36 + Math.floorMod(tonic - 36, 12).coerceAtMost(6), padCenter = 62, arpCenter = 62,
        )

    /** "Aftershock": D minor stomp, i–VII–VI–VII with a horn-section shout. */
    private val bullHook = Melody(
        arrayOf(
            "D5:2 -:1 D5:1 A4:2 C5:1 D5:1 -:2 A4:2 G4:2 F4:2",
            "E5:2 -:1 E5:1 C5:2 D5:1 E5:1 -:2 G5:2 E5:2 C5:2",
            "D5:2 -:1 D5:1 Bb4:2 C5:1 D5:1 -:2 F5:2 D5:2 Bb4:2",
            "C5:3 D5:1 E5:4 G5:2 E5:2 C5:4",
            "D5:2 -:1 D5:1 A4:2 C5:1 D5:1 -:2 A4:2 G4:2 F4:2",
            "E5:2 -:1 E5:1 C5:2 D5:1 E5:1 -:2 G5:2 E5:2 C5:2",
            "D5:2 -:1 D5:1 Bb4:2 C5:1 D5:1 -:2 F5:2 D5:2 Bb4:2",
            "G4:2 C5:2 D5:4 -:2 D5:1 D5:1 E5:4",
        ),
    )

    /** "Licensed to Chill": C dorian, a Cm9–F9 vamp under a surf-guitar descent. */
    private val foxHook = Melody(
        arrayOf(
            "G4:3 F4:1 Eb4:2 D4:2 -:2 Eb4:2 C4:4",
            "C5:3 Bb4:1 A4:2 G4:2 -:2 A4:2 F4:4",
            "G4:2 Bb4:2 C5:2 D5:4 -:2 Eb5:2 D5:2",
            "C5:6 A4:2 Bb4:2 A4:2 G4:2 F4:2",
            "G4:3 F4:1 Eb4:2 D4:2 -:2 Eb4:2 C4:4",
            "C5:3 Bb4:1 A4:2 G4:2 -:2 A4:2 F4:4",
            "G4:2 Bb4:2 C5:2 D5:4 -:2 Eb5:2 D5:2",
            "C5:4 G4:2 Eb4:2 C4:8",
        ),
    )

    /** "Ho Ho Hold On": Beethoven's "Ode to Joy" (public domain) as a D major arena anthem. */
    private val badgerHook = Melody(
        arrayOf(
            "F#5:4 F#5:4 G5:4 A5:4",
            "A5:4 G5:4 F#5:4 E5:4",
            "D5:4 D5:4 E5:4 F#5:4",
            "F#5:6 E5:2 E5:8",
            "F#5:4 F#5:4 G5:4 A5:4",
            "A5:4 G5:4 F#5:4 E5:4",
            "D5:4 D5:4 E5:4 F#5:4",
            "E5:6 D5:2 D5:4 A4:2 B4:1 C#5:1",
        ),
    )

    /** "Into the Green": a heroic G minor horn call over a war-drum march, i–VI–VII–i. */
    private val viperHook = Melody(
        arrayOf(
            "G4:3 D4:1 G4:4 Bb4:2 A4:2 G4:2 A4:2",
            "Bb4:6 G4:2 Eb5:4 D5:2 C5:2",
            "C5:3 A4:1 F5:4 Eb5:2 D5:2 C5:2 D5:2",
            "D5:8 -:4 D4:2 D4:2",
            "G4:3 D4:1 G4:4 Bb4:2 A4:2 G4:2 A4:2",
            "Bb4:6 C5:2 Eb5:4 F5:2 G5:2",
            "F5:4 Eb5:2 D5:2 C5:4 A4:2 C5:2",
            "D5:2 Bb4:2 G4:12",
        ),
    )

    private fun buildTheme(h: Hero): SongSpec {
        val t = tint(null)
        val trim = THEME_TRIM[h.ordinal]
        return when (h) {
            Hero.BULL -> bullHot(
                themeBase("bull-theme", 100f, 50, AEOLIAN, tri(AEOLIAN, 0, 6, 5, 6), arrayOf(Chord.diatonic(AEOLIAN, 3), Chord.diatonic(AEOLIAN, 0), Chord.diatonic(AEOLIAN, 5), Chord.of(AEOLIAN, 4, Quality.MAJ)), 2401),
                t, "bull-theme", bullHook,
            )
            Hero.FOX -> foxHot(
                themeBase("fox-theme", 144f, 48, DORIAN, tri(DORIAN, 0, 3, 0, 3), tri(DORIAN, 2, 3, 4, 0), 7007),
                t, "fox-theme", foxHook,
            )
            Hero.BADGER -> badgerHot(
                themeBase("badger-theme", 132f, 50, IONIAN, tri(IONIAN, 0, 4, 5, 0, 0, 4, 3, 4), tri(IONIAN, 5, 3, 0, 4), 1988),
                t, "badger-theme", badgerHook,
            )
            Hero.VIPER -> viperHot(
                themeBase("viper-theme", 108f, 55, AEOLIAN, tri(AEOLIAN, 0, 5, 6, 0), tri(AEOLIAN, 3, 5, 2, 6), 3161),
                t, "viper-theme", viperHook,
            )
        }.derive(gain = trim, fixedIntensity = 0.85f)
    }

    // ---- Tables ----------------------------------------------------------------------------

    private fun arrange(h: Hero, z: Zone, silent: Boolean): SongSpec {
        val base = Songs.forZone(z, silent)
        val t = tint(z)
        val name = if (silent) "${Songs.forZone(z).name}-${h.name.lowercase()}-sneak" else "${base.name}-${h.name.lowercase()}"
        val spec = when (h) {
            Hero.BULL -> if (silent) bullSneak(base, t, name) else bullHot(base, t, name)
            Hero.FOX -> if (silent) foxSneak(base, t, name) else foxHot(base, t, name)
            Hero.BADGER -> if (silent) badgerSneak(base, t, name) else badgerHot(base, t, name)
            Hero.VIPER -> if (silent) viperSneak(base, t, name) else viperHot(base, t, name)
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
    answer = answer, gain = gain,
)
