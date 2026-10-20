#!/usr/bin/env python3
"""Offline safety checks: monitor failure must stop only the suite container."""
import io
import json
from pathlib import Path
import runpy
import tempfile
import unittest
from unittest.mock import patch

WATCH = Path(__file__).with_name('capacity-watch.py')


class RemoteStream:
    def __init__(self, lines):
        self.stdout = io.StringIO(lines)
        self.terminated = False

    def poll(self):
        return None

    def terminate(self):
        self.terminated = True

    def wait(self, timeout=None):
        return 0


class WatchSafetyTest(unittest.TestCase):
    def exercise(self, lines, expected_error=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            remote = RemoteStream(lines)
            arguments = ['capacity-watch.py', '--reports', directory, '--identity-file', '/unused/key',
                         '--known-hosts', '/unused/known-hosts']
            with patch('sys.argv', arguments), patch('subprocess.Popen', return_value=remote), \
                    patch('subprocess.run') as stop_container, patch('sys.stdout', io.StringIO()):
                if expected_error:
                    with self.assertRaises(expected_error):
                        runpy.run_path(str(WATCH), run_name='__main__')
                else:
                    runpy.run_path(str(WATCH), run_name='__main__')
            self.assertTrue((root / 'STOP').exists())
            self.assertTrue(remote.terminated)
            self.assertEqual(stop_container.call_args.args[0],
                             ['docker', 'stop', '--time', '3', 'sems5006-capacity-load'])
            return (root / 'STOP').read_text()

    def test_guard_event_stops_suite(self):
        reason = self.exercise(json.dumps({'stopReasons': ['hostOverload']}) + '\n')
        self.assertIn('hostOverload', reason)

    def test_lost_monitor_stops_suite(self):
        self.assertIn('Monitor ended', self.exercise(''))

    def test_invalid_monitor_output_stops_suite(self):
        self.assertIn('Monitor ended', self.exercise('invalid-json\n', json.JSONDecodeError))


if __name__ == '__main__':
    unittest.main()
