"""Подготовка предоставленных созвездий и оригинальных карт Starclock."""

import argparse
import base64
import hashlib
import io
import json
import math
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageChops

NIGHT = (11, 16, 32)
GOLD = (227, 192, 123)
PARCHMENT = (232, 220, 192)
AMETHYST = (142, 111, 179)
SIGNS = {
    "tower": "Башня", "spear": "Копье", "hearth": "Очаг",
    "beast": "Зверь", "mother": "Матерь", "smith": "Кузнец",
    "merchant": "Купец", "spinner": "Пряха", "messenger": "Гонец",
    "ancestor": "Пращур", "shepherd": "Пастырь", "wanderer": "Странник",
    "serpent": "Змей",
}


def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=False, compress_level=9)


def figure(source):
    """Извлекает голубые линии оригинала без перерисовки формы."""
    source = source.convert("RGB")
    mask = Image.new("L", source.size)
    values = []
    for red, green, blue in source.getdata():
        cyan = min(green - red, (blue - red) * 0.8)
        values.append(round(max(0, min(255, (cyan - 4) * 5))))
    mask.putdata(values)
    # Объединяем близкие штрихи в компоненты на уменьшенной сетке.
    small = mask.resize((222, 111), Image.Resampling.BOX)
    small = small.point(lambda value: 255 if value > 14 else 0).filter(ImageFilter.MaxFilter(5))
    pixels = small.load()
    visited = set()
    regions = []
    for y in range(small.height):
        for x in range(small.width):
            if not pixels[x, y] or (x, y) in visited:
                continue
            todo = [(x, y)]
            visited.add((x, y))
            component = []
            while todo:
                px, py = todo.pop()
                component.append((px, py))
                for nx, ny in ((px-1, py), (px+1, py), (px, py-1), (px, py+1)):
                    if 0 <= nx < small.width and 0 <= ny < small.height and pixels[nx, ny] and (nx, ny) not in visited:
                        visited.add((nx, ny))
                        todo.append((nx, ny))
            if len(component) >= 100:
                regions.extend(component)
    if not regions:
        raise ValueError("В исходнике не найдена фигура")
    sx, sy = source.width / 222, source.height / 111
    bounds = (max(0, math.floor(min(x for x, y in regions) * sx)),
              max(0, math.floor(min(y for x, y in regions) * sy)),
              min(source.width, math.ceil((max(x for x, y in regions)+1) * sx)),
              min(source.height, math.ceil((max(y for x, y in regions)+1) * sy)))
    region_mask = Image.new("L", small.size)
    region_draw = ImageDraw.Draw(region_mask)
    for point in regions:
        region_draw.point(point, fill=255)
    mask = ImageChops.multiply(mask, region_mask.resize(source.size, Image.Resampling.NEAREST))
    mask = mask.crop(bounds)
    mask.thumbnail((432, 432), Image.Resampling.LANCZOS)
    alpha = Image.new("L", (512, 512))
    alpha.paste(mask, ((512-mask.width)//2, (512-mask.height)//2))
    glow = alpha.filter(ImageFilter.GaussianBlur(3)).point(lambda value: value // 2)
    result = Image.new("RGBA", (512, 512), (*AMETHYST, 0))
    result.putalpha(glow)
    lines = Image.new("RGBA", (512, 512), (*GOLD, 0))
    lines.putalpha(alpha)
    result.alpha_composite(lines)
    return result, bounds


def starfield():
    image = Image.new("RGBA", (512, 512), (*NIGHT, 255))
    draw = ImageDraw.Draw(image)
    for index in range(370):
        x = (index * 173 + index * index * 31 + 19) % 512
        y = (index * 257 + index * index * 7 + 43) % 512
        bright = index % 13 == 0
        color = (*PARCHMENT, 155) if bright else (*AMETHYST, 55 + index % 65)
        draw.point((x, y), fill=color)
        if bright:
            draw.line((x-2, y, x+2, y), fill=(*GOLD, 100))
            draw.line((x, y-2, x, y+2), fill=(*GOLD, 100))
    return image


def card(back=False):
    image = Image.new("RGBA", (256, 384))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((1, 1, 254, 382), radius=13, fill=(*PARCHMENT, 255), outline=GOLD, width=3)
    draw.rounded_rectangle((8, 8, 247, 375), radius=8, fill=(*NIGHT, 255), outline=GOLD, width=2)
    draw.rectangle((15, 15, 240, 368), outline=AMETHYST)
    for x, y, dx, dy in ((20,20,1,1), (235,20,-1,1), (20,363,1,-1), (235,363,-1,-1)):
        draw.line([(x, y+22*dy), (x,y), (x+22*dx,y)], fill=GOLD, width=2)
        draw.polygon([(x+3*dx,y+8*dy),(x+8*dx,y+3*dy),(x+13*dx,y+8*dy),(x+8*dx,y+13*dy)], outline=GOLD)
    draw.line((32, 63, 223, 63), fill=GOLD)
    draw.line((32, 302, 223, 302), fill=GOLD)
    if back:
        for radius in (48, 76, 91):
            draw.ellipse((128-radius,192-radius,128+radius,192+radius), outline=GOLD, width=1)
        for index in range(12):
            angle = index * math.tau / 12
            x, y = 128 + math.cos(angle)*83, 192 + math.sin(angle)*83
            draw.ellipse((x-2,y-2,x+2,y+2), fill=PARCHMENT)
        draw.polygon([(128,102),(159,192),(128,282),(97,192)], outline=AMETHYST, width=3)
        draw.line((49,192,207,192), fill=GOLD)
        draw.line((128,113,128,271), fill=GOLD)
        draw.ellipse((117,181,139,203), fill=GOLD)
        draw.ellipse((122,186,134,198), fill=NIGHT)
    return image


def generate(output, archive):
    target = output / "assets/starclock/textures"
    report = {"archive": archive.name, "sha256": hashlib.sha256(archive.read_bytes()).hexdigest(), "figures": {}}
    figures = {}
    with zipfile.ZipFile(archive) as zipped:
        entries = {Path(name).stem: name for name in zipped.namelist() if name.lower().endswith(".png")}
        for identifier, name in SIGNS.items():
            if name not in entries:
                raise ValueError(f"В архиве отсутствует {name}.png")
            raw = zipped.read(entries[name])
            image, bounds = figure(Image.open(io.BytesIO(raw)))
            figures[identifier] = image
            save(image, target / f"constellation/{identifier}.png")
            report["figures"][identifier] = {"source": entries[name], "sha256": hashlib.sha256(raw).hexdigest(), "crop": bounds}
    save(starfield(), target / "gui/starfield.png")
    front, back = card(), card(True)
    save(front, target / "gui/card_front.png")
    save(back, target / "gui/card_back.png")
    serpent = front.copy()
    sign = figures["serpent"].resize((224,224), Image.Resampling.LANCZOS)
    serpent.alpha_composite(sign, (16,72))
    save(serpent, target / "gui/card_zmei.png")
    particle = Image.new("RGBA", (8,8))
    draw = ImageDraw.Draw(particle)
    draw.line((3,0,3,7), fill=(*GOLD,100))
    draw.line((0,3,7,3), fill=(*GOLD,100))
    draw.rectangle((2,2,4,4), fill=GOLD)
    draw.point((3,3), fill=PARCHMENT)
    save(particle, target / "particle/star.png")
    for name, reversed_sign in (("upright",False), ("reversed",True), ("sky",False), ("timer",False)):
        icon = Image.new("RGBA",(16,16))
        draw = ImageDraw.Draw(icon)
        if name in ("upright","reversed"):
            points = [(8,2),(13,8),(10,8),(10,13),(6,13),(6,8),(3,8)]
            draw.polygon(points,fill=GOLD)
            if reversed_sign:
                icon = icon.transpose(Image.Transpose.ROTATE_180)
        elif name == "sky":
            draw.polygon([(8,1),(10,6),(15,8),(10,10),(8,15),(6,10),(1,8),(6,6)],fill=GOLD)
            draw.point((8,8),fill=PARCHMENT)
        else:
            draw.ellipse((2,2,13,13),outline=GOLD,width=2)
            draw.line([(8,4),(8,8),(11,9)],fill=PARCHMENT,width=1)
        save(icon,target / f"gui/{name}.png")
    report_path = output / "starclock-art.json"
    report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print("Созданы 13 фигур, три карты, звёздное поле, частица и четыре значка")
    return figures


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--preview",type=Path)
    args = parser.parse_args()
    figures = generate(args.output,args.archive)
    if args.preview:
        preview = Image.new("RGBA",(5*256,3*280),(*NIGHT,255))
        draw = ImageDraw.Draw(preview)
        for index,(name,im) in enumerate(figures.items()):
            x,y=(index%5)*256,(index//5)*280
            preview.alpha_composite(im.resize((256,256)),(x,y))
            draw.text((x+8,y+256),name,fill=GOLD)
        save(preview,args.preview)
        print("CONSTELLATION_GENERATED_BASE64 "+base64.b64encode(args.preview.read_bytes()).decode())
