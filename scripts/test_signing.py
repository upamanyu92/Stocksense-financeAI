import base64
from pathlib import Path
import tempfile
import unittest
from prepare_signing import prepare, properties_value


class SigningTests(unittest.TestCase):
    def test_preserves_special_credentials_and_excludes_existing_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            env = {'ANDROID_KEYSTORE_BASE64': base64.b64encode(b'fake-keystore').decode(),
                   'ANDROID_KEYSTORE_PASSWORD': ' $value\nwith spaces\\',
                   'ANDROID_KEY_ALIAS': 'key:alias', 'ANDROID_KEY_PASSWORD': 'pässword'}
            prepare(root, env)
            self.assertEqual((root / 'keystore.jks').read_bytes(), b'fake-keystore')
            props = dict(line.split('=', 1) for line in (root / 'keystore.properties').read_text().splitlines())
            for field, value in (('storePassword', env['ANDROID_KEYSTORE_PASSWORD']),
                                 ('keyPassword', env['ANDROID_KEY_PASSWORD'])):
                encoded = props[field].split('\\u')[1:]
                decoded = bytes.fromhex(''.join(encoded)).decode('utf-16-be')
                self.assertEqual(decoded, value)
            self.assertEqual((root / 'keystore.properties').stat().st_mode & 0o777, 0o600)
            with self.assertRaises(ValueError):
                prepare(root, env)

    def test_partial_signing_configuration_fails(self):
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(ValueError):
            prepare(Path(directory), {'ANDROID_KEYSTORE_BASE64': 'anything'})


if __name__ == '__main__':
    unittest.main()
