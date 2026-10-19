#!/usr/bin/env python3
"""Audit an unsigned or signed .app without changing it; does not replace Apple validation."""
import argparse, hashlib, json, plistlib
from pathlib import Path


def audit(app):
    info = plistlib.loads((app / 'Info.plist').read_bytes())
    assert info['CFBundleIdentifier'] == 'org.freevia.sudokubuddy.ios'
    assert info['SudokuFeedbackEndpoint'] == 'https://sudoku-buddy-feedback.antoni-ivanov.workers.dev/v1/reports'
    assert info['ITSAppUsesNonExemptEncryption'] is False
    assert set(info['UIDeviceFamily']) == {1, 2}
    assert 'NSCameraUsageDescription' in info
    assert 'NSMicrophoneUsageDescription' not in info
    assert 'NSPhotoLibraryUsageDescription' not in info  # PHPicker does not request full-library access.
    assert not list(app.rglob('scan-fixture*'))
    assert not list(app.rglob('CorpusReplay*'))
    catalogs = sorted(p.name for p in app.glob('*.lproj'))
    assert len(catalogs) == 71, len(catalogs)
    for folder in app.glob('*.lproj'):
        assert (folder / 'Messages.json').is_file()
        assert (folder / 'InfoPlist.strings').is_file()
    assert len(info['UIAppFonts']) == 4
    for font in info['UIAppFonts']:
        assert (app / font).is_file()
    privacy = plistlib.loads((app / 'PrivacyInfo.xcprivacy').read_bytes())
    assert privacy['NSPrivacyTracking'] is False
    assert len(privacy['NSPrivacyCollectedDataTypes']) == 4
    binary = (app / info['CFBundleExecutable']).read_bytes()
    for marker in [b'--scan-fixture', b'--scan-review-fixture', b'--puzzle-fixture', b'--history-test-session', b'--mock-feedback-service', b'--refused-fixture', b'--corpus-replay', b'--corpus-photo', b'--pixel-probe']:
        assert marker not in binary, marker
    models = list(app.rglob('*.bin'))
    source_model = Path(__file__).resolve().parents[2] / 'core/recognize/src/main/resources/digits.bin'
    assert (app / 'digits.bin').read_bytes() == source_model.read_bytes()
    assert (app / 'Frameworks/opencv2.framework/opencv2').is_file()
    return {'bundle_id': info['CFBundleIdentifier'], 'version': info['CFBundleShortVersionString'],
            'build': info['CFBundleVersion'], 'minimum_ios': info['MinimumOSVersion'],
            'sdk': info.get('DTSDKName'), 'catalogs': len(catalogs), 'fonts': info['UIAppFonts'],
            'phone_orientations': info['UISupportedInterfaceOrientations'],
            'tablet_orientations': info['UISupportedInterfaceOrientations~ipad'],
            'models': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in models},
            'exempt_encryption': True, 'production_endpoint': True, 'debug_fixtures_absent': True,
            'privacy_manifest_present': True, 'binary_sha256': hashlib.sha256(binary).hexdigest(),
            'qualification': 'Local bundle audit; signing, upload validation and physical-device qualification remain separate.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('app', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = json.dumps(audit(args.app), indent=2) + '\n'
    if args.output:
        args.output.write_text(report)
    print(report, end='')
