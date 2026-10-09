"""Collect a reviewed candidate locally and verify every packaged file. Never distributes."""

import argparse
import hashlib
import json
import os
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

from check_play_bundle import audit_bundle, audit_config, audit_manifest

MATERIALS = (
    "docs/play-release-notes-en-US.txt",
    "docs/play-release-readiness.md",
    "docs/play-console-submission.md",
    "docs/play-internal-test-results.csv",
    "docs/privacy-policy.md",
    "docs/store/icon-512.png",
    "docs/store/feature-graphic-1024x500.png",
    "docs/store/screenshots/README.md",
    "docs/store/screenshots/01-scan.png",
    "docs/store/screenshots/02-puzzle.png",
    "docs/store/screenshots/03-check.png",
    "docs/store/screenshots/04-hint.png",
    "docs/store/screenshots/05-history.png",
)
EVIDENCE = (
    "manifest.xml", "bundle-config.json", "bundle-validation.txt", "bundle-audit.txt",
    "signature-verification.txt", "upload-certificate.txt",
)
OPTIONAL_EVIDENCE = (
    "build-log.txt", "test-summary.json", "lint-summary.json", "device-tests.md",
    "generated-apks.txt", "generated-apk-signature.txt", "generated-apk-alignment.txt",
    "source-before-build.json",
)
INDEX = "SHA256SUMS.json"


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def contained_file(root: Path, relative: str) -> Path:
    candidate = root / relative
    if not candidate.resolve().is_relative_to(root.resolve()) or not candidate.is_file():
        raise ValueError(f"Missing or out-of-folder material: {relative}")
    if candidate.is_symlink():
        raise ValueError(f"Symlinked material is not allowed: {relative}")
    return candidate


def source_state(root: Path) -> dict:
    def git(*arguments: str) -> str:
        return subprocess.run(["git", *arguments], cwd=root, text=True, encoding="utf-8",
                              capture_output=True, check=True).stdout.rstrip("\r\n")
    status = git("status", "--porcelain=v1", "--untracked-files=normal")
    names = set(git("ls-files", "--cached", "--others", "--exclude-standard", "-z").split("\0")) - {""}
    fingerprints = source_fingerprints(root, names)
    provenance = {
        "commit": git("rev-parse", "HEAD"), "dirty": bool(status),
        "identity_note": "The commit alone does not identify a dirty build. File hashes describe the scoped local source at packaging time.",
        "source_files_sha256": fingerprints,
        "source_snapshot_sha256": hashlib.sha256(json.dumps(fingerprints, sort_keys=True).encode()).hexdigest(),
        "source_scope": "App/core sources and resources, Gradle configuration/wrapper, workflow and release/training scripts. Excludes credentials, private corpus/feedback, build outputs and untracked ignored files. Deleted tracked files have null hashes.",
    }
    if os.environ.get("GITHUB_RUN_ID") and os.environ.get("GITHUB_REPOSITORY"):
        provenance["run_url"] = (os.environ.get("GITHUB_SERVER_URL", "https://github.com") + "/" +
                                 os.environ["GITHUB_REPOSITORY"] + "/actions/runs/" + os.environ["GITHUB_RUN_ID"])
    return provenance


def source_fingerprints(root: Path, names: set[str]) -> dict:
    top_level = {"build.gradle.kts", "settings.gradle.kts", "gradle.properties",
                 "gradlew", "gradlew.bat", ".gitattributes", ".gitignore"}
    fingerprints = {}
    for name in sorted(names):
        path = Path(name)
        if path.is_absolute() or ".." in path.parts:
            raise ValueError("Invalid repository-relative source path")
        lower = name.lower()
        if any(word in lower for word in ("password", "credential", "secret")) or path.suffix.lower() in {
                ".jks", ".keystore", ".p12", ".pfx", ".pem", ".key"}:
            continue
        wanted = (name in top_level or name.startswith(("app/src/", "core/", "gradle/", ".github/workflows/"))
                  or name == "app/build.gradle.kts"
                  or name.startswith(("tools/release/", "tools/recognizer/")) and path.suffix == ".py")
        if not wanted or any(part in lower.split("/") for part in ("build", ".gradle", "corpus", "feedback")):
            continue
        file = root / name
        if file.is_symlink() or not file.resolve().is_relative_to(root.resolve()):
            raise ValueError(f"Source file escapes the checkout: {name}")
        fingerprints[name] = digest(file) if file.is_file() else None
    return fingerprints


def package_candidate(source: Path, bundle: Path, evidence: Path, output: Path,
                      version_code: int, provenance: dict) -> dict:
    if not 0 < version_code <= 2_100_000_000:
        raise ValueError("Invalid Play version code")
    if output.exists():
        raise ValueError("Output already exists; use a new folder to preserve prior candidates")
    if not bundle.is_file() or bundle.is_symlink():
        raise ValueError("Candidate bundle is missing or symlinked")
    # Explicit files only: raw screenshots, corpus photographs and credentials must
    # never enter a handoff merely because they were added under docs/store later.
    files = {name.removeprefix("docs/"): contained_file(source, name) for name in MATERIALS}
    files.update({"evidence/" + name: contained_file(evidence, name) for name in EVIDENCE})
    files.update({"evidence/" + name: contained_file(evidence, name)
                  for name in OPTIONAL_EVIDENCE if (evidence / name).exists()})
    files["app-release.aab"] = bundle
    # Validate metadata and native payload again at the handoff boundary. Manifest
    # and config must be bundletool dumps of this exact bundle, as in the CI workflow.
    audit_manifest((evidence / "manifest.xml").read_text(encoding="utf-8-sig"), version_code)
    audit_config(json.loads((evidence / "bundle-config.json").read_text(encoding="utf-8-sig")))
    audit_bundle(bundle)
    for origin in files.values():
        if origin.resolve().is_relative_to(output.resolve()):
            raise ValueError("The output folder must not contain a source artifact")
    output.mkdir(parents=True, exist_ok=False)
    for name, origin in files.items():
        destination = output / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(origin, destination)
    record = {
        "status": "NOT APPROVED FOR DISTRIBUTION",
        "prepared_at_utc": datetime.now(timezone.utc).isoformat(),
        "package": "org.freevia.sudokubuddy", "version_name": "1.0.0",
        "version_code": version_code, "source": provenance,
        "bundle_sha256": digest(output / "app-release.aab"),
        "qualification": "Static metadata/native checks passed. Supplied signing evidence, device tests and Play approval need separate qualification.",
    }
    (output / "release-record.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    hashes = {path.relative_to(output).as_posix(): digest(path)
              for path in sorted(output.rglob("*")) if path.is_file()}
    (output / INDEX).write_text(json.dumps(hashes, indent=2) + "\n", encoding="utf-8")
    verify_package(output)
    return record


def verify_package(folder: Path) -> None:
    hashes = json.loads(contained_file(folder, INDEX).read_text(encoding="utf-8"))
    if not isinstance(hashes, dict) or "app-release.aab" not in hashes or "release-record.json" not in hashes:
        raise ValueError("Incomplete checksum inventory")
    actual = {path.relative_to(folder).as_posix() for path in folder.rglob("*") if path.is_file()}
    if actual != set(hashes) | {INDEX}:
        raise ValueError("Package contains missing or unlisted files")
    for name, expected in hashes.items():
        if digest(contained_file(folder, name)) != expected:
            raise ValueError(f"Checksum mismatch: {name}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prepare = commands.add_parser("prepare")
    prepare.add_argument("--source", type=Path, default=Path(__file__).resolve().parents[2])
    prepare.add_argument("--bundle", type=Path, required=True)
    prepare.add_argument("--evidence", type=Path, required=True)
    prepare.add_argument("--output", type=Path, required=True)
    prepare.add_argument("--version-code", type=int, required=True)
    verify = commands.add_parser("verify")
    verify.add_argument("folder", type=Path)
    snapshot = commands.add_parser("source")
    snapshot.add_argument("--source", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    if args.command == "prepare":
        record = package_candidate(args.source, args.bundle, args.evidence, args.output,
                                   args.version_code, source_state(args.source))
        print(f"Prepared {args.output}; AAB SHA-256 {record['bundle_sha256']}; NOT APPROVED FOR DISTRIBUTION")
    elif args.command == "verify":
        verify_package(args.folder)
        print("Every packaged file matches its checksum inventory")
    else:
        print(json.dumps(source_state(args.source), indent=2))


if __name__ == "__main__":
    main()
