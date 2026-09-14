# Numeric Precision Presentation Boundary v1

## Core rule
Displayed rounding must never change the stored Fact meaning or create false precision.

## Presentation
- preserve the provider/derived raw numeric value in data/provenance;
- UI may round for readability only when the rounded nature is visually clear from context;
- use a consistent scale/unit label with every abbreviated number;
- percentages and ratios must not show more precision than their inputs justify.

## Forbidden behavior
- do not overwrite stored values with rounded display values;
- do not infer trailing zero precision that the provider did not supply;
- do not compare values after incompatible rounding and call the difference exact;
- do not hide material small differences through aggressive abbreviation when comparison depends on them.

## Product meaning
Inspect must be able to reveal the underlying exact represented value and Evidence path behind a rounded summary.

## Implementation boundary
Formatting utilities, locale handling, significant-digit rules, UI, and tests are CODEX-FIRST/later work.