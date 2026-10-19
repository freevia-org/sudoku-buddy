#!/usr/bin/env python3
"""Compare actual importer outputs against the complete current local corpus inventory.

Recognition accuracy and platform equivalence are separate: identical mistakes count
as equivalent, and the per-platform accuracy reports retain those mistakes.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIELDS = ('status', 'givens', 'progress', 'uncertain')


def read(path, expected):
    rows = [json.loads(line) for line in path.read_text().splitlines()]
    indexed = {(row['folder'], row['photo']): row for row in rows}
    if len(rows) != len(indexed) or indexed.keys() != expected:
        raise ValueError(f'{path}: incomplete or duplicate corpus inventory')
    for key, row in indexed.items():
        if row['status'] not in ('review', 'refused'):
            raise ValueError(f'{key}: invalid recognition status')
        if row['status'] == 'review':
            for field in ('givens', 'progress'):
                if len(row[field]) != 81 or any(type(v) is not int or not 0 <= v <= 9 for v in row[field]):
                    raise ValueError(f'{key}: invalid {field}')
            if any(g and p for g, p in zip(row['givens'], row['progress'])):
                raise ValueError(f'{key}: printed and written digit overlap')
            if any(type(v) is not int or not 0 <= v < 81 for v in row['uncertain']):
                raise ValueError(f'{key}: invalid review markers')
    return indexed


def compare(android, ios, expected):
    a, b = read(android, expected), read(ios, expected)
    differences = []
    for key in sorted(expected):
        changed = [field for field in FIELDS if a[key].get(field) != b[key].get(field)]
        if changed:
            differences.append(dict(folder=key[0], photo=key[1], fields=changed))
    return dict(images=len(expected), matching_images=len(expected)-len(differences),
                equivalent=not differences, compared_fields=list(FIELDS), differences=differences,
                scope='Actual offline importer outputs; not physical camera, pixel rendering, or independent OCR generalization')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--android', type=Path, required=True)
    p.add_argument('--ios', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    expected = {(folder, file.name) for folder in ('corpus', 'corpus-rectified')
                for file in (ROOT / folder).iterdir() if file.suffix.lower() in ('.jpg', '.jpeg', '.png', '.webp')}
    if not expected:
        raise ValueError('No corpus supplied')
    result = compare(args.android, args.ios, expected)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))
    if not result['equivalent']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
