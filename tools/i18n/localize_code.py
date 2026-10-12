"""One-time presentation-boundary migration; never rewrites solver/history strings."""
import re
from catalog import ROOT, string_end

def expression_end(code, start):
    depth, i = 0, start
    while i < len(code):
        if code.startswith("//", i):
            i = code.index("\n", i)
        elif code.startswith("/*", i):
            i = code.index("*/", i + 2) + 2
        elif code[i] == '"':
            if code.startswith('"""', i): i = code.index('"""', i + 3) + 3
            else: i = string_end(code, i)
        elif code[i] in "([{": depth += 1; i += 1
        elif code[i] in ")]}":
            if depth == 0: return i
            depth -= 1; i += 1
        elif code[i] == "," and depth == 0: return i
        else: i += 1
    raise ValueError("Missing expression terminator")

for path in (ROOT / "app/src/main/kotlin/org/freevia/sudokubuddy/app").glob("*.kt"):
    if path.name in {"Localization.kt", "MessageCatalog.kt", "AppLanguages.kt", "LicenseScreen.kt"}: continue
    code = path.read_text(encoding="utf-8")
    changes, i = [], 0
    while i < len(code):
        if code.startswith("//", i):
            end = code.find("\n", i); i = len(code) if end < 0 else end
        elif code.startswith("/*", i):
            i = code.index("*/", i + 2) + 2
        elif code[i] == '"':
            if code.startswith('"""', i): i = code.index('"""', i + 3) + 3
            else: i = string_end(code, i)
        elif code.startswith("Text(", i) and (i == 0 or not code[i - 1].isalnum()):
            start = i + 5
            end = expression_end(code, start)
            argument = code[start:end]
            prefix = re.match(r"\s*(?:text\s*=\s*)?", argument).end()
            value = argument[prefix:].rstrip()
            if not value.startswith("localized("):
                changes.append((start + prefix, start + prefix + len(value), f"localized({value})"))
            i = end
        else: i += 1
    for start, end, replacement in reversed(changes):
        code = code[:start] + replacement + code[end:]
    code, descriptions = re.subn(r'contentDescription = ("[^"\n]*")', r'contentDescription = localized(\1)', code)
    if changes or descriptions:
        path.write_text(code, encoding="utf-8")
        print(f"{path.name}: {len(changes)} display messages")
