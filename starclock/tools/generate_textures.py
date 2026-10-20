"""Оригинальные спрайты Civitatum Starclock. Python 3.10+, Pillow 11.3.0, MIT."""

import argparse
from pathlib import Path
from PIL import Image, ImageDraw

NIGHT = "#0B1020"
GOLD = "#E3C07B"
PARCHMENT = "#E8DCC0"
AMETHYST = "#8E6FB3"
INK = "#2A2118"
MATERIALS = {
    1: ("#A16D46", "#D79B63", "#614332"),
    2: ("#A7B4C5", "#E4E8EE", "#586479"),
    3: (GOLD, "#FFF0BB", "#80623D"),
}


def draw_deck(draw, tier):
    edge, light, shade = MATERIALS[tier]
    draw.rectangle((5, 2, 13, 12), fill=shade, outline=INK)
    draw.rectangle((3, 3, 11, 13), fill=PARCHMENT, outline=INK)
    draw.rectangle((2, 4, 10, 14), fill=edge, outline=INK)
    draw.rectangle((3, 5, 9, 13), fill=NIGHT)
    draw.line((4, 6, 8, 6), fill=light)
    draw.line((4, 12, 8, 12), fill=light)
    draw.point((6, 8), fill=PARCHMENT)
    draw.line((5, 9, 7, 9), fill=AMETHYST)
    draw.point((6, 10), fill=GOLD)
    if tier >= 2:
        draw.point((4, 7), fill=GOLD)
        draw.point((8, 11), fill=GOLD)
    if tier == 3:
        draw.line((11, 3, 12, 3), fill=AMETHYST)
        draw.point((12, 4), fill=PARCHMENT)


def draw_sextant(draw, tier):
    edge, light, shade = MATERIALS[tier]
    draw.line([(3, 3), (3, 12), (12, 12)], fill=INK, width=3)
    draw.line([(3, 3), (3, 12), (12, 12)], fill=edge, width=1)
    draw.line([(4, 2), (8, 3), (11, 6), (13, 10), (13, 12)], fill=shade, width=2)
    draw.line([(4, 2), (8, 3), (11, 6), (13, 10)], fill=light)
    draw.line((3, 12, 11, 4), fill=INK, width=3)
    draw.line((3, 12, 11, 4), fill=edge)
    draw.rectangle((8, 3, 12, 5), fill=shade, outline=INK)
    draw.point((12, 4), fill=AMETHYST)
    draw.point((4, 11), fill=light)
    draw.point((6, 4), fill=INK)
    draw.point((10, 8), fill=INK)
    if tier == 3:
        draw.point((9, 10), fill=AMETHYST)
        draw.point((10, 10), fill=PARCHMENT)


def draw_bowl(draw, tier):
    edge, light, shade = MATERIALS[tier]
    draw.polygon([(1, 6), (14, 6), (12, 11), (10, 13), (5, 13), (3, 11)], fill=INK)
    draw.polygon([(2, 7), (13, 7), (11, 11), (9, 12), (6, 12), (4, 10)], fill=edge)
    draw.line((5, 12, 10, 12), fill=shade)
    draw.ellipse((1, 3, 14, 8), fill=light, outline=INK)
    draw.ellipse((3, 4, 12, 7), fill=NIGHT)
    draw.line((4, 5, 8, 5), fill="#456184")
    draw.point((10, 5), fill=PARCHMENT)
    draw.point((8, 6), fill=AMETHYST)
    if tier >= 2:
        draw.rectangle((6, 9, 9, 10), fill=shade)
        draw.point((7, 9), fill=AMETHYST)
    if tier == 3:
        draw.point((5, 5), fill=GOLD)


def draw_ring(draw, tier):
    edge, light, shade = MATERIALS[tier]
    draw.ellipse((2, 2, 13, 13), fill=INK)
    draw.ellipse((3, 3, 12, 12), fill=edge)
    draw.ellipse((5, 5, 10, 10), fill=INK)
    draw.ellipse((6, 6, 9, 9), fill=(0, 0, 0, 0))
    draw.line((4, 4, 7, 3), fill=light)
    draw.line((10, 12, 12, 10), fill=shade)
    for point in [(7, 3), (10, 4), (12, 7), (11, 10), (8, 12), (5, 11), (3, 8)]:
        draw.point(point, fill=INK)
    if tier >= 2:
        draw.rectangle((9, 2, 11, 4), fill=AMETHYST, outline=INK)
        draw.point((10, 2), fill=PARCHMENT)
    if tier == 3:
        draw.point((3, 5), fill=PARCHMENT)
        draw.point((11, 11), fill=PARCHMENT)


def draw_key(draw):
    draw.ellipse((2, 1, 9, 8), fill=GOLD, outline=INK)
    draw.ellipse((4, 3, 7, 6), fill=(0, 0, 0, 0), outline=INK)
    draw.rectangle((5, 7, 7, 14), fill=INK)
    draw.line((6, 7, 6, 13), fill=GOLD)
    draw.rectangle((7, 10, 10, 11), fill=INK)
    draw.line((7, 10, 9, 10), fill=GOLD)
    draw.point((3, 3), fill=PARCHMENT)
    draw.point((6, 2), fill=AMETHYST)



def model_atlas(kind):
    """64×64: четыре материала сверху, орнамент и отражение снизу."""
    image = Image.new("RGBA", (64, 64), INK)
    draw = ImageDraw.Draw(image)
    bases = [(GOLD, "#B48C52", "#FFF0BB"), (NIGHT, "#172541", AMETHYST),
             (PARCHMENT, "#C6B78D", "#FFF3D5"), (AMETHYST, "#583D78", "#C7A7ED")]
    for tile, (base, shade, light) in enumerate(bases):
        left = tile * 16
        draw.rectangle((left, 0, left + 15, 15), fill=base)
        for y in range(16):
            for x in range(16):
                grain = (x * 17 + y * 11 + x * y * 3) % 41
                if grain == 0:
                    draw.point((left + x, y), fill=shade)
                elif grain == 1:
                    draw.point((left + x, y), fill=light)
        draw.line((left, 0, left + 15, 0), fill=light)
        draw.line((left, 15, left + 15, 15), fill=shade)
    draw.rectangle((0, 16, 23, 55), fill=NIGHT, outline=GOLD)
    draw.rectangle((2, 18, 21, 53), outline="#80623D")
    draw.rectangle((32, 16, 55, 55), fill=PARCHMENT, outline=GOLD)
    draw.rectangle((34, 18, 53, 53), outline=INK)
    for x, y in [(4, 21), (18, 24), (6, 45), (17, 49), (4, 34), (19, 37)]:
        draw.point((x, y), fill=PARCHMENT)
    draw.ellipse((5, 27, 18, 44), outline=AMETHYST)
    draw.line((11, 24, 11, 47), fill=GOLD)
    draw.line((4, 35, 19, 35), fill=GOLD)
    draw.polygon([(11, 29), (15, 35), (11, 42), (7, 35)], outline=PARCHMENT)
    draw.line([(39, 40), (42, 28), (47, 37), (50, 25)], fill=INK)
    for x, y in [(39, 40), (42, 28), (47, 37), (50, 25)]:
        draw.rectangle((x - 1, y - 1, x + 1, y + 1), fill=AMETHYST)
    if kind == "sextant":
        for x in range(1, 16, 3):
            draw.line((x, 5, x, 10 if x % 2 else 8), fill=INK)
        draw.ellipse((50, 2, 61, 13), fill="#65549B", outline="#D2C4F0")
        draw.line((52, 4, 56, 4), fill=PARCHMENT)
        draw.point((53, 5), fill=PARCHMENT)
    elif kind == "bowl":
        draw.rectangle((0, 16, 23, 55), fill=NIGHT, outline=GOLD)
        for radius in (4, 8, 11):
            draw.ellipse((11 - radius, 35 - radius * 2, 11 + radius, 35 + radius * 2),
                         outline="#30466C")
        draw.line([(4, 42), (9, 30), (14, 39), (19, 26)], fill=AMETHYST)
        for x, y in [(4, 42), (9, 30), (14, 39), (19, 26)]:
            draw.rectangle((x - 1, y - 1, x + 1, y + 1), fill=PARCHMENT)
    elif kind == "ring":
        for x in range(2, 15, 4):
            draw.line((x, 3, x, 12), fill=INK)
            draw.point((x + 1, 5), fill=PARCHMENT)
        draw.polygon([(56, 1), (63, 7), (56, 14), (49, 7)], fill="#70549D",
                     outline="#D6BEF0")
        draw.line([(51, 6), (56, 3), (60, 7)], fill=PARCHMENT)
    for y in range(57, 64):
        for x in range(64):
            draw.point((x, y), fill=GOLD if (x + y * 3) % 13 == 0 else NIGHT)
    return image


def generate_model_atlases(output):
    target = output / "assets" / "starclock" / "textures" / "model"
    target.mkdir(parents=True, exist_ok=True)
    for kind in ("deck", "sextant", "bowl", "ring"):
        model_atlas(kind).save(target / f"{kind}.png", optimize=False, compress_level=9)
    print(f"Создано 4 атласа моделей 64×64 в {target}")


def generate(output):
    target = output / "assets" / "starclock" / "textures" / "item"
    target.mkdir(parents=True, exist_ok=True)
    for kind, painter in [("deck", draw_deck), ("sextant", draw_sextant),
                          ("bowl", draw_bowl), ("ring", draw_ring)]:
        for tier in (1, 2, 3):
            image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            painter(ImageDraw.Draw(image), tier)
            image.save(target / f"{kind}_{tier}.png", optimize=False, compress_level=9)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw_key(ImageDraw.Draw(image))
    image.save(target / "tower_key.png", optimize=False, compress_level=9)
    print(f"Создано 13 текстур в {target}")
    generate_model_atlases(output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Генерация оригинальных текстур Starclock")
    parser.add_argument("--output", required=True, type=Path)
    generate(parser.parse_args().output)
