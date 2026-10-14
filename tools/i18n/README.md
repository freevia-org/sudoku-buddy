# Bundled app translations

The app uses Android message resources and follows the device language unless a
language is selected in Settings. Android 13 and later also expose that choice in
system app settings. Android 8–12 persist the choice inside the app.

The locale context wraps the Activity rather than replacing it; permission and
photo-picker launchers continue to find their ActivityResultRegistry. Locale
changes recompose the current screen without recreating its photographed puzzle.
Sudoku coordinates, solver technique identifiers and saved history formats remain
unchanged. Only their presentation is localized. Sudoku digits remain 1–9 and the
photographed grid retains its physical left-to-right coordinates in RTL locales.

## Update catalogs

1. `python tools/i18n/catalog.py` extracts English source templates deterministically.
2. Translate new entries, retaining each `{0}` argument and its occurrence count.
   `python tools/i18n/translate_cli.py` uses the signed-in official Antigravity CLI
   (`agy`). Select an available model with `--model`, for example
   `--model claude-sonnet-4-6 --workers 1`. Model selections and prompt revisions are recorded
   with draft caches.
   Alternatively, `--provider codex --workers 1` uses the signed-in Codex CLI,
   an empty temporary workspace and a JSON output schema. Its user configuration
   is excluded so unrelated MCP connections and hooks are not loaded.
   Only public English messages are submitted; no photos, saved puzzles, app source
   or credentials are included. CI needs no model, CLI sign-in or network access.
3. Add editorial translations keyed by English source to `overrides/<tag>.json`.
   The central action-label glossary is `action_labels.json`.
   `solver_terms.json` covers dynamic explanatory words and contact notices.
   Finite row/column/box choices are also expanded into contextual sentence
   templates, so translations can use the correct grammatical case and gender.
4. `python tools/i18n/prepare.py` applies overrides and registers only complete
   catalogs in both the language picker and Android locale configuration.
5. `python tools/i18n/validate.py` verifies coverage and interpolation arguments.

On a fresh checkout, `prepare.py --from-resources` applies editorial overrides to
the committed XML directly. Generated draft caches are ignored by Git. Native
reviewers can edit overrides without installing translation models or PyTorch.

`prepare.py --available` is for incremental local QA only. The final preparation
must succeed without that option for every language listed in `languages.json`.
Use native language names in the picker so users can recover from a mistaken choice.

Hebrew and Indonesian use Android's legacy resource directory codes `values-iw`
and `values-in`, with modern `he` and `id` in the picker and locale configuration.
Lint can report these aliases as `UnusedTranslation`; the offline validator checks
their coverage through the corresponding resource directories.

During incremental authoring, `translate_cli.py --provider codex --context-only`
can translate newly extracted variants into a separate cache before regenerating
the English catalog. `prepare.py` combines both caches; `--from-resources` still
requires neither cache. This keeps in-progress variants separate from completed
language drafts.

The `context-extra` and `context-evidence` cache groups isolate later additions.
Evidence sentences include both the verb and the numbered row, column or box;
the renderer constrains their arguments to Sudoku digits and coordinates so a
match cannot consume a preceding paragraph.

## Translation quality and release review

`python tools/i18n/review_cli.py --workers 3` reviews every bundled catalog using
the signed-in Codex CLI. It submits only public English messages and their bundled
translations, in an empty temporary workspace with tools and user configuration
excluded. Findings are cached in the ignored `drafts/review/` directory. A second
request independently checks proposed corrections; damaged arguments, candidate
sets, brands and fragment spacing are rejected before application.

`review_cli.py --apply-completed` copies verified findings to editorial overrides.
Then run `prepare.py --from-resources`, `validate.py`, and the offline Python tests.
Review uncertain findings separately; the model's coverage count is not native
speaker certification. Preparation preserves the English boundary whitespace,
so applying overrides repeatedly cannot duplicate tutor paragraph breaks.

Initial catalogs are machine-assisted drafts. Completeness and placeholder checks
do **not** certify linguistic quality. Native-language review is still needed,
especially for consent/privacy notices and advanced Sudoku explanations. Do not
describe the draft catalogs as native-reviewed or publication-ready. Review the
capture and sharing actions separately: taking a photo does not upload it.

Generation includes Sudoku, camera and sharing context and validates every dynamic
argument and product name before saving a draft. Editorial overrides
take precedence over generated drafts. Original third-party license notices remain
verbatim; navigation around those notices is localized.

Antigravity is an optional authoring tool, not an app dependency. Translations ship
as Android resources and work offline. See the official
[Antigravity headless-mode documentation](https://www.antigravity.google/docs/cli/headless/)
for CLI authentication and output formats.
