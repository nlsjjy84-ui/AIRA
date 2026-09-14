# Assessment Uncertainty Presentation Boundary v1

## Core rule
Assessment는 근거의 한계·충돌·누락을 숨기지 않는다. 불확실성을 하나의 확정 점수로 위장하지 않는다.

## Meaning
- `COMPLETED`는 절대적 진실/투자정답을 의미하지 않는다.
- Evidence gap, CONFLICTING Fact, date/period ambiguity, missing relation은 사용자에게 구분 가능해야 한다.
- 근거가 충분하지 않으면 `근거 부족/확인 필요`로 남기며 임의 결론으로 메우지 않는다.
- 여러 근거가 서로 다르면 한쪽을 조용히 선택하지 않고 충돌 자체를 보여준다.

## Restraint
- 근거 없는 0~100 신뢰도/매수확률/성공확률을 만들지 않는다.
- AI confidence를 사실 확률이나 미래 수익 확률처럼 표현하지 않는다.
- 불확실성 표시가 사용자 행동을 유도하는 공포/과장 문구가 되어서는 안 된다.

## Product meaning
Assessment의 목적은 사용자를 대신해 결론을 내리는 것이 아니라, 현재 근거로 무엇을 말할 수 있고 무엇은 아직 말할 수 없는지 구분해 보여주는 것이다.

## Implementation boundary
Confidence model, badges, scoring UI, copy, thresholds and tests are CODEX-FIRST/later work.