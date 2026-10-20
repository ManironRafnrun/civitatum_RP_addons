"""Собирает MP4 из кадров настоящего клиента, снятых тестовым набором."""
import argparse
import base64
import json
import subprocess
import tempfile
from pathlib import Path
import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "starclock"
LABELS = {"deck": "Колода", "sextant": "Секстант", "bowl": "Чаша", "ring": "Кольцо"}


def run(command):
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-hide_banner", "-loglevel", "error", "-y", *command], check=True)


def generate(frames, output):
    output.mkdir(parents=True, exist_ok=True)
    audio = PROJECT / "build/generated/starclockAudio/assets/starclock/sounds/ritual"
    clips = []
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 17)
    with tempfile.TemporaryDirectory(prefix="starclock-video-") as temporary:
        temp = Path(temporary)
        for kind, label in LABELS.items():
            sources = sorted((frames / kind).glob("*.png"))
            if len(sources) < 100:
                raise ValueError(f"Недостаточно кадров {kind}: {len(sources)}")
            prepared = temp / kind
            prepared.mkdir()
            for index, source in enumerate(sources):
                with Image.open(source) as image:
                    image = image.convert("RGB").resize((640,426), Image.Resampling.LANCZOS)
                draw = ImageDraw.Draw(image)
                draw.rectangle((0,0,640,30),fill="#0B1020")
                draw.text((12,4),label,fill="#E3C07B",font=font)
                image.save(prepared / f"{index:04d}.png",compress_level=1)
            animation = json.loads((PROJECT / f"src/main/resources/assets/starclock/animations/{kind}.animation.json").read_text())
            effects = next(iter(animation["animations"].values())).get("sound_effects", {})
            cues = []
            for time, effect in effects.items():
                for item in effect if isinstance(effect,list) else [effect]:
                    name = item["effect"].split(":")[-1].removeprefix("ritual.")
                    cues.append((float(time), name, 0.5))
            cues.extend([(2.0,"stars_reveal",0.5),(4.35,"card_flip",0.5)])
            if kind != "bowl":  # В записанном примере чаши показан Змей, без положительного/отрицательного сигнала.
                cues.append((4.35,"omen_good",0.4))
            arguments = ["-framerate","20","-i",str(prepared / "%04d.png")]
            filters = []
            for index,(time,name,volume) in enumerate(cues,1):
                arguments.extend(["-i",str(audio / f"{name}.ogg")])
                filters.append(f"[{index}:a]adelay={round(time*1000)}:all=1,volume={volume}[s{index}]")
            labels = "".join(f"[s{index}]" for index in range(1,len(cues)+1))
            filters.append(labels+f"amix=inputs={len(cues)}:normalize=0,alimiter=limit=0.95[mix]")
            clip = temp / f"{kind}.mp4"
            run(arguments+["-filter_complex",";".join(filters),"-map","0:v","-map","[mix]",
                           "-t",str(len(sources)/20),"-c:v","libx264","-preset","slow","-crf","27",
                           "-pix_fmt","yuv420p","-c:a","aac","-b:a","96k",str(clip)])
            clips.append(clip)
        listing = temp / "clips.txt"
        listing.write_text("".join("file '"+clip.as_posix()+"'\n" for clip in clips),encoding="utf-8")
        destination = output / "starclock-rituals.mp4"
        run(["-f","concat","-safe","0","-i",str(listing),"-c","copy","-movflags","+faststart",str(destination)])
        report = {"source":"Кадры настоящего клиента Minecraft; фазы задаёт тестовый набор clientCheck.",
                  "audio":"Оригинальные звуки мода сведены по времени ключевых кадров.",
                  "fps":20,"size":[640,426],"tools":list(LABELS)}
        (output / "video.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
        print("STARClock_VIDEO_BASE64 "+base64.b64encode(destination.read_bytes()).decode(),flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--frames",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    args = parser.parse_args()
    generate(args.frames,args.output)
