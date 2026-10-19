"""Reject releases that are not pinned to the three tested SEMS images."""
import json
from pathlib import Path
import re
import sys

manifest = json.loads(Path(sys.argv[1]).read_text())
commit = sys.argv[2]
if not re.fullmatch(r"[0-9a-f]{40}", commit):
    raise SystemExit("Invalid release commit")
if set(manifest) != {"x-release-commit", "services"} or manifest["x-release-commit"] != commit:
    raise SystemExit("Release manifest does not match the deployment commit")
if set(manifest["services"]) != {"backend", "frontend", "postgres"}:
    raise SystemExit("Unexpected release services")
for service, config in manifest["services"].items():
    expected = rf"ghcr\.io/seventh720/swe5006-sems/{service}@sha256:[0-9a-f]{{64}}"
    if set(config) != {"image"} or not re.fullmatch(expected, config["image"]):
        raise SystemExit(f"Unpinned or unexpected image: {service}")
print("PASS release commit and image digests")
