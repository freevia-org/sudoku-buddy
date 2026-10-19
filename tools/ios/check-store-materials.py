#!/usr/bin/env python3
"""Validate listing limits, screenshot inventory and submission attachment integrity."""
import hashlib, json, struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKET = ROOT / 'docs/app-store'


def check():
    limits = {'name': 30, 'subtitle': 30, 'keywords': 100, 'promotional-text': 170, 'description': 4000, 'release-notes': 4000}
    lengths = {}
    for field, limit in limits.items():
        text = (PACKET / 'en-US' / (field + '.txt')).read_text().strip()
        assert text and len(text) <= limit, (field, len(text), limit)
        lengths[field] = len(text)
    stages = ['Where', 'Square', 'Trace', 'Answer']
    for stage in stages:
        assert stage in (PACKET / 'en-US/description.txt').read_text()
    for file in ['README.md', 'submission-answers.md', 'privacy-answers.md', 'review-notes.txt', 'device-qualification.md', 'review-puzzle.png', 'review-puzzle.txt']:
        assert (PACKET / file).stat().st_size > 0
    grid = ''.join((PACKET / 'review-puzzle.txt').read_text().split())
    assert len(grid) == 81 and all(c in '.123456789' for c in grid)
    manifest = json.loads((PACKET / 'screenshots/manifest.json').read_text())
    families = set()
    for item in manifest['screenshots']:
        data = (PACKET / 'screenshots' / item['file']).read_bytes()
        assert data[:8] == b'\x89PNG\r\n\x1a\n'
        width, height, depth, color = struct.unpack('>IIBB', data[16:26])
        assert color == 2 and depth == 8, 'Screenshots must be opaque RGB PNG'
        assert [width, height] == item['pixels']
        assert hashlib.sha256(data).hexdigest() == item['sha256']
        assert item['family'] in ['iPhone', 'iPad']
        if item['family'] == 'iPad': assert (width,height) in [(2064,2752),(2752,2064),(2048,2732),(2732,2048)]
        else: assert (width,height) in [(1206,2622),(1260,2736),(1290,2796),(1320,2868),(1179,2556)]
        families.add(item['family'])
    assert {p.name for p in (PACKET / 'screenshots').glob('*.png')} == {s['file'] for s in manifest['screenshots']}
    assert all(sum(s['family']==f for s in manifest['screenshots']) <= 10 for f in families)
    assert families == {'iPhone','iPad'}
    assert len(manifest['screenshots']) >= 6
    print(json.dumps({'listing_lengths': lengths, 'screenshots':len(manifest['screenshots']), 'families':sorted(families)},indent=2))


if __name__ == '__main__': check()
