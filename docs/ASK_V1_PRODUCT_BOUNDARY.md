# Ask v1 Product Boundary

## Role
`Ask / 질문` is the place where the user chooses **which perspective to use when looking at the selected context**. It is not the primary search screen and not a chatbot-style free-form answer box.

## Entry
- global/header search remains the place to find/select COMPANY, SECURITY, or another supported canonical target;
- Ask begins from an explicit selected context or an allowed product entry state;
- Ask may offer structured perspective choices, but those choices do not themselves constitute an Assessment or recommendation.

## Output meaning
- Ask produces/records the user's viewing perspective or analysis intent for the next flow;
- that perspective may shape what Inspect/Relate/Assess emphasizes, but it must not fabricate Facts, Events, Evidence, or causal claims;
- Ask does not auto-create Interest, Alert, or Assessment.

## Product restraint
- no duplicate search box as the core Ask interaction;
- no default chatbot helper framing;
- no hidden AI decision presented as the user's answer;
- no buy/sell recommendation or ranking behavior.

## Navigation
The natural next steps are `Inspect / 살피기`, `Relate / 잇기`, or an eligible `Assess / 판단` flow while preserving the selected canonical context and perspective.

## Implementation boundary
Perspective taxonomy, control design, state persistence, routing, APIs, components, and tests are CODEX-FIRST/later work.