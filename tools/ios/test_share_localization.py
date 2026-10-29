"""The extension ships the same localized language-neutral actions/privacy as the app."""
import importlib.util
import json
from pathlib import Path
import re
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('share_resources', HERE / 'prepare-resources.py')
resources = importlib.util.module_from_spec(spec)
spec.loader.exec_module(resources)
ROOT = resources.ROOT

class ShareLocalizationTests(unittest.TestCase):
    def test_all_71_extension_catalogs_match_app_and_generated_resources(self):
        output, languages = resources.resources()
        subset = resources.share_resources(output)
        self.assertEqual(len(languages), 71)
        self.assertEqual(len(subset), 71)
        for name, data in subset.items():
            with self.subTest(name=name):
                self.assertEqual((ROOT / 'iosApp/ShareExtension/Resources' / name).read_bytes(), data)
                self.assertEqual(set(json.loads(data)), set(resources.SHARE_SOURCES))
                self.assertTrue(all(json.loads(data).values()))
                app = json.loads(output[name])
                self.assertEqual(json.loads(data), {key: app[key] for key in resources.SHARE_SOURCES})
    def test_extension_visible_text_uses_catalog_keys(self):
        source = (ROOT / 'iosApp/ShareExtension/ShareViewController.swift').read_text()
        keys = re.findall(r'text\("([^"\\]*)"\)', source)
        self.assertTrue(keys)
        self.assertLessEqual(set(keys), set(resources.SHARE_SOURCES))
        self.assertNotRegex(source, r'(?:status\.text\s*=|setTitle\()\s*"[A-Za-z]')
        self.assertIn('language.isRTL ? .forceRightToLeft', source)
