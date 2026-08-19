# ADR-007: Company Financial Facts Public Read

## Status

Accepted — 2026-08-19

## Context

AIRA needs an HTTP boundary for official company financial facts without exposing persistence
entities or requiring clients to understand Fact, FactAssertion, Evidence, and Source joins.
These facts are public regulatory information and contain no user, session, or credential data.

## Decision

- `GET /api/companies/{companyId}/financial-facts` is an explicitly public read endpoint.
- `periodStart` and `periodEnd` are required and identify an exact reporting period.
- `predicates` is optional; omission requests all predicates supported by read v1.
- Latest/current selection is not defined in v1.
- Multiple valid facts or provenance records are returned without selecting one arbitrarily.
- `publishedAt` and `collectedAt` retain distinct meanings. A null publication time is not
  replaced by collection time.
- Evidence identity and stored provenance URL are returned so clients can group facts from the
  same filing without reconstructing source URLs.
- Public exposure is expressed by an explicit GET-only security matcher. User-specific and
  personalized endpoints remain authenticated.

## Alternatives Considered

- Requiring authentication was rejected because the response contains official public facts and
  should be available before personalization is needed.
- Relying implicitly on the global `permitAll` fallback was rejected because it would hide the
  intended exposure policy and could accidentally broaden future methods.
- A latest-annual endpoint was deferred because revision, status, conflicting assertions, and
  provenance priority require a separate policy.

## Consequences

Anonymous and authenticated clients can retrieve exact-period financial facts with provenance.
Future write methods are not covered by the public GET matcher. A future latest-annual query must
define its own selection and revision semantics before implementation.
