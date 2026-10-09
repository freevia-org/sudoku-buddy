import json
import contextlib
import io
import tempfile
import unittest
import zipfile
from pathlib import Path

from package_candidate import EVIDENCE, INDEX, MATERIALS, package_candidate, source_fingerprints, verify_package
from test_check_play_bundle import MANIFEST, elf


class CandidatePackageTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.source = self.root / "source"
        self.evidence = self.root / "evidence"
        self.evidence.mkdir()
        self.output = self.root / "candidate"
        self.bundle = self.root / "build.aab"
        with zipfile.ZipFile(self.bundle, "w") as archive:
            for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
                for name in ("libopencv_java4.so", "libc++_shared.so"):
                    data = bytearray(elf())
                    if abi in ("armeabi-v7a", "x86"):
                        data[4] = 1
                    archive.writestr(f"base/lib/{abi}/{name}", data)
        for name in MATERIALS:
            file = self.source / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(name)
        for name in EVIDENCE:
            (self.evidence / name).write_text(name)
        (self.evidence / "manifest.xml").write_text(MANIFEST)
        (self.evidence / "bundle-config.json").write_text(json.dumps({"optimizations": {
            "uncompressNativeLibraries": {"alignment": "PAGE_ALIGNMENT_16K"}}}))

    def tearDown(self):
        self.temporary.cleanup()

    def prepare(self):
        with contextlib.redirect_stdout(io.StringIO()):
            return package_candidate(self.source, self.bundle, self.evidence, self.output,
                                     123, {"commit": "a" * 40, "dirty": True})

    def test_explicit_materials_only_and_honest_source_record(self):
        (self.source / "docs/store/raw-photo.jpg").write_bytes(b"private")
        (self.evidence / "private-key.jks").write_bytes(b"secret")
        record = self.prepare()
        verify_package(self.output)
        self.assertTrue(record["source"]["dirty"])
        self.assertEqual("NOT APPROVED FOR DISTRIBUTION", record["status"])
        self.assertFalse((self.output / "store/raw-photo.jpg").exists())
        self.assertFalse((self.output / "evidence/private-key.jks").exists())
        inventory = json.loads((self.output / INDEX).read_text())
        self.assertEqual(len(MATERIALS) + len(EVIDENCE) + 2, len(inventory))

    def test_changed_missing_and_extra_files_fail_verification(self):
        self.prepare()
        image = self.output / "store/icon-512.png"
        original = image.read_bytes()
        image.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "Checksum mismatch"):
            verify_package(self.output)
        image.unlink()
        with self.assertRaisesRegex(ValueError, "missing or unlisted"):
            verify_package(self.output)
        image.write_bytes(original)
        (self.output / "unexpected.txt").write_text("extra")
        with self.assertRaisesRegex(ValueError, "missing or unlisted"):
            verify_package(self.output)

    def test_explicit_optional_evidence_is_copied_and_hashed(self):
        (self.evidence / "device-tests.md").write_text("Pending physical-device testing")
        (self.evidence / "unrelated.log").write_text("not selected")
        self.prepare()
        verify_package(self.output)
        inventory = json.loads((self.output / INDEX).read_text())
        self.assertIn("evidence/device-tests.md", inventory)
        self.assertNotIn("evidence/unrelated.log", inventory)

    def test_missing_evidence_creates_no_partial_candidate(self):
        (self.evidence / EVIDENCE[0]).unlink()
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertFalse(self.output.exists())

    def test_mismatched_version_and_bad_config_create_no_candidate(self):
        (self.evidence / "manifest.xml").write_text(MANIFEST.replace('versionCode="123"', 'versionCode="124"'))
        with self.assertRaisesRegex(ValueError, "version code"):
            self.prepare()
        self.assertFalse(self.output.exists())
        (self.evidence / "manifest.xml").write_text(MANIFEST)
        (self.evidence / "bundle-config.json").write_text('{}')
        with self.assertRaisesRegex(ValueError, "PAGE_ALIGNMENT_16K"):
            self.prepare()
        self.assertFalse(self.output.exists())

    def test_existing_candidate_is_never_overwritten(self):
        self.prepare()
        digest = (self.output / INDEX).read_bytes()
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.prepare()
        self.assertEqual(digest, (self.output / INDEX).read_bytes())

    def test_checksum_inventory_cannot_escape_package(self):
        self.prepare()
        inventory = json.loads((self.output / INDEX).read_text())
        inventory["../build.aab"] = "anything"
        (self.output / INDEX).write_text(json.dumps(inventory))
        with self.assertRaises(ValueError):
            verify_package(self.output)

    def test_source_provenance_excludes_private_data_and_tracks_local_edits(self):
        names = {"app/src/main/Test.kt", "core/model/src/Deleted.kt", "gradle.properties",
                 "private/corpus.jpg", "corpus/photo.jpg", "local.properties",
                 "app/upload.keystore", "app/src/main/credentials.json", "app/build/temporary.kt"}
        for name in names - {"core/model/src/Deleted.kt"}:
            path = self.source / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("original")
        before = source_fingerprints(self.source, names)
        self.assertEqual({"app/src/main/Test.kt", "core/model/src/Deleted.kt", "gradle.properties"}, set(before))
        self.assertIsNone(before["core/model/src/Deleted.kt"])
        (self.source / "app/src/main/Test.kt").write_text("edited")
        after = source_fingerprints(self.source, names)
        self.assertNotEqual(before["app/src/main/Test.kt"], after["app/src/main/Test.kt"])


if __name__ == "__main__":
    unittest.main()
