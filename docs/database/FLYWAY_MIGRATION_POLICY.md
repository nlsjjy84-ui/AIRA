# AIRA Flyway Migration 운영 원칙

## 현재 상태

`V1`~`V4`는 Frozen AIRA ERD v1을 옮긴 최초 migration 초안이다. 아직 어떤 데이터베이스에도 적용하지 않는다. 애플리케이션 설정에서 Flyway는 기본적으로 비활성화되어 있으며, 운영자가 검토 후 `FLYWAY_ENABLED=true`를 명시한 경우에만 실행 대상으로 삼는다.

## 적용 전 검토

1. 대상 DB가 PostgreSQL이며 `gen_random_uuid()`를 지원하는 버전인지 확인한다.
2. 대상 DB가 비어 있는지, 기존 객체 또는 `flyway_schema_history`가 있는지 확인한다.
3. 네 migration의 checksum, FK 순서, CHECK, unique 및 index를 별도 검증 DB에서 검토한다.
4. DB 사용자에게 필요한 DDL 권한만 부여했는지 확인한다.
5. 백업·복구 절차와 실패 시 처리 방식을 확인한다.
6. Argon2id benchmark, 암호화·keyed-hash 키 관리와 token 생성 정책을 먼저 확정한다.
7. 개인정보 및 보안 데이터의 실제 보존기간을 확정한다.

## 불변 migration 원칙

- 공유 환경에 한 번이라도 적용된 versioned migration은 수정하거나 이름을 바꾸지 않는다.
- 변경이 필요하면 다음 버전의 새 migration으로 추가한다.
- 이미 적용된 파일을 수정해 checksum을 맞추거나 `repair`로 변경을 은폐하지 않는다.
- `clean`은 운영·공유 DB에서 사용하지 않는다.
- Architecture 또는 Frozen ERD와 충돌하는 변경은 migration 작성 전에 ADR 또는 명시적 검토를 거친다.
- migration 실행과 애플리케이션 배포 권한은 가능한 범위에서 분리한다.

## Hibernate 역할

Hibernate는 `spring.jpa.hibernate.ddl-auto=validate`만 사용한다. 스키마 생성·수정은 Flyway migration만 담당한다. Flyway가 비활성화된 상태에서 스키마가 아직 없다면 애플리케이션 기동 검증은 실패하는 것이 정상이다.
