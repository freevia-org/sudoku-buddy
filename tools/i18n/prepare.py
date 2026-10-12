"""Apply editorial overrides and register complete bundled language catalogs.

No model downloads or translation occur here. Fails rather than advertising a
partially translated language. Use --available only for local incremental QA.
"""
import argparse
import json
import re
import xml.etree.ElementTree as ET
from catalog import ROOT, RES, write_resources, resource_directory

HERE = ROOT / "tools/i18n"

def read(path):
    return json.loads(path.read_text(encoding="utf-8"))

def normalize_message(source, translation):
    # Some provider JSON responses double-escape paragraph breaks. Recover the
    # intended paragraphs without touching messages that describe literal escapes.
    if "\n" in source and "\\n" not in source:
        return translation.replace("\\n", "\n")
    return translation

def bundled(tag):
    path = resource_directory(tag) / "messages.xml"
    if not path.exists(): return {}
    result = {}
    for item in ET.parse(path).getroot():
        value = item.text or ""
        if value.startswith('"') and value.endswith('"'):
            value = json.loads(value.replace("\\'", "'"))
        value = re.sub(r"%(\d+)\$s", lambda match: "{" + str(int(match[1]) - 1) + "}", value)
        result[item.attrib["name"]] = value
    return result

def prepare(available=False, from_resources=False):
    entries = read(ROOT / "app/src/main/assets/messages.json")
    keys = {entry["source"]: entry["key"] for entry in entries}
    languages = read(HERE / "languages.json")
    actions = read(HERE / "action_labels.json")
    terms = read(HERE / "solver_terms.json")
    registered = {}
    for tag, label in languages.items():
        path = HERE / f"drafts/gemini/{tag}.json"
        candidate = bundled(tag) if from_resources else read(path) if path.exists() else {}
        if not from_resources:
            for group in ("context", "context-extra", "context-evidence"):
                context_path = HERE / f"drafts/{group}/{tag}.json"
                if context_path.exists():
                    candidate.update(read(context_path))
        editorial = dict(zip(actions["keys"], actions["languages"][tag], strict=True)) if tag in actions["languages"] else {}
        if tag in terms["languages"]:
            editorial.update(dict(zip(terms["keys"], terms["languages"][tag], strict=True)))
        override_path = HERE / f"overrides/{tag}.json"
        if override_path.exists(): editorial.update(read(override_path))
        editorial["Sudoku Buddy"] = "Sudoku Buddy"
        for source, translation in editorial.items():
            if source not in keys: raise ValueError(f"{tag}: override has no source: {source}")
            assert sorted(re.findall(r"\{\d+\}", source)) == sorted(re.findall(r"\{\d+\}", translation)), source
            candidate[keys[source]] = translation
        if tag == "en":
            values = {entry["key"]: entry["source"] for entry in entries}
        elif not set(keys.values()) <= candidate.keys():
            if available: continue
            raise ValueError(f"Incomplete catalog: {tag}")
        else:
            values = {key: candidate[key] for key in keys.values()}
        values = {entry["key"]: normalize_message(entry["source"], values[entry["key"]]) for entry in entries}
        write_resources(tag, values)
        registered[tag] = label
    if available:
        # Android can select locale resources even when the language is absent
        # from locale-config. Remove our stale generated drafts during partial QA.
        for tag in languages.keys() - registered.keys():
            directory = resource_directory(tag)
            (directory / "messages.xml").unlink(missing_ok=True)
            if directory.exists() and not any(directory.iterdir()):
                directory.rmdir()
    locale_xml = ['<?xml version="1.0" encoding="utf-8"?>',
                  '<locale-config xmlns:android="http://schemas.android.com/apk/res/android">']
    locale_xml += [f'    <locale android:name="{tag}" />' for tag in registered]
    locale_xml += ['</locale-config>']
    (RES / "xml/locales_config.xml").write_text("\n".join(locale_xml) + "\n", encoding="utf-8")
    path = ROOT / "app/src/main/kotlin/org/freevia/sudokubuddy/app/AppLanguages.kt"
    code = path.read_text(encoding="utf-8")
    start = code.index("    val supported:")
    end = code.index("\n    )", start) + len("\n    )")
    lines = ['    val supported: List<Pair<String, String>> = listOf(']
    lines += [f'        "{tag}" to "{label}",' for tag, label in registered.items()]
    lines += ['    )']
    path.write_text(code[:start] + "\n".join(lines) + code[end:], encoding="utf-8")
    print(f"Prepared {len(registered)} complete languages.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--available", action="store_true")
    parser.add_argument("--from-resources", action="store_true", help="Apply editorial overrides to existing XML without local model caches")
    args = parser.parse_args()
    prepare(args.available, args.from_resources)
