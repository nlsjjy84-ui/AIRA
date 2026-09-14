# OpenDART Material Event Registration Contract

## Event identity
For one validated row from an approved material-event endpoint, compute a deterministic provider-aware Event dedup key from:
- fixed prefix/version,
- provider `OPENDART`,
- approved endpoint contract key,
- canonical COMPANY identity,
- provider-native 14-digit `rcept_no`.

Do not use title text, company name, collection time, or provider response order in Event identity.
A correction/new filing with a new `rcept_no` remains a distinct Event unless explicit official lineage is separately proven.
Cross-endpoint rule: the same provider `rcept_no` appearing under more than one approved endpoint key is a classification conflict and BLOCKS that receipt. If the same real-world incident appears through different endpoint contracts with different `rcept_no` values, preserve separate Events rather than guessing lineage or a merge.

## Generic Event domain path
Current `Event.createEarnings` and `EventRegistrationStore` are EARNINGS-specific. Codex must add an additive generic/material creation and registration path that:
- accepts the already-approved `EventType` mapping,
- preserves the existing EARNINGS behavior unchanged,
- registers by dedup key under the existing unique constraint,
- links canonical COMPANY as `SUBJECT`,
- links endpoint-specific structured OFFICIAL_DATA Evidence as `SUPPORTS`,
- confirms only after canonical Entity + Evidence links exist.
## Structured Evidence for material endpoints
- Follow `OPENDART_MATERIAL_EVENT_EVIDENCE_CONTRACT.md`.
- One validated endpoint row uses `EvidenceType.OFFICIAL_DATA` with AIRA external identity `OPENDART_MATERIAL:{endpointKey}:{rcept_no}`.
- Do not place structured row content/hash into the raw filing `DISCLOSURE / external_id=rcept_no` Evidence slot.
- Split registration per receipt; do not use a whole multi-filing HTTP response as one Evidence.
- Same structured Evidence identity/revision with identical canonical content/provenance reuses Evidence; different content/provenance BLOCKS.

## Time precision
OpenDART material-event date fields in the approved catalog are date-only unless the official contract explicitly provides a timestamp.
- Validate and preserve the official decision/occurrence date in canonical Evidence content.
- Do NOT convert a date-only provider field to an invented midnight/noon timestamp.
- With the current Event schema, leave `occurred_at` null for date-only source precision.
- `first_observed_at` / `last_observed_at` track when AIRA observed the Event and must not pretend to be the provider occurrence date.
- A future date-precision Event model may promote the date explicitly; that schema expansion is outside this v1 packet.
## Deterministic Event title / dedup serialization
- Event title is the neutral catalog label only (for example `유상증자 결정`, `회사합병 결정`, `부도발생`).
- Do not embed company name, amount, counterparty, direction, sentiment, or collection time in the title.
- Title is display metadata and never participates in identity.
- Canonical dedup string: `AIRA|EVENT|V1|OPENDART_MATERIAL|{endpointKey}|{companyCanonicalKey}|{rcept_no}`.
- Encode the canonical string as UTF-8 and hash with SHA-256 to produce the existing `dedup_key` bytes.
- Do not normalize or lowercase provider `rcept_no`; it is already validated as exactly 14 digits.
- Re-registration with the same dedup key reuses the Event and may advance only observation metadata allowed by the existing Event lifecycle; it must not rewrite prior Evidence or infer lineage.
