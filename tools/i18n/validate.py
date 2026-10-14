"""Validate bundled catalogs without models, network access, or Android tooling."""
import json
import re
import xml.etree.ElementTree as ET
from catalog import ROOT, RES, extract, resource_directory
from prepare import bundled
from translate_cli import brand_count, literal_notation

def validate_actions(entries, decoded, language, actions):
    keys = {entry["source"]: entry["key"] for entry in entries}
    labels = dict(zip(actions["keys"], actions["languages"][language], strict=True))
    for source, label in labels.items():
        assert decoded[keys[source]] == label, f"{language}: action glossary differs from bundled {source}"
    for entry in entries:
        # Explicit UI commands name the button itself, not a translated verb.
        # Quoted labels can retain their spelling in grammatically inflected prose.
        for action in re.findall(r"\b(?:Use|Press|Tap) (Read|Check|Hint|Solve)\b", entry["source"]):
            assert labels[action].casefold() in decoded[entry["key"]].casefold(), f"{language}/{entry['key']}: missing {action} button label"

def validate():
    entries = json.loads((ROOT / "app/src/main/assets/messages.json").read_text(encoding="utf-8"))
    assert entries == extract(write=False), "English catalog needs regeneration after source changes"
    expected = {entry["key"]: entry["source"] for entry in entries}
    assert len(expected) == len(entries), "Duplicate message keys"
    locales = [node.attrib["{http://schemas.android.com/apk/res/android}name"]
               for node in ET.parse(RES / "xml/locales_config.xml").getroot()]
    languages = json.loads((ROOT / "tools/i18n/languages.json").read_text(encoding="utf-8"))
    actions = json.loads((ROOT / "tools/i18n/action_labels.json").read_text(encoding="utf-8"))
    assert locales == list(languages), "Not all requested languages are bundled"
    language_code = (ROOT / "app/src/main/kotlin/org/freevia/sudokubuddy/app/AppLanguages.kt").read_text(encoding="utf-8")
    picker = language_code.split("val supported:", 1)[1].split("fun matchingTag", 1)[0]
    assert locales == re.findall(r'"([a-zA-Z-]+)" to ', picker), "Language picker differs from Android locale configuration"
    for language in locales:
        path = resource_directory(language) / "messages.xml"
        root = ET.parse(path).getroot()
        messages = {node.attrib["name"]: node.text or "" for node in root}
        decoded = bundled(language)
        validate_actions(entries, decoded, language, actions)
        assert len(root) == len(messages), f"{language}: duplicate keys"
        assert messages.keys() == expected.keys(), f"{language}: incomplete catalog"
        for key, value in messages.items():
            source = expected[key]
            wanted = sorted(str(int(n) + 1) for n in re.findall(r"\{(\d+)\}", source))
            actual = sorted(re.findall(r"%(\d+)\$s", value))
            assert wanted == actual, f"{language}/{key}: damaged arguments"
            assert value.strip('"').strip(), f"{language}/{key}: empty translation"
            assert not re.search(r"ZXQ|98765\d", value), f"{language}/{key}: leftover marker"
            assert literal_notation(source) == literal_notation(decoded[key]), f"{language}/{key}: changed candidate set or email"
            for boundary in (r"^\s*", r"\s*$"):
                assert re.search(boundary, source).group() == re.search(boundary, decoded[key]).group(), f"{language}/{key}: changed fragment spacing"
            for brand in ("Sudoku Buddy", "Freevia"):
                assert source.count(brand) == brand_count(decoded[key], brand, language), f"{language}/{key}: changed brand"
            if "\n" in source and "\\n" not in source:
                assert "\\n" not in decoded[key], f"{language}/{key}: literal escaped paragraph break"
    print(f"Validated {len(locales)} complete catalogs, {len(entries)} messages each.")

if __name__ == "__main__": validate()
