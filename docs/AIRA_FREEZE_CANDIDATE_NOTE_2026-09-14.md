# AIRA v1 freeze HEAD - 2026-09-14

## Git snapshot

- Branch: `main`
- Parent HEAD before freeze: `517624740e6d8e22403526b09e65e31f5cfdf6e1`. This note is included in the `chore: freeze AIRA v1 candidate` commit on `main`; the commit itself is identified by `git rev-parse HEAD` after checkout. A commit cannot embed its own hash.
- Before staging, the candidate had 32 modified tracked files and 286 intended untracked files. Classification found 1,482 untracked generated/temp files under `.tmp-` or `.tmp_` and zero uncertain files. All intended code, tests, docs, and V11-V17 migrations were selected; generated/temp files were excluded. No existing file was deleted or reset. Temporary artifacts may remain untracked after the commit and do not belong to the freeze HEAD.

## Migration integrity

- Exactly one migration version exists for each V1 through V17; the version sequence is contiguous. The SHA-256 manifest below identifies the files inspected.
- Tracked V1-V10 have no diff from the parent HEAD. V11-V17 were untracked before staging and are included in the freeze commit. No migration contents were edited in this freeze procedure.
- Existing targeted PostgreSQL PASS notes report successful disposable Flyway history through V17, most recently the 12A/12D and downstream gates. This freeze verified repository contents, **not a fresh database replay or test run**.

| Version | SHA-256 |
| --- | --- |
| V1 | `1FC584E6BA9C6399590CC70A98461B206512DE9150EA6AA54EADE6E6EB0D4924` |
| V2 | `CE551C1DF8725C3E09568E87BCE0E6B4492CED256583692C27772912A77ACE30` |
| V3 | `652B35928D89F02A1BB143D10F933377EB237ECF5ABC548C39AA8C01891D68A1` |
| V4 | `51D71A2470D0D15826102CB2D119BCBCE20F03B527FA4ABAAA55CBA086C8331C` |
| V5 | `3279885D822ADEA673D8DBC7EECFE9054957746481EE4F98B88D0C1DAD9764E5` |
| V6 | `2E0BB3752AB31DE614ADF572A2AC80111FA2735ADC681FA453E1F261C91341AE` |
| V7 | `E88581A44CC3B9DBF6602839AF3A7D03895779022A641355E6EA76D79F2AA982` |
| V8 | `9F61A9FBCE715D7A3A7366B8923624DAF0251F051A2E4D9FCFC89B35E9629B60` |
| V9 | `3A7F5E56C95E2E7A3C9343D2AAD48347B15795AF48A5FBD957C132858CA60C31` |
| V10 | `3E6E2A92CBB7567BE21C88EA3736259C7F2FE2FE50F37473CA55C0A62582A1CE` |
| V11 | `94E34BCAE6D500729E24BEF41FF539D312D8DBC5E226BA19C5F1B61139118147` |
| V12 | `775EFF6ECA7208A348B1049CAA5D82B344330778229BDC1FFC3E240050E6A944` |
| V13 | `D4197AA305427C93A6F61657EF5D3D409E6EA821C2B97253CE3E9E78FDB989D1` |
| V14 | `EA3C774872A2BDA7332B890069CB8F227006C30A80A2DD3AD7297E14A4831DFD` |
| V15 | `BA1A3AE8B7C06D96226F806290C5565C01249EE36622EB607D54506DD9734301` |
| V16 | `7BA2C040A7FC3952290F46CE7F5BCC9410E956AC28534F50588168F9B229A3C2` |
| V17 | `2E4D61BC4AC7C52166BCC62C29F0E369999F54070AFB3D8C94726A80E7D35E89` |

## Accepted evidence and remaining scope

- Existing PASS notes cover OpenDART exact receipt/FactPeriodEvidence, KRX canonical registration and KOSPI/KOSDAQ Current/NEW Interest eligibility, upstream provider separation, P6/P7 35 automatic endpoints, V17 delivery origin, Historical Exact/Assessment Current, downstream private integration, Auth/Interest, 12A API, 12B/12C/12D/12E Web/read wiring, and the WHY comment sweep. The targeted test counts and conditions remain in their individual notes; this audit did not rerun them.
- **ECOS HOLD (non-blocking for the accepted OpenDART/KRX path):** the actual prepared stat/item/cycle/time/unit/target mapping payload was not recovered. The earlier ECOS handoff describes unfinished ECOS-31/32 work, but it does not supply that payload or supersede the explicit later HOLD. No mapping was inferred here.
- **P7 HOLD (non-blocking):** `astInhtrfEtcPtbkOpt` remains excluded from automatic Event registration. The other 35 endpoints are the accepted automatic scope.
- Historical HOLD/BLOCK notes for KRX Current/Interest, 12C read wiring, and P7 downstream are superseded by their later PASS notes. `STALE` remains reserved until a freshness rule is approved; it is not an active inferred state.

## Freeze-candidate judgment

**Reproducible freeze HEAD:** the committed tree contains the intended candidate, including V11-V17 and accepted evidence notes. ECOS and the one P7 endpoint remain HOLD as described above. Generated/temp artifacts were deliberately left outside the commit. This is a source freeze, not a fresh full-regression or live-provider acceptance claim.
