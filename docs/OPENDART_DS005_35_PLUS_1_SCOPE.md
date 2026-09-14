# OpenDART DS005 P7 scope

The approved P7 scope is the 35 automatic endpoint entries in `OpenDartDs005Catalog` plus one HOLD endpoint, `astInhtrfEtcPtbkOpt`. The seven waves contain 4, 4, 4, 7, 8, 4, and 4 automatic endpoints. The catalog fixes each endpoint's EventType and official neutral Korean title. The historical 24-endpoint catalog and field matrix do not constrain this scope.

The OpenDART [DS005 developer guide](https://opendart.fss.or.kr/guide/main.do?apiGrpCd=DS005) lists all 36 endpoints. P7 uses only the common provider receipt/company identity and full structured rows; this note makes no new claim about endpoint-specific fields or occurrence-date semantics. HOLD is never eligible for automatic Event registration.

Every successful validated response is grouped by exact `rcept_no`. Distinct rows of a receipt form one deterministic whole-receipt Evidence hash, independent of response order or request window. The P6 service owns transactional Event registration. `occurredAt` and `publishedAt` remain null without an approved official timestamp.
