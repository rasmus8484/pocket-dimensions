"""Synthesises the "reality crack" sound for the Anchor Breaker: a distant thunderstrike.

Built like real thunder heard from a distance:
  clap    - a dense broadband burst with the harshest highs rolled off (less sharp than a close strike)
  punch   - a low boom with a fast attack and soft saturation, so it hits rather than hums
  rolling - dozens of delayed copies of the clap arriving at irregular times over a few seconds, each
            quieter and duller than the last (distance absorbs high frequencies), plus a low rumble
            that swells and fades with them. This is the "bounces off hills on its way to you" echo.
Output: mono 44.1 kHz WAV (Minecraft positional sounds must be mono).

Usage: python tools/sound/reality_crack.py <out_dir>    (writes crack_near/mid/far.wav)
"""
import sys, wave
from pathlib import Path
import numpy as np

SR = 44100


def env_exp(n, tau_s):
    return np.exp(-np.arange(n) / (tau_s * SR))


def lowpass(x, cutoff, order=2):
    """Zero-phase low-pass via FFT with a smooth Butterworth-like roll-off."""
    n = len(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    g = 1 / np.sqrt(1 + (f / cutoff) ** (2 * order))
    return np.fft.irfft(np.fft.rfft(x) * g, n)


def highpass(x, cutoff, order=2):
    n = len(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    g = 1 / np.sqrt(1 + (cutoff / np.maximum(f, 1e-6)) ** (2 * order))
    return np.fft.irfft(np.fft.rfft(x) * g, n)


def place(buf, sig, t):
    i = int(t * SR)
    if i >= len(buf):
        return
    end = min(len(buf), i + len(sig))
    buf[i:end] += sig[:end - i]


def make_thunder(seed, length=5.5, clap_cut=3200, echoes=46, roll_span=3.4, roll_dark=380, boom_gain=1.0, roll_gain=1.0):
    rng = np.random.default_rng(seed)
    n = int(length * SR)
    out = np.zeros(n)

    # Clap: 140 ms of dense noise, fast attack, crackly amplitude texture, highs softened
    cn = int(0.14 * SR)
    tt = np.arange(cn) / SR
    texture = 0.55 + 0.45 * (rng.random(cn) ** 3 > 0.6)          # grainy crackle inside the burst
    clap = rng.standard_normal(cn) * texture * np.minimum(1, tt / 0.002) * env_exp(cn, 0.045)
    clap = highpass(lowpass(clap, clap_cut), 120)
    clap /= np.max(np.abs(clap))
    place(out, clap * 0.85, 0.0)

    # Punch: low boom with a 5 ms attack and soft saturation
    bn = int(1.2 * SR)
    tt = np.arange(bn) / SR
    body = lowpass(rng.standard_normal(bn), 160, order=3)
    body /= np.max(np.abs(body))
    freq = 38 + 30 * np.exp(-tt / 0.12)
    tone = np.sin(2 * np.pi * np.cumsum(freq) / SR)
    boom = (0.75 * body + 0.6 * tone) * np.minimum(1, tt / 0.005) * env_exp(bn, 0.28)
    boom = np.tanh(boom * 2.2) / np.tanh(2.2)
    place(out, boom * 0.95 * boom_gain, 0.004)

    # Rolling: irregular echoes of the clap, later = quieter + darker; clusters make it "roll"
    times = np.sort(0.12 + roll_span * rng.random(echoes) ** 1.6)  # denser early
    for t in times:
        frac = (t - 0.12) / roll_span
        e = clap * rng.uniform(0.35, 1.0) * (1 - frac) ** 1.1 * 0.55
        e = lowpass(np.concatenate([e, np.zeros(int(0.05 * SR))]), clap_cut * (1 - frac) + roll_dark * frac)
        place(out, e * roll_gain, t)

    # Rumble bed under the rolls: low noise whose loudness follows slow random swells
    tt = np.arange(n) / SR
    swell = np.interp(tt, np.linspace(0, length, 14), rng.uniform(0.3, 1.0, 14))
    bed = lowpass(rng.standard_normal(n), 110, order=3)
    bed /= np.max(np.abs(bed))
    bed *= swell * np.minimum(1, tt / 0.15) * np.exp(-tt / (roll_span * 0.55))
    out += bed * 0.45 * roll_gain

    out = highpass(out, 25)
    fade = int(0.5 * SR)
    out[-fade:] *= np.linspace(1, 0, fade)
    out /= np.max(np.abs(out)) / 0.95
    return out


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype('<i2')
    with wave.open(str(path), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())


if __name__ == '__main__':
    out_dir = Path(sys.argv[1] if len(sys.argv) > 1 else '.')
    out_dir.mkdir(parents=True, exist_ok=True)
    variants = {
        'crack_near': dict(seed=5, clap_cut=4200, echoes=34, roll_span=2.4, boom_gain=1.15, roll_gain=1.6),   # punchiest, shorter roll
        'crack_mid':  dict(seed=8, roll_gain=2.2),                                                            # balanced
        'crack_far':  dict(seed=13, clap_cut=2400, echoes=60, roll_span=4.4, roll_dark=300, roll_gain=2.6),  # softer crack, long roll
    }
    for name, kw in variants.items():
        write_wav(out_dir / f'{name}.wav', make_thunder(**kw))
        print('wrote', out_dir / f'{name}.wav')
