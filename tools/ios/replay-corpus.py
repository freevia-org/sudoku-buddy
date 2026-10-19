#!/usr/bin/env python3
"""Replay the actual iOS importer locally; run after a Debug simulator build.

--prepare copies images into the selected app sandbox and launches replay.
--collect writes all-cell metrics and failures, preserving refusals in denominators.
No photo is bundled in the app, committed, or uploaded.
"""
import argparse
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUNDLE = 'org.freevia.sudokubuddy.ios'


def sim(*args, check=True):
    return subprocess.run(['xcrun', 'simctl', *args], text=True, capture_output=True, check=check).stdout.strip()


def collect(replay, output, platform='iOS simulator; native OpenCV geometry + portable Kotlin recognition'):
    rows = [json.loads(line) for line in (replay / 'results.jsonl').read_text().splitlines()]
    expected = {(folder, p.name) for folder in ['corpus', 'corpus-rectified'] for p in (ROOT / folder).iterdir() if p.suffix.lower() in ['.jpg', '.jpeg', '.png', '.webp']}
    actual = {(r['folder'], r['photo']) for r in rows}
    if (replay / 'complete').exists() and (expected != actual or len(actual) != len(rows)):
        raise ValueError('Completed replay does not match current corpus inventory')
    labels = {j['photo']: j for p in (ROOT / 'corpus-labels').glob('*.json') for j in [json.loads(p.read_text())]}
    totals = dict(labelled_images=0, labelled_cells=0, correct_cells=0, correct_roles=0, correct_digits=0, digit_cells=0)
    failures = []
    for row in rows:
        label = labels.get(row['photo'])
        if not label:
            continue
        totals['labelled_images'] += 1
        given = ''.join(label['givens']); written = ''.join(label['written'])
        errors = []
        for i, (g, w) in enumerate(zip(given, written)):
            expected_digit = int(g) if g in '123456789' else int(w) if w in '123456789' else 0
            expected_role = 'GIVEN' if g.isdigit() and g != '0' else 'GUESS' if expected_digit else 'EMPTY'
            totals['labelled_cells'] += 1
            accepted = row['status'] == 'review'
            a = row.get('givens', [0] * 81)[i]; b = row.get('progress', [0] * 81)[i]
            actual_digit = a or b
            actual_role = 'GIVEN' if a else 'GUESS' if b else 'EMPTY'
            role_ok = accepted and actual_role == expected_role
            digit_ok = accepted and actual_digit == expected_digit
            totals['correct_roles'] += role_ok
            if expected_digit:
                totals['digit_cells'] += 1
                totals['correct_digits'] += digit_ok
            totals['correct_cells'] += role_ok and digit_ok
            if not (role_ok and digit_ok):
                errors.append(dict(cell=i, expected_role=expected_role, expected_digit=expected_digit,
                                   actual_role=actual_role if accepted else 'REFUSED', actual_digit=actual_digit if accepted else None))
        if errors:
            failures.append(dict(photo=row['photo'], folder=row['folder'], status=row['status'], errors=errors))
    by_folder = {}
    for folder in ['corpus', 'corpus-rectified']:
        labelled = [r for r in rows if r['folder'] == folder and r['photo'] in labels]
        errors = [e for f in failures if f['folder'] == folder for e in f['errors']]
        digit_cells = sum(g in '123456789' or w in '123456789' for r in labelled for g, w in zip(''.join(labels[r['photo']]['givens']), ''.join(labels[r['photo']]['written'])))
        by_folder[folder] = dict(labelled_images=len(labelled), labelled_cells=len(labelled) * 81,
                                 correct_cells=len(labelled) * 81 - len(errors),
                                 correct_roles=len(labelled) * 81 - sum(e['actual_role'] != e['expected_role'] for e in errors),
                                 digit_cells=digit_cells,
                                 correct_digits=digit_cells - sum(e['expected_digit'] != 0 and e['actual_digit'] != e['expected_digit'] for e in errors))
    refusal_name = '0-02-05-3c2309bc39761674fc696fed1abdc1d89ec9436d7f340d0b6332481fc818fb42_b2fcb79a0899d6dc.jpg'
    expected_refusal = next((dict(photo=r['photo'], correctly_refused=r['status'] == 'refused') for r in rows if r['photo'] == refusal_name), None)
    counts = {folder: dict(images=sum(r['folder'] == folder for r in rows),
                          review=sum(r['folder'] == folder and r['status'] == 'review' for r in rows))
              for folder in ['corpus', 'corpus-rectified']}
    report = dict(complete=(replay / 'complete').exists(), platform=platform,
                  folders=counts, totals=totals, by_folder=by_folder, expected_result_screen=expected_refusal, refusals=[r for r in rows if r['status'] != 'review'], failures=failures,
                  caveat='Regression corpus overlaps training; these are not independent generalization scores. Refused labelled images count as errors for every cell.')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n')
    output.with_suffix('.jsonl').write_text((replay / 'results.jsonl').read_text())
    print(json.dumps({k: v for k, v in report.items() if k not in ['refusals', 'failures']}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', required=True)
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--collect', action='store_true')
    parser.add_argument('--output', type=Path, default=ROOT / 'docs/recognition/ios-corpus.json')
    args = parser.parse_args()
    if args.prepare:
        sim('boot', args.device, check=False)
        sim('bootstatus', args.device, '-b')
        sim('install', args.device, str(ROOT / '.tools/DerivedData/Build/Products/Debug-iphonesimulator/SudokuBuddy.app'))
    container = Path(sim('get_app_container', args.device, BUNDLE, 'data'))
    replay = container / 'Documents/CorpusReplay'
    if args.prepare:
        sim('terminate', args.device, BUNDLE, check=False)
        replay.mkdir(parents=True, exist_ok=True)
        for name in ['complete', 'results.jsonl']:
            (replay / name).unlink(missing_ok=True)
        for folder in ['corpus', 'corpus-rectified']:
            if (replay / folder).exists():
                shutil.rmtree(replay / folder)
            shutil.copytree(ROOT / folder, replay / folder, dirs_exist_ok=True)
        print(sim('launch', args.device, BUNDLE, '--history-test-session', '81F7187A-C967-48BE-8CB5-0E7377B81C73', '--corpus-replay'), flush=True)
        print(f'Replay directory: {replay}', flush=True)
    if args.collect:
        collect(replay, args.output)

if __name__ == '__main__':
    main()
