# Generated Text Grounding Boundary v1

## Core rule
AI-generated or templated explanatory text may summarize supported AIRA state, but it cannot create new Fact, Event, Evidence, identity, period, causality, or certainty.

## Required grounding
- factual statements must trace to existing supported Fact/Event/Assessment and Evidence;
- uncertainty/conflict/missing-data state must survive summarization;
- exact dates, values, units, entities, and periods must come from grounded data;
- generated explanations must preserve the difference between observed facts, derived interpretation, and unresolved uncertainty.

## Forbidden behavior
- no invented numbers, dates, motives, event causes, or company/security links;
- no filling missing provider fields from general knowledge or model guess;
- no converting correlation or temporal proximity into causality;
- no wording that upgrades CANDIDATE/CONFLICTING/NO_DATA into confirmed truth;
- no generated text becoming Evidence for its own claim.

## Product meaning
Generated text is a presentation layer over the trust chain, never a new authority inside the trust chain.

## Implementation boundary
Prompting, retrieval, citations, hallucination guards, model choice, evaluation, and tests are CODEX-FIRST/later work.