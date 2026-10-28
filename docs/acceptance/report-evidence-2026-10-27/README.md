# Final report verification evidence

Recorded on **27 October 2026 (SGT)** after the presentation. The report now includes a direct comparison of the analysis/design sequences for core workflows, a connected ERD for all 11 application tables, and actual verification-output excerpts in Appendix A.

## Fresh CI artifacts

[PR #50](https://github.com/seventh720/SWE5006-SEMS/pull/50) contains the prepared Vite/Vitest patch updates. [CI run 40445034465](https://github.com/seventh720/SWE5006-SEMS/actions/runs/40445034465) passed all six verification jobs. GitHub checked out temporary merge **82060f508b98b86b6e4804e98d01e46701591b76**, combining branch head **8cfc17d157c493a1c1bc0fff423a097a278a8e7f** with main **a6a7b1306695f8428841dde0d30375543a7832c1**. This was a PR verification; deployment and DAST were skipped.

| Check | Actual result | Original artifact |
|---|---|---|
| Lint | Eight checks PASS | `lint-report.zip` |
| Backend unit-only | 62 passed, 140 skipped, 202 total; container tests disabled | `backend-unit-coverage.zip` |
| Backend with PostgreSQL | 215 passed, 1 optional query comparison skipped, 216 total | `backend-combined-coverage.zip` |
| Backend combined coverage | 1330/1406 lines (94.59%); 429/526 branches (81.56%) | Combined JaCoCo XML/HTML |
| Frontend | 244 tests across 32 files passed; typecheck/build passed | Frontend job log and `frontend-coverage.zip` |
| Frontend coverage | 1332/1436 lines (92.75%); 1723/2063 branches (83.51%) | `coverage-summary.json` |
| SAST | 0 findings, 0 scan errors, 207 files scanned | `sast-report.zip` / `semgrep.json` |
| Dependencies and secrets | No unignored HIGH/CRITICAL vulnerability or secret findings; 2 scoped Java exceptions remain | `dependency-secret-reports.zip` |
| Container scans and smoke checks | Backend, frontend and PostgreSQL passed; no unignored HIGH/CRITICAL vulnerability or secret findings; backend retains the same 2 exceptions | `image-security-reports.zip` |

Unit-only and PostgreSQL-enabled totals are separate executions and must not be added. Frontend component tests commonly mock APIs. Trivy counts use the configured HIGH/CRITICAL filter; the Spring MVC exceptions are applicability assessments, not fixes, and expire on 17 November 2026.

## Separately dated deployed results

PR #50 was subsequently merged as **2583b595b67d0bae579e6f0f653087a31e1bfe96**. Its [main run 40448082772](https://github.com/seventh720/SWE5006-SEMS/actions/runs/40448082772) passed all verification jobs, image publication, staging deployment/HTTPS and passive DAST. See the [post-merge job status](post-merge-status.json). The numerical excerpts and original ZIPs below retain their own measurement baselines; this later status does not replace their contents.

- **DAST:** the original ZAP HTML/JSON, summary and run/coverage metadata from **270c2b8**, [run 39311200552](https://github.com/seventh720/SWE5006-SEMS/actions/runs/39311200552), 20 October SGT. High/Medium/Low alert types 0; four informational alert types covering 17 instances. Passive unauthenticated public pages/GET APIs only.
- **Performance:** the original [21 October capacity evidence](../capacity-2026-10-21/README.md) from **037720b**. 500 VU read/mixed passed; 1000 VU read passed, mixed and contention failed their full criteria. Contention retained stock correctness but recorded one connection refusal. These workloads were not rerun for the new CI.

## Offline artifacts and integrity

The accompanying `SEMS_Verification_Evidence_20261027.zip` contains the original downloaded CI archives, frontend job log, saved DAST and performance files, reproduction scripts and an SHA-256 manifest. Original CI archive hashes were verified against GitHub's artifact digest records. See [artifact digests](artifact-digests.json) and [derived result index](results.json).

The final Word report, original scan/test archives and companion ZIP are archived separately from this checkout. This repository retains the measured result index, artifact digests and post-merge job status so the recorded results remain traceable without committing the report or raw archives. Local exports under `outputs/` are Git-ignored.
