# Nemotron inference gateway

QuantSense uses NVIDIA Nemotron 3 Super as its primary language model and a
Nemotron model hosted through Hugging Face Inference Providers as fallback.
The Android app sends requests to this gateway. NVIDIA/HF credentials stay here.
TensorFlow Lite stock predictions and deterministic financial calculations stay
in the Android app.

## Migration plan and implemented behavior

1. Replace llama.cpp language inference with an HTTPS gateway client for stock
   insights, chat, portfolio analysis, and CredenceAI narratives.
2. Use `nvidia/nemotron-3-super-120b-a12b` on NVIDIA's OpenAI-compatible endpoint.
3. On exhausted NVIDIA quota, switch to HF and persist the retry time in SQLite.
   Honor `Retry-After` (seconds or HTTP date). Without it, explicit quota/credit
   exhaustion (402 or quota-related 429) waits until next midnight UTC. This is
   an application policy: the provider's actual billing/reset timezone may differ.
   Ordinary 429 throttling waits 60 seconds rather than disabling NVIDIA all day.
4. Network/server/auth failures and empty completions also try HF. Failed NVIDIA
   authentication and 5xx responses receive a 60-second cooldown, not a daily lockout.
5. Retry NVIDIA automatically after cooldown. Persist the SQLite file across
   service restarts. A changed NVIDIA key gets separate cooldown state.
6. Label template guidance clearly when neither provider succeeds. Remove mandatory
   phone downloads and cancel the previous automatic model-download job.

## Credentials and configuration

Requires Python 3.11+; no third-party Python dependencies.

Copy `.env.example` to `.env` if `.env` does not already exist, then fill in secrets.
A local ignored `.env` may already contain the NVIDIA key supplied for this task.
Do not overwrite it or commit it. Use a secret manager/environment variables on a server.

- `NVIDIA_API_KEY`: NVIDIA API key.
- `NVIDIA_MODEL`: defaults to `nvidia/nemotron-3-super-120b-a12b`.
- `HF_TOKEN`: Hugging Face token with Inference Providers permission and credits.
- `HF_MODEL`: defaults to `nvidia/NVIDIA-Nemotron-3-Nano-30B-A3B:fastest`.
  Confirm that this model is currently served by a provider accessible to your HF
  account. Set an explicit supported provider suffix if needed. Model weights on
  Hugging Face do not by themselves guarantee hosted inference availability.
- `GATEWAY_ACCESS_TOKEN`: random token of at least 32 characters for private testing.
- `QUOTA_STATE_PATH`: persistent SQLite file path (default `quota.sqlite3` in cwd).
- `GATEWAY_HOST`: defaults to loopback `127.0.0.1`.
- `PORT`: defaults to `8080`.

The NVIDIA key is not a Hugging Face credential. HF inference has independent
usage limits and billing; it is not an unlimited free fallback.

Run from this directory:

```bash
python server.py
```

For remote deployments, serve it behind a TLS reverse proxy. Release builds require
HTTPS; debug builds also allow HTTP exclusively to loopback and the Android emulator host. No provider call is made until an authenticated completion request arrives.

## Local computer testing (USB device or Android emulator)

For a single-command launch on the computer containing the project and Android SDK:

```bash
python gateway/run_app.py
```

This configures the gateway, starts it if needed, waits for Android, builds and
installs the matching debug APK, forwards the gateway port, and opens QuantSense.
Use `--serial DEVICE_SERIAL` for a specific connected device, or `--avd AVD_NAME`
to start an existing emulator. Cloud/headless machines can additionally use
`--headless --software` if hardware virtualization is unavailable. Software
emulation is much slower. Use `--verbose-boot` to include kernel diagnostics in the ignored `gateway/emulator.log`. Keep the launcher terminal open.

Manual alternative, on the computer containing the project and the connected device/emulator:

```bash
python gateway/local.py
```

This loads the ignored `gateway/.env`, binds only to `127.0.0.1:8080`, and updates
ignored `local.properties` with the matching gateway URL and private test token.
It preserves SDK configuration. Keep this terminal open.

In a second terminal on **the same computer**:

```bash
adb reverse tcp:8080 tcp:8080
./gradlew installDebug
```

On Windows use `gradlew.bat installDebug`. Start the app and open AI service settings
→ Check connection. Re-run `adb reverse` when the USB device reconnects or emulator
restarts. If several devices are connected, select one with `adb -s SERIAL reverse ...`
and install to that device. Android USB debugging must be enabled.

No HTTPS domain, public hosting, LAN exposure, or native LLM is needed for this
private debug workflow. Cleartext is allowed only for debug builds and only for
`127.0.0.1`, `localhost`, and emulator host `10.0.2.2`. Release builds reject it.
An authenticated `/health` endpoint checks gateway configuration without using
inference credits; it does not establish that provider credentials are valid.

A gateway running in a cloud workspace is **not** a gateway running on your own
computer. Run the command on your own computer after obtaining the project and
securely configuring its ignored `.env`. That secret file is deliberately absent
from Git. A USB phone attached to your computer cannot reach the cloud workspace
via `adb reverse` on your computer. Do not copy provider keys into Android config.

## Android connection (remote HTTPS deployment)

In the root ignored `local.properties`, set:

```properties
nemotron.gatewayUrl=https://your-ai-service.example.com
nemotron.gatewayToken=YOUR_PRIVATE_TEST_GATEWAY_ACCESS_TOKEN
```

Alternatively use Gradle properties `-Pnemotron.gatewayUrl=...` and
`-Pnemotron.gatewayToken=...`. **Never put NVIDIA_API_KEY or HF_TOKEN here**:
BuildConfig values are extractable from the APK. The shared gateway token is for
private development only. Before public distribution, replace shared-token access
with server-validated short-lived user sessions and apply per-user rate limits at
the gateway/reverse proxy. The app's existing local PIN login is not server auth.
The included stdlib HTTP service is a development implementation, not an exposed
public production server. Run one gateway process; multiple replicas require
shared atomic quota coordination instead of the process-local lock.

A build without a gateway URL initially shows “not configured” and labelled template guidance; connection settings can be supplied on-device.
Downloaded APKs support **AI service → Gateway URL / Gateway access token → Save connection**.
The token is encrypted with Android Keystore and stored outside Android backup.
Release APKs and CI debug APKs contain no shared access token. Private local debug
source builds still support `nemotron.gatewayToken` for convenience.
See [GitHub Actions setup](../docs/github-actions.md) for repository secrets and runtime artifacts.

AI settings includes a live connection check. This consumes a small inference request.
Financial prompts are transmitted to the active provider; the gateway suppresses
request and provider error logging and does not store prompts or responses.

## Tests and activation

```bash
python -m unittest -v
```

Tests inject provider responses; they do not consume credits. They cover NVIDIA
success, daily persistence and reset, throttling, retry headers, missing HF access,
network/auth errors, empty output, both providers failing, HTTP authorization and
sanitized errors.

Activation requires a working HF token/model-provider pairing and either the local
computer workflow above or an HTTPS backend deployment with the Android gateway URL. Run AI settings “Check connection”,
then test an exhausted-primary scenario with a controlled provider stub; do not
intentionally consume a real quota to test fallback. Live provider behavior and
availability must be checked from a network allowed to reach NVIDIA and Hugging Face.
