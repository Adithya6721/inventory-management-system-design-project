"""Known response traces: expected rejection is not accepted throughput."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('components', Path(__file__).parents[1] / 'run-components.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class ComponentResultsTest(unittest.TestCase):
    def test_separates_rejections_errors_and_admitted_latency(self):
        trace = [
            dict(elapsed='20', outcome='accepted', success='true', responseCode='200', scheduledLagMs='0'),
            dict(elapsed='1', outcome='rate_limited', success='true', responseCode='429', scheduledLagMs='2'),
            dict(elapsed='3', outcome='write_conflict', success='true', responseCode='409', scheduledLagMs=''),
            dict(elapsed='5', outcome='unexpected', success='false', responseCode='500', scheduledLagMs=''),
        ]
        result = runner.describe(trace, 2)
        self.assertEqual(result['attempted_rps'], 2)
        self.assertEqual(result['accepted_rps'], .5)
        self.assertEqual(result['accepted_mean_ms'], 20)
        self.assertEqual(result['unexpected_errors'], 1)
        self.assertEqual(result['rejection_pct'], 25)
        self.assertEqual(result['outcomes']['write_conflict'], 1)


if __name__ == '__main__': unittest.main()
