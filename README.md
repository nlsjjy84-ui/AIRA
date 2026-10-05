# AIRA

AIRA는 공개 시장정보를 출처와 근거에 연결해 제공하는 애플리케이션입니다. 현재 사용자 화면은 실제 데이터가 준비된 회사를 둘러보고, 정확한 보고 기간의 핵심 재무정보와 OpenDART 공시 근거를 확인하는 공개 흐름을 제공합니다.

## AIRA v1 status

**AIRA v1 core scope is COMPLETE as of 2026-09-21, and KRX/ECOS official-data ingestion was live-verified on 2026-09-22.**

**Live Demo:** https://aira-production-dfd0.up.railway.app

핵심 사용자 흐름은 `MAIN → 검색 → Ask → Inspect → Relate → Assess`이며, 개인금융은 이 탐색 흐름과 분리된 별도 진입 영역으로 유지합니다.

현재 v1에서 확인할 수 있는 주요 범위:

- KRX 공식 Open API 6종: 유가증권·코스닥 종목기본정보, 유가증권·코스닥 일별매매정보, KOSPI·KOSDAQ 시리즈 일별시세정보
- KRX 종목기본정보와 일별매매정보의 동일 시장·동일 거래일 식별자 매핑, 전체 응답 Evidence 보존, 대표 지수 metric Fact 등록
- 한국은행 ECOS `Source → Series Identity → Observation` 계약과 `REAL_GDP` 분기 관측값 수집
- OpenDART 연간 연결재무제표 기반 매출·영업이익과 Historical Exact 기간/접수번호 검증
- 공식 Evidence에 연결된 Confirmed Event와 Current/Historical Assessment
- Interest → Briefing → Alert 개인화 흐름
- 별도 Personal Finance 진입과 재인증·동의·데이터 관리 경계
- 데스크톱/모바일 공통 탐색 UI와 수직 스크롤·가로 overflow 방지
- 외부 뉴스 제공자 장애가 시장 지수·공식 사건·판단 흐름으로 전파되지 않는 격리 처리

2026-09-21 최종 통합 검증에서는 실제 retained PostgreSQL 데이터 기준으로 NAVER 2025 Historical Exact가 동일 OpenDART 접수번호 `20260313001021`의 공식 재검증을 거쳐 `AVAILABLE`임을 확인했고, KOSPI/KOSDAQ Evidence, Search → Ask → Inspect → Relate → Assess 브라우저 흐름, Finance 비로그인 보안 경계, 모바일 390px 스크롤/overflow를 확인했습니다. Web unit/integration baseline은 71/71 PASS, production build PASS, browser E2E는 8 PASS와 환경 의존 5 skip 상태입니다.

2026-09-22 공식 데이터 통합 검증에서는 KRX 6개 endpoint를 거래일 `2026-09-21`로 실제 호출해 KOSPI 942종목, KOSDAQ 1,818종목과 지수 응답 54/40행을 검증했습니다. PostgreSQL에는 Evidence 6건, Security 2,760건, Fact/Assertion 각 22,086건이 저장됐고, 저장값 대조와 동일 응답 재실행의 멱등성을 확인했습니다. 지수 전체 행은 Evidence에 보존하되 대표 metric만 Fact로 등록합니다. ECOS는 `200Y104 / 1400 / Q` REAL_GDP의 `2025Q1`, `2025Q2`를 실제 호출해 Evidence 1건과 Fact/Assertion 각 2건을 검증했습니다. provider HTTP는 DB transaction 밖에서 수행하며, canonical evidence 충돌·실패 rollback·동일 재실행을 검증했고 Event/Assessment는 생성하지 않습니다. API 전체 회귀는 158 suites, 664 tests, failures 0, errors 0, skipped 10입니다. 상세 근거는 [KRX verification](docs/KRX_VERIFICATION_2026-09-22.md)과 [ECOS verification](docs/ECOS_VERIFICATION_2026-09-22.md)에 기록했습니다.

시장 뉴스는 GDELT DOC 2.0 메타데이터를 별도 보조 정보로 사용합니다. 외부 제공자가 지연되거나 실패하면 오류/직전 캐시 상태를 표시하며 AIRA의 Fact·Event·Assessment와 섞거나 대체하지 않습니다.

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

공식 데이터 live adapter를 별도로 실행할 때만 필요한 값:

- `OPENDART_API_KEY`: OpenDART
- `AIRA_KRX_AUTH_KEY`: KRX Open API
- `BOK_ECOS_API_KEY`: 한국은행 ECOS Open API

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

## Loading the full KRX universe (deployed demo)

The default KRX stock refresh only stores the two representative securities (`KOSPI:000660,KOSPI:035420`), so the deployed demo can only search those. The 2,760 securities in `docs/KRX_VERIFICATION_2026-09-22.md` come from an isolated verification database, not from the deployed one. To load every listed security, set these process environment variables on the deployment once, start it, and then remove them so restarts do not call KRX again:

```text
AIRA_KRX_STOCK_REFRESH_ENABLED=true
AIRA_KRX_STOCK_REFRESH_MODE=live
AIRA_KRX_AUTH_KEY=<your KRX Open API key>
AIRA_KRX_STOCK_REFRESH_TARGETS=KOSPI:ALL,KOSDAQ:ALL
```

`MARKET:ALL` stores the whole market; a six-digit code still stores one security. Search matches names and aliases anywhere in the text and symbols exactly. OpenDART financial statements remain limited to the representative companies prepared by the demo bootstrap.

## Demo data status

새 로컬 PostgreSQL 환경에서 공식 OpenDART 데이터 경로를 명시적으로 준비할 수 있습니다. Bootstrap은 기본 실행에서 비활성이고, live OpenDART mode와 API key를 함께 opt-in해야 합니다. Fresh schema 적용, 대표 기업 준비, 재실행 검증과 UI 확인 절차는 [Official Demo Bootstrap & Runbook v1](docs/runbooks/OFFICIAL_DEMO_BOOTSTRAP_V1.md)을 따르세요.

Architecture와 데이터 경계 변경 전에는 `docs/architecture`와 `docs/adr`을 먼저 확인하세요.
