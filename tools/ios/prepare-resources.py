"""Deterministically package Android catalogs and applicable notices for iOS.

No translation service is used. iOS wording aliases reuse reviewed Android copy;
English keeps platform-specific instructions. See ios-messages.json for provenance.
"""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/i18n'))
from catalog import resource_directory


def android_values(tag):
    result = {}
    for item in ET.parse(resource_directory(tag) / 'messages.xml').getroot():
        value = item.text or ''
        if value.startswith('"') and value.endswith('"'):
            value = json.loads(value.replace("\\'", "'"))
        result[item.attrib['name']] = value
    return result


def resources():
    languages = json.loads((ROOT / 'tools/i18n/languages.json').read_text())
    definitions = json.loads((ROOT / 'app/src/main/assets/messages.json').read_text())
    aliases = json.loads((ROOT / 'tools/ios/ios-messages.json').read_text())
    keys = {e['source']: e['key'] for e in definitions}
    output = {}
    metadata = []
    for tag, label in languages.items():
        locale = {'zh-CN': 'zh-Hans', 'zh-TW': 'zh-Hant'}.get(tag, tag)
        values = android_values(tag)
        assert set(keys.values()) <= values.keys(), f'Incomplete Android catalog: {tag}'
        messages = {e['source']: values[e['key']] for e in definitions}
        # Adapt the operating-system brand before display, including Persian's
        # transliteration. Keep the reviewed sentence and its original lookup key.
        for source in messages:
            if 'phone model, Android version' in source:
                brand = 'اندروید' if tag == 'fa' else 'Android'
                assert brand in messages[source], (tag, source)
                messages[source] = messages[source].replace(brand, 'iOS')
        for source, equivalent in aliases.items():
            # Pieces may be joined without losing positional argument numbering.
            pieces = equivalent if isinstance(equivalent, list) else [equivalent]
            assert all(p in keys for p in pieces), (source, pieces)
            translated = ''.join(messages[p] for p in pieces)
            messages[source] = re.sub(r'\{(\d+)\}', lambda m: '%' + str(int(m[1])+1) + '$s', source) if tag == 'en' else translated
            assert sorted(re.findall(r'\{\d+\}', source)) == sorted('{' + str(int(n)-1) + '}' for n in re.findall(r'%(\d+)\$s', messages[source])), (tag, source)
        for source, phrase in {
            "{0} answers need another look.": "%1$s " + messages["entries that disagree"],
            "All {0} entered answers are correct.": messages["Everything you have written so far is right."] + " (%1$s)",
        }.items():
            messages[source] = re.sub(r'\{(\d+)\}', lambda m: '%' + str(int(m[1])+1) + '$s', source) if tag == 'en' else phrase
        output[f'{locale}.lproj/Messages.json'] = (json.dumps(messages, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode()
        permission = messages['Point the camera at a sudoku puzzle.']
        info = {'CFBundleDisplayName': 'Sudoku Buddy', 'NSCameraUsageDescription': permission}
        output[f'{locale}.lproj/InfoPlist.strings'] = ('\n'.join(json.dumps(k) + ' = ' + json.dumps(v, ensure_ascii=False) + ';' for k,v in info.items()) + '\n').encode()
        metadata.append({'id': locale, 'name': label})
    output['languages.json'] = (json.dumps(metadata, ensure_ascii=False, indent=2) + '\n').encode()
    for name, path in {
        'Apache-2.0.txt': 'LICENSE', 'Sudoku-Buddy-NOTICE.txt': 'tools/ios/NOTICE.txt',
        'Kotlin-NOTICE.txt': 'app/src/main/assets/licenses/Kotlin-NOTICE.txt',
        'LLVM-libcxx-LICENSE.txt': 'app/src/main/assets/licenses/LLVM-libcxx-LICENSE.txt',
    }.items():
        output['Licenses/' + name] = (ROOT / path).read_bytes()
    # Native grid geometry now uses the same OpenCV family as Android.
    # Carry the upstream bundled notices, excluding the Android runtime itself.
    license_root = ROOT / 'app/src/main/assets/licenses'
    for path in license_root.iterdir():
        if path.name.startswith('OpenCV-') or path.name in {
            'ADE-LICENSE', 'Carotene-LICENSE.txt', 'FLANN-LICENSE.txt', 'ITT-BSD-3-Clause.txt',
            'KleidiCV-Apache-2.0.txt', 'oneTBB-LICENSE.txt', 'oneTBB-NOTICE.txt'}:
            output['Licenses/' + path.name] = path.read_bytes()
    return output, metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    output, metadata = resources()
    base = ROOT / 'iosApp/SudokuBuddy/Resources'
    if args.check:
        stale = [name for name,data in output.items() if not (base/name).exists() or (base/name).read_bytes() != data]
        if stale: raise SystemExit('Stale iOS resources: ' + ', '.join(stale))
    else:
        for name, data in output.items():
            target = base / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    print(f'{len(metadata)} languages, {len(output)} resources verified' if args.check else f'Packaged {len(metadata)} languages, {len(output)} resources')


if __name__ == '__main__':
    main()
