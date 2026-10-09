"""Exercise release-blocking failures with synthetic artifacts, without an Android build."""

import contextlib
import io
import struct
import tempfile
import unittest
import warnings
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

from check_play_bundle import audit_bundle, audit_elf, audit_manifest


def elf(alignment=16384, relro_end=None, load_size=16384):
    data = bytearray(176)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<HH", data, 54, 56, 1 if relro_end is None else 2)
    struct.pack_into("<I", data, 64, 1)
    struct.pack_into("<I", data, 68, 6)  # PF_R | PF_W
    struct.pack_into("<Q", data, 104, load_size)
    struct.pack_into("<Q", data, 112, alignment)
    if relro_end is not None:
        struct.pack_into("<I", data, 120, 0x6474E552)
        struct.pack_into("<Q", data, 160, relro_end)
    return bytes(data)


MANIFEST = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
package="org.freevia.sudokubuddy" android:versionCode="123" android:versionName="1.0.0">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.INTERNET"/>
<application android:allowBackup="false">
<provider android:name="androidx.core.content.FileProvider"
android:authorities="org.freevia.sudokubuddy.scans" android:exported="false"
android:grantUriPermissions="true"/>
</application>
</manifest>'''


class CandidateAuditTest(unittest.TestCase):
    def test_valid_identity_and_library(self):
        with contextlib.redirect_stdout(io.StringIO()):
            audit_manifest(MANIFEST, 123)
            audit_elf("libopencv.so", elf(relro_end=16384))

    def test_native_failures_block_release(self):
        for data in (elf(4096), elf(24576), elf(relro_end=4096), elf()[:70], b"not ELF"):
            with self.subTest(data=data[:16]), self.assertRaises(ValueError):
                audit_elf("libopencv.so", data)

    def test_unaligned_relro_with_only_padding_is_safe(self):
        with contextlib.redirect_stdout(io.StringIO()):
            audit_elf("separate-relro-load.so", elf(relro_end=4096, load_size=4096))

    def test_relro_must_not_protect_writable_prefix(self):
        data = bytearray(elf(relro_end=16384))
        struct.pack_into("<Q", data, 136, 4096)
        struct.pack_into("<Q", data, 160, 12288)
        with self.assertRaisesRegex(ValueError, "overlaps writable data"):
            audit_elf("writable-prefix.so", data)

    def test_relro_must_not_protect_neighboring_writable_load(self):
        data = bytearray(elf(relro_end=4096, load_size=4096)) + bytearray(56)
        struct.pack_into("<H", data, 56, 3)
        struct.pack_into("<IIQQQQQQ", data, 176, 1, 6, 8192, 8192, 8192, 4096, 4096, 16384)
        with self.assertRaisesRegex(ValueError, "overlaps writable data"):
            audit_elf("neighboring-data.so", data)

    def test_debug_identity_network_and_wrong_version_block_release(self):
        examples = [
            MANIFEST.replace('package="org.freevia.sudokubuddy"', 'package="org.freevia.sudokubuddy.debug"'),
            MANIFEST.replace('<uses-permission android:name="android.permission.INTERNET"/>\n', ''),
            MANIFEST.replace('</manifest>', '<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/></manifest>'),
            MANIFEST.replace('android:versionCode="123"', 'android:versionCode="1"'),
            MANIFEST.replace('<application ', '<application android:debuggable="true" '),
            MANIFEST.replace('android:allowBackup="false"', 'android:allowBackup="true"'),
            MANIFEST.replace('<application ', '<application android:testOnly="true" '),
            MANIFEST.replace('</manifest>', '<instrumentation android:name="test.Runner"/></manifest>'),
        ]
        for xml in examples:
            with self.subTest(xml=xml), self.assertRaises(ValueError):
                audit_manifest(xml, 123)

    def test_unsafe_or_broken_photo_sharing_blocks_release(self):
        examples = [
            MANIFEST.replace('android:exported="false"', 'android:exported="true"'),
            MANIFEST.replace('android:grantUriPermissions="true"', 'android:grantUriPermissions="false"'),
            MANIFEST.replace('org.freevia.sudokubuddy.scans', 'org.freevia.sudokubuddy.debug.scans'),
            MANIFEST.replace('androidx.core.content.FileProvider', 'example.UnreviewedProvider'),
        ]
        for xml in examples:
            with self.subTest(xml=xml), self.assertRaises(ValueError):
                audit_manifest(xml, 123)

    def test_backup_rules_exclude_external_photos_and_all_private_storage(self):
        resources = Path(__file__).resolve().parents[2] / "app/src/main/res/xml"
        domains = {
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        }
        legacy = ET.parse(resources / "backup_rules.xml").getroot()
        current = ET.parse(resources / "data_extraction_rules.xml").getroot()
        for rules in (legacy, current.find("cloud-backup"), current.find("device-transfer")):
            self.assertIsNotNone(rules)
            self.assertFalse(rules.findall("include"))
            excluded = {node.get("domain") for node in rules.findall("exclude") if node.get("path") == "."}
            self.assertTrue(domains <= excluded, f"Backup storage domains left exposed: {domains - excluded}")

    def test_x86_64_failure_cannot_hide_behind_valid_arm64(self):
        with tempfile.TemporaryDirectory() as folder:
            bundle = Path(folder) / "candidate.aab"
            with zipfile.ZipFile(bundle, "w") as archive:
                for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
                    for name in ("libopencv_java4.so", "libc++_shared.so"):
                        data = bytearray(elf(4096 if abi == "x86_64" else 16384))
                        if abi in ("armeabi-v7a", "x86"):
                            data[4] = 1
                        archive.writestr(f"base/lib/{abi}/{name}", data)
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError, "Load alignment"):
                audit_bundle(bundle)

    def test_missing_required_library_and_wrong_bitness_block_release(self):
        with tempfile.TemporaryDirectory() as folder:
            bundle = Path(folder) / "candidate.aab"
            for missing, wrong_bitness in ((True, False), (False, True), (False, False)):
                with zipfile.ZipFile(bundle, "w") as archive:
                    for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
                        for name in ("libopencv_java4.so", "libc++_shared.so"):
                            if missing and abi == "x86_64" and name == "libc++_shared.so":
                                continue
                            data = bytearray(elf())
                            if abi in ("armeabi-v7a", "x86") and not wrong_bitness:
                                data[4] = 1
                            archive.writestr(f"base/lib/{abi}/{name}", data)
                with contextlib.redirect_stdout(io.StringIO()):
                    if missing or wrong_bitness:
                        with self.assertRaises(ValueError):
                            audit_bundle(bundle)
                    else:
                        audit_bundle(bundle)

    def test_duplicate_zip_entries_block_release(self):
        with tempfile.TemporaryDirectory() as folder:
            bundle = Path(folder) / "candidate.aab"
            with warnings.catch_warnings(), zipfile.ZipFile(bundle, "w") as archive:
                warnings.simplefilter("ignore", UserWarning)
                archive.writestr("base/lib/arm64-v8a/libopencv_java4.so", elf())
                archive.writestr("base/lib/arm64-v8a/libopencv_java4.so", elf())
            with self.assertRaisesRegex(ValueError, "duplicate ZIP"):
                audit_bundle(bundle)

    def test_missing_bundle_abis_block_release(self):
        with tempfile.TemporaryDirectory() as folder:
            bundle = Path(folder) / "candidate.aab"
            with zipfile.ZipFile(bundle, "w") as archive:
                archive.writestr("base/lib/arm64-v8a/libopencv.so", elf())
            with self.assertRaises(ValueError):
                audit_bundle(bundle)


if __name__ == "__main__":
    unittest.main()
