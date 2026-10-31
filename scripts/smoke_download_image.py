"""Verify the public download image serves APKs/runtime and excludes private files."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image', required=True)
    args = parser.parse_args()
    env = dict(os.environ)
    for key in ('DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH'):
        env.pop(key, None)
    docker = ['docker', '--host=unix:///var/run/docker.sock']
    container = subprocess.check_output(docker + ['run', '--rm', '-d', '-p', '127.0.0.1::80', args.image],
                                        env=env, text=True).strip()
    try:
        port = subprocess.check_output(docker + ['port', container, '80/tcp'], env=env, text=True).strip().rsplit(':', 1)[1]
        url = 'http://127.0.0.1:' + str(int(port))
        for attempt in range(25):
            try:
                with urllib.request.urlopen(url, timeout=2) as response:
                    html = response.read().decode()
                break
            except OSError:
                time.sleep(.2)
        else:
            raise RuntimeError('Download image did not become ready.')
        apks = re.findall(r'href="([^"/]+\.apk)"', html)
        if not apks:
            raise RuntimeError('APK download links are missing.')
        for name in apks:
            with urllib.request.urlopen(urllib.request.Request(url + '/' + name, method='HEAD'), timeout=5) as response:
                if int(response.headers['Content-Length']) <= 0:
                    raise RuntimeError('APK download is empty.')
        archive_name = 'quantsense-gateway-runtime.tar.gz'
        with urllib.request.urlopen(url + '/runtime/' + archive_name, timeout=5) as response:
            actual = hashlib.sha256(response.read()).hexdigest()
        expected = hashlib.sha256(Path('dist/gateway-runtime', archive_name).read_bytes()).hexdigest()
        if actual != expected:
            raise RuntimeError('Runtime download differs from the build artifact.')
        with urllib.request.urlopen(url + '/runtime/' + archive_name + '.sha256', timeout=5) as response:
            if response.read().decode().split()[0] != expected:
                raise RuntimeError('Runtime checksum does not match.')
        for path in ('/.env', '/runtime/.env', '/local.properties', '/keystore.properties'):
            try:
                urllib.request.urlopen(url + path, timeout=3)
            except urllib.error.HTTPError as error:
                if error.code != 404:
                    raise RuntimeError('Unexpected response for a private path.') from None
            else:
                raise RuntimeError('Private configuration was exposed.')
        print('Download image passed: APKs, runtime bundle, checksum, and private-file isolation.')
    finally:
        subprocess.run(docker + ['rm', '-f', container], env=env, stdout=subprocess.DEVNULL, check=False)


if __name__ == '__main__':
    main()
