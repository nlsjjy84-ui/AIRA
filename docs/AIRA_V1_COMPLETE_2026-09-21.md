# AIRA v1 COMPLETE — 2026-09-21

## Status

AIRA v1의 현재 합의 범위는 개발 완료로 닫는다.

새 오류가 발견되거나 요구사항이 추가되지 않는 한 이미 PASS한 기능·화면·UI 규칙을 다시 열지 않는다.

## Product boundary

핵심 탐색 흐름:

```text
MAIN → 검색 → Ask → Inspect → Relate → Assess
```

Personal Finance는 위 핵심 탐색 5단계와 분리된 별도 진입 영역이다.

## Final verified runtime

- branch: `main`
- completion checkpoint before this note: `2b293bd`
- backend health: `UP`
- PostgreSQL retained development database: reachable and schema-valid
- KRX latest completed official trading day:
  - KOSPI: AVAILABLE, 2026-09-18, Evidence linked
  - KOSDAQ: AVAILABLE, 2026-09-18, Evidence linked
- NAVER 2025 Historical Exact:
  - period: 2025-01-01 — 2025-12-31
  - OpenDART receipt: `20260313001021`
  - state: `AVAILABLE`
  - supported Facts: REVENUE, OPERATING_INCOME
  - retained legacy Facts were revalidated against the same official OpenDART filing and linked to verified period Evidence
- real browser flow:
  - Search → Ask → Inspect → Relate → Assess: PASS
  - official Evidence source link: PASS
  - Confirmed Event relation: PASS
  - Current Assessment interpretation: PASS
- Personal Finance unauthenticated boundary: HTTP 401
- mobile 390px:
  - horizontal overflow: none
  - vertical wheel scroll reaches document bottom
- browser page errors during final flow: 0

## Automated verification baseline

- Web unit/integration: 71 / 71 PASS
- production Web build: PASS
- browser E2E: 8 PASS / 5 environment-dependent skip
- isolated API suite after V22: 649 tests, 0 failures, 0 errors, 8 skips

The environment-dependent skips are not treated as product failures.

## External provider boundary

Market news uses GDELT DOC 2.0 only as separate article metadata.

At final verification time GDELT could return HTTP 503 / rate-limit-related failure. AIRA correctly transitions from loading to an explanatory error state and continues to show official market indices and confirmed events. News never substitutes for AIRA Fact, Event, Evidence, or Assessment.

## UI rules locked

- official AIRA logo retained
- Header owns search / interest / briefing / alerts / user
- Sidebar owns MAIN / Ask / Inspect / Relate / Assess / Personal Finance / settings
- Ask is perspective selection, not a chatbot input
- no duplicated search inside the embedded explorer
- card/radius use kept restrained
- role-based colors retained
- meaningful charts/bars only
- Light-only current mode
- no mobile horizontal overflow
- vertical scrolling remains functional on long pages
- Personal Finance remains separate from the core five-stage flow

These rules are covered by the existing UI regression tests, including `apps/web/e2e/ui-rules.spec.js`.

## Completion rule

From this checkpoint forward:

1. PASS + no new failure = do not reopen.
2. Reopen only for a reproducible bug, a failed regression test, a security/data-integrity issue, or an explicit new product requirement.
3. Do not weaken provenance rules to make missing data appear available.
4. Do not manually fabricate or backfill official Evidence without provider revalidation.
5. New work belongs to deployment, portfolio/presentation packaging, operations, or a separately approved v2 scope.

## Next phase

AIRA v1 development is complete. The next phase is packaging and delivery:

- remote Git backup
- deployment/runtime packaging when needed
- README/portfolio presentation
- interview/demo narrative
