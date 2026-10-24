"""Synthesizes the Angry Uncle sound effects that no CC0 pack covers. Usage: gen_sounds.py <out_dir>"""
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy import signal

SR = 44100
rng = np.random.default_rng(7)


def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def phase(freq):
    """Integrates an instantaneous frequency curve into a phase curve."""
    return 2 * np.pi * np.cumsum(freq) / SR


def env_adsr(n, attack=0.005, release=0.05):
    e = np.ones(n)
    a = int(attack * SR)
    r = int(release * SR)
    e[:a] = np.linspace(0, 1, a)
    e[-r:] *= np.linspace(1, 0, r)
    return e


def lowpass(x, cutoff, order=4):
    sos = signal.butter(order, cutoff, "low", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def bandpass(x, center, q):
    bw = center / q
    sos = signal.butter(2, [max(center - bw / 2, 20), center + bw / 2], "band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def normalize(x, peak=0.89):
    return x / np.abs(x).max() * peak


def save(out, name, x):
    sf.write(out / f"{name}.wav", normalize(x).astype(np.float32), SR, subtype="PCM_16")
    print(f"{name}.wav {len(x) / SR:.2f}s")


def boing(base, length=0.5):
    """A spring: pitch wobbling around a rising base, the wobble dying out."""
    t = t_axis(length)
    wobble = 0.45 * np.exp(-t * 7) * np.sin(2 * np.pi * 14 * t)
    freq = base * (1 + 0.6 * t / length) * (1 + wobble)
    p = phase(freq)
    tone = np.sin(p) + 0.3 * np.sin(2 * p) + 0.12 * np.sin(3 * p)
    return tone * np.exp(-t * 5) * env_adsr(len(t), 0.003, 0.08)


def sting(length=1.6):
    """A dramatic orchestra-ish hit: a dissonant low brass chord and a timpani boom."""
    t = t_axis(length)
    chord = np.zeros_like(t)
    for f in (65.4, 92.5, 130.8, 185.0, 196.0):  # C2, F#2, C3, F#3, G3: a tritone stack with a clash
        detune = 1 + rng.uniform(-0.004, 0.004)
        chord += signal.sawtooth(phase(np.full_like(t, f * detune)))
    # Brass "blat": bright on the attack, darkening as it decays.
    bright = lowpass(chord, 3200)
    dark = lowpass(chord, 600)
    fade = np.exp(-t * 4)
    brass = (bright * fade + dark * (1 - fade)) * np.exp(-t * 2.2)
    boom_freq = 55 + 45 * np.exp(-t * 18)
    boom = np.sin(phase(boom_freq)) * np.exp(-t * 3.5)
    hit_noise = lowpass(rng.standard_normal(len(t)), 1800) * np.exp(-t * 30) * 0.6
    return (0.55 * brass + 1.0 * boom + hit_noise) * env_adsr(len(t), 0.004, 0.2)


def rumble(length=1.5):
    """Low, shaking earth rumble that swells and fades, for the screen shake."""
    t = t_axis(length)
    brown = np.cumsum(rng.standard_normal(len(t)))
    brown = lowpass(brown - lowpass(brown, 20, 2), 140)
    sub = np.sin(phase(np.full_like(t, 42.0))) * 0.6
    tremolo = 0.65 + 0.35 * np.sin(2 * np.pi * 9 * t)
    shape = np.sin(np.pi * np.clip(t / length, 0, 1)) ** 0.6
    return (normalize(brown, 1) + sub) * tremolo * shape


def growl(length=1.15):
    """
    A cartoon angry "GRRRAAAH!": a buzzy voice source, a pitch contour that climbs into a shout and falls off,
    a fast amplitude flutter for the rolled "rrr", and formant filters sliding from "uh" to "aah".
    """
    t = t_axis(length)
    u = t / length
    f0 = 95 + 120 * np.clip((u - 0.15) / 0.35, 0, 1) - 50 * np.clip((u - 0.7) / 0.3, 0, 1)
    f0 *= 1 + 0.02 * rng.standard_normal(len(t)).cumsum() / np.sqrt(len(t))  # slow drift
    f0 *= 1 + 0.015 * np.sin(2 * np.pi * 6 * t)  # shaky vibrato
    src = signal.sawtooth(phase(f0)) + 0.5 * signal.sawtooth(phase(f0 / 2))  # subharmonic: the growl
    src += 0.25 * rng.standard_normal(len(t))  # breath and rasp

    # "rrr" flutter during the first third, fading into a rough but steady shout.
    flutter_depth = 0.85 * np.clip(1 - (u - 0.1) / 0.3, 0.15, 1)
    flutter = 1 - flutter_depth * (0.5 + 0.5 * np.sin(2 * np.pi * 27 * t))

    # Vowel formants (F1, F2, F3): "uh" (640, 1190, 2390) sliding to "aah" (850, 1220, 2810).
    mix = np.clip((u - 0.25) / 0.25, 0, 1)
    voice = np.zeros_like(t)
    block = 1024
    for start in range(0, len(t), block):
        m = mix[min(start + block // 2, len(t) - 1)]
        seg = slice(max(start - 256, 0), min(start + block, len(t)))
        for (a, b), gain in zip(((640, 850), (1190, 1220), (2390, 2810)), (1.0, 0.7, 0.35)):
            out = bandpass(src[seg], a + (b - a) * m, 6) * gain
            voice[start:seg.stop] += out[start - seg.start:]
    voice = lowpass(voice, 4500)

    loudness = np.clip(u / 0.08, 0, 1) * (0.55 + 0.45 * np.clip((u - 0.2) / 0.3, 0, 1)) * np.clip((1 - u) / 0.25, 0, 1)
    # A little saturation makes it sound strained rather than polite.
    return np.tanh(2.5 * normalize(voice * flutter * loudness, 1))


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    save(out, "sfx_boing_low", boing(220))
    save(out, "sfx_boing_high", boing(330, 0.42))
    save(out, "sfx_sting", sting())
    save(out, "sfx_rumble", rumble())
    save(out, "sfx_growl", growl())


if __name__ == "__main__":
    main()
