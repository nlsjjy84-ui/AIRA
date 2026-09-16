# AIRA Personal Finance Security v1

Status: IMPLEMENTED foundation + explicit production boundaries (2026-09-16)

## Purpose
Personal-finance data is an optional AIRA capability, not a replacement for AIRA's market/company analysis core.
A normal AIRA login is required but is not sufficient for sensitive finance operations.

## Implemented now
- Existing AIRA session authentication remains the primary account login.
- Finance access requires password step-up reauthentication after login.
- Successful step-up issues a separate opaque finance grant valid for 10 minutes.
- Only the grant hash is stored; the raw grant is returned only in an HTTP-only cookie.
- The finance cookie is scoped to `/api/me/finance` and follows the session Secure/SameSite policy.
- Each finance grant is bound to the exact AIRA session that created it.
- Logout, session revocation, idle expiry, or absolute expiry makes the finance grant unusable.
- Reissuing a grant revokes the previous active grant for that session.

## Reauthentication abuse protection
- Finance reauthentication failures are persisted in PostgreSQL, not process memory.
- Five consecutive password failures lock finance reauthentication for 15 minutes.
- Lock state survives application restart and is serialized per user in the database.
- A successful reauthentication clears the finance-only failure state.
- Failure responses do not reveal whether the password, session, or lock state caused rejection.

## Consent and ownership
- Consent is stored as append-only history: source, provider, policy version, allowed scopes, consent time, revoke time.
- Active consent is never silently overwritten; it must be explicitly revoked first.
- Revoking consent immediately disconnects connections created from that consent.
- Connection removal and previously imported data deletion are separate user actions.
- Cross-user consent/connection/session relationships are blocked by database constraints.
- Missing and foreign consent identifiers intentionally produce the same not-found response.

## Explicit erasure
- The user can explicitly erase personal-finance data without deleting the AIRA account.
- Erasure removes budgets, transactions, finance accounts, connections, consent history, grants, and finance reauthentication guard state.
- Destructive erasure requires a valid short-lived finance grant and clears the finance cookie afterward.

## Data minimization
- Raw account numbers, raw card numbers, provider passwords, and provider access tokens are not stored in the current foundation.
- External account/transaction identity is represented by stable hashes where identity is required.
- `DEMO_IMPORT` must remain visibly distinct from a real `MYDATA_API` connection.
- Personal-finance rows remain separate from AIRA public canonical Fact/Evidence tables.

## Production-only controls not faked in the portfolio build
These controls are required before a real regulated MyData connection is claimed:
- provider-standard authorization/consent flow and institution identity verification;
- mTLS or equivalent provider-to-provider authenticated transport where required;
- KMS/HSM or dedicated secret vault for provider credentials and token encryption/rotation;
- field-level encryption for any sensitive raw financial fields that later become necessary;
- centralized API gateway/WAF rate limits in addition to application reauthentication throttling;
- tamper-resistant security audit logging that records access metadata without logging financial payloads;
- encrypted backups, retention/deletion schedules, incident response, and key-compromise procedures;
- anomaly detection for unusual bulk reads, exports, device changes, and repeated authorization failures.

## AI boundary
- Finance data is not sent to an AI provider by this security layer.
- Future AI explanation should prefer aggregates and pseudonymous transaction references over raw identifiers.
- Account/card identifiers, authentication material, and unnecessary transaction memo data must be removed before model input.
- RULE analysis remains labeled RULE until a real configured AI execution succeeds.

This document does not claim that AIRA currently has a live financial-institution MyData connection.
