"""Generate the small glossary of interpolated solver words with Codex."""
import json
import argparse
import shutil
import re
from collections import Counter
from translate_cli import HERE, codex_json, read, write

TERMS = "none one two three four five six seven eight contains was were rows columns unit".split()


def main(terms=TERMS):
    languages = {tag: name for tag, name in read(HERE / "languages.json").items() if tag != "en"}
    term_schema = {"type": "object", "properties": {term: {"type": "string"} for term in terms},
                   "required": terms, "additionalProperties": False}
    schema = {"type": "object", "properties": {tag: term_schema for tag in languages},
              "required": list(languages), "additionalProperties": False}
    prompt = (
        "Translate this glossary for the Android Sudoku Buddy tutor into every listed language. "
        "Return the required JSON object: language code -> English term -> translation. "
        "Do not call tools, inspect files, use skills, search or delegate. "
        "These are words interpolated into explanatory sentences, not commands or identifiers. "
        "none means no digits; one through eight are counts of digits. contains is the verb in "
        "'cell r1c1 contains 7'. was/were are auxiliary verbs in '7 was ruled out' or "
        "'7 and 8 were ruled out'. rows/columns are plural Sudoku grid units. unit is a generic "
        "Sudoku row, column or 3x3 box. Use concise, consistent, established Sudoku terminology "
        "and the grammatical forms most natural in these contexts. Every term must be translated. "
        "Additional full sentences are ordinary app text. Preserve Sudoku Buddy, Freevia, "
        "email addresses, URLs and numbered placeholders such as {0} exactly. "
        "For 'Check {0} uncertain squares.' use a natural number-independent label meaning "
        "'Uncertain squares to check: {0}.' For '{0} squares' use a number-independent label "
        "meaning 'Number of squares: {0}'. These counts vary, so do not require a different "
        "noun ending for two, three or five. Preserve the original meaning.\n"
        + json.dumps({"languages": languages, "terms": terms}, ensure_ascii=False)
    )
    cli = shutil.which("codex")
    if not cli:
        raise RuntimeError("Sign in to the Codex CLI first")
    values = codex_json(cli, None, prompt, schema)
    if values.keys() != languages.keys():
        raise ValueError("Glossary changed languages")
    for tag, terms in values.items():
        if terms.keys() != set(term_schema["required"]) or any(not isinstance(v, str) or not v.strip() for v in terms.values()):
            raise ValueError(f"Incomplete glossary: {tag}")
        for source, translated in terms.items():
            if Counter(re.findall(r"\{\d+\}", source)) != Counter(re.findall(r"\{\d+\}", translated)):
                raise ValueError(f"Changed interpolation arguments: {tag}/{source}")
            for literal in ("Sudoku Buddy", "Freevia", "info@freevia.org"):
                if source.count(literal) != translated.count(literal):
                    raise ValueError(f"Changed brand or contact address: {tag}/{source}")
    path = HERE / "solver_terms.json"
    previous = read(path) if path.exists() else {"keys": [], "languages": {}}
    keys = list(dict.fromkeys(previous["keys"] + term_schema["required"]))
    merged = {}
    for tag in languages:
        translations = dict(zip(previous["keys"], previous["languages"].get(tag, [])))
        translations.update(values[tag])
        merged[tag] = [translations[key] for key in keys]
    write(path, {"keys": keys, "languages": merged})
    print(f"Translated {len(term_schema['required'])} fragments into {len(languages)} languages.", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--terms", nargs="+")
    main(parser.parse_args().terms or TERMS)
