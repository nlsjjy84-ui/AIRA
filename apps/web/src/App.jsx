import { useCallback, useEffect, useMemo, useState } from 'react'
import { getCompanies, getFinancialFacts, getFinancialPeriods } from './api/companyApi.js'

const LABELS = { REVENUE: '매출', OPERATING_INCOME: '영업이익' }

function messageFor(error, subject) {
  if (error?.status === 400) return `${subject} 요청을 확인해 주세요.`
  if (error?.status === 404) return `이용 가능한 ${subject}을 찾지 못했습니다.`
  if (error?.status === 409) return '공식 근거를 일관되게 확인할 수 없습니다.'
  return '서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

function Status({ children, busy = false }) {
  return <p className="status" role={busy ? 'status' : 'note'} aria-live="polite">{children}</p>
}

function ErrorState({ error, subject, retry }) {
  return <div className="state-message" role="alert">
    <p>{messageFor(error, subject)}</p>
    <button type="button" className="secondary-action" onClick={retry}>다시 시도</button>
  </div>
}

export default function App() {
  const [companiesState, setCompaniesState] = useState({ loading: true, data: [], error: null })
  const [selectedCompany, setSelectedCompany] = useState(null)
  const [periodsState, setPeriodsState] = useState({ loading: false, data: [], error: null })
  const [selectedPeriod, setSelectedPeriod] = useState(null)
  const [factsState, setFactsState] = useState({ loading: false, data: [], error: null })

  const loadCompanies = useCallback(() => {
    const controller = new AbortController()
    setCompaniesState({ loading: true, data: [], error: null })
    getCompanies(controller.signal)
      .then(body => setCompaniesState({ loading: false, data: body.companies ?? [], error: null }))
      .catch(error => error.name !== 'AbortError' && setCompaniesState({ loading: false, data: [], error }))
    return () => controller.abort()
  }, [])

  useEffect(loadCompanies, [loadCompanies])

  const selectCompany = useCallback((company) => {
    setSelectedCompany(company)
    setSelectedPeriod(null)
    setFactsState({ loading: false, data: [], error: null })
    setPeriodsState({ loading: true, data: [], error: null })
    getFinancialPeriods(company.companyId)
      .then(body => {
        const periods = body.periods ?? []
        setPeriodsState({ loading: false, data: periods, error: null })
        if (periods.length === 1) setSelectedPeriod(periods[0])
      })
      .catch(error => setPeriodsState({ loading: false, data: [], error }))
  }, [])

  const retryPeriods = () => selectedCompany && selectCompany(selectedCompany)

  const loadFacts = useCallback(() => {
    if (!selectedCompany || !selectedPeriod) return
    const controller = new AbortController()
    setFactsState({ loading: true, data: [], error: null })
    getFinancialFacts(selectedCompany.companyId, selectedPeriod, controller.signal)
      .then(body => setFactsState({ loading: false, data: body.facts ?? [], error: null }))
      .catch(error => error.name !== 'AbortError' && setFactsState({ loading: false, data: [], error }))
    return () => controller.abort()
  }, [selectedCompany, selectedPeriod])

  useEffect(loadFacts, [loadFacts])

  const evidence = useMemo(() => {
    const grouped = new Map()
    factsState.data.forEach(fact => {
      if (!grouped.has(fact.evidenceId)) grouped.set(fact.evidenceId, fact)
    })
    return [...grouped.values()]
  }, [factsState.data])

  return <>
    <header className="site-header"><a className="brand" href="/" aria-label="AIRA 홈">AIRA</a></header>
    <main>
      <section className="intro" aria-labelledby="page-title">
        <p className="eyebrow">PUBLIC COMPANY FINANCIALS</p>
        <h1 id="page-title">공식 데이터와 근거를<br />함께 확인하세요.</h1>
        <p className="intro-copy">현재 제공되는 기업을 선택하면 정확한 보고 기간의 핵심 재무정보와 원문 공시를 볼 수 있습니다.</p>
        <a className="primary-action" href="#companies">기업 둘러보기</a>
      </section>

      <section id="companies" className="content-section" aria-labelledby="companies-title">
        <div className="section-heading"><span>01</span><h2 id="companies-title">기업 선택</h2></div>
        {companiesState.loading && <Status busy>기업을 불러오는 중입니다.</Status>}
        {companiesState.error && <ErrorState error={companiesState.error} subject="기업" retry={loadCompanies} />}
        {!companiesState.loading && !companiesState.error && companiesState.data.length === 0 &&
          <Status>현재 확인할 수 있는 기업이 없습니다. 데이터가 준비되면 이곳에 표시됩니다.</Status>}
        <div className="company-list">
          {companiesState.data.map(company => <button key={company.companyId} type="button"
            className={`company-option ${selectedCompany?.companyId === company.companyId ? 'selected' : ''}`}
            aria-pressed={selectedCompany?.companyId === company.companyId}
            onClick={() => selectCompany(company)}>
            <span className="company-name">{company.canonicalName}</span>
            <span className="company-meta">{company.countryCode === 'KR' ? '대한민국' : company.countryCode ?? '국가 미상'}</span>
            <span aria-hidden="true">→</span>
          </button>)}
        </div>
      </section>

      {selectedCompany && <section className="content-section" aria-labelledby="period-title">
        <div className="section-heading"><span>02</span><h2 id="period-title">보고 기간</h2></div>
        {periodsState.loading && <Status busy>이용 가능한 기간을 불러오는 중입니다.</Status>}
        {periodsState.error && <ErrorState error={periodsState.error} subject="보고 기간" retry={retryPeriods} />}
        {!periodsState.loading && !periodsState.error && periodsState.data.length === 0 &&
          <Status>이 기업에서 이용 가능한 재무 기간이 없습니다.</Status>}
        {periodsState.data.length > 0 && <div className="period-control">
          <label htmlFor="reporting-period">정확한 보고 기간</label>
          <select id="reporting-period" value={selectedPeriod ? `${selectedPeriod.periodStart}|${selectedPeriod.periodEnd}` : ''}
            onChange={event => {
              const [start, end] = event.target.value.split('|')
              setSelectedPeriod(periodsState.data.find(period => period.periodStart === start && period.periodEnd === end))
            }}>
            {periodsState.data.length > 1 && <option value="">기간을 선택하세요</option>}
            {periodsState.data.map(period => <option key={`${period.periodStart}|${period.periodEnd}`}
              value={`${period.periodStart}|${period.periodEnd}`}>{period.periodStart} — {period.periodEnd}</option>)}
          </select>
        </div>}
      </section>}

      {selectedPeriod && <section className="content-section facts-section" aria-labelledby="facts-title">
        <div className="section-heading"><span>03</span><h2 id="facts-title">{selectedCompany.canonicalName} 핵심 재무정보</h2></div>
        <p className="period-caption">{selectedPeriod.periodStart} — {selectedPeriod.periodEnd}</p>
        {factsState.loading && <Status busy>재무정보와 공식 근거를 확인하는 중입니다.</Status>}
        {factsState.error && <ErrorState error={factsState.error} subject="재무정보" retry={loadFacts} />}
        {!factsState.loading && !factsState.error && factsState.data.length === 0 &&
          <Status>선택한 기간에 표시할 재무정보가 없습니다.</Status>}
        <dl className="fact-list">
          {factsState.data.map(fact => <div className="fact-row" key={`${fact.predicate}-${fact.evidenceId}`}>
            <dt>{LABELS[fact.predicate] ?? fact.predicate}</dt>
            <dd><strong>{new Intl.NumberFormat('ko-KR').format(fact.value)}</strong> <span>{fact.currency}</span></dd>
          </div>)}
        </dl>
        {evidence.length > 0 && <aside className="evidence" aria-labelledby="evidence-title">
          <p className="eyebrow" id="evidence-title">OFFICIAL EVIDENCE</p>
          {evidence.map(item => <div key={item.evidenceId} className="evidence-row">
            <div><strong>{item.sourceName}</strong><span>공시 식별자 {item.evidenceExternalId}</span></div>
            <a href={item.evidenceOriginalUrl} target="_blank" rel="noopener noreferrer">원문 확인 <span aria-hidden="true">↗</span></a>
          </div>)}
          <p className="evidence-note">같은 공시에 포함된 재무 항목은 하나의 공식 근거로 묶어 표시합니다.</p>
        </aside>}
      </section>}
    </main>
    <footer><span>AIRA</span><p>공식 시장정보를 근거와 함께 제공합니다.</p></footer>
  </>
}
