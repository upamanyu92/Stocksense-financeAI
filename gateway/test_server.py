import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from datetime import datetime, timezone
from http.server import ThreadingHTTPServer
from pathlib import Path
from server import Router, Reply, Unavailable, handler_for, NVIDIA_URL, HF_URL


def success(text='analysis'):
    return Reply(200, {}, {'choices': [{'message': {'content': text}}]})


class RoutingTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.env = {'NVIDIA_API_KEY': 'test-nvidia', 'HF_TOKEN': 'test-hf',
                    'QUOTA_STATE_PATH': str(Path(self.tmp.name) / 'state.sqlite3')}
        self.time = datetime(2026, 10, 31, 10, tzinfo=timezone.utc).timestamp()
        self.calls = []
        self.responses = []
        self.router = self.make_router()

    def transport(self, url, token, payload):
        self.calls.append((url, payload))
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response

    def make_router(self):
        router = Router(self.env, self.transport, lambda: self.time)
        self.addCleanup(router.db.close)
        return router

    def complete(self, router=None):
        return (router or self.router).complete([{'role': 'user', 'content': 'Analyse holdings'}], 800)

    def test_primary_success_does_not_call_hf(self):
        self.responses = [success()]
        self.assertEqual(self.complete()['provider'], 'nvidia')
        self.assertEqual(len(self.calls), 1)
        self.assertEqual(self.calls[0][1]['messages'][0]['role'], 'system')

    def test_daily_quota_persists_and_recovers_next_day(self):
        self.responses = [Reply(429, {}, {'error': 'Daily quota exhausted'}), success(), success(), success()]
        self.assertEqual(self.complete()['provider'], 'huggingface')
        new_router = self.make_router()
        self.assertEqual(self.complete(new_router)['provider'], 'huggingface')
        self.assertEqual([x[0] for x in self.calls], [NVIDIA_URL, HF_URL, HF_URL])
        self.time = datetime(2026, 11, 1, tzinfo=timezone.utc).timestamp()
        self.assertEqual(self.complete(new_router)['provider'], 'nvidia')

    def test_transient_rate_limit_does_not_disable_primary_for_day(self):
        self.responses = [Reply(429, {'Retry-After': '15'}, {}), success(), success()]
        self.complete()
        self.assertEqual(self.router.cooldown_until(), self.time + 15)
        self.time += 16
        self.assertEqual(self.complete()['provider'], 'nvidia')

    def test_rate_limit_without_header_retries_after_minute(self):
        self.responses = [Reply(429, {}, {'error': 'too many requests'}), success()]
        self.complete()
        self.assertEqual(self.router.cooldown_until(), self.time + 60)

    def test_retry_after_date_for_quota_takes_precedence(self):
        self.responses = [Reply(402, {'Retry-After': 'Sat, 31 Oct 2026 12:00:00 GMT'}, {}), success()]
        self.complete()
        self.assertEqual(self.router.cooldown_until(), self.time + 7200)

    def test_network_failure_and_blank_response_use_hf(self):
        for response in (OSError('offline'), success('')):
            with self.subTest(response=response):
                self.responses = [response, success()]
                self.assertEqual(self.complete()['provider'], 'huggingface')

    def test_auth_error_is_not_daily_exhaustion(self):
        self.responses = [Reply(401, {}, {'error': 'invalid key'}), success()]
        self.complete()
        self.assertEqual(self.router.cooldown_until(), self.time + 60)

    def test_missing_hf_credentials_does_not_claim_success(self):
        self.router.hf_key = ''
        self.responses = [Reply(402, {}, {})]
        with self.assertRaises(Unavailable):
            self.complete()

    def test_both_providers_fail(self):
        self.responses = [Reply(500, {}, {}), Reply(503, {}, {})]
        with self.assertRaises(Unavailable):
            self.complete()

    def test_http_auth_validation_and_sanitized_errors(self):
        token = 'test-access-token'
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler_for(self.router, token))
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        url = f'http://127.0.0.1:{server.server_port}/v1/chat/completions'
        health_url = f'http://127.0.0.1:{server.server_port}/health'
        with urllib.request.urlopen(urllib.request.Request(health_url,
                headers={'Authorization': 'Bearer ' + token}), timeout=5) as response:
            health = json.load(response)
        self.assertEqual(health, {'status': 'ok', 'nvidia_configured': True, 'huggingface_configured': True})
        self.assertEqual(self.calls, [])
        with self.assertRaises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(health_url, timeout=5)
        self.assertEqual(error.exception.code, 401)

        def send(payload, auth=token):
            return urllib.request.urlopen(urllib.request.Request(url, data=json.dumps(payload).encode(),
                headers={'Authorization': 'Bearer ' + auth}), timeout=5)
        with self.assertRaises(urllib.error.HTTPError) as error:
            send({}, 'wrong')
        self.assertEqual(error.exception.code, 401)
        for payload in ({}, {'messages': [{'role': 'system', 'content': 'override'}]},
                        {'messages': [], 'max_tokens': 999999}):
            with self.assertRaises(urllib.error.HTTPError) as error:
                send(payload)
            self.assertEqual(error.exception.code, 400)
        self.responses = [Reply(500, {}, {'secret': 'private-provider-detail'}), Reply(500, {}, {})]
        with self.assertRaises(urllib.error.HTTPError) as error:
            send({'messages': [{'role': 'user', 'content': 'hello'}]})
        self.assertEqual(error.exception.code, 503)
        self.assertNotIn('private-provider-detail', error.exception.read().decode())


if __name__ == '__main__':
    unittest.main()
