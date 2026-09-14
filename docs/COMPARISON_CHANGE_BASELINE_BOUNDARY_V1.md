# Comparison Change Baseline Boundary v1

## Core rule
Any change value must state its baseline explicitly. A percentage or delta without a named comparison basis is not a valid AIRA comparison.

## Required baseline semantics
- year-over-year compares the same comparable fiscal/reporting period of the prior year;
- quarter-over-quarter compares the immediately preceding comparable quarter;
- trading-day change compares the immediately prior valid trading observation only when that is the stated metric;
- custom comparisons must surface both baseline period/date and target period/date.

## Forbidden behavior
- do not label a change merely as `증가/감소` without the comparison basis;
- do not silently switch from YoY to QoQ because one baseline is missing;
- do not compare non-aligned durations as if they were the same-period growth rate;
- do not use collection time as the baseline for economic change.

## Missing baseline
If the required baseline Fact is missing, conflicting, or period-incompatible, the change is unavailable rather than guessed.

## Implementation boundary
Comparison calculators, query strategy, derived Fact storage, UI controls, and tests are CODEX-FIRST/later work.