#!/usr/bin/env python3
"""Source checkpoints on a separate ref; the normal Git index stays untouched."""
import argparse
import contextlib
import datetime as dt
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
BRANCH = "codex/checkpoints/ccc"
SOURCE_PATHS = (
    "src", "art", "gradle", "tools", "build.gradle", "settings.gradle",
    "gradle.properties", "gradlew", "gradlew.bat", "LICENSE.txt", "NOTICE.txt", "TEMPLATE_LICENSE.txt",
    "README.md", "README_RU.md", "VALIDATION_RU.md", "BACKUP_RU.md", "AGENTS.md",
    ".gitignore", ".gitattributes",
)
INTERVAL = 600
LEASE = 1200


class BackupError(Exception):
    pass


def git(*args, env=None, timeout=45, check=True):
    execution_env = dict(os.environ, GIT_TERMINAL_PROMPT="0",
                         GIT_SSH_COMMAND="ssh -o BatchMode=yes -o ConnectTimeout=15")
    if env:
        execution_env.update(env)
    try:
        p = subprocess.run(["git", *args], cwd=ROOT, env=execution_env,
                           capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired as ex:
        raise BackupError(f"Git {args[0]} timed out; remote backup is unconfirmed") from ex
    if check and p.returncode:
        # Git errors can echo credential-bearing remote URLs. Do not print them.
        reason = "; remote backup is unconfirmed"
        if "non-fast-forward" in p.stderr or "fetch first" in p.stderr:
            reason = "; the remote branch changed; no history was overwritten"
        raise BackupError(f"Git {args[0]} failed (exit {p.returncode})" + reason)
    return p


def state_dir():
    p = Path(git("rev-parse", "--absolute-git-dir").stdout.strip()) / "civitatum-backup"
    p.mkdir(exist_ok=True)
    return p


def save_json(path, data):
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    os.replace(tmp, path)


@contextlib.contextmanager
def lock(directory, name="checkpoint.lock"):
    with (directory / name).open("a+b") as f:
        try:
            if os.name == "nt":
                import msvcrt
                f.seek(0)
                f.write(b"0")
                f.flush()
                f.seek(0)
                msvcrt.locking(f.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as ex:
            raise BackupError("Another checkpoint is running") from ex
        try:
            yield
        finally:
            if os.name == "nt":
                f.seek(0)
                msvcrt.locking(f.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(f, fcntl.LOCK_UN)


def checkpoint(remote="origin", branch=BRANCH, local_only=False):
    ref = "refs/heads/" + branch
    git("check-ref-format", ref)
    directory = state_dir()
    with lock(directory):
        head = git("rev-parse", "HEAD").stdout.strip()
        existing = git("show-ref", "--verify", "--hash", ref, check=False)
        previous = existing.stdout.strip() if existing.returncode == 0 else head
        # A separate index includes only approved source paths, even if the user
        # has unrelated staged files. A checkpoint never commits on their branch.
        with tempfile.TemporaryDirectory(prefix="index-", dir=directory) as tmp:
            env = {"GIT_INDEX_FILE": str(Path(tmp) / "index")}
            git("read-tree", head, env=env)
            tracked = git("ls-files", env=env).stdout.splitlines()
            paths = [p for p in SOURCE_PATHS if (ROOT / p).exists() or
                     any(n == p or n.startswith(p + "/") for n in tracked)]
            git("add", "-A", "--", *paths, env=env)
            tree = git("write-tree", env=env).stdout.strip()
        old_tree = git("rev-parse", previous + "^{tree}").stdout.strip()
        ancestor = git("merge-base", "--is-ancestor", head, previous, check=False)
        if tree != old_tree or ancestor.returncode != 0:
            parents = ["-p", previous]
            if ancestor.returncode != 0 and head != previous:
                parents += ["-p", head]
            timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
            commit = git("-c", "commit.gpgsign=false", "commit-tree", tree, *parents,
                         "-m", "Source checkpoint " + timestamp).stdout.strip()
        else:
            commit = previous
        git("update-ref", ref, commit, existing.stdout.strip() if existing.returncode == 0 else "")
        record = {"branch": branch, "commit": commit, "attempt_at": time.time(),
                  "remote_verified": False, "mode": "local" if local_only else "remote"}
        status_path = directory / "status.json"
        if status_path.exists():
            old = json.loads(status_path.read_text())
            for key in ("last_verified_commit", "last_verified_at"):
                if key in old:
                    record[key] = old[key]
        save_json(directory / "status.json", record)
        print("LOCAL_CHECKPOINT", commit, flush=True)
        if local_only:
            return record
        git("remote", "get-url", remote)
        config_path = directory / "github-config.json"
        if config_path.exists():
            from github_checkpoint import GitHubBackup, GitHubBackupError
            config = json.loads(config_path.read_text())
            expected_url = "https://github.com/" + config["repository_full_name"] + ".git"
            if git("remote", "get-url", remote).stdout.strip() != expected_url:
                raise BackupError("The GitHub connector configuration belongs to a different remote")
            api_status_path = directory / "github-branches.json"
            api_status = json.loads(api_status_path.read_text()) if api_status_path.exists() else {}
            previous_remote = api_status.get(branch, {}).get("remote_commit")
            try:
                result = GitHubBackup(ROOT, config).publish(
                    commit, branch, previous_remote, config.get("base_branch", "main"))
            except GitHubBackupError as ex:
                raise BackupError(str(ex)) from ex
            api_status[branch] = dict(result, local_commit=commit)
            save_json(api_status_path, api_status)
            record.update(result, remote=remote, transport="github_connector", remote_verified=True,
                          pushed_at=time.time(), last_verified_commit=commit, last_verified_at=time.time())
            save_json(status_path, record)
            print("REMOTE_VERIFIED", result["remote_commit"], "SOURCE", commit, flush=True)
            return record
        # No force push, no --all, no rewriting releases or the development branch.
        git("push", "--porcelain", remote, ref + ":" + ref)
        response = git("ls-remote", "--heads", remote, ref).stdout.split()
        if len(response) != 2 or response[0] != commit or response[1] != ref:
            raise BackupError("Remote commit verification failed")
        record.update(remote=remote, remote_verified=True, pushed_at=time.time(),
                      last_verified_commit=commit, last_verified_at=time.time())
        save_json(directory / "status.json", record)
        print("REMOTE_VERIFIED", commit, flush=True)
        return record


def heartbeat():
    save_json(state_dir() / "activity.json", {"expires_at": time.time() + LEASE})
    print("ACTIVITY_RENEWED", flush=True)


def watch(remote, branch):
    directory = state_dir()
    with lock(directory, "watcher.lock"):
        watch_locked(directory, remote, branch)


def watch_locked(directory, remote, branch):
    (directory / "stop").unlink(missing_ok=True)
    heartbeat()
    try:
        checkpoint(remote, branch)
    except BackupError:
        # Do not start a timer whose first upload is already unverified.
        raise
    save_json(directory / "watch.json", {"pid": os.getpid(), "interval_seconds": INTERVAL})
    due = time.monotonic() + INTERVAL
    try:
        while not (directory / "stop").exists():
            if time.monotonic() >= due:
                lease = json.loads((directory / "activity.json").read_text())
                if time.time() < lease["expires_at"]:
                    try:
                        checkpoint(remote, branch)
                    except BackupError as ex:
                        print("BACKUP_FAILED", ex, file=sys.stderr, flush=True)
                due += INTERVAL
                while due <= time.monotonic():
                    due += INTERVAL
            time.sleep(1)
    finally:
        (directory / "watch.json").unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["once", "watch", "heartbeat", "status", "stop"])
    parser.add_argument("--remote", default="origin")
    parser.add_argument("--branch", default=BRANCH)
    parser.add_argument("--local-only", action="store_true", help="Only for setup/testing; no external protection")
    args = parser.parse_args()
    if args.local_only and args.command != "once":
        parser.error("--local-only is allowed only with once")
    try:
        if args.command == "once":
            checkpoint(args.remote, args.branch, args.local_only)
        elif args.command == "watch":
            watch(args.remote, args.branch)
        elif args.command == "heartbeat":
            heartbeat()
        elif args.command == "stop":
            (state_dir() / "stop").write_text("stop\n")
            print("STOP_REQUESTED")
        else:
            p = state_dir() / "status.json"
            print(p.read_text() if p.exists() else "NO_CHECKPOINT_YET")
    except (BackupError, OSError, ValueError) as ex:
        print("BACKUP_FAILED", ex, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
