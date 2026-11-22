"""Offline regression checks on the exact lexical assets packaged for Android."""
import hashlib
import json
import unittest

import round22 as training


class PackagedModelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((training.ROOT / "tools/round22-results.json").read_text())
        version, bias, threshold, *rows = training.ASSET.read_text(encoding="utf-8").splitlines()
        cls.version = version
        cls.bias = float(bias)
        cls.threshold = float(threshold)
        cls.weights = {key: float(value) for key, value in (row.split("\t") for row in rows)}

    def margin(self, text):
        return self.bias + sum(self.weights.get(key, 0) for key in sorted(training.features(text)))

    def test_packaged_identity(self):
        self.assertEqual(self.version, "lexical-v2")
        for path, field in [(training.ASSET, "sha256"), (training.SPEC, "spec_sha256")]:
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), self.report[field])

    def test_packaged_scores(self):
        vectors = training.ROOT / "app/src/test/resources/safety/lexical-parity.tsv"
        rows = vectors.read_text().splitlines()[1:]
        self.assertGreaterEqual(len(rows), 22)
        for row in rows:
            encoded, expected = row.split("\t")
            self.assertAlmostEqual(self.margin(bytes.fromhex(encoded).decode()), float(expected), places=9)

    def test_split_integrity_and_predictions(self):
        corpus = training.old.load(training.old.DATA)
        groups = self.report["groups"]
        seen = {}
        for split, categories in groups.items():
            examples = [row for row in corpus if row["category"] in categories]
            for row in examples:
                key = tuple(training.tokenize(row["input"]))
                self.assertEqual(seen.setdefault(key, split), split)
            if split != "train":
                scores = [(self.margin(row["input"]), row["label"] == "harmful") for row in examples]
                self.assertEqual(training.old.summarize(scores, self.threshold), self.report[split]["candidate"])
        for row in corpus:
            self.assertEqual(sum(row["category"] in cats for cats in groups.values()), 1)


if __name__ == "__main__":
    unittest.main()
