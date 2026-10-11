"""Coverage regressions for presentation fragments and privacy notices."""
import unittest
from catalog import extract, solver_variants, evidence_sources


class CatalogTest(unittest.TestCase):
    def test_assembled_evidence_includes_the_verb_and_numbered_unit(self):
        sources = {entry["source"] for entry in extract(write=False)}
        self.assertEqual(14, len(evidence_sources()))
        self.assertTrue(evidence_sources() <= sources)
        self.assertIn("{0} is excluded by {1}, which contains {2} in the same row {3}.", sources)

    def test_unit_phrases_are_translated_with_their_grammatical_context(self):
        self.assertEqual({" {0} in the same row.", " {0} in the same column.", " {0} in the same box."},
                         solver_variants(" $value in the same ${sharedUnit(index, peer)}.", "NakedSingle.kt"))
        self.assertEqual({"{0} was ruled out.", "{0} were ruled out."},
                         solver_variants('${list(gone)} ${if (gone.size == 1) "was" else "were"} ruled out.', "NakedSingle.kt"))

    def test_solver_interpolation_words_are_not_filtered_out(self):
        sources = {entry["source"] for entry in extract(write=False)}
        self.assertTrue({"none", "one", "three", "eight", "contains", "was", "were",
                         "rows", "columns", "unit"} <= sources)

    def test_contact_address_does_not_exclude_feedback_privacy_notice(self):
        sources = {entry["source"] for entry in extract(write=False)}
        self.assertIn("No images are attached automatically. Reports and attachments submitted here "
                      "are public. Use info@freevia.org for examples you prefer to send privately.", sources)


if __name__ == "__main__":
    unittest.main()
