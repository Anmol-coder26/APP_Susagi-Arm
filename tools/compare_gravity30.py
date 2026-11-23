"""Compare two completed Intelligence25Test exports for the same frozen corpus."""
import csv
import sys
from collections import Counter
from pathlib import Path


def load(path):
    with Path(path).open(encoding="utf-8-sig") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    indexed = {r["id"]: r for r in rows}
    if len(indexed) != len(rows) or len(rows) != 108:
        raise ValueError("Expected 108 unique message IDs")
    return indexed


def compare(before, after, output):
    old, new = load(before), load(after)
    if old.keys() != new.keys():
        raise ValueError("Corpus IDs changed")
    counts = [Counter(), Counter()]
    changes = Counter()
    with Path(output).open("w", encoding="utf-8", newline="") as stream:
        write = csv.writer(stream, delimiter="\t", lineterminator="\n").writerow
        write("id category language label previous updated previous_evidence updated_evidence ml_flag outcome".split())
        for key, a in old.items():
            b = new[key]
            if any(a[k] != b[k] for k in ("label", "category", "language", "ml_flag", "margin")):
                raise ValueError("Fixture or shadow score mismatch: " + key)
            pos = a["label"] == "harmful"
            high = [r["rule_risk"] == "HIGH" for r in (a,b)]
            for count, flag in zip(counts, high):
                count[("TP" if flag else "FN") if pos else ("FP" if flag else "TN")] += 1
            change = "unchanged" if high[0] == high[1] else "improved" if high[1] == pos else "regressed"
            changes[pos, change] += 1
            write([key, a["category"], a["language"], a["label"], a["rule_risk"], b["rule_risk"],
                   a["evidence"], b["evidence"], b["ml_flag"], change])
    for count in counts:
        tp, fp, tn, fn = (count[k] for k in ("TP","FP","TN","FN"))
        def ratio(a,b): return a/b if b else None
        print(dict(TP=tp, FP=fp, TN=tn, FN=fn, precision=ratio(tp,tp+fp), recall=ratio(tp,tp+fn),
                   f1=ratio(2*tp,2*tp+fp+fn), fpr=ratio(fp,fp+tn)))
    print("new harmful detections", changes[True,"improved"], "new false alarms", changes[False,"regressed"])


if __name__ == "__main__":
    compare(*sys.argv[1:])
