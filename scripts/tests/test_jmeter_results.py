"""Check measurement boundaries and goodput using a known synthetic JMeter trace."""
import csv
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("cache_runner", Path(__file__).parents[1] / "run-jmeter-cache.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class JMeterResultsTest(unittest.TestCase):
    def trace(self, directory, rows):
        source = Path(directory) / "raw.jtl"
        with source.open("w", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["timeStamp", "elapsed", "responseCode", "success"])
            writer.writerows(rows)
        return source

    def test_excludes_ramp_and_boundary_straddlers_and_counts_assertion_failures(self):
        with tempfile.TemporaryDirectory() as directory:
            raw = self.trace(directory, [
                [1000, 900, 200, "true"],  # ramp
                [1950, 100, 200, "true"],  # overlaps start
                [2100, 20, 200, "true"],
                [2400, 40, 200, "false"],  # semantic assertion failure despite HTTP 200
                [2800, 100, 503, "false"],
                [3950, 100, 200, "true"],  # overlaps end
            ])
            measured = Path(directory) / "measured.jtl"
            result = runner.analyse_samples(raw, measured, ramp_seconds=1, duration=2)
            self.assertEqual(result["raw_samples"], 6)
            self.assertEqual(result["samples"], 3)
            self.assertEqual(result["errors"], 2)
            self.assertEqual(result["status_counts"], {"200": 2, "503": 1})
            self.assertEqual(result["successful_rps"], .5)
            self.assertAlmostEqual(result["mean_ms"], 160 / 3)
            self.assertAlmostEqual(result["p95_ms"], 94)
            with measured.open() as stream:
                self.assertEqual(len(list(csv.DictReader(stream))), 3)

    def test_rejects_truncated_timed_run(self):
        with tempfile.TemporaryDirectory() as directory:
            raw = self.trace(directory, [[1000, 20, 200, "true"]])
            with self.assertRaisesRegex(RuntimeError, "cover"):
                runner.analyse_samples(raw, Path(directory) / "out.jtl", 1, 30)

    def test_empty_trace_is_invalid(self):
        with tempfile.TemporaryDirectory() as directory:
            raw = self.trace(directory, [])
            with self.assertRaisesRegex(RuntimeError, "no samples"):
                runner.analyse_samples(raw, Path(directory) / "out.jtl", 0, None)


if __name__ == "__main__":
    unittest.main()
