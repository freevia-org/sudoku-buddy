"""Commands must reference the actual buttons, despite editorial overrides."""
import unittest

from validate import validate_actions


class ActionLabelsTest(unittest.TestCase):
    def setUp(self):
        self.entries = [{"source": "Read", "key": "read"},
                        {"source": "Use Read to compare the photo.", "key": "command"}]
        self.actions = {"keys": ["Read"], "languages": {"uk": ["Розпізнане"]}}

    def test_quoted_actual_label_is_valid(self):
        validate_actions(self.entries, {"read": "Розпізнане",
            "command": "Скористайтеся кнопкою «Розпізнане», щоб порівняти фото."}, "uk", self.actions)

    def test_instruction_naming_another_button_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, "missing Read button label"):
            validate_actions(self.entries, {"read": "Розпізнане",
                "command": "Використовуйте Переглянути розпізнавання."}, "uk", self.actions)

    def test_override_cannot_silently_drift_from_glossary(self):
        with self.assertRaisesRegex(AssertionError, "glossary differs"):
            validate_actions(self.entries, {"read": "Перегляд", "command": "Перегляд"}, "uk", self.actions)


if __name__ == "__main__":
    unittest.main()
