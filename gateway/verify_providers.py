"""Opt-in trusted CI probe. Never print credentials, provider bodies, or prompts."""
import os
from server import Router, NVIDIA_URL, HF_URL


def main():
    if not all(os.environ.get(name) for name in ('NVIDIA_API_KEY', 'HF_TOKEN', 'GATEWAY_ACCESS_TOKEN')):
        raise SystemExit('Configure NVIDIA_API_KEY, HF_TOKEN and GATEWAY_ACCESS_TOKEN repository secrets first.')
    router = Router({**os.environ, 'QUOTA_STATE_PATH': ':memory:'})
    messages = [{'role': 'user', 'content': 'Reply with OK only.'}]
    try:
        primary = router.call(NVIDIA_URL, router.nvidia_key, router.nvidia_model, messages, 512)
        fallback = router.call(HF_URL, router.hf_key, router.hf_model, messages, 512)
        primary_ready = router.result(primary, router.nvidia_model, 'nvidia') is not None
        fallback_ready = router.result(fallback, router.hf_model, 'huggingface') is not None
        print('NVIDIA inference ready:', primary_ready)
        print('Hugging Face inference ready:', fallback_ready)
        if not fallback_ready or (not primary_ready and (primary is None or primary.status not in (402, 429))):
            raise SystemExit('Provider readiness failed. Check credentials, quota, and model/provider availability.')
    finally:
        router.db.close()


if __name__ == '__main__':
    main()
