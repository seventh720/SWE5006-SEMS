"""Exercise the release boundary without registry or server access."""
import copy
import json
from pathlib import Path
import subprocess
import tempfile

validator = Path(__file__).with_name("validate-release.py")
sha = "a" * 40
valid = {"x-release-commit": sha, "services": {
    name: {"image": f"ghcr.io/seventh720/swe5006-sems/{name}@sha256:{'b' * 64}"}
    for name in ("backend", "frontend", "postgres")
}}
cases = [("valid pinned release", valid, sha, True)]
for label, mutate in (
    ("mutable image tag", lambda m: m["services"]["backend"].update(image="ghcr.io/seventh720/swe5006-sems/backend:main")),
    ("extra service", lambda m: m["services"].update(extra={"image": "unexpected"})),
    ("injected compose settings", lambda m: m["services"]["backend"].update(privileged=True)),
    ("wrong registry", lambda m: m["services"]["backend"].update(image=f"evil.example/backend@sha256:{'b' * 64}")),
):
    manifest = copy.deepcopy(valid)
    mutate(manifest)
    cases.append((label, manifest, sha, False))
cases.append(("mismatched commit", valid, "c" * 40, False))
with tempfile.TemporaryDirectory() as temp:
    path = Path(temp) / "manifest.json"
    for label, manifest, expected_sha, accepted in cases:
        path.write_text(json.dumps(manifest))
        result = subprocess.run(["python3", str(validator), str(path), expected_sha], capture_output=True)
        assert (result.returncode == 0) == accepted, label
        print(f"PASS {label}")
