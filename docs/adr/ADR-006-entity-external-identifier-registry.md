# ADR-006: Entity 외부 식별자 Registry

- 상태: Accepted
- 기준일: 2026-08-19

## Context

외부 공식 데이터 공급자는 AIRA의 내부 Entity UUID와 다른 식별자를 사용한다. 회사명이나
종목명 문자열 추측은 잘못된 Entity 연결을 만들 수 있고, 기존 `market_code`와 `symbol`만으로는
여러 namespace의 회사·증권 식별자를 일관되게 관리할 수 없다.

## Decision

`entity_external_identifier`는 외부 namespace, identifier type과 원문 식별자 값을 하나의
공개 시장 Entity에 연결한다.

- `(namespace, identifier_type, identifier_value)`는 하나의 Entity만 가리킨다.
- 동일 mapping의 재등록은 기존 mapping을 재사용한다.
- 다른 Entity로의 remapping은 자동 갱신하지 않고 identity conflict로 거부한다.
- 식별자 값은 종류별 의미를 보존하며 범용 대소문자 또는 숫자 정규화를 하지 않는다.
- OpenDART `CORP_CODE`는 leading zero를 보존한 8자리 문자열이며 `COMPANY`에만 연결한다.
- Source Registry의 provenance 책임과 Entity 외부 identity 책임을 분리한다.

## Alternatives Considered

1. **`entity.canonical_key`에 provider ID 저장**: Entity의 내부 canonical identity와 외부 ID가 결합되고 다중 식별자를 지원하지 못한다.
2. **`market_code`와 `symbol` 재사용**: 증권 식별 의미에 치우쳐 회사 식별자와 namespace 분리를 안전하게 표현하지 못한다.
3. **회사명 자동 매칭**: 이름 변경·동명이인·표기 차이 때문에 identity 무결성을 보장하지 못한다.

## Consequences

- 외부 provider adapter는 명시적 identifier mapping으로 내부 Entity를 조회할 수 있다.
- 새로운 namespace와 identifier type은 핵심 Entity 컬럼을 추가하지 않고 확장할 수 있다.
- mapping 생성은 운영상 명시적인 행위이며 잘못된 remapping은 별도 해결 절차가 필요하다.
- COMPANY와 SECURITY의 관계 및 Security 식별자는 별도 설계 범위로 남는다.
