# Briefing / Alert Newness Boundary v1

## Core rule
`새 정보`는 단순히 더 늦게 수집된 데이터가 아니다. AIRA가 이전에 보지 못한 새 Event/Assessment/Evidence identity 또는 기존 identity에 대해 명시적으로 승인된 새 상태가 생긴 경우에만 새 정보 후보가 된다.

## Newness meaning
- 새로운 `collectedAt`만으로는 새 정보가 아니다.
- 동일 Evidence/Fact/Event/Assessment의 재수집·재조회는 새 정보가 아니다.
- 동일 canonical content의 idempotent reuse는 새 알림/브리핑 원인이 아니다.
- 새 `rcept_no`, 새 Event identity, 새 COMPLETED Assessment처럼 의미 있는 새 identity는 새 정보 후보가 될 수 있다.
- 기존 Fact가 `CONFLICTING`으로 바뀌거나 기존 Assessment가 explicit supersession으로 교체되는 경우는 상태 변화로서 별도 후보가 될 수 있다.

## Restraint
- provider polling 빈도나 ingestion 실행 횟수는 사용자에게 보이는 새 정보 개수를 늘리지 않는다.
- 수집 실패 후 재시도 성공도, 동일 identity/content라면 중복 새 정보로 취급하지 않는다.
- 단순 UI 재정렬/정렬시각 변경은 새 정보가 아니다.

## Implementation boundary
Dedup query, delivery marker, read/unread state, notification fan-out, retry handling and tests are CODEX-FIRST. Existing Briefing/Alert accepted semantics stay closed.