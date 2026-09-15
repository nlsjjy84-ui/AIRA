import { useEffect, useRef, useState } from 'react'
import { searchEntities, readFinancialExact, readFinancialComparison, readAssessmentCurrent, readMarketCurrent,
  readMarketSeries, readMarketPrevious, readAssessmentHistorical } from './api/canonicalApi.js'
import { getCompanyEvents } from './api/companyApi.js'
import { getOfficialEvidence } from './api/evidenceApi.js'
import { getEventDetail } from './api/eventApi.js'
import { advance, contextTrail, EXPLORER_HISTORY_KEY, initialExplorerState, selectTarget, stateCopy, STEPS } from './explorerState.js'
import { formatQuantity, FinancialSplit, MarketOverview, MarketSeriesView, MarketPreviousView,
  OhlcCandle, AssessmentFlow, EvidenceChain } from './visualizations.jsx'

const INSPECT_SECTIONS = [['fact', '확인된 사실'], ['evidence', '공식 근거'], ['history', '변경 이력'], ['compare', '기간 비교']]
const RELATE_SECTIONS = [['confirmed', '확인된 연결'], ['correction', '정정 이력 연결'], ['review', '추가로 살펴볼 연결']]
const ASSESS_SECTIONS = [['fact', '확인된 사실'], ['interpretation', 'AIRA 해석'], ['reason', '왜 이렇게 해석했나요?'],
  ['path', '가능한 연결 경로'], ['confidence', '신뢰 수준'], ['unknown', '아직 모르는 것']]
const CONFIDENCE_LABEL = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음' }
const IMPORTANCE_LABEL = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음', CRITICAL: '매우 높음' }
const METHOD_LABEL = { RULE: '규칙 기반', AI: 'AI 기반', HYBRID: '혼합', HUMAN_REVIEW: '사람 검토' }
const EVENT_TYPE_LABEL = { EARNINGS: '실적', DISCLOSURE: '공시', BUSINESS: '사업', GOVERNANCE: '지배구조', POLICY: '정책', OTHER: '기타' }
const FINANCIAL_LABEL = { REVENUE: '매출', OPERATING_INCOME: '영업이익' }
const COMPARISON_REASON_COPY = {
  INVALID_EXACT_SELECTION: '비교할 기간과 공시 접수번호를 다시 확인해 주세요.',
  A_FACTS_NOT_FOUND: 'A 기간에서 비교할 정확한 재무 값을 찾지 못했습니다.',
  B_FACTS_NOT_FOUND: 'B 기간에서 비교할 정확한 재무 값을 찾지 못했습니다.',
  A_COMPANY_NOT_FOUND: 'A 기간의 기업 자료를 찾지 못했습니다.', B_COMPANY_NOT_FOUND: 'B 기간의 기업 자료를 찾지 못했습니다.',
  A_UNSUPPORTED_PREDICATE: 'A 기간에서 지원하지 않는 재무 항목입니다.', B_UNSUPPORTED_PREDICATE: 'B 기간에서 지원하지 않는 재무 항목입니다.',
  A_INCONSISTENT_PROVENANCE: 'A 기간의 공식 근거가 일관되지 않아 비교할 수 없습니다.',
  B_INCONSISTENT_PROVENANCE: 'B 기간의 공식 근거가 일관되지 않아 비교할 수 없습니다.',
  CURRENCY_MISMATCH: '두 기간의 통화 단위가 달라 직접 비교할 수 없습니다.',
}

const STATE_PRESENTATION = {
  NO_DATA: ['결과 없음', 'neutral'], PARTIAL: ['일부 확인', 'attention'], STALE: ['갱신 확인 필요', 'attention'],
  CONFLICTING: ['자료 충돌', 'danger'], BLOCKED: ['근거 확인 필요', 'attention'],
  UNSUPPORTED: ['조건 확인 필요', 'neutral'], UNAVAILABLE: ['조회 불가', 'danger'],
}

function LoadingNotice({ children }) {
  return <p role="status" className="explorer-loading"><span aria-hidden="true" />{children}</p>
}

function StateNotice({ state, lead, detail }) {
  const [label, tone] = STATE_PRESENTATION[state] ?? ['상태 확인', 'neutral']
  return <div role="status" className={`explorer-notice notice-${tone}`}>
    <strong>{lead ?? label}</strong><span>{stateCopy(state)}{detail ? ` ${detail}` : ''}</span>
  </div>
}

export default function CanonicalExplorer({ embedded = false, interest = null }) {
  const [context, setContext] = useState(() => window.history.state?.[EXPLORER_HISTORY_KEY] ?? initialExplorerState())
  const [term, setTerm] = useState(() => new URLSearchParams(window.location.search).get('q') ?? '')
  const [results, setResults] = useState({ loading: false, state: null, entities: [] })
  const [data, setData] = useState({ loading: false, state: null, value: null })
  const [compare, setCompare] = useState({ open: false, periodStart: '', periodEnd: '', receipt: '', loading: false, data: null, state: null })
  const [marketRange, setMarketRange] = useState({ open: false, from: '', to: '', loading: false, data: null, state: null })
  const [previous, setPrevious] = useState({ loading: false, data: null, state: null })
  const [ohlc, setOhlc] = useState({ loading: false, values: null, state: null })
  const [historical, setHistorical] = useState({ loading: false, value: null, state: null })
  const [evidence, setEvidence] = useState({ loading: false, value: null, state: null })
  const [eventDetailState, setEventDetailState] = useState({ loading: false, value: null, error: null })
  const [inspectSection, setInspectSection] = useState('fact')
  const [relateSection, setRelateSection] = useState('confirmed')
  const [assessSection, setAssessSection] = useState('fact')
  const requestNumber = useRef(0)
  const eventRequestNumber = useRef(0)

  useEffect(() => {
    const restore = () => { requestNumber.current += 1; setContext(window.history.state?.[EXPLORER_HISTORY_KEY] ?? initialExplorerState()); setData({ loading: false, state: null, value: null }); setCompare({ open: false, periodStart: '', periodEnd: '', receipt: '', loading: false, data: null, state: null }); setMarketRange({ open: false, from: '', to: '', loading: false, data: null, state: null }); setPrevious({ loading: false, data: null, state: null }); setEventDetailState({ loading: false, value: null, error: null }) }
    window.addEventListener('popstate', restore)
    return () => window.removeEventListener('popstate', restore)
  }, [])

  useEffect(() => {
    if (context.step === 'Inspect') setInspectSection('fact')
    if (context.step === 'Relate') setRelateSection('confirmed')
    if (context.step === 'Assess') setAssessSection('fact')
  }, [context.step, context.detail, context.eventId])

  useEffect(() => {
    if (context.step !== 'Assess' || !context.eventId) { setEventDetailState({ loading: false, value: null, error: null }); return undefined }
    const request = ++eventRequestNumber.current
    setEventDetailState({ loading: true, value: null, error: null })
    getEventDetail(context.eventId).then(result => {
      if (request === eventRequestNumber.current) setEventDetailState({ loading: false, value: result, error: null })
    }).catch(error => {
      if (request === eventRequestNumber.current) setEventDetailState({ loading: false, value: null, error })
    })
    return () => { eventRequestNumber.current += 1 }
  }, [context.step, context.eventId])

  useEffect(() => {
    if (!evidence.value) return
    requestAnimationFrame(() => document.getElementById('explorer-evidence-detail')?.scrollIntoView?.({ block: 'start' }))
  }, [evidence.value])

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
    const stepChanged = next.step !== context.step
    requestNumber.current += 1
    window.history.pushState({ ...window.history.state, [EXPLORER_HISTORY_KEY]: next }, '', window.location.href)
    setContext(next)
    if (next.step !== 'MAIN') setResults({ loading: false, state: null, entities: [] })
    setData({ loading: false, state: null, value: null })
    setCompare({ open: false, periodStart: '', periodEnd: '', receipt: '', loading: false, data: null, state: null })
    setMarketRange({ open: false, from: '', to: '', loading: false, data: null, state: null })
    setPrevious({ loading: false, data: null, state: null })
    setOhlc({ loading: false, values: null, state: null })
    setHistorical({ loading: false, value: null, state: null })
    setEvidence({ loading: false, value: null, state: null })
    setEventDetailState({ loading: false, value: null, error: null })
    if (stepChanged) requestAnimationFrame(() => document.querySelector('.canonical-explorer')?.scrollIntoView?.({ block: 'start' }))
  }

  useEffect(() => {
    if (!embedded) return undefined
    const moveFromShell = event => navigate(advance(context, event.detail))
    window.addEventListener('aira-workflow-step', moveFromShell)
    return () => window.removeEventListener('aira-workflow-step', moveFromShell)
  }, [embedded, context])

  useEffect(() => {
    if (!embedded) return
    window.dispatchEvent(new CustomEvent('aira-workflow-context', { detail: {
      step: context.step, hasTarget: Boolean(context.target), hasEvent: Boolean(context.eventId),
    } }))
  }, [embedded, context.step, context.target, context.eventId])

  function edit(next) {
    // Field typing is one selection in history; Back should return to the prior screen, not a prior character.
    requestNumber.current += 1
    window.history.replaceState({ ...window.history.state, [EXPLORER_HISTORY_KEY]: next }, '', window.location.href)
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
          window.history.replaceState({ ...window.history.state, [EXPLORER_HISTORY_KEY]: next }, '', window.location.href)
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

  function selectEvent(item) {
    navigate({ ...context, eventId: item.eventId, step: 'Assess', assessmentId: null, evidenceId: null })
  }

  const target = context.target
  const eventDetail = eventDetailState.value
  const selectedInterest = target?.entityType === 'COMPANY'
    ? interest?.items?.find(item => item.entityId === target.entityId)
    : null
  const stepCopy = ({ MAIN: '대상 찾기', Ask: '관점 선택', Inspect: '자료 살피기', Relate: '관계 잇기', Assess: '판단 근거 확인' })[context.step]
  const stageVisual = ({
    MAIN: { eyebrow: 'AIRA · MAIN', title: '대상을 찾고 근거를 따라 확인하세요.', copy: '기업·종목을 식별한 뒤 관점, 자료, 사건, 분석 순서로 살펴봅니다.' },
    Ask: { eyebrow: 'ASK · 질문', title: '무엇을 볼지 먼저 정하세요.', copy: '질문을 입력하는 대신 같은 대상을 어떤 관점으로 확인할지 선택합니다.' },
    Inspect: { eyebrow: 'INSPECT · 살피기', title: '자료를 좁혀 정확한 관측까지 내려갑니다.', copy: '분류와 하위분류를 거쳐 값·기간·공시·공식 관측을 확인합니다.' },
    Relate: { eyebrow: 'RELATE · 잇기', title: '사건과 대상의 연결을 따라갑니다.', copy: '확인된 사건과 관련 대상을 근거가 있는 관계만 이어서 봅니다.' },
    Assess: { eyebrow: 'ASSESS · 판단', title: '현재 분석과 판단 근거를 분리해 봅니다.', copy: '현재 판단과 당시 판단을 구분하고 연결된 공식 근거를 직접 확인합니다.' },
  })[context.step]
  const detailLabel = context.detail === 'Historical Exact' ? '정확한 기간·공시' : context.detail === 'KRX Current' ? '공식 거래일 현재값' : context.detail
  const selectionLabel = ({ HISTORICAL_EXACT: '정확한 기간·공시', LATEST_OFFICIAL_MARKET_D: '공식 거래일 현재값', KRX_CURRENT: '공식 거래일 현재값' })[data.selection] ?? detailLabel
  const currentCategory = context.step === 'Relate' || context.step === 'Assess' ? '사건' : context.category
  const currentDetail = context.step === 'Relate' ? '관련 사건' : context.step === 'Assess' ? '현재 분석' : detailLabel
  const trailParts = contextTrail(context)
  const eventCompanies = context.step === 'Assess' ? (eventDetail?.companies ?? []) : []
  const targetContext = target ? `${target.canonicalName} · ${target.entityType === 'COMPANY' ? '기업' : '종목'}`
    : eventCompanies.length > 0 ? eventCompanies.map(company => company.companyName).join(' · ') : '선택 전'
  const contextNodes = [
    { label: target ? '대상' : context.step === 'Assess' ? '관련 대상' : '대상', value: targetContext, ready: Boolean(target) || eventCompanies.length > 0 },
    { label: '관점', value: context.perspective ?? '선택 전', ready: Boolean(context.perspective) },
    { label: '분류', value: currentCategory ?? '선택 전', ready: Boolean(currentCategory) },
    { label: '세부', value: currentDetail ?? '선택 전', ready: Boolean(currentDetail) },
  ]
  return <div className={`canonical-explorer ${embedded ? 'embedded' : ''} stage-${context.step.toLowerCase()}`}>
    <header className="explorer-head stage-hero"><p className="eyebrow">{stageVisual.eyebrow}</p><h1>{stageVisual.title}</h1>
      <p className="explorer-head-copy">{stageVisual.copy}</p></header>
    {!embedded && <form role="search" onSubmit={submitSearch} className="explorer-search">
      <label htmlFor="canonical-search">기업·종목 찾기</label>
      <input id="canonical-search" type="search" value={term} onChange={event => setTerm(event.target.value)}
        placeholder="기업명·종목명·종목코드 검색" />
      <button type="submit">검색</button>
    </form>}
    {results.loading && <LoadingNotice>검색 중…</LoadingNotice>}
    {!results.loading && results.state && results.state !== 'AVAILABLE' && <StateNotice state={results.state} />}
    {results.entities.length > 0 && <section className="search-results-panel" aria-labelledby="search-results-title">
      <div className="search-results-heading"><div><p className="eyebrow">SEARCH RESULT</p><h2 id="search-results-title">정확한 대상을 선택하세요.</h2></div>
        <p>{results.entities.length}개 결과 · 이름과 식별자를 확인한 뒤 선택합니다.</p></div>
      <ul className="explorer-results">{results.entities.map(item => {
        const typeLabel = item.entityType === 'COMPANY' ? '기업' : '종목'
        const identifier = item.symbol ?? item.canonicalKey
        return <li key={`${item.entityType}:${item.entityId}`}><button type="button" className={`search-result ${item.entityType.toLowerCase()}`}
          aria-label={`${item.canonicalName} ${typeLabel} · ${identifier} 선택`} onClick={() => navigate(selectTarget(context, item))}>
          <span className="result-identity"><strong>{item.canonicalName}</strong><small>{typeLabel}</small></span>
          <span className="result-key">{identifier}</span><span className="result-action" aria-hidden="true">선택 →</span>
        </button></li>
      })}</ul>
    </section>}

    {context.step === 'MAIN' && <section className="main-entry" aria-labelledby="main-entry-title">
      <div className="main-entry-copy"><p className="eyebrow">START HERE</p><h2 id="main-entry-title">위 검색창에서 기업·종목을 찾는 것부터 시작합니다.</h2>
        <p>추천 순위가 아니라 정확한 대상을 고른 뒤, 필요한 관점과 공식 근거를 따라 확인합니다.</p></div>
      <div className="main-path" aria-label="AIRA 탐색 구조">
        <div className="main-path-step main-path-main"><span>01 · MAIN</span><strong>대상 찾기</strong><small>기업·종목 식별</small></div>
        <i aria-hidden="true">→</i>
        <div className="main-path-step main-path-ask"><span>02 · ASK</span><strong>관점 정하기</strong><small>무엇을 볼지 선택</small></div>
        <i aria-hidden="true">→</i>
        <div className="main-path-branch"><div className="main-path-step main-path-inspect"><span>03A · INSPECT</span><strong>자료 살피기</strong><small>값·기간·공시</small></div>
          <div className="main-path-step main-path-relate"><span>03B · RELATE</span><strong>관계 잇기</strong><small>사건·관련 대상</small></div></div>
        <i aria-hidden="true">→</i>
        <div className="main-path-step main-path-assess"><span>04 · ASSESS</span><strong>판단 확인</strong><small>분석·근거</small></div>
      </div>
      <p className="main-path-note">Ask에서 고른 관점에 따라 Inspect 또는 Relate로 갈라지고, 필요한 경우 Assess에서 판단 근거까지 확인합니다.</p>
    </section>}
    {context.step !== 'MAIN' && <section className="explorer-context" aria-label="현재 탐색 문맥">
      <div className="context-stage"><span>현재 단계</span><strong>{context.step} · {stepCopy}</strong>
        <button type="button" className="context-back" onClick={() => window.history.back()}>← 이전 상태</button></div>
      <ol className="context-branch">{contextNodes.map((node, index) => <li key={node.label} className={node.ready ? 'ready' : 'pending'}>
        <span>{node.label}</span><strong>{node.value}</strong>{index < contextNodes.length - 1 && <i aria-hidden="true">›</i>}
      </li>)}</ol>
      <nav aria-label="현재 탐색 경로" className="explorer-trail">{trailParts.map((part, index) => <span key={index}>{index > 0 && ' › '}{part}</span>)}</nav>
    </section>}
    {!embedded && <nav aria-label="탐색 단계" className="explorer-steps">{STEPS.map(step => <button key={step} type="button"
      aria-current={context.step === step ? 'step' : undefined}
      disabled={step !== 'MAIN' && !target || step === 'Assess' && !context.eventId}
      onClick={() => navigate(advance(context, step))}>{step}</button>)}</nav>}
    {context.step !== 'MAIN' && target?.entityType === 'COMPANY' && interest && <section className="explorer-interest" aria-label="선택한 회사 관심 설정">
      <div><span>관심회사</span><strong>{target.canonicalName}</strong></div>
      <div className="explorer-interest-actions">
        <button type="button" className={selectedInterest ? 'saved-action' : 'secondary-action'} disabled={interest.loading}
          onClick={() => !interest.user ? interest.onLogin() : selectedInterest ? interest.onRemove(target.entityId) : interest.onSave(target.entityId)}>
          {interest.loading ? '처리 중…' : selectedInterest ? '관심회사에서 삭제' : interest.user ? '관심회사에 저장' : '로그인하고 관심회사에 저장'}</button>
        {selectedInterest && <button type="button" className="secondary-action" disabled={interest.loading}
          onClick={() => interest.onAlert(target.entityId, !selectedInterest.alertEnabled)}>
          {selectedInterest.alertEnabled ? '앱 알림 끄기' : '앱 알림 켜기'}</button>}
      </div>
      {interest.error && <div className="explorer-interest-error" role="alert"><span>관심회사 요청을 처리하지 못했습니다.</span>
        {interest.retry && <button type="button" onClick={interest.retry}>다시 시도</button>}</div>}
    </section>}
    {context.step === 'Ask' && target && <section className="explorer-panel"><h2>어떤 관점으로 볼지 선택하세요.</h2>
      <p className="context-subject">현재 대상 · <strong>{target.canonicalName}</strong> · {target.entityType === 'COMPANY' ? '기업' : '종목'}</p>
      <div className="explorer-options branch-options"><button type="button" className="choice-card ask-choice inspect-choice" aria-label="공식 사실과 근거" onClick={() => navigate({ ...context, perspective: '공식 사실과 근거', step: 'Inspect', category: null, detail: null })}>
          <span className="choice-route">INSPECT · 살피기</span><strong>공식 사실과 근거</strong><small>정확한 값·기간·공시와 공식 출처를 따라 살펴봅니다.</small></button>
        <button type="button" className="choice-card ask-choice relate-choice" aria-label="사건과 분석" onClick={() => navigate({ ...context, perspective: '사건과 분석', step: 'Relate', category: null, detail: null })}>
          <span className="choice-route">RELATE · 잇기</span><strong>사건과 분석</strong><small>확인된 사건과 관련 회사, 현재 판단의 근거를 잇습니다.</small></button></div>
      <p className="panel-lead panel-note">검색은 대상을 찾는 곳입니다. Ask는 질문을 입력하는 챗봇이 아니라, 같은 대상을 어떤 관점으로 확인할지 정하는 단계입니다.</p></section>}
    {context.step === 'Inspect' && target && <section className="explorer-panel"><h2>자료를 하위 분류로 좁혀 확인하세요.</h2>
      {!context.category && <div className="explorer-options branch-options"><button type="button" className="choice-card" onClick={() => navigate({ ...context, category: target.entityType === 'COMPANY' ? '재무' : '시장', detail: null })}><strong>{target.entityType === 'COMPANY' ? '재무' : '시장'}</strong><small>{target.entityType === 'COMPANY' ? '공식 재무 값과 정확한 보고기간' : 'KRX 공식 거래일 관측값'}</small></button></div>}
      {context.category && !context.detail && <div className="explorer-options branch-options"><button type="button" className="choice-card" onClick={() => navigate({ ...context, detail: target.entityType === 'COMPANY' ? 'Historical Exact' : 'KRX Current' })}><strong>{target.entityType === 'COMPANY' ? '정확한 기간·공시' : '공식 거래일 현재값'}</strong><small>{target.entityType === 'COMPANY' ? '기간과 접수번호까지 지정해 같은 관측을 다시 확인합니다.' : '추천 순위가 아닌 공식 관측값 자체를 확인합니다.'}</small></button></div>}
      {context.detail && <div className="explorer-detail"><p className="classification-path"><span>{context.category}</span><i aria-hidden="true">›</i><strong>{detailLabel}</strong></p>
        {target.entityType === 'COMPANY' ? <div className="exact-observation-layout"><div className="explorer-fields exact-fields"><label>기간 시작 <input type="date" value={context.periodStart} onChange={event => edit({ ...context, periodStart: event.target.value })} /></label>
          <label>기간 종료 <input type="date" value={context.periodEnd} onChange={event => edit({ ...context, periodEnd: event.target.value })} /></label>
          <label className="receipt-field">공시 접수번호 <input value={context.receipt} onChange={event => edit({ ...context, receipt: event.target.value })} /></label>
          <button type="button" onClick={loadDetail}>정확한 자료 확인</button></div>
          <aside className="exact-selection-note" aria-label="정확한 관측 기준"><strong>정확한 관측 기준</strong><p>기간 시작·종료와 공시 접수번호를 함께 지정해 같은 공식 관측을 다시 확인합니다.</p><span>기간 + 접수번호를 함께 확인</span></aside></div>
          : <><label>시장 항목 <select value={context.predicate} onChange={event => edit({ ...context, predicate: event.target.value })}><option value="CLOSE_PRICE">종가</option><option value="TRADING_VOLUME">거래량</option><option value="MARKET_CAP">시가총액</option></select></label><button type="button" onClick={loadDetail}>정확한 자료 확인</button></>}
        </div>}
      <p className="panel-lead panel-note">선택한 대상과 관점은 유지한 채 분류 → 세부 자료 → 정확한 관측 순서로 내려갑니다.</p></section>}
    {context.step === 'Relate' && target && <section className="explorer-panel"><h2>어떤 것들이 연결되어 있는지 나눠서 확인하세요.</h2>
      <p className="context-subject">현재 대상 · <strong>{target.canonicalName}</strong> · {target.entityType === 'COMPANY' ? '기업' : '종목'}</p>
      <nav className="subsection-nav" aria-label="Relate 하위 메뉴">{RELATE_SECTIONS.map(([key, label]) => <button key={key} type="button"
        aria-current={relateSection === key ? 'page' : undefined} onClick={() => setRelateSection(key)}>{label}</button>)}</nav>
      {relateSection === 'confirmed' && <div className="subsection-panel" aria-label="확인된 연결">
        {target.entityType === 'COMPANY' ? <button type="button" className="primary-action" onClick={loadDetail}>확인된 사건 불러오기</button>
          : <p className="state-message">종목을 기업으로 자동 전환하지 않습니다. 기업 사건은 기업을 다시 선택해 확인하세요.</p>}
        {Array.isArray(data.value) && <div className="relation-list">{data.value.map(item => <article className="relation-row" key={item.eventId}>
          <div className="relation-route"><span>공식 근거 {(item.evidence ?? []).length}건</span><i aria-hidden="true">→</i><span>사건</span><i aria-hidden="true">→</i><span>{target.canonicalName}</span></div>
          <h3>{item.title}</h3><p className="event-meta">{EVENT_TYPE_LABEL[item.eventType] ?? item.eventType} · {item.occurredAt?.slice(0, 10) ?? '발생시각 미상'}</p>
          <div className="relation-actions">{(item.evidence ?? []).map(reference => <button key={reference.evidenceId} type="button" className="secondary-action"
            onClick={() => openEvidence(reference.evidenceId, { type: 'EVENT', label: item.eventId })}>공식 근거 · {reference.sourceName}</button>)}
            <button type="button" className="primary-action" onClick={() => selectEvent(item)}>이 사건 판단 보기</button></div>
        </article>)}</div>}
        <p className="panel-lead panel-note">공식 근거로 확인된 연결만 표시하고 확인되지 않은 관계는 이어 붙이지 않습니다.</p></div>}
      {relateSection === 'correction' && <div className="subsection-panel subview-empty" aria-label="정정 이력 연결"><strong>현재 확인된 정정 이력 연결이 없습니다.</strong>
        <p>공시의 정정 관계가 공식 데이터로 명시적으로 확인될 때만 연결합니다. Evidence revision만으로 정정 계보를 추론하지 않습니다.</p></div>}
      {relateSection === 'review' && <div className="subsection-panel subview-empty" aria-label="추가로 살펴볼 연결"><strong>현재 저장된 추가 확인 연결이 없습니다.</strong>
        <p>공식 자료에서 직접 확인되지 않은 인과관계는 자동으로 만들지 않습니다. 별도 근거가 확보된 경우에만 이 영역에 표시합니다.</p></div>}
    </section>}
    {context.step === 'Assess' && context.eventId && <section className="explorer-panel"><h2>확인된 사실과 AIRA 해석을 구분해 확인하세요.</h2>
      <p className="event-meta">{eventDetail ? `선택한 사건 · ${eventDetail.title}` : eventDetailState.loading ? '선택한 사건 확인 중…'
        : eventDetailState.error?.status === 404 ? '선택한 사건 · 현재 공개 상세 없음' : '선택한 사건 · 불러오기 실패'}</p>
      <nav className="subsection-nav assess-submenu" aria-label="Assess 하위 메뉴">{ASSESS_SECTIONS.map(([key, label]) => <button key={key} type="button"
        aria-current={assessSection === key ? 'page' : undefined} disabled={!eventDetail} onClick={() => setAssessSection(key)}>{label}</button>)}</nav>
      {assessSection === 'fact' && <div className="subsection-panel" aria-label="확인된 사실">{eventDetail ? <>
        <h3>{eventDetail.title}</h3><dl className="assessment-facts"><div><dt>대상</dt><dd>{(eventDetail.companies ?? []).map(company => company.companyName).join(' · ')}</dd></div>
          <div><dt>사건 유형</dt><dd>{EVENT_TYPE_LABEL[eventDetail.eventType] ?? eventDetail.eventType}</dd></div><div><dt>발생일</dt><dd>{eventDetail.occurredAt?.slice(0, 10) ?? '발생시각 미상'}</dd></div></dl>
        <div className="relation-actions">{(eventDetail.eventEvidence ?? []).map(item => <button key={item.evidenceId} type="button" className="secondary-action"
          onClick={() => openEvidence(item.evidenceId, { type: 'EVENT', label: eventDetail.eventId })}>공식 근거 · {item.sourceName}</button>)}</div></>
        : eventDetailState.loading ? <LoadingNotice>사건 사실 확인 중…</LoadingNotice>
          : eventDetailState.error?.status === 404 ? <p className="state-message">이 사건은 현재 공개 상세로 제공되지 않습니다.</p>
            : eventDetailState.error ? <div className="state-message" role="alert">사건 상세를 불러오지 못했습니다.</div>
              : <p className="state-message">현재 확인할 사건 상세가 없습니다.</p>}</div>}
      {assessSection === 'interpretation' && <div className="subsection-panel" aria-label="AIRA 해석">{eventDetail?.assessment ? <>
        <p className="assessment-summary">{eventDetail.assessment.summary}</p><button type="button" className="primary-action" onClick={loadDetail}>현재·이전 판단 연결 확인</button>
        <AssessmentFlow assessment={data.value} onHistorical={openHistorical} onEvidence={openEvidence} />
        {historical.loading && <LoadingNotice>이전 당시 판단 확인 중…</LoadingNotice>}
        {historical.value && <div className="historical-summary"><h3>이전 당시 판단</h3><dl className="assessment-facts">
          <div><dt>판단 시점</dt><dd>{historical.value.completedAt?.slice(0, 10) ?? '저장된 시점 없음'}</dd></div><div><dt>분석 버전</dt><dd>{historical.value.analysisVersion}</dd></div>
          <div><dt>분석 방법</dt><dd>{METHOD_LABEL[historical.value.method] ?? historical.value.method}</dd></div><div><dt>확신</dt><dd>{CONFIDENCE_LABEL[historical.value.confidence] ?? historical.value.confidence}</dd></div></dl>
          <p>{historical.value.uncertainty}</p><div className="relation-actions">{(historical.value.evidenceIds ?? []).map(id => <button key={id} type="button" className="secondary-action"
            onClick={() => openEvidence(id, { type: 'ASSESSMENT', label: historical.value.assessmentId })}>당시 판단 근거</button>)}</div></div>}</>
        : <p className="state-message">이 사건에 연결된 현재 AIRA 판단이 없습니다.</p>}</div>}
      {assessSection === 'reason' && <div className="subsection-panel" aria-label="왜 이렇게 해석했나요?">{(eventDetail?.assessment?.evidence ?? []).length > 0
        ? <div className="assessment-evidence-list">{eventDetail.assessment.evidence.map(item => <div className="assessment-evidence-row" key={item.evidenceId}><div><strong>{item.sourceName}</strong><span>{item.title}</span></div>
          <button type="button" className="secondary-action" onClick={() => openEvidence(item.evidenceId, { type: 'ASSESSMENT', label: eventDetail.assessment.assessmentId })}>근거 상세</button></div>)}</div>
        : <p className="state-message">현재 표시할 판단 근거가 없습니다.</p>}</div>}
      {assessSection === 'path' && <div className="subsection-panel subview-empty" aria-label="가능한 연결 경로"><strong>현재 저장된 연결 경로가 없습니다.</strong>
        <p>확인되지 않은 인과관계는 만들지 않습니다. 근거가 있는 연결 경로가 등록된 경우에만 표시합니다.</p></div>}
      {assessSection === 'confidence' && <div className="subsection-panel" aria-label="신뢰 수준">{eventDetail?.assessment ? <dl className="assessment-facts">
        <div><dt>확신</dt><dd>{CONFIDENCE_LABEL[eventDetail.assessment.confidence] ?? eventDetail.assessment.confidence}</dd></div><div><dt>중요도</dt><dd>{IMPORTANCE_LABEL[eventDetail.assessment.importance] ?? eventDetail.assessment.importance}</dd></div>
        <div><dt>분석 방법</dt><dd>{METHOD_LABEL[eventDetail.assessment.method] ?? eventDetail.assessment.method}</dd></div><div><dt>분석 버전</dt><dd>{eventDetail.assessment.analysisVersion}</dd></div></dl>
        : <p className="state-message">현재 표시할 신뢰 수준이 없습니다.</p>}</div>}
      {assessSection === 'unknown' && <div className="subsection-panel" aria-label="아직 모르는 것"><p className="uncertainty-copy">{eventDetail?.assessment?.uncertainty ?? '현재 저장된 미확인 정보가 없습니다.'}</p></div>}
    </section>}
    {data.loading && <LoadingNotice>자료를 확인하는 중…</LoadingNotice>}
    {!data.loading && data.state && data.state !== 'AVAILABLE' && <StateNotice state={data.state} />}
    {context.step === 'Inspect' && target?.entityType === 'SECURITY' && !data.loading && data.value && !Array.isArray(data.value) && <div className="explorer-data">
      <p>{selectionLabel} {data.periodStart && `· ${data.periodStart} — ${data.periodEnd}`}</p>
      {data.value.tradingDate && <MarketOverview observation={data} predicate={context.predicate} />}
    </div>}
    {context.step === 'Inspect' && target?.entityType === 'COMPANY' && data.value?.facts?.length > 0 && <section className="inspect-result-workspace" aria-label="재무 Inspect 결과">
      <p className="inspect-result-context">정확한 기간·공시 · {data.periodStart} — {data.periodEnd} · 공시 접수번호 {data.receipt}</p>
      <nav className="subsection-nav" aria-label="Inspect 하위 메뉴">{INSPECT_SECTIONS.map(([key, label]) => <button key={key} type="button"
        aria-current={inspectSection === key ? 'page' : undefined} onClick={() => setInspectSection(key)}>{label}</button>)}</nav>
      {inspectSection === 'fact' && <div className="subsection-panel" aria-label="확인된 사실"><div className="inspect-fact-list" role="list">{data.value.facts.map(fact => <div className="inspect-fact-row" role="listitem" key={`${fact.predicate}:${fact.evidenceId}`}>
        <span>{FINANCIAL_LABEL[fact.predicate] ?? fact.predicate}</span><strong>{formatQuantity(fact.value, fact.currency)}</strong><small>{fact.periodStart ?? data.periodStart} — {fact.periodEnd ?? data.periodEnd}</small>
      </div>)}</div></div>}
      {inspectSection === 'evidence' && <div className="subsection-panel" aria-label="공식 근거"><div className="inspect-evidence-list">{[...new Map(data.value.facts.filter(fact => fact.evidenceId).map(fact => [fact.evidenceId, fact])).values()].map(fact => <div className="inspect-evidence-row" key={fact.evidenceId}>
        <div><strong>{fact.sourceName ?? '공식 출처'}</strong><span>공시 접수번호 {fact.evidenceExternalId ?? data.receipt}</span><small>근거 식별자 · {fact.evidenceId}</small></div>
        <button type="button" className="secondary-action" onClick={() => openEvidence(fact.evidenceId, { type: 'FACT', label: `${target.canonicalName} · ${data.periodStart} — ${data.periodEnd}` })}>공식 근거 상세</button>
      </div>)}</div></div>}
      {inspectSection === 'history' && <div className="subsection-panel subview-empty" aria-label="변경 이력"><strong>현재 확인된 변경 이력이 없습니다.</strong>
        <p>정정·변경 관계가 공식 데이터로 확인될 때만 이력으로 연결합니다. 이름이나 revision만으로 변경 계보를 만들지 않습니다.</p></div>}
      {inspectSection === 'compare' && <div className="subsection-panel" aria-label="기간 비교"><section className="comparison-workspace"><div className="split-mode-heading"><div><p className="eyebrow">SPLIT VIEW · 비교</p><h3>A ↔ B 분할 비교</h3>
          <p>A의 현재 문맥을 유지한 채 B의 정확한 기간·공시를 옆에 놓고 비교합니다.</p></div><button type="button" onClick={() => setCompare(value => ({ ...value, open: !value.open }))}>{compare.open ? '분할보기 닫기' : '분할보기 열기'}</button></div>
        {compare.open && <><form onSubmit={loadComparison} className="explorer-fields compare-fields" aria-label="B 정확한 기간 선택">
          <label>B 기간 시작 <input type="date" value={compare.periodStart} onChange={event => changeCompareField('periodStart', event.target.value)} /></label>
          <label>B 기간 종료 <input type="date" value={compare.periodEnd} onChange={event => changeCompareField('periodEnd', event.target.value)} /></label>
          <label>B 공시 접수번호 <input value={compare.receipt} onChange={event => changeCompareField('receipt', event.target.value)} /></label>
          <button type="submit">B 관측값 확인</button></form>
          {compare.loading && <LoadingNotice>B 관측값 확인 중…</LoadingNotice>}
          {compare.state && compare.state !== 'AVAILABLE' && <StateNotice state={compare.state} lead="비교할 수 없습니다."
            detail={COMPARISON_REASON_COPY[compare.data?.reason]} />}
          {compare.data?.state === 'AVAILABLE' && <FinancialSplit comparison={compare.data} onEvidence={openEvidence} />}</>}
      </section></div>}
    </section>}
    {context.step === 'Inspect' && target?.entityType === 'SECURITY' && context.detail &&
      <section className="viz-block"><button type="button" onClick={() => setMarketRange(value => ({ ...value, open: !value.open }))}>
        공식 관측 흐름 {marketRange.open ? '닫기' : '열기'}</button>
        {marketRange.open && <><form onSubmit={loadMarketRange} className="explorer-fields" aria-label="시장 시계열 날짜 범위">
          <label>시작일 <input type="date" value={marketRange.from} onChange={event => changeRangeField('from', event.target.value)} /></label>
          <label>종료일 <input type="date" value={marketRange.to} onChange={event => changeRangeField('to', event.target.value)} /></label>
          <button type="submit">공식 시계열 확인</button></form>
          {marketRange.loading && <LoadingNotice>공식 관측일을 불러오는 중…</LoadingNotice>}
          {marketRange.state && marketRange.state !== 'AVAILABLE' && <StateNotice state={marketRange.state} lead="관측 흐름을 표시할 수 없습니다." />}
          <MarketSeriesView series={marketRange.data} onEvidence={openEvidence} /></>}
      </section>}
    {context.step === 'Inspect' && target?.entityType === 'SECURITY' && data.value?.tradingDate &&
      <section className="viz-block"><button type="button" onClick={loadPrevious}>D와 직전 실제 관측일 비교</button>
        {previous.loading && <LoadingNotice>직전 공식 관측값 확인 중…</LoadingNotice>}
        {previous.state && previous.state !== 'AVAILABLE' && <StateNotice state={previous.state} lead="직전 관측과 비교할 수 없습니다." />}
        <MarketPreviousView comparison={previous.data} onEvidence={openEvidence} />
      </section>}
    {context.step === 'Inspect' && target?.entityType === 'SECURITY' && data.value?.tradingDate &&
      <section className="viz-block"><button type="button" onClick={loadOhlc}>같은 공식 거래일 OHLC 확인</button>
        {ohlc.loading && <LoadingNotice>시가·고가·저가·종가 확인 중…</LoadingNotice>}
        {ohlc.state === 'NO_DATA' && <div role="status" className="explorer-notice notice-neutral"><strong>캔들 표시 불가</strong><span>같은 공식 거래일의 시가·고가·저가·종가가 모두 확인되지 않았습니다.</span></div>}
        <OhlcCandle observations={ohlc.values} />
        {ohlc.values?.TRADING_VOLUME?.state === 'AVAILABLE' && ohlc.values.TRADING_VOLUME.periodStart === data.periodStart &&
          <p>거래량 {ohlc.values.TRADING_VOLUME.value.value}주 · 공식 거래일 {ohlc.values.TRADING_VOLUME.periodStart}</p>}
      </section>}
    {evidence.loading && <LoadingNotice>공식 근거 확인 중…</LoadingNotice>}
    {evidence.state === 'UNAVAILABLE' && <StateNotice state="UNAVAILABLE" lead="공식 근거를 불러올 수 없습니다." />}
    {evidence.value && <section id="explorer-evidence-detail" className="evidence-inspector" aria-label="열린 근거 상세">
      <div className="evidence-inspector-head"><strong>공식 근거 상세</strong><button type="button" onClick={() => setEvidence({ loading: false, value: null, state: null, relation: null })}>근거 상세 닫기</button></div>
      <EvidenceChain evidence={evidence.value} relation={evidence.relation} />
    </section>}
  </div>
}
