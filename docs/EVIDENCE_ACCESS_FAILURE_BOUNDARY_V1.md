# Evidence Access Failure Boundary v1

## Core rule
A temporary inability to open an Evidence URL is not itself proof that the historical Evidence claim is false or should be deleted.

## Distinguish states
- Evidence identity/provenance exists and content was previously validated;
- original URL is currently reachable;
- original URL is currently unavailable;
- provider explicitly withdrew/replaced content under an accepted lineage contract.

These are different states.

## Forbidden behavior
- do not delete or invalidate a Fact solely because an external link later breaks;
- do not treat provider outage as claim contradiction;
- do not silently replace historical Evidence with a newer page that happens to load;
- do not claim original-document verification if retrieval did not succeed.

## Product meaning
Inspect may show that the original source is currently unavailable while preserving the stored provenance and its validation history.

## Implementation boundary
Link health checks, cached/archive policy, retry behavior, UI state, and tests are CODEX-FIRST/later work.