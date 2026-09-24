#!/usr/bin/env python3
"""Verify the local SkillHive built-in import against its pinned manifest and files."""

import hashlib
import json
import subprocess
import sys
from pathlib import Path


project_root = Path(__file__).resolve().parents[1]
storage_root = Path.home().joinpath("skillhive-data", "storage").resolve()
manifest_path = project_root / "server/skillhub-app/src/main/resources/builtin-skills/manifest.json"


def query(sql: str) -> list[str]:
    result = subprocess.run(
        [
            "docker", "exec", "skillhive-postgres-1", "psql", "-U", "skillhub",
            "-d", "skillhub", "-At", "-F", "|", "-c", sql,
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.splitlines()


manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
expected = {(item["slug"], item["version"]) for item in manifest["skills"]}
actual_rows = query(
    "SELECT s.slug, v.version, v.status FROM skill s "
    "JOIN skill_version v ON v.skill_id = s.id ORDER BY s.slug"
)
actual = set()
failures = []
for row in actual_rows:
    slug, version, status = row.split("|", 2)
    actual.add((slug, version))
    if status != "PUBLISHED":
        failures.append(f"{slug}@{version}: status={status}")

for missing in sorted(expected - actual):
    failures.append(f"missing skill: {missing[0]}@{missing[1]}")
for extra in sorted(actual - expected):
    failures.append(f"unexpected skill: {extra[0]}@{extra[1]}")

file_rows = query("SELECT storage_key, sha256 FROM skill_file ORDER BY id")
for row in file_rows:
    key, expected_hash = row.split("|", 1)
    path = storage_root.joinpath(key).resolve()
    if not path.is_relative_to(storage_root) or not path.is_file():
        failures.append(f"missing or invalid file: {key}")
        continue
    actual_hash = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual_hash != expected_hash:
        failures.append(f"checksum mismatch: {key}")

print(f"Skills: {len(actual)}/{len(expected)}; files verified: {len(file_rows) - len([f for f in failures if 'file:' in f or 'mismatch:' in f])}/{len(file_rows)}")
for failure in failures:
    print(f"ERROR: {failure}", file=sys.stderr)
sys.exit(1 if failures else 0)
