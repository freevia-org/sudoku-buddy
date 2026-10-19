import importlib.util,re,unittest
from pathlib import Path

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('site_builder',HERE/'build-site.py')
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)


class SiteTests(unittest.TestCase):
    def test_generated_assets_match_sources_and_have_bounded_scope(self):
        files=builder.build()
        self.assertEqual(len(files),6)
        for name,data in files.items():
            self.assertTrue(name.startswith(('sudoku-buddy/','assets/sudoku-buddy/ios-')))
            self.assertEqual((builder.ROOT/'docs/site'/name).read_bytes(),data)

    def test_ios_previews_do_not_advertise_an_available_app_store_release(self):
        for name in ['sudoku-buddy/index.html','sudoku-buddy/support/index.html']:
            text=builder.build()[name].decode()
            self.assertIn('not yet available on the App Store',text)
            self.assertNotRegex(text,r'href="[^"]*(apps.apple.com|itms-apps:)')
            self.assertEqual(len(re.findall(r'<h1(?:\s|>)',text)),1)

    def test_policy_covers_both_platforms_and_has_working_deletion_anchor(self):
        text=builder.build()['sudoku-buddy/privacy/index.html'].decode()
        self.assertIn('id="deleting-your-data"',text)
        self.assertIn('operating-system version',text)
        self.assertIn('On iOS',text)
        self.assertIn('iCloud backup settings',text)
        self.assertIn('mailto:info@freevia.org',text)
        self.assertIn('Submission receipts',text)
        home=builder.build()['sudoku-buddy/index.html'].decode()
        self.assertIn('/sudoku-buddy/privacy/#deleting-your-data',home)
        self.assertNotIn('#uninstalling',home)

    def test_preview_images_are_unmodified_store_captures(self):
        files=builder.build()
        for name,data in files.items():
            if name.endswith('.png'):
                source=name.split('/ios-',1)[1]
                self.assertEqual(data,(builder.ROOT/'docs/app-store/screenshots'/source).read_bytes())


if __name__=='__main__':unittest.main()
