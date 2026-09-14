# Search Ranking Neutrality Boundary v1

## Core rule
Search ordering helps the user locate an intended entity; it must not function as an investment recommendation ranking.

## Allowed relevance factors
- exact/strong identifier match;
- exact/strong official name match;
- entity-type disambiguation;
- market/code context needed to identify the intended COMPANY or SECURITY.

## Forbidden ranking meaning
- no expected return, momentum, popularity, sponsored status, commercial relationship, or opaque AI preference may boost search position as a hidden recommendation;
- Interest status may be shown as user context but must not silently redefine factual identity matching;
- search rank must not be described as quality, safety, attractiveness, or investment merit.

## Product meaning
When multiple entities match, expose enough identity context for the user to choose rather than automatically selecting a presumed best result.

## Implementation boundary
Search index, scoring algorithm, typo tolerance, performance tuning, UI, and tests are CODEX-FIRST/later work.