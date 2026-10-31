import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from build_runtime_bundle import build, configuration, FILES


class RuntimeBundleTests(unittest.TestCase):
    def test_bundle_excludes_credentials_and_quota_state(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'gateway').mkdir()
            for name in FILES:
                (root / 'gateway' / name).write_text('public runtime source')
            for name in ('.env', 'quota.sqlite3', 'emulator.log'):
                (root / 'gateway' / name).write_text('private sentinel')
            archive = build(root, root / 'output', {})
            with tarfile.open(archive) as bundle:
                self.assertEqual(set(bundle.getnames()), {'gateway/' + f for f in (*FILES, 'runtime-config.json')})
                for member in bundle.getmembers():
                    self.assertNotIn(b'private sentinel', bundle.extractfile(member).read())
                config = json.load(bundle.extractfile('gateway/runtime-config.json'))
                self.assertEqual(config['gateway_url'], '')

    def test_only_nonsecret_configuration_is_exported(self):
        config = configuration({'NVIDIA_API_KEY': 'private-sentinel', 'HF_TOKEN': 'private-sentinel',
                                'GATEWAY_ACCESS_TOKEN': 'private-sentinel',
                                'NEMOTRON_GATEWAY_URL': 'https://gateway.example.com'})
        self.assertNotIn('private-sentinel', json.dumps(config))
        self.assertEqual(config['gateway_url'], 'https://gateway.example.com')

    def test_gateway_rejects_credentials_and_provider_endpoints(self):
        for url in ('http://gateway.example.com', 'https://key@gateway.example.com',
                    'https://gateway.example.com?token=value', 'https://gateway.example.com#token',
                    'https://router.huggingface.co/v1/chat/completions',
                    'https://integrate.api.nvidia.com/v1'):
            with self.subTest(url=url), self.assertRaises(ValueError):
                configuration({'NEMOTRON_GATEWAY_URL': url})

    def test_rejects_invalid_model_ids(self):
        with self.assertRaises(ValueError):
            configuration({'HF_MODEL': 'not a model'})


if __name__ == '__main__':
    unittest.main()
