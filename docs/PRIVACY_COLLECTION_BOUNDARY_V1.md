# Privacy Collection Boundary v1

## Core rule
AIRA does not collect Wi-Fi/Bluetooth device or network identifiers, nor user access-history data, as product personalization/analysis inputs.

## Forbidden collection/use
- no Wi-Fi SSID/BSSID or nearby-network fingerprinting;
- no Bluetooth device identifiers/beacon history;
- no location inference from those signals;
- no access-history profile used for recommendation, ranking, advertising, or behavioral targeting;
- no commercial/ad profile built from AIRA usage.

## Product meaning
AIRA personalization is based on explicit user choices such as Interest/settings and the user's selected context, not passive device/network surveillance.

## Security restraint
Necessary session/authentication controls remain governed by the accepted auth/security contract, but must not be repurposed into behavioral profiling or exposed as recommendation signals.

## Implementation boundary
Operational logging, retention, security telemetry, privacy notices, export/deletion flows, and tests are CODEX-FIRST/later work.