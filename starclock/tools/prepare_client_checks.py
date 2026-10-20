"""Готовит проверку клиента: мир GameTest и неизменённые зависимости Punchy из Modrinth."""
import argparse
import hashlib
import json
import shutil
import subprocess
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

API = "https://api.modrinth.com/v2/"
HEADERS = {"User-Agent": "Civitatum-Starclock/0.1.0 (client compatibility test)"}


def request(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=90) as response:
        return response.read()


def versions(project):
    query = urllib.parse.urlencode({"game_versions": '["1.21.1"]', "loaders": '["neoforge"]'})
    return json.loads(request(API + "project/" + project + "/version?" + query))


def prepare(root):
    run = root / "build" / "clientCheckRun"
    world = root / "build" / "gameTestRun" / "world"
    if not (world / "level.dat").is_file():
        raise RuntimeError("Сначала выполните :starclock:runGameTestServer")
    destination = run / "saves" / "StarclockCheck"
    if destination.exists():
        shutil.rmtree(destination)
    shutil.copytree(world, destination)
    run.mkdir(parents=True, exist_ok=True)
    (run / "options.txt").write_text("tutorialStep:none\nlang:ru_ru\nfov:0.0\nrenderDistance:2\nsimulationDistance:2\nmaxFps:60\n", encoding="utf-8")
    mods = run / "mods"
    mods.mkdir(exist_ok=True)
    manifest = []
    visited = {"8BmcQJ2H", "geckolib"}

    def install(version):
        project = version["project_id"]
        if project in visited:
            return
        visited.add(project)
        binary = next((entry for entry in version["files"] if entry["primary"]), version["files"][0])
        payload = request(binary["url"])
        if hashlib.sha512(payload).hexdigest() != binary["hashes"]["sha512"]:
            raise RuntimeError("Не совпала SHA-512: " + binary["filename"])
        target = mods / binary["filename"]
        target.write_bytes(payload)
        manifest.append({"project": project, "version": version["id"],
                         "filename": binary["filename"], "sha512": binary["hashes"]["sha512"]})
        for dependency in version["dependencies"]:
            if dependency["dependency_type"] != "required":
                continue
            dependency_id = dependency.get("project_id")
            if dependency_id in visited:
                continue
            if dependency.get("version_id"):
                candidate = json.loads(request(API + "version/" + dependency["version_id"]))
            else:
                choices = versions(dependency_id)
                if not choices:
                    raise RuntimeError("Нет зависимости NeoForge 1.21.1: " + str(dependency_id))
                candidate = choices[0]
            install(candidate)

    choices = versions("punchy-fpa")
    version = next((entry for entry in choices if entry["version_number"] == "2.6.2"), None)
    if version is None:
        raise RuntimeError("В Modrinth нет Punchy 2.6.2 для NeoForge 1.21.1")
    install(version)
    (run / "dependency-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    for jar in mods.glob("*punchy*.jar"):
        with zipfile.ZipFile(jar) as archive:
            classes = [name[:-6].replace("/", ".") for name in archive.namelist()
                       if name.endswith("Config.class") and not name.startswith("META-INF/")]
            print("PUNCHY_CLASSES", classes, flush=True)
        for name in classes:
            result = subprocess.run(["javap", "-classpath", str(jar), "-p", name],
                                    text=True, capture_output=True, check=True)
            print(result.stdout, flush=True)
    print("CLIENT_CHECK_DEPENDENCIES", json.dumps(manifest), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Подготовка тестового клиента")
    parser.add_argument("--project", type=Path, required=True)
    prepare(parser.parse_args().project.resolve())
