"""Check that each published APK includes the tracked on-device prediction model."""
from pathlib import Path
import zipfile

apks = list(Path('app/build/outputs/apk/release').glob('*.apk'))
if not apks:
    raise SystemExit('No release APKs were built.')
expected = Path('app/src/main/assets/stock_prediction.tflite').read_bytes()
for apk in apks:
    with zipfile.ZipFile(apk) as archive:
        if archive.read('assets/stock_prediction.tflite') != expected:
            raise SystemExit('APK prediction model does not match the tracked runtime asset.')
print('Validated prediction runtime asset in', len(apks), 'release APKs.')
