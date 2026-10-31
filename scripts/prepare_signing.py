"""Materialize optional Android signing credentials without shell interpolation or logging secrets."""
import base64
import os
from pathlib import Path


def properties_value(value):
    data = value.encode('utf-16-be')
    return ''.join('\\u' + data[i:i+2].hex() for i in range(0, len(data), 2))


def prepare(root, env):
    names = ('ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD')
    if not any(env.get(name) for name in names):
        print('No release keystore configured; using the existing debug-signing fallback.')
        return
    if not all(env.get(name) for name in names):
        raise ValueError('All four Android signing secrets must be configured together.')
    keystore = root / 'keystore.jks'
    props = root / 'keystore.properties'
    if keystore.exists() or props.exists():
        raise ValueError('Refusing to replace an existing signing configuration.')
    keystore.write_bytes(base64.b64decode(env['ANDROID_KEYSTORE_BASE64'], validate=True))
    keystore.chmod(0o600)
    fields = {'storeFile': str(keystore.resolve()), 'storePassword': env['ANDROID_KEYSTORE_PASSWORD'],
              'keyAlias': env['ANDROID_KEY_ALIAS'], 'keyPassword': env['ANDROID_KEY_PASSWORD']}
    props.write_text(''.join(key + '=' + properties_value(value) + '\n' for key, value in fields.items()))
    props.chmod(0o600)


if __name__ == '__main__':
    prepare(Path.cwd(), os.environ)
