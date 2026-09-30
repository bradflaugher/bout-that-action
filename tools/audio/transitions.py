#!/usr/bin/env python3
"""Measure the music's transitions, as rendered by TransitionWavExportTest.

    ATA_TRANS_WAV=/tmp/trans ./gradlew :app:testDebugUnitTest --tests '*TransitionWavExportTest*' --rerun
    python3 tools/audio/transitions.py /tmp/trans [--png]

For every transition (a command in a scenario's timeline, and the switch the director made
for it) it prints:

  lat    seconds from the command to the switch
  click  the spikiest sample (its second difference over the 4 ms around it; a pure click
         scores ~20) from the command to 1.2 s after the switch, minus the spikiest in the
         steady music 1-5 s in: > 0 means sharper than anything the music does by itself
  dip    how far the momentary loudness (K-weighted, 400 ms) falls below the quieter side (before/after), in dB,
         minus the music's own natural dip over the same span
  bump   how far it rises above the louder side, likewise
  step   the level change after vs before (a mode's own loudness, not a fault by itself)
  beat   ms from the switch to the nearest beat of the old grid, and bar-phase (in steps)
  down   whether the new track starts on its downbeat
  tempo  the new/old tempo ratio and its distance to the nearest simple ratio
  clash  semitone clashes between the old chord and the new one
  settle seconds until the loudness stays within 1.5 dB of where it ends up

--png writes <name>.png: the waveform, the loudness, the commands, switches and both beat grids.
"""
import json
import os
import sys
from fractions import Fraction

import numpy as np
from scipy.io import wavfile
from scipy.signal import fftconvolve

SIMPLE = [Fraction(1, 2), Fraction(2, 3), Fraction(3, 4), Fraction(4, 5), Fraction(1), Fraction(5, 4), Fraction(4, 3), Fraction(3, 2), Fraction(2)]


def load(path):
    sr, x = wavfile.read(path)
    x = x.astype(np.float64) / 32768.0
    return sr, x.mean(axis=1)


def k_weight(x, sr):
    """ITU-R BS.1770 K-weighting (48 kHz coefficients): a head shelf, then the RLB high-pass."""
    from scipy.signal import lfilter

    assert sr == 48000
    y = lfilter([1.53512485958697, -2.69169618940638, 1.19839281085285], [1, -1.69065929318241, 0.73248077421585], x)
    return lfilter([1.0, -2.0, 1.0], [1, -1.99004745483398, 0.99007225036621], y)


def loudness(x, sr, win=0.05, hop=0.01, smooth=0.4):
    """Momentary loudness (K-weighted, 400 ms) in LUFS-ish dB every 10 ms, for a mono mix."""
    x = k_weight(x, sr)
    w, h = int(win * sr), int(hop * sr)
    n = (len(x) - w) // h
    sq = np.concatenate([[0.0], np.cumsum(x * x)])
    idx = np.arange(n) * h
    r = np.sqrt((sq[idx + w] - sq[idx]) / w)
    k = max(1, int(smooth / hop))
    rs = np.sqrt(np.convolve(r * r, np.ones(k) / k, mode="same"))
    t = (idx + w / 2) / sr
    return t, 20 * np.log10(rs + 1e-6) - 0.691, 20 * np.log10(r + 1e-6)


def db_between(t, db, a, b):
    m = (t >= a) & (t < b)
    return db[m]


def nearest_ratio(r):
    best = min(SIMPLE, key=lambda f: abs(np.log(r / float(f))))
    return best, abs(r / float(best) - 1) * 100


def semitone_clashes(a, b):
    n = 0
    for i in range(12):
        if not (a >> i) & 1:
            continue
        for j in range(12):
            if (b >> j) & 1 and (i - j) % 12 in (1, 11) and not (b >> i) & 1 and not (a >> j) & 1:
                n += 1
    return n


PCS = "C C# D Eb E F F# G Ab A Bb B".split()


def chord_name(m):
    return "".join(PCS[i] + " " for i in range(12) if (m >> i) & 1).strip() or "-"


def chord_at(chords, frame):
    c = 0
    for f, m in chords:
        if f <= frame:
            c = m
        else:
            break
    return c


def analyse(base, png):
    sr, x = load(base + ".wav")
    meta = json.load(open(base + ".json"))
    t, db, _ = loudness(x, sr)
    d2 = np.diff(x, 2)
    k = int(0.004 * sr)
    loc = np.sqrt(fftconvolve(d2 * d2, np.ones(2 * k + 1) / (2 * k + 1), mode="same")) + 1e-7
    spk = np.abs(d2) / loc
    spk[loc < 1e-4] = 0
    spk_ref = spk[int(1 * sr):int(5 * sr)].max()
    events = meta["events"]
    switches = meta["switches"]
    rows = []
    for k, ev in enumerate(events):
        R = ev["frame"]
        nxt = events[k + 1]["frame"] if k + 1 < len(events) else len(x)
        sw = [s for s in switches if R <= s["frame"] < nxt]
        # For a command with no song change (pause, CAUTION, a flip cancelled by the next one) the command is the switch.
        s = sw[-1] if sw else None
        S = s["frame"] if s else R
        tR, tS, tN = R / sr, S / sr, nxt / sr
        prev = events[k - 1]["frame"] / sr if k > 0 else 0.0
        before = db_between(t, db, max(prev + 1.0, tR - 3.0), tR - 0.05)
        after = db_between(t, db, min(tS + 2.5, tN - 0.6), min(tS + 5.5, tN - 0.05))
        region = db_between(t, db, max(tR, tS - 0.5), min(tS + 2.0, tN))
        row = {"label": ev["label"], "lat": tS - tR, "n_sw": len(sw)}
        if len(before) and len(after) and len(region):
            lb, la = np.median(before), np.median(after)
            nat_dip = min(np.min(before) - lb, np.min(after) - la)
            nat_bump = max(np.max(before) - lb, np.max(after) - la)
            row["dip"] = min(0.0, (np.min(region) - min(lb, la)) - nat_dip)
            row["bump"] = max(0.0, (np.max(region) - max(lb, la)) - nat_bump)
            row["step"] = la - lb
            tail = db_between(t, db, tR, tN)
            tt = t[(t >= tR) & (t < tN)]
            out = np.where(np.abs(tail - la) > 1.5 + max(0.0, nat_bump))[0]
            row["settle"] = (tt[out[-1]] - tR) if len(out) and tt[out[-1]] < tS + 5.5 else 0.0
        # Clicks: the spikiest sample (second difference over its 4 ms neighbourhood) from the
        # command to 1.2 s after the switch, vs the spikiest in the steady music (1-5 s in).
        w = spk[max(0, R - int(0.05 * sr)):S + int(1.2 * sr)]
        row["click"] = (w.max() if len(w) else 0.0) - spk_ref
        if s and s["from"]:
            steps_per_s = s["fromBpm"] * 4 / 60
            pos = s["fromPos"]
            beat_ph = (pos / 4) % 1
            row["beat_ms"] = min(beat_ph, 1 - beat_ph) * 4 / steps_per_s * 1000
            row["bar_step"] = pos % 16
            row["down"] = abs(s["toPos"] % 16) < 1e-3 or abs(s["toPos"] % 16 - 16) < 1e-3
            ratio = s["toBpm"] / s["fromBpm"]
            fr, dev = nearest_ratio(ratio)
            row["tempo"] = f"{s['fromBpm']:.0f}->{s['toBpm']:.0f} ({ratio:.2f}~{fr} {dev:.0f}%)"
            row["tempo_dev"] = dev
            oc = s["fromChord"]
            nc = chord_at(meta["chords"], S + int(0.05 * sr))
            row["clash"] = semitone_clashes(oc, nc)
            row["chords"] = f"{chord_name(oc)} > {chord_name(nc)}"
        rows.append(row)
    if png:
        plot(base, x, sr, t, db, meta)
    return meta, rows


def plot(base, x, sr, t, db, meta):
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    fig, (a1, a2) = plt.subplots(2, 1, figsize=(16, 6), sharex=True, gridspec_kw={"height_ratios": [1, 1.2]})
    hop = max(1, len(x) // 4000)
    xs = x[: len(x) // hop * hop].reshape(-1, hop)
    tx = np.arange(xs.shape[0]) * hop / sr
    a1.fill_between(tx, xs.min(1), xs.max(1), color="#446", lw=0)
    a1.set_ylim(-1, 1)
    a2.plot(t, db, color="#c33", lw=1.2)
    a2.set_ylim(np.percentile(db, 2) - 12, np.max(db) + 3)
    a2.set_ylabel("LUFS (400 ms)")
    for ev in meta["events"]:
        for a in (a1, a2):
            a.axvline(ev["frame"] / sr, color="#e90", lw=1.5)
        a2.text(ev["frame"] / sr, a2.get_ylim()[1] - 2, ev["label"], color="#a60", fontsize=8)
    for s in meta["switches"]:
        ts = s["frame"] / sr
        for a in (a1, a2):
            a.axvline(ts, color="#2a2", lw=1.2, ls="--")
        a2.text(ts, a2.get_ylim()[0] + 1, s["to"], color="#282", fontsize=7, rotation=90)
    # Beat grids: every song's beats, from each switch on (the old one continued faintly for 1 s).
    sws = meta["switches"]
    for i, s in enumerate(sws):
        t0 = s["frame"] / sr
        t1 = sws[i + 1]["frame"] / sr if i + 1 < len(sws) else len(x) / sr
        beat = 60 / s["toBpm"]
        start = t0 - (s["toPos"] / 4) * beat
        b = start
        while b < t1:
            if b >= t0:
                bar = abs(round((b - start) / beat) % 4) == 0
                a1.plot([b, b], [0.8 if bar else 0.9, 1.0], color="#06c", lw=1.5 if bar else 0.7)
            b += beat
        if s["from"]:
            ob = 60 / s["fromBpm"]
            ostart = t0 - (s["fromPos"] / 4) * ob
            b = ostart + np.ceil((t0 - 1.0 - ostart) / ob) * ob
            while b < t0 + 1.0:
                a1.plot([b, b], [-1.0, -0.9], color="#c06", lw=0.8)
                b += ob
    a2.set_xlabel("s")
    fig.suptitle(os.path.basename(base))
    fig.tight_layout()
    fig.savefig(base + ".png", dpi=70)
    plt.close(fig)


def fmt(r):
    def g(k, f):
        return (f % r[k]) if k in r else "   -  "

    return (
        f"{r['label']:<13} lat={r['lat']:5.2f} click={g('click', '%+5.1f')} dip={g('dip', '%5.1f')} bump={g('bump', '%4.1f')} "
        f"step={g('step', '%+5.1f')} beat={g('beat_ms', '%4.0f')}ms bar={g('bar_step', '%5.2f')} "
        f"down={'Y' if r.get('down') else ('n' if 'down' in r else '-')} clash={g('clash', '%d')} "
        f"settle={g('settle', '%4.1f')} {r.get('tempo', '')} {r.get('chords', '')}"
    )


def main():
    d = sys.argv[1]
    png = "--png" in sys.argv
    only = [a for a in sys.argv[2:] if not a.startswith("--")]
    worst = {"click": [], "dip": [], "bump": [], "beat_ms": [], "tempo_dev": [], "clash": []}
    for f in sorted(os.listdir(d)):
        if not f.endswith(".json"):
            continue
        base = os.path.join(d, f[:-5])
        if only and not any(o in f for o in only):
            continue
        meta, rows = analyse(base, png)
        print(f"== {f[:-5]}")
        for r in rows:
            print("   " + fmt(r))
            for k in worst:
                if k in r:
                    worst[k].append((r[k], f[:-5] + ":" + r["label"]))
    print("\n== worst")
    for k, v in worst.items():
        if not v:
            continue
        v.sort(key=lambda p: p[0], reverse=(k != "dip"))
        print(f"  {k:<9}: " + ", ".join(f"{a:.1f} {b}" for a, b in v[:6]))


if __name__ == "__main__":
    main()
