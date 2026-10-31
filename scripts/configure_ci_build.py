"""Use only non-secret gateway connection metadata from the validated runtime artifact."""
import json
import os
from pathlib import Path
import tarfile
from build_runtime_bundle import configuration


def main():
    archive = Path('dist/gateway-runtime/quantsense-gateway-runtime.tar.gz')
    with tarfile.open(archive) as bundle:
        config = json.load(bundle.extractfile('gateway/runtime-config.json'))
    if config.get('schema_version') != 1:
        raise SystemExit('Unsupported gateway runtime configuration.')
    validated = configuration({'NEMOTRON_GATEWAY_URL': config.get('gateway_url', ''),
                               'NVIDIA_MODEL': config.get('nvidia_model', ''),
                               'HF_MODEL': config.get('hf_model', '')})
    with open(os.environ['GITHUB_ENV'], 'a') as output:
        output.write('NEMOTRON_GATEWAY_URL=' + validated['gateway_url'] + '\n')
    model = Path('app/src/main/assets/stock_prediction.tflite')
    if not model.is_file() or model.stat().st_size < 8:
        raise SystemExit('The tracked TFLite runtime asset is missing.')
    print('APK gateway URL configured from runtime artifact:', bool(validated['gateway_url']))
    print('Gateway tokens are entered on-device; provider keys stay in backend runtime secrets.')


if __name__ == '__main__':
    main()
