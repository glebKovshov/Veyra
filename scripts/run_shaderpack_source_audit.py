#!/usr/bin/env python3
"""Fetch pinned public shaderpack inputs and record Veyra's source-only adapters."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import urllib.request


ROOT = Path(__file__).resolve().parents[1]
INPUTS = ROOT / "validation" / "shaderpack-inputs.json"
PROFILE_MATRIX = ROOT / "validation" / "shaderpack-profile-matrix.json"
DOWNLOADS = ROOT / "build" / "shaderpack-source-audit" / "inputs"
REPORTS = ROOT / "build" / "reports" / "external-shaderpacks"
DIMENSIONS = (0, -1, 1)
MAX_ARCHIVE_BYTES = 512 * 1024 * 1024


def sha512(path: Path) -> str:
    digest = hashlib.sha512()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch_pinned_inputs() -> tuple[list[dict[str, object]], dict[str, Path]]:
    metadata = json.loads(INPUTS.read_text(encoding="utf-8"))
    profiles = json.loads(PROFILE_MATRIX.read_text(encoding="utf-8"))
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    paths: dict[str, Path] = {}
    for entry in metadata:
        filename = entry["filename"]
        url = entry["url"]
        expected = entry["sha512"].lower()
        if Path(filename).name != filename or len(expected) != 128:
            raise ValueError(f"Invalid pinned archive metadata: {filename!r}")
        if not url.startswith("https://cdn.modrinth.com/"):
            raise ValueError(f"Unexpected shaderpack archive host: {url!r}")

        target = DOWNLOADS / filename
        if not target.is_file() or sha512(target) != expected:
            target.unlink(missing_ok=True)
            request = urllib.request.Request(url, headers={"User-Agent": "Veyra-pinned-source-audit/1"})
            temporary = target.with_suffix(target.suffix + ".part")
            digest = hashlib.sha512()
            size = 0
            try:
                with urllib.request.urlopen(request, timeout=90) as response, temporary.open("wb") as output:
                    while chunk := response.read(1024 * 1024):
                        size += len(chunk)
                        if size > MAX_ARCHIVE_BYTES:
                            raise ValueError(f"Archive exceeds the {MAX_ARCHIVE_BYTES}-byte safety limit: {filename}")
                        digest.update(chunk)
                        output.write(chunk)
                actual = digest.hexdigest()
                if actual != expected:
                    raise ValueError(f"SHA-512 mismatch for {filename}: expected {expected}, got {actual}")
                temporary.replace(target)
            finally:
                temporary.unlink(missing_ok=True)
        paths[filename] = target

    return profiles, paths


def audit_arguments(profiles: list[dict[str, object]], paths: dict[str, Path]) -> list[str]:
    args: list[str] = []
    metadata = json.loads(INPUTS.read_text(encoding="utf-8"))
    profile_keys = {
        str(entry["filename"]): str(entry["profile_audit_key"])
        for entry in metadata
        if entry.get("profile_audit_key")
    }
    declared_keys = {str(row["pack"]) for row in profiles}
    unknown_keys = declared_keys - set(profile_keys.values())
    if unknown_keys:
        raise ValueError(f"Profile matrix references packs without a pinned input: {sorted(unknown_keys)}")

    for dimension in DIMENSIONS:
        for filename, archive in paths.items():
            args.extend((f"--dimension={dimension}", "--profile=", str(archive)))
            declared = dict.fromkeys(
                str(row["profile"])
                for row in profiles
                if row["pack"] == profile_keys.get(filename) and row["dimension"] == dimension
            )
            for profile in declared:
                args.extend((f"--dimension={dimension}", f"--profile={profile}", str(archive)))
    return args


def main() -> int:
    REPORTS.mkdir(parents=True, exist_ok=True)
    profiles, paths = fetch_pinned_inputs()
    args = audit_arguments(profiles, paths)
    # Keep paths and all profile arguments out of cmd.exe's 8191-character limit.
    arguments_file = REPORTS / "arguments.json"
    arguments_file.write_text(json.dumps(args), encoding="utf-8")
    wrapper = str(ROOT / "gradlew.bat") if sys.platform == "win32" else "./gradlew"
    command = [wrapper, "--no-daemon", "auditShaderPacks", f"-PshaderPackAuditArguments={arguments_file}"]
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, check=False)
    (REPORTS / "gradle-output.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        print(result.stdout[-12000:], file=sys.stdout)
        print(result.stderr[-12000:], file=sys.stderr)
        return result.returncode

    records: list[dict[str, object]] = []
    for line in result.stdout.splitlines():
        if not line.startswith("{") or '"pack":' not in line:
            continue
        records.append(json.loads(line))

    expected_defaults = len(paths) * len(DIMENSIONS)
    expected_profiles = len(profiles)
    defaults = [row for row in records if not row["profile"]]
    profile_rows = [row for row in records if row["profile"]]
    if len(defaults) != expected_defaults or len(profile_rows) != expected_profiles:
        raise RuntimeError(
            f"Expected {expected_defaults} default and {expected_profiles} profile audit records; "
            f"received {len(defaults)} and {len(profile_rows)}. See {REPORTS / 'gradle-output.txt'}."
        )

    (REPORTS / "shaderpack-source-audit.json").write_text(
        json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    checksums = {name: sha512(path) for name, path in paths.items()}
    (REPORTS / "input-sha512.json").write_text(
        json.dumps(checksums, indent=2) + "\n", encoding="utf-8"
    )
    accepted = sum(int(row["geometryFragmentsAccepted"]) for row in records)
    rejected = sum(int(row["geometryFragmentsRejected"]) for row in records)
    print(
        f"Audited {len(paths)} pinned packs in {len(DIMENSIONS)} dimensions: "
        f"{len(defaults)} defaults and {len(profile_rows)} declared-profile cases; "
        f"{accepted} geometry fragment programs accepted, {rejected} rejected."
    )
    print(f"Reports written under {REPORTS.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
