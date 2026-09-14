# Zero / Negative Baseline Change Boundary v1

## Core rule
AIRA must not manufacture a conventional percentage change when the mathematical baseline makes that percentage undefined or economically misleading.

## Zero baseline
- baseline = 0 makes ordinary percentage change undefined;
- show absolute change and the two observed values instead;
- do not display infinity, an arbitrary cap, or `100%+` as a substitute.

## Negative baseline
- a percentage computed across negative/positive sign changes can be mathematically valid yet economically misleading;
- do not present it as an ordinary growth rate without an explicitly approved metric definition;
- prefer absolute change, sign transition, and source values.

## Missing/conflicting baseline
No percentage is produced from missing, NO_DATA, BLOCKED, or CONFLICTING baseline values.

## Product meaning
The UI must distinguish `percentage unavailable` from `0% change`.

## Implementation boundary
Calculation helpers, formatting rules, metric-specific exceptions, and tests are CODEX-FIRST/later work.