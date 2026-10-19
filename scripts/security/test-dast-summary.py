#!/usr/bin/env python3
"""Check that the CI security gate never turns missing coverage into a pass."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('dast_summary', Path(__file__).with_name('dast-summary.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class DastGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.write('coverage.json', [{'url': str(i), 'status': 200} for i in range(5)])

    def write(self, name, value):
        (self.directory / name).write_text(json.dumps(value))

    def scan(self, risks):
        self.write('zap.json', {'site': [{'alerts': [
            {'riskcode': str(risk), 'pluginid': str(index), 'name': 'Finding'}
            for index, risk in enumerate(risks)
        ]}]})
        with contextlib.redirect_stdout(io.StringIO()):
            return module.summarize(self.directory)

    def test_medium_and_high_fail(self):
        for risk in (2, 3):
            with self.subTest(risk=risk):
                self.assertEqual(self.scan([risk]), 1)

    def test_low_and_information_stay_visible_without_failing(self):
        self.assertEqual(self.scan([0, 1]), 0)
        text = (self.directory / 'summary.md').read_text()
        self.assertIn('Low=1', text)
        self.assertIn('Informational=1', text)

    def test_empty_site_is_not_a_clean_scan(self):
        self.write('zap.json', {'site': []})
        with self.assertRaises(ValueError):
            module.summarize(self.directory)

    def test_missing_report_is_not_a_clean_scan(self):
        with self.assertRaises(FileNotFoundError):
            module.summarize(self.directory)

    def test_incomplete_or_failed_seed_is_rejected(self):
        self.write('zap.json', {'site': [{'alerts': []}]})
        for coverage in ([], [{'status': 500}] * 5):
            with self.subTest(coverage=coverage):
                self.write('coverage.json', coverage)
                with self.assertRaises(ValueError):
                    module.summarize(self.directory)


if __name__ == '__main__':
    unittest.main()
