# AIRA Open Product Decisions

> Interest-specific update (2026-09-14): the latest approved NEW Interest policy fixes common-stock close `< 1000 KRW`, daily rise `>= +20%`, and ETF/ETN/leverage/inverse/futures exclusions. The open questions below concern broader discovery/search presentation; they do not override this Interest rule. The KOSPI/KOSDAQ market eligibility HOLD was resolved by `KRX_CURRENT_INTEREST_ELIGIBILITY_PASS_NOTE_2026-09-14.md`.

## OPEN-01 — 급등 종목 기본 탐색/노출 제외 임계값
Settled principle:
- 상한가 또는 15~20% 이상 급등 종목은 기본 탐색/노출에서 제외한다.
- 사용자가 이미 Interest로 등록한 종목은 예외로 허용한다.

Not yet settled:
- numeric threshold is not yet fixed to exactly 15% or exactly 20%.

Rule until user decision:
- Codex/later implementation MUST NOT invent a numeric threshold.
- Do not silently choose 15%, 20%, or another percentage.
- Keep this policy OPEN until the user explicitly fixes the number.

## OPEN-02 — 동전주 제외 수치 기준
Settled principle:
- 동전주는 AIRA 기본 탐색/노출 대상에서 제외한다.

Not yet settled:
- exact price threshold defining `동전주` has not been fixed in the available prior decisions.

Rule until user decision:
- Codex/later implementation MUST NOT invent a price threshold.
- Do not silently choose 1,000원, 2,000원, or another value.
- Keep this policy OPEN until the user explicitly fixes the numeric definition.

## OPEN-03 — 제외 대상의 검색/Interest 지원 범위
Settled principle:
- 레버리지·인버스·선물·동전주는 기본 탐색/Home에서 앞세우지 않는다.
- ETF는 초기 범위에서 제외한다.
- 급등 종목은 기본 탐색/노출에서 제외하되 기존 Interest 예외 원칙을 유지한다.

Not yet settled:
- 레버리지·인버스·선물·동전주·초기 ETF를 명시적 검색 자체에서도 막을지;
- 해당 자산의 Interest 등록을 완전히 금지할지;
- 또는 기본 탐색에서만 제외하고 명시적 접근은 허용할지.

Rule until user decision:
- Codex/later implementation MUST NOT invent hard search/Interest rejection rules for these categories.
- Keep default discovery exclusion separate from explicit-access policy until the user fixes the scope.
