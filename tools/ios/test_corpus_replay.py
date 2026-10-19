"""Benchmark contracts: printed clues win, and refused images stay in denominators."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('replay', Path(__file__).with_name('replay-corpus.py'))
replay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(replay)


class CorpusReplayTests(unittest.TestCase):
    def test_given_only_labels_and_refusals_are_counted_correctly(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ['corpus', 'corpus-rectified', 'corpus-labels', 'replay']:
                (root / name).mkdir()
            rows = []
            for name, folder, status in [('printed.jpg', 'corpus-rectified', 'review'), ('refused.jpg', 'corpus', 'refused')]:
                (root / folder / name).touch()
                label = dict(photo=name, givens=['5........'] + ['.........'] * 8, written=['.........'] * 9)
                (root / 'corpus-labels' / (name + '.json')).write_text(json.dumps(label))
                rows.append(dict(photo=name, folder=folder, status=status, givens=[5] + [0] * 80, progress=[0] * 81))
            (root / 'replay/results.jsonl').write_text('\n'.join(json.dumps(r) for r in rows))
            (root / 'replay/complete').touch()
            output = root / 'report.json'
            with patch.object(replay, 'ROOT', root), contextlib.redirect_stdout(io.StringIO()):
                replay.collect(root / 'replay', output)
            report = json.loads(output.read_text())
            self.assertEqual(report['totals']['labelled_cells'], 162)
            self.assertEqual(report['totals']['correct_cells'], 81)
            self.assertEqual(report['totals']['digit_cells'], 2)
            self.assertEqual(report['totals']['correct_digits'], 1)
            self.assertEqual(report['by_folder']['corpus-rectified']['correct_cells'], 81)
            self.assertEqual(len(report['failures'][0]['errors']), 81)

    def test_completed_replay_fails_closed_when_images_are_missing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ['corpus', 'corpus-rectified', 'corpus-labels', 'replay']:
                (root / name).mkdir()
            (root / 'corpus/missing.jpg').touch()
            (root / 'replay/results.jsonl').write_text('')
            (root / 'replay/complete').touch()
            with patch.object(replay, 'ROOT', root):
                with self.assertRaisesRegex(ValueError, 'inventory'):
                    replay.collect(root / 'replay', root / 'report.json')


if __name__ == '__main__':
    unittest.main()
