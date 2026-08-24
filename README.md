# AIRA

AIRA는 공개 시장정보를 출처와 근거에 연결해 제공하는 애플리케이션입니다. 현재 사용자 화면은 실제 데이터가 준비된 회사를 둘러보고, 정확한 보고 기간의 핵심 재무정보와 OpenDART 공시 근거를 확인하는 공개 흐름을 제공합니다.

## Repository layout

- `apps/api`: Java 25, Spring Boot, PostgreSQL backend
- `apps/web`: React, Vite 기반 public web frontend
- `docs/architecture`: 승인된 Architecture와 frozen ERD
- `docs/adr`: 주요 설계 결정
- `docs/database`: Flyway migration 정책
- `services`, `packages`, `infrastructure`: 후속 확장을 위해 예약된 디렉터리이며 현재 실행 구성은 없습니다.

## Prerequisites

- JDK 25
- PostgreSQL with an AIRA database
- Node.js 24+ and npm

## Local environment

비밀값은 파일이나 Git에 저장하지 말고 현재 terminal process의 환경변수로 전달합니다.

backend context 실행에 필요한 값:

- `DB_PASSWORD`: local PostgreSQL password
- `AIRA_RECOVERY_EMAIL_ENCRYPTION_KEY`
- `AIRA_RECOVERY_EMAIL_LOOKUP_KEY`

이 두 recovery key는 복구 API를 호출하지 않더라도 보호 bean 초기화에 필요합니다. 각 값은 유효한 별도 256-bit key여야 하며 README에 실제 값을 기록하지 않습니다.

실제 recovery email 전송을 사용할 때 추가로 필요한 값:

- `RESEND_API_KEY`
- `RESEND_FROM_EMAIL`
- `AIRA_PUBLIC_BASE_URL`

OpenDART live adapter를 별도로 실행할 때만 필요한 값:

- `OPENDART_API_KEY`

그 밖의 설정 이름은 `apps/api/src/main/resources/application.properties`에서 확인할 수 있습니다. 실제 secret, `.env`, local property 파일은 commit하지 않습니다.

## Run the backend

For the reproducible Windows startup path, including isolated database naming, masked secret input, Flyway initialization, health verification, web startup, and safe restart, follow [Local / Portfolio Runtime Readiness v1](docs/runbooks/LOCAL_RUNTIME_V1.md).

PostgreSQL schema는 `apps/api/src/main/resources/db/migration`의 Flyway migration과 일치해야 합니다. Flyway는 기본적으로 비활성화되어 있으므로 새 데이터베이스에 자동 적용된다고 가정하지 마세요. 적용 전 `docs/database/FLYWAY_MIGRATION_POLICY.md`를 확인해야 합니다.

PowerShell에서 `DB_PASSWORD`를 현재 process에만 설정한 뒤 실행합니다.

```powershell
Set-Location C:\AIRA\apps\api
.\gradlew.bat bootRun
```

Backend 기본 URL은 `http://127.0.0.1:8080`입니다.

## Run the frontend

최초 한 번 repository root에서 dependencies를 설치합니다.

```powershell
Set-Location C:\AIRA
npm.cmd install
npm.cmd run dev:web
```

Frontend 기본 URL은 `http://127.0.0.1:5173`입니다. 개발 서버는 relative `/api` 요청을 local Spring Boot backend로 proxy하므로 global CORS 확대가 필요하지 않습니다.

## Tests

Frontend component/integration tests와 production build:

```powershell
npm.cmd run test:web
npm.cmd run build:web
```

Backend tests는 `apps/api/src/test`에 있습니다. PostgreSQL E2E는 명시적인 local DB 설정과 비밀번호가 필요한 별도 검증입니다.

Browser E2E는 설치된 Microsoft Edge를 재사용하며 backend와 실제 test data가 준비된 상태에서 실행합니다.

```powershell
npm.cmd run test:e2e
```

## Demo data status

새 로컬 PostgreSQL 환경에서 공식 OpenDART 데이터 경로를 명시적으로 준비할 수 있습니다. Bootstrap은 기본 실행에서 비활성이고, live OpenDART mode와 API key를 함께 opt-in해야 합니다. Fresh schema 적용, 대표 기업 준비, 재실행 검증과 UI 확인 절차는 [Official Demo Bootstrap & Runbook v1](docs/runbooks/OFFICIAL_DEMO_BOOTSTRAP_V1.md)을 따르세요.

Architecture와 데이터 경계 변경 전에는 `docs/architecture`와 `docs/adr`을 먼저 확인하세요.
