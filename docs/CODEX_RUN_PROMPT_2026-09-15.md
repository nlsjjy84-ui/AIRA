# Codex Run Prompt — 2026-09-15

AIRA 이어가자. `AIRA 기준 명세 v1.0`과 현재 로컬 작업트리를 최상위 기준으로 사용한다.

먼저 `C:\AIRA\docs\CODEX_PACKET_PLAN_2026-09-15.md`와 거기에 참조된 handoff/contract 문서만 읽어라. CLOSED된 ECOS-1~30, 이미 닫힌 OpenDART 1A/Filing Discovery/period-witness 계약, 기존 전체 감사 결과를 처음부터 다시 재감사하지 마라.

시작할 때 `C:\AIRA`의 branch, HEAD, concise changed/untracked filenames, migration filenames만 한 번 확인한다. `.tmp-witness-*` 내부를 열거하거나 삭제/정리하지 마라. 현재 pending/untracked 작업을 보존하고 reset/revert/clean하지 마라.

작업은 P1 -> P8 순서로 진행한다. 각 Packet은 문서에 정의된 책임만 구현하고, 다른 Packet 책임을 미리 섞지 마라. 한 Packet을 구현할 때 이미 닫힌 계약을 다시 설계하지 말고 현재 코드와 충돌하는 구체적 증거가 있을 때만 해당 지점을 보고하라.

각 Packet에서: 관련 파일을 한 번에 묶어 읽고 -> 구현 책임을 한 덩어리로 수정하고 -> Packet 종료 시 targeted test를 한 번 실행한다. 파일 하나 수정할 때마다 테스트하지 마라. 전체 API regression은 P1~P7이 모두 coherent한 뒤 P8에서 정확히 한 번만 실행한다.
DB_PASSWORD가 필요한 real PostgreSQL 검증은 관련 Packet이 실제로 닫힐 준비가 된 뒤 한 번에 요청하고, 실행 후 결과 XML을 직접 확인한다. `failures=0 / errors=0 / skipped=0`이 필요한 테스트는 skip 상태로 CLOSED 처리하지 마라.

새 의미가 필요하지만 계약에 없는 경우 임의로 설계하지 말고 정확한 BLOCK 지점과 필요한 결정만 보고하라. 반대로 단순 구현/리팩터링/테스트 작성은 사용자에게 중간 승인 요청 없이 현재 계약대로 진행하라.

기존 migration은 수정하지 말고 필요한 경우에만 additive migration을 추가한다. 사용자 지시 없이 commit/push하지 마라.

P8에서는 provider-targeted closing evidence -> required PostgreSQL XML -> full API regression 1회 -> `git diff --check` -> migration/temp-file audit 순서로 마감한다.

최종 보고는 Packet별 CLOSED/BLOCK, 실제 테스트 수치, migration 변화, 남은 미결정 사항만 간결하게 정리한다. 이미 설명한 계약을 장황하게 반복하지 마라.
