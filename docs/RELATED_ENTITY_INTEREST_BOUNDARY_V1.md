# Related Entity Interest Boundary v1

## Rule
- User Interest is explicit and remains attached to the canonical Entity the user actually selected.
- A COMPANY<->SECURITY relation does not automatically create, copy, enable, or migrate Interest to the related Entity.
- Following a COMPANY does not silently follow every linked SECURITY.
- Following a SECURITY does not silently create COMPANY Interest.

## Product behavior
- AIRA may show that a related COMPANY or SECURITY exists and let the user explicitly choose it.
- Briefing/Alert eligibility must continue to use the user's actual enabled Interest records and existing accepted contracts.
- Related-entity context may be displayed as evidence/context, but it does not expand the user's subscription scope by itself.

## Restraint
- No automatic interest propagation, recommendation, inferred preference, or bulk-follow behavior is introduced.
- Any future grouped-interest feature requires an explicit product decision and user-visible opt-in.

## Codex boundary
This is a semantic rule only. Do not alter the already-accepted Interest/Briefing/Alert implementation in P1-P8 unless an actual regression conflicts with this rule.
