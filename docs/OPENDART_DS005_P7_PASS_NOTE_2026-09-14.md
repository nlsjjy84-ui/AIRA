# P7 OpenDART DS005 PASS NOTE — 2026-09-14

Status: PASS for the approved 35 automatic endpoints; `astInhtrfEtcPtbkOpt` remains HOLD.

The seven wave counts are `4+4+4+7+8+4+4=35`, with one explicit HOLD. `OpenDartDs005Catalog` fixes the endpoint key, approved EventType, and official neutral Korean title. Explicit date windows and 8-digit corp codes are validated before HTTP. Status `000` is parsed, `013` yields no events, and other statuses use the existing provider categories. The credential-bearing request URI never enters Evidence or error text.

P7 validates response rows, groups them by exact company and receipt, retains distinct rows in one order-independent whole-receipt SHA-256 hash, and passes one structured Evidence input per receipt to P6. Invalid receipt rows are reported separately. P6 creates canonical COMPANY SUBJECT and structured SUPPORTS relations in its own transaction; it preserves idempotency and rejects changed Evidence. Neither collection nor receipt time is used as occurrence or publication time.

Targeted verification: `OpenDartDs005WaveTests` 11 passed (all seven waves, 35+1 checksum, hash/order/window behavior, rejected receipt, status); `OpenDartMaterialEventRegistrationPostgresTests` 5 passed (including parser-to-P6 multi-row receipt, idempotency and conflict; existing concurrency and rollback coverage). Flyway validated all 16 existing migrations on the disposable PostgreSQL test database. No migration changed or added.

Endpoint-specific field meanings beyond the historical verified matrix remain unspecified. No provider credentials or live provider requests were needed.
