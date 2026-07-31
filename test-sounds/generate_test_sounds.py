#!/usr/bin/env python3
"""バンドナイフ張力測定アプリ用のテスト音を生成するスクリプト。

基本周波数 f0 (~7.5 Hz) の整数倍のみで合成する。
PCスピーカーで聞こえる 80〜300 Hz 帯（主に 12次・24次・36次）を強調する。
"""

import math
import random
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 44100
OUTPUT_DIR = Path(__file__).parent
DEFAULT_DURATION = 1.2
TARGET_PEAK = 0.95
WEAK_TARGET_PEAK = 0.14

DEFAULT_EQUIPMENT = {
    "name": "ペフ用スライサー1号",
    "mass_per_meter": 0.844,
    "span_meters": 1.0,
    "standard_tension": 160.0,
    "spec_lower": 150.0,
    "spec_upper": 180.0,
    "width_mm": 86.0,
    "thickness_mm": 1.25,
}

MEASUREMENT_JITTERS = [-0.012, -0.005, 0.0, 0.006, 0.011]
PEAK_ORDER = 12
MAX_ORDER = 30  # 36次まで（f0~7.5Hz なら 270 Hz 付近）


def tension_to_frequency(tension_n: float, mass: float, span: float) -> float:
    eq = DEFAULT_EQUIPMENT
    w = eq["width_mm"] / 1000.0
    t = eq["thickness_mm"] / 1000.0
    ei = 210e9 * (w * t**3 / 12.0)
    k = math.pi / span
    omega_sq = k**2 * tension_n / mass + k**4 * ei / mass
    return math.sqrt(omega_sq) / (2 * math.pi)


def write_wav(path: Path, samples: list[float], target_peak: float = TARGET_PEAK) -> None:
    boosted = normalize_peak(samples, target_peak)
    clipped = [max(-1.0, min(1.0, s)) for s in boosted]
    pcm = b"".join(struct.pack("<h", int(s * 32767)) for s in clipped)
    with wave.open(str(path), "w") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(pcm)


def normalize_peak(samples: list[float], target_peak: float) -> list[float]:
    peak = max((abs(s) for s in samples), default=0.0)
    if peak < 1e-8:
        return samples
    return [s * (target_peak / peak) for s in samples]


def tap_envelope(t: float, attack: float = 500.0, decay: float = 4.5) -> float:
    return (1.0 - math.exp(-attack * t)) * math.exp(-decay * t)


def audible_order_weight(fundamental_hz: float, order: int) -> float:
    """周波数帯ごとに重み付け。可聴帯域の倍音を強く、超低域は弱く（アプリ用に残す）。"""
    freq = fundamental_hz * order
    # 1/n 減衰（のこぎり波に近い自然な倍音列）
    weight = 1.0 / order

    if freq < 35:
        weight *= 0.08
    elif freq < 65:
        weight *= 0.35
    elif freq < 120:
        weight *= 5.0
    elif freq < 320:
        weight *= 4.0
    else:
        weight *= 0.5

    # 12次・24次・36次をさらに強調（耳で倍音として分かりやすい）
    if order in (12, 24, 36):
        weight *= 2.5
    if order in (11, 13, 23, 25):
        weight *= 1.6

    return weight


def harmonic_weights(
    fundamental_hz: float,
    max_order: int,
    *,
    order_scale: dict[int, float] | None = None,
) -> list[tuple[int, float]]:
    raw: list[tuple[int, float]] = []
    for order in range(1, max_order + 1):
        weight = audible_order_weight(fundamental_hz, order)
        if order_scale and order in order_scale:
            weight *= order_scale[order]
        if weight > 1e-6:
            raw.append((order, weight))
    return raw


def add_tap_click(samples: list[float], click_ms: float = 8.0, level: float = 0.35) -> list[float]:
    """打撃感のある短いクリック（倍音確認用・高域を少し足す）。"""
    n = min(int(SAMPLE_RATE * click_ms / 1000.0), len(samples))
    rng = random.Random(42)
    for i in range(n):
        click = (1.0 - i / n) * level * rng.uniform(-1.0, 1.0)
        samples[i] += click
    return samples


def generate_harmonic_tap(
    fundamental_hz: float,
    *,
    max_order: int = MAX_ORDER,
    duration: float = DEFAULT_DURATION,
    decay: float = 4.5,
    order_scale: dict[int, float] | None = None,
    orders_only: list[int] | None = None,
    target_peak: float = TARGET_PEAK,
    with_click: bool = True,
) -> list[float]:
    weights = harmonic_weights(fundamental_hz, max_order, order_scale=order_scale)
    if orders_only is not None:
        allowed = set(orders_only)
        weights = [(o, w) for o, w in weights if o in allowed]

    n = int(SAMPLE_RATE * duration)
    samples: list[float] = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = tap_envelope(t, decay=decay)
        value = 0.0
        for order, weight in weights:
            value += weight * math.sin(2 * math.pi * fundamental_hz * order * t)
        samples.append(env * value)

    if with_click:
        samples = add_tap_click(samples)
    return normalize_peak(samples, target_peak)


def generate_silence(duration: float = 1.0) -> list[float]:
    return [0.0] * int(SAMPLE_RATE * duration)


def _double_hit(samples: list[float], gap_sec: float = 0.035) -> list[float]:
    duration = 1.0
    gap_samples = int(gap_sec * SAMPLE_RATE)
    n = int(SAMPLE_RATE * duration)
    out = [0.0] * n
    for hit_start in (0, gap_samples):
        for j, value in enumerate(samples):
            idx = hit_start + j
            if idx < n:
                out[idx] += value
    return normalize_peak(out, TARGET_PEAK)


def generate_audible_harmonic_sounds() -> None:
    eq = DEFAULT_EQUIPMENT
    m, s = eq["mass_per_meter"], eq["span_meters"]
    out_dir = OUTPUT_DIR / "audible-harmonics"
    out_dir.mkdir(parents=True, exist_ok=True)

    f0_std = tension_to_frequency(eq["standard_tension"], m, s)
    f0_low = tension_to_frequency(eq["spec_lower"], m, s)
    f0_high = tension_to_frequency(eq["spec_upper"], m, s)

    h12 = f0_std * 12
    h24 = f0_std * 24
    h36 = f0_std * 36

    sounds: list[tuple[str, list[float], str]] = [
        (
            f"00_listen_12th_24th_36th_{h12:.0f}_{h24:.0f}_{h36:.0f}hz.wav",
            generate_harmonic_tap(
                f0_std,
                orders_only=[12, 24, 36],
                with_click=True,
            ),
            f"耳確認用: {h12:.0f}/{h24:.0f}/{h36:.0f} Hz の3つの倍音がはっきり聞こえる",
        ),
        (
            f"01_harmonics_f0_{f0_std:.1f}hz_12th_{h12:.0f}hz_160n.wav",
            generate_harmonic_tap(f0_std),
            f"f0={f0_std:.1f} Hz 1-{MAX_ORDER}次（12次={h12:.0f} Hz 最強）標準 160 N",
        ),
        (
            f"02_harmonics_f0_{f0_high:.1f}hz_12th_{f0_high * 12:.0f}hz_180n.wav",
            generate_harmonic_tap(f0_high),
            f"規格上限 180 N",
        ),
        (
            f"03_harmonics_weak_low_orders_f0_{f0_std:.1f}hz.wav",
            generate_harmonic_tap(
                f0_std,
                order_scale={o: 0.1 for o in range(1, 8)},
            ),
            "低次(1-7次)を弱くした倍音列",
        ),
        (
            f"04_audible_band_12th_to_18th_{h12:.0f}hz_up.wav",
            generate_harmonic_tap(f0_std, orders_only=[12, 13, 14, 15, 16, 17, 18]),
            f"可聴帯域のみ {h12:.0f} Hz 付近の倍音束",
        ),
        (
            f"05_series_f0_{f0_std:.1f}hz_12th_{h12:.0f}hz_160n.wav",
            generate_harmonic_tap(f0_std),
            "01 と同型",
        ),
        (
            f"06_series_f0_{f0_low:.1f}hz_12th_{f0_low * 12:.0f}hz_150n.wav",
            generate_harmonic_tap(f0_low),
            "規格下限 150 N",
        ),
        (
            f"07_series_f0_{f0_high:.1f}hz_12th_{f0_high * 12:.0f}hz_180n.wav",
            generate_harmonic_tap(f0_high),
            "規格上限 180 N",
        ),
        (
            "08_harmonics_160n_weak_tap.wav",
            generate_harmonic_tap(f0_std, target_peak=WEAK_TARGET_PEAK),
            "弱い打撃",
        ),
        (
            "09_harmonics_160n_double_hit.wav",
            _double_hit(generate_harmonic_tap(f0_std, duration=0.45, target_peak=1.0)),
            "二重打撃",
        ),
    ]

    seq_name = "10_measurement_5taps_12th_harmonic.wav"

    print("=== 倍音テスト音（audible-harmonics/）===")
    print(f"  f0={f0_std:.2f} Hz / 12次={h12:.0f} Hz / 24次={h24:.0f} Hz / 36次={h36:.0f} Hz\n")

    for filename, samples, note in sounds:
        write_wav(out_dir / filename, samples)
        peak = max(abs(x) for x in samples)
        print(f"  {filename}")
        print(f"    {note}  (peak={peak:.3f})")

    keep = {s[0] for s in sounds} | {seq_name}
    for old in out_dir.glob("*.wav"):
        if old.name not in keep:
            old.unlink()
            print(f"  (削除) {old.name}")

    sequence: list[float] = []
    for i, jitter in enumerate(MEASUREMENT_JITTERS):
        if i > 0:
            sequence.extend(generate_silence(1.2))
        f0 = f0_std * (1.0 + jitter)
        sequence.extend(generate_harmonic_tap(f0))
    write_wav(out_dir / seq_name, normalize_peak(sequence, TARGET_PEAK))
    print(f"\n  {seq_name}")
    print("    5回測定フロー用")
    print(f"  -> {len(sounds) + 1} ファイル\n")


def main() -> None:
    generate_audible_harmonic_sounds()


if __name__ == "__main__":
    main()
