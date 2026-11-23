"""Join a saved SafetyBarrier JVM run to fresh scores from the packaged V2 weights."""
import argparse
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

import round22


def compare(predictions, output):
    corpus = round22.old.load(round22.old.DATA)
    metadata = json.loads((round22.ROOT / "tools/round22-results.json").read_text())
    assert hashlib.sha256(round22.ASSET.read_bytes()).hexdigest() == metadata["sha256"]
    assert hashlib.sha256(round22.SPEC.read_bytes()).hexdigest() == metadata["spec_sha256"]
    assert hashlib.sha256(round22.old.DATA.read_bytes().replace(b"\r\n", b"\n")).hexdigest() == metadata["source"]
    with predictions.open(encoding="utf-8-sig", newline="") as stream:
        records = list(csv.DictReader(stream, delimiter="\t"))
    index = {r["id"]: r for r in records if r["category"] != "url"}
    if len(index) != sum(r["category"] != "url" for r in records) or set(index) != {r["id"] for r in corpus}:
        raise ValueError("Duplicate or unmatched prediction IDs")
    _, bias, threshold, *lines = round22.ASSET.read_text(encoding="utf-8").splitlines()
    weights = {key: float(value) for key, value in (line.split("\t") for line in lines)}
    counts = Counter()
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as stream:
        write = csv.writer(stream, delimiter="\t", lineterminator="\n").writerow
        write("id split category language label rule signals margin cues ml rule_correct ml_correct overlap".split())
        for row in corpus:
            prior = index[row["id"]]
            if any(prior[k] != row[k] for k in ("label", "category", "language")):
                raise ValueError(row["id"])
            split, = [s for s, cats in metadata["groups"].items() if row["category"] in cats]
            features = round22.features(row["input"])
            margin = float(bias) + sum(weights.get(k, 0) for k in sorted(features))
            rule = prior["prediction"] == "HIGH"
            ml = margin >= float(threshold)
            harmful = row["label"] == "harmful"
            group = ("both" if ml else "rule_only") if rule else ("ml_only" if ml else "neither")
            counts[split, row["label"], group] += 1
            write([row["id"], split, row["category"], row["language"], row["label"], prior["prediction"],
                   prior["signals"], margin, ",".join(sorted(k for k in features if k.startswith("@"))),
                   ml, rule == harmful, ml == harmful, group])
    print("SafetyBarrier report SHA256", hashlib.sha256(predictions.read_bytes()).hexdigest())
    for key, count in sorted(counts.items()):
        print(*key, count)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("predictions", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    compare(args.predictions, args.output)
