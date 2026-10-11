"""Regressions from real CLI output; no model, credentials or network needed."""
import unittest
import tempfile
from pathlib import Path
from unittest.mock import patch
from translate_cli import brand_count, parse_response, literal_notation, select_context_entries, translate, TranslationRejected, read
from prepare import normalize_message


class TranslationImportTest(unittest.TestCase):
    def test_one_rejected_message_does_not_resend_valid_parent_messages(self):
        entries = [{"key": "msg_read", "source": "Read"},
                   {"key": "msg_check", "source": "Check"},
                   {"key": "msg_submit", "source": "Submit {0}"}]
        with tempfile.TemporaryDirectory(prefix="sudoku-translation-test-") as directory:
            with patch("translate_cli.CACHE_DIR", Path(directory)), patch("translate_cli.codex_json", side_effect=[
                {"0": "Lesen", "1": "Prüfen", "2": "Falsch"}, {"0": "Falsch"},
            ]) as provider:
                with self.assertRaises(TranslationRejected):
                    translate("codex.exe", "default", "de", "Deutsch", entries)
                self.assertEqual(2, provider.call_count)
                self.assertEqual({"msg_read": "Lesen", "msg_check": "Prüfen"}, read(Path(directory) / "de.json"))

    def test_context_translation_can_resume_after_english_catalog_regeneration(self):
        entries = [{"key": "a", "source": "A"}, {"key": "b", "source": "B"}]
        self.assertEqual(entries[1:], select_context_entries(entries, entries, entries[1:]))
        with self.assertRaises(ValueError):
            select_context_entries(entries, entries, [])

    def test_candidate_sets_and_contact_addresses_keep_their_meaning(self):
        self.assertEqual(literal_notation("Use {1,6} at {0}; info@freevia.org"),
                         literal_notation("info@freevia.org: {0} → {6, 1}"))
        self.assertNotEqual(literal_notation("Use {1,6}"), literal_notation("Use {1,7}"))

    def test_double_escaped_tutor_paragraphs_display_as_paragraphs(self):
        self.assertEqual("Erster Absatz.\n\nZweiter Absatz.",
                         normalize_message("First paragraph.\n\nSecond paragraph.",
                                           "Erster Absatz.\\n\\nZweiter Absatz."))
        self.assertEqual("Literal \\n", normalize_message("Literal \\n", "Literal \\n"))

    def test_valid_json_preserves_unicode_and_sudoku_arguments(self):
        self.assertEqual({"0": "候補 {{1}} を {0} に入れる。"},
                         parse_response('{"0": "候補 {{1}} を {0} に入れる。"}'))

    def test_unescaped_quoted_labels_are_recovered_without_changing_display_text(self):
        self.assertEqual({"0": 'Look under "Would not read".'},
                         parse_response('{"0": "Look under "Would not read"."}'))

    def test_truncation_keeps_only_complete_string_values_for_retry(self):
        self.assertEqual({"0": "Complete"},
                         parse_response('{\n"0": "Complete",\n"1": "unfinished'))

    def test_duplicate_keys_do_not_silently_replace_a_translation(self):
        with self.assertRaises(ValueError):
            parse_response('{"0": "First", "0": "Different"}')

    def test_hungarian_brand_inflection_retains_brand_identity(self):
        self.assertEqual(1, brand_count("Megosztás a Freeviával", "Freevia", "hu"))
        self.assertEqual(0, brand_count("Megosztás a Freviával", "Freevia", "hu"))


if __name__ == "__main__":
    unittest.main()
