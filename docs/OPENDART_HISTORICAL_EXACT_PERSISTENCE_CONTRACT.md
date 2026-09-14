# OpenDART Historical Exact Persistence Contract

## Boundary
Historical Exact is split into two phases.

### Phase A — network / validation, outside DB write transaction
- Filing Discovery resolves the validated target filing and provider-native 14-digit `rcept_no`.
- Annual CFS value response is fetched and fully validated.
- Period-witness response is fetched and resolved for the same filing identity.
- All selected supported metrics must belong to the same receipt.
- Exact period start/end is finalized from the filing-specific period witness.
- Any required company-directory/profile HTTP needed by an approved bootstrap path is completed before persistence.
- Build one immutable validated prepared-filing input; no DB mutation occurs while provider HTTP is in progress.

### Phase B — one filing persistence transaction
Persist one validated filing atomically under the existing company advisory lock.
Within the same transaction:
- re-resolve/recheck `OPENDART/CORP_CODE -> COMPANY` identity and legacy period conflicts;
- if the already-approved bootstrap path is used, create COMPANY only from a previously validated directory record; no HTTP is allowed here;
- register/reuse the filing `DISCLOSURE` Evidence;
- persist all approved metric Event/Fact/FactAssertion writes for that filing;
- register/reuse the separate period-witness Evidence;
- register all `FactPeriodEvidence` links for the persisted Facts.
## Atomicity / rollback
- `OpenDartFilingPersistence.persist()` is the filing-level transaction boundary.
- Nested existing earnings ingestion uses normal REQUIRED transaction participation; it must not open independent commits per metric.
- Failure of any metric write, filing Evidence conflict, witness Evidence conflict, company identity conflict, or period-link registration rolls back the entire filing persistence transaction.
- Do not leave a subset of metrics persisted for one filing.
- Provider HTTP retry/re-fetch must never occur inside the write transaction.

## Identity and period recheck
- Persistence must recheck company identity after acquiring the advisory lock; preflight results alone are not sufficient for write safety.
- Existing rows for the same filing/predicate with a different COMPANY or a different exact period are BLOCK conditions, not migration/overwrite opportunities.
- A same filing rerun with identical Evidence/provenance/period is idempotent.

## Scope restraint
- Do not merge different `rcept_no` values.
- Do not add automatic correction supersession.
- Do not use current `acc_mt` to recompute a filing period already resolved by the witness.
- Do not weaken the existing EARNINGS Event/Fact provenance invariants to simplify Historical Exact.
