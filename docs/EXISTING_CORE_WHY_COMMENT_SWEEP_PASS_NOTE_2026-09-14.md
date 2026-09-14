# Existing-core WHY comment sweep - PASS NOTE (2026-09-14)

This pass audited the existing canonical domain and provenance, OpenDART/KRX ingestion, Historical Exact and type-specific Current, P6/P7 events, Assessment, private Interest/Briefing/Alert, Auth and recovery, API reads, and Web navigation/visualization. Existing WHY comments already cover the key receipt/period validation, exact identity and no-fallback reads, graph terminal selection, ingestion origin, private delivery, and server-owned comparison semantics; they were retained.

Three comments were clarified without changing behavior. The KRX previous-observation read validates the caller's D and Fact ID against the exact stored official point; it does not itself establish D. `/api/me/**` is the authenticated boundary for private routes. `STALE` remains a reserved response state because no freshness threshold has been approved; elapsed time alone must not emit it.

The historical OpenDART 24-endpoint catalog is already marked superseded by the approved 35 automatic + 1 HOLD scope. No migration, schema, or runtime behavior was changed for this sweep.

Sanity: API `compileJava` passed; Web `explorerState.test.js` and `visualizations.test.jsx` passed (10 tests); Web production build passed. Full regression was not run.
