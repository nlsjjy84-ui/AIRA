# AIRA Working Manual — User Rules

## 최상위 목적
AIRA를 가장 빨리, 정확하게 완성한다. 목표는 이 채팅에서 일을 많이 하는 것이 아니라 **사용자 총시간 + Codex 사용량 + 중복 작업을 최소화하는 것**이다.

## 1. 모든 작업 시작 전 3분류
작업을 시작하기 전에 반드시 아래 중 하나로 먼저 판정한다.
- `CLOSED`: 이미 결정/검증된 것. 다시 읽거나 감사하지 않는다.
- `NOW-DESIGN`: 지금 결정해두면 Codex가 추측/재설계하지 않아도 되는 의미·불변조건·BLOCK 기준.
- `CODEX-FIRST`: 다중 파일 구현, 반복 코드, 저장소 광범위 탐색, 통합, E2E, 대량 테스트, 리팩터링, 구현 세부 검증.

판정 없이 도구 작업을 시작하지 않는다.

## 2. NOW-DESIGN의 엄격한 범위
지금 하는 선행설계는 **결정 하나를 닫는 데 필요한 최소 근거만** 본다.
- 이미 아는 내용이면 파일을 다시 읽지 않는다.
- 코드 확인은 설계 결정에 꼭 필요한 경우에만 한다.
- 한 설계 이슈에서 연쇄적으로 새 구현 구멍을 추적하지 않는다.
- 구현 세부·클래스 구조·테스트 작성법까지 파고들기 시작하면 즉시 `CODEX-FIRST`로 넘긴다.
- 문서 정리 자체가 목적이 되면 중단한다.

## 3. Codex에게 남길 일
다음은 원칙적으로 지금 하지 않는다.
- 다중 파일 구현/수정
- repository/service/controller wiring
- 반복 DTO/parser/client 작성
- 저장소 전체 검색과 영향도 분석
- PostgreSQL E2E/통합 테스트 구현 및 실행
- 전체 회귀, 광범위 리팩터링
- 구현을 위해 필요한 세부 코드 추적
- 같은 계약을 여러 문서에 맞추는 대규모 문서 정합성 작업
## 4. 반복/회귀 방지 규칙
- CLOSED 계약은 실제 새 결함이 나오기 전까지 다시 열지 않는다.
- 이미 읽은 파일을 안심용으로 재독하지 않는다.
- 같은 검색을 표현만 바꿔 반복하지 않는다.
- 하나의 문제를 해결하다 새 문제를 발견해도, 그 새 문제가 Codex가 빠르게 처리할 구현 문제라면 기록만 하고 추적을 멈춘다.
- 테스트는 Packet 종료 시점에만 묶어서 한다. 작은 수정마다 돌리지 않는다.

## 5. 강제 STOP 조건
다음 중 하나가 발생하면 즉시 현재 작업을 멈추고 `CODEX-FIRST`로 전환한다.
- 설계 하나 때문에 코드/문서 파일을 계속 추가로 열고 있다.
- '이것도 확인해야 정확하다'가 두 번 이상 연속 발생한다.
- 구현 클래스/메서드/트랜잭션 세부를 따라가고 있다.
- 한 Packet을 오래 붙잡고 다음 영역으로 못 넘어가고 있다.
- 지금 작업을 Codex가 repo-wide context로 더 빨리 처리할 수 있다.

멈춘 뒤에는 `확정된 설계 / 남은 구현 / Codex에 넘길 항목`만 짧게 기록하고 다음 선행설계로 이동한다.

## 6. 사용자 보고 방식
- 시작 전: `NOW-DESIGN / CODEX-FIRST / CLOSED` 판정을 한 문장으로 알린다.
- 진행 중: 의미 있는 설계 변경/BLOCK만 보고한다.
- 구현 상세 탐색 상황을 장황하게 중계하지 않는다.
- 사용자에게 이미 보고한 내용을 반복하지 않는다.

## 7. 하루 종료 규칙
사용자가 오늘 작업을 끝내거나 내일 시작 프롬프트를 요청하면 반드시 함께 제공한다.
1. 오늘 실제로 닫은 것
2. Codex로 넘긴 것
3. 다음 시작점 한 개
4. 이 `AIRA Working Manual` 핵심 규칙을 포함한 다음날 시작 프롬프트

## 8. 위반 시 복구
내가 이 매뉴얼을 어기고 Codex 몫까지 파고들고 있음을 발견하면 '여기까지만 마저 하자'고 계속하지 않는다. 즉시 멈추고, 이미 얻은 결과만 보존한 뒤 다음 고가치 NOW-DESIGN 항목으로 이동한다.

## 현재 즉시 적용
- P5 추가 코드 추적/구현 준비/문서 확장은 지금 중단한다.
- P5에서 이미 확정한 설계는 보존한다.
- Filing Discovery 구현, expected receipt 전파, 다중 파일 Historical Exact 통합, E2E는 Codex 작업으로 넘긴다.

## Daily handoff rule — cumulative
- This manual is cumulative. New rules may be appended, but existing user rules are not silently removed or weakened.
- At the end of every workday/session, the next-day start prompt MUST include: (1) the exact next starting point, (2) current CLOSED/OPEN/BLOCKED state, and (3) the current manual core rules.
- The next-day prompt must explicitly remind the assistant to classify each task as CLOSED / NOW-DESIGN / CODEX-FIRST before acting.
- The next-day prompt must explicitly forbid repeated audits, repeated scans, reopening CLOSED contracts, manual bulk implementation, repeated regression runs, and unnecessary repo exploration.
- If the manual grows, summarize only operational rules in the daily prompt, while keeping this file as the full authoritative cumulative version.
- If the user adds or corrects a working rule, append it here the same day and use the newest rule from that point forward.

## Project sequence / urgency
- Until 2026-09-15 before lunch: maximize high-value AIRA pre-Codex design closure without doing Codex implementation work.
- When Codex becomes available: execute the prepared AIRA packets quickly, close implementation/integration/tests, and finish AIRA.
- Immediately after AIRA is sufficiently complete for portfolio/employment use, switch primary focus to Project2 `잇결` portfolio completion; do not restart its research from zero.
- Daily next-day handoff must preserve both the current AIRA stopping point and this next-project sequence.
- Overall objective is not to perfect one packet indefinitely; it is to complete AIRA and then `잇결` as fast as possible without sacrificing correctness.

## Portfolio sequence correction (2026-09-13)
- After AIRA is finished to employment-portfolio quality, the next main portfolio is CredoBounty, not 잇결.
- CredoBounty should be rebuilt as an employment-focused case study; do not simply polish the existing 68-page master deck.
- 잇결 remains a valuable secondary case study, especially for UX research / interaction design differentiation.
- Current priority: AIRA → CredoBounty → 잇결.
- Do not restart CredoBounty or 잇결 research from zero; reuse the existing project evidence and prior decisions.
