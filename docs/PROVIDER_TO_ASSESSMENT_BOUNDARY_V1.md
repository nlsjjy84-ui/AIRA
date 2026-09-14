# Provider -> Assessment Boundary v1

## Purpose
Prevent provider adapters from bypassing AIRA's accepted trust chain or inventing downstream product behavior.

## Fixed boundary
- Provider ingestion may create/reuse only the Source, Evidence, Fact/Event, and provider-specific provenance required by its closed contract.
- KRX Market Facts remain Facts only. KRX ingestion does not create Event, Assessment, Interest, Briefing, or Alert rows.
- ECOS REAL_GDP remains a statistical Fact only. ECOS ingestion does not create Event, Assessment, Interest, Briefing, or Alert rows.
- OpenDART Material Event ingestion may create a CONFIRMED Event only after canonical COMPANY + supporting Evidence exist. It does not automatically create an Assessment.
- Existing accepted OpenDART EARNINGS behavior remains unchanged; this contract does not redesign the accepted EARNINGS path.

## Assessment gate
- Assessment is a separate downstream responsibility.
- A COMPLETED Assessment still requires a CONFIRMED Event and Assessment Evidence under `AIRA 기준 명세 v1.0`.
- No provider adapter may mark data as an Assessment merely because the provider is official or the value/event is material.
- Current vs Historical Exact Assessment semantics remain separate and are not inferred inside provider ingestion.

## Downstream restraint
- Briefing and Alert must not consume raw provider responses directly.
- Interest, Briefing and Alert remain downstream of the accepted trust chain and their existing contracts.
- No scheduler, recommendation logic, scoring, sentiment, UI behavior, or automatic user-facing judgment is added in this packet.

## Codex consequence
Provider Packets P1-P7 stop at their closed provider outputs. Any new provider-to-Assessment automation requires a separate product decision; Codex must BLOCK rather than invent it.
