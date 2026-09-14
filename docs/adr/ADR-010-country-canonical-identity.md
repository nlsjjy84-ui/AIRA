# ADR-010: Country Canonical Identity

- Status: Accepted
- 기준일: 2026-09-12
- 상위 기준: AIRA 기준 명세 v1.0, ADR-006, ADR-009
- 적용 Packet: ECOS-28

## Context

V1 `entity`는 `COUNTRY`와 ISO 3166-1 alpha-2 형식의 `country_code`를 이미 허용하지만,
COUNTRY의 canonical identity 생성 규칙과 중복 방지 규칙은 아직 정의하지 않았다.
ECOS REAL_GDP series는 대한민국 COUNTRY subject를 필요로 하므로 이름 추론이나
provider code를 사용하지 않는 명시적 bootstrap 계약이 필요하다.

## Decision

COUNTRY canonical identity는 AIRA 내부에서 `COUNTRY:{country_code}`로 결정론적으로 만든다.
`country_code`는 대문자 ISO alpha-2 형식이며 COUNTRY에서는 필수다.
대한민국의 고정 bootstrap 계약은 다음과 같다.

- `entity_type = COUNTRY`
- `canonical_key = COUNTRY:KR`
- `canonical_name = Republic of Korea`
- `country_code = KR`
- `market_code = NULL`, `symbol = NULL`
- 최초 생성 시 `active = true`

`canonical_name`은 표시/설명 값이며 identity 비교를 이름 추론으로 수행하지 않는다.
Bootstrap은 위 고정 metadata와 정확히 일치하는 기존 row만 재사용한다.
같은 canonical key가 다른 type/name/code/state로 존재하면 자동 수정하지 않고 BLOCK한다.

`country_code`는 이미 Entity의 provider-neutral 표준 필드이므로 ISO alpha-2 값을
`entity_external_identifier`에 중복 등록하지 않는다. ECOS STAT/ITEM code는 COUNTRY의
canonical key, country_code 또는 external identifier가 될 수 없다.

## Database Contract

V13은 기존 migration을 수정하지 않고 다음 제약만 additive하게 추가한다.

- COUNTRY row는 `country_code IS NOT NULL`이어야 한다.
- COUNTRY row는 `canonical_key = 'COUNTRY:' || country_code`여야 한다.
- COUNTRY row의 `market_code`, `symbol`은 NULL이어야 한다.
- COUNTRY의 `country_code`는 partial unique index로 유일해야 한다.

기존 COUNTRY row가 이 계약과 충돌하면 migration이 조용히 보정하지 않고 실패해야 한다.
비-Country Entity의 기존 `country_code` 사용은 변경하지 않는다.

## Boundary

- COMPANY canonical identity의 opaque UUID 규칙은 변경하지 않는다.
- Entity external identifier registry와 OpenDART 규칙을 변경하지 않는다.
- Statistical Series, Fact, Event, Assessment 의미를 변경하지 않는다.
- 대한민국 bootstrap은 ECOS 호출이나 Source 등록을 수행하지 않는다.
- 국가명 문자열, ECOS series name 또는 provider metadata로 COUNTRY를 자동 매칭하지 않는다.
