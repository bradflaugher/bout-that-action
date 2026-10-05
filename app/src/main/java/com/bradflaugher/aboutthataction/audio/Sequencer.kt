package com.bradflaugher.aboutthataction.audio

import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Plays one [SongSpec]: a 16th-note step sequencer driving pad, bass, arp, lead, drums and
 * ambience, with intensity-driven layering and kick sidechain ducking.
 *
 * Rendering is split into sub-blocks that end exactly on sequencer events, so every note
 * starts sample-accurately and control-rate parameters update at most every 64 samples.
 */
internal class MusicPlayer(private val sr: Int, id: Int) {
    private val pad = Instrument(sr, 8, id * 8 + 1)
    private val bass = Instrument(sr, 1, id * 8 + 2)
    private val arp = Instrument(sr, 4, id * 8 + 3)
    private val lead = Instrument(sr, 1, id * 8 + 4)
    /** The harmony voice under the lead ([Phrase.harmony]). */
    private val harm = Instrument(sr, 1, id * 8 + 5)
    private val kit = DrumKit(sr)
    private val wind = Wind(sr)
    private val rotor = Rotor(sr)
    private val crowd = Crowd(sr)
    private val jungle = Jungle(sr)
    private val vinyl = Vinyl(sr)
    private val whistle = SlideWhistle(sr)
    private var lastFill = false
    private val dropFx = DropFx(sr)
    private val rng = Rng(0x5eed + id.toLong())

    var spec: SongSpec? = null; private set
    private var composer: Composer? = null

    // Transport
    var absStep = -1L; private set
    var stepPos = 1.0; private set
    var bpm = 120f; private set
    /** The song's own tempo: a transition may start it locked to the old grid and glide it home. */
    private var bpmTarget = 120f
    var endStep = Long.MAX_VALUE
    var sequencing = false; private set
    private var swingPending = false
    private var swingAt = 0.0
    private var stutterPending = false
    private var impactPending = false
    private var rollPending = false
    private var rollAt = 0.5
    private var rollVel = 0f
    // Hat roll: [hatRollN] strokes per step; the next one lands at stroke [hatRollK].
    private var hatRollN = 0
    private var hatRollK = 0
    private var hatRollVel = 0f
    /** Which of the roll's strokes sound (bit k: stroke k); triplet hats skip some. */
    private var hatRollMask = -1
    // Trap drop ([SongSpec.dropThreshold]): landed, and the held breath before landing.
    private var dropOn = true
    private var dropGap = false

    // Controls
    var rate = 1f
    var intensity = 0.5f
    private var warp = 1f
    private var warpTarget = 1f
    private var crushOn = false
    private var crushCount = 0
    private var heldL = 0f
    private var heldR = 0f
    private var time = 0.0
    private var crowdSwell = 0f

    // The fade: an equal-power ramp (sine in, cosine out) from [fadeFrom] to [fadeTo].
    private var fade = 0f
    private var fadeFrom = 0f
    private var fadeTo = 0f
    private var rampLeft = 0
    private var rampCos = 1.0
    private var rampSin = 0.0
    private var rampRotC = 1.0
    private var rampRotS = 0.0
    /** Transition drum pickup: from this step up to [endStep] the fill pattern plays, ending on the switch. */
    var fillFrom = Long.MAX_VALUE
    // An accent: a crash and a kick on the next beat (the ALERT hit).
    private var hitPending = false
    val audible: Boolean get() = sequencing || fade > 1e-4f || (rampLeft > 0 && fadeTo > 0f)
    /** Output level of the fade right now (0..1). */
    val level: Float get() = fade
    /** A [SongSpec.dropThreshold] song's drop has landed (or it has none). */
    val dropArmed: Boolean get() = dropOn

    // Layer gains, refreshed every render call from intensity.
    private var lKick = 0f
    private var lSnare = 0f
    private var lHat = 0f
    private var lPerc = 0f
    private var lArp = 0f
    private var lLead = 0f
    private var bright = 1f
    // Loudness trim ([MusicLevels]): followed per sample so a heat change never steps.
    private var levels = MusicLevels.of("")
    private var levelTarget = 1f
    private var levelGain = 1f

    // Sidechain
    private var duckEnv = 0f
    private var duckSm = 0f
    private val duckCoef = Dsp.decay60(0.32f, sr)
    private val duckSmooth = Dsp.onePole(0.003f, sr)

    private val c = Instrument.MAX_CHUNK
    private val padL = FloatArray(c)
    private val padR = FloatArray(c)
    private val bassL = FloatArray(c)
    private val bassR = FloatArray(c)
    private val arpL = FloatArray(c)
    private val arpR = FloatArray(c)
    private val leadL = FloatArray(c)
    private val leadR = FloatArray(c)
    private val harmL = FloatArray(c)
    private val harmR = FloatArray(c)
    private val drL = FloatArray(c)
    private val drR = FloatArray(c)
    private val drRev = FloatArray(c)
    private val ambL = FloatArray(c)
    private val ambR = FloatArray(c)

    val position: Double get() = absStep + stepPos

    /** Pitch classes (bit k: pc k) of the chord now playing, or 0 before the first step. */
    fun chordMask(): Int {
        val sp = spec ?: return 0
        val comp = composer ?: return 0
        if (absStep < 0) return 0
        val ch = comp.chordAt((absStep / 16).toInt())
        var m = 0
        for (iv in ch.intervals) m = m or (1 shl Math.floorMod(sp.tonic + comp.transpose + ch.root + iv, 12))
        return m
    }

    /**
     * Start [s] from bar [startBar] (its section and chords as if it had played there) at
     * [startBpm], gliding to its own tempo; fading in over [fadeInSeconds] (0: at once).
     */
    fun start(s: SongSpec, comp: Composer, impact: Boolean, fadeInSeconds: Float, startBar: Int = 0, startBpm: Float = s.bpm) {
        spec = s
        composer = comp
        comp.reset()
        if (startBar % 8 != 0) comp.begin(startBar / 8)
        pad.kill(); bass.kill(); arp.kill(); lead.kill(); harm.kill(); kit.kill()
        pad.patch = s.pad; bass.patch = s.bass; arp.patch = s.arp; lead.patch = s.lead; harm.patch = s.lead
        kit.setTuning(s.kit)
        absStep = startBar * 16L - 1; stepPos = 1.0
        fillFrom = Long.MAX_VALUE; hitPending = false
        swingPending = false; stutterPending = false; rollPending = false; hatRollN = 0
        crowdSwell = 0f
        lastFill = false
        whistle.kill()
        endStep = Long.MAX_VALUE
        sequencing = true
        bpm = startBpm; bpmTarget = s.bpm
        warp = 1f; warpTarget = 1f; crushOn = false
        impactPending = impact
        duckEnv = 0f; duckSm = 0f
        dropOn = s.dropThreshold < 0f || heat(s) >= s.dropThreshold
        dropGap = false
        dropFx.kill()
        levels = MusicLevels.of(s.name)
        updateLayers(); levelGain = levelTarget
        // Even "at once" takes a few milliseconds, so nothing left in the filters can click.
        fade = 0f; ramp(1f, max(fadeInSeconds, MIN_FADE))
    }

    /**
     * Stop sequencing and fade out over [fadeSeconds] (an equal-power cosine). [release] lets
     * go of every note now; otherwise they ring on to their own ends under the fade, so the old
     * chord bridges into whatever comes next.
     */
    fun stop(fadeSeconds: Float, release: Boolean = true) {
        if (!audible) return
        sequencing = false
        endStep = Long.MAX_VALUE
        fillFrom = Long.MAX_VALUE
        if (release) {
            pad.releaseAll(); bass.releaseAll(); arp.releaseAll(); lead.releaseAll(); harm.releaseAll()
        }
        ramp(0f, fadeSeconds)
    }

    /** Head for gain [to] along a quarter sine over [seconds]. */
    private fun ramp(to: Float, seconds: Float) {
        fadeFrom = fade; fadeTo = to
        val n = max(1, (seconds * sr).toInt())
        rampLeft = n
        val d = Math.PI / 2 / n
        rampCos = 1.0; rampSin = 0.0; rampRotC = kotlin.math.cos(d); rampRotS = kotlin.math.sin(d)
    }

    /** Accent the next beat with a crash and a kick (spotted!). */
    fun hit() {
        if (sequencing) hitPending = true
    }

    fun sanitize() {
        pad.sanitize(); bass.sanitize(); arp.sanitize(); lead.sanitize(); harm.sanitize(); whistle.sanitize()
    }

    private fun samplesPerStep(): Double = sr * 60.0 / (bpm * 4.0)
    private fun inc(): Double = rate * warp / samplesPerStep()

    /** Samples until step [target] begins (0 if it is due now). */
    fun samplesUntil(target: Long): Int {
        val steps = target - absStep - stepPos
        if (steps <= 1e-7) return 0
        return max(1.0, ceil(steps / inc())).toInt().coerceAtMost(Int.MAX_VALUE / 2)
    }

    private fun heat(s: SongSpec) = if (s.fixedIntensity >= 0f) s.fixedIntensity else intensity

    private fun updateLayers() {
        val s = spec ?: return
        val i = heat(s)
        val kt = s.kickThreshold
        lKick = Dsp.smoothstep(kt - 0.1f, kt + 0.1f, i)
        lSnare = Dsp.smoothstep(kt, kt + 0.2f, i)
        lHat = 0.5f + 0.5f * Dsp.smoothstep(0f, 0.6f, i)
        lPerc = Dsp.smoothstep(0.45f, 0.7f, i)
        lArp = if (s.arpThreshold < 0f) 1f else Dsp.smoothstep(s.arpThreshold - 0.1f, s.arpThreshold + 0.1f, i)
        lLead = max(s.leadFloor, Dsp.smoothstep(s.leadThreshold - 0.1f, s.leadThreshold + 0.1f, i))
        bright = Dsp.smoothstep(-0.1f, 1f, i)
        // A trap drop's song is two levels: its bed before the drop, and after it as the heat says.
        levelTarget = MusicLevels.gainAt(levels, i, dropOn, s.dropThreshold)
    }

    private fun cutMul(p: Patch): Float = 2f.pow(-2.3f * (1f - bright) * p.bright)

    /** Accumulate [n] samples into the destination buses starting at [off]. */
    fun render(n: Int, dL: FloatArray, dR: FloatArray, dRev: FloatArray, dDly: FloatArray, off: Int) {
        if (!audible) return
        updateLayers()
        if (bpm != bpmTarget) {
            // Glide home from a tempo locked to the last song's grid (tempo only: no pitch change).
            bpm += (bpmTarget - bpm) * min(1f, n / (sr * BPM_GLIDE))
            if (abs(bpm - bpmTarget) < 0.01f) bpm = bpmTarget
        }
        warp += (warpTarget - warp) * (if (warpTarget < warp) 0.04f else 0.5f) * (n / 256f)
        var done = 0
        while (done < n) {
            if (sequencing) processEvents()
            var chunk = min(n - done, c)
            if (sequencing) chunk = min(chunk, samplesToNext())
            synth(chunk, dL, dR, dRev, dDly, off + done)
            if (sequencing) {
                stepPos += chunk * inc()
                if (stepPos > 1.0 && absStep + 1 >= endStep) stepPos = 1.0
            }
            done += chunk
        }
        time += n.toDouble() / sr
        if (!sequencing && fade < 1e-4f && (rampLeft == 0 || fadeTo == 0f)) {
            fade = 0f; rampLeft = 0
            pad.kill(); bass.kill(); arp.kill(); lead.kill(); harm.kill()
        }
    }

    private fun samplesToNext(): Int {
        var target = 1.0
        val s = spec
        if (swingPending && s != null && swingAt > stepPos) target = min(target, swingAt)
        if (stutterPending && 0.5 > stepPos) target = min(target, 0.5)
        if (rollPending && rollAt > stepPos) target = min(target, rollAt)
        if (hatRollN > 0) target = min(target, hatRollK.toDouble() / hatRollN)
        val k = ceil((target - stepPos) / inc())
        return max(1.0, min(k, c.toDouble())).toInt()
    }

    /** How late (a fraction of a step) the current step plays: swung 16ths, or swung 8ths ([SongSpec.swing8]). */
    private fun swingDelay(s: SongSpec): Double {
        if (s.swing8 > 0f) {
            return when ((absStep % 4).toInt()) {
                2 -> s.swing8.toDouble()
                1, 3 -> s.swing8 * 0.5
                else -> 0.0
            }
        }
        return if (absStep % 2 == 1L && s.swing > 0f) s.swing.toDouble() else 0.0
    }

    private fun processEvents() {
        val s = spec ?: return
        while (true) {
            if (stepPos >= 1.0 - 1e-7) {
                if (absStep + 1 >= endStep) {
                    // Parked on the switch point: the director takes it from here.
                    stepPos = 1.0; return
                }
                stepPos = max(0.0, stepPos - 1.0)
                absStep++
                swingPending = false
                hatRollN = 0
                swingAt = swingDelay(s)
                if (swingAt > 0.0) swingPending = true else fireStep(s)
                continue
            }
            if (swingPending && stepPos >= swingAt - 1e-7) {
                swingPending = false; fireStep(s); continue
            }
            if (rollPending && stepPos >= rollAt - 1e-7) {
                rollPending = false
                kit.snare.trigger(rollVel)
                continue
            }
            if (hatRollN > 0 && stepPos >= hatRollK.toDouble() / hatRollN - 1e-7) {
                if ((hatRollMask shr hatRollK) and 1 != 0) kit.hat.trigger(hatRollVel * rng.vary(0.1f), false)
                if (++hatRollK >= hatRollN) hatRollN = 0
                continue
            }
            if (stutterPending && stepPos >= 0.5 - 1e-7) {
                stutterPending = false
                kit.hat.trigger(0.7f * lHat, false)
                if (lSnare > 0.1f) kit.snare.trigger(0.35f * lSnare)
                continue
            }
            break
        }
    }

    private fun nearest(note: Int, center: Int): Int {
        var n = note
        while (n > center + 6) n -= 12
        while (n < center - 6) n += 12
        return n
    }

    private fun ties(row: String, s: Int): Int {
        var k = 0
        while (s + k + 1 < 16 && row[s + k + 1] == '~') k++
        return k
    }

    /** Steps until the pad should release: next strike/release or chord change. */
    private fun padLength(sp: SongSpec, bar: Int, s: Int, row: String, sustain: Boolean): Int {
        var k = 1
        while (k < 64) {
            val pos = s + k
            val b = bar + pos / 16
            val st = pos % 16
            if (st == 0 && b % sp.barsPerChord == 0) break
            if (!sustain) {
                val ch = row[st]
                if (ch == 'x' || ch == '-') break
            }
            k++
        }
        return k
    }

    private fun fireStep(sp: SongSpec) {
        val comp = composer ?: return
        val bar = (absStep / 16).toInt()
        val s = (absStep % 16).toInt()
        val phraseBar = bar % 8
        if (s == 0 && phraseBar == 0) {
            comp.begin(bar / 8)
            if (sp.glitch) glitchPhrase(bar)
        }
        val sec = comp.section
        val chord = comp.chordAt(bar)
        val stepSamples = (samplesPerStep() / (rate * warp)).toFloat()
        // A note swung late (swung 8ths) gives the lateness back at its end, so it still ends in time.
        val late = if (sp.swing8 > 0f) swingAt.toFloat() else 0f
        val key = sp.tonic + comp.transpose
        val isB = sec == Section.B || sec == Section.B2

        // ---- The drop
        var dropped = true
        var gap = false
        if (sp.dropThreshold >= 0f) {
            val i = heat(sp)
            val cool = i < sp.dropThreshold - 0.15f
            if (s == 0) {
                if (dropGap) {
                    // Land it: a crash, a kick and a sub boom on the downbeat.
                    dropGap = false; dropOn = true; impactPending = true
                    dropFx.boom(Dsp.midiToHz((nearest(key + chord.root, sp.bassCenter) + 12).toFloat()), 0.3f)
                } else if (dropOn && cool) {
                    dropOn = false
                }
            }
            val nextSec = if (phraseBar != 7) sec else sp.phrases?.let { it[(bar / 8 + 1) % it.size].section } ?: sp.sections[(bar / 8 + 1) % sp.sections.size]
            dropped = dropOn && sec != Section.BREAK
            if (s == 12 && !dropped && !dropGap && endStep == Long.MAX_VALUE && nextSec != Section.BREAK &&
                (i >= sp.dropThreshold || (dropOn && !cool))
            ) {
                // Hold your breath: the last beat cuts out under a swell.
                dropGap = true
                bass.releaseAll(); pad.releaseAll(); arp.releaseAll(); lead.releaseAll(); harm.releaseAll()
                dropFx.swell((4 * stepSamples).toInt(), 0.35f)
            }
            gap = dropGap
        }

        // ---- Drums
        val transitionFill = endStep != Long.MAX_VALUE && absStep >= fillFrom && absStep < endStep
        // A transition's fill always ends on the switch, whichever step that is.
        val fs = if (transitionFill) (16 - (endStep - absStep)).toInt().coerceIn(0, 15) else s
        val ps = if (transitionFill) fs else s
        val fill = transitionFill ||
            (phraseBar == 7 && s >= 12 && lSnare > 0.3f) ||
            (bar % 16 == 15 && s >= 8 && lSnare > 0.5f)
        val brk = sec == Section.BREAK && !fill
        val plan = comp.phrase
        val build = plan?.build?.takeIf { phraseBar >= 6 }
        val pat = if (fill) plan?.fill ?: sp.fill else build ?: plan?.drums ?: if (isB) sp.drumsB else sp.drumsA
        // A transition's fill always sounds, as hard as the heat: a calm one is a soft one.
        val fillMin = 0.3f + 0.3f * heat(sp)
        val kMul = if (transitionFill) max(lKick, fillMin) else if (dropped) lKick else 0f
        val sMul = if (transitionFill) max(lSnare, fillMin) else lSnare
        if (!brk && !gap) {
            val kv = DrumPattern.velocity(pat.kick[ps]) * kMul
            if (kv > 0.02f) {
                kit.kick.trigger(kv * rng.vary(0.05f)); duckEnv = max(duckEnv, kv)
            }
            val sc = pat.snare[ps]
            val sv = DrumPattern.velocity(sc) * sMul
            if (sv > 0.02f) {
                kit.snare.trigger(sv * rng.vary(0.06f))
                if (sc == 'r') {
                    // Roll: a second stroke halfway to the next step.
                    rollPending = true; rollAt = (stepPos + 1.0) * 0.5; rollVel = sv * 0.8f
                }
            }
            val cv = DrumPattern.velocity(pat.clap[ps]) * sMul * (0.4f + 0.6f * lPerc)
            if (cv > 0.02f) kit.clap.trigger(cv)
            val xv = DrumPattern.velocity(pat.crash[ps]) * sMul
            if (xv > 0.02f) kit.crash.trigger(xv)
        }
        if (sp.slideWhistle > 0f) {
            // Every fill gets a slide whistle up (by default from the key's tonic, two octaves) as heat allows.
            if (fill && !lastFill && lPerc > 0.05f && (bar / 8) % sp.whistleEvery == sp.whistleEvery - 1) {
                val from = Dsp.midiToHz(nearest(key, sp.whistleFrom).toFloat())
                whistle.trigger(from, from * sp.whistleRange, (16 - s) * stepSamples / sr * 0.85f, lPerc)
            }
            lastFill = fill
        }
        val ov = DrumPattern.velocity(pat.open[ps])
        if (gap) {
            // (the held breath: no hats either)
        } else if (ov > 0f) {
            kit.hat.trigger(ov * lHat * rng.vary(0.1f), true)
        } else {
            val hc = (if (bar % 2 == 1) pat.hat2 else pat.hat)[ps]
            val hv = DrumPattern.velocity(hc)
            // 16th-note triplets: 'y' strikes thirds 0 and 2 of the step, 'z' just third 1.
            val mask = when (hc) {
                'y' -> 0b101
                'z' -> 0b010
                else -> -1
            }
            if (hv > 0f && mask and 1 != 0) kit.hat.trigger(hv * lHat * rng.vary(0.12f), false)
            val strokes = when (hc) {
                'r' -> 2
                't', 'y', 'z' -> 3
                'q' -> 4
                'w' -> 6
                else -> 0
            }
            if (strokes > 0) {
                // The rest of the roll, evenly through what's left of the step.
                hatRollN = strokes; hatRollK = 1; hatRollVel = hv * lHat * 0.85f; hatRollMask = mask
                if (stepPos > 0.0) {
                    while (hatRollK < hatRollN && hatRollK.toDouble() / hatRollN <= stepPos) hatRollK++
                    if (hatRollK >= hatRollN) hatRollN = 0
                }
            }
        }
        val jc = pat.jingle[ps]
        if (jc != '.') {
            val jv = DrumPattern.velocity(jc) * lHat
            if (jv > 0.02f) kit.jingle.trigger(jv)
        }
        val tc = pat.tom[ps]
        val tomMul = if (transitionFill) max(lSnare, fillMin) else lSnare
        if (tc != '.' && tomMul > 0.05f) kit.tom(DrumPattern.velocity(tc) * tomMul, if (tc in '1'..'3') tc - '1' else 1)
        val pv = DrumPattern.velocity(pat.perc[ps]) * lPerc
        if (pv > 0.02f && !brk && !gap) kit.perc(pv * rng.vary(0.1f))
        if (s == 0 && ((phraseBar == 0 && bar > 0 && lKick > 0.5f && !brk && dropped) || impactPending)) {
            kit.crash.trigger(if (impactPending) 1f else 0.75f)
            crowdSwell = 1f
        }
        // The crowd leans in through the phrase's last bar.
        if (phraseBar == 7 && sp.crowd > 0f) crowdSwell = max(crowdSwell, s / 16f * 0.8f)
        if (impactPending) {
            kit.kick.trigger(1f); duckEnv = 1f; impactPending = false
        }
        var stab = false
        if (hitPending && s % 4 == 0 && !gap) {
            // The band has seen you too: crash, kick, snare and clap together, the whole band
            // stabbing the chord (brass, piano, horns: whoever plays it), over a sub boom on its root.
            kit.crash.trigger(1f); kit.kick.trigger(1f); kit.snare.trigger(0.8f); kit.clap.trigger(0.7f); crowdSwell = 1f
            dropFx.boom(Dsp.midiToHz((nearest(key + chord.root, sp.bassCenter) + 12).toFloat()), 0.25f)
            hitPending = false
            stab = true
        }

        // ---- Bass
        val brow = plan?.bass ?: if (isB) sp.bassB else sp.bassA
        val bc = brow[s]
        // Before the drop the bass only teases: its line, soft (the downbeat a little firmer).
        if (bc != '.' && bc != '~' && !gap) {
            val root = nearest(key + chord.root, sp.bassCenter)
            val iv = chord.intervals
            val note = when (bc) {
                'O', 'o' -> root + 12
                'F' -> root + iv[min(2, iv.size - 1)]
                'T' -> root + iv[1]
                'D', 'd' -> root + iv[min(2, iv.size - 1)] - 12
                'S', 's' -> root + Scales.note(comp.scale, chord.degree + 1) - Scales.note(comp.scale, chord.degree)
                '7' -> root + if (iv.size > 3) iv[3] else Scales.note(comp.scale, chord.degree + 6) - Scales.note(comp.scale, chord.degree)
                'A', 'a' -> nearest(key + comp.chordAt(bar + 1).root, sp.bassCenter) - 1
                else -> root
            }
            val vel = (if (bc.isLowerCase()) 0.62f else 1f) * (if (brk) 0.8f else 1f) * (if (dropped) 1f else if (s == 0) min(1f, sp.dropTease * 1.3f) else sp.dropTease)
            val len = 1 + ties(brow, s)
            // A slide holds the note into the next one, so the mono voice glides there.
            val slide = sp.bassSlide && s + len < 16 && brow[s + len] != '.'
            bass.noteOn(note, vel, (len * stepSamples * (if (slide) 1.1f else 0.92f) - late * stepSamples).toInt())
        }

        // ---- Pad
        val chordChange = s == 0 && bar % sp.barsPerChord == 0
        val prow = if (isB) sp.padRhythmB else sp.padRhythm
        val sustain = if (isB) sp.padSustainB else sp.padSustain
        val strike = stab || if (sustain) chordChange else prow[s] == 'x'
        if (strike && !gap) {
            val len = (padLength(sp, bar, s, prow, sustain) * stepSamples).toInt()
            val v = if (stab) 1f else 0.8f
            pad.releaseAll()
            if (sp.padPower) {
                // Power chord: root, fifth and octave, stacked up from the root.
                val root = nearest(key + chord.root, sp.padCenter - 5)
                val fifth = chord.intervals[min(2, chord.size - 1)]
                pad.noteOn(root, v, len); pad.noteOn(root + fifth, v, len); pad.noteOn(root + 12, v * 0.9f, len)
            } else {
                for (k in 0 until chord.size) {
                    val note = nearest(key + chord.root + chord.intervals[k], sp.padCenter)
                    pad.noteOn(note, v, len)
                }
            }
        }

        // ---- Arp
        if (lArp > 0.02f) {
            val arow = plan?.arp ?: if (isB) sp.arpB else sp.arpA
            val ac = arow[s]
            if (ac in '0'..'9') {
                val root = nearest(key + chord.root, sp.arpCenter)
                val note = root + chord.tone(ac - '0') - chord.root
                val len = 1 + ties(arow, s)
                val accent = if (s % 4 == 0) 1f else 0.78f
                if (!gap) arp.noteOn(note, lArp * accent, max(1f, len * stepSamples * sp.arpGate - late * stepSamples).toInt())
            }
        }

        // ---- Lead
        if (lLead > 0.03f && sec != Section.BREAK) {
            val note = comp.leadAt(bar, s)
            if (note >= 0) {
                val legato = if (sp.lead.glide > 0f) 1.04f else 0.85f
                val accent = if (s % 4 == 0) 1f else 0.85f
                if (!gap) lead.noteOn(note, lLead * accent, (comp.leadLen * stepSamples * legato - late * stepSamples).toInt())
                if (!gap && plan != null && plan.harmony) {
                    val h = comp.harmonyFor(note, chord)
                    if (h >= 0) harm.noteOn(h, lLead * accent, (comp.leadLen * stepSamples * legato - late * stepSamples).toInt())
                }
            }
        }

        if (sp.rotor > 0f) rotor.trigger(if (s % 4 == 0) 1f else if (s % 2 == 0) 0.7f else 0.5f)
        if (sp.glitch) glitchStep(bar, s)
    }

    private fun glitchPhrase(bar: Int) {
        if (bar > 0 && bar % 16 == 0) {
            bpm = VOID_BPMS[rng.nextInt(VOID_BPMS.size)]; bpmTarget = bpm
        }
        crushOn = bar > 0 && rng.chance(0.3f)
    }

    private fun glitchStep(bar: Int, s: Int) {
        if (rng.chance(0.07f)) stutterPending = true
        if (s == 0) warpTarget = 1f
        if (bar % 4 == 3 && s == 12 && rng.chance(0.4f)) warpTarget = 0.5f
    }

    private fun synth(n: Int, dL: FloatArray, dR: FloatArray, dRev: FloatArray, dDly: FloatArray, off: Int) {
        val sp = spec ?: return
        val pm = rate * warp
        pad.render(padL, padR, n, pm, cutMul(sp.pad))
        bass.render(bassL, bassR, n, pm, cutMul(sp.bass))
        arp.render(arpL, arpR, n, pm, cutMul(sp.arp))
        lead.render(leadL, leadR, n, pm, cutMul(sp.lead))
        val harmOn = harm.active
        if (harmOn) {
            harm.render(harmL, harmR, n, pm, cutMul(sp.lead))
            val hg = sp.mix.harmony
            for (i in 0 until n) leadL[i] += harmL[i] * hg
        }
        kit.render(drL, drR, drRev, n, pm)
        ambL.fill(0f, 0, n); ambR.fill(0f, 0, n)
        wind.render(ambL, ambR, n, sp.wind)
        rotor.render(ambL, ambR, n, sp.rotor, 0.6f * Dsp.sin01((time * 0.05).toFloat()))
        jungle.render(ambL, ambR, n, sp.jungle)
        vinyl.render(ambL, ambR, n, sp.vinyl)
        whistle.render(ambL, ambR, n, sp.slideWhistle, pm)
        dropFx.render(ambL, ambR, n)
        if (sp.crowd > 0f) {
            val i = if (sp.fixedIntensity >= 0f) sp.fixedIntensity else intensity
            crowdSwell *= crowdDecay.pow(n)
            crowd.render(ambL, ambR, n, sp.crowd * (0.15f + 0.3f * i + crowdSwell) * fadeTo)
        }

        val m = sp.mix
        val gPad = m.pad
        val gBass = m.bass
        val gArp = m.arp
        val gLead = m.lead
        val gDr = m.drums
        val arpGL = gArp * (1f - max(0f, m.arpPan))
        val arpGR = gArp * (1f + min(0f, m.arpPan))
        for (i in 0 until n) {
            duckSm += (duckEnv - duckSm) * duckSmooth
            duckEnv *= duckCoef
            if (rampLeft > 0) {
                val c = rampCos * rampRotC - rampSin * rampRotS
                rampSin = rampSin * rampRotC + rampCos * rampRotS
                rampCos = c
                fade = if (fadeTo > fadeFrom) fadeFrom + (fadeTo - fadeFrom) * rampSin.toFloat() else fadeTo + (fadeFrom - fadeTo) * rampCos.toFloat()
                if (--rampLeft == 0) fade = fadeTo
            }
            val pd = gPad * (1f - m.padDuck * duckSm)
            val bd = gBass * (1f - m.bassDuck * duckSm)
            val ad = 1f - m.arpDuck * duckSm
            val b = bassL[i] * bd
            val a = arpL[i] * ad
            val ld = leadL[i] * gLead
            var l = padL[i] * pd + b + a * arpGL + ld + drL[i] * gDr + ambL[i]
            var r = padR[i] * pd + b + a * arpGR + ld + drR[i] * gDr + ambR[i]
            if (crushOn) {
                if (crushCount-- <= 0) {
                    crushCount = 3; heldL = l; heldR = r
                }
                l = heldL; r = heldR
            }
            levelGain += (levelTarget - levelGain) * levelSmooth
            val f = fade * sp.gain * levelGain
            dL[off + i] += l * f
            dR[off + i] += r * f
            dRev[off + i] += ((padL[i] + padR[i]) * pd * m.padVerb + a * gArp * m.arpVerb + ld * m.leadVerb +
                drRev[i] * gDr + (ambL[i] + ambR[i]) * 0.15f) * f
            dDly[off + i] += (a * gArp * m.arpDelay + ld * m.leadDelay) * f
        }
    }

    private val crowdDecay = Dsp.decay60(3.5f, sr)
    private val levelSmooth = Dsp.onePole(0.08f, sr)

    companion object {
        private val VOID_BPMS = floatArrayOf(96f, 110f, 124f, 132f, 140f, 150f, 170f)

        /** The shortest fade in (seconds). */
        const val MIN_FADE = 0.003f

        /** Seconds (time constant) a locked tempo takes to glide back to the song's own. */
        private const val BPM_GLIDE = 3f
    }
}

/** How the music moves from the song playing to the next one. */
internal enum class Transition {
    /**
     * A new zone (or any new song mid-run): on the next bar line at least half a bar away,
     * announced by a drum fill and a riser; the old song lets go and the new one lands with a
     * crash, its tempo locked to the old grid and gliding home.
     */
    BAR,

    /**
     * The other mode, turning up (SILENT's sneak mix to GUNS HOT): on the next beat, after a
     * two-step pickup, landing with a crash; the new song comes in at the same place in its
     * phrase on the closest chord, its tempo locked to the old grid.
     */
    FLIP_UP,

    /** The other mode, turning down: on the next beat, the old chord ringing on under the new groove. */
    FLIP_DOWN,

    /** Menus (the picker's themes): on the next beat, from the top of the new song. */
    BEAT,

    /** Right away, fading in (after a stop: the game-over loop). */
    NOW,

    /**
     * Out of the game-over dirge (RETRY, NEW RUN, TITLE): right away, never waiting for its slow
     * bar, the dirge and the new song crossing over along the same short equal-power curve.
     */
    EXIT,
}

/**
 * Owns the [MusicPlayer]s and moves between songs musically ([Transition]): every switch lands
 * on the old song's grid (a beat or a bar line), the new song starts on its downbeat at a
 * tempo locked to the old one's when they're near a simple ratio (then glides to its own),
 * and the two cross over along equal-power curves, so there's no hole and no pile-up.
 */
internal class MusicDirector(private val sr: Int) {
    // Three, so a quick flip back never has to cut off a song that's still fading out.
    private val players = Array(PLAYERS) { MusicPlayer(sr, it) }
    // Every arrangement's composer is built up front, so nothing allocates on the audio thread.
    private val composers = IdentityHashMap<SongSpec, Composer>().apply { (Songs.all + HeroSongs.all).forEach { put(it, Composer(it)) } }
    private var active = 0
    private val riser = Riser(sr)
    private var riserStart = 0.0
    private var how = Transition.BAR

    // A start waiting for its player to choke off what it was still fading out (a few ms).
    private var deferredLeft = 0
    private var dSpec: SongSpec? = null
    private var dImpact = false
    private var dFade = 0f
    private var dBar = 0
    private var dBpm = 0f

    var current: SongSpec? = null; private set
    var pending: SongSpec? = null; private set
    var rate = 1f
    var intensity = 0.5f

    val bpm: Float get() = players[active].bpm
    val delayBeats: Float get() = current?.delayBeats ?: 0.75f

    /** Frames rendered so far (diagnostics). */
    internal var frames = 0L; private set

    /** What a switch looked like (diagnostics; tests only, so the allocation is fine). */
    internal class SwitchInfo(
        val frame: Long, val from: String?, val fromBpm: Float, val fromPos: Double, val fromChord: Int,
        val to: String, val toBpm: Float, val toPos: Double,
        /** The old song's notes ring on under the new one. */
        val held: Boolean = false,
    )
    internal var onSwitch: ((SwitchInfo) -> Unit)? = null
    internal val activePlayer: MusicPlayer get() = players[active]
    /** Switches that had to choke off a still-fading song (diagnostics). */
    internal var steals = 0; private set

    /** Fade the music out entirely. */
    fun stop(fadeSeconds: Float) {
        players[active].stop(fadeSeconds)
        current = null
        pending = null
        deferredLeft = 0; dSpec = null
        riser.progress = -1f
    }

    private fun composerFor(s: SongSpec): Composer = composers.getOrPut(s) { Composer(s) }

    /** Queue [spec]; [immediate] cuts over now (fading in) instead of on the bar. */
    fun request(spec: SongSpec, immediate: Boolean) = request(spec, if (immediate) Transition.NOW else Transition.BAR)

    /** Move to [spec] by [t]; [fadeIn] is how long a [Transition.NOW] takes to fade in. */
    fun request(spec: SongSpec, t: Transition, fadeIn: Float = 0.8f) {
        if (dSpec != null) {
            // Mid-choke (a few ms): whatever was asked last starts when it's done.
            dSpec = spec; dImpact = t == Transition.BAR || t == Transition.FLIP_UP; current = spec
            return
        }
        val a = players[active]
        if (t == Transition.EXIT) {
            switchTo(spec, t, EXIT_FADE)
            return
        }
        if (t == Transition.NOW || current == null || !a.sequencing) {
            switchTo(spec, t, if (t == Transition.NOW) fadeIn else 0.05f)
            return
        }
        if (spec === current) {
            // Changed our mind: cancel the pending transition.
            if (pending != null) {
                pending = null; a.endStep = Long.MAX_VALUE; a.fillFrom = Long.MAX_VALUE; riser.progress = -1f
            }
            return
        }
        if (spec === pending && t == how) return
        val target = if (t == Transition.BAR) {
            if (pending != null && how == Transition.BAR) a.endStep else barTarget(a)
        } else {
            // The next beat still to be played (never one that has just sounded). Turning up
            // into a song at twice the tempo, its beat is the old one's eighth: the next of those.
            val q = if (t == Transition.FLIP_UP && abs(lockedBpm(spec, a.bpm) / a.bpm - 2f) < 0.01f) 2 else 4
            (a.absStep + q) / q * q
        }
        if (pending == null) riserStart = a.position
        pending = spec; how = t
        a.endStep = target
        a.fillFrom = when (t) {
            Transition.BAR -> target - 8
            Transition.FLIP_UP -> target - 2
            else -> Long.MAX_VALUE
        }
        if (t != Transition.BAR && t != Transition.FLIP_UP) riser.progress = -1f
    }

    private fun barTarget(a: MusicPlayer): Long {
        val nextBar = (a.absStep / 16 + 1) * 16
        return if (nextBar - a.position < 8.0) nextBar + 16 else nextBar
    }

    /**
     * Accent the next beat of what's playing (spotted in GUNS HOT). A switch already pending
     * takes the accent only if it lands by then (it lands with its own crash); one further off
     * (a zone change waiting for its bar line) doesn't swallow it.
     */
    fun hit() {
        val a = players[active]
        if (pending == null || a.endStep - a.position > HIT_WINDOW) a.hit()
    }

    private fun switchTo(spec: SongSpec, t: Transition, fadeIn: Float) {
        val old = players[active]
        val playing = old.sequencing
        val oldBpm = old.bpm
        val oldPos = old.position
        val oldChord = old.chordMask()
        val flip = t == Transition.FLIP_UP || t == Transition.FLIP_DOWN
        val bar = if (playing && flip) alignedBar(spec, old, oldChord) else 0
        val startBpm = if (playing && t != Transition.NOW && t != Transition.EXIT) lockedBpm(spec, oldBpm) else spec.bpm
        // Flips and menu cuts let the old chord ring on under the new song, when it doesn't rub.
        // (VOID reharmonises every phrase at random, so its plan can't say what the new chord
        // will be: a VOID song never lets the old chord ring.)
        val glitchy = spec.glitch || (playing && old.spec?.glitch == true)
        val hold = (flip || t == Transition.BEAT) && !glitchy && clashes(oldChord, chordMask(spec, bar)) == 0
        // (A menu cut between songs that rub gets out of the way quicker.)
        val out = when {
            t == Transition.BEAT && !hold -> 0.3f
            // Cut over now: the old song fades out along the curve the new one fades in on.
            t == Transition.NOW || t == Transition.EXIT -> max(fadeIn, MusicPlayer.MIN_FADE)
            else -> fadeOut(t, startBpm)
        }
        // (A cut-over crossfade holds the old notes under its fade: let go, they'd die on their
        // own release, short of the curve, and leave a hole.)
        val cut = t == Transition.NOW || t == Transition.EXIT
        if (playing) old.stop(out, release = !hold && !cut)
        // A player that's done; failing that, the quietest, choked off first.
        var next = -1
        for (i in players.indices) if (i != active && !players[i].audible) {
            next = i; break
        }
        val choke = next < 0
        if (choke) {
            for (i in players.indices) if (i != active && (next < 0 || players[i].level < players[next].level)) next = i
        }
        active = next
        current = spec
        pending = null
        riser.progress = -1f
        dSpec = spec; dImpact = t == Transition.BAR || t == Transition.FLIP_UP; dFade = fadeIn; dBar = bar; dBpm = startBpm
        if (choke) {
            steals++
            players[active].stop(CHOKE)
            deferredLeft = max(1, (CHOKE * sr).toInt())
        } else {
            startDeferred()
        }
        val cb = onSwitch
        if (cb != null) {
            cb(SwitchInfo(frames, old.spec?.name.takeIf { playing }, oldBpm, oldPos, oldChord, spec.name, startBpm, bar * 16.0, playing && hold))
        }
    }

    private fun startDeferred() {
        val s = dSpec ?: return
        dSpec = null
        deferredLeft = 0
        val p = players[active]
        p.intensity = intensity
        p.start(s, composerFor(s), dImpact, dFade, dBar, dBpm)
    }

    /** How long the old song takes to fade under the new one. */
    private fun fadeOut(t: Transition, nextBpm: Float): Float = when (t) {
        // Under the landing crash.
        Transition.BAR -> 1.6f
        Transition.FLIP_UP -> 0.5f
        // The old chord rings on for a beat of the new groove (the sneak mixes start sparse).
        Transition.FLIP_DOWN -> max(0.8f, 60f / nextBpm * 1.75f)
        Transition.BEAT -> 0.45f
        Transition.NOW, Transition.EXIT -> EXIT_FADE
    }

    /**
     * Where [spec] comes in when it takes over from [old] (the other mode of the same track):
     * the same phrase of its plan (the next one, if that's a break), on the bar whose chord
     * clashes least with the one ringing out (the same chord, when it has it), nearest the
     * same bar of the phrase.
     */
    private fun alignedBar(spec: SongSpec, old: MusicPlayer, oldChord: Int): Int {
        val oldBar = max(0L, old.absStep / 16).toInt()
        var phrase = oldBar / 8
        if (spec.glitch || oldChord == 0) return phrase * 8
        val plan = spec.phrases
        val size = plan?.size ?: spec.sections.size
        fun sectionOf(p: Int) = plan?.get(p % size)?.section ?: spec.sections[p % size]
        for (k in 0 until size) {
            if (sectionOf(phrase + k) != Section.BREAK) {
                phrase += k; break
            }
        }
        val here = oldBar % 8
        var best = 0
        var bestScore = Float.MAX_VALUE
        var k = 0
        while (k < 8) {
            val m = chordMask(spec, phrase * 8 + k)
            val score = clashes(oldChord, m) * 4f - Integer.bitCount(oldChord and m) + abs(k - here) * 0.3f
            if (score < bestScore) {
                bestScore = score; best = k
            }
            k += spec.barsPerChord
        }
        return phrase * 8 + best
    }

    /** Pitch classes of [spec]'s chord at [bar] (as its plan has it; VOID's random ones aside). */
    private fun chordMask(spec: SongSpec, bar: Int): Int {
        val plan = spec.phrases
        val p = bar / 8
        val sec = plan?.get(p % plan.size)?.section ?: spec.sections[p % spec.sections.size]
        val prog = if (sec == Section.B || sec == Section.B2) spec.progB else spec.progA
        val ch = prog[((bar % 8) / spec.barsPerChord) % prog.size]
        var m = 0
        for (iv in ch.intervals) m = m or (1 shl Math.floorMod(spec.tonic + ch.root + iv, 12))
        return m
    }

    /** [spec]'s tempo, or the one at a simple ratio to [oldBpm] within its [SongSpec.tempoLock]. */
    private fun lockedBpm(spec: SongSpec, oldBpm: Float): Float {
        if (spec.tempoLock <= 0f || spec.glitch) return spec.bpm
        var best = spec.bpm
        var err = Float.MAX_VALUE
        for (r in LOCK_RATIOS) {
            val b = oldBpm * r
            val e = abs(spec.bpm / b - 1f)
            if (e < err) {
                err = e; best = b
            }
        }
        return if (err <= spec.tempoLock) best else spec.bpm
    }

    /** Overwrite the buses with [n] samples of music. */
    fun render(n: Int, l: FloatArray, r: FloatArray, rev: FloatArray, dly: FloatArray) {
        l.fill(0f, 0, n); r.fill(0f, 0, n); rev.fill(0f, 0, n); dly.fill(0f, 0, n)
        for (p in players) {
            p.rate = rate; p.intensity = intensity
        }
        var done = 0
        while (done < n) {
            val a = players[active]
            var chunk = n - done
            val next = pending
            if (deferredLeft > 0) {
                chunk = min(chunk, deferredLeft)
            } else if (next != null) {
                val until = a.samplesUntil(a.endStep)
                if (until <= 0) {
                    switchTo(next, how, 0f)
                    continue
                }
                chunk = min(chunk, until)
                val span = a.endStep - riserStart
                if (how == Transition.BAR || how == Transition.FLIP_UP) {
                    riser.progress = if (span > 0) ((a.position - riserStart) / span).toFloat().coerceIn(0f, 1f) else 1f
                }
            }
            for (p in players) p.render(chunk, l, r, rev, dly, done)
            riser.render(l, r, done, chunk, riserGain)
            done += chunk
            frames += chunk
            if (deferredLeft > 0) {
                deferredLeft -= chunk
                if (deferredLeft <= 0) startDeferred()
            }
        }
    }

    fun sanitize() = players.forEach { it.sanitize() }

    /** The riser swells as big as the song is hot (a sneak mix's is a whisper). */
    private val riserGain: Float
        get() {
            val c = current
            val heat = if (c != null && c.fixedIntensity >= 0f) c.fixedIntensity else intensity
            return 0.22f * (0.35f + 0.65f * heat.coerceIn(0f, 1f))
        }

    companion object {
        private const val PLAYERS = 3
        /** Steps: a pending switch further off than this doesn't take a hit's accent (a beat). */
        private const val HIT_WINDOW = 4.0
        /** Seconds the game-over dirge and the song after it take to cross over. */
        const val EXIT_FADE = 0.4f
        /** Seconds to choke off a still-fading song when every player is busy. */
        private const val CHOKE = 0.006f
        /** Tempo ratios a new song may lock to (a metric modulation the ear follows). */
        private val LOCK_RATIOS = floatArrayOf(0.5f, 2f / 3f, 0.75f, 0.8f, 1f, 1.25f, 4f / 3f, 1.5f, 2f)

        /** Semitone rubs between two chords' pitch classes (bit masks), notes they share aside. */
        fun clashes(a: Int, b: Int): Int {
            var n = 0
            for (i in 0 until 12) {
                if ((a shr i) and 1 == 0 || (b shr i) and 1 != 0) continue
                val up = (i + 1) % 12
                val down = (i + 11) % 12
                if ((b shr up) and 1 != 0 && (a shr up) and 1 == 0) n++
                if ((b shr down) and 1 != 0 && (a shr down) and 1 == 0) n++
            }
            return n
        }
    }
}
