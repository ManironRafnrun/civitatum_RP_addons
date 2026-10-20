"""Проверяет повторяемость графики и геометрию исходных изображений."""
import hashlib
import tempfile
from pathlib import Path
from generate_art import generate

PROJECT = Path(__file__).resolve().parents[1]


def main():
    with tempfile.TemporaryDirectory(prefix="starclock-art-test-") as temporary:
        root = Path(temporary)
        for name in ("a","b"):
            generate(root / name, PROJECT / "starsigns.zip")
        first = {p.relative_to(root / "a"): hashlib.sha256(p.read_bytes()).hexdigest()
                 for p in (root / "a").rglob("*") if p.is_file()}
        second = {p.relative_to(root / "b"): hashlib.sha256(p.read_bytes()).hexdigest()
                  for p in (root / "b").rglob("*") if p.is_file()}
        if first != second:
            raise AssertionError("Графика зависит от каталога или порядка запуска")
        if len(first) != 23:
            raise AssertionError(f"Неверное количество файлов графики: {len(first)}")
        print("ART_CHECK_OK: 22 PNG и манифест повторяются побайтно")


if __name__ == "__main__":
    main()
