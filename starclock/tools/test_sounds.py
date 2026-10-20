"""Проверяет воспроизводимость Ogg и реальное декодирование звуков."""
import array
import math
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
import imageio_ffmpeg
import generate_sounds


class SoundGeneratorTest(unittest.TestCase):
    def test_repeatability_and_decoded_signal(self):
        with tempfile.TemporaryDirectory(prefix="starclock-sound-check-") as directory:
            first, second = Path(directory) / "first", Path(directory) / "second"
            generate_sounds.generate(first)
            generate_sounds.generate(second)
            relative = Path("assets/starclock/sounds/ritual")
            for name, duration in generate_sounds.DURATIONS.items():
                path = first / relative / (name + ".ogg")
                self.assertEqual(path.read_bytes(), (second / relative / path.name).read_bytes(), name)
                result = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-nostdin", "-v", "error",
                                         "-i", str(path), "-f", "s16le", "-ac", "1", "-ar",
                                         str(generate_sounds.RATE), "pipe:1"],
                                        check=True, capture_output=True)
                self.assertFalse(result.stderr, name + ": " + result.stderr.decode("utf-8", errors="replace"))
                samples = array.array("h")
                samples.frombytes(result.stdout)
                if sys.byteorder != "little":
                    samples.byteswap()
                self.assertLess(abs(len(samples) / generate_sounds.RATE - duration), 0.04, name)
                peak = max(abs(value) for value in samples) / 32768
                rms = math.sqrt(sum((value / 32768) ** 2 for value in samples) / len(samples))
                self.assertGreater(peak, 0.3, name)
                self.assertLess(peak, 0.93, name)
                self.assertGreater(rms, 0.02, name)
                self.assertLess(rms, 0.40, name)
                self.assertLess(abs(samples[0]) / 32768, 0.1, name)
                self.assertLess(abs(samples[-1]) / 32768, 0.1, name)
                print(f"AUDIO_CHECK_OK {name}: {len(samples) / generate_sounds.RATE:.3f}s, "
                      f"peak={peak:.3f}, rms={rms:.3f}")


if __name__ == "__main__":
    unittest.main()
