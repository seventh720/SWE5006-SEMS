#!/usr/bin/env python3
"""Check the suite ceiling without Docker, credentials, SSH or HTTP requests."""
import contextlib
from datetime import datetime, timezone
import io
import json
from pathlib import Path
import runpy
import sys
import tempfile
import unittest
from unittest.mock import patch


class CapacityRunTest(unittest.TestCase):
    def plan(self, *options):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            credentials = root / 'credentials.json'
            credentials.write_text('{}')
            (root / 'resources.jsonl').write_text(json.dumps({
                'at': datetime.now(timezone.utc).isoformat(), 'stopReasons': [],
            }) + '\n')
            arguments = ['capacity-run.py', '--credentials', str(credentials), '--reports', str(root), *options]
            with patch.object(sys, 'argv', arguments), patch('subprocess.run') as run, \
                    patch('threading.Thread'), patch('time.sleep'), \
                    contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                run.return_value.returncode = 0
                runpy.run_path(str(Path(__file__).with_name('capacity-run.py')), run_name='__main__')
                return json.loads((root / 'suite.json').read_text())

    def test_500_ceiling_applies_to_reads_writes_and_contention(self):
        plan = self.plan('--max-vus', '500')
        for mode in ['read', 'mixed']:
            stages = [stage for stage in plan if stage['mode'] == mode]
            self.assertEqual([stage['targetVUs'] for stage in stages], [1, 50, 100, 250, 500])
            self.assertEqual(stages[-1]['plannedHold'], '180s')
        self.assertEqual((plan[-1]['mode'], plan[-1]['targetVUs']), ('contention', 500))
        self.assertTrue(all(stage['targetVUs'] <= 500 for stage in plan))

    def test_default_still_reaches_1000_and_target_hold_can_be_set(self):
        plan = self.plan('--target-hold', '60s')
        self.assertEqual(plan[-1]['targetVUs'], 1000)
        self.assertEqual([stage['plannedHold'] for stage in plan if stage['mode'] != 'contention'
                          and stage['targetVUs'] == 1000], ['60s', '60s'])

    def test_start_level_above_ceiling_is_rejected(self):
        with self.assertRaises(SystemExit) as raised:
            self.plan('--max-vus', '500', '--read-start-level', '1000')
        self.assertEqual(raised.exception.code, 2)

    def test_retries_get_distinct_idempotency_namespaces(self):
        first = self.plan('--max-vus', '500')
        second = self.plan('--max-vus', '500')
        self.assertEqual(len({stage['runId'] for stage in first}), 1)
        self.assertNotEqual(first[0]['runId'], second[0]['runId'])


if __name__ == '__main__':
    unittest.main()
