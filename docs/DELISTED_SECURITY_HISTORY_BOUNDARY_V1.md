# Delisted / Inactive Security History Boundary v1

## Core rule
A SECURITY becoming delisted/inactive does not erase or invalidate its historical identity, Facts, Events, Evidence, or Assessments.

## Product meaning
- listing status is time-sensitive state, not Entity identity;
- historical KRX observations remain attached to the same SECURITY;
- current UI may mark the security inactive/delisted while preserving historical inspection;
- delisted status must not be presented as if the security never existed.

## Restraint
- do not create a new SECURITY merely because listing status changes;
- do not delete historical Interest history or provenance automatically;
- do not treat absence from a current base-info snapshot as sufficient proof of a specific delisting date without official evidence.

## Current-vs-history
Current discovery policy may hide inactive securities by default, but explicit historical/search access is a separate product decision and must not rewrite history.

## Implementation boundary
Listing-status history model, delisting detection, default search filters, UI, and tests are CODEX-FIRST/later work.