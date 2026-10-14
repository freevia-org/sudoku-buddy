"""Review suggestions must not damage Sudoku evidence or joined messages."""
import unittest

from review_cli import valid_replacement


class ReviewValidationTest(unittest.TestCase):
    def test_reordered_arguments_are_valid_but_substituted_arguments_are_not(self):
        self.assertTrue(valid_replacement("Place {0} at {1}.", "Old", "En {1}, coloca {0}.", "es"))
        self.assertFalse(valid_replacement("Place {0} at {1}.", "Old", "En {0}, coloca {0}.", "es"))

    def test_example_coordinates_and_candidate_sets_cannot_change(self):
        source = "Remove 7 at r1c3 using {1,4,7}."
        self.assertTrue(valid_replacement(source, "Old", "Elimina 7 en r1c3 usando {1,4,7}.", "es"))
        self.assertFalse(valid_replacement(source, "Old", "Elimina 7 en r1c4 usando {1,4,7}.", "es"))
        self.assertFalse(valid_replacement(source, "Old", "Elimina 7 en r1c3 usando {1,4,8}.", "es"))
        self.assertFalse(valid_replacement("Remove 7.", "Old", "Elimina 8.", "es"))

    def test_written_out_numbers_can_be_rendered_as_digits(self):
        self.assertTrue(valid_replacement("Find two cells.", "Old", "2つのマスを探します。", "ja"))

    def test_fragment_spacing_and_paragraph_breaks_cannot_disappear(self):
        self.assertTrue(valid_replacement("\nRemove {0} from ", "Old", "\nElimina {0} de ", "es"))
        self.assertFalse(valid_replacement("\nRemove {0} from ", "Old", "Elimina {0} de", "es"))

    def test_contact_destination_and_brand_cannot_change(self):
        source = "Contact Freevia: info@freevia.org"
        self.assertFalse(valid_replacement(source, "Old", "Contacta Freevia: info@example.org", "es"))
        self.assertFalse(valid_replacement(source, "Old", "Contacta Another: info@freevia.org", "es"))


if __name__ == "__main__":
    unittest.main()
