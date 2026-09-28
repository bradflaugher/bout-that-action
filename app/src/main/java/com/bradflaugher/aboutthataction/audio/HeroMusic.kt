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
 *  - BEAST: stadium marching-band funk. Drumline snare with rolls, claps, tambourine, a
 *    boomy 808 kick and bass, a brass section stabbing chords, a bell lyre, a trumpet
 *    shout for a hook and a crowd that roars into every fill.
 *  - ACE: spy jazz / surf. Swung ride and brushes, a walking upright bass, lush 7th/9th
 *    chords on strings, a tremolo vibraphone and a twangy, tremolo-picked surf guitar.
 *  - HARDY: 80s action rock on the worst Christmas ever. Gated-reverb snare, big toms,
 *    sleigh bells, palm-muted power chords, tubular bells and an overdriven guitar lead
 *    whose hook is Beethoven's "Ode to Joy" (public domain); his sneak mix tiptoes on
 *    pizzicato under the "Shchedryk" bell ostinato (Leontovych, public domain).
 *  - VOLT: chiptune cyberpunk. Pulse-wave everything, fast arps, bit-crushed drums, glitch
 *    stutters, a hard sidechain pump and a tempo nudge up.
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

    // ---- Signatures (original, except HARDY's public-domain quotes) ------------------------

    /** BEAST: a horn-section shout — octave hit, bounce, and a tumble down to the third. */
    private val beastSig = Motif("7:2 .:1 7:1 4:2 6:1 7:1 .:2 4:2 3:2 2:2")
    private val beastAns = Motif("4:1 4:1 .:2 2:2 4:2 .:2 0:2 2:4")

    /** ACE: a slinky surf-spy descent from the fifth, a breath, and home. */
    private val aceSig = Motif("4:3 3:1 2:2 1:2 .:2 2:2 0:4")
    private val aceAns = Motif("0:2 2:2 4:2 6:6 .:4")

    /** HARDY: the head and tail of Beethoven's "Ode to Joy" (1824). */
    private val hardySig = Motif("2:2 2:2 3:2 4:2 4:2 3:2 2:2 1:2")
    private val hardyAns = Motif("0:2 0:2 1:2 2:2 2:3 1:1 1:4")

    /** HARDY sneaking: the four-note "Shchedryk" bell ostinato (Leontovych, 1916), twice. */
    private val hardyBells = Motif("2:2 1:1 2:1 0:2 2:2 1:1 2:1 0:2 .:4")

    /** VOLT: a chip arpeggio up to the octave, a flick past it and a bounce down. */
    private val voltSig = Motif("0:1 2:1 4:1 7:1 .:2 7:1 9:1 7:2 4:2 5:2 4:2")
    private val voltAns = Motif("7:1 6:1 4:1 2:1 4:2 .:2 2:1 1:1 0:2 .:4")

    // ---- Loudness trims (measured: each arrangement matches its zone's own track) ----------

    // Zones in order: ROOFTOP, TOWER, LABS, METRO, MINES, MAGMA, HELL, VOID.
    private val HOT_TRIM = arrayOf(
        floatArrayOf(1.06f, 1.00f, 0.94f, 0.98f, 1.00f, 1.01f, 1.11f, 0.98f), // BEAST
        floatArrayOf(0.97f, 0.87f, 0.89f, 0.85f, 0.85f, 0.88f, 0.90f, 0.82f), // ACE
        floatArrayOf(1.12f, 1.05f, 1.02f, 1.04f, 1.04f, 1.05f, 1.12f, 1.00f), // HARDY
        floatArrayOf(1.11f, 1.04f, 1.11f, 1.10f, 1.12f, 1.15f, 1.19f, 1.05f), // VOLT
    )
    private val SNEAK_TRIM = arrayOf(
        floatArrayOf(0.99f, 0.97f, 0.98f, 0.98f, 1.00f, 0.97f, 0.97f, 0.98f), // BEAST
        floatArrayOf(0.97f, 0.95f, 1.00f, 0.94f, 1.07f, 0.97f, 1.01f, 0.95f), // ACE
        floatArrayOf(1.01f, 0.99f, 0.98f, 1.01f, 1.01f, 0.99f, 0.99f, 1.02f), // HARDY
        floatArrayOf(0.93f, 0.93f, 0.90f, 0.89f, 0.99f, 0.93f, 0.92f, 0.94f), // VOLT
    )
    private val THEME_TRIM = floatArrayOf(1.06f, 0.85f, 1.03f, 1.04f)

    // ---- BEAST -----------------------------------------------------------------------------

    private fun beastKit(base: SongSpec, t: Tint) = DrumTuning(
        kickHi = 150f, kickLo = 40f, kickPitchDecay = 0.06f, kickDecay = boom(base, 1.4f, 0.65f), kickClick = 0.35f,
        kickDrive = 0.5f, snareTone = 240f, snareNoiseHz = 5600f, snareDecay = 0.13f, snareToneMix = 0.45f,
        snareLevel = 0.85f, snareVerb = 0.35f, clapHz = 1300f, clapDecay = 0.2f, clapLevel = 0.6f,
        tomHz = 130f, tomLevel = 0.55f, hatLevel = 0.3f, jingleHz = 6500f, jingleDecay = 0.12f, jingleNoise = 0.65f,
        jingleLevel = 0.22f, crashLevel = 0.36f, drive = t.drive, crush = t.crush,
    )

    private val beastBass = Patch(
        wave1 = Wave.SINE, wave2 = Wave.TRIANGLE, osc2Level = 0.35f, sub = 0.35f, cutoff = 1200f, q = 0.8f,
        envAmt = 1f, keyTrack = 0.3f, a = 0.002f, d = 0.6f, s = 0.6f, r = 0.12f, fd = 0.2f, drive = 0.9f,
        glide = 0.04f, gain = 0.36f, bright = 0.4f,
    )
    private val beastBrass = Patch(
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

    private fun beastHot(base: SongSpec, t: Tint, name: String, hook: Melody? = null): SongSpec {
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
            kit = beastKit(base, t),
            bassA = "R..R..O.R.rR..F.", bassB = "R.rR..O.R.rRO.Fo",
            arpA = "0.2.4...2.4.5...", arpB = "5.4.3.2.4.3.2.1.", arpGate = 0.5f, arpCenter = base.arpCenter + 5,
            padRhythm = "x.-...x.-.x.-...", padRhythmB = "x.......x.-.x.-.",
            leadOctave = leadOctave(base, 58),
            leadTemplates = arrayOf(beastSig.rhythm, "x.x...x.x.x...x.", "x..x..x...x.x..."),
            motifSeed = base.motifSeed + 11, hook = hook, signature = beastSig, answer = beastAns,
            pad = beastBrass.tinted(t, 0.3f), bass = beastBass.tinted(t, 0.4f), arp = bellLyre.tinted(t, 0f),
            lead = trumpet.tinted(t, 0.5f),
            mix = Mix(
                pad = 1.6f, bass = 0.85f, arp = 1.35f, lead = 1.1f, drums = 0.43f * zd, padVerb = 0.2f, arpDelay = 0.3f,
                arpVerb = 0.25f, leadDelay = 0.18f, leadVerb = 0.2f, padDuck = 0.35f, bassDuck = 0.3f, arpDuck = 0.15f, arpPan = 0.3f,
            ),
            crowd = 0.3f,
        )
    }

    private fun beastSneak(base: SongSpec, t: Tint, name: String) = base.derive(
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
        leadTemplates = arrayOf(beastSig.rhythm, "x.......x.......", "x...........x..."),
        signature = beastSig, answer = beastAns,
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

    // ---- ACE -------------------------------------------------------------------------------

    private fun aceKit(base: SongSpec, t: Tint) = DrumTuning(
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

    private fun aceHot(base: SongSpec, t: Tint, name: String, hook: Melody? = null): SongSpec {
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
            kit = aceKit(base, t),
            bassA = "R...T...F...A...", bassB = "R...S...T...A...",
            arpA = "0..2..4..3..1...", arpB = "4..3..2..1..0.2.", arpGate = 1.2f,
            padRhythm = "x...............", padRhythmB = "x.....x.........",
            leadOctave = leadOctave(base, 54),
            leadTemplates = arrayOf(aceSig.rhythm, "x..x..x.x.......", "x.x...x..x.x...."),
            motifSeed = base.motifSeed + 23, hook = hook, signature = aceSig, answer = aceAns,
            pad = strings.tinted(t, 0.1f), bass = upright.tinted(t, 0.2f), arp = vibes, lead = surfGuitar.tinted(t, 0.6f),
            mix = Mix(
                pad = 1f, bass = 1f, arp = 1.25f, lead = 1.55f, drums = 0.67f * zoneDrums(base), padVerb = 0.35f,
                arpDelay = 0.3f, arpVerb = 0.35f, leadDelay = 0.3f, leadVerb = 0.45f, padDuck = 0.2f, bassDuck = 0.15f,
                arpDuck = 0.1f, arpPan = 0.35f,
            ),
        )
    }

    private fun aceSneak(base: SongSpec, t: Tint, name: String) = base.derive(
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
        leadTemplates = arrayOf(aceSig.rhythm, "x.......x.......", "x...........x..."),
        signature = aceSig, answer = aceAns, leadOctave = leadOctave(base, 58),
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

    // ---- HARDY -----------------------------------------------------------------------------

    private fun hardyKit(base: SongSpec, t: Tint) = DrumTuning(
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

    private fun hardyHot(base: SongSpec, t: Tint, name: String, hook: Melody? = null): SongSpec {
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
            kit = hardyKit(base, t),
            bassA = "R.R.R.R.R.R.O.F.", bassB = "R.rrR.rrR.rrO.rr",
            arpA = "0.......4.......", arpB = "3...2...1...0...", arpGate = 2f,
            padRhythm = "x-x-x-x-x-x-x-x-", padRhythmB = "x.......x.....-.", padPower = true,
            padCenter = base.padCenter - 7,
            leadOctave = leadOctave(base, 60),
            leadTemplates = arrayOf(hardySig.rhythm, "x...x.x.x...x...", "x.x.x...x..xx..."),
            motifSeed = base.motifSeed + 37, hook = hook, signature = hardySig, answer = hardyAns,
            pad = powerChords.tinted(t, 0.3f), bass = rockBass.tinted(t, 0.3f), arp = tubularBells, lead = rockLead.tinted(t, 0.4f),
            mix = Mix(
                pad = 2.5f, bass = 1f, arp = 1.35f, lead = 1.45f, drums = 0.48f * zoneDrums(base), padVerb = 0.15f,
                arpDelay = 0.2f, arpVerb = 0.4f, leadDelay = 0.25f, leadVerb = 0.25f, padDuck = 0.3f, bassDuck = 0.3f,
                arpDuck = 0.1f, arpPan = 0.3f,
            ),
        )
    }

    private fun hardySneak(base: SongSpec, t: Tint, name: String) = base.derive(
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
        leadTemplates = arrayOf(hardyBells.rhythm, "x.......x.......", "x...........x..."),
        signature = hardyBells, answer = null, leadThreshold = 0.35f, leadOctave = leadOctave(base, 62),
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

    // ---- VOLT ------------------------------------------------------------------------------

    private const val VOLT_TEMPO = 1.06f

    private fun voltKit(base: SongSpec, t: Tint) = DrumTuning(
        kickHi = 200f, kickLo = 50f, kickPitchDecay = 0.025f, kickDecay = boom(base, 1f, 0.45f), kickClick = 0.6f,
        snareTone = 260f, snareNoiseHz = 7000f, snareDecay = 0.12f, snareToneMix = 0.35f,
        hatTone = 1.3f, hatDecay = 0.03f, hatLevel = 0.3f,
        percHz = 1800f, percRatio = 2f, percDecay = 0.04f, percFm = 3f, percNoise = 0f, percLevel = 0.22f,
        drive = maxOf(0.2f, t.drive), crush = maxOf(3, t.crush),
    )

    private val chipBass = Patch(
        wave1 = Wave.PULSE, wave2 = Wave.SQUARE, pw = 0.25f, osc2Semi = -12f, osc2Level = 0.4f, detune = 0f, cutoff = 2200f,
        q = 0.8f, envAmt = 0.8f, a = 0.001f, d = 0.2f, s = 0.8f, r = 0.04f, gain = 0.2f, crush = 2, bright = 0.5f,
    )
    private val pwmPad = Patch(
        wave1 = Wave.PULSE, wave2 = Wave.PULSE, pw = 0.35f, pwm = 0.2f, osc2Level = 0.6f, detune = 0.12f, cutoff = 1600f,
        q = 0.8f, envAmt = 0.6f, a = 0.02f, d = 0.8f, s = 0.7f, r = 0.3f, gain = 0.1f,
    )
    private val chipArp = Patch(
        wave1 = Wave.PULSE, pw = 0.125f, cutoff = 5000f, q = 0.7f, a = 0.001f, d = 0.12f, s = 0.5f, r = 0.03f,
        gain = 0.13f, crush = 3, bright = 0.3f,
    )
    private val chipLead = Patch(
        wave1 = Wave.SQUARE, wave2 = Wave.PULSE, pw = 0.25f, osc2Semi = 12f, osc2Level = 0.25f, detune = 0f, cutoff = 4500f,
        q = 0.7f, a = 0.002f, d = 0.3f, s = 0.75f, r = 0.1f, glide = 0.025f, vibrato = 0.3f, vibRate = 7.5f,
        gain = 0.13f, crush = 2, bright = 0.4f,
    )

    private fun voltHot(base: SongSpec, t: Tint, name: String, hook: Melody? = null): SongSpec {
        val hell = base === Songs.hell
        return base.derive(
            name = name, bpm = base.bpm * VOLT_TEMPO, tempoScale = VOLT_TEMPO,
            drumsA = DrumPattern(
                kick = base.drumsA.kick, snare = if (hell) base.drumsA.snare else "....X.......X...",
                hat = "xoxoxoxoxoxoxoxo", perc = "..x.....x.x.....",
            ),
            drumsB = DrumPattern(
                kick = base.drumsB.kick, snare = if (hell) base.drumsB.snare else "....X..x....X.x.",
                hat = "XoxoXoxoXoxoXoxo", perc = "x..x..x..x..x.x.",
            ),
            fill = DrumPattern(kick = "X.X.X.X.X.X.XXXX", snare = "....X...rrrrrrrr", hat = "xoxoxoxoxoxoxoxo"),
            kit = voltKit(base, t),
            bassA = "R.O.R.O.R.O.R.O.", bassB = "R.OrR.OrR.OrR.Or",
            arpA = "0240240240240240", arpB = "5420542054205421", arpGate = 0.6f, arpCenter = base.arpCenter + 5,
            padRhythm = "x...............",
            leadOctave = leadOctave(base, 62),
            leadTemplates = arrayOf(voltSig.rhythm, "x.xx.x.xx.x.x...", "x..x.xx..x.x.x.."),
            motifSeed = base.motifSeed + 53, hook = hook, signature = voltSig, answer = voltAns,
            pad = pwmPad.tinted(t, 0.2f), bass = chipBass.tinted(t, 0.2f), arp = chipArp, lead = chipLead.tinted(t, 0.2f),
            mix = Mix(
                pad = 0.72f, bass = 0.8f, arp = 1f, lead = 0.9f, drums = 0.55f * zoneDrums(base), padVerb = 0.2f,
                arpDelay = 0.35f, arpVerb = 0.1f, leadDelay = 0.25f, leadVerb = 0.15f, padDuck = if (hell) 0.4f else 0.85f, bassDuck = if (hell) 0.3f else 0.6f,
                arpDuck = if (hell) 0.2f else 0.45f, arpPan = 0.3f,
            ),
            stutter = 0.04f,
        )
    }

    private fun voltSneak(base: SongSpec, t: Tint, name: String) = base.derive(
        name = name, bpm = base.bpm * VOLT_TEMPO, tempoScale = VOLT_TEMPO,
        drumsA = DrumPattern(kick = base.drumsA.kick, hat = "x...o...x...o...", perc = "......x.......x."),
        drumsB = DrumPattern(kick = base.drumsB.kick, snare = "....o.......x...", hat = "x.o.x.o.x.o.x.o.", perc = "..x...x...x..xx."),
        fill = DrumPattern(kick = base.fill.kick, snare = "..........o.o.or", hat = "x.o.x.o.x.o.oooo"),
        kit = DrumTuning(
            kickHi = 130f, kickLo = 48f, kickPitchDecay = 0.04f, kickDecay = 0.3f, kickClick = 0.2f, kickDrive = 0.2f,
            snareTone = 240f, snareNoiseHz = 6000f, snareDecay = 0.1f, snareLevel = 0.4f, snareVerb = 0.5f,
            hatTone = 1.3f, hatDecay = 0.025f, hatLevel = 0.16f,
            percHz = 2100f, percRatio = 2f, percDecay = 0.035f, percFm = 2f, percNoise = 0f, percLevel = 0.2f,
            crush = maxOf(4, t.crush),
        ),
        arpA = "..2...4...2...5.", arpB = "..3.......1...4.", arpGate = 1f,
        leadTemplates = arrayOf(voltSig.rhythm, "x.......x.......", "x...........x..."),
        signature = voltSig, answer = voltAns, leadOctave = leadOctave(base, 62),
        pad = Patch(wave1 = Wave.PULSE, pw = 0.4f, pwm = 0.25f, cutoff = 700f, a = 1.5f, d = 1f, s = 0.9f, r = 1.8f, gain = 0.1f, bright = 0.2f),
        bass = Patch(wave1 = Wave.PULSE, pw = 0.3f, pwm = 0.2f, sub = 0.6f, cutoff = 300f, a = 0.6f, d = 1f, s = 1f, r = 1.2f, gain = 0.2f, bright = 0.2f),
        arp = Patch(wave1 = Wave.TRIANGLE, cutoff = 5000f, a = 0.001f, d = 0.5f, s = 0f, r = 0.4f, gain = 0.16f, crush = 4, bright = 0.1f),
        lead = Patch(wave1 = Wave.TRIANGLE, cutoff = 5000f, a = 0.01f, d = 0.4f, s = 0.8f, r = 0.3f, glide = 0.03f, vibrato = 0.3f, vibRate = 6.5f, gain = 0.18f, crush = 2, bright = 0.2f),
        mix = scaled(base.mix, pad = 0.35f, arp = 1.6f, arpDelay = 0.7f, padDuck = 0.6f, bassDuck = 0.4f, arpDuck = 0.2f),
        stutter = 0.015f,
    )

    // ---- Themes (hero picker) --------------------------------------------------------------

    private fun tri(scale: IntArray, vararg degrees: Int) = Array(degrees.size) { Chord.diatonic(scale, degrees[it]) }

    /** A theme's skeleton: key, chords, tempo and section plan; the hero's band fills it in. */
    private fun themeBase(name: String, bpm: Float, tonic: Int, scale: IntArray, progA: Array<Chord>, progB: Array<Chord>, seed: Long) =
        Songs.title.derive(
            name = name, bpm = bpm, tonic = tonic, scale = scale, progA = progA, progB = progB, motifSeed = seed,
            hook = null, bassCenter = 36 + Math.floorMod(tonic - 36, 12).coerceAtMost(6), padCenter = 62, arpCenter = 62,
        )

    /** "Beast Quake": D minor stomp, i–VII–VI–VII with a horn-section shout. */
    private val beastHook = Melody(
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
    private val aceHook = Melody(
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
    private val hardyHook = Melody(
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

    /** "Have You Tried Turning It Off": F# minor chip anthem, i–VI–III–VII. */
    private val voltHook = Melody(
        arrayOf(
            "F#4:1 A4:1 C#5:1 F#5:1 -:2 F#5:1 A5:1 F#5:2 C#5:2 D5:2 C#5:2",
            "D4:1 F#4:1 A4:1 D5:1 -:2 D5:1 F#5:1 D5:2 A4:2 B4:2 A4:2",
            "E4:1 A4:1 C#5:1 E5:1 -:2 E5:1 A5:1 E5:2 C#5:2 D5:2 E5:2",
            "B4:2 E5:2 G#5:4 F#5:1 E5:1 D5:1 C#5:1 B4:4",
            "F#4:1 A4:1 C#5:1 F#5:1 -:2 F#5:1 A5:1 F#5:2 C#5:2 D5:2 C#5:2",
            "D4:1 F#4:1 A4:1 D5:1 -:2 D5:1 F#5:1 D5:2 A4:2 B4:2 A4:2",
            "E4:1 A4:1 C#5:1 E5:1 -:2 E5:1 A5:1 E5:2 C#5:2 D5:2 E5:2",
            "E5:1 D5:1 C#5:1 B4:1 C#5:4 E5:2 G#5:2 F#5:4",
        ),
    )

    private fun buildTheme(h: Hero): SongSpec {
        val t = tint(null)
        val trim = THEME_TRIM[h.ordinal]
        return when (h) {
            Hero.BEAST -> beastHot(
                themeBase("beast-theme", 100f, 50, AEOLIAN, tri(AEOLIAN, 0, 6, 5, 6), arrayOf(Chord.diatonic(AEOLIAN, 3), Chord.diatonic(AEOLIAN, 0), Chord.diatonic(AEOLIAN, 5), Chord.of(AEOLIAN, 4, Quality.MAJ)), 2401),
                t, "beast-theme", beastHook,
            )
            Hero.ACE -> aceHot(
                themeBase("ace-theme", 144f, 48, DORIAN, tri(DORIAN, 0, 3, 0, 3), tri(DORIAN, 2, 3, 4, 0), 7007),
                t, "ace-theme", aceHook,
            )
            Hero.HARDY -> hardyHot(
                themeBase("hardy-theme", 132f, 50, IONIAN, tri(IONIAN, 0, 4, 5, 0, 0, 4, 3, 4), tri(IONIAN, 5, 3, 0, 4), 1988),
                t, "hardy-theme", hardyHook,
            )
            Hero.VIPER -> voltHot(
                themeBase("viper-theme", 142f, 54, AEOLIAN, tri(AEOLIAN, 0, 5, 2, 6), tri(AEOLIAN, 3, 5, 6, 0), 8088),
                t, "viper-theme", voltHook,
            )
        }.derive(gain = trim, fixedIntensity = 0.85f)
    }

    // ---- Tables ----------------------------------------------------------------------------

    private fun arrange(h: Hero, z: Zone, silent: Boolean): SongSpec {
        val base = Songs.forZone(z, silent)
        val t = tint(z)
        val name = if (silent) "${Songs.forZone(z).name}-${h.name.lowercase()}-sneak" else "${base.name}-${h.name.lowercase()}"
        val spec = when (h) {
            Hero.BEAST -> if (silent) beastSneak(base, t, name) else beastHot(base, t, name)
            Hero.ACE -> if (silent) aceSneak(base, t, name) else aceHot(base, t, name)
            Hero.HARDY -> if (silent) hardySneak(base, t, name) else hardyHot(base, t, name)
            Hero.VIPER -> if (silent) voltSneak(base, t, name) else voltHot(base, t, name)
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
    stutter: Float = this.stutter,
    tempoScale: Float = this.tempoScale,
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
    padPower = padPower, crowd = crowd, stutter = stutter, tempoScale = tempoScale, signature = signature,
    answer = answer, gain = gain,
)
