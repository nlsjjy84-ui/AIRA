# Cross-Source Comparison Boundary v1

## Core rule
Different official providers may be compared only when the compared concepts, subjects, periods, units, and definitions are demonstrably compatible.

## Required compatibility
- same or explicitly mapped economic concept;
- compatible subject/entity scope;
- aligned period/date semantics;
- compatible unit/currency/scale;
- source-specific definitions do not materially change the meaning of the metric.

## Restraint
`Official source` does not mean two values are automatically interchangeable.
- do not resolve provider disagreement by choosing the newest collection time;
- do not average conflicting official values into one truth;
- do not hide definition differences behind normalization labels;
- preserve each source Evidence and expose the reason comparison is limited when compatibility is uncertain.

## Product meaning
Relate/Assess may show comparable observations side-by-side while keeping provider identity and provenance visible.

## Implementation boundary
Concept mapping registries, compatibility checks, cross-provider calculations, UI, and tests are CODEX-FIRST/later work.