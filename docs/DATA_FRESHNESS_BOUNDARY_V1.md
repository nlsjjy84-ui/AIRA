# Data Freshness Boundary v1

## Core rule
Freshness is determined from the source observation/reporting date/period appropriate to the Fact/Event/Assessment, not merely from when AIRA collected the Evidence.

## Separation
- provider observation/reporting period = when the underlying fact/event applies;
- `collectedAt` = when AIRA fetched/registered the evidence;
- re-collecting old data does not make the underlying observation current;
- Current and Historical Exact semantics remain distinct.

## Product restraint
- do not label a stale observation as current solely because it was recently collected;
- do not use `collectedAt` as a substitute for provider observation date/period;
- do not silently replace missing current data with the latest historical value without clearly marking it as historical/stale context;
- provider-specific freshness windows must be explicit if later introduced.

## User-facing meaning
Where freshness matters, expose the applicable date/period and, when useful, separately expose AIRA collection time. Use neutral stale/unavailable wording instead of implying real-time coverage.

## Implementation boundary
Freshness thresholds, staleness flags, scheduling, cache expiry, UI badges, and tests are CODEX-FIRST/later work.