import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  createDemoFinanceConsent, deleteFinanceData, getFinanceAccess, getFinanceConsents,
  getFinancePatterns, getFinanceSummary, importDemoFinanceData,
  reauthenticateFinance, requestFinanceAiExplanation, revokeFinanceAccess,
  revokeFinanceConsent, upsertFinanceBudget,
} from './api/personalFinanceApi.js'

const CATEGORY_LABELS = {
  TOTAL: '전체', HOUSING: '주거', FOOD: '식비', TRANSPORT: '교통', SHOPPING: '쇼핑',
  HEALTH: '건강', EDUCATION: '교육', LEISURE: '여가', SUBSCRIPTION: '구독', FINANCE: '금융', OTHER: '기타',
}

function currentMonth() {
  return new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', timeZone: 'Asia/Seoul' })
    .format(new Date()).slice(0, 7)
}

function won(value) {
  const number = Number(value ?? 0)
  return `${new Intl.NumberFormat('ko-KR').format(Number.isFinite(number) ? number : 0)}원`
}

function FinanceError({ children }) {
  return <p className="finance-error" role="alert">{children}</p>
}
export default function PersonalFinance({ user, onLogin }) {
  const [month, setMonth] = useState(currentMonth)
  const [access, setAccess] = useState({ loading: Boolean(user), authorized: false, error: null })
  const [password, setPassword] = useState('')
  const [consents, setConsents] = useState([])
  const [summary, setSummary] = useState(null)
  const [pattern, setPattern] = useState(null)
  const [explanation, setExplanation] = useState(null)
  const [budget, setBudget] = useState('')
  const [notice, setNotice] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [dataManagementOpen, setDataManagementOpen] = useState(false)

  const activeDemoConsent = useMemo(() => consents.find(item =>
    item.sourceType === 'DEMO_IMPORT' && item.providerKey === 'AIRA_DEMO_V1' && !item.revokedAt), [consents])

  const loadProtected = useCallback(async selectedMonth => {
    const [consentRows, summaryData, patternData] = await Promise.all([
      getFinanceConsents(), getFinanceSummary(selectedMonth), getFinancePatterns(selectedMonth),
    ])
    setConsents(consentRows ?? [])
    setSummary(summaryData)
    setPattern(patternData)
  }, [])

  const checkAccess = useCallback(async () => {
    if (!user) return
    setAccess({ loading: true, authorized: false, error: null })
    try {
      const status = await getFinanceAccess()
      setAccess({ loading: false, authorized: Boolean(status?.authorized), error: null })
      if (status?.authorized) await loadProtected(month)
    } catch (failure) {
      setAccess({ loading: false, authorized: false, error: failure })
    }
  }, [loadProtected, month, user])

  useEffect(() => { checkAccess() }, [checkAccess])

  async function reauthenticate(event) {
    event.preventDefault()
    setBusy(true); setError(null); setNotice(null)
    try {
      await reauthenticateFinance(password)
      setPassword('')
      setAccess({ loading: false, authorized: true, error: null })
      await loadProtected(month)
      setNotice('금융정보 보호 확인이 완료되었습니다. 10분 동안 내 금융을 확인할 수 있습니다.')
    } catch {
      setError('금융정보 보호 확인에 실패했습니다. 비밀번호를 다시 확인해 주세요.')
    } finally { setBusy(false) }
  }

  async function connectDemo() {
    setBusy(true); setError(null); setNotice(null)
    try {
      if (!activeDemoConsent) await createDemoFinanceConsent()
      const imported = await importDemoFinanceData()
      await loadProtected(month)
      setNotice(`AIRA 데모 데이터 ${imported.insertedTransactionCount}건을 연결했습니다. 실제 금융기관 연결이 아닙니다.`)
    } catch (failure) {
      setError(failure.status === 409 ? '이미 활성화된 데모 동의가 있습니다. 다시 불러와 주세요.' : '데모 금융데이터를 연결하지 못했습니다.')
    } finally { setBusy(false) }
  }
  async function refreshMonth(nextMonth) {
    setMonth(nextMonth); setExplanation(null); setError(null)
    if (!access.authorized) return
    setBusy(true)
    try { await loadProtected(nextMonth) }
    catch { setError('선택한 달의 금융정보를 불러오지 못했습니다.') }
    finally { setBusy(false) }
  }

  async function saveBudget(event) {
    event.preventDefault()
    setBusy(true); setError(null); setNotice(null)
    try {
      await upsertFinanceBudget(month, 'TOTAL', budget)
      await loadProtected(month)
      setBudget('')
      setNotice('이번 달 전체 예산을 저장했습니다.')
    } catch { setError('예산을 저장하지 못했습니다. 금액을 확인해 주세요.') }
    finally { setBusy(false) }
  }

  async function explain() {
    setBusy(true); setError(null); setNotice(null)
    try { setExplanation(await requestFinanceAiExplanation(month)) }
    catch { setError('AI 설명을 불러오지 못했습니다. RULE 분석은 그대로 확인할 수 있습니다.') }
    finally { setBusy(false) }
  }

  async function lockFinance() {
    setBusy(true); setError(null); setNotice(null)
    try {
      await revokeFinanceAccess()
      setAccess({ loading: false, authorized: false, error: null })
      setSummary(null); setPattern(null); setExplanation(null); setConsents([])
    } catch {
      // 서버의 접근 권한 폐기가 확인되지 않았으므로 잠금 성공으로 표시하지 않습니다.
      setError('내 금융을 잠그지 못했습니다. 연결 상태를 확인하고 다시 시도해 주세요.')
    } finally { setBusy(false) }
  }
  async function revokeDemoConsent() {
    if (!activeDemoConsent) return
    setBusy(true); setError(null); setNotice(null)
    try {
      await revokeFinanceConsent(activeDemoConsent.id)
      await loadProtected(month)
      setNotice('데모 금융데이터 연결 동의를 해제했습니다. 기존에 가져온 데이터는 자동 삭제되지 않습니다.')
    } catch { setError('데모 연결 동의를 해제하지 못했습니다.') }
    finally { setBusy(false) }
  }

  async function deleteAllFinanceData() {
    setBusy(true); setError(null); setNotice(null)
    try {
      await deleteFinanceData()
      setSummary(null); setPattern(null); setExplanation(null); setConsents([])
      setDataManagementOpen(false)
      setAccess({ loading: false, authorized: false, error: null })
    } catch (failure) {
      if (failure?.status === 403) {
        setSummary(null); setPattern(null); setExplanation(null); setConsents([])
        setDataManagementOpen(false)
        setAccess({ loading: false, authorized: false, error: null })
        setError('금융정보 보호 확인이 만료되었습니다. 다시 확인해 주세요.')
      } else {
        setError('금융데이터를 삭제하지 못했습니다. 다시 시도해 주세요.')
      }
    } finally { setBusy(false) }
  }

  if (!user) return <section className="finance-page finance-entry" aria-labelledby="finance-title">
    <div className="finance-hero-copy">
      <p className="eyebrow">PERSONAL FINANCE · 선택 기능</p>
      <h1 id="finance-title">내 금융은<br />AIRA 탐색과 분리합니다.</h1>
      <p>기업·시장 분석의 MAIN/Ask/Inspect/Relate/Assess 흐름은 그대로 두고, 내 금융은 사용자가 원할 때만 여는 별도 영역입니다.</p>
      <button type="button" className="primary-action" onClick={onLogin}>로그인하고 내 금융 열기</button>
    </div>
    <aside className="finance-boundary-note" aria-label="내 금융 이용 원칙">
      <strong>현재 연결은 데모입니다.</strong>
      <p>실제 금융기관 연결을 가장하지 않으며, 로그인 후에도 별도의 금융정보 보호 확인이 필요합니다.</p>
    </aside>
  </section>

  if (access.loading) return <section className="finance-page"><p className="status" role="status">내 금융 보호 상태를 확인하는 중입니다.</p></section>
  if (!access.authorized) return <section className="finance-page finance-gate" aria-labelledby="finance-title">
    <div className="finance-gate-copy">
      <p className="eyebrow">PERSONAL FINANCE · 보호 영역</p>
      <h1 id="finance-title">내 금융을 열기 전<br />한 번 더 확인합니다.</h1>
      <p>AIRA 로그인과 별도로 금융정보 보호 확인을 거칩니다. 확인은 짧게 유지되며, 만료되면 다시 잠깁니다.</p>
      <div className="finance-security-steps" aria-label="내 금융 보호 단계">
        <span className="complete">01 로그인</span><span className="current">02 보호 확인</span><span>03 내 금융</span>
      </div>
    </div>
    <form className="finance-reauth" onSubmit={reauthenticate}>
      <label htmlFor="finance-password">AIRA 비밀번호</label>
      <input id="finance-password" type="password" value={password} onChange={event => setPassword(event.target.value)}
        minLength="15" maxLength="72" required autoComplete="current-password" />
      <button className="primary-action" disabled={busy}>{busy ? '확인 중…' : '내 금융 열기'}</button>
      <small>이 확인으로 실제 금융기관 연결이 시작되지는 않습니다.</small>
      {(error || access.error) && <FinanceError>{error ?? '보호 상태를 확인하지 못했습니다. 다시 시도해 주세요.'}</FinanceError>}
    </form>
  </section>

  const categories = summary?.categories ?? []
  const maxCategorySpent = Math.max(1, ...categories.map(item => Number(item.spent ?? 0)))
  const totalSpentNumber = Number(summary?.totalSpent ?? 0)
  const totalBudgetNumber = Number(summary?.totalBudget ?? 0)
  const budgetUsagePercent = totalBudgetNumber > 0
    ? Math.max(0, Math.round((totalSpentNumber / totalBudgetNumber) * 100))
    : null
  const totalChange = pattern?.total
  return <section className="finance-page" aria-labelledby="finance-title">
    <header className="finance-page-head">
      <div>
        <p className="eyebrow">PERSONAL FINANCE · 사용자 소유 데이터</p>
        <h1 id="finance-title">내 금융</h1>
        <p className="finance-lead">예산과 소비 흐름을 확인하되, AIRA가 대신 판단하거나 지출을 추천하지 않습니다.</p>
      </div>
      <div className="finance-head-actions">
        <label htmlFor="finance-month">기준 월</label>
        <input id="finance-month" type="month" value={month} onChange={event => refreshMonth(event.target.value)} />
        <button type="button" className="finance-lock" onClick={lockFinance} disabled={busy}>내 금융 잠그기</button>
      </div>
    </header>

    <div className="finance-demo-strip" role="note">
      <div><strong>DEMO_IMPORT</strong><span>현재 포트폴리오에서는 AIRA 데모 금융데이터만 연결합니다.</span></div>
      {activeDemoConsent
        ? <button type="button" onClick={revokeDemoConsent} disabled={busy}>데모 연결 동의 해제</button>
        : <button type="button" className="secondary-action" onClick={connectDemo} disabled={busy}>{busy ? '처리 중…' : '데모 데이터 연결'}</button>}
    </div>

    {notice && <p className="finance-notice" role="status">{notice}</p>}
    {error && <FinanceError>{error}</FinanceError>}
    <div className="finance-overview-grid">
      <section className="finance-total" aria-labelledby="finance-total-title">
        <div className="finance-section-kicker"><span>{month}</span><span>MONTHLY FLOW</span></div>
        <h2 id="finance-total-title">이번 달 지출</h2>
        <strong className="finance-total-value">{won(summary?.totalSpent)}</strong>
        <div className="finance-total-context">
          <span>예산 {summary?.totalBudget ? won(summary.totalBudget) : '아직 설정 안 됨'}</span>
          <span>남은 예산 {summary?.totalBudget ? won(summary.totalRemaining) : '—'}</span>
        </div>
        {budgetUsagePercent != null && <div className="finance-budget-visual" data-over-budget={budgetUsagePercent > 100 ? 'true' : undefined} aria-label={`예산 사용 ${budgetUsagePercent}%`}>
          <div><span>예산 사용</span><strong>{budgetUsagePercent}%</strong></div>
          <div className="finance-budget-track" aria-hidden="true"><span style={{ width: `${Math.min(100, budgetUsagePercent)}%` }} /></div>
        </div>}
        <form className="finance-budget-form" onSubmit={saveBudget}>
          <label htmlFor="finance-budget">전체 예산 설정</label>
          <div><input id="finance-budget" type="number" min="1" step="1" inputMode="numeric" value={budget}
            onChange={event => setBudget(event.target.value)} placeholder="예: 2000000" required />
          <button className="secondary-action" disabled={busy}>저장</button></div>
        </form>
      </section>

      <aside className="finance-trust-rail" aria-label="금융정보 상태">
        <p className="eyebrow">TRUST STATUS</p>
        <dl>
          <div><dt>보호 확인</dt><dd>열림 · 단기 접근</dd></div>
          <div><dt>데이터 출처</dt><dd>{activeDemoConsent ? 'AIRA 데모' : '연결 없음'}</dd></div>
          <div><dt>분석 기준</dt><dd>{pattern?.method ?? 'RULE'}</dd></div>
          <div><dt>실제 MyData</dt><dd>연결하지 않음</dd></div>
        </dl>
        <p>금융정보는 공개 기업 Fact/Evidence와 섞이지 않습니다.</p>
        <button type="button" className="finance-data-manage" onClick={() => setDataManagementOpen(open => !open)}
          aria-expanded={dataManagementOpen} aria-controls="finance-data-management">금융데이터 관리</button>
        {dataManagementOpen && <div id="finance-data-management" className="finance-data-management">
          <strong>가져온 금융데이터 전체 삭제</strong>
          <p>AIRA 계정은 유지되고, 데모 거래·계좌·동의·예산·금융 AI 설명 기록만 삭제됩니다. 삭제 후 내 금융은 다시 잠깁니다.</p>
          <div>
            <button type="button" onClick={() => setDataManagementOpen(false)} disabled={busy}>취소</button>
            <button type="button" className="finance-delete-action" onClick={deleteAllFinanceData} disabled={busy}>
              {busy ? '삭제 중…' : '금융데이터 전체 삭제'}
            </button>
          </div>
        </div>}
      </aside>
    </div>

    <section className="finance-breakdown" aria-labelledby="finance-breakdown-title">
      <div className="finance-section-heading"><div><p className="eyebrow">SPENDING MAP</p><h2 id="finance-breakdown-title">어디에 썼는지</h2></div><p>금액이 큰 순위를 추천처럼 해석하지 않고, 선택한 달의 지출 분포만 보여줍니다.</p></div>
      {categories.length === 0 ? <p className="status">표시할 지출 데이터가 없습니다. 데모 데이터를 연결하면 예시 흐름을 볼 수 있습니다.</p> :
        <div className="finance-bars">{categories.filter(item => item.category !== 'TOTAL').map(item => {
          const width = Math.max(4, Math.round((Number(item.spent ?? 0) / maxCategorySpent) * 100))
          return <div className="finance-bar-row" key={item.category}>
            <div><strong>{CATEGORY_LABELS[item.category] ?? item.category}</strong><span>{won(item.spent)}</span></div>
            <div className="finance-bar-track" aria-hidden="true"><span style={{ width: `${width}%` }} /></div>
            {item.budget != null && <small>예산 {won(item.budget)} · 남음 {won(item.remaining)}</small>}
          </div>
        })}</div>}
    </section>

    <section className="finance-pattern" aria-labelledby="finance-pattern-title">
      <div className="finance-section-heading"><div><p className="eyebrow">RULE ANALYSIS</p><h2 id="finance-pattern-title">지난달과 달라진 흐름</h2></div><span className="finance-method-badge">{pattern?.method ?? 'RULE'}</span></div>
      {totalChange ? <div className="finance-change-lead">
        <strong>{won(totalChange.currentSpent)}</strong>
        <p>지난달 {won(totalChange.previousSpent)}에서 <b>{won(Math.abs(Number(totalChange.delta ?? 0)))}</b> {totalChange.direction === 'INCREASED' ? '늘었습니다.' : totalChange.direction === 'DECREASED' ? '줄었습니다.' : '차이가 없습니다.'}</p>
      </div> : <p className="status">비교할 소비 흐름이 없습니다.</p>}
      <div className="finance-change-list">{pattern?.categories?.map(item => <div key={item.scope}>
        <span>{CATEGORY_LABELS[item.scope] ?? item.scope}</span><strong>{won(item.currentSpent)}</strong>
        <small>{item.unknownReason ? '비교 기준 부족' : `${item.direction === 'INCREASED' ? '↑' : item.direction === 'DECREASED' ? '↓' : '–'} ${won(Math.abs(Number(item.delta ?? 0)))}`}</small>
      </div>)}</div>
    </section>

    <section className="finance-explain" aria-labelledby="finance-explain-title">
      <div className="finance-section-heading"><div><p className="eyebrow">OPTIONAL EXPLANATION</p><h2 id="finance-explain-title">AI 설명</h2></div>
        <button type="button" className="secondary-action" onClick={explain} disabled={busy}>{busy ? '확인 중…' : '설명 요청'}</button></div>
      <p>AI는 이미 계산된 월간 집계와 변화만 설명합니다. 예산을 대신 정하거나 소비 결정을 추천하지 않습니다.</p>
      {explanation && <div className="finance-explanation-result">
        <div><strong>{explanation.method === 'AI' ? 'AI 설명' : 'RULE 유지'}</strong>
          {explanation.providerKey && <span>{explanation.providerKey} · {explanation.modelKey}</span>}</div>
        <p>{explanation.explanation ?? '현재 AI 제공자를 사용할 수 없어 RULE 분석만 유지합니다.'}</p>
        {explanation.limitations?.length > 0 && <ul>{explanation.limitations.map(item => <li key={item}>{item}</li>)}</ul>}
      </div>}
    </section>
  </section>
}
