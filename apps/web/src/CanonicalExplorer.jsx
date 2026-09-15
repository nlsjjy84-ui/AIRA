import { useEffect, useRef, useState } from 'react'
import { searchEntities, readFinancialExact, readFinancialComparison, readAssessmentCurrent, readMarketCurrent,
  readMarketSeries, readMarketPrevious, readAssessmentHistorical } from './api/canonicalApi.js'
import { getCompanyEvents } from './api/companyApi.js'
import { getOfficialEvidence } from './api/evidenceApi.js'
import { getEventDetail } from './api/eventApi.js'
import { advance, contextTrail, initialExplorerState, selectTarget, stateCopy, STEPS } from './explorerState.js'
import { FinancialOverview, FinancialSplit, MarketOverview, MarketSeriesView, MarketPreviousView,
  OhlcCandle, EventTimeline, AssessmentFlow, EvidenceChain } from './visualizations.jsx'

const KEY = 'airaCanonicalExplorer12B'

export default function CanonicalExplorer({ embedded = false }) {
  const [context, setContext] = useState(() => window.history.state?.[KEY] ?? initialExplorerState())
  const [term, setTerm] = useState(() => new URLSearchParams(window.location.search).get('q') ?? '')
  const [results, setResults] = useState({ loading: false, state: null, entities: [] })
  const [data, setData] = useState({ loading: false, state: null, value: null })
  const [compare, setCompare] = useState({ open: false, periodStart: '', periodEnd: '', receipt: '', loading: false, data: null, state: null })
  const [marketRange, setMarketRange] = useState({ open: false, from: '', to: '', loading: false, data: null, state: null })
  const [previous, setPrevious] = useState({ loading: false, data: null, state: null })
  const [ohlc, setOhlc] = useState({ loading: false, values: null, state: null })
  const [historical, setHistorical] = useState({ loading: false, value: null, state: null })
  const [evidence, setEvidence] = useState({ loading: false, value: null, state: null })
  const [eventDetail, setEventDetail] = useState(null)
  const requestNumber = useRef(0)

  useEffect(() => {
    const restore = () => { requestNumber.current += 1; setContext(window.history.state?.[KEY] ?? initialExplorerState()); setData({ loading: false, state: null, value: null }); setCompare({ open: false, periodStart: '', periodEnd: '', receipt: '', loading: false, data: null, state: null }); setMarketRange({ open: false, from: '', to: '', loading: false, data: null, state: null }); setPrevious({ loading: false, data: null, state: null }); setEventDetail(null) }
    window.addEventListener('popstate', restore)
    return () => window.removeEventListener('popstate', restore)
  }, [])

  useEffect(() => {
    const query = new URLSearchParams(window.location.search).get('q')?.trim()
    if (!query) return
    const request = ++requestNumber.current
    setResults({ loading: true, state: null, entities: [] })
    searchEntities(query).then(response => {
      if (request === requestNumber.current) setResults({ loading: false, state: response.state, entities: response.entities ?? [] })
    }).catch(() => {
      if (request === requestNumber.current) setResults({ loading: false, state: 'UNAVAILABLE', entities: [] })
    })
  }, [])

  function navigate(next) {
    if (next === context) return
    requestNumber.current += 1
    window.history.pushState({ ...window.history.state, [KEY]: next }, '', window.location.href)
    setContext(next)
    setData({ loading: false, state: null, value: null })
    setCompare({ open: false, periodStart: '', periodEnd: '', receipt: '', loading: false, data: null, state: null })
    setMarketRange({ open: false, from: '', to: '', loading: false, data: null, state: null })
    setPrevious({ loading: false, data: null, state: null })
    setOhlc({ loading: false, values: null, state: null })
    setHistorical({ loading: false, value: null, state: null })
    setEvidence({ loading: false, value: null, state: null })
    setEventDetail(null)
  }

  useEffect(() => {
    if (!embedded) return undefined
    const moveFromShell = event => navigate(advance(context, event.detail))
    window.addEventListener('aira-workflow-step', moveFromShell)
    return () => window.removeEventListener('aira-workflow-step', moveFromShell)
  }, [embedded, context])

  function edit(next) {
    // Field typing is one selection in history; Back should return to the prior screen, not a prior character.
    requestNumber.current += 1
    window.history.replaceState({ ...window.history.state, [KEY]: next }, '', window.location.href)
    setContext(next)
    setData({ loading: false, state: null, value: null })
    setCompare(value => ({ ...value, loading: false, data: null, state: null }))
    setMarketRange(value => ({ ...value, loading: false, data: null, state: null }))
    setPrevious({ loading: false, data: null, state: null })
    setOhlc({ loading: false, values: null, state: null })
  }

  function changeCompareField(field, value) {
    requestNumber.current += 1
    setCompare(previous => ({ ...previous, [field]: value, loading: false, data: null, state: null }))
  }

  function changeRangeField(field, value) {
    requestNumber.current += 1
    setMarketRange(previous => ({ ...previous, [field]: value, loading: false, data: null, state: null }))
  }

  async function submitSearch(event) {
    event.preventDefault()
    const query = term.trim()
    if (!query) { setResults({ loading: false, state: 'UNSUPPORTED', entities: [] }); return }
    const request = ++requestNumber.current
    setResults({ loading: true, state: null, entities: [] })
    try {
      const response = await searchEntities(query)
      if (request === requestNumber.current) setResults({ loading: false, state: response.state, entities: response.entities ?? [] })
    } catch {
      if (request === requestNumber.current) setResults({ loading: false, state: 'UNAVAILABLE', entities: [] })
    }
  }

  async function loadDetail() {
    const request = ++requestNumber.current
    setData({ loading: true, state: null, value: null })
    try {
      let response
      if (context.step === 'Inspect' && context.target?.entityType === 'COMPANY') {
        if (!context.periodStart || !context.periodEnd || !context.receipt) {
          setData({ loading: false, state: 'UNSUPPORTED', value: null }); return
        }
        response = await readFinancialExact(context.target.entityId, context)
      } else if (context.step === 'Inspect' && context.target?.entityType === 'SECURITY') {
        response = await readMarketCurrent(context.target.entityId, context.predicate)
      } else if (context.step === 'Relate' && context.target?.entityType === 'COMPANY') {
        const events = await getCompanyEvents(context.target.entityId)
        response = { state: events.events?.length ? 'AVAILABLE' : 'NO_DATA', value: events.events ?? [] }
      } else if (context.step === 'Assess' && context.eventId) {
        response = await readAssessmentCurrent(context.eventId)
      } else {
        response = { state: 'UNSUPPORTED', value: null }
      }
      if (request === requestNumber.current) {
        setData({ loading: false, state: response.state, value: response.value,
          selection: response.selection, periodStart: response.periodStart, periodEnd: response.periodEnd, receipt: response.receipt })
        const assessmentId = response.value?.assessmentId ?? context.assessmentId
        const evidenceId = response.value?.facts?.[0]?.evidenceId ?? response.value?.evidenceIds?.[0] ?? context.evidenceId
        if (assessmentId !== context.assessmentId || evidenceId !== context.evidenceId) {
          const next = { ...context, assessmentId, evidenceId }
          window.history.replaceState({ ...window.history.state, [KEY]: next }, '', window.location.href)
          setContext(next)
        }
      }
    } catch {
      if (request === requestNumber.current) setData({ loading: false, state: 'UNAVAILABLE', value: null })
    }
  }

  async function loadComparison(event) {
    event.preventDefault()
    if (!compare.periodStart || !compare.periodEnd || !compare.receipt) {
      setCompare(value => ({ ...value, state: 'UNSUPPORTED' })); return
    }
    const request = ++requestNumber.current
    setCompare(value => ({ ...value, loading: true, state: null, data: null }))
    try {
      // The 12D endpoint verifies both selected receipts and returns the only permitted change values.
      const result = await readFinancialComparison(context.target.entityId, context, compare,
        [...new Set(data.value.facts.map(fact => fact.predicate))])
      if (request === requestNumber.current) setCompare(value => ({ ...value, loading: false, state: result.state, data: result }))
    } catch {
      if (request === requestNumber.current) setCompare(value => ({ ...value, loading: false, state: 'UNAVAILABLE' }))
    }
  }

  async function loadMarketRange(event) {
    event.preventDefault()
    if (!marketRange.from || !marketRange.to) {
      setMarketRange(value => ({ ...value, state: 'UNSUPPORTED', data: null })); return
    }
    const request = ++requestNumber.current
    setMarketRange(value => ({ ...value, loading: true, state: null, data: null }))
    try {
      const result = await readMarketSeries(context.target.entityId, context.predicate, marketRange.from, marketRange.to)
      if (request === requestNumber.current) setMarketRange(value => ({ ...value, loading: false, state: result.state, data: result }))
    } catch {
      if (request === requestNumber.current) setMarketRange(value => ({ ...value, loading: false, state: 'UNAVAILABLE', data: null }))
    }
  }

  async function loadPrevious() {
    const request = ++requestNumber.current
    setPrevious({ loading: true, state: null, data: null })
    try {
      // D and its Fact ID come from Current; a stored earlier point never replaces a missing D.
      const result = await readMarketPrevious(context.target.entityId, context.predicate,
        data.value.tradingDate, data.value.factId)
      if (request === requestNumber.current) setPrevious({ loading: false, state: result.state, data: result })
    } catch {
      if (request === requestNumber.current) setPrevious({ loading: false, state: 'UNAVAILABLE', data: null })
    }
  }

  async function loadOhlc() {
    const request = ++requestNumber.current
    setOhlc({ loading: true, values: null, state: null })
    const names = ['OPEN_PRICE', 'HIGH_PRICE', 'LOW_PRICE', 'CLOSE_PRICE', 'TRADING_VOLUME']
    try {
      const responses = await Promise.allSettled(names.map(name => readMarketCurrent(context.target.entityId, name)))
      const values = Object.fromEntries(responses.map((result, index) => [names[index], result.status === 'fulfilled'
        ? result.value : { state: 'UNAVAILABLE', value: null }]))
      if (request === requestNumber.current) setOhlc({ loading: false, values,
        state: names.slice(0, 4).every(name => values[name].state === 'AVAILABLE') ? 'AVAILABLE' : 'NO_DATA' })
    } catch {
      if (request === requestNumber.current) setOhlc({ loading: false, values: null, state: 'UNAVAILABLE' })
    }
  }

  async function openEvidence(id, relation = null) {
    setEvidence({ loading: true, value: null, state: null, relation })
    try { setEvidence({ loading: false, value: await getOfficialEvidence(id), state: 'AVAILABLE', relation }) }
    catch { setEvidence({ loading: false, value: null, state: 'UNAVAILABLE', relation }) }
  }

  async function openHistorical(id) {
    setHistorical({ loading: true, value: null, state: null })
    try { setHistorical({ loading: false, value: await readAssessmentHistorical(id), state: 'AVAILABLE' }) }
    catch { setHistorical({ loading: false, value: null, state: 'UNAVAILABLE' }) }
  }

  async function selectEvent(item) {
    navigate({ ...context, eventId: item.eventId, step: 'Assess', assessmentId: null, evidenceId: null })
    const request = requestNumber.current
    try {
      const result = await getEventDetail(item.eventId)
      if (request === requestNumber.current) setEventDetail(result)
    } catch {
      if (request === requestNumber.current) setEventDetail(null)
    }
  }

  const target = context.target
  return <div className={`canonical-explorer ${embedded ? 'embedded' : ''}`}>
    <header className="explorer-head"><p className="eyebrow">AIRA · MAIN</p><h1>대상을 찾고 근거를 따라 확인하세요.</h1>
      <p>기업·종목을 식별한 뒤 관점, 자료, 사건, 분석 순서로 살펴봅니다.</p></header>
    {!embedded && <form role="search" onSubmit={submitSearch} className="explorer-search">
      <label htmlFor="canonical-search">기업·종목 찾기</label>
      <input id="canonical-search" type="search" value={term} onChange={event => setTerm(event.target.value)}
        placeholder="기업명·종목명·종목코드 검색" />
      <button type="submit">검색</button>
    </form>}
    {results.loading && <p role="status">검색 중…</p>}
    {!results.loading && results.state && results.state !== 'AVAILABLE' && <p role="status">{stateCopy(results.state)}</p>}
    {results.entities.length > 0 && <ul className="explorer-results">{results.entities.map(item => <li key={`${item.entityType}:${item.entityId}`}>
      <button type="button" onClick={() => navigate(selectTarget(context, item))}>
        {item.canonicalName} <span>{item.entityType === 'COMPANY' ? '기업' : '종목'} · {item.symbol ?? item.canonicalKey}</span>
      </button></li>)}</ul>}

    <nav aria-label="현재 탐색 경로" className="explorer-trail">{contextTrail(context).map((part, index) => <span key={index}>{index > 0 && ' › '}{part}</span>)}</nav>
    {!embedded && <nav aria-label="탐색 단계" className="explorer-steps">{STEPS.map(step => <button key={step} type="button"
      aria-current={context.step === step ? 'step' : undefined}
      disabled={step !== 'MAIN' && !target || step === 'Assess' && !context.eventId}
      onClick={() => navigate(advance(context, step))}>{step}</button>)}</nav>}
    {context.step !== 'MAIN' && <button type="button" className="secondary-action" onClick={() => window.history.back()}>이전 상태로</button>}

    {context.step === 'MAIN' && <p>검색 결과에서 정확한 기업 또는 종목을 선택하세요.</p>}
    {context.step === 'Ask' && target && <section className="explorer-panel"><h2>Ask · 확인할 관점</h2>
      <p>{target.canonicalName} · {target.entityType}. 자유 질문 대신 관점을 선택합니다.</p>
      <div className="explorer-options"><button type="button" onClick={() => navigate({ ...context, perspective: '공식 사실과 근거', step: 'Inspect', category: null, detail: null })}>공식 사실과 근거</button>
        <button type="button" onClick={() => navigate({ ...context, perspective: '사건과 분석', step: 'Relate', category: null, detail: null })}>사건과 분석</button></div></section>}
    {context.step === 'Inspect' && target && <section className="explorer-panel"><h2>Inspect · 자료 분류</h2>
      {!context.category && <div className="explorer-options"><button type="button" onClick={() => navigate({ ...context, category: target.entityType === 'COMPANY' ? '재무' : '시장', detail: null })}>{target.entityType === 'COMPANY' ? '재무' : '시장'}</button></div>}
      {context.category && !context.detail && <div className="explorer-options"><button type="button" onClick={() => navigate({ ...context, detail: target.entityType === 'COMPANY' ? 'Historical Exact' : 'KRX Current' })}>{target.entityType === 'COMPANY' ? '정확한 기간·공시' : '공식 거래일 현재값'}</button></div>}
      {context.detail && <div className="explorer-detail"><p>{context.category} › {context.detail}</p>
        {target.entityType === 'COMPANY' ? <div className="explorer-fields"><label>기간 시작 <input type="date" value={context.periodStart} onChange={event => edit({ ...context, periodStart: event.target.value })} /></label>
          <label>기간 종료 <input type="date" value={context.periodEnd} onChange={event => edit({ ...context, periodEnd: event.target.value })} /></label>
          <label>공시 접수번호 <input value={context.receipt} onChange={event => edit({ ...context, receipt: event.target.value })} /></label></div>
          : <label>시장 항목 <select value={context.predicate} onChange={event => edit({ ...context, predicate: event.target.value })}><option value="CLOSE_PRICE">종가</option><option value="TRADING_VOLUME">거래량</option><option value="MARKET_CAP">시가총액</option></select></label>}
        <button type="button" onClick={loadDetail}>정확한 자료 확인</button></div>}</section>}
    {context.step === 'Relate' && target && <section className="explorer-panel"><h2>Relate · 관련 사건</h2>
      {target.entityType === 'COMPANY' ? <button type="button" onClick={loadDetail}>사건 목록 확인</button> : <p>종목을 기업으로 자동 전환하지 않습니다. 기업 사건은 기업을 다시 선택해 확인하세요.</p>}
      {Array.isArray(data.value) && <EventTimeline events={data.value} onSelect={selectEvent} />}</section>}
    {context.step === 'Assess' && context.eventId && <section className="explorer-panel"><h2>Assess · 현재 분석</h2>
      <p>Event ID {context.eventId}</p><button type="button" onClick={loadDetail}>Assessment 확인</button>
      {eventDetail && <div className="viz-block"><h3>사건 상세</h3><p>{eventDetail.title} · Event ID {eventDetail.eventId}</p>
        {!eventDetail.assessment && <p>이 Event에 연결된 Assessment가 없습니다.</p>}
        {(eventDetail.eventEvidence ?? []).map(item => <button key={item.evidenceId} type="button" onClick={() => openEvidence(item.evidenceId,
          { type: 'EVENT', label: eventDetail.eventId })}>Event Evidence ID {item.evidenceId}</button>)}</div>}
      <AssessmentFlow assessment={data.value} onHistorical={openHistorical} onEvidence={openEvidence} />
      {historical.loading && <p role="status">이전 Assessment 확인 중…</p>}
      {historical.value && <div className="viz-block"><h3>이전 Historical Exact</h3><p>Assessment ID {historical.value.assessmentId} · Event ID {historical.value.eventId}</p>
        {(historical.value.evidenceIds ?? []).map(id => <button key={id} type="button" onClick={() => openEvidence(id,
          { type: 'ASSESSMENT', label: historical.value.assessmentId })}>Evidence ID {id}</button>)}</div>}</section>}
    {data.loading && <p role="status">자료를 확인하는 중…</p>}
    {!data.loading && data.state && data.state !== 'AVAILABLE' && <p role="status">{data.state}: {stateCopy(data.state)}</p>}
    {!data.loading && data.value && !Array.isArray(data.value) && <div className="explorer-data">
      <p>{data.selection ?? context.detail} {data.periodStart && `· ${data.periodStart} — ${data.periodEnd}`} {data.receipt && `· receipt ${data.receipt}`}</p>
      {data.value.facts && <FinancialOverview observation={data} onEvidence={openEvidence} />}
      {data.value.tradingDate && <MarketOverview observation={data} predicate={context.predicate} />}
    </div>}
    {context.step === 'Inspect' && target?.entityType === 'COMPANY' && data.value?.facts?.length > 0 &&
      <section className="viz-block"><button type="button" onClick={() => setCompare(value => ({ ...value, open: !value.open }))}>A ↔ B 비교 {compare.open ? '닫기' : '열기'}</button>
        {compare.open && <><form onSubmit={loadComparison} className="explorer-fields" aria-label="B 정확한 기간 선택">
          <label>B 기간 시작 <input type="date" value={compare.periodStart} onChange={event => changeCompareField('periodStart', event.target.value)} /></label>
          <label>B 기간 종료 <input type="date" value={compare.periodEnd} onChange={event => changeCompareField('periodEnd', event.target.value)} /></label>
          <label>B 공시 접수번호 <input value={compare.receipt} onChange={event => changeCompareField('receipt', event.target.value)} /></label>
          <button type="submit">B 관측값 확인</button></form>
          {compare.loading && <p role="status">B 관측값 확인 중…</p>}
          {compare.state && compare.state !== 'AVAILABLE' && <p role="status">비교 불가 · {compare.state}: {stateCopy(compare.state)} {compare.data?.reason}</p>}
          {compare.data?.state === 'AVAILABLE' && <FinancialSplit comparison={compare.data} onEvidence={openEvidence} />}</>}
      </section>}
    {context.step === 'Inspect' && target?.entityType === 'SECURITY' && context.detail &&
      <section className="viz-block"><button type="button" onClick={() => setMarketRange(value => ({ ...value, open: !value.open }))}>
        공식 관측 흐름 {marketRange.open ? '닫기' : '열기'}</button>
        {marketRange.open && <><form onSubmit={loadMarketRange} className="explorer-fields" aria-label="시장 시계열 날짜 범위">
          <label>시작일 <input type="date" value={marketRange.from} onChange={event => changeRangeField('from', event.target.value)} /></label>
          <label>종료일 <input type="date" value={marketRange.to} onChange={event => changeRangeField('to', event.target.value)} /></label>
          <button type="submit">공식 시계열 확인</button></form>
          {marketRange.loading && <p role="status">공식 관측일을 불러오는 중…</p>}
          {marketRange.state && marketRange.state !== 'AVAILABLE' && <p role="status">시계열 {marketRange.state}: {stateCopy(marketRange.state)} {marketRange.data?.reason}</p>}
          <MarketSeriesView series={marketRange.data} onEvidence={openEvidence} /></>}
      </section>}
    {context.step === 'Inspect' && target?.entityType === 'SECURITY' && data.value?.tradingDate &&
      <section className="viz-block"><button type="button" onClick={loadPrevious}>D와 직전 실제 관측일 비교</button>
        {previous.loading && <p role="status">직전 공식 관측값 확인 중…</p>}
        {previous.state && previous.state !== 'AVAILABLE' && <p role="status">직전 비교 불가 · {previous.state}: {stateCopy(previous.state)} {previous.data?.reason}</p>}
        <MarketPreviousView comparison={previous.data} onEvidence={openEvidence} />
      </section>}
    {context.step === 'Inspect' && target?.entityType === 'SECURITY' && data.value?.tradingDate &&
      <section className="viz-block"><button type="button" onClick={loadOhlc}>같은 공식 거래일 OHLC 확인</button>
        {ohlc.loading && <p role="status">OHLC 확인 중…</p>}
        {ohlc.state === 'NO_DATA' && <p role="status">OHLC 네 값이 모두 확인되지 않아 캔들을 표시하지 않습니다.</p>}
        <OhlcCandle observations={ohlc.values} />
        {ohlc.values?.TRADING_VOLUME?.state === 'AVAILABLE' && ohlc.values.TRADING_VOLUME.periodStart === data.periodStart &&
          <p>거래량 {ohlc.values.TRADING_VOLUME.value.value}주 · 공식 거래일 {ohlc.values.TRADING_VOLUME.periodStart}</p>}
      </section>}
    {evidence.loading && <p role="status">Evidence 확인 중…</p>}
    {evidence.state === 'UNAVAILABLE' && <p role="status">Evidence를 불러올 수 없습니다.</p>}
    <EvidenceChain evidence={evidence.value} relation={evidence.relation} />
  </div>
}
