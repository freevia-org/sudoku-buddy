"""Training guards: fail on partially stale exports and group related puzzle photos."""
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from benchmark import puzzle_groups
import cells


class DataGuardsTest(unittest.TestCase):
    def test_current_analyzer_source_path_exists(self):
        self.assertTrue(Path(cells.ANALYZER).is_file())

    def test_one_fresh_file_does_not_hide_a_stale_export(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "CellAnalyzer.kt"
            source.write_text("source", encoding="utf-8")
            exports = root / "exports"
            exports.mkdir()
            old = exports / "old.f32"
            fresh = exports / "fresh.f32"
            old.write_bytes(b"old")
            fresh.write_bytes(b"fresh")
            os.utime(source, (20, 20))
            os.utime(old, (10, 10))
            os.utime(fresh, (30, 30))
            with patch.object(cells, "ANALYZER", str(source)), \
                    patch.object(cells, "NORMALISED", str(exports)), \
                    patch.object(cells, "REPO", str(root)):
                self.assertTrue(cells.export_is_stale())
                os.utime(old, (30, 30))
                self.assertFalse(cells.export_is_stale())

    def test_puzzle_group_does_not_change_as_answers_are_written(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, clues, answers in [("before", ["1........"] * 9, ["........."] * 9),
                                         ("after", ["1........"] * 9, ["123456789"] * 9),
                                         ("other", ["2........"] * 9, ["........."] * 9)]:
                (root / (name + ".json")).write_text(json.dumps({
                    "photo": name + ".jpg", "givens": clues, "written": answers,
                }), encoding="utf-8")
            groups = puzzle_groups(root)
            self.assertEqual(groups["before"], groups["after"])
            self.assertNotEqual(groups["before"], groups["other"])


if __name__ == "__main__":
    unittest.main()
