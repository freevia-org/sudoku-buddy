"""Checks resource parity, format safety, and that shipping UI copy is bundled."""
import importlib.util
import hashlib
import json
import plistlib
import re
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('ios_resources', HERE / 'prepare-resources.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
ROOT = module.ROOT


class ResourcesTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files, cls.languages = module.resources()
        cls.base = ROOT / 'iosApp/SudokuBuddy/Resources'
        cls.english = json.loads(cls.files['en.lproj/Messages.json'])

    def test_generated_resources_match_sources(self):
        for name, expected in self.files.items():
            with self.subTest(name=name):
                self.assertEqual((self.base / name).read_bytes(), expected)

    def test_all_android_languages_have_native_bundle_catalogs(self):
        self.assertEqual(len(self.languages), 71)
        project = (ROOT / 'iosApp/SudokuBuddy.xcodeproj/project.pbxproj').read_text()
        for locale in self.languages:
            code = locale['id']
            self.assertIn(f'Resources/{code}.lproj/Messages.json', project)
            self.assertIn(f'Resources/{code}.lproj/InfoPlist.strings', project)
        self.assertIn('zh-Hans.lproj/Messages.json', self.files)
        self.assertIn('zh-Hant.lproj/Messages.json', self.files)
        self.assertIn('he.lproj/Messages.json', self.files)
        self.assertIn('id.lproj/Messages.json', self.files)

    def test_every_translated_placeholder_preserves_its_argument(self):
        for path, data in self.files.items():
            if not path.endswith('/Messages.json'): continue
            for source, translated in json.loads(data).items():
                with self.subTest(path=path, source=source):
                    expected = sorted(re.findall(r'\{(\d+)\}', source))
                    actual = sorted(str(int(n)-1) for n in re.findall(r'%(\d+)\$s', translated))
                    self.assertEqual(expected, actual)

    def test_bug_report_platform_brand_is_correct_in_every_language(self):
        for path, data in self.files.items():
            if not path.endswith('/Messages.json'): continue
            messages = json.loads(data)
            sentence = next(value for key, value in messages.items() if 'phone model, Android version' in key)
            self.assertIn('iOS', sentence, path)
            self.assertNotIn('Android', sentence, path)
            self.assertNotIn('اندروید', sentence, path)

    def test_puzzle_font_faces_are_registered_and_match_the_recorded_android_assets(self):
        info=plistlib.loads((ROOT/'iosApp/SudokuBuddy/Info.plist').read_bytes())
        metadata=json.loads((ROOT/'iosApp/SudokuBuddy/Fonts/provenance.json').read_text())
        self.assertEqual(metadata['license'],'Apache-2.0')
        self.assertEqual(set(info['UIAppFonts']),{face['file'] for face in metadata['faces']})
        project=(ROOT/'iosApp/SudokuBuddy.xcodeproj/project.pbxproj').read_text()
        for face in metadata['faces']:
            data=(ROOT/'iosApp/SudokuBuddy/Fonts'/face['file']).read_bytes()
            self.assertEqual(hashlib.sha256(data).hexdigest(),face['sha256'])
            self.assertIn('Fonts/'+face['file'],project)
        notice=self.files['Licenses/Sudoku-Buddy-NOTICE.txt'].decode()
        self.assertIn('Roboto',notice)
        self.assertIn('OpenCV 4.12',notice)
        self.assertNotIn('or OpenCV',notice)

    def test_static_swift_copy_is_in_the_catalog(self):
        for path in (ROOT / 'iosApp/SudokuBuddy').glob('*.swift'):
            for text in re.findall(r'tr\("([^"\\]*)"\)', path.read_text()):
                with self.subTest(path=path.name, text=text):
                    self.assertIn(text, self.english)

    def test_custom_service_configuration_has_an_explicit_plist_input(self):
        info = plistlib.loads((ROOT / 'iosApp/SudokuBuddy/Info.plist').read_bytes())
        self.assertEqual(info['SudokuFeedbackEndpoint'], '$(SUDOKU_FEEDBACK_ENDPOINT)')
        project = (ROOT / 'iosApp/SudokuBuddy.xcodeproj/project.pbxproj').read_text()
        self.assertEqual(project.count('INFOPLIST_FILE = SudokuBuddy/Info.plist'), 2)

    def test_ios_notices_do_not_claim_android_dependencies(self):
        names = [name for name in self.files if name.startswith('Licenses/')]
        self.assertGreater(len(names), 4)
        self.assertFalse(any('Android-runtime' in name for name in names))
        self.assertIn('Licenses/OpenCV-LICENSE', names)
        self.assertIn('MNIST', self.files['Licenses/Sudoku-Buddy-NOTICE.txt'].decode())
        self.assertIn('Apache License', self.files['Licenses/Apache-2.0.txt'].decode())


if __name__ == '__main__':
    unittest.main()
