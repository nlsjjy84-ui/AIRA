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

export function FinancialOverview({ observation, onEvidence }) {
  const [expanded, setExpanded] = useState(null)
  const facts = observation?.value?.facts ?? []
  if (!facts.length) return null
  return <section className="viz-block" aria-label="재무 한눈에 보기"><h3>한눈에 · 재무</h3>
    <p>{observation.periodStart} — {observation.periodEnd} · 공시 {observation.receipt}</p>
    <div className="viz-cards">{facts.map(fact => <article key={`${fact.predicate}:${fact.evidenceId}`}>
      <h4>{FACT_LABEL[fact.predicate] ?? fact.predicate}</h4><strong>{formatQuantity(fact.value, fact.currency)}</strong>
      <button type="button" onClick={() => setExpanded(expanded === fact.predicate ? null : fact.predicate)}>상세 {expanded === fact.predicate ? '닫기' : '보기'}</button>
      {expanded === fact.predicate && <dl><dt>정확한 값</dt><dd>{fact.value} {fact.currency}</dd>
        <dt>기간</dt><dd>{fact.periodStart} — {fact.periodEnd}</dd>
        <dt>Evidence ID</dt><dd>{fact.evidenceId}</dd>
        <dt>출처</dt><dd>{fact.sourceName}</dd></dl>}
      {expanded === fact.predicate && fact.evidenceId && <button type="button" onClick={() => onEvidence(fact.evidenceId,
        { type: 'FACT', label: `${fact.predicate} · ${fact.periodStart} — ${fact.periodEnd}` })}>근거 확인</button>}
    </article>)}</div></section>
}

export function FinancialSplit({ comparison, onEvidence }) {
  if (comparison?.state !== 'AVAILABLE' || !comparison.metrics?.length) return null
  return <section className="viz-block" aria-label="A와 B 분할 비교"><h3>비교 · A ↔ B</h3>
    <p>백엔드가 검증한 정확한 A/B 기간과 공시만 비교합니다. 증감은 B − A입니다.</p>
    {comparison.metrics.map(metric => <div className="viz-compare-row" key={metric.predicate}>
      <h4>{FACT_LABEL[metric.predicate] ?? metric.predicate}</h4>
      <div className="viz-split">{[['A', comparison.a, metric.a], ['B', comparison.b, metric.b]].map(([side, period, observation]) =>
        <article key={side}><small>{side} · {period.periodStart} — {period.periodEnd} · 공시 {period.receipt}</small>
          <strong>{formatQuantity(observation.value, observation.currency)}</strong>
          {observation.evidenceIds.map(id => <button key={id} type="button" onClick={() => onEvidence(id,
            { type: 'FACT', label: `${metric.predicate} · ${side} ${period.periodStart} — ${period.periodEnd} · ${period.receipt}` })}>
            {side} Evidence ID {id}</button>)}</article>)}</div>
      <p>증감액 {formatQuantity(metric.changeAmountBMinusA, metric.a.currency)} · 증감률 {metric.changePercentBOverA == null
        ? `계산 불가 (${metric.percentReason})` : formatQuantity(metric.changePercentBOverA, '%')}</p>
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
    <p>KRX 공식 거래일 {value.tradingDate} · Fact ID {value.factId}</p>
    <p>이 응답은 D의 단일 관측값입니다. 흐름과 직전 관측 비교는 아래에서 따로 엽니다.</p></section>
}

export function MarketSeriesView({ series, onEvidence }) {
  const [selectedId, setSelectedId] = useState(null)
  if (series?.state !== 'AVAILABLE' || !series.points?.length) return null
  const selected = series.points.find(point => point.factId === selectedId)
  const max = Math.max(1, ...series.points.map(point => Number(point.value)))
  return <section className="viz-block" aria-label="공식 시장 관측 흐름"><h3>흐름 · 공식 관측일</h3>
    <p>{series.from} — {series.to} · {FACT_LABEL[series.predicate] ?? series.predicate}. 각 막대는 저장된 실제 관측일이며 빈 날짜는 채우지 않습니다.</p>
    <ol className="viz-series">{series.points.map(point => <li key={point.factId}>
      <button type="button" aria-pressed={selectedId === point.factId} onClick={() => setSelectedId(point.factId)}>
        <time>{point.tradingDate}</time><meter aria-label={`${point.tradingDate} ${series.predicate} ${point.value}`}
          min="0" max={max} value={Math.max(0, Number(point.value))} />
        <span>{formatQuantity(point.value, series.predicate === 'TRADING_VOLUME' ? '주' : 'KRW')}</span>
      </button></li>)}</ol>
    {selected && <div className="viz-selection"><h4>선택한 정확한 관측</h4><p>{selected.tradingDate} · 값 {selected.value} · Fact ID {selected.factId}</p>
      <p>공식 Evidence {selected.evidenceExternalId}</p>
      {selected.evidenceIds.map(id => <button key={id} type="button" onClick={() => onEvidence(id,
        { type: 'FACT', label: `${series.predicate} · ${selected.tradingDate} · ${selected.factId}` })}>Evidence ID {id}</button>)}</div>}
  </section>
}

export function MarketPreviousView({ comparison, onEvidence }) {
  if (comparison?.state !== 'AVAILABLE' || !comparison.current || !comparison.previous) return null
  return <section className="viz-block" aria-label="D와 직전 공식 관측 비교"><h3>비교 · D와 직전 실제 관측일</h3>
    <div className="viz-split">{[['직전 관측', comparison.previous], ['D', comparison.current]].map(([label, point]) =>
      <article key={label}><small>{label} · {point.tradingDate}</small><strong>{formatQuantity(point.value,
        comparison.predicate === 'TRADING_VOLUME' ? '주' : 'KRW')}</strong><small>Fact ID {point.factId}</small>
        {point.evidenceIds.map(id => <button key={id} type="button" onClick={() => onEvidence(id,
          { type: 'FACT', label: `${comparison.predicate} · ${point.tradingDate} · ${point.factId}` })}>
          Evidence ID {id}</button>)}</article>)}</div>
    <p>증감액 {formatQuantity(comparison.changeAmount, comparison.predicate === 'TRADING_VOLUME' ? '주' : 'KRW')}
      {' · '}증감률 {comparison.changePercent == null ? `계산 불가 (${comparison.percentReason})`
        : formatQuantity(comparison.changePercent, '%')}</p>
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
  return <figure className="viz-block"><figcaption>상세 · 공식 거래일 {observations.OPEN_PRICE.value.tradingDate} OHLC</figcaption>
    <svg viewBox="0 0 120 150" role="img" aria-label={`시가 ${open}, 고가 ${high}, 저가 ${low}, 종가 ${close}`}>
      <line x1="60" x2="60" y1={y(high)} y2={y(low)} stroke="currentColor" strokeWidth="2" />
      <rect x="43" y={Math.min(y(open), y(close))} width="34" height={Math.max(2, Math.abs(y(open) - y(close)))} fill="none" stroke="currentColor" strokeWidth="2" />
    </svg><dl>{names.map(name => <div key={name}><dt>{FACT_LABEL[name]}</dt><dd>{formatQuantity(observations[name].value.value, 'KRW')} · Fact ID {observations[name].value.factId}</dd></div>)}</dl></figure>
}

export function EventTimeline({ events, onSelect }) {
  if (!events?.length) return null
  // Only provider-approved occurredAt participates in chronological order; unknown time remains separate.
  const dated = events.filter(item => item.occurredAt).toSorted((a, b) => a.occurredAt.localeCompare(b.occurredAt))
  const unknown = events.filter(item => !item.occurredAt)
  return <section className="viz-block" aria-label="사건 시간순"><h3>흐름 · Event</h3>
    <ol className="viz-timeline">{[...dated, ...unknown].map(item => <li key={item.eventId}>
      <time>{item.occurredAt ? item.occurredAt.slice(0, 10) : '발생시각 미상'}</time>
      <button type="button" onClick={() => onSelect(item)}>{item.title}</button><small>Event ID {item.eventId}</small>
    </li>)}</ol></section>
}

export function AssessmentFlow({ assessment, onHistorical, onEvidence }) {
  if (!assessment?.assessmentId) return null
  return <section className="viz-block" aria-label="Assessment 승계 흐름"><h3>흐름 · Assessment</h3>
    <div className="viz-flow">{assessment.supersedesAssessmentId && <><button type="button" onClick={() => onHistorical(assessment.supersedesAssessmentId)}>
      이전 · {assessment.supersedesAssessmentId}</button><span aria-hidden="true">→</span></>}
      <strong>현재 · {assessment.assessmentId}</strong></div>
    <p>Current는 API의 unique terminal입니다. 이전 ID는 Historical Exact로 따로 확인합니다.</p>
    {(assessment.evidenceIds ?? []).map(id => <button key={id} type="button" onClick={() => onEvidence(id,
      { type: 'ASSESSMENT', label: assessment.assessmentId })}>Evidence ID {id}</button>)}</section>
}

export function EvidenceChain({ evidence, relation }) {
  if (!evidence) return null
  return <section className="viz-block" aria-label="근거 연결"><h3>근거 · 출처 → Evidence</h3>
    <ol className="viz-chain"><li>{evidence.source?.sourceName} · {evidence.source?.sourceType}</li>
      <li>{evidence.title ?? evidence.externalId} · Evidence ID {evidence.evidenceId}</li>
      {relation && <li>{relation.type} · {relation.label}</li>}</ol>
    {evidence.originalUrl && <a href={evidence.originalUrl} target="_blank" rel="noopener noreferrer">공식 원문 열기</a>}
  </section>
}
