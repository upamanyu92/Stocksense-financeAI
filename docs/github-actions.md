# GitHub Actions credentials and runtime artifacts

The repository is public. Provider keys and gateway access tokens must never be
published in APKs, release assets, container layers, logs, or runtime archives.

## Repository settings

In GitHub, open **Settings → Secrets and variables → Actions → Secrets** and set:

| Repository secret | Use |
| --- | --- |
| `NVIDIA_API_KEY` | NVIDIA-hosted Nemotron inference in the backend |
| `HF_TOKEN` | Hugging Face Inference Providers fallback |
| `GATEWAY_ACCESS_TOKEN` | Authenticate private gateway clients; at least 32 characters |

The existing optional Android signing secrets remain `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.
Configure all four together or builds retain the existing debug-signing fallback.
Do not use a debug-signed APK as a production store release.

To copy the already configured local credentials without printing them or placing
values in command-line arguments, use an authenticated GitHub CLI with repository
secrets write access:

```bash
python scripts/sync_github_secrets.py
```

The helper reads ignored `gateway/.env`, preflights the repository secrets API,
and passes each value to `gh secret set` through stdin. It does not remove local
credentials because the selected local-computer gateway still needs them.
The workspace must allow `api.github.com`; GitHub repository access alone does
not establish access to the Actions secrets API.

Under **Actions → Variables**, optionally set these non-secret repository variables:

| Variable | Default |
| --- | --- |
| `NEMOTRON_GATEWAY_URL` | Empty; users set their own gateway URL on-device |
| `NVIDIA_MODEL` | `nvidia/nemotron-3-super-120b-a12b` |
| `HF_MODEL` | `nvidia/NVIDIA-Nemotron-3-Nano-30B-A3B:fastest` |

The URL must be your own HTTPS gateway, not NVIDIA's or Hugging Face's provider
endpoint. For local-computer testing, leave this variable unset and use the debug
APK's AI service settings with `http://127.0.0.1:8080` and `adb reverse`.
Release APKs require HTTPS, including for a gateway running on your computer.

## Workflow behavior

`android-deploy.yml` runs on PRs to main, main/copilot pushes, version tags, and
manual dispatch. It calls reusable `android.yml` to run gateway/build-helper tests,
Android unit tests, lint, and debug APK assembly. Develop pushes also run those checks.
PR code receives no provider or signing secrets. Secrets are limited to individual
steps on main/tag runs. Main/tag builds fail clearly if runtime credentials are missing.

The runtime job checks authenticated HTTP health with the three repository secrets.
This verifies credential injection, not upstream authentication, and consumes no
inference credits. To check real provider access, manually dispatch the workflow
on main with **verify_providers** enabled. This makes small billable requests and
checks HF availability independently. A public model repository does not guarantee
that an HF provider currently serves it or that the account has credits.

The runtime bundle contains only allowlisted code, `.env.example`, and
`runtime-config.json` with model IDs and an optional public gateway URL. It never
contains `.env`, provider keys, access tokens, persisted quota databases, or logs.
The release job downloads this artifact, validates its metadata, uses its public
URL as build configuration, and verifies the tracked TFLite asset inside every APK.
No GGUF download or native llama build is required. The standard release version
and per-ABI naming behavior is preserved.

Artifacts include debug APKs, test reports, release APKs, and the gateway runtime
archive plus SHA-256 checksum. Main/tag pushes publish the existing GHCR download
image containing release APKs and the clean runtime bundle. Before pushing, an HTTP smoke check verifies APK downloads, the runtime checksum, and exclusion of private configuration paths. Version tags also
attach both to GitHub Releases. The download image serves files; it does not host
the inference gateway or expose repository secrets.

## Running downloaded artifacts

Extract the runtime archive and run `python server.py` from its `gateway` directory
after configuring its private `.env` or environment variables. Supply provider
credentials through your server/local computer environment. GitHub Actions secrets
are available only to workflow jobs and are not automatically delivered to your
computer. Keep quota SQLite state persistent on the gateway host.

Install a downloaded APK, open **Profile → AI service**, and enter your gateway URL
and access token, then **Save connection → Check connection**. Tokens entered on
the device are encrypted using Android Keystore and stored outside Android backup.
Release APKs contain no gateway token. CI debug APKs also contain none; the private
local source-build workflow still supports its ignored debug-only build token.

A public multi-user deployment still needs user-specific server authentication
and rate limits. The shared gateway token is for private testing; it is not a
replacement for server-validated user sessions.
