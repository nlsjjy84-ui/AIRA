# Alert / Decision Boundary v1

## Core rule
AIRA Alert는 `새로 확인할 정보가 생겼다`는 신호이지 매수·매도·보유·긴급성 판단이 아니다.

## Meaning
- Alert 존재 자체가 호재/악재 점수나 투자 중요도 순위를 의미하지 않는다.
- Alert는 사용자의 명시적 Interest 및 accepted eligibility semantics 안에서만 생성된다.
- `급등`, `실적`, `공시`, `위험` 같은 사건 유형만으로 행동 지시를 만들지 않는다.
- Alert에서 사용자가 판단해야 할 핵심 Evidence/Assessment 경로를 Inspect/Assess로 이어준다.

## Forbidden framing
- `지금 사야 함`, `매도 필요`, `놓치면 안 됨`, `강력 추천` 같은 행동 유도 표현 금지.
- 광고/상업적 우선순위가 Alert qualification/order에 개입하면 안 된다.
- AI confidence를 사용자 행동 확률처럼 표시하지 않는다.

## Product meaning
Alert의 역할은 주의를 요청하는 것이 아니라 `새 근거가 생겼음을 알려 사용자가 확인할 수 있게 하는 것`이다.

## Implementation boundary
Severity UI, delivery channels, ordering, copy templates, push/email support and tests are CODEX-FIRST/later work.