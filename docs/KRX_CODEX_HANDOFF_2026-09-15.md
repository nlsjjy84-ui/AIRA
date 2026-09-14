# KRX Codex Handoff — 2026-09-15

## Position in sequence
- ECOS handoff is prepared first.
- KRX is the next provider packet because its evidence/idempotency contract is already settled.
- Do not reopen closed AIRA/ECOS design decisions while working KRX.

## Settled KRX Evidence contract
- Reuse existing `SourceType.EXCHANGE`.
- Reuse `EvidenceType.OFFICIAL_DATA`.
- One validated KRX Daily HTTP response for one explicit `basDd` is one Evidence snapshot.
- Multiple securities/metrics from that response may share the same Evidence.
- KRX does not provide a provider-native document ID for this response.
- Therefore Evidence `externalId` is AIRA deterministic request identity, e.g. `KRX_OPENAPI:{datasetKey}:{basDd}`.
- Never describe that `externalId` as a provider-native ID.
- Provider revision is unavailable, so Evidence revision remains `1`.
## Idempotency and conflict contract
- For the same `(source, externalId, revision)`, canonicalize the full validated response and compare content hash/provenance.
- If canonical content and provenance are identical, reuse the existing Evidence.
- If the same identity returns different canonical content, BLOCK.
- Do not overwrite the Evidence.
- Do not auto-increment revision.
- Do not silently repair or reinterpret provider data.

## Canonical hash rules
- Hash the full validated response, not a subset selected for one Fact.
- Exclude `AUTH_KEY` and collection timestamp from the content identity.
- Row order must not affect the hash.
- Canonicalization must use deterministic row ordering and deterministic field ordering.
- The hash represents the validated KRX response snapshot, not transport noise.
## Codex implementation boundary
- Reuse already-validated KRX transport/parser contracts in the repository; do not redesign them.
- Implement only the missing registration/orchestration needed to turn validated KRX Daily data into shared Evidence and downstream Facts/FactAssertions according to existing AIRA rules.
- Do not invent new metrics, predicates, endpoint semantics, field aliases, or normalization rules. If a required mapping is not already settled in code/docs, BLOCK and report it.
- Do not create Events or Assessments merely because a daily market value changed.
- Do not add revision/supersession behavior beyond the settled Evidence conflict rule.
- Preserve existing migrations; add a migration only if an actual new invariant requires storage support.

## Verification strategy
- Do not run full regression after each file/responsibility.
- First finish the KRX responsibility bundle.
- Then run KRX targeted tests once, including Evidence sharing, identical replay, changed-content BLOCK, deterministic hash, and Fact/Assertion linkage already required by settled mappings.
- Run the full API regression once at the end of the combined provider work, unless a real cross-cutting conflict requires earlier evidence.
- Preserve `.tmp-witness-*` and all unrelated pending/untracked work.
- No commit or push unless the user explicitly requests it.

## 2026-09-13 contract closure update
`KRX_6_API_V1_CONTRACT.md` is now the authoritative implementation contract for the six approved/requested endpoints.
- Stock identity and the daily-to-base-info code join are settled.
- The eight stock Market Fact predicates and their currency/unit semantics are settled.
- Codex must include the provider-neutral market-Fact model extension needed to support those predicates; current `Fact`/`FactPredicate` do not yet support them.
- KOSPI/KOSDAQ index endpoints are Evidence-only in v1 because the approved response contract does not expose a verified stable index identifier. Do not create index Facts or name-based canonical entities.
- This update supersedes any earlier handoff wording that treated those mappings as still open.

Additional authoritative KRX contracts:
- `MARKET_FACT_MODEL_V1_CONTRACT.md`
- `KRX_SECURITY_BOOTSTRAP_CONTRACT.md`
- `KRX_INGESTION_ORCHESTRATION_CONTRACT.md`
Codex should read these with `KRX_6_API_V1_CONTRACT.md` before touching KRX code. They close the provider-neutral Fact extension, SECURITY identity/bootstrap, and transaction/rollback semantics; do not redesign them from scratch.
