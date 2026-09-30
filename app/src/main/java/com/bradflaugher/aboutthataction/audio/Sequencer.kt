package com.bradflaugher.aboutthataction.audio

import java.util.IdentityHashMap
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
    var endStep = Long.MAX_VALUE
    var sequencing = false; private set
    private var swingPending = false
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

    private var fade = 0f
    private var fadeTarget = 0f
    private var fadeCoef = 1f
    val audible: Boolean get() = sequencing || fade > 1e-4f

    // Layer gains, refreshed every render call from intensity.
    private var lKick = 0f
    private var lSnare = 0f
    private var lHat = 0f
    private var lPerc = 0f
    private var lArp = 0f
    private var lLead = 0f
    private var bright = 1f

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

    fun start(s: SongSpec, comp: Composer, impact: Boolean, fadeInSeconds: Float) {
        spec = s
        composer = comp
        comp.reset()
        pad.kill(); bass.kill(); arp.kill(); lead.kill(); harm.kill()
        pad.patch = s.pad; bass.patch = s.bass; arp.patch = s.arp; lead.patch = s.lead; harm.patch = s.lead
        kit.setTuning(s.kit)
        absStep = -1L; stepPos = 1.0
        swingPending = false; stutterPending = false; rollPending = false; hatRollN = 0
        crowdSwell = 0f
        lastFill = false
        whistle.kill()
        endStep = Long.MAX_VALUE
        sequencing = true
        bpm = s.bpm
        warp = 1f; warpTarget = 1f; crushOn = false
        impactPending = impact
        duckEnv = 0f; duckSm = 0f
        dropOn = s.dropThreshold < 0f || heat(s) >= s.dropThreshold
        dropGap = false
        dropFx.kill()
        fadeTarget = 1f
        if (fadeInSeconds > 0f) {
            fade = 0f; fadeCoef = Dsp.onePole(fadeInSeconds / 4f, sr)
        } else {
            fade = 1f; fadeCoef = 1f
        }
    }

    /** Stop sequencing; ring out and fade over roughly [fadeSeconds]. */
    fun stop(fadeSeconds: Float) {
        if (!audible) return
        sequencing = false
        pad.releaseAll(); bass.releaseAll(); arp.releaseAll(); lead.releaseAll(); harm.releaseAll()
        fadeTarget = 0f
        fadeCoef = Dsp.onePole(fadeSeconds / 5f, sr)
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
        lLead = Dsp.smoothstep(s.leadThreshold - 0.1f, s.leadThreshold + 0.1f, i)
        bright = Dsp.smoothstep(-0.1f, 1f, i)
    }

    private fun cutMul(p: Patch): Float = 2f.pow(-2.3f * (1f - bright) * p.bright)

    /** Accumulate [n] samples into the destination buses starting at [off]. */
    fun render(n: Int, dL: FloatArray, dR: FloatArray, dRev: FloatArray, dDly: FloatArray, off: Int) {
        if (!audible) return
        updateLayers()
        warp += (warpTarget - warp) * (if (warpTarget < warp) 0.04f else 0.5f) * (n / 256f)
        var done = 0
        while (done < n) {
            if (sequencing) processEvents()
            var chunk = min(n - done, c)
            if (sequencing) chunk = min(chunk, samplesToNext())
            synth(chunk, dL, dR, dRev, dDly, off + done)
            if (sequencing) stepPos += chunk * inc()
            done += chunk
        }
        time += n.toDouble() / sr
        if (!sequencing && fade < 1e-4f) {
            fade = 0f
            pad.kill(); bass.kill(); arp.kill(); lead.kill(); harm.kill()
        }
    }

    private fun samplesToNext(): Int {
        var target = 1.0
        val s = spec
        if (swingPending && s != null && s.swing > stepPos) target = min(target, s.swing.toDouble())
        if (stutterPending && 0.5 > stepPos) target = min(target, 0.5)
        if (rollPending && rollAt > stepPos) target = min(target, rollAt)
        if (hatRollN > 0) target = min(target, hatRollK.toDouble() / hatRollN)
        val k = ceil((target - stepPos) / inc())
        return max(1.0, min(k, c.toDouble())).toInt()
    }

    private fun processEvents() {
        val s = spec ?: return
        while (true) {
            if (stepPos >= 1.0 - 1e-7) {
                stepPos = max(0.0, stepPos - 1.0)
                absStep++
                if (absStep >= endStep) {
                    sequencing = false; return
                }
                swingPending = false
                hatRollN = 0
                if (absStep % 2 == 1L && s.swing > 0f) swingPending = true else fireStep(s)
                continue
            }
            if (swingPending && stepPos >= s.swing - 1e-7) {
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
        val transitionFill = endStep != Long.MAX_VALUE && absStep >= endStep - 8
        val fill = transitionFill ||
            (phraseBar == 7 && s >= 12 && lSnare > 0.3f) ||
            (bar % 16 == 15 && s >= 8 && lSnare > 0.5f)
        val brk = sec == Section.BREAK && !fill
        val plan = comp.phrase
        val build = plan?.build?.takeIf { phraseBar >= 6 }
        val pat = if (fill) plan?.fill ?: sp.fill else build ?: plan?.drums ?: if (isB) sp.drumsB else sp.drumsA
        val kMul = if (transitionFill) max(lKick, 0.6f) else if (dropped) lKick else 0f
        val sMul = if (transitionFill) max(lSnare, 0.6f) else lSnare
        if (!brk && !gap) {
            val kv = DrumPattern.velocity(pat.kick[s]) * kMul
            if (kv > 0.02f) {
                kit.kick.trigger(kv * rng.vary(0.05f)); duckEnv = max(duckEnv, kv)
            }
            val sc = pat.snare[s]
            val sv = DrumPattern.velocity(sc) * sMul
            if (sv > 0.02f) {
                kit.snare.trigger(sv * rng.vary(0.06f))
                if (sc == 'r') {
                    // Roll: a second stroke halfway to the next step.
                    rollPending = true; rollAt = (stepPos + 1.0) * 0.5; rollVel = sv * 0.8f
                }
            }
            val cv = DrumPattern.velocity(pat.clap[s]) * sMul * (0.4f + 0.6f * lPerc)
            if (cv > 0.02f) kit.clap.trigger(cv)
            val xv = DrumPattern.velocity(pat.crash[s]) * sMul
            if (xv > 0.02f) kit.crash.trigger(xv)
        }
        if (sp.slideWhistle > 0f) {
            // Every fill gets a slide whistle up (by default from the key's tonic, two octaves) as heat allows.
            if (fill && !lastFill && lPerc > 0.05f) {
                val from = Dsp.midiToHz(nearest(key, sp.whistleFrom).toFloat())
                whistle.trigger(from, from * sp.whistleRange, (16 - s) * stepSamples / sr * 0.85f, lPerc)
            }
            lastFill = fill
        }
        val ov = DrumPattern.velocity(pat.open[s])
        if (gap) {
            // (the held breath: no hats either)
        } else if (ov > 0f) {
            kit.hat.trigger(ov * lHat * rng.vary(0.1f), true)
        } else {
            val hc = (if (bar % 2 == 1) pat.hat2 else pat.hat)[s]
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
        val jc = pat.jingle[s]
        if (jc != '.') {
            val jv = DrumPattern.velocity(jc) * lHat
            if (jv > 0.02f) kit.jingle.trigger(jv)
        }
        val tc = pat.tom[s]
        val tomMul = if (transitionFill) max(lSnare, 0.6f) else lSnare
        if (tc != '.' && tomMul > 0.05f) kit.tom(DrumPattern.velocity(tc) * tomMul, if (tc in '1'..'3') tc - '1' else 1)
        val pv = DrumPattern.velocity(pat.perc[s]) * lPerc
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

        // ---- Bass
        val brow = plan?.bass ?: if (isB) sp.bassB else sp.bassA
        val bc = brow[s]
        // Before the drop the bass only teases: a soft note on the downbeat.
        if (bc != '.' && bc != '~' && !gap && (dropped || s == 0)) {
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
            val vel = (if (bc.isLowerCase()) 0.62f else 1f) * (if (brk) 0.8f else 1f) * (if (dropped) 1f else 0.5f)
            val len = 1 + ties(brow, s)
            // A slide holds the note into the next one, so the mono voice glides there.
            val slide = sp.bassSlide && s + len < 16 && brow[s + len] != '.'
            bass.noteOn(note, vel, (len * stepSamples * (if (slide) 1.1f else 0.92f)).toInt())
        }

        // ---- Pad
        val chordChange = s == 0 && bar % sp.barsPerChord == 0
        val prow = if (isB) sp.padRhythmB else sp.padRhythm
        val sustain = if (isB) sp.padSustainB else sp.padSustain
        val strike = if (sustain) chordChange else prow[s] == 'x'
        if (strike && !gap) {
            val len = (padLength(sp, bar, s, prow, sustain) * stepSamples).toInt()
            pad.releaseAll()
            if (sp.padPower) {
                // Power chord: root, fifth and octave, stacked up from the root.
                val root = nearest(key + chord.root, sp.padCenter - 5)
                val fifth = chord.intervals[min(2, chord.size - 1)]
                pad.noteOn(root, 0.8f, len); pad.noteOn(root + fifth, 0.8f, len); pad.noteOn(root + 12, 0.7f, len)
            } else {
                for (k in 0 until chord.size) {
                    val note = nearest(key + chord.root + chord.intervals[k], sp.padCenter)
                    pad.noteOn(note, 0.8f, len)
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
                if (!gap) arp.noteOn(note, lArp * accent, max(1f, len * stepSamples * sp.arpGate).toInt())
            }
        }

        // ---- Lead
        if (lLead > 0.03f && sec != Section.BREAK) {
            val note = comp.leadAt(bar, s)
            if (note >= 0) {
                val legato = if (sp.lead.glide > 0f) 1.04f else 0.85f
                val accent = if (s % 4 == 0) 1f else 0.85f
                if (!gap) lead.noteOn(note, lLead * accent, (comp.leadLen * stepSamples * legato).toInt())
                if (!gap && plan != null && plan.harmony) {
                    val h = comp.harmonyFor(note, chord)
                    if (h >= 0) harm.noteOn(h, lLead * accent, (comp.leadLen * stepSamples * legato).toInt())
                }
            }
        }

        if (sp.rotor > 0f) rotor.trigger(if (s % 4 == 0) 1f else if (s % 2 == 0) 0.7f else 0.5f)
        if (sp.glitch) glitchStep(bar, s)
    }

    private fun glitchPhrase(bar: Int) {
        if (bar > 0 && bar % 16 == 0) bpm = VOID_BPMS[rng.nextInt(VOID_BPMS.size)]
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
            crowd.render(ambL, ambR, n, sp.crowd * (0.15f + 0.3f * i + crowdSwell) * fadeTarget)
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
            fade += (fadeTarget - fade) * fadeCoef
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
            val f = fade * sp.gain
            dL[off + i] += l * f
            dR[off + i] += r * f
            dRev[off + i] += ((padL[i] + padR[i]) * pd * m.padVerb + a * gArp * m.arpVerb + ld * m.leadVerb +
                drRev[i] * gDr + (ambL[i] + ambR[i]) * 0.15f) * f
            dDly[off + i] += (a * gArp * m.arpDelay + ld * m.leadDelay) * f
        }
    }

    private val crowdDecay = Dsp.decay60(3.5f, sr)

    companion object {
        private val VOID_BPMS = floatArrayOf(96f, 110f, 124f, 132f, 140f, 150f, 170f)
    }
}

/**
 * Owns two [MusicPlayer]s and moves between songs musically: a requested song starts on
 * the next bar line (at least half a bar away), announced by a drum fill and a riser;
 * the old song rings out while the new one lands with a crash.
 */
internal class MusicDirector(private val sr: Int) {
    private val players = arrayOf(MusicPlayer(sr, 0), MusicPlayer(sr, 1))
    // Every arrangement's composer is built up front, so nothing allocates on the audio thread.
    private val composers = IdentityHashMap<SongSpec, Composer>().apply { (Songs.all + HeroSongs.all).forEach { put(it, Composer(it)) } }
    private var active = 0
    private val riser = Riser(sr)
    private var riserStart = 0.0

    var current: SongSpec? = null; private set
    var pending: SongSpec? = null; private set
    var rate = 1f
    var intensity = 0.5f

    val bpm: Float get() = players[active].bpm
    val delayBeats: Float get() = current?.delayBeats ?: 0.75f

    /** Fade the music out entirely. */
    fun stop(fadeSeconds: Float) {
        players[active].endStep = Long.MAX_VALUE
        players[active].stop(fadeSeconds)
        current = null
        pending = null
        riser.progress = -1f
    }

    private fun composerFor(s: SongSpec): Composer = composers.getOrPut(s) { Composer(s) }

    /** Queue [spec]; [immediate] cuts over now (with a short fade) instead of on the bar. */
    fun request(spec: SongSpec, immediate: Boolean) {
        val a = players[active]
        if (immediate || current == null || !a.sequencing) {
            switchTo(spec, impact = false, oldFade = if (immediate) 1.2f else 2f, fadeIn = if (immediate) 0.8f else 0.05f)
            return
        }
        if (spec === pending) return
        if (spec === current) {
            // Changed our mind: cancel the pending transition.
            if (pending != null) {
                pending = null; a.endStep = Long.MAX_VALUE; riser.progress = -1f
            }
            return
        }
        pending = spec
        if (a.endStep == Long.MAX_VALUE) {
            val nextBar = (a.absStep / 16 + 1) * 16
            val remaining = nextBar - a.position
            a.endStep = if (remaining < 8.0) nextBar + 16 else nextBar
            riserStart = a.position
        }
    }

    private fun switchTo(spec: SongSpec, impact: Boolean, oldFade: Float, fadeIn: Float) {
        players[active].stop(oldFade)
        active = 1 - active
        players[active].intensity = intensity
        players[active].start(spec, composerFor(spec), impact, fadeIn)
        current = spec
        pending = null
        riser.progress = -1f
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
            if (next != null) {
                val until = a.samplesUntil(a.endStep)
                if (until <= 0) {
                    switchTo(next, impact = true, oldFade = 0.9f, fadeIn = 0f)
                    continue
                }
                chunk = min(chunk, until)
                val span = a.endStep - riserStart
                riser.progress = if (span > 0) ((a.position - riserStart) / span).toFloat().coerceIn(0f, 1f) else 1f
            }
            for (p in players) p.render(chunk, l, r, rev, dly, done)
            riser.render(l, r, done, chunk, 0.22f)
            done += chunk
        }
    }

    fun sanitize() = players.forEach { it.sanitize() }
}
