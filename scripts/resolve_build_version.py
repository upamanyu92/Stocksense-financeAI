import os
import re
from pathlib import Path

props = {}
for line in Path("version.properties").read_text().splitlines():
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    props[key.strip()] = value.strip()

base_name = props.get("VERSION_NAME", "1.0.0")
base_code = int(props.get("VERSION_CODE", "1"))
ref = os.environ.get("GITHUB_REF", "")
ref_name = os.environ.get("GITHUB_REF_NAME", "")
run_number = int(os.environ.get("GITHUB_RUN_NUMBER", "1"))
# Reserve 900000-999999 for non-tagged branch builds so tagged semantic
# release version codes stay below that range without collisions.
branch_build_base = 900_000
branch_build_max_run = 99_999

def sanitize(value: str) -> str:
    sanitized = re.sub(r"[^0-9A-Za-z._-]+", "-", value).strip("-").lower()
    return sanitized or "build"

if ref.startswith("refs/tags/"):
    semver_string = ref_name[1:] if ref_name.startswith("v") else ref_name
    match = re.match(r"^(\d+)\.(\d+)\.(\d+)$", semver_string)
    if not match:
        raise SystemExit(
            f"Release tags must follow vMAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH, got: {ref_name}"
        )
    major, minor, patch = map(int, match.groups())
    # Encode MAJOR.MINOR.PATCH as major*100000 + minor*1000 + patch so
    # tagged release version codes stay below branch_build_base.
    version_code = major * 100_000 + minor * 1_000 + patch
    if version_code >= 900_000:
        raise SystemExit(
            f"Release version code {version_code} exceeds the reserved tagged-build range."
        )
    version_name = semver_string
    docker_tag = sanitize(ref_name)
else:
    branch = sanitize(ref_name)
    version_name = f"{base_name}-{branch}.{run_number}"
    if run_number > branch_build_max_run:
        raise SystemExit(
            f"Run number {run_number} exceeds the reserved branch-build version-code range."
        )
    version_code = branch_build_base + run_number
    docker_tag = sanitize(version_name)

artifact_name = f"release-apks-{sanitize(version_name)}"

with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output_file:
    output_file.write(f"release_version_name={version_name}\n")
    output_file.write(f"release_version_code={version_code}\n")
    output_file.write(f"release_artifact_name={artifact_name}\n")
    output_file.write(f"docker_version_tag={docker_tag}\n")
