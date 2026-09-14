# Assess v1 Product Boundary

## Role
`Assess / 판단` presents an already-qualified AIRA Assessment and its grounds so the user can make their own decision. It does not decide or recommend on the user's behalf.

## Must expose
- Assessment identity/status;
- whether the view is Current or Historical Exact;
- the CONFIRMED Event(s) and canonical subject(s) supporting the Assessment;
- Assessment Evidence and relevant Fact context used by the accepted Assessment contract;
- unresolved/conflicting/missing inputs that limit interpretation;
- explicit freshness/as-of information appropriate to the Assessment type.

## Product behavior
- follow existing accepted Current vs Historical Exact semantics; this document does not redefine them;
- never silently substitute Current data for a Historical Exact request or vice versa;
- never convert a `CONFLICTING` raw Fact into a resolved input without an explicit approved resolution contract;
- no buy/sell recommendation, target price, certainty score, or hidden AI verdict is introduced by the screen;
- when required Assessment prerequisites are absent, show unavailable/not-completed state rather than synthesizing an answer.

## Navigation
Assess can link back to Inspect for evidence detail and Relate for context. It does not change Interest automatically.

## Implementation boundary
Assessment DTOs, current/historical queries, visual explanation, evidence expansion, components, caching, and tests are CODEX-FIRST/later work.