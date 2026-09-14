# AIRA Codex Execution Plan — 2026-09-15

## Goal
Finish the already-designed provider ingestion work with minimum repeated repository exploration and minimum repeated regression testing.

## 0. One-time local preflight
- Work only from the current local working tree at `C:\AIRA`.
- Capture branch, HEAD, concise changed/untracked file names, and migration list once.
- Preserve all pending/untracked work and `.tmp-witness-*`.
- Do not clean, reset, revert, commit, or push unless explicitly instructed.
- Read the provider handoff documents first; do not rediscover closed contracts from scratch.

## 1. ECOS bundle first
- Use `ECOS_CODEX_HANDOFF_2026-09-15.md` and `ECOS_32_ORCHESTRATION_CONTRACT.md`.
- Do not reopen ECOS-1~30.
- Close ECOS-31 from the current implementation rather than redesigning it.
- Implement ECOS-32 thin orchestration only.
- Keep network observation reading outside the DB transaction; persist validated binding/evidence/facts atomically inside the DB transaction.
## 2. KRX bundle second
- Use `KRX_CODEX_HANDOFF_2026-09-15.md`.
- Reuse the settled shared-Evidence/idempotency/hash contract.
- Implement only missing registration/orchestration for already-settled KRX mappings.
- If a metric/predicate/field mapping is not already settled, BLOCK rather than invent it.

## 3. OpenDART / Historical Exact third
- Use `OPENDART_CODEX_HANDOFF_2026-09-15.md`.
- Do not reopen 1A, Filing Discovery, fiscal-period, or period-witness semantics.
- Finish only the remaining multi-file integration bundle: validated filing identity -> original official Evidence provenance -> settled period witness -> accepted material Event/approved adapter path.
- Do not infer correction lineage, merge, supersession, or account mapping from labels/names.

## 4. Verification cadence
- Do not run full API regression after ECOS, KRX, and OpenDART separately.
- Finish each provider bundle and use provider-targeted tests only where needed to close that bundle.
- After all three bundles are coherent, run the full API regression exactly once.
## 5. Final closeout only once
- Run `git diff --check` once after provider work is complete.
- Audit migration ordering/additivity once; never modify prior migrations.
- Confirm `.tmp-witness-*` and unrelated pending/untracked files are preserved.
- Inspect real-DB result XML directly for required PostgreSQL evidence rather than asking the user to repeat output.
- Report remaining BLOCK items separately from completed work.

## Deferred beyond this Codex bundle
- New ECOS metrics beyond the settled REAL_GDP scope.
- Scheduler / recurring ingestion automation.
- Large historical backfill orchestration.
- New revision/supersession policy not already settled.
- UI/public API expansion unrelated to closing provider ingestion.
- Broad refactors that do not directly reduce a demonstrated blocker.

## Efficiency rule
A closed contract stays closed. Re-read or re-test only when the current local code presents a concrete conflict, a test exposes a real invariant gap, or the handoff explicitly requires new evidence.
