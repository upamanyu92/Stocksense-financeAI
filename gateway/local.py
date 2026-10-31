"""Configure the private Android debug build and run the gateway on this computer."""
import argparse
import os
from pathlib import Path
import secrets
from server import load_environment, main


def configure(root, port, token):
    path = root / 'local.properties'
    existing = path.read_text() if path.exists() else ''
    lines = [line for line in existing.splitlines()
             if not line.startswith(('nemotron.gatewayUrl=', 'nemotron.gatewayToken='))]
    lines += [f'nemotron.gatewayUrl=http://127.0.0.1:{port}', f'nemotron.gatewayToken={token}']
    path.write_text('\n'.join(lines) + '\n')
    path.chmod(0o600)


def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--configure-only', action='store_true')
    args = parser.parse_args()
    load_environment()
    env_path = Path(__file__).with_name('.env')
    token = os.environ.get('GATEWAY_ACCESS_TOKEN', '')
    if not token:
        token = secrets.token_urlsafe(32)
        with env_path.open('a') as file:
            file.write('\nGATEWAY_ACCESS_TOKEN=' + token + '\n')
        env_path.chmod(0o600)
        os.environ['GATEWAY_ACCESS_TOKEN'] = token
    if len(token) < 32 or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_' for c in token):
        raise SystemExit('Use a gateway access token of at least 32 URL-safe characters')
    if not os.environ.get('NVIDIA_API_KEY') or not os.environ.get('HF_TOKEN'):
        raise SystemExit('Set NVIDIA_API_KEY and HF_TOKEN in gateway/.env or the environment first')
    port = int(os.environ.get('PORT', '8080'))
    if not 1 <= port <= 65535:
        raise SystemExit('Invalid PORT')
    # Local mode never exposes provider credentials or the server to the LAN.
    os.environ['GATEWAY_HOST'] = '127.0.0.1'
    os.environ.setdefault('QUOTA_STATE_PATH', str(Path(__file__).with_name('quota.sqlite3')))
    configure(Path(__file__).resolve().parent.parent, port, token)
    print(f'Private debug build configured for http://127.0.0.1:{port}', flush=True)
    print(f'For a USB device/emulator on this computer: adb reverse tcp:{port} tcp:{port}', flush=True)
    if not args.configure_only:
        print('Gateway running on this computer. Keep this terminal open; Ctrl+C stops it.', flush=True)
        main()


if __name__ == '__main__':
    run()
