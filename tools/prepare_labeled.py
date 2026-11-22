"""Validate owner-supplied labeled TSV; preserves group separation, never converts spam to scam."""
import csv, hashlib, json, random, sys
from pathlib import Path

def prepare(path, channel, seed=21):
    groups, seen, ids = {}, set(), set()
    with Path(path).open(encoding="utf-8-sig",newline="") as f:
        reader=csv.DictReader(f,delimiter="\t",strict=True)
        required={"id","source","raw_text_or_url","label","language","scenario","group_id","channel"}
        if not required.issubset(reader.fieldnames or []): raise ValueError("Missing required column")
        for row in reader:
            if None in row or not all(row.get(k,"").strip() for k in required): raise ValueError("Incomplete record")
            if row["channel"]!=channel: continue
            key=" ".join(row["raw_text_or_url"].casefold().split())
            if key in seen or row["id"] in ids or row["label"] not in {"harmful","benign"}: raise ValueError("Duplicate or invalid label")
            ids.add(row["id"]); seen.add(key)
            groups.setdefault(row["group_id"],[]).append(row)
    names=sorted(groups)
    if len(names)<3: raise ValueError("Need at least three independent groups")
    random.Random(seed).shuffle(names)
    n=max(1,len(names)//5)
    return {part:[r for group in nameset for r in groups[group]] for part,nameset in
            (("train",names[2*n:]),("validation",names[:n]),("test",names[n:2*n]))}

if __name__=="__main__":
    print(json.dumps(prepare(sys.argv[1],sys.argv[2]),ensure_ascii=False))
