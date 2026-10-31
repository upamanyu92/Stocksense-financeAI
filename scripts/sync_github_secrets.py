"""Upload the locally supplied gateway credentials to GitHub Actions using gh stdin."""
import argparse
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', default='upamanyu92/Stocksense-financeAI')
    args = parser.parse_args()
    path = Path(__file__).resolve().parent.parent / 'gateway/.env'
    values = dict(line.split('=', 1) for line in path.read_text().splitlines()
                  if line.strip() and not line.lstrip().startswith('#') and '=' in line)
    names = ('NVIDIA_API_KEY', 'HF_TOKEN', 'GATEWAY_ACCESS_TOKEN')
    if not all(values.get(name) for name in names):
        raise SystemExit('The local gateway/.env must contain all three credentials.')
    # Preflight before sending any credentials. No credential is passed in argv or printed.
    subprocess.run(['gh', 'api', f'repos/{args.repo}/actions/secrets/public-key', '--silent'], check=True)
    for name in names:
        subprocess.run(['gh', 'secret', 'set', name, '--repo', args.repo],
                       input=values[name].encode(), check=True)
        print('Updated GitHub Actions secret:', name)


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError:
        raise SystemExit('GitHub secret synchronization failed. Check API network access and repository secrets write permission.')
