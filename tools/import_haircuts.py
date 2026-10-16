"""Import the authored 64px set verbatim; add separate shade maps for one-tone hair."""
import copy
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile
from PIL import Image


def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def main():
    root = Path(__file__).resolve().parents[1] / 'src/main/resources'
    with zipfile.ZipFile(sys.argv[1]) as archive:
        prefix = 'CCC_Haircuts_1.0.0/'
        catalog = json.loads(archive.read(prefix + 'catalog.asset.json'))
        verification = {}
        for entry in catalog['entries']:
            entry['linked_shades'] = entry['gradient'] or entry['id'] == 'hc_blonde_bangs'
            for model, variant in entry['models'].items():
                directory = variant['directory']
                assert directory.startswith('assets/civitatum/textures/character/hair_v2/') and '..' not in directory
                for name in archive.namelist():
                    relative = name.removeprefix(prefix)
                    if relative.startswith(directory + '/') and relative.endswith('.png'):
                        target = root / relative
                        target.parent.mkdir(parents=True, exist_ok=True)
                        target.write_bytes(archive.read(name))
                original = root / directory / variant['textures']['original']
                verification[entry['id'] + '/' + model] = hashlib.sha256(original.read_bytes()).hexdigest()
            # A single hair field still contains authored shadows. Give those shadows their own
            # linked tone without altering any uploaded PNG or flattening the original RGB.
            if len(entry['controls']) == 1:
                entry['linked_shades'] = True
                dark_reference = None
                for model, variant in entry['models'].items():
                    directory = root / variant['directory']
                    original = Image.open(directory / 'original.png').convert('RGBA')
                    pixels = list(original.getdata())
                    levels = sorted((r * .2126 + g * .7152 + b * .0722) for r, g, b, a in pixels if a)
                    low, high = levels[len(levels) // 5], levels[len(levels) * 4 // 5]
                    if high - low < 1:
                        low, high = min(levels), max(levels) + 1
                    weights = [[], []]
                    masks = [[], []]
                    neutral = []
                    for r, g, b, a in pixels:
                        light = r * .2126 + g * .7152 + b * .0722
                        main_weight = round(max(0, min(1, (light - low) / (high - low))) * 255) if a else 0
                        shadow_weight = 255 - main_weight if a else 0
                        for i, weight in enumerate((main_weight, shadow_weight)):
                            weights[i].append((255, 255, 255, weight))
                            masks[i].append((255, 255, 255, 255 if a and (main_weight >= shadow_weight) == (i == 0) else 0))
                        reference = (main_weight * high + shadow_weight * low) / 255 if a else 1
                        value = round(min(255, light / max(1, reference) * 235)) if a else 0
                        neutral.append((value, value, value, a))
                    for i, key in enumerate(('main', 'decor_1')):
                        image = Image.new('RGBA', (64, 64)); image.putdata(weights[i]); save(image, directory / ('tone_blend_' + key + '.png'))
                        image = Image.new('RGBA', (64, 64)); image.putdata(masks[i]); save(image, directory / ('tone_mask_' + key + '.png'))
                        variant['masks'][key] = 'tone_mask_' + key + '.png'
                    image = Image.new('RGBA', (64, 64)); image.putdata(neutral); save(image, directory / 'tone_neutral.png')
                    variant['gradient_blending'] = {'neutral': 'tone_neutral.png', 'masks': {k: 'tone_blend_' + k + '.png' for k in ('main', 'decor_1')}}
                    if dark_reference is None:
                        dark_pixels = [(r, g, b) for r, g, b, a in pixels if a and r * .2126 + g * .7152 + b * .0722 <= low]
                        dark_reference = '#%02X%02X%02X' % tuple(round(sum(p[i] for p in dark_pixels) / len(dark_pixels)) for i in range(3))
                control = copy.deepcopy(entry['controls']['main'])
                control['label_ru'] = 'Оттенок волос'; control['affects_ru'] = 'Тени и более тёмные пряди'; control['palette'][0]['rgb'] = dark_reference
                entry['controls']['decor_1'] = control
                entry['gradient'] = True
                entry['generated_shade_maps'] = True
                entry['preferred_recolor_mode'] = 'weighted_gradient'
                entry['disabled_controls'] = [k for k in entry['disabled_controls'] if k != 'decor_1']
        (root / 'appearance/hair_v2.json').write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n')
        (root / 'appearance/hair_v2_verification.json').write_text(json.dumps(verification, indent=2) + '\n')
        (root / 'appearance/hair_v2_credits.md').write_text(archive.read(prefix + 'CREDITS.md').decode() + '\nArchive SHA-256: ' + hashlib.sha256(Path(sys.argv[1]).read_bytes()).hexdigest() + '\n')
    print('Imported', len(catalog['entries']), 'styles; authored originals retained byte for byte')


if __name__ == '__main__':
    main()
