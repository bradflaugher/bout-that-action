#!/usr/bin/env python3
"""Measure every piece of music the game plays, as MusicRenderExportTest renders it.

    ATA_MUSIC_WAV=/tmp/music ./gradlew :app:testDebugUnitTest --tests '*MusicRenderExportTest*' --rerun
    uv run --with numpy --with scipy tools/audio/music_qa.py /tmp/music [--csv out.csv] [name-prefix ...]

For every scene in manifest.json (from its steady point on) it prints:

  LUFS    integrated loudness (ITU-R BS.1770-4: K-weighted, gated at -70 LUFS and -10 LU)
  dBTP    true peak (4x oversampled), and the sample peak's count of clipped samples (|x| >= 0.999)
  LRA     the short-term (3 s) loudness spread, 10th to 95th percentile (EBU R128 loudness range)
  hole    how far the quietest 400 ms window sits under the integrated loudness (a hole in the bed)
  DC      the larger channel's mean, in dBFS; denormal samples
  click   the spikiest sample against its 4 ms neighbourhood (second difference; a pure click
          scores ~20, drum hits score 8-14)
  cent    spectral centroid (Hz), and the energy share in sub (<60), bass (60-150), mud
          (150-500), mid (0.5-2k), presence (2-5k), harsh (5-10k) and air (>10k), in dB
  mono    loudness lost folding to mono (dB; > 1 dB means phasey width that a phone's single
          speaker throws away) and the L/R correlation
  phone   loudness lost through a phone speaker (a 4th-order high-pass at 180 Hz and a
          low-pass at 9 kHz): the less, the more the bass carries on its harmonics

then the spread of LUFS across scenes (the loudness band) and the outliers, and the transitions'
report (tools/audio/transitions.py) if the transition scenarios were rendered too.
"""
import json
import os
import sys

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, fftconvolve, lfilter, resample_poly, sosfilt

SR = 48000
BANDS = [("sub", 20, 60), ("bass", 60, 150), ("mud", 150, 500), ("mid", 500, 2000), ("pres", 2000, 5000), ("harsh", 5000, 10000), ("air", 10000, 20000)]


def load(path):
    sr, x = wavfile.read(path)
    assert sr == SR, path
    if x.dtype == np.int16:
        x = x.astype(np.float64) / 32768.0
    else:
        x = x.astype(np.float64)
    return x


def k_weight(x):
    """ITU-R BS.1770 K-weighting at 48 kHz: the head shelf, then the RLB high-pass. x: (n, ch)."""
    y = lfilter([1.53512485958697, -2.69169618940638, 1.19839281085285], [1, -1.69065929318241, 0.73248077421585], x, axis=0)
    return lfilter([1.0, -2.0, 1.0], [1, -1.99004745483398, 0.99007225036621], y, axis=0)


def block_power(x, win, hop):
    """Mean square per block (summed over channels, BS.1770's channel weights all 1)."""
    p = (x * x).sum(axis=1)
    c = np.concatenate([[0.0], np.cumsum(p)])
    n = max(0, (len(p) - win) // hop + 1)
    idx = np.arange(n) * hop
    return (c[idx + win] - c[idx]) / win


def lufs(x):
    """Integrated loudness, gated (BS.1770-4)."""
    z = block_power(k_weight(x), int(0.4 * SR), int(0.1 * SR))
    if len(z) == 0:
        return -99.0
    l = -0.691 + 10 * np.log10(z + 1e-20)
    z = z[l > -70]
    if len(z) == 0:
        return -99.0
    rel = -0.691 + 10 * np.log10(z.mean()) - 10
    z = z[-0.691 + 10 * np.log10(z) > rel]
    return -0.691 + 10 * np.log10(z.mean())


def short_term(x, win=3.0, hop=0.1):
    z = block_power(k_weight(x), int(win * SR), int(hop * SR))
    return -0.691 + 10 * np.log10(z + 1e-20)


def true_peak(x):
    up = resample_poly(x, 4, 1, axis=0)
    return 20 * np.log10(max(np.abs(up).max(), 1e-9))


def clicks(m):
    d2 = np.diff(m, 2)
    k = int(0.004 * SR)
    loc = np.sqrt(fftconvolve(d2 * d2, np.ones(2 * k + 1) / (2 * k + 1), mode="same").clip(0)) + 1e-7
    s = np.abs(d2) / loc
    s[loc < 3e-4] = 0
    return float(s.max()) if len(s) else 0.0


def spectrum(m):
    n = 8192
    hop = n // 2
    w = np.hanning(n)
    acc = np.zeros(n // 2 + 1)
    cnt = 0
    for i in range(0, len(m) - n, hop):
        acc += np.abs(np.fft.rfft(m[i:i + n] * w)) ** 2
        cnt += 1
    acc /= max(cnt, 1)
    f = np.fft.rfftfreq(n, 1 / SR)
    tot = acc[(f >= 20)].sum() + 1e-30
    cent = float((f * np.sqrt(acc)).sum() / (np.sqrt(acc).sum() + 1e-30))
    bands = {name: 10 * np.log10(acc[(f >= lo) & (f < hi)].sum() / tot + 1e-12) for name, lo, hi in BANDS}
    return cent, bands


_phone = None


def phone(x):
    global _phone
    if _phone is None:
        _phone = np.vstack([butter(4, 180, "highpass", fs=SR, output="sos"), butter(2, 9000, "lowpass", fs=SR, output="sos")])
    m = x.mean(axis=1, keepdims=True)
    return sosfilt(_phone, np.hstack([m, m]), axis=0)


def measure(x, settle):
    s = x[int(settle * SR):]
    out = {}
    out["lufs"] = lufs(s)
    out["tp"] = true_peak(s)
    out["clip"] = int((np.abs(s) >= 0.999).sum())
    st = short_term(s)
    st = st[st > -70]
    out["lra"] = float(np.percentile(st, 95) - np.percentile(st, 10)) if len(st) else 0.0
    mom = short_term(s, 0.4, 0.1)
    out["hole"] = float(mom.min() - out["lufs"]) if len(mom) else 0.0
    out["dc"] = 20 * np.log10(max(abs(s[:, 0].mean()), abs(s[:, 1].mean()), 1e-12))
    out["denorm"] = int(((np.abs(s) < 1.1754944e-38) & (s != 0)).sum())
    m = s.mean(axis=1)
    out["click"] = clicks(m)
    out["cent"], out["bands"] = spectrum(m)
    mono = np.hstack([m[:, None], m[:, None]])
    out["mono"] = out["lufs"] - lufs(mono)
    out["corr"] = float(np.corrcoef(s[:, 0], s[:, 1])[0, 1])
    out["phone"] = out["lufs"] - lufs(phone(s))
    return out


def fmt(name, r):
    b = r["bands"]
    return (
        f"{name:<28} {r['lufs']:6.1f} {r['tp']:6.1f} {r['clip']:4d} {r['lra']:4.1f} {r['hole']:6.1f} {r['dc']:6.0f} {r['denorm']:3d} {r['click']:5.1f} "
        f"{r['cent']:5.0f} " + " ".join(f"{b[k]:5.1f}" for k, _, _ in BANDS) + f" {r['mono']:5.2f} {r['corr']:5.2f} {r['phone']:5.1f}"
    )


HEADER = (
    f"{'scene':<28} {'LUFS':>6} {'dBTP':>6} {'clip':>4} {'LRA':>4} {'hole':>6} {'DC':>6} {'dn':>3} {'click':>5} {'cent':>5} "
    + " ".join(f"{k:>5}" for k, _, _ in BANDS) + f" {'mono':>5} {'corr':>5} {'phone':>5}"
)


def main():
    d = sys.argv[1]
    args = sys.argv[2:]
    csv = None
    if "--csv" in args:
        i = args.index("--csv")
        csv = args[i + 1]
        del args[i:i + 2]
    only = args
    manifest = json.load(open(os.path.join(d, "manifest.json")))
    rows = []
    print(HEADER)
    for e in manifest:
        if e["kind"] == "transition":
            continue
        if only and not any(e["name"].startswith(o) for o in only):
            continue
        r = measure(load(os.path.join(d, e["name"] + ".wav")), e["settle"])
        r["name"], r["kind"] = e["name"], e["kind"]
        rows.append(r)
        print(fmt(e["name"], r), flush=True)
    if not rows:
        return
    L = np.array([r["lufs"] for r in rows])
    target = float(np.median(L))
    print(f"\nloudness: median {target:.1f} LUFS, range {L.min():.1f}..{L.max():.1f} ({L.max() - L.min():.1f} LU), "
          f"within +-1.5 LU of the median: {int((np.abs(L - target) <= 1.5).sum())}/{len(L)}")
    for kind in sorted(set(r["kind"] for r in rows)):
        k = np.array([r["lufs"] for r in rows if r["kind"] == kind])
        print(f"  {kind:<9} n={len(k):3d} median {np.median(k):6.1f}  min {k.min():6.1f}  max {k.max():6.1f}")
    out = sorted(rows, key=lambda r: abs(r["lufs"] - target), reverse=True)[:12]
    print("  furthest from the median: " + ", ".join(f"{r['name']} {r['lufs'] - target:+.1f}" for r in out))
    print(f"true peak: max {max(r['tp'] for r in rows):.2f} dBTP; clipped samples: {sum(r['clip'] for r in rows)}; "
          f"denormals: {sum(r['denorm'] for r in rows)}; worst DC {max(r['dc'] for r in rows):.0f} dBFS")
    print(f"clicks: worst {max(r['click'] for r in rows):.1f} ({max(rows, key=lambda r: r['click'])['name']})")
    print(f"mono loss: worst {max(r['mono'] for r in rows):.2f} dB ({max(rows, key=lambda r: r['mono'])['name']}); "
          f"phone loss: {min(r['phone'] for r in rows):.1f}..{max(r['phone'] for r in rows):.1f} dB "
          f"(worst {max(rows, key=lambda r: r['phone'])['name']})")
    C = np.array([r["cent"] for r in rows])
    print(f"centroid: {C.min():.0f}..{C.max():.0f} Hz (median {np.median(C):.0f}); "
          f"mud share {min(r['bands']['mud'] for r in rows):.1f}..{max(r['bands']['mud'] for r in rows):.1f} dB, "
          f"harsh share {min(r['bands']['harsh'] for r in rows):.1f}..{max(r['bands']['harsh'] for r in rows):.1f} dB")
    if csv:
        with open(csv, "w") as f:
            f.write("name,kind,lufs,tp,clip,lra,hole,dc,click,cent," + ",".join(k for k, _, _ in BANDS) + ",mono,corr,phone\n")
            for r in rows:
                f.write(",".join([r["name"], r["kind"]] + [f"{r[k]:.3f}" for k in ("lufs", "tp", "clip", "lra", "hole", "dc", "click", "cent")]
                                 + [f"{r['bands'][k]:.2f}" for k, _, _ in BANDS] + [f"{r[k]:.3f}" for k in ("mono", "corr", "phone")]) + "\n")


if __name__ == "__main__":
    main()
