"""Publish exact source trees using the active session's GitHub connector.

Authentication remains in the hosted-app runtime, outside this repository.
API commits have their own IDs; original local IDs are recorded in messages.
"""
import base64
import importlib.util
import json
from pathlib import Path
import re
import subprocess
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading
import time
from urllib.parse import quote


class GitHubBackupError(Exception):
    pass


def payload(result):
    if result.get("isError"):
        raise GitHubBackupError("GitHub rejected the operation")
    value = result.get("structuredContent")
    if value is None:
        blocks = [b["text"] for b in result.get("content", []) if b.get("type") == "text"]
        for text in blocks:
            try:
                value = json.loads(text)
                break
            except ValueError:
                continue
    # fetch wraps the endpoint's JSON in a UTF-8 content field.
    if isinstance(value, dict) and isinstance(value.get("content"), str):
        value = json.loads(value["content"])
    if not isinstance(value, (dict, list)):
        raise GitHubBackupError("GitHub returned an unexpected response")
    return value


class GitHubBackup:
    def __init__(self, root, config, call=None):
        self.root = Path(root)
        self.repo = config["repository_full_name"]
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", self.repo):
            raise GitHubBackupError("Invalid GitHub repository")
        if call is None:
            sdk = Path(config["sdk_path"])
            if not sdk.is_file() or sdk.resolve().is_relative_to(self.root.resolve()):
                raise GitHubBackupError("The active-session SDK must exist outside the source repository")
            spec = importlib.util.spec_from_file_location("civitatum_hosted_apps", sdk)
            module = importlib.util.module_from_spec(spec)
            import sys
            sys.modules[spec.name] = module
            spec.loader.exec_module(module)
            client = module.HostedAppsClient()
            connector = config["connector_id"]
            call = lambda name, args: client.call_tool(connector, name, args)
        self.call = call

    def api(self, name, **args):
        try:
            return payload(self.call(name, args))
        except GitHubBackupError:
            raise
        except Exception as ex:
            # Hosted errors can contain authentication or URLs. Keep them private.
            raise GitHubBackupError("GitHub " + name + " failed; remote backup is unconfirmed") from ex

    def fetch(self, suffix):
        return self.api("fetch", url="https://api.github.com/repos/" + self.repo + "/" + suffix)

    def refs(self):
        items = self.fetch("git/refs")
        if not isinstance(items, list):
            raise GitHubBackupError("Cannot inspect GitHub branches")
        return {item["ref"]: item["object"]["sha"] for item in items}

    def tree(self, sha):
        value = self.fetch("git/trees/" + quote(sha, safe="") + "?recursive=1")
        if value.get("truncated"):
            raise GitHubBackupError("GitHub tree is incomplete")
        return value

    def git_bytes(self, *args):
        p = subprocess.run(["git", *args], cwd=self.root, capture_output=True, check=False)
        if p.returncode:
            raise GitHubBackupError("Cannot read the local source snapshot")
        return p.stdout

    def local_tree(self, commit):
        return self.git_bytes("rev-parse", commit + "^{tree}").decode().strip()

    def upload_tree(self, commit, known_blobs=(), known_tree_shas=()):
        expected_tree = self.local_tree(commit)
        known = set(known_blobs)
        cache_path = self.root / ".git/civitatum-backup/uploaded-blobs.json"
        cache = json.loads(cache_path.read_text()) if cache_path.exists() else {}
        verified = set(cache.get(self.repo, []))
        known.update(verified)
        binary = {}
        for row in self.git_bytes("ls-tree", "-r", "-z", commit).split(b"\0"):
            if not row:
                continue
            metadata, _ = row.split(b"\t", 1)
            mode, kind, sha = metadata.decode().split()
            if kind != "blob" or mode not in ("100644", "100755", "120000"):
                raise GitHubBackupError("Unsupported source-tree entry")
            if sha not in known:
                binary[sha] = self.git_bytes("cat-file", "blob", sha)
        # Git blobs are immutable and content addressed. Upload each unique binary once;
        # only the final, verified branch update mutates a ref, and it remains sequential.
        guard = threading.Lock()
        def upload(item):
            sha, data = item
            for attempt in range(3):
                try:
                    result = self.api("create_blob", repository_full_name=self.repo,
                                      content=base64.b64encode(data).decode(), encoding="base64")
                    break
                except GitHubBackupError:
                    if attempt == 2:
                        raise
                    time.sleep(2 ** attempt)
            if result.get("sha") != sha:
                raise GitHubBackupError("Binary source verification failed")
            with guard:
                verified.add(sha)
                cache[self.repo] = sorted(verified)
                temporary = cache_path.with_suffix(".tmp")
                temporary.write_text(json.dumps(cache))
                temporary.replace(cache_path)
            return sha
        with ThreadPoolExecutor(max_workers=8) as pool:
            futures = [pool.submit(upload, item) for item in binary.items()]
            for future in as_completed(futures):
                future.result()
        # Build one directory at a time. A flat tree with thousands of appearance assets
        # exceeds the connector request limit; unchanged directory objects need no upload.
        known_trees = set(known_tree_shas)
        def directory(sha):
            if sha in known_trees:
                return
            entries = []
            for row in self.git_bytes("ls-tree", "-z", sha).split(b"\0"):
                if not row:
                    continue
                metadata, name = row.split(b"\t", 1)
                mode, kind, child = metadata.decode().split()
                if kind == "tree":
                    directory(child)
                entries.append({"path": name.decode("utf-8"), "mode": mode, "type": kind, "sha": child})
            uploaded = self.api("create_tree", repository_full_name=self.repo, tree_elements=entries)
            if uploaded.get("sha") != sha:
                raise GitHubBackupError("Directory verification failed; no branch was changed")
            known_trees.add(sha)
        directory(expected_tree)
        return expected_tree

    def publish(self, commit, branch, previous_remote=None, base_branch="main", message=None):
        self.git_bytes("check-ref-format", "refs/heads/" + branch)
        ref = "refs/heads/" + branch
        refs = self.refs()
        head = refs.get(ref)
        if previous_remote and head != previous_remote:
            raise GitHubBackupError("The remote branch changed; no history was overwritten")
        source_tree = self.local_tree(commit)
        if head:
            remote_commit = self.fetch("git/commits/" + head)
            old_tree = remote_commit["tree"]["sha"]
            if old_tree == source_tree:
                return {"remote_commit": head, "source_tree": source_tree}
            if previous_remote is None:
                raise GitHubBackupError("An existing branch has different sources; inspect it before saving")
            parent = head
        else:
            parent = refs.get("refs/heads/" + base_branch)
            if not parent:
                raise GitHubBackupError("The backup needs an existing base branch")
            old_tree = self.fetch("git/commits/" + parent)["tree"]["sha"]
        old = self.tree(old_tree)
        known = [entry["sha"] for entry in old["tree"] if entry["type"] == "blob"]
        tree = self.upload_tree(commit, known, [old_tree] + [entry["sha"] for entry in old["tree"] if entry["type"] == "tree"])
        label = message or "Source checkpoint"
        created = self.api("create_commit", repository_full_name=self.repo,
                           parent_sha=parent, tree_sha=tree,
                           message=label + "\n\nLocal source commit: " + commit + "\nSource tree: " + tree)
        new_head = created["sha"]
        if head:
            self.api("update_ref", repository_full_name=self.repo, branch_name=branch,
                     sha=new_head, expected_sha=head, force=False)
        else:
            self.api("create_branch", repository_full_name=self.repo, branch_name=branch, sha=new_head)
        if self.refs().get(ref) != new_head:
            raise GitHubBackupError("GitHub branch verification failed")
        actual = self.fetch("git/commits/" + new_head)
        if actual["tree"]["sha"] != source_tree:
            raise GitHubBackupError("GitHub source verification failed")
        return {"remote_commit": new_head, "source_tree": source_tree}
