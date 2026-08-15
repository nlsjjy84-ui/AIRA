# ADR-002: Evidence와 Assessment 분리

- 상태: Accepted
- 기준일: 2026-08-14

## Context

외부에서 확인 가능한 사실과 AIRA 또는 AI의 해석을 같은 데이터로 취급하면 사용자가 근거와 추론을 구분할 수 없다. AI 생성 문장은 오류 가능성이 있으며 원문 근거를 대체할 수 없다.

## Decision

외부에서 확인 가능한 `Evidence`와 분석 결과인 `Assessment`를 분리한다.

- Evidence는 외부 원문과 출처로 추적 가능한 근거만 나타낸다.
- AI가 생성한 문장은 Evidence가 아니다.
- Assessment는 중요도, 요약, 확신 수준, 불확실성, 시간 범위와 상태를 표현한다.
- Assessment가 사용한 Evidence를 추적할 수 있어야 한다.
- Entity별 영향은 `Assessment → Impact → Entity`로 표현한다.
- 불확실한 해석을 확정 사실 또는 투자 지시로 표현하지 않는다.

## Alternatives Considered

1. **Evidence와 분석을 하나의 본문으로 저장**: 구현은 단순하지만 출처와 해석의 경계가 사라진다.
2. **AI 출력 전체를 Evidence로 간주**: 검색과 재사용은 쉽지만 근거의 의미를 훼손하고 오류를 사실처럼 전파한다.
3. **Event에 단일 감성 점수 저장**: Entity와 시간 범위에 따른 서로 다른 영향을 표현하지 못한다.

## Consequences

- 사용자와 운영자가 판단 근거를 검토할 수 있다.
- Evidence 변경 시 영향을 받는 Assessment를 식별하고 재평가할 수 있다.
- 데이터 구조와 조회 흐름이 단순 요약 저장보다 복잡해진다.
- 분석 결과에는 근거 연결과 불확실성 표현이 필수 품질 기준이 된다.

