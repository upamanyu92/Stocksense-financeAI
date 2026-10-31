"""Run the private gateway, build, install, and launch QuantSense on Android."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import urllib.request
from server import load_environment

ROOT = Path(__file__).resolve().parent.parent


def tool(name):
    installed = shutil.which(name)
    if installed:
        return installed
    for sdk in (os.environ.get('ANDROID_SDK_ROOT'), os.environ.get('ANDROID_HOME')):
        if not sdk:
            continue
        directory = 'platform-tools' if name == 'adb' else 'emulator'
        candidate = Path(sdk) / directory / (name + ('.exe' if os.name == 'nt' else ''))
        if candidate.exists():
            return str(candidate)
    raise RuntimeError(f'{name} not found. Set ANDROID_HOME to your Android SDK directory.')


def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', help='Android device serial, if several are connected')
    parser.add_argument('--avd', help='Start this existing Android Virtual Device if no device is connected')
    parser.add_argument('--headless', action='store_true', help='Run an emulator without a window')
    parser.add_argument('--verbose-boot', action='store_true', help='Include Android kernel diagnostics in emulator.log')
    parser.add_argument('--software', action='store_true', help='Use software CPU emulation without KVM')
    args = parser.parse_args()
    children = []
    try:
        subprocess.run([sys.executable, str(ROOT / 'gateway/local.py'), '--configure-only'], check=True)
        load_environment()
        port = int(os.environ.get('PORT', '8080'))
        token = os.environ['GATEWAY_ACCESS_TOKEN']
        def health():
            request = urllib.request.Request(f'http://127.0.0.1:{port}/health',
                headers={'Authorization': 'Bearer ' + token})
            with urllib.request.urlopen(request, timeout=2) as response:
                return json.load(response).get('status') == 'ok'
        try:
            ready = health()
        except OSError:
            ready = False
        if not ready:
            gateway = subprocess.Popen([sys.executable, str(ROOT / 'gateway/local.py')], cwd=ROOT)
            children.append(gateway)
            for _ in range(30):
                if gateway.poll() is not None:
                    raise RuntimeError('Gateway failed to start; check port and credentials.')
                try:
                    if health():
                        break
                except OSError:
                    pass
                time.sleep(0.5)
            else:
                raise RuntimeError('Gateway did not become ready.')
        print('Gateway ready (provider access still requires a live inference check).', flush=True)
        adb = tool('adb')
        devices = subprocess.check_output([adb, 'devices'], text=True)
        serials = [line.split()[0] for line in devices.splitlines()[1:] if line.endswith(('\tdevice', '\toffline'))]
        serial = args.serial
        if not serial:
            if len(serials) > 1:
                raise RuntimeError('Several devices connected; pass --serial DEVICE_SERIAL.')
            if serials:
                serial = serials[0]
            elif args.avd:
                emulator = tool('emulator')
                command = [emulator, '-avd', args.avd, '-port', '5554', '-no-snapshot', '-no-audio']
                if args.headless:
                    command += ['-no-window', '-gpu', 'swiftshader', '-no-boot-anim']
                if args.verbose_boot:
                    command += ['-show-kernel']
                if args.software:
                    command += ['-accel', 'off', '-cores', '2', '-memory', '2048']
                log = (ROOT / 'gateway/emulator.log').open('w')
                children.append(subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT))
                log.close()
                serial = 'emulator-5554'
            else:
                raise RuntimeError('Connect a USB-debugging Android device, or pass --avd NAME for an existing emulator.')
        deadline = time.monotonic() + 900
        last_notice = 0
        while time.monotonic() < deadline:
            if children and any(child.poll() is not None for child in children):
                raise RuntimeError('Gateway/emulator stopped. Check gateway/emulator.log.')
            try:
                booted = subprocess.check_output([adb, '-s', serial, 'shell', 'getprop', 'sys.boot_completed'],
                    text=True, stderr=subprocess.DEVNULL, timeout=5).strip() == '1'
                if booted:
                    break
            except (subprocess.SubprocessError, OSError):
                pass
            if time.monotonic() - last_notice > 30:
                print('Waiting for Android to boot…', flush=True)
                last_notice = time.monotonic()
            time.sleep(2)
        else:
            raise RuntimeError('Android did not finish booting within 15 minutes.')
        subprocess.run([adb, '-s', serial, 'reverse', f'tcp:{port}', f'tcp:{port}'], check=True)
        gradle = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
        subprocess.run([str(gradle), ':app:assembleDebug', '--console=plain'], cwd=ROOT, check=True)
        abi = subprocess.check_output([adb, '-s', serial, 'shell', 'getprop', 'ro.product.cpu.abi'], text=True).strip()
        apk = ROOT / f'app/build/outputs/apk/debug/app-{abi}-debug.apk'
        if not apk.exists():
            apk = ROOT / 'app/build/outputs/apk/debug/app-universal-debug.apk'
        subprocess.run([adb, '-s', serial, 'install', '-r', str(apk)], check=True)
        start = subprocess.run([adb, '-s', serial, 'shell', 'am', 'start', '-W', '-n',
                        'com.stocksense.app/.MainActivity'], check=True, capture_output=True, text=True)
        print(start.stdout, flush=True)
        if 'Error:' in start.stdout or 'Error:' in start.stderr:
            raise RuntimeError('Android rejected the activity launch.')
        if 'Status: ok' not in start.stdout:
            print('Launch requested; Android has not confirmed the first frame yet.', flush=True)
        else:
            print('QuantSense launched successfully.', flush=True)
        print('Keep this terminal open. Ctrl+C stops services started by this command.', flush=True)
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f'Launch failed: {error}', file=sys.stderr)
        return 1
    finally:
        for child in reversed(children):
            if child.poll() is None:
                child.terminate()
    return 0


if __name__ == '__main__':
    raise SystemExit(run())
