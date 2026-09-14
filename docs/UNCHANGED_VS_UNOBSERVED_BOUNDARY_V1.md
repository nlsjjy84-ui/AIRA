# Unchanged vs Unobserved Boundary v1

## Core rule
`no new observed value` does not mean `the value is unchanged`.

## Required distinctions
- Observed same value: provider/Evidence explicitly supports the same value again.
- No observation / NO_DATA: AIRA has no new supported value for that scope.
- Provider failure: observation was attempted but not successfully obtained.
- Historical carry-forward: may be displayed only as a prior known value, never relabeled as newly observed/current unless the product contract explicitly permits it.

## Forbidden behavior
- do not forward-fill missing observations and call them provider facts;
- do not infer `unchanged` from silence;
- do not convert NO_DATA or provider failure into zero;
- do not create synthetic Evidence merely to preserve a previous value.

## Presentation rule
If a prior value is shown beside a missing current observation, label it as the prior known value and make the missing/current status explicit.

## Implementation boundary
Carry-forward queries, chart gaps, caching, UI labels, and tests are CODEX-FIRST/later work.