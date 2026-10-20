"""Оригинальные звуки Starclock, MIT. Синтез PCM и кодирование Ogg Vorbis."""
import argparse
import array
import hashlib
import json
import math
import struct
import subprocess
import sys
import tempfile
import wave
from pathlib import Path
import imageio_ffmpeg

RATE = 44100
TAU = math.tau
DURATIONS = {"shuffle": 0.28, "sextant_click": 0.18, "water_pour": 0.70,
             "ring_hum": 0.75, "stars_reveal": 1.20, "card_flip": 0.24,
             "omen_good": 1.35, "omen_bad": 1.35}


class Noise:
    def __init__(self, seed):
        self.state = seed
        self.low = 0.0

    def sample(self):
        value = self.state
        value ^= (value << 13) & 0xFFFFFFFF
        value ^= value >> 17
        value ^= (value << 5) & 0xFFFFFFFF
        self.state = value & 0xFFFFFFFF
        return self.state / 2147483648.0 - 1.0


def bell(t, frequency, decay):
    if t < 0:
        return 0.0
    onset = min(1.0, t / 0.004)
    return onset * math.exp(-decay * t) * (
        math.sin(TAU * frequency * t) + 0.28 * math.sin(TAU * frequency * 2.003 * t)
        + 0.09 * math.sin(TAU * frequency * 4.011 * t))


def synthesize(name):
    duration = DURATIONS[name]
    noise = Noise(int.from_bytes(hashlib.sha256(name.encode("ascii")).digest()[:4], "little") or 1)
    samples = []
    for index in range(round(duration * RATE)):
        t = index / RATE
        raw = noise.sample()
        noise.low += 0.06 * (raw - noise.low)
        high = raw - noise.low
        if name == "shuffle":
            strokes = sum(math.exp(-((t - start) / 0.022) ** 2) for start in (0.035, 0.11, 0.19))
            value = (high * 0.7 + noise.low * 0.6) * strokes
        elif name == "sextant_click":
            value = (0.5 * bell(t, 1640, 42) + 0.3 * bell(t, 2900, 65)
                     + high * math.exp(-130 * t) * 0.22)
        elif name == "water_pour":
            flowing = math.sin(math.pi * t / duration) ** 1.1
            value = (noise.low * 1.5 + high * 0.07) * flowing
            for start, frequency in ((0.08, 780), (0.24, 1120), (0.41, 630), (0.54, 930)):
                dt = t - start
                if dt >= 0:
                    value += 0.13 * math.exp(-38 * dt) * math.sin(TAU * (frequency * dt - 370 * dt * dt))
        elif name == "ring_hum":
            modulation = 0.7 + 0.3 * math.sin(TAU * 4.5 * t)
            phase = TAU * 220 * t + 0.035 * math.sin(TAU * 5 * t)
            value = modulation * (0.65 * math.sin(phase) + 0.23 * math.sin(phase * 2) + 0.10 * math.sin(phase * 4))
        elif name == "stars_reveal":
            value = sum(0.28 * bell(t - start, frequency, 4.0)
                        for start, frequency in ((0.0, 440), (0.12, 554.365), (0.24, 659.255), (0.40, 880)))
            value += high * 0.035 * math.sin(math.pi * t / duration) ** 2
        elif name == "card_flip":
            swish = math.exp(-((t - 0.09) / 0.043) ** 2)
            flutter = 0.55 + 0.45 * math.sin(TAU * 52 * t)
            value = high * swish * flutter * 0.7 + 0.12 * bell(t - 0.13, 1050, 90)
        elif name == "omen_good":
            value = sum(0.25 * bell(t - start, frequency, 2.8)
                        for start, frequency in ((0.0, 261.6256), (0.07, 329.6276), (0.14, 391.9954), (0.30, 523.2511)))
        elif name == "omen_bad":
            value = sum(0.23 * bell(t - start, frequency, 2.6)
                        for start, frequency in ((0.0, 146.8324), (0.08, 155.5635), (0.16, 207.6523)))
            value += noise.low * 0.12 * math.sin(math.pi * t / duration)
        else:
            raise ValueError("Неизвестный звук: " + name)
        attack = 0.001 if name == "sextant_click" else 0.008
        release = 0.045 if duration < 0.4 else 0.16
        envelope = min(1.0, t / attack, (duration - t) / release)
        if name == "ring_hum":
            envelope *= min(1.0, t / 0.06, (duration - t) / 0.22)
        samples.append(value * max(0.0, envelope))
    peak = max(abs(value) for value in samples)
    if peak <= 0:
        raise ValueError("Синтезирована тишина: " + name)
    gain = 0.62 / peak
    pcm = array.array("h", (round(max(-1, min(1, value * gain)) * 32767) for value in samples))
    if sys.byteorder != "little":
        pcm.byteswap()
    return pcm.tobytes()


def ogg_crc(data):
    crc = 0
    for value in data:
        crc ^= value << 24
        for _ in range(8):
            crc = ((crc << 1) ^ (0x04C11DB7 if crc & 0x80000000 else 0)) & 0xFFFFFFFF
    return crc


def canonicalize_ogg(path, name):
    """Фиксирует серийный номер потока и пересчитывает CRC каждой страницы."""
    payload = bytearray(path.read_bytes())
    serial = hashlib.sha256(name.encode("ascii")).digest()[:4]
    offset = 0
    while offset < len(payload):
        if payload[offset:offset + 4] != b"OggS":
            raise ValueError("Повреждён контейнер Ogg: " + name)
        segments = payload[offset + 26]
        body_start = offset + 27 + segments
        end = body_start + sum(payload[offset + 27:body_start])
        if end > len(payload):
            raise ValueError("Неполная страница Ogg: " + name)
        payload[offset + 14:offset + 18] = serial
        payload[offset + 22:offset + 26] = b"\0\0\0\0"
        payload[offset + 22:offset + 26] = struct.pack("<I", ogg_crc(payload[offset:end]))
        offset = end
    path.write_bytes(payload)


def generate(output, report=None):
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    target = output / "assets" / "starclock" / "sounds" / "ritual"
    target.mkdir(parents=True, exist_ok=True)
    metadata = {"sample_rate": RATE, "channels": 1, "sounds": {}}
    with tempfile.TemporaryDirectory(prefix="starclock-audio-") as temporary:
        for name, duration in DURATIONS.items():
            source = Path(temporary) / (name + ".wav")
            with wave.open(str(source), "wb") as wav:
                wav.setnchannels(1)
                wav.setsampwidth(2)
                wav.setframerate(RATE)
                wav.writeframes(synthesize(name))
            destination = target / (name + ".ogg")
            subprocess.run([ffmpeg, "-nostdin", "-v", "error", "-y", "-i", str(source),
                            "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", "4",
                            "-fflags", "+bitexact", "-flags:a", "+bitexact", str(destination)], check=True)
            canonicalize_ogg(destination, name)
            metadata["sounds"][name] = {"duration": duration, "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
                                        "bytes": destination.stat().st_size}
    if report is not None:
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Создано {len(DURATIONS)} оригинальных звуков Ogg Vorbis в {target}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Синтез звуков Civitatum Starclock")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", type=Path)
    options = parser.parse_args()
    generate(options.output, options.report)
