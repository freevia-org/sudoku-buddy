import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('comparison',Path(__file__).with_name('compare-platform-replays.py'))
comparison=importlib.util.module_from_spec(spec)
spec.loader.exec_module(comparison)


class PlatformReplayComparisonTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        self.expected={('corpus','puzzle.jpg')}
        self.row=dict(folder='corpus',photo='puzzle.jpg',status='review',givens=[0]*81,progress=[0]*81,uncertain=[2])

    def write(self,name,rows):
        path=self.root/name;path.write_text(''.join(json.dumps(row)+'\n' for row in rows));return path

    def test_identical_digits_do_not_hide_different_review_markers(self):
        other={**self.row,'uncertain':[3]}
        result=comparison.compare(self.write('a',[self.row]),self.write('b',[other]),self.expected)
        self.assertFalse(result['equivalent']);self.assertEqual(result['differences'][0]['fields'],['uncertain'])

    def test_matching_invalid_arrays_cannot_claim_equivalence(self):
        bad={**self.row,'givens':[0]*80}
        with self.assertRaisesRegex(ValueError,'invalid givens'):
            comparison.compare(self.write('a',[bad]),self.write('b',[bad]),self.expected)

    def test_missing_and_duplicate_photos_fail_closed(self):
        for rows in [[],[self.row,self.row]]:
            with self.subTest(rows=len(rows)),self.assertRaisesRegex(ValueError,'inventory'):
                comparison.compare(self.write('a',rows),self.write('b',[self.row]),self.expected)

    def test_timing_and_locator_diagnostics_are_not_app_reading_differences(self):
        other={**self.row,'seconds':3.0,'locator':'opencv'}
        self.assertTrue(comparison.compare(self.write('a',[self.row]),self.write('b',[other]),self.expected)['equivalent'])
