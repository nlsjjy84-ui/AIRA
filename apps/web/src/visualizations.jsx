import { useState } from 'react'

const KOREAN = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 })

export function formatQuantity(value, unit = '') {
  if (value === null || value === undefined || value === '') return '자료 없음'
  const number = Number(value)
  if (!Number.isFinite(number)) return '자료 없음'
  if (unit === 'KRW') {
    const magnitude = Math.abs(number)
    if (magnitude >= 1e12) return `${KOREAN.format(number / 1e12)}조 원`
    if (magnitude >= 1e8) return `${KOREAN.format(number / 1e8)}억 원`
    return `${KOREAN.format(number)}원`
  }
  if (unit === '%') return `${KOREAN.format(number)}%`
  if (unit === '%p') return `${KOREAN.format(number)}%p`
  return `${KOREAN.format(number)}${unit ? ` ${unit}` : ''}`
}

const FACT_LABEL = { REVENUE: '매출', OPERATING_INCOME: '영업이익', CLOSE_PRICE: '종가',
  OPEN_PRICE: '시가', HIGH_PRICE: '고가', LOW_PRICE: '저가', TRADING_VOLUME: '거래량',
  MARKET_CAP: '시가총액' }
const RELATION_LABEL = { FACT: '사실', EVENT: '사건', ASSESSMENT: '판단' }
const SOURCE_TYPE_LABEL = { EXCHANGE: '거래소', REGULATOR: '감독기관', FILING: '공시', OFFICIAL: '공식 기관' }
const PERCENT_REASON_LABEL = { BASE_NON_POSITIVE: 'A 값이 0 이하라 일반 증감률을 계산할 수 없습니다.' }

export function FinancialOverview({ observation, onEvidence }) {
  const [expanded, setExpanded] = useState(null)
  const facts = observation?.value?.facts ?? []
  if (!facts.length) return null
  return <section className="viz-block viz-overview" aria-label="재무 한눈에 보기">
    <div className="viz-heading"><div><p className="eyebrow">EXACT FINANCIALS</p><h3>재무 핵심값</h3></div>
      <p>{observation.periodStart} — {observation.periodEnd}<br />공시 {observation.receipt}</p></div>
    <div className="viz-metrics" role="list">{facts.map(fact => {
      const key = `${fact.predicate}:${fact.evidenceId}`
      const open = expanded === key
      return <div className={`viz-metric ${open ? 'expanded' : ''}`} role="listitem" key={key}>
        <div className="viz-metric-line"><div><span>{FACT_LABEL[fact.predicate] ?? fact.predicate}</span>
          <strong>{formatQuantity(fact.value, fact.currency)}</strong></div>
          <button type="button" aria-expanded={open} onClick={() => setExpanded(open ? null : key)}>{open ? '상세 닫기' : '상세 보기'}</button></div>
        {open && <div className="viz-metric-detail"><dl><div><dt>정확한 값</dt><dd>{fact.value} {fact.currency}</dd></div>
          <div><dt>기간</dt><dd>{fact.periodStart} — {fact.periodEnd}</dd></div>
          <div><dt>근거 식별자</dt><dd>{fact.evidenceId}</dd></div>
          <div><dt>출처</dt><dd>{fact.sourceName}</dd></div></dl>
          {fact.evidenceId && <button type="button" onClick={() => onEvidence(fact.evidenceId,
            { type: 'FACT', label: `${fact.predicate} · ${fact.periodStart} — ${fact.periodEnd}` })}>근거 확인</button>}</div>}
      </div>
    })}</div>
  </section>
}

export function FinancialSplit({ comparison, onEvidence }) {
  if (comparison?.state !== 'AVAILABLE' || !comparison.metrics?.length) return null
  return <section className="viz-block comparison-result" aria-label="A와 B 분할 비교">
    <div className="comparison-result-heading"><h3>비교 · A ↔ B</h3>
      <p>백엔드가 검증한 정확한 A/B 기간과 공시만 비교합니다. 증감은 B − A입니다.</p></div>
    {comparison.metrics.map(metric => <div className="viz-compare-row" key={metric.predicate}>
      <h4>{FACT_LABEL[metric.predicate] ?? metric.predicate}</h4>
      <div className="viz-split">{[['A', comparison.a, metric.a], ['B', comparison.b, metric.b]].map(([side, period, observation]) =>
        <article className={`viz-side viz-side-${side.toLowerCase()}`} key={side}><small>{side} · {period.periodStart} — {period.periodEnd} · 공시 {period.receipt}</small>
          <strong>{formatQuantity(observation.value, observation.currency)}</strong>
          {observation.evidenceIds.map(id => <button key={id} type="button" onClick={() => onEvidence(id,
            { type: 'FACT', label: `${metric.predicate} · ${side} ${period.periodStart} — ${period.periodEnd} · ${period.receipt}` })}>
            {side} 근거 식별자 · {id}</button>)}</article>)}</div>
      <p className="viz-change-summary"><span>증감액 {formatQuantity(metric.changeAmountBMinusA, metric.a.currency)}</span><span>증감률 {metric.changePercentBOverA == null
        ? `계산 불가 · ${PERCENT_REASON_LABEL[metric.percentReason] ?? '계산 조건을 충족하지 않습니다.'}` : formatQuantity(metric.changePercentBOverA, '%')}</span></p>
      {/* Meter width is only a visual projection of server values; the change numbers above come from the API. */}
      {Number(metric.a.value) >= 0 && Number(metric.b.value) >= 0 && <div className="viz-bars" aria-label={`${metric.predicate} A와 B 값 막대`}>
        {[metric.a, metric.b].map((observation, index) => <div key={index}><span>{index ? 'B' : 'A'}</span>
          <meter aria-label={`${index ? 'B' : 'A'} ${metric.predicate} ${observation.value} ${observation.currency}`}
            min="0" max={Math.max(Number(metric.a.value), Number(metric.b.value), 1)} value={Number(observation.value)} />
          <span>{formatQuantity(observation.value, observation.currency)}</span></div>)}</div>}
    </div>)}</section>
}

export function MarketOverview({ observation, predicate }) {
  if (!observation?.value) return null
  const value = observation.value
  return <section className="viz-block" aria-label="시장 한눈에 보기"><h3>한눈에 · {FACT_LABEL[predicate] ?? predicate}</h3>
    <strong className="viz-big-number">{formatQuantity(value.value, predicate === 'TRADING_VOLUME' ? '주' : 'KRW')}</strong>
    <p>KRX 공식 거래일 {value.tradingDate} · 관측 식별자 · {value.factId}</p>
    <p>이 응답은 D의 단일 관측값입니다. 흐름과 직전 관측 비교는 아래에서 따로 엽니다.</p></section>
}

export function MarketSeriesView({ series, onEvidence }) {
  const [selectedId, setSelectedId] = useState(null)
  if (series?.state !== 'AVAILABLE' || !series.points?.length) return null
  const selected = series.points.find(point => point.factId === selectedId)
  const unit = series.predicate === 'TRADING_VOLUME' ? '주' : 'KRW'
  const numeric = series.points.map(point => Number(point.value))
  const chartable = numeric.every(Number.isFinite)
  const chartMin = chartable ? Math.min(...numeric) : 0
  const chartMax = chartable ? Math.max(...numeric) : 0
  const width = 720, height = 220, padX = 38, padY = 28
  const xAt = index => series.points.length === 1 ? width / 2 : padX + index * (width - padX * 2) / (series.points.length - 1)
  const yAt = value => chartMax === chartMin ? height / 2 : padY + (chartMax - value) * (height - padY * 2) / (chartMax - chartMin)
  const polyline = chartable ? series.points.map((point, index) => `${xAt(index)},${yAt(Number(point.value))}`).join(' ') : ''
  return <section className="viz-block viz-process" aria-label="공식 시장 관측 흐름">
    <div className="viz-process-heading"><div><p className="eyebrow">OFFICIAL SERIES</p><h3>공식 관측 흐름</h3></div>
      <p>{series.from} — {series.to} · {FACT_LABEL[series.predicate] ?? series.predicate}. 빈 날짜는 채우거나 추정하지 않습니다.</p></div>
    {chartable && <div className="viz-series-chart-wrap">
      <div className="viz-series-scale" aria-hidden="true"><span>{formatQuantity(chartMax, unit)}</span><span>{formatQuantity(chartMin, unit)}</span></div>
      <svg className="viz-series-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`${FACT_LABEL[series.predicate] ?? series.predicate} 공식 관측 흐름`}>
        <line x1={padX} x2={width - padX} y1={padY} y2={padY} className="viz-grid-line" />
        <line x1={padX} x2={width - padX} y1={height - padY} y2={height - padY} className="viz-grid-line" />
        <polyline points={polyline} className="viz-series-line" />
        {series.points.map((point, index) => <g key={point.factId} className={selectedId === point.factId ? 'selected' : ''}>
          <circle cx={xAt(index)} cy={yAt(Number(point.value))} r={selectedId === point.factId ? 6 : 4} className="viz-series-dot" />
        </g>)}
      </svg>
      <div className="viz-series-axis" aria-hidden="true"><span>{series.points[0].tradingDate}</span><span>{series.points.at(-1).tradingDate}</span></div>
    </div>}
    <ol className="viz-series-list">{series.points.map(point => <li key={point.factId}>
      <button type="button" aria-pressed={selectedId === point.factId} onClick={() => setSelectedId(point.factId)}>
        <time>{point.tradingDate}</time><strong>{formatQuantity(point.value, unit)}</strong>
      </button></li>)}</ol>
    {selected && <div className="viz-selection"><h4>선택한 정확한 관측</h4><p>{selected.tradingDate} · 값 {selected.value} · 관측 식별자 · {selected.factId}</p>
      <p>공식 근거 · {selected.evidenceExternalId}</p>
      {selected.evidenceIds.map(id => <button key={id} type="button" onClick={() => onEvidence(id,
        { type: 'FACT', label: `${series.predicate} · ${selected.tradingDate} · ${selected.factId}` })}>근거 식별자 · {id}</button>)}</div>}
  </section>
}

export function MarketPreviousView({ comparison, onEvidence }) {
  if (comparison?.state !== 'AVAILABLE' || !comparison.current || !comparison.previous) return null
  const unit = comparison.predicate === 'TRADING_VOLUME' ? '주' : 'KRW'
  const values = [Number(comparison.previous.value), Number(comparison.current.value)]
  return <section className="viz-block comparison-result market-previous" aria-label="D와 직전 공식 관측 비교"><h3>비교 · D와 직전 실제 관측일</h3>
    <div className="viz-split">{[['직전 관측', comparison.previous], ['D', comparison.current]].map(([label, point]) =>
      <article className={`viz-side ${label === 'D' ? 'viz-side-b' : 'viz-side-a'}`} key={label}><small>{label} · {point.tradingDate} · 관측 식별자 · {point.factId}</small><strong>{formatQuantity(point.value, unit)}</strong>
        {point.evidenceIds.map(id => <button key={id} type="button" onClick={() => onEvidence(id,
          { type: 'FACT', label: `${comparison.predicate} · ${point.tradingDate} · ${point.factId}` })}>
          근거 식별자 · {id}</button>)}</article>)}</div>
    <p className="viz-change-summary"><span>증감액 {formatQuantity(comparison.changeAmount, unit)}</span>
      <span>증감률 {comparison.changePercent == null ? `계산 불가 (${comparison.percentReason})` : formatQuantity(comparison.changePercent, '%')}</span></p>
    {values.every(value => Number.isFinite(value) && value >= 0) && <div className="viz-bars" aria-label="직전 관측과 D 값 막대">{[['직전', comparison.previous], ['D', comparison.current]].map(([label, point]) =>
      <div key={label}><span>{label}</span><meter aria-label={`${label} ${comparison.predicate} ${point.value} ${unit}`} min="0" max={Math.max(...values, 1)} value={Number(point.value)} /><span>{formatQuantity(point.value, unit)}</span></div>)}</div>}
  </section>
}

export function OhlcCandle({ observations }) {
  const names = ['OPEN_PRICE', 'HIGH_PRICE', 'LOW_PRICE', 'CLOSE_PRICE']
  if (!names.every(name => observations?.[name]?.state === 'AVAILABLE' && observations[name].value)) return null
  const dates = new Set(names.map(name => observations[name].value.tradingDate))
  if (dates.size !== 1) return null
  const [open, high, low, close] = names.map(name => Number(observations[name].value.value))
  if (![open, high, low, close].every(Number.isFinite) || low > Math.min(open, close) || high < Math.max(open, close) || high <= low) return null
  const y = value => 10 + (high - value) / (high - low) * 120
  return <figure className="viz-block viz-ohlc"><figcaption>상세 · 공식 거래일 {observations.OPEN_PRICE.value.tradingDate} OHLC</figcaption>
    <div className="viz-ohlc-body"><div className="viz-ohlc-graphic" aria-hidden="true"><span>{formatQuantity(high, 'KRW')}</span>
      <svg className="viz-ohlc-chart" viewBox="0 0 120 150">
        <line x1="60" x2="60" y1={y(high)} y2={y(low)} stroke="currentColor" strokeWidth="2" />
        <rect x="43" y={Math.min(y(open), y(close))} width="34" height={Math.max(2, Math.abs(y(open) - y(close)))} fill="none" stroke="currentColor" strokeWidth="2" />
      </svg><span>{formatQuantity(low, 'KRW')}</span></div>
      <dl className="viz-ohlc-values">{names.map(name => <div key={name}><dt>{FACT_LABEL[name]}</dt><dd>{formatQuantity(observations[name].value.value, 'KRW')}<small>관측 식별자 · {observations[name].value.factId}</small></dd></div>)}</dl>
    </div>
    <span className="sr-only" role="img" aria-label={`시가 ${open}, 고가 ${high}, 저가 ${low}, 종가 ${close}`}></span>
  </figure>
}

export function EventTimeline({ events, onSelect }) {
  if (!events?.length) return null
  // Only provider-approved occurredAt participates in chronological order; unknown time remains separate.
  const dated = events.filter(item => item.occurredAt).toSorted((a, b) => a.occurredAt.localeCompare(b.occurredAt))
  const unknown = events.filter(item => !item.occurredAt)
  return <section className="viz-block viz-process viz-event-flow" aria-label="사건 시간순">
    <div className="viz-process-heading"><div><p className="eyebrow">EVENT FLOW · 사건 흐름</p><h3>확인된 사건의 시간 흐름</h3></div>
      <p>공식 발생시각이 확인된 사건만 시간순으로 놓고, 시각 미상은 따로 남깁니다.</p></div>
    <ol className="viz-timeline">{[...dated, ...unknown].map(item => <li key={item.eventId}>
      <span className="viz-node-label">사건</span><time>{item.occurredAt ? item.occurredAt.slice(0, 10) : '발생시각 미상'}</time>
      <button type="button" onClick={() => onSelect(item)}>{item.title}</button><small>사건 식별자 · {item.eventId}</small>
    </li>)}</ol></section>
}

export function AssessmentFlow({ assessment, onHistorical, onEvidence }) {
  if (!assessment?.assessmentId) return null
  return <section className="viz-block viz-process viz-assessment-flow" aria-label="판단 승계 흐름">
    <div className="viz-process-heading"><div><p className="eyebrow">ASSESSMENT FLOW · 판단 연결</p><h3>현재 판단과 이전 판단의 연결</h3></div>
      <p>현재 판단을 단순한 최신 시각으로 추정하지 않고 저장된 승계 관계만 표시합니다.</p></div>
    <div className="viz-flow">{assessment.supersedesAssessmentId && <><button type="button" className="viz-flow-node previous"
      onClick={() => onHistorical(assessment.supersedesAssessmentId)}><small>이전 당시 판단</small><span>{assessment.supersedesAssessmentId}</span><em className="viz-contract-term">Historical Exact</em></button>
      <span className="viz-flow-arrow" aria-hidden="true">→</span></>}
      <strong className="viz-flow-node current"><small>현재 판단</small><span>{assessment.assessmentId}</span><em className="viz-contract-term">Current</em></strong></div>
    {(assessment.evidenceIds ?? []).length > 0 && <div className="viz-evidence-links"><span>판단 근거 식별자</span>
      {(assessment.evidenceIds ?? []).map(id => <button key={id} type="button" className="viz-evidence-action" onClick={() => onEvidence(id,
        { type: 'ASSESSMENT', label: assessment.assessmentId })}>{id}</button>)}</div>}
  </section>
}

export function EvidenceChain({ evidence, relation }) {
  if (!evidence) return null
  return <section className="viz-block viz-process viz-evidence-path" aria-label="근거 연결">
    <div className="viz-process-heading"><div><p className="eyebrow">EVIDENCE PATH · 근거 경로</p><h3>출처에서 현재 판단까지의 근거 경로</h3></div>
      <p>클릭한 관계만 보여주며 다른 사실·사건·판단으로 자동 확장하지 않습니다.</p></div>
    <ol className="viz-chain">
      <li><span className="viz-node-label">공식 출처</span><strong>{evidence.source?.sourceName}</strong><small>{SOURCE_TYPE_LABEL[evidence.source?.sourceType] ?? evidence.source?.sourceType}</small></li>
      <li><span className="viz-node-label">근거 자료</span><strong>{evidence.title ?? evidence.externalId}</strong><small>근거 식별자 · {evidence.evidenceId}</small></li>
      {relation && <li><span className="viz-node-label">연결 대상</span><strong>{RELATION_LABEL[relation.type] ?? relation.type} · {relation.label}</strong></li>}
    </ol>
    {evidence.originalUrl && <a className="viz-source-link" href={evidence.originalUrl} target="_blank" rel="noopener noreferrer">공식 원문 열기 <span aria-hidden="true">↗</span></a>}
  </section>
}
