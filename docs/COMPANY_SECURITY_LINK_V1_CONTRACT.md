# COMPANY <-> SECURITY Link v1 Contract

## Purpose
Connect official OpenDART COMPANY identity to official KRX SECURITY identity without name matching or provider-specific identity collapse.

## Preconditions
- COMPANY is already canonical and has explicit `OPENDART/CORP_CODE` identity.
- SECURITY is already canonical and has explicit `KRX/STANDARD_CODE` identity.
- Neither ingestion path creates this relation implicitly; linking is a separate responsibility after both identities exist.

## Official linking evidence
- OpenDART official company-directory metadata may provide the company's official stock code.
- KRX official base-info Evidence for a specific `basDd` provides the dated short-code -> standard-code mapping.
- A link candidate is valid only when the official OpenDART stock code exactly matches one KRX base-info short code and that row resolves to exactly one canonical SECURITY standard code.
- Company/security names are never matching keys.

## BLOCK conditions
- missing OpenDART stock code;
- no matching KRX base-info row;
- more than one candidate SECURITY;
- incompatible Entity type or external-identifier mapping;
- conflicting existing COMPANY<->SECURITY relation;
- any attempt to repair identity by company/security name, abbreviation, fuzzy match, or latest-price data.

## Time restraint
- The v1 relation means an officially evidenced COMPANY<->SECURITY association, not a complete historical listing-membership timeline.
- Do not backdate the relation beyond the official evidence actually used.
- Market migration, ticker change, delisting/relisting, multiple share classes, or one COMPANY mapping to multiple securities require explicit later rules; they are not guessed in v1.

## Codex boundary
Do not implement this relation inside P1-P8 unless a later explicit Packet is opened for it. P1-P8 provider ingestion remains valid without COMPANY<->SECURITY linking.
