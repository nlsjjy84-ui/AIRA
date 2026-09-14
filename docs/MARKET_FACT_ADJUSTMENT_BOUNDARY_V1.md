# Market Fact Adjustment Boundary v1

## Core rule
KRX Market Fact v1 stores the official observed daily values from the approved endpoint contract. It does not silently transform them into adjusted-price or derived-series values.

## Source Fact meaning
- OPEN/HIGH/LOW/CLOSE_PRICE, TRADING_VOLUME, TRADING_VALUE, MARKET_CAP, and LISTED_SHARES remain source observations for the requested trading date;
- corporate actions, splits, capital increases, market transfers, or later metadata changes do not rewrite historical source Facts;
- historical Facts retain their original Evidence and assertion provenance.

## Forbidden behavior
- no silent split-adjusted price replacement;
- no back-adjustment of volume or shares unless an explicit derived-series contract exists;
- no return, momentum, volatility, technical indicator, or recommendation metric is created inside provider ingestion;
- no historical Fact overwrite merely because a later corporate action changes comparability.

## Future derived series
Any adjusted/derived market series must be separately named, reproducible from explicit inputs/rules, and traceable without replacing the original KRX Facts.

## Implementation boundary
Corporate-action adjustment engines, return series, chart normalization, derived predicates, and related tests are CODEX-FIRST/later work.