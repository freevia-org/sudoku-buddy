#!/usr/bin/env python3
"""Build an opt-in debug workaround for Apple HVF's invalid SVE2-without-SVE flags.

Requires an installed Android NDK. Never use this with a release or real device.
"""
import argparse
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--ndk', type=Path, required=True)
p.add_argument('--output', type=Path, default=ROOT / '.tools/android-emulator-shim')
a = p.parse_args()
lib = a.output / 'jniLibs/arm64-v8a'
resources = a.output / 'resources/lib/arm64-v8a'
lib.mkdir(parents=True, exist_ok=True)
resources.mkdir(parents=True, exist_ok=True)
compiler = a.ndk / 'toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android26-clang'
subprocess.run([str(compiler), '-shared', '-fPIC', '-Wl,-z,max-page-size=16384',
                str(ROOT / 'tools/android/emulator-hwcap.c'), '-ldl', '-o', str(lib / 'libemulator_hwcap.so')], check=True)
(resources / 'wrap.sh').write_text('#!/system/bin/sh\nexport LD_PRELOAD="${0%/*}/libemulator_hwcap.so"\nexec "$@"\n')
print(f'Local Debug builds only: -PemulatorHwcapShim={a.output.resolve()}')
