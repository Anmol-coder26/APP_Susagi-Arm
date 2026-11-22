import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import train_message_model as m


class ModelTests(unittest.TestCase):
    def test_locked_export(self):
        expected = m.ASSET.read_bytes()
        m.main()
        self.assertEqual(expected, m.ASSET.read_bytes())
        self.assertEqual(hashlib.sha256(expected).hexdigest(),json.loads(m.REPORT.read_text())["artifact_sha256"])

    def test_features(self):
        self.assertEqual(m.features("OTP"),m.features("otp"))
        self.assertNotEqual(m.features("पैसे"),m.features("पैसा"))
        self.assertEqual(m.features("x"*2048),m.features("x"*2048+"refund"))

    def test_separation(self):
        groups=json.loads(m.REPORT.read_text())["groups"]
        self.assertEqual(len(set(sum(groups.values(),[]))),sum(map(len,groups.values())))

    def test_duplicate_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"data.tsv"
            lines=m.DATA.read_text(encoding="utf8").splitlines()
            path.write_text("\n".join(lines[:2]+lines[1:2]),encoding="utf8")
            with self.assertRaises(ValueError):
                m.load(path)

    def test_golden(self):
        values=list(map(float,m.ASSET.read_text().splitlines()[2:]))
        margin=values[0]+sum(values[i+2] for i in sorted(m.features("Refund मिला OTP")))
        self.assertAlmostEqual(-10.409397380528246,margin)

if __name__ == "__main__":
    unittest.main()
