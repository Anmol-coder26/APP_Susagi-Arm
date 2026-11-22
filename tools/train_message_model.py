"""Train a bounded byte-trigram Bernoulli naive Bayes model. Standard library only."""
import csv
import hashlib
import json
import math
import random
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "app/src/test/resources/safety/directive17.tsv"
ASSET = ROOT / "app/src/main/assets/security/message-v1.txt"
REPORT = ROOT / "tools/message-model-evaluation.json"
DIM = 512


def features(text):
    # Bound by Unicode scalar count before encoding; lowercase ASCII bytes only.
    b = text[:2048].encode("utf-8").lower()
    return {(a * 31 * 31 + c * 31 + d) % DIM for a, c, d in zip(b, b[1:], b[2:])}


def load(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t", strict=True)
        if reader.fieldnames != ["id", "category", "language", "label", "input"]:
            raise ValueError("Unexpected fixture schema")
        rows = []
        seen = set()
        ids = set()
        for row in reader:
            if None in row or not all(row.values()) or row["label"] not in {"harmful", "benign"}:
                raise ValueError(f"Invalid record at line {reader.line_num}")
            if row["category"] == "url":
                continue
            words = frozenset(row["input"].casefold().split())
            if not words or row["id"] in ids or any(len(words & other) / len(words | other) >= .85 for other in seen):
                raise ValueError(f"Duplicate or empty example at line {reader.line_num}")
            ids.add(row["id"])
            seen.add(words)
            rows.append(row)
    return rows


def fit(rows):
    counts = [Counter(), Counter()]
    sizes = [0, 0]
    for row in rows:
        label = int(row["label"] == "harmful")
        sizes[label] += 1
        counts[label].update(features(row["input"]))
    if min(sizes) == 0:
        raise ValueError("Training requires both labels")
    bias = math.log(sizes[1] / sizes[0])
    weights = []
    for i in range(DIM):
        p, q = [(counts[k][i] + 1) / (sizes[k] + 2) for k in (1, 0)]
        bias += math.log((1-p)/(1-q))
        weights.append(math.log(p/(1-p)) - math.log(q/(1-q)))
    return bias, weights


def summarize(scores, threshold):
    tp, fp, tn, fn = [sum((score >= threshold) == pred and label == truth for score, label in scores)
                      for pred, truth in [(True, True), (True, False), (False, False), (False, True)]]
    def rate(n, d):
        return n / d if d else None
    return dict(tp=tp, fp=fp, tn=tn, fn=fn, precision=rate(tp,tp+fp), recall=rate(tp,tp+fn),
                f1=rate(2*tp,2*tp+fp+fn), fpr=rate(fp,fp+tn), fnr=rate(fn,fn+tp))


def main():
    rows = load(DATA)
    groups = sorted({row["category"] for row in rows})
    random.Random(1703).shuffle(groups)
    splits = {name: [row for row in rows if row["category"] in cats] for name, cats in
              [("train", groups[4:]), ("validation", groups[:2]), ("test", groups[2:4])]}
    bias, weights = fit(splits["train"])
    def scores(name):
        return [(bias + sum(weights[i] for i in sorted(features(r["input"]))), r["label"] == "harmful") for r in splits[name]]
    val = scores("validation")
    candidates = [s for s, _ in val] + [max(s for s, _ in val)+1]
    threshold = max(candidates, key=lambda t: (summarize(val,t)["f1"] or 0, t))
    fingerprint = hashlib.sha256(DATA.read_bytes().replace(b"\r\n", b"\n")).hexdigest()
    artifact = "\n".join(["susagi-byte3-bnb-v1", fingerprint, str(bias), str(threshold), *map(str,weights)])+"\n"
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    ASSET.write_bytes(artifact.encode("ascii"))
    report = dict(artifact_sha256=hashlib.sha256(ASSET.read_bytes()).hexdigest(), source_sha256=fingerprint,
                  seed=1703, labels=["benign","harmful"], counts={k:len(v) for k,v in splits.items()},
                  groups={k:sorted({r["category"] for r in v}) for k,v in splits.items()},
                  validation=summarize(val,threshold), test=summarize(scores("test"),threshold))
    REPORT.write_bytes((json.dumps(report,indent=2)+"\n").encode())
    print(json.dumps(report))

if __name__ == "__main__":
    main()
