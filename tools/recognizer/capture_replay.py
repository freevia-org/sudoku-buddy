"""Validate DEBUG capture evidence and prepare byte-exact JVM inference replay.

python tools/recognizer/capture_replay.py <capture-directory>
./gradlew :core:recognize:test --tests '*ExactCaptureReplayTest*' -Dprobe=<capture-directory>/replay.tsv

No JPEG decoding, grid fitting, cell cropping, or classifier inference occurs here.
"""

import argparse
import csv
import hashlib
import json
from pathlib import Path


def prepare(directory: Path) -> Path:
    directory = directory.resolve()
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    if manifest["schemaVersion"] != 1 or manifest["indexBase"] != 0:
        raise ValueError("unsupported capture evidence schema")
    if manifest["digitProbabilityOrder"] != list(range(1, 10)):
        raise ValueError("unsupported digit probability order")
    cells = sorted(manifest["cells"], key=lambda cell: cell["index"])
    if [cell["index"] for cell in cells] != list(range(81)):
        raise ValueError("capture must contain each of the 81 cells exactly once")
    color_count = sum("rgb" in cell for cell in cells)
    if color_count not in (0, 81):
        raise ValueError("RGB cells must be present for all 81 cells or none")

    def raw(record, channels, encoding):
        path = (directory / record["file"]).resolve()
        if path.parent != directory or record["encoding"] != encoding:
            raise ValueError("invalid raw cell path or encoding")
        data = path.read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        if len(data) != record["bytes"] or len(data) != record["width"] * record["height"] * channels:
            raise ValueError(f"{path.name}: wrong byte count")
        if digest != record["sha256"]:
            raise ValueError(f"{path.name}: SHA256 mismatch")
        return str(path), record["width"], record["height"], digest

    rows = []
    for cell in cells:
        gray = raw(cell["gray"], 1, "gray8-row-major")
        rgb = raw(cell["rgb"], 3, "rgb8-interleaved-row-major") if "rgb" in cell else ("", "", "", "")
        if rgb[0] and gray[1:3] != rgb[1:3]:
            raise ValueError("gray/RGB dimensions differ")
        reading = cell.get("reading") or {}
        grid = cell.get("gridCell") or {}
        rows.append([
            cell["index"], *gray, *rgb,
            manifest["outcome"], str(cell["index"] in manifest["uncertainCells"]).lower(),
            reading.get("ink", ""), reading.get("digit") or "",
            ",".join(map(str, reading.get("probabilities") or [])),
            reading.get("margin", ""), reading.get("heightRatio", ""), reading.get("darkness", ""),
            reading.get("colorChroma") if reading.get("colorChroma") is not None else "",
            str(reading.get("roleUncertain", False)).lower(),
            grid.get("source", ""), grid.get("digit") or "",
        ])
    output = directory / "replay.tsv"
    with output.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream, delimiter="\t")
        writer.writerow([
            "index", "grayFile", "grayWidth", "grayHeight", "graySha256",
            "rgbFile", "rgbWidth", "rgbHeight", "rgbSha256", "outcome", "uncertain",
            "ink", "digit", "probabilities", "margin", "heightRatio", "darkness", "colorChroma",
            "roleUncertain", "gridRole", "gridDigit",
        ])
        writer.writerows(rows)
    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture_directory", type=Path)
    print(prepare(parser.parse_args().capture_directory))
