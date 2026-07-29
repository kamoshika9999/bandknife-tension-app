#!/usr/bin/env python3
"""バンドナイフ張力測定アプリ用のテスト音を生成するスクリプト。

アプリ仕様:
  - サンプルレート: 44100 Hz
  - 検出周波数範囲: 5〜300 Hz（基本周波数はスパン 1 m で約 7 Hz、耳に聞こえるのは倍音）
  - 打撃品質判定: 振幅 0.15〜0.95、倍音比、二重打撃検出
  - 張力: T = m·((2πf)² − (nπ/S)⁴·EI/m) / (nπ/S)²  （張力＋曲げ。未設定時は旧式 T = 4·m·S²·f²）
"""

import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 44100
OUTPUT_DIR = Path(__file__).parent

# デフォルト設備（Repository.ensureDefaultEquipmentIfNeeded と同じ）
DEFAULT_EQUIPMENT = {
    "name": "ペフ用スライサー1号",
    "mass_per_meter": 0.844,       # kg/m（幅86mm × 板厚1.25mm × 密度7850）
    "span_meters": 1.0,
    "standard_tension": 160.0,     # N
    "spec_lower": 150.0,
    "spec_upper": 180.0,
    "width_mm": 86.0,
    "thickness_mm": 1.25,
}

# AppViewModel.injectManualScreenshotTaps() と同じ揺らぎ（5回測定用）
MEASUREMENT_JITTERS = [-0.012, -0.005, 0.0, 0.006, 0.011]


def tension_to_frequency(tension_n: float, mass: float, span: float) -> float:
    """張力＋曲げモデルで周波数を逆算（デフォルト設備寸法）。"""
    eq = DEFAULT_EQUIPMENT
    w = eq["width_mm"] / 1000.0
    t = eq["thickness_mm"] / 1000.0
    e = 210e9
    inertia = w * t**3 / 12.0  # 面外曲げ（張力感度が高い）
    ei = e * inertia
    n = 1
    k = n * math.pi / span
    omega_sq = k**2 * tension_n / mass + k**4 * ei / mass
    return math.sqrt(omega_sq) / (2 * math.pi)


def write_wav(path: Path, samples: list[float]) -> None:
    """16bit モノラル WAV を書き出す。"""
    clipped = [max(-1.0, min(1.0, s)) for s in samples]
    pcm = b"".join(struct.pack("<h", int(s * 32767)) for s in clipped)
    with wave.open(str(path), "w") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(pcm)


def tap_envelope(t: float, attack: float = 300.0, decay: float = 10.0) -> float:
    """打撃音らしい急峻な立ち上がりと指数減衰。"""
    return (1.0 - math.exp(-attack * t)) * math.exp(-decay * t)


def generate_tap(
    freq_hz: float,
    duration: float = 0.45,
    amplitude: float = 0.55,
    harmonic_ratio: float = 0.15,
    decay: float = 10.0,
) -> list[float]:
    n = int(SAMPLE_RATE * duration)
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = tap_envelope(t, decay=decay)
        phase = 2 * math.pi * freq_hz * t
        s = math.sin(phase) + harmonic_ratio * math.sin(2 * phase)
        samples.append(amplitude * env * s)
    return samples


def generate_multifreq_tap(
    components: list[tuple[float, float]],
    duration: float = 0.45,
    amplitude: float = 0.55,
    decay: float = 10.0,
) -> list[float]:
    """任意の周波数と強さの組み合わせで打撃音を合成する。"""
    n = int(SAMPLE_RATE * duration)
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = tap_envelope(t, decay=decay)
        value = 0.0
        for freq_hz, weight in components:
            value += weight * math.sin(2 * math.pi * freq_hz * t)
        samples.append(amplitude * env * value)
    return samples


def generate_halving_chain_tap(
    peak_hz: float,
    *,
    depth: int = 4,
    sub_strength: float = 0.5,
    duration: float = 0.45,
    amplitude: float = 0.55,
) -> list[float]:
    """耳に聞こえるピークと、アプリが半分ずつたどる下位成分（2, 4, 8, 16倍音相当）。"""
    components: list[tuple[float, float]] = []
    freq = peak_hz
    weight = 1.0
    for _ in range(depth + 1):
        components.append((freq, weight))
        freq /= 2.0
        weight *= sub_strength
    return generate_multifreq_tap(components, duration=duration, amplitude=amplitude)


def generate_integer_harmonic_series_tap(
    fundamental_hz: float,
    *,
    max_order: int = 12,
    peak_order: int = 12,
    duration: float = 0.45,
    amplitude: float = 0.55,
) -> list[float]:
    """基本周波数の整数倍を並べ、指定次数（例: 12次≈90Hz）を最も強くする。"""
    components = []
    for order in range(1, max_order + 1):
        # ピーク次数に近いほど強く、低次は弱め
        weight = 0.15 + 0.85 * math.exp(-((order - peak_order) ** 2) / 8.0)
        components.append((fundamental_hz * order, weight))
    return generate_multifreq_tap(components, duration=duration, amplitude=amplitude)


def generate_silence(duration: float = 1.0) -> list[float]:
    return [0.0] * int(SAMPLE_RATE * duration)


def generate_audible_harmonic_sounds() -> None:
    """耳に聞こえる帯域（主に 80〜90 Hz）をピークとする倍音テスト音。"""
    eq = DEFAULT_EQUIPMENT
    m, s = eq["mass_per_meter"], eq["span_meters"]
    out_dir = OUTPUT_DIR / "audible-harmonics"
    out_dir.mkdir(parents=True, exist_ok=True)

    f0_std = tension_to_frequency(eq["standard_tension"], m, s)
    f0_low = tension_to_frequency(eq["spec_lower"], m, s)
    f0_high = tension_to_frequency(eq["spec_upper"], m, s)

    # 12次付近が耳に聞こえる帯域（80〜90 Hz）
    peak_order = 12
    audible_std = f0_std * peak_order
    audible_low = f0_low * peak_order
    audible_high = f0_high * peak_order

    sounds: list[tuple[str, list[float], str]] = [
        (
            "01_halving_chain_peak_85hz.wav",
            generate_halving_chain_tap(85.0, depth=4, sub_strength=0.5),
            "ピーク 85 Hz + 42/21/11/5 Hz（アプリの半分たどり用）",
        ),
        (
            "02_halving_chain_peak_88hz.wav",
            generate_halving_chain_tap(88.0, depth=4, sub_strength=0.55),
            "ピーク 88 Hz + 下位成分（半分たどり用・やや強め）",
        ),
        (
            "03_halving_chain_weak_sub_85hz.wav",
            generate_halving_chain_tap(85.0, depth=4, sub_strength=0.25),
            "ピーク 85 Hz・下位成分が弱い（半分たどりが止まりやすい）",
        ),
        (
            "04_peak_only_85hz_no_subharmonics.wav",
            generate_tap(85.0),
            "85 Hz のみ（下位成分なし・対照用）",
        ),
        (
            f"05_series_f0_{f0_std:.1f}hz_12th_{audible_std:.0f}hz_160n.wav",
            generate_integer_harmonic_series_tap(f0_std, max_order=14, peak_order=peak_order),
            f"基本 {f0_std:.1f} Hz の整数倍列、{peak_order}次={audible_std:.0f} Hz が最強（160 N）",
        ),
        (
            f"06_series_f0_{f0_low:.1f}hz_12th_{audible_low:.0f}hz_150n.wav",
            generate_integer_harmonic_series_tap(f0_low, max_order=14, peak_order=peak_order),
            f"規格下限 150 N 相当（12次約{audible_low:.0f} Hz）",
        ),
        (
            f"07_series_f0_{f0_high:.1f}hz_12th_{audible_high:.0f}hz_180n.wav",
            generate_integer_harmonic_series_tap(f0_high, max_order=14, peak_order=peak_order),
            f"規格上限 180 N 相当（12次約{audible_high:.0f} Hz）",
        ),
        (
            "08_halving_chain_85hz_weak_tap.wav",
            generate_halving_chain_tap(85.0, depth=4, sub_strength=0.5, amplitude=0.08),
            "半分たどり用・弱い打撃（品質判定テスト）",
        ),
        (
            "09_halving_chain_85hz_double_hit.wav",
            _double_halving_chain(85.0),
            "半分たどり用・二重打撃",
        ),
    ]

    print("=== 耳に聞こえる倍音テスト音（audible-harmonics/）===")
    print(f"  デフォルト設備: 基本 {f0_std:.2f} Hz → {peak_order}次で約 {audible_std:.0f} Hz")
    print("  アプリはピークから半分ずつ下がれる成分があると、より低い周波数を採用します\n")

    for filename, samples, note in sounds:
        write_wav(out_dir / filename, samples)
        peak = max(abs(x) for x in samples)
        print(f"  {filename}")
        print(f"    {note}  (peak={peak:.3f})")

    # 5回測定（標準張力＋揺らぎ、12次が聞こえる帯域）
    sequence: list[float] = []
    for i, jitter in enumerate(MEASUREMENT_JITTERS):
        if i > 0:
            sequence.extend(generate_silence(1.2))
        f0 = f0_std * (1.0 + jitter)
        sequence.extend(
            generate_integer_harmonic_series_tap(f0, max_order=14, peak_order=peak_order)
        )
    seq_name = "10_measurement_5taps_12th_harmonic.wav"
    write_wav(out_dir / seq_name, sequence)
    print(f"\n  {seq_name}")
    print("    5回測定フロー用（各打撃とも 12次付近が最強）")
    print(f"  → {len(sounds) + 1} ファイルを {out_dir} に生成\n")


def _double_halving_chain(peak_hz: float) -> list[float]:
    """二重打撃（半分たどり用チェーン）。"""
    duration = 0.6
    gap = 0.035
    hit = generate_halving_chain_tap(peak_hz, duration=0.25, amplitude=0.55)
    gap_samples = int(gap * SAMPLE_RATE)
    n = int(SAMPLE_RATE * duration)
    samples = [0.0] * n
    for hit_start in (0, gap_samples):
        for j, value in enumerate(hit):
            idx = hit_start + j
            if idx < n:
                samples[idx] += value
    return samples


def main() -> None:
    generate_audible_harmonic_sounds()


if __name__ == "__main__":
    main()
