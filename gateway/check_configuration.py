"""Verify CI runtime credential injection over authenticated HTTP without inference calls."""
import json
import os
from pathlib import Path
import tempfile
import threading
import urllib.request
from http.server import ThreadingHTTPServer
from server import Router, handler_for


def main():
    required = ('NVIDIA_API_KEY', 'HF_TOKEN', 'GATEWAY_ACCESS_TOKEN')
    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        raise SystemExit('Missing repository secrets: ' + ', '.join(missing))
    token = os.environ['GATEWAY_ACCESS_TOKEN']
    if len(token) < 32:
        raise SystemExit('GATEWAY_ACCESS_TOKEN must have at least 32 characters.')
    with tempfile.TemporaryDirectory() as directory:
        router = Router({**os.environ, 'QUOTA_STATE_PATH': str(Path(directory) / 'quota.sqlite3')})
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler_for(router, token))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            request = urllib.request.Request(f'http://127.0.0.1:{server.server_port}/health',
                                             headers={'Authorization': 'Bearer ' + token})
            with urllib.request.urlopen(request, timeout=5) as response:
                body = json.load(response)
            assert body == {'status': 'ok', 'nvidia_configured': True, 'huggingface_configured': True}
            print('Gateway runtime credentials loaded and authenticated health check passed. No inference requested.')
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
            router.db.close()


if __name__ == '__main__':
    main()
