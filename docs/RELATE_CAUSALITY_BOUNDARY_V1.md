# Relate Causality Boundary v1

## Core rule
`Relate` shows evidence-grounded relationships and shared context. It does not manufacture causal claims.

## Allowed relationship meaning
- explicit canonical Entity links may be shown as identity/structural relationships;
- shared CONFIRMED Event participation may be shown as co-occurrence/context;
- shared Evidence or temporally adjacent observations may be shown only as association/context when useful;
- provider-supplied explicit relationship semantics may be preserved when their Evidence is available.

## Forbidden inference
- temporal proximity alone does not prove causation;
- price movement after a disclosure does not mean the disclosure caused the movement;
- correlation between Facts does not become cause/effect automatically;
- shared company/security/event membership does not authorize directional causal language;
- AI confidence or heuristic scoring may not silently upgrade association into causation.

## User-facing wording
Use neutral forms such as `연결됨`, `함께 나타남`, `같은 사건에 관련됨`, or `근거상 관계가 확인됨` according to evidence precision. Use causal wording only when the supporting evidence itself establishes that semantic relationship.

## Implementation boundary
This document fixes product meaning only. Relation queries, graph traversal, scoring, UI visualization, and tests are CODEX-FIRST/later implementation work.