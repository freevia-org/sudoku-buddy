"""Offline candidate audit. Never uploads, installs, or changes a bundle."""

import argparse
import json
import struct
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "org.freevia.sudokubuddy"


def audit_manifest(xml: str, version_code: int) -> None:
    root = ET.fromstring(xml)
    if root.get("package") != PACKAGE:
        raise ValueError("Wrong application ID")
    if root.get(ANDROID + "versionCode") != str(version_code):
        raise ValueError("Wrong version code")
    if root.get(ANDROID + "versionName") != "1.0.0":
        raise ValueError("Review versionName and release materials together")
    sdk = root.find("uses-sdk")
    if sdk is None or sdk.get(ANDROID + "minSdkVersion") != "26":
        raise ValueError("Expected minSdk 26")
    if sdk.get(ANDROID + "targetSdkVersion") != "36":
        raise ValueError("Expected targetSdk 36")
    app = root.find("application")
    if app is None or app.get(ANDROID + "debuggable", "false") != "false":
        raise ValueError("Missing application or debuggable release")
    if app.get(ANDROID + "testOnly", "false") != "false" or root.find("instrumentation") is not None:
        raise ValueError("Test-only or instrumented builds cannot be release candidates")
    if app.get(ANDROID + "allowBackup") != "false":
        raise ValueError("Backup declaration changed; reconcile privacy policy")
    providers = app.findall("provider")
    sharing = [p for p in providers if p.get(ANDROID + "name") == "androidx.core.content.FileProvider"]
    if len(sharing) != 1:
        raise ValueError("Expected exactly one photo-sharing FileProvider")
    provider = sharing[0]
    if (provider.get(ANDROID + "authorities") != PACKAGE + ".scans"
            or provider.get(ANDROID + "exported") != "false"
            or provider.get(ANDROID + "grantUriPermissions") != "true"):
        raise ValueError("FileProvider must grant individual photo URIs without exposing storage")
    permissions = {
        node.get(ANDROID + "name")
        for node in root
        if node.tag in ("uses-permission", "uses-permission-sdk-23")
    }
    # AndroidX can add a signature-only permission for its own receiver protection.
    allowed = {
        "android.permission.CAMERA",
        "android.permission.INTERNET",
        PACKAGE + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    }
    if not {"android.permission.CAMERA", "android.permission.INTERNET"} <= permissions or permissions - allowed:
        raise ValueError(f"Review changed permissions: {sorted(permissions)}")
    print("Manifest: expected identity, version, SDK, backup, photo-sharing provider and permissions")


def audit_elf(name: str, data: bytes) -> None:
    if len(data) < 16 or data[:4] != b"\x7fELF":
        raise ValueError(f"Not an ELF library: {name}")
    if data[4] == 1 and data[5] == 1 and len(data) >= 52:
        return  # 16 KB ELF requirement checked for 64-bit libraries.
    if data[4] != 2 or data[5] != 1 or len(data) < 64:
        raise ValueError(f"Unsupported or truncated ELF: {name}")
    offset = struct.unpack_from("<Q", data, 32)[0]
    entry_size, count = struct.unpack_from("<HH", data, 54)
    if entry_size < 56 or not count or offset + entry_size * count > len(data):
        raise ValueError(f"Invalid ELF program headers: {name}")
    loads = []
    relros = []
    for index in range(count):
        at = offset + index * entry_size
        kind = struct.unpack_from("<I", data, at)[0]
        if kind == 1:
            alignment = struct.unpack_from("<Q", data, at + 48)[0]
            file_offset, address = struct.unpack_from("<QQ", data, at + 8)
            flags = struct.unpack_from("<I", data, at + 4)[0]
            memory_size = struct.unpack_from("<Q", data, at + 40)[0]
            loads.append((address, address + memory_size, flags))
            if alignment < 16384 or alignment & (alignment - 1):
                raise ValueError(f"Load alignment below 16 KB or invalid: {name}")
            if (address - file_offset) % alignment:
                raise ValueError(f"Incongruent ELF load segment: {name}")
        if kind == 0x6474E552:  # GNU_RELRO
            address = struct.unpack_from("<Q", data, at + 16)[0]
            memory_size = struct.unpack_from("<Q", data, at + 40)[0]
            relros.append((address, address + memory_size))
    if not loads:
        raise ValueError(f"ELF has no load segments: {name}")
    for start, end in relros:
        if start == end:
            continue
        # Bionic rounds RELRO protection out to whole pages. An unaligned end is
        # safe when RELRO occupies a separate LOAD segment and the rounded range
        # contains only padding. Reject actual writable-data overlap at either end,
        # including data in another LOAD segment, instead of rejecting that layout.
        # See bionic/linker/linker_phdr.cpp:_phdr_table_set_gnu_relro_prot.
        rounded_start = start // 16384 * 16384
        rounded_end = (end + 16383) // 16384 * 16384
        for load_start, load_end, flags in loads:
            if not flags & 2:  # PF_W
                continue
            for extra_start, extra_end in ((rounded_start, start), (end, rounded_end)):
                if max(load_start, extra_start) < min(load_end, extra_end):
                    raise ValueError(f"16 KB RELRO protection overlaps writable data: {name}")
    print(f"64-bit ELF alignment: {name}")


def audit_bundle(bundle: Path) -> None:
    with zipfile.ZipFile(bundle) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())):
            raise ValueError("Bundle contains ambiguous duplicate ZIP entries")
        libraries = [n for n in archive.namelist() if "/lib/" in n and n.endswith(".so")]
        if not libraries:
            raise ValueError("Expected OpenCV native libraries")
        abis = {n.split("/lib/", 1)[1].split("/", 1)[0] for n in libraries}
        if abis != {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}:
            raise ValueError(f"Review changed bundle ABI coverage: {sorted(abis)}")
        for abi in abis:
            names = {n.rsplit("/", 1)[1] for n in libraries if f"/lib/{abi}/" in n}
            missing = {"libopencv_java4.so", "libc++_shared.so"} - names
            if missing:
                raise ValueError(f"Missing required native libraries for {abi}: {sorted(missing)}")
        for name in libraries:
            data = archive.read(name)
            abi = name.split("/lib/", 1)[1].split("/", 1)[0]
            expected_class = 2 if abi in ("arm64-v8a", "x86_64") else 1
            if len(data) < 5 or data[4] != expected_class:
                raise ValueError(f"Wrong ELF bitness for ABI: {name}")
            audit_elf(name, data)
        print(f"Bundle ABIs: {', '.join(sorted(abis))}; libraries: {len(libraries)}")


def audit_config(config: dict) -> None:
    if config.get("optimizations", {}).get("uncompressNativeLibraries", {}).get("alignment") != "PAGE_ALIGNMENT_16K":
        raise ValueError("Bundle must request PAGE_ALIGNMENT_16K")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--version-code", type=int, required=True)
    args = parser.parse_args()
    if not 0 < args.version_code <= 2100000000:
        parser.error("Invalid Play version code")
    config = json.loads(args.config.read_text(encoding="utf-8"))
    audit_config(config)
    audit_manifest(args.manifest.read_text(encoding="utf-8"), args.version_code)
    audit_bundle(args.bundle)
    print("Static checks passed; signatures and runtime testing are separate gates")


if __name__ == "__main__":
    main()
