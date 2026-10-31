"""Build an allowlisted gateway runtime archive containing no credentials or local state."""
import json
import os
from pathlib import Path
import re
import tarfile
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent.parent
FILES = ('server.py', 'local.py', 'run_app.py', 'README.md', '.env.example')


def configuration(env):
    url = env.get('NEMOTRON_GATEWAY_URL', '').strip().rstrip('/')
    if url:
        parsed = urlsplit(url)
        if (parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password
                or parsed.query or parsed.fragment or parsed.hostname in
                ('integrate.api.nvidia.com', 'router.huggingface.co')):
            raise ValueError('NEMOTRON_GATEWAY_URL must be an HTTPS gateway URL, without credentials, query or fragment')
    nvidia = env.get('NVIDIA_MODEL') or 'nvidia/nemotron-3-super-120b-a12b'
    hf = env.get('HF_MODEL') or 'nvidia/NVIDIA-Nemotron-3-Nano-30B-A3B:fastest'
    for model in (nvidia, hf):
        if not re.fullmatch(r'nvidia/[A-Za-z0-9._-]+(?::[A-Za-z0-9_-]+)?', model):
            raise ValueError('Use a NVIDIA Nemotron model identifier, optionally with an HF provider suffix')
    return {'schema_version': 1, 'gateway_url': url, 'nvidia_model': nvidia, 'hf_model': hf}


def build(root, output, env):
    config = configuration(env)
    staging = output / 'gateway'
    staging.mkdir(parents=True, exist_ok=True)
    # Explicit allowlist: never copy a whole source directory containing .env or quota state.
    for name in FILES:
        (staging / name).write_bytes((root / 'gateway' / name).read_bytes())
    (staging / 'runtime-config.json').write_text(json.dumps(config, indent=2) + '\n')
    archive = output / 'quantsense-gateway-runtime.tar.gz'
    with tarfile.open(archive, 'w:gz') as bundle:
        for name in (*FILES, 'runtime-config.json'):
            bundle.add(staging / name, arcname='gateway/' + name)
    return archive


if __name__ == '__main__':
    print(build(ROOT, ROOT / 'dist/gateway-runtime', os.environ))
