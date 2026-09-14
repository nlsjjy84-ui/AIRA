# Derived Value Provenance Boundary v1

## Core rule
A calculated/normalized/converted value is a derived claim, not a replacement for the original provider Fact.

## Required meaning for any future derived value
- preserve every original input Fact/Evidence unchanged;
- record the derivation formula/rule and its version;
- record all required parameters such as FX rate, scale, adjustment factor, comparison period, or benchmark;
- preserve the applicable as-of/period semantics of both inputs and output;
- make the result reproducible from explicit inputs.

## Forbidden behavior
- do not overwrite source Facts with calculated values;
- do not hide unit/currency conversion inside presentation code and then treat the result as provider data;
- do not present a derived metric as if the provider directly published it;
- do not use an opaque AI-generated number without traceable inputs/rules as an AIRA Fact.

## Product meaning
Original facts and derived values must remain distinguishable in Inspect/Relate/Assess. Derivation provenance is part of the explanation, not an implementation detail.

## Implementation boundary
Derived Fact schema, formula registry, calculation engine, FX integration, UI, and tests are CODEX-FIRST/later work.