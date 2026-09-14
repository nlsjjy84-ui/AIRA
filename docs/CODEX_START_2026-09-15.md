# Codex Start Prompt — 2026-09-15

AIRA 기준 명세 v1.0을 최상위 기준으로 사용한다.
먼저 현재 저장소의 branch, HEAD, git status, staged/untracked/pending 변경과 migration 상태만 확인한다. 어떤 파일도 clean/delete/revert 하지 말고 `.tmp-witness-*`와 기존 pending 작업을 그대로 보존한다. commit/push도 하지 않는다.

그다음 아래 두 문서를 구현 계약으로 읽는다.
- `docs/ECOS_CODEX_HANDOFF_2026-09-15.md`
- `docs/ECOS_32_ORCHESTRATION_CONTRACT.md`
또한 ECOS-31의 이미 작성된 구현과 `docs/adr/ADR-013-ecos-real-gdp-quarterly-fact-ingestion.md`를 현재 기준으로 사용한다.

중요: ECOS-1~30은 CLOSED다. 재설계·재감사·개별 반복 테스트하지 않는다. 실제 compile/test conflict가 발생했을 때만 관련 최소 파일을 확인한다.

작업 순서:
1. 현재 ECOS-31 pending 구현을 최소 점검하여 계약 위반/컴파일 문제만 수정한다.
2. ECOS-31 targeted PostgreSQL 검증으로 닫는다.
3. ECOS-32를 계약대로 구현한다: network/read phase는 DB transaction 밖, persistence는 하나의 transactional coordinator 안에서 atomic하게 처리한다.
4. 기존 reader / series binding / Evidence registration / ECOS-31 fact ingestion을 재사용하고 규칙을 orchestration에 복제하지 않는다.
5. ECOS-31/32 integration bundle을 한 번 실행한다.
6. bundle PASS 후 전체 API regression은 딱 한 번만 실행한다.
마감 검증은 다음 순서로만 한다:
- focused ECOS bundle result 확인
- full API regression 1회
- `git diff --check`
- migration audit
- `.tmp-witness-*` 및 pending/untracked 보존 확인

BLOCK 규칙:
- 기존 CLOSED 계약만으로 판단할 수 없고 새 제품/데이터 의미 결정이 필요하면 임의 추론하지 말고 BLOCK한다.
- 같은 Evidence identity의 다른 content는 overwrite/auto revision 하지 말고 BLOCK한다.
- blank/non-numeric REAL_GDP observation을 UNKNOWN 또는 skip으로 처리하지 않는다.
- Event/Assessment/FactPeriodEvidence/새 observation table/scheduler/backfill/public API/UI를 이번 범위에 추가하지 않는다.

보고 방식:
- 중간 보고는 설계 변경, BLOCK, targeted 결과, full regression 결과에만 한다.
- 이미 확인한 파일이나 CLOSED packet을 다시 읽었다는 보고를 반복하지 않는다.
- 테스트 프로세스 시작 후 다른 작업을 끼우지 말고 실제 종료와 XML까지 확인한다.

최종 목표는 ECOS-31/32를 CLOSED 상태로 만들고, 다음 Packet으로 넘어갈 수 있는 깨끗한 구현/검증 상태를 남기는 것이다.