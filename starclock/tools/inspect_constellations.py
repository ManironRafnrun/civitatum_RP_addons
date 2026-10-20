"""Показывает состав исходного архива и создаёт обзор оригинальных изображений."""
import argparse
import base64
import io
import json
import math
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps


def inspect(archive_path, output):
    output.mkdir(parents=True, exist_ok=True)
    previews = []
    metadata = []
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
    with zipfile.ZipFile(archive_path) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            item = {"path": entry.filename, "bytes": entry.file_size}
            if Path(entry.filename).suffix.lower() in (".png", ".jpg", ".jpeg", ".webp"):
                if entry.file_size > 32 * 1024 * 1024:
                    raise ValueError("Слишком большой исходный файл: " + entry.filename)
                with Image.open(io.BytesIO(archive.read(entry))) as source:
                    picture = ImageOps.exif_transpose(source).convert("RGB")
                    item["size"] = picture.size
                    thumb = ImageOps.contain(picture, (216, 244), Image.Resampling.LANCZOS)
                    panel = Image.new("RGB", (232, 282), "#E8DCC0")
                    panel.paste(thumb, ((232 - thumb.width) // 2, 4 + (244 - thumb.height) // 2))
                    draw = ImageDraw.Draw(panel)
                    draw.text((6, 252), Path(entry.filename).name[:28], font=font, fill="#2A2118")
                    previews.append(panel)
            metadata.append(item)
    report = json.dumps(metadata, ensure_ascii=False, indent=2)
    (output / "archive.json").write_text(report + "\n", encoding="utf-8")
    print("CONSTELLATION_ARCHIVE " + report, flush=True)
    if previews:
        columns = 4
        sheet = Image.new("RGB", (232 * columns, 282 * math.ceil(len(previews) / columns)), "#0B1020")
        for index, panel in enumerate(previews):
            sheet.paste(panel, ((index % columns) * 232, (index // columns) * 282))
        sheet.save(output / "originals.png")
        binary = io.BytesIO()
        sheet.save(binary, format="PNG", compress_level=9)
        print("CONSTELLATION_ORIGINALS_BASE64 " + base64.b64encode(binary.getvalue()).decode("ascii"), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    options = parser.parse_args()
    inspect(options.archive, options.output)
