#!/usr/bin/env python3
"""Replay private local photos through Android's production importer. No uploads.

Build/install Debug and androidTest APKs first. On affected Apple HVF emulators,
use tools/android/prepare-emulator-shim.py and the explicit emulatorHwcapShim
Gradle property. The workaround is excluded from ordinary and release builds.
"""
import argparse
import importlib.util
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = 'org.freevia.sudokubuddy.debug'
spec = importlib.util.spec_from_file_location('ios_replay', ROOT / 'tools/ios/replay-corpus.py')
metrics = importlib.util.module_from_spec(spec)
spec.loader.exec_module(metrics)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--collect', action='store_true')
    parser.add_argument('--hwcap-shim', action='store_true', help='Record that this run uses the local HVF workaround')
    parser.add_argument('--output', type=Path, default=ROOT / '.tools/android-actual-corpus.json')
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
    adb = [str(sdk / 'platform-tools/adb'), '-s', args.serial]
    run = lambda *tail: subprocess.run(adb + list(tail), check=True, capture_output=True).stdout
    if args.prepare:
        run('shell', 'run-as', PACKAGE, 'mkdir', '-p', 'files/ParityCorpus')
        # COPYFILE_DISABLE avoids AppleDouble metadata entries becoming pretend photos.
        archive = subprocess.Popen(['tar', '-cf', '-', 'corpus', 'corpus-rectified'], cwd=ROOT,
                                   env={**os.environ, 'COPYFILE_DISABLE': '1'}, stdout=subprocess.PIPE)
        try:
            subprocess.run(adb + ['shell', '-T', 'run-as', PACKAGE, 'tar', '-xf', '-', '-C', 'files/ParityCorpus'],
                           stdin=archive.stdout, check=True)
        finally:
            archive.stdout.close()
        if archive.wait():
            raise RuntimeError('Could not stage the entire corpus')
        run('shell', 'run-as', PACKAGE, 'rm', '-f', 'files/ParityCorpus/complete')
        result = run('shell', 'am', 'instrument', '-w', '-r', '-e', 'timeout_msec', '1200000', '-e', 'class',
                     'org.freevia.sudokubuddy.app.NativeVisionParityTest#replayFullLocalCorpusThroughProductionImporter',
                     PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner').decode()
        print(result)
        if 'INSTRUMENTATION_STATUS_CODE: 0' not in result or 'FAILURES' in result:
            raise RuntimeError('Corpus instrumentation did not pass')
    if args.collect:
        replay = args.output.parent / (args.output.stem + '-replay')
        replay.mkdir(parents=True, exist_ok=True)
        run('shell', 'run-as', PACKAGE, 'cat', 'files/ParityCorpus/complete')
        (replay / 'results.jsonl').write_bytes(run('exec-out', 'run-as', PACKAGE, 'cat', 'files/ParityCorpus/results.jsonl'))
        (replay / 'complete').write_text('complete')
        platform = 'Android emulator; production importer'
        if args.hwcap_shim:
            platform += '; debug-only invalid SVE2 capability mask'
        metrics.collect(replay, args.output, platform)


if __name__ == '__main__':
    main()
