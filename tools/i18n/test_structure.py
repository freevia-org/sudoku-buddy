import unittest
from validate import structural_problems


class StructuralProblemsTest(unittest.TestCase):
    """The shape checks, each shown both to catch its defect and to leave a fair translation alone.

    A check that cannot fail is not a check, so every rule here has a case that
    breaks it; and a check that fires on honest translation is worse than none,
    so every rule also has a case it must accept.
    """

    def test_a_faithful_translation_has_no_problems(self):
        self.assertEqual([], structural_problems("Done.\n\nPress Hint (again).", "Fertig.\n\nTippen Sie (erneut) auf Tipp."))

    def test_merging_two_paragraphs_is_caught(self):
        self.assertIn("changed paragraph breaks", structural_problems("One.\n\nTwo.", "Eins. Zwei."))

    def test_adding_a_paragraph_break_is_caught(self):
        self.assertIn("changed paragraph breaks", structural_problems("One. Two.", "Eins.\nZwei."))

    def test_a_dropped_closing_bracket_is_caught(self):
        self.assertIn("unbalanced ()", structural_problems("Use it (if shown).", "Verwenden (falls angezeigt."))

    def test_a_dropped_square_bracket_is_caught(self):
        self.assertIn("unbalanced []", structural_problems("Tap [Hint] now.", "Tippen Sie [Tipp jetzt."))

    def test_a_translation_may_add_a_balanced_pair_of_its_own(self):
        # Some languages parenthesise what English leaves bare. That is style, not damage.
        self.assertEqual([], structural_problems("Hold the phone flat.", "Halten Sie das Telefon flach (waagerecht)."))

    def test_an_unbalanced_source_fragment_must_stay_unbalanced_the_same_way(self):
        # Messages are assembled from fragments at runtime, so a fragment that
        # opens a bracket another one closes is correct as it stands.
        self.assertEqual([], structural_problems("Step 1 (of", "Schritt 1 (von"))
        self.assertIn("unbalanced ()", structural_problems("Step 1 (of", "Schritt 1 von"))


if __name__ == "__main__":
    unittest.main()
