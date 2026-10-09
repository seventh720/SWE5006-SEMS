"""Publish Vitest coverage totals to GitHub outputs and the job summary."""
import json
import os
import sys
from pathlib import Path

report = Path(sys.argv[1])
values = {"line": "N/A", "branch": "N/A"}
if report.is_file():
    totals = json.loads(report.read_text())["total"]
    for kind, key in [("line", "lines"), ("branch", "branches")]:
        counter = totals[key]
        if counter["total"]:
            values[kind] = f"{100 * counter['covered'] / counter['total']:.1f}%"
with open(os.environ["GITHUB_OUTPUT"], "a") as output:
    for kind, value in values.items():
        output.write(f"{kind}={value}\n")
with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
    summary.write(f"### Frontend unit and component tests\n\nLine coverage: **{values['line']}**\n\nBranch coverage: **{values['branch']}**\n")
    if not report.is_file():
        summary.write("\nCoverage report was not generated.\n")
