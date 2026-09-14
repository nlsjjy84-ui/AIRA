# Unit / Currency Comparison Boundary v1

## Core rule
Facts with different units, scales, currencies, or measurement bases are not directly comparable until an explicit approved conversion/normalization rule exists.

## Examples
- `KRW` and `KRW_BILLION` are different scales and must not be compared numerically without explicit scaling;
- a company filing in one currency must not be compared to another currency through an implicit FX assumption;
- share counts, trading volume, price, trading value, market cap, revenue, and GDP remain distinct measurement meanings even when all are numeric.

## Forbidden behavior
- do not silently divide/multiply values for display without provenance;
- do not apply an unstated FX rate;
- do not compare or rank entities using raw numbers from incompatible units;
- do not infer that a larger raw numeric value means greater economic significance across different predicates/units.

## Future conversion
Any conversion or derived normalized metric must preserve the original Fact, record the conversion rule/rate/date/source, and create a separately traceable derived value rather than overwriting source Facts.

## Implementation boundary
Conversion services, FX sources, scale formatting, derived Facts, UI comparison controls, and tests are CODEX-FIRST/later work.