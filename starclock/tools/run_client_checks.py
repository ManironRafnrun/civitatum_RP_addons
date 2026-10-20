"""Запускает настоящий клиент под Xvfb с Punchy и без него и проверяет отчёты."""
import json
import os
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "starclock"
RUN = PROJECT / "build" / "clientCheckRun"
REPORTS = PROJECT / "build" / "clientCheckReports"


def check(mode):
    report = RUN / "starclock-client-check.json"
    report.unlink(missing_ok=True)
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
    if not content["success"] or len(content["completed"]) != 24 or content["failures"]:
        raise RuntimeError(f"Проверка {mode} не прошла: {content}")
    expected_punchy = mode == "with-punchy"
    if content["punchy"] != expected_punchy:
        raise RuntimeError(f"Неверный набор модов для {mode}")
    print(f"CLIENT_CHECK_OK {mode}: 24 инструмента/руки", flush=True)


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
