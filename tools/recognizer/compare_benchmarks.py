"""Compare two comprehensive labelled-cell TSVs without hiding individual regressions."""
import argparse
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path


def compare(before_path, after_path):
    def load(path):
        with Path(path).open(encoding="utf-8", newline="") as stream:
            return {(row["photo"], int(row["cell"])): row for row in csv.DictReader(stream, delimiter="\t")
                    if int(row["cell"]) >= 0}

    before, after = load(before_path), load(after_path)
    if set(before) != set(after):
        raise ValueError("benchmarks must cover exactly the same labelled cells")
    totals = Counter()
    per_photo = defaultdict(Counter)
    fixes, regressions, output_regressions, gray_regressions, gray_output_regressions = [], [], [], [], []
    raw_exact_regressions, gray_exact_regressions = [], []
    for key in sorted(before):
        old, new = before[key], after[key]
        if (old["expectedRole"], old["expectedDigit"]) != (new["expectedRole"], new["expectedDigit"]):
            raise ValueError(f"{key}: ground truth differs")
        expected = new["expectedRole"], new["expectedDigit"]
        old_raw = old["colorRole"], old["colorDigit"]
        new_raw = new["colorRole"], new["colorDigit"]
        old_grid = old["colorGridRole"], old["colorGridDigit"]
        new_grid = new["colorGridRole"], new["colorGridDigit"]
        old_gray = old["actualRole"], old["actualDigit"]
        new_gray = new["actualRole"], new["actualDigit"]
        old_gray_grid = old["grayGridRole"], old["grayGridDigit"]
        new_gray_grid = new["grayGridRole"], new["grayGridDigit"]
        scores = {"raw_role_before": old_raw[0] == expected[0], "raw_role_after": new_raw[0] == expected[0],
                  "raw_exact_before": old_raw == expected, "raw_exact_after": new_raw == expected,
                  "output_exact_before": old_grid == expected, "output_exact_after": new_grid == expected,
                  "classifier_changed": old["classifierDigit"] != new["classifierDigit"],
                  "role_uncertain_after": new.get("colorRoleUncertain") == "true",
                  "gray_role_before": old_gray[0] == expected[0], "gray_role_after": new_gray[0] == expected[0],
                  "gray_exact_before": old_gray == expected, "gray_exact_after": new_gray == expected,
                  "gray_output_exact_before": old_gray_grid == expected, "gray_output_exact_after": new_gray_grid == expected}
        for name, value in scores.items():
            totals[name] += value
            per_photo[key[0]][name] += value
        detail = {"photo": key[0], "cell": key[1], "expected": expected, "before": old_raw, "after": new_raw}
        if scores["raw_role_before"] and not scores["raw_role_after"]:
            regressions.append(detail)
        if scores["raw_exact_before"] and not scores["raw_exact_after"]:
            raw_exact_regressions.append(detail)
        if not scores["raw_role_before"] and scores["raw_role_after"]:
            fixes.append(detail)
        if scores["output_exact_before"] and not scores["output_exact_after"]:
            output_regressions.append({"photo": key[0], "cell": key[1], "expected": expected,
                                       "before": old_grid, "after": new_grid})
        if scores["gray_role_before"] and not scores["gray_role_after"]:
            gray_regressions.append({"photo": key[0], "cell": key[1], "expected": expected,
                                     "before": old_gray, "after": new_gray})
        if scores["gray_exact_before"] and not scores["gray_exact_after"]:
            gray_exact_regressions.append({"photo": key[0], "cell": key[1], "expected": expected,
                                          "before": old_gray, "after": new_gray})
        if scores["gray_output_exact_before"] and not scores["gray_output_exact_after"]:
            gray_output_regressions.append({"photo": key[0], "cell": key[1], "expected": expected,
                                            "before": old_gray_grid, "after": new_gray_grid})
    photos = sorted(per_photo)
    outcomes = {}
    for name, records in (("before", before), ("after", after)):
        outcomes[name] = dict(Counter(records[(photo, 0)]["colorReader"] for photo in photos))
    return {"before": str(before_path), "after": str(after_path), "labelled_cells": len(after),
            "labelled_images": len(photos), **totals, "reader_outcomes": outcomes,
            "role_fixes": fixes, "new_raw_role_errors": regressions, "new_output_errors": output_regressions,
            "new_gray_raw_role_errors": gray_regressions, "new_gray_output_errors": gray_output_regressions,
            "new_raw_exact_errors": raw_exact_regressions, "new_gray_raw_exact_errors": gray_exact_regressions,
            "per_photo": dict(per_photo)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--corpus", type=Path)
    parser.add_argument("--labels", type=Path)
    args = parser.parse_args()
    report = compare(args.before, args.after)
    if args.corpus or args.labels:
        if not args.corpus or not args.labels:
            parser.error("--corpus and --labels must be supplied together")
        images = sorted(path.name for path in args.corpus.iterdir() if path.suffix.lower() in (".jpg", ".jpeg", ".png"))
        expected_ids = sorted(name for name in images if (args.labels / (Path(name).stem + ".json")).is_file())
        if not expected_ids or sorted(report["per_photo"]) != expected_ids or report["labelled_cells"] != len(expected_ids) * 81:
            raise ValueError("benchmark image IDs/cell counts do not match the current labelled corpus")
        report["verified_dataset"] = {"corpus_image_count": len(images), "labelled_image_count": len(expected_ids),
                                      "labelled_cell_count": len(expected_ids) * 81,
                                      "labelled_image_ids": expected_ids, "paired_gray_rgb_dataset": True}
    if args.output:
        args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({name: value for name, value in report.items() if name != "per_photo"}, indent=2))
