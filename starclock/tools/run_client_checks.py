"""Запускает настоящий клиент под Xvfb с Punchy и без него и проверяет отчёты."""
from make_preview_video import generate as make_video
import base64
import io
import json
from PIL import Image
import os
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "starclock"
RUN = PROJECT / "build" / "clientCheckRun"
REPORTS = PROJECT / "build" / "clientCheckReports"


def check(mode):
    options = RUN / "options.txt"
    content = options.read_text(encoding="utf-8")
    options.write_text(content.replace("lang:ru_ru", "lang:en_us") if mode == "with-punchy"
                       else content.replace("lang:en_us", "lang:ru_ru"), encoding="utf-8")
    report = RUN / "starclock-client-check.json"
    report.unlink(missing_ok=True)
    shutil.rmtree(RUN / "starclock-frames", ignore_errors=True)
    result = subprocess.run(["xvfb-run", "-a", "./gradlew", ":starclock:runClientCheck",
                             "-PstarclockClientCheck", "--console=plain"],
                            cwd=ROOT, env={**os.environ, "LIBGL_ALWAYS_SOFTWARE": "1",
                                          "ALSOFT_DRIVERS": "null"}, timeout=480)
    destination = REPORTS / mode
    destination.mkdir(parents=True, exist_ok=True)
    for name in ("logs", "screenshots", "crash-reports"):
        if (RUN / name).exists():
            shutil.copytree(RUN / name, destination / name, dirs_exist_ok=True)
    if report.exists():
        shutil.copy2(report, destination / report.name)
    if result.returncode != 0:
        raise RuntimeError(f"Клиент {mode} завершился с кодом {result.returncode}")
    if not report.exists():
        raise RuntimeError(f"Клиент {mode} не завершил проверку")
    content = json.loads(report.read_text(encoding="utf-8"))
    if not content["success"] or len(content["completed"]) != 24 or content["screens"] != 24 or content["failures"]:
        raise RuntimeError(f"Проверка {mode} не прошла: {content}")
    expected_punchy = mode == "with-punchy"
    if content["punchy"] != expected_punchy:
        raise RuntimeError(f"Неверный набор модов для {mode}")
    print(f"CLIENT_CHECK_OK {mode}: 24 инструмента/руки", flush=True)
    if mode == "without-punchy":
        sheet = Image.new("RGB", (640, 428), "#0B1020")
        for index, case in enumerate((0, 6, 12, 18)):
            source = RUN / "screenshots" / f"starclock-{case}.png"
            with Image.open(source) as screenshot:
                panel = screenshot.convert("RGB").resize((320, 214), Image.Resampling.NEAREST)
                sheet.paste(panel, ((index % 2) * 320, (index // 2) * 214))
        sheet.save(destination / "preview.png", compress_level=9)
        cards = Image.new("RGB", (960, 640), "#0B1020")
        for index, case in enumerate((0, 1, 12, 22)):
            with Image.open(RUN / "screenshots" / f"starclock-card-{case}.png") as screenshot:
                cards.paste(screenshot.convert("RGB").resize((480,320)),((index%2)*480,(index//2)*320))
        cards.save(destination / "cards.png",compress_level=9)
        encoded = io.BytesIO()
        cards.save(encoded,format="PNG",compress_level=9)
        print("CLIENT_CARD_PREVIEW_BASE64 "+base64.b64encode(encoded.getvalue()).decode(),flush=True)
        payload = io.BytesIO()
        sheet.save(payload, format="PNG", compress_level=9)
        print("CLIENT_CHECK_PREVIEW_BASE64 " + base64.b64encode(payload.getvalue()).decode("ascii"), flush=True)
        make_video(RUN / "starclock-frames", REPORTS / "video")


def main():
    mods = RUN / "mods"
    disabled = PROJECT / "build" / "clientCheckDependencies"
    disabled.mkdir(parents=True, exist_ok=True)
    jars = list(mods.glob("*.jar"))
    if not jars:
        raise RuntimeError("Сначала запустите tools/prepare_client_checks.py")
    for jar in jars:
        shutil.move(str(jar), disabled / jar.name)
    try:
        check("without-punchy")
    finally:
        for jar in disabled.glob("*.jar"):
            shutil.move(str(jar), mods / jar.name)
    check("with-punchy")


if __name__ == "__main__":
    main()
