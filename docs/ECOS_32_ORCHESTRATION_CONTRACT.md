# ECOS-32 Orchestration Contract

- Status: Design CLOSED for 2026-09-15 Codex implementation
- Depends on: ECOS-1~30 CLOSED, ECOS-31 current implementation/ADR-013
- Goal: connect validated ECOS REAL_GDP observation reading to persistence without reopening closed contracts.

## Boundary

ECOS-32 owns only the execution path:
`scope -> observation reader -> validated result -> persistence coordinator -> ingestion result`.

Do not add scheduler, controller/public API, batch framework, historical backfill policy, new revision model, Event, Assessment, FactPeriodEvidence, or a provider-observation table.
Do not redesign ECOS parser, evidence hashing/identity, statistical series binding, Fact model, or ECOS-31 dedup/conflict semantics unless an actual compile/test conflict proves it necessary.

## Transaction boundary

Network I/O must not run inside the database transaction.
The outer orchestration operation is therefore NOT transactional.
It first calls `EcosRealGdpObservationReader.read(scope)` and receives a fully validated `EcosRealGdpObservationResult`.
Only after that succeeds may database persistence begin.

Persistence must be delegated to one transactional coordinator method so series binding, Evidence registration/reuse, and all Fact/context/assertion writes for that result commit or roll back together.
A provider/budget/validation failure before persistence must produce zero database writes.
## Persistence order

Inside the single persistence transaction:
1. Register/reuse the canonical Korea REAL_GDP series binding.
2. Register/reuse the Evidence snapshot for the validated observation result.
3. Load/verify that the returned Evidence is the canonical BOK ECOS Evidence expected by the closed contract.
4. Ingest every non-blank quarterly observation through the ECOS-31 statistical Fact path.
5. Return one orchestration result containing the stable binding/evidence identifiers plus the per-quarter Fact results/count.

The persistence coordinator must pass the exact same validated result and Evidence identity through the flow. It must not rebuild provider rows or reinterpret metadata.

## Failure and idempotency

Same request identity + same snapshot content -> reuse Evidence.
Same request identity + different snapshot content -> BLOCK; no overwrite and no automatic revision increment.
Same statistical quarter/value re-run -> reuse the same provider-neutral Fact/context/assertion state.
Same quarter/value from a different valid Evidence -> preserve the Fact and add provenance only as allowed by ECOS-31.
Different value for the same provider-neutral Fact identity -> preserve assertions and use ECOS-31 conflict behavior; never silently replace the stored value.
Blank/non-numeric observation required for ingestion -> BLOCK the persistence transaction; do not invent UNKNOWN and do not silently skip it.
Any persistence exception must roll back the whole result persistence transaction.
## Codex implementation shape

Prefer two small responsibilities rather than one broad service:
- outer operation: performs the HTTP/read phase and delegates validated output;
- transactional persistence coordinator: performs only database work.

Reuse the existing ECOS reader, series-binding service, evidence-registration service, and ECOS-31 fact-ingestion service. Do not duplicate their rules in the orchestrator.

## Close criteria

Codex should close ECOS-32 with one focused integration bundle proving:
- provider/read failure => zero writes;
- successful multi-quarter result => one Evidence snapshot shared by all quarter Facts;
- exact `YYYYQn` quarter periods are preserved;
- identical re-run is idempotent;
- persistence failure/blank value rolls back the whole persistence phase;
- no Event/Assessment rows are created.

After the ECOS-31/32 bundle is green, run the full API regression once, then `git diff --check` and migration/temp-file audit once.
Do not rerun the already CLOSED ECOS-1~30 packet tests individually unless the bundle exposes a concrete regression.

## Explicitly deferred

Scheduler/cron, long-range automatic backfill, observation revision/supersession, new macro metrics, public API/UI exposure, and production scheduling/monitoring remain later work and must not be pulled into this packet.