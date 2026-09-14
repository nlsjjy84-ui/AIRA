# Relate v1 Product Boundary

## Role
`Relate / 잇기` shows verified relationships and shared context around an explicitly selected canonical entity, Fact, Event, or Assessment. It is not a free-form graph generator.

## Must expose
- the selected starting subject;
- each related canonical entity/object as a distinct identity;
- relationship meaning/type when officially known;
- the Evidence/provenance supporting that relationship or shared context;
- time/period precision where the relationship is time-bound;
- unresolved or unavailable relationship state rather than inferred links.

## Product behavior
- follow `RELATE_CAUSALITY_BOUNDARY_V1.md`: association/context is not automatically causation;
- COMPANY and SECURITY may be linked without being merged;
- relationship presence never auto-propagates Interest, Alert, or Assessment eligibility;
- visual proximity, graph position, or connection count is not a recommendation/ranking signal.

## Navigation
Relate may return to `Inspect` for evidence detail or continue to `Assess` only when a qualifying Assessment already exists or an explicitly approved Assessment flow is invoked.

## Implementation boundary
Graph queries, traversal depth, layout, clustering, scoring, components, performance optimizations, and tests are CODEX-FIRST/later work.