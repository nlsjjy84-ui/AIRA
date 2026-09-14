# Public API Data Boundary v1

## Core rule
AIRA public API exposes only explicitly approved shared/public market-information resources. It never exposes private user/account state.

## Never public
- user Interest selections;
- private Briefing/Alert delivery state;
- user settings/profile data;
- session identifiers/tokens/reset credentials;
- private account/authentication metadata;
- internal secrets, provider API keys, DB/internal filesystem details.

## Shared/public candidates
Public Facts, Events, Evidence metadata, Assessments, and canonical Entity information may be exposed only when their existing product/source contracts permit it and without weakening provenance semantics.

## Trust restraint
API payment tier or quota never changes factual values, provenance, Source authority, conflict status, or Assessment meaning. Access control/metering and truth semantics stay separate.

## Redistribution restraint
Do not assume that provider content may be redistributed merely because AIRA can ingest it. Field/content exposure must respect the relevant source/API terms under a separately verified publication contract.

## Implementation boundary
API schemas, auth, plans, quotas, billing, field filtering, source-license enforcement, and tests are CODEX-FIRST/later work.