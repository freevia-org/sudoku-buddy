"""Summarize the exact JVM recognition pipeline without dropping failures.

Run the Kotlin CorpusBenchmarkTest with -Ddump=true first. The optional --output
persists a machine-readable report for comparison. Labels score answer-vs-clue roles;
they do not distinguish candidates, erasures or editorial artifacts yet.
"""
import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path


def summarize(path):
    with Path(path).open(encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    cells = [r for r in rows if r["cell"] != "-1"]
    photos = {r["photo"] for r in rows}
    labelled = {r["photo"] for r in cells}
    detected = {r["photo"] for r in rows if r["gate"] == "usable"}
    result = {
        "corpus_content": "JPEG images: camera photos, screenshots and illustrations",
        "photos": len(photos), "labelled_photos": len(labelled),
        "detected_photos": len(detected), "labelled_cells": len(cells),
        "unlabelled_photos": len(photos - labelled),
        "note_artifact_ground_truth": "partial explicit annotations reported separately; other candidates/artifacts merged with empty",
        "classifier_training_overlap": "shipped classifier trained on corpus; not independent generalization",
    }
    for role_field, digit_field in [("actualRole", "actualDigit"), ("colorRole", "colorDigit"),
                                   ("grayGridRole", "grayGridDigit"), ("colorGridRole", "colorGridDigit")]:
        if not cells or role_field not in cells[0]:
            continue
        stats = {"role_correct": sum(r[role_field] == r["expectedRole"] for r in cells),
                 "cells": len(cells), "reader_unreadable_cells": sum(r[role_field] == "UNREADABLE" for r in cells)}
        for source in ["GIVEN", "GUESS", "EMPTY"]:
            selected = [r for r in cells if r["expectedRole"] == source]
            stats[source.lower()] = {
                "total": len(selected),
                "role_correct": sum(r[role_field] == source for r in selected),
                "value_correct": sum(r[digit_field] == r["expectedDigit"] for r in selected),
                "role_and_value_correct": sum(r[role_field] == source and r[digit_field] == r["expectedDigit"] for r in selected),
                "predicted_as_clue": sum(r[role_field] == "GIVEN" for r in selected),
            }
        result[role_field] = stats
    digits = [r for r in cells if r["expectedDigit"]]
    result["classifier_all_labelled_digits"] = {
        "total": len(digits),
        "correct": sum(r["classifierDigit"] == r["expectedDigit"] for r in digits),
        "missing_ink": sum(r["inkPresent"] != "true" for r in digits),
        "editorial_digits": sum(r["editorial"] == "true" for r in digits),
        "confusions": dict(Counter(f'{r["expectedDigit"]}->{r["classifierDigit"] or "missing"}' for r in digits if r["classifierDigit"] != r["expectedDigit"])),
        "by_source": {role: {
            "total": sum(r["expectedRole"] == role for r in digits),
            "correct": sum(r["expectedRole"] == role and r["classifierDigit"] == r["expectedDigit"] for r in digits),
        } for role in ["GIVEN", "GUESS"]},
    }
    by_photo = defaultdict(list)
    for row in cells:
        by_photo[row["photo"]].append(row)
    result["per_photo"] = {
        name: {
            "gray_role_correct": sum(r["actualRole"] == r["expectedRole"] for r in group),
            "color_role_correct": sum(r.get("colorRole") == r["expectedRole"] for r in group),
            "gray_role_and_value_correct": sum(r["actualRole"] == r["expectedRole"] and r["actualDigit"] == r["expectedDigit"] for r in group),
            "color_role_and_value_correct": sum(r.get("colorRole") == r["expectedRole"] and r.get("colorDigit") == r["expectedDigit"] for r in group),
        } for name, group in sorted(by_photo.items())
    }
    labels = Path(__file__).resolve().parents[2] / "corpus-labels"
    annotation_counts = Counter()
    for row in cells:
        path = labels / (Path(row["photo"]).stem + ".json")
        label = json.loads(path.read_text(encoding="utf-8"))
        index = int(row["cell"])
        if index in label.get("candidateCells", []):
            annotation_counts["candidate_cells"] += 1
            annotation_counts["gray_candidates_ignored"] += row["actualRole"] == "EMPTY"
            annotation_counts["color_candidates_ignored"] += row.get("colorRole") == "EMPTY"
            if "grayInk" in row:
                annotation_counts["gray_candidates_mark"] += row["grayInk"] == "MARK"
                annotation_counts["color_candidates_mark"] += row["colorInk"] == "MARK"
        if index in label.get("blankCells", []):
            annotation_counts["blank_cells"] += 1
            if "grayInk" in row:
                annotation_counts["gray_blank_none"] += row["grayInk"] == "NONE"
                annotation_counts["color_blank_none"] += row["colorInk"] == "NONE"
        if index in label.get("artifactCells", []):
            annotation_counts["positive_artifact_cells"] += 1
    result["explicit_annotations"] = dict(annotation_counts)
    for field in ["reader", "colorReader"]:
        if cells and field in cells[0]:
            result[field + "_photos"] = dict(Counter(group[0][field] for group in by_photo.values()))
    return result


def puzzle_groups(directory):
    """Identical clue grids stay together; this prevents same-puzzle photo leakage.

    A writer holdout still needs explicit writer identity annotations. A puzzle group
    is not a writer group and these must not be presented as interchangeable.
    """
    groups = {}
    for path in sorted(Path(directory).glob("*.json")):
        label = json.loads(path.read_text(encoding="utf-8"))
        clues = "".join(label["givens"])
        groups[Path(label["photo"]).stem] = hashlib.sha256(clues.encode("ascii")).hexdigest()[:16]
    return groups


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tsv", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = summarize(args.tsv)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "per_photo"}, indent=2))
    for name, score in report["per_photo"].items():
        change = score["color_role_correct"] - score["gray_role_correct"]
        if change:
            print(f'{name}: gray {score["gray_role_correct"]}/81 -> color {score["color_role_correct"]}/81 ({change:+d})')
