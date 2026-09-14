# Comparison Period Alignment Boundary v1

## Core rule
AIRA는 기간 의미가 다른 값을 같은 조건의 직접 비교처럼 보여주지 않는다.

## Required comparison meaning
- annual vs annual, quarter vs quarter 등 비교 대상의 기간 종류를 명시한다.
- fiscal-year end가 다른 회사는 동일 캘린더 연도라는 이유만으로 같은 기간이라고 가정하지 않는다.
- Historical Exact 비교는 각 Fact의 실제 period_start/period_end를 기준으로 한다.
- short/transition fiscal period는 정상 12개월 기간과 동일 길이 비교처럼 숨기지 않는다.
- Current와 Historical Exact를 한 비교축에서 섞을 때는 각각의 시점/기간 차이를 명확히 표시한다.

## Restraint
- `YYYY` 라벨 하나만 보고 기간을 자동 정렬하지 않는다.
- 기간이 불일치하면 계산 가능한 척 비율/증감률을 자동 생성하지 않는다.
- 사용자가 명시적으로 다른 기간을 비교하더라도 차이를 숨기지 않는다.

## Product meaning
비교의 목적은 숫자를 가까이 배치하는 것이 아니라, 같은 의미 조건인지 사용자가 먼저 확인할 수 있게 하는 것이다.

## Implementation boundary
Period-matching algorithm, comparison query, derived growth calculations, UI and tests are CODEX-FIRST/later work.