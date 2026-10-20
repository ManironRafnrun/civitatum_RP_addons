"""Пустой шаблон GameTest; только стандартная библиотека Python, MIT."""

import argparse
import gzip
import struct
from pathlib import Path


def name(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def tag(kind, key, data):
    return bytes([kind]) + name(key) + data


def generate(output):
    root = (
        tag(3, "DataVersion", struct.pack(">i", 3955))
        + tag(9, "size", bytes([3]) + struct.pack(">i", 3) + struct.pack(">iii", 5, 3, 5))
        + tag(9, "palette", bytes([10]) + struct.pack(">i", 1)
              + tag(8, "Name", name("minecraft:air")) + b"\x00")
        + tag(9, "blocks", bytes([10]) + struct.pack(">i", 0))
        + tag(9, "entities", bytes([10]) + struct.pack(">i", 0))
        + b"\x00"
    )
    target = output / "data" / "civitatum_starclock" / "structure" / "empty.nbt"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(gzip.compress(tag(10, "", root), mtime=0))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Подготовка шаблона GameTest")
    parser.add_argument("--output", type=Path, required=True)
    generate(parser.parse_args().output)
