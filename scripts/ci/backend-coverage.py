"""Publish JaCoCo line/branch coverage to GitHub outputs and the job summary."""
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

report = Path(sys.argv[1])
label = sys.argv[2]
values = {"line": "N/A", "branch": "N/A"}
if report.is_file():
    for counter in ET.parse(report).getroot().findall("counter"):
        kind = counter.attrib["type"].lower()
        if kind in values:
            covered = int(counter.attrib["covered"])
            total = covered + int(counter.attrib["missed"])
            values[kind] = f"{100 * covered / total:.1f}%" if total else "N/A"
with open(os.environ["GITHUB_OUTPUT"], "a") as output:
    for kind, value in values.items():
        output.write(f"{kind}={value}\n")
with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
    summary.write(f"### {label}\n\nLine coverage: **{values['line']}**\n\nBranch coverage: **{values['branch']}**\n")
    if not report.is_file():
        summary.write("\nCoverage report was not generated.\n")
