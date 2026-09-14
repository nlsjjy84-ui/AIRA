# Non-Trading Day Interpretation Boundary v1

## Core rule
No KRX daily observation on a non-trading date is not equivalent to price/volume = 0.

## Product meaning
- no official trading observation -> no Market Fact for that metric/date;
- do not forward-fill the previous trading day's close as if it were observed on the closed date;
- do not create zero volume/value unless the provider explicitly publishes zero for a valid trading observation;
- freshness calculations must distinguish a closed market from a failed/stale collection.

## Comparison restraint
Trading-day change uses the previous valid trading observation under an explicit metric definition, not the previous calendar date.

## Historical truth
Weekends, holidays, suspensions, and provider NO_DATA must not all be collapsed into one semantic state without supporting evidence.

## Implementation boundary
Trading calendars, holiday data, suspension handling, freshness logic, UI, and tests are CODEX-FIRST/later work.