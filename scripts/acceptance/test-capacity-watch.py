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
GUARD = Path(__file__).with_name('capacity-guard.py')


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
    def exercise(self, lines, expected_error=None, extra_args=()):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            remote = RemoteStream(lines)
            arguments = ['capacity-watch.py', '--reports', directory, '--identity-file', '/unused/key',
                         '--known-hosts', '/unused/known-hosts', *extra_args]
            with patch('sys.argv', arguments), patch('subprocess.Popen', return_value=remote) as launch, \
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
            self.remote_command = launch.call_args.args[0][-1]
            return (root / 'STOP').read_text()

    def test_guard_event_stops_suite(self):
        reason = self.exercise(json.dumps({'stopReasons': ['hostOverload']}) + '\n')
        self.assertIn('hostOverload', reason)

    def test_lost_monitor_stops_suite(self):
        self.assertIn('Monitor ended', self.exercise(''))

    def test_invalid_monitor_output_stops_suite(self):
        self.assertIn('Monitor ended', self.exercise('invalid-json\n', json.JSONDecodeError))

    def test_custom_health_settings_reach_remote_guard(self):
        self.exercise(json.dumps({'stopReasons': ['swe5006-api']}) + '\n',
                      extra_args=['--health-latency-limit', '4', '--health-timeout', '7'])
        self.assertIn('--health-latency-limit 4.0 --health-timeout 7.0', self.remote_command)

    def test_invalid_timeout_does_not_start_monitor(self):
        arguments = ['capacity-watch.py', '--reports', '/unused/reports', '--identity-file', '/unused/key',
                     '--known-hosts', '/unused/known-hosts', '--health-latency-limit', '5', '--health-timeout', '3']
        with patch('sys.argv', arguments), patch('subprocess.Popen') as launch, patch('sys.stderr', io.StringIO()):
            with self.assertRaises(SystemExit) as result:
                runpy.run_path(str(WATCH), run_name='__main__')
        self.assertEqual(result.exception.code, 2)
        launch.assert_not_called()


class GuardHealthTest(unittest.TestCase):
    def exercise(self, latencies, failed=False, extra_args=(), expected_timeout=6):
        clock = [0.0]
        frame = [0]
        output = io.StringIO()

        class Pool:
            def __init__(self, **kwargs):
                pass

            def __enter__(self):
                return self

            def __exit__(self, *args):
                pass

            def map(self, function, items):
                frame[0] += 1
                return map(function, items)

        class Response:
            status = 200

            def __enter__(self):
                return self

            def __exit__(self, *args):
                pass

            def read(self):
                return b'{"status":"UP"}'

        def urlopen(url, timeout):
            self.assertEqual(timeout, expected_timeout)
            response = Response()
            if url.endswith('/actuator/health'):
                clock[0] += latencies[frame[0] - 1]
                response.status = 503 if failed else 200
            else:
                clock[0] += 0.01
            return response

        def command(arguments, **kwargs):
            if 'printenv' in arguments:
                return 'example.test'
            if 'stats' in arguments:
                return json.dumps({'Name': 'swe5001-caddy-1', 'CPUPerc': '1%', 'MemUsage': '32MiB / 256MiB',
                                   'MemPerc': '12.5%', 'PIDs': '10'})
            if 'inspect' in arguments:
                return '/swe5006-backend-1|true|false|0'
            return '{}'

        def read_text(path, **kwargs):
            return 'MemAvailable: 2097152 kB\n' if str(path) == '/proc/meminfo' else '1.0 1.0 1.0 1/100 1'

        def sleep(seconds):
            clock[0] += seconds

        with patch('sys.argv', ['capacity-guard.py', *extra_args]), \
                patch('subprocess.check_output', side_effect=command), patch.object(Path, 'read_text', read_text), \
                patch('concurrent.futures.ThreadPoolExecutor', Pool), patch('urllib.request.urlopen', side_effect=urlopen), \
                patch('time.monotonic', side_effect=lambda: clock[0]), patch('time.sleep', side_effect=sleep), \
                patch('sys.stdout', output):
            runpy.run_path(str(GUARD), run_name='__main__')
        return [json.loads(line) for line in output.getvalue().splitlines()]

    def test_explicit_legacy_limit_reproduces_previous_stop(self):
        samples = self.exercise([2.2, 2.2, 2.2],
                                extra_args=['--health-latency-limit', '2', '--health-timeout', '3'], expected_timeout=3)
        self.assertEqual(len(samples), 3)
        self.assertTrue(all(s['health']['swe5006-api']['ok'] for s in samples))
        self.assertEqual(samples[-1]['stopReasons'], ['swe5006-api'])

    def test_default_allows_moderate_latency_and_resets_consecutive_count(self):
        samples = self.exercise([2.2, 5.2, 5.2, 0.1, 5.2, 5.2, 5.2],
                                expected_timeout=6)
        self.assertEqual(len(samples), 7)
        self.assertTrue(all(not s['stopReasons'] for s in samples[:-1]))
        self.assertEqual(samples[-1]['stopReasons'], ['swe5006-api'])

    def test_failed_health_still_stops_with_relaxed_latency_limit(self):
        samples = self.exercise([0.1, 0.1, 0.1], failed=True,
                                extra_args=['--health-latency-limit', '5', '--health-timeout', '6'], expected_timeout=6)
        self.assertEqual(len(samples), 3)
        self.assertFalse(samples[-1]['health']['swe5006-api']['ok'])
        self.assertEqual(samples[-1]['stopReasons'], ['swe5006-api'])


if __name__ == '__main__':
    unittest.main()
