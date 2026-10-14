"""Extract presentation messages without changing solver identifiers or saved formats.

Catalog extraction is deterministic and offline. Translation generation is a separate
optional build-time tool; it is never run by the app or CI.
"""
import hashlib
import itertools
import json
import re
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
ASSETS = ROOT / "app/src/main/assets"
SOLVER_WORDS = set("none one two three four five six seven eight contains was were rows columns unit".split())
# These source literals are protocol/debug identifiers, never displayed through
# localized(). Date-format patterns intentionally remain presentation resources.
NON_PRESENTATION_LITERALS = {
    "Content-Type", "X-Sudoku-Training-Consent", "CaptureEvidence", "UnsafeOptInUsageError",
}

def evidence_sources():
    # NakedSingle/HiddenSingle concatenate a verb between literals, and
    # sharedUnit returns a numbered unit. Translate the assembled sentences.
    return {
        sentence
        for verb in ("contains", "can only hold")
        for sentence in (
            f"{{0}} {verb} {{1}}.",
            *(f"{{0}} is excluded by {{1}}, which {verb} {{2}} in the same {unit} {{3}}."
              for unit in ("row", "column", "box")),
            *(f"{{0}} cannot hold {{1}} because {{2}} {verb} {{3}} in the same {unit} {{4}}."
              for unit in ("row", "column", "box")),
        )
    }

def string_end(source, start):
    i = start + 1
    while i < len(source):
        if source[i] == "\\":
            i += 2
        elif source.startswith("${", i):
            i = interpolation_end(source, i + 2)
        elif source[i] == '"':
            return i + 1
        else:
            i += 1
    raise ValueError("Unclosed Kotlin string")

def interpolation_end(source, start):
    depth, i = 1, start
    while i < len(source) and depth:
        if source[i] == '"':
            i = string_end(source, i)
            continue
        if source[i] == "{": depth += 1
        if source[i] == "}": depth -= 1
        i += 1
    return i

def literals(source):
    result, i = [], 0
    while i < len(source):
        if source.startswith("//", i):
            end = source.find("\n", i)
            i = len(source) if end < 0 else end
        elif source.startswith("/*", i):
            depth, i = 1, i + 2
            while depth:
                if source.startswith("/*", i): depth += 1; i += 2
                elif source.startswith("*/", i): depth -= 1; i += 2
                else: i += 1
        elif source.startswith('"""', i):
            i = source.index('"""', i + 3) + 3
        elif source[i] == '"':
            end = string_end(source, i)
            result.append((i, end, source[i + 1:end - 1]))
            i = end
        elif source[i] == "'":
            i += 1
            while i < len(source):
                if source[i] == "\\": i += 2
                elif source[i] == "'": i += 1; break
                else: i += 1
        else:
            i += 1
    return result

def template(raw):
    parts, i, count = [], 0, 0
    while i < len(raw):
        if raw.startswith("${", i):
            i = interpolation_end(raw, i + 2)
            parts.append(f"{{{count}}}"); count += 1
        elif raw[i] == "$" and re.match(r"[A-Za-z_]", raw[i + 1:i + 2]):
            end = re.match(r"\$[A-Za-z_]\w*", raw[i:]).end()
            i += end; parts.append(f"{{{count}}}"); count += 1
        elif raw[i] == "\\" and i + 1 < len(raw):
            parts.append({"n":"\n", "t":"\t", "r":"\r"}.get(raw[i + 1], raw[i + 1]))
            i += 2
        else:
            parts.append(raw[i]); i += 1
    return "".join(parts)

def solver_variants(raw, filename):
    """Resolve finite English word choices before translating whole sentences.

    Case and gender depend on the surrounding sentence. Translating a bare
    nominative 'box' cannot produce the right form for 'in the same box'.
    Solver identifiers and stored English explanations remain unchanged.
    """
    choices, i = {}, 0
    bare = {"lineWord": ("rows", "columns"), "crossWord": ("columns", "rows")}
    if filename == "SingleDigit.kt":
        bare.update(word=("rows", "columns"), across=("column", "row"))
    if filename == "Colouring.kt":
        bare["name"] = ("one", "the other")
    while i < len(raw):
        if raw.startswith("${", i):
            end = interpolation_end(raw, i + 2)
            token, expression = raw[i:end], raw[i + 2:end - 1]
            if expression.startswith(("unitName(", "sharedUnit(")):
                choices[token] = ("row", "column", "box")
            elif expression.startswith("chain.deadEndUnit"):
                choices[token] = ("row", "column", "box", "unit")
            else:
                conditional = re.fullmatch(r'if\s*\(.*\)\s*"([^"$]*)"\s*else\s*"([^"$]*)"', expression)
                if conditional:
                    choices[token] = conditional.groups()
            i = end
        elif raw[i] == "$":
            match = re.match(r"\$([A-Za-z_]\w*)", raw[i:])
            if match:
                if match[1] in bare:
                    choices[match[0]] = bare[match[1]]
                i += len(match[0])
            else:
                i += 1
        else:
            i += 1
    result = set()
    for replacements in itertools.product(*choices.values()):
        selected = dict(zip(choices, replacements, strict=True))
        if "$lineWord" in selected and "$crossWord" in selected and selected["$lineWord"] == selected["$crossWord"]:
            continue
        if "$word" in selected and "$across" in selected and selected["$word"].removesuffix("s") == selected["$across"]:
            continue
        variant = raw
        for token, replacement in zip(choices, replacements, strict=True):
            variant = variant.replace(token, replacement)
        result.add(template(variant))
        count = re.search(r"\$\{counts\[8\s*-\s*gone\.size\]\}", variant)
        if filename == "NakedSingle.kt" and count:
            # The count also needs grammatical case/gender. Here one removed
            # candidate means seven visible exclusions; two or more use 'were'.
            for word in ("one", "two", "three", "four", "five", "six", "seven"):
                if (" was ruled " in variant) != (word == "seven"):
                    continue
                result.add(template(variant.replace(count[0], word)))
    return result

def extract(write=True):
    sources = set()
    paths = list((ROOT / "app/src/main/kotlin").rglob("*.kt"))
    for module in ("solver", "recognize", "vision"):
        paths += list((ROOT / f"core/{module}/src/main/kotlin").rglob("*.kt"))
    for path in paths:
        if path.name in {"Localization.kt", "MessageCatalog.kt", "AppLanguages.kt", "LicenseScreen.kt"}: continue
        code = path.read_text(encoding="utf-8")
        strings = literals(code)
        i = 0
        while i < len(strings):
            start, end, raw = strings[i]
            while i + 1 < len(strings) and re.fullmatch(r"\s*\+\s*", code[end:strings[i + 1][0]]):
                i += 1
                raw += strings[i][2]; end = strings[i][1]
            value = template(raw)
            if (re.search(r"[A-Za-z]{2}", value) and not re.search(r"[A-Za-z]:/", value)
                    and value not in NON_PRESENTATION_LITERALS
                    # Serialization property names are protocol data, not UI.
                    and not re.search(r'"[A-Za-z_]\w*"\s*:', value)
                    and (" " in value or value[:1].isupper() or value.strip() in SOLVER_WORDS | {"row", "column", "box", "and", "or", "empty"})
                    and len(value) < 6000):
                sources.add(value)
                if "core/solver/" in path.as_posix():
                    sources.update(solver_variants(raw, path.name))
            i += 1
    sources.update(["Language", "Use device language", "Choose language", "Cancel", "English", "finished"])
    sources.update(SOLVER_WORDS)
    sources.update(evidence_sources())
    sources.update(("This fills nothing in. It rules {0} out of one square.",
                    "This fills nothing in. It rules {0} out of {1} squares."))
    # These technique identifiers are assembled by the solver. Translate each full
    # name so adjective endings agree with the noun in the destination language.
    sources.update(f"{kind} {size}" for kind in ("Naked", "Hidden") for size in ("pair", "triple", "quad"))
    entries = []
    for source in sorted(sources):
        slug = re.sub(r"[^a-z0-9]+", "_", source.lower()).strip("_")[:45]
        entries.append({"source": source, "key": f"msg_{slug}_{hashlib.sha256(source.encode()).hexdigest()[:8]}"})
    if write:
        ASSETS.mkdir(exist_ok=True)
        (ASSETS / "messages.json").write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        write_resources("en", {entry["key"]: entry["source"] for entry in entries})
    return entries

def resource_directory(language):
    known = {"en":"values", "pt-BR":"values-pt-rBR", "zh-CN":"values-zh-rCN", "zh-TW":"values-zh-rTW",
             "he":"values-iw", "id":"values-in"}
    qualifier = known.get(language)
    if qualifier is None:
        qualifier = "values-b+" + language.replace("-", "+") if len(language) > 2 else "values-" + language
    return RES / qualifier

def write_resources(language, values):
    directory = resource_directory(language)
    directory.mkdir(exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for key, value in sorted(values.items()):
        value = re.sub(r"\{(\d+)\}", lambda m: f"%{int(m[1]) + 1}$s", value)
        value = value.replace("\\", "\\\\").replace('"', '\\"').replace("'", "\\'").replace("\n", "\\n").replace("\t", "\\t")
        lines.append(f'    <string name="{key}" formatted="false">"{escape(value)}"</string>')
    lines.append('</resources>')
    (directory / "messages.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    entries = extract()
    print(f"Catalog: {len(entries)} messages")
