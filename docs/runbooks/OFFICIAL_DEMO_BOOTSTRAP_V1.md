# Official Demo Bootstrap & Runbook v1

This runbook prepares AIRA's existing official-data product flow in a fresh local PostgreSQL database:

```text
Company → OpenDART provenance → Fact/Evidence → Event → Assessment
        → existing Interest / Briefing / Alert consumers
```

The bootstrap is disabled by default. It has no fixture mode because this repository does not contain a licensed, complete official response fixture for the representative annual filings. The only supported mode is an explicitly enabled live OpenDART run. Normal application startup and normal tests do not make this network call.

## Representative data

The bootstrap uses the exact pair already covered by AIRA's official multi-company preparation test:

- `000660` — resolved from the current official OpenDART company directory
- `035420` — resolved from the current official OpenDART company directory
- business year `2025`

Names, OpenDART corporation codes, fiscal year-end months, filing identifiers, financial values, dates, and evidence are resolved from OpenDART. They are not embedded or inferred by the bootstrap.

## Prerequisites

- Windows PowerShell
- JDK 25
- Node.js 24+ and npm
- PostgreSQL with an empty local database named `aira`
- an OpenDART API key authorized for the official APIs used by AIRA

The PostgreSQL user configured in `application.properties` is `postgres`. The database must support `gen_random_uuid()` and the user must have the DDL rights needed for the initial Flyway run. Review [the Flyway policy](../database/FLYWAY_MIGRATION_POLICY.md) before applying migrations.

## 1. Set process-local configuration

Use real values only in the current PowerShell process. Do not save them in the repository or paste them into logs.

```powershell
Set-Location C:\AIRA\apps\api
$env:DB_PASSWORD = '<local-postgres-password>'
$env:AIRA_RECOVERY_EMAIL_ENCRYPTION_KEY = '<base64-encoded-256-bit-key>'
$env:AIRA_RECOVERY_EMAIL_LOOKUP_KEY = '<different-base64-encoded-256-bit-key>'
$env:OPENDART_API_KEY = '<opendart-api-key>'
```

Recovery email keys are required for application bean initialization even though bootstrap does not use account recovery. They must be two different valid 256-bit keys.

## 2. Initialize the fresh schema and run bootstrap

Flyway is opt-in. The following command explicitly enables both schema initialization and the live bootstrap:

```powershell
$env:FLYWAY_ENABLED = 'true'
$env:AIRA_DEMO_BOOTSTRAP_ENABLED = 'true'
$env:AIRA_DEMO_BOOTSTRAP_MODE = 'live'
$env:AIRA_DEMO_BOOTSTRAP_BUSINESS_YEAR = '2025'
.\gradlew.bat --no-daemon bootRun
```

Do not enable bootstrap against production-like execution. No HTTP trigger exists. The runner calls the existing official company preparation operation first and the existing event-assessment preparation operation second.

Expected log shape after success:

```text
Official demo bootstrap complete: companies=2, events=2, assessments=2 [000660=<entity-uuid>, 035420=<entity-uuid>]
```

The exact UUIDs and company names are data-derived and may differ between fresh databases. The process remains running as the API server at `http://127.0.0.1:8080`.

## 3. Health and API checks

In a second PowerShell terminal:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
$companies = Invoke-RestMethod http://127.0.0.1:8080/api/companies
$companies.companies | Format-Table companyId,canonicalName,countryCode
```

The health response should report `UP`. The catalog should include the two official company names resolved from stock codes `000660` and `035420`; the public catalog deliberately exposes AIRA's internal company UUID rather than the bootstrap input stock code.

For either returned `companyId`, inspect available reporting periods and events:

```powershell
$companyId = '<company-uuid-from-catalog>'
Invoke-RestMethod "http://127.0.0.1:8080/api/companies/$companyId/financial-periods"
Invoke-RestMethod "http://127.0.0.1:8080/api/companies/$companyId/events"
```

Use the exact `periodStart` and `periodEnd` returned by the periods endpoint when calling `financial-facts`.

## 4. Start and verify the web experience

In another PowerShell terminal:

```powershell
Set-Location C:\AIRA
npm.cmd install
npm.cmd run dev:web
```

Open `http://127.0.0.1:5173` and verify:

1. Both representative companies appear in the company catalog.
2. Selecting a company shows its exact annual reporting period.
3. Revenue and operating income show OpenDART source and filing evidence.
4. The related earnings Event shows its rule-based Assessment and uncertainty.
5. Create or sign in to a local account and save a representative company as an interest.
6. Confirm the existing Briefing includes the shared Assessment.
7. Enable the company's in-app alert setting and confirm the existing Alert experience reconciles eligible new assessments.

Briefing and Alert are existing consumers; bootstrap does not change their policy or implementation.

## 5. Rerun and idempotency

Stop the API with `Ctrl+C`, then run the same `bootRun` command with the same environment variables. Existing unique keys and preparation logic reuse the logical Company, identifier, Fact, Evidence, Event, and Assessment records. The summary should again show two companies, two events, and two assessments, with the same entity UUIDs.

For an explicit live PostgreSQL rerun verification against a dedicated disposable database:

```powershell
$env:AIRA_OFFICIAL_DEMO_BOOTSTRAP_E2E = 'true'
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/<fresh-disposable-database>'
.\gradlew.bat --no-daemon test --tests com.aira.api.demo.OfficialDemoBootstrapPostgresTests
```

The datasource override must name a newly created, empty, disposable database; never point this verification at an existing `aira` development database. The test enables Flyway, calls live OpenDART, runs bootstrap twice, and verifies official Fact/Evidence/Event/Assessment records, stable company identities, company-event queries, Interest consumption, Briefing, and Alert. It is skipped unless the E2E flag, DB password, and OpenDART key are all present. `--no-daemon` ensures the live test does not reuse a Gradle daemon started under a different network environment.

## 6. Return to normal startup

Remove the bootstrap opt-in before an ordinary application run:

```powershell
Remove-Item Env:AIRA_DEMO_BOOTSTRAP_ENABLED -ErrorAction SilentlyContinue
Remove-Item Env:AIRA_DEMO_BOOTSTRAP_MODE -ErrorAction SilentlyContinue
Remove-Item Env:AIRA_DEMO_BOOTSTRAP_BUSINESS_YEAR -ErrorAction SilentlyContinue
Remove-Item Env:AIRA_OFFICIAL_DEMO_BOOTSTRAP_E2E -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
```

Flyway may also be disabled after the schema exists:

```powershell
Remove-Item Env:FLYWAY_ENABLED -ErrorAction SilentlyContinue
```

There is no application-level database reset command. Use only a dedicated disposable database for clean-room reruns; do not run Flyway `clean` against shared or retained data.
