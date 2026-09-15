import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import HistoricalAssessment from './HistoricalAssessment.jsx'
import CanonicalExplorer from './CanonicalExplorer.jsx'
import { getCompanies, getCompanyEvents, getFinancialFacts, getFinancialPeriods } from './api/companyApi.js'
import { getEventDetail, getRecentEvents } from './api/eventApi.js'
import { getOfficialEvidence } from './api/evidenceApi.js'
import { addInterest, confirmPasswordReset, confirmRecoveryEmail, disableInterestAlert, enableInterestAlert, getCurrentUser, getInterests, login, logout, removeInterest, requestPasswordReset, requestRecoveryEmailVerification, signup } from './api/authApi.js'
import { getOrCreateBriefing } from './api/briefingApi.js'
import { alertEmptyMessage } from './alertEmptyState.js'
import { getAlert, reconcileAlerts } from './api/alertApi.js'

const LABELS = { REVENUE: '매출', OPERATING_INCOME: '영업이익' }
const EVIDENCE_TYPE_LABELS = { ARTICLE: '기사', DISCLOSURE: '공시', IR: 'IR 자료', PRESS_RELEASE: '보도자료', OFFICIAL_DATA: '공식 데이터', OTHER: '기타' }
const SOURCE_TYPE_LABELS = { NEWS: '뉴스', REGULATOR: '감독기관', EXCHANGE: '거래소', COMPANY_IR: '기업 IR', GOVERNMENT: '정부기관', OTHER: '기타' }
const EVENT_TYPE_LABELS = { EARNINGS: '실적', DISCLOSURE: '공시', BUSINESS: '사업', GOVERNANCE: '지배구조', POLICY_REGULATION: '정책·규제', RISK: '리스크', MARKET: '시장' }
const IMPORTANCE_LABELS = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음', CRITICAL: '매우 높음' }
const CONFIDENCE_LABELS = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음' }
const ASSESSMENT_METHOD_LABELS = { RULE: '규칙 기반', AI: 'AI 기반', HYBRID: '혼합', HUMAN_REVIEW: '사람 검토' }

function messageFor(error, subject) {
  if (error?.status === 400) return `${subject} 요청을 확인해 주세요.`
  if (error?.status === 404) return `이용 가능한 ${subject}을 찾지 못했습니다.`
  if (error?.status === 409) return subject === '관심회사' ? '이미 저장된 관심회사입니다.' : '공식 근거를 일관되게 확인할 수 없습니다.'
  return '서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

function Status({ children, busy = false }) {
  return <p className="status" role={busy ? 'status' : 'note'} aria-live="polite">{children}</p>
}

function ErrorState({ error, subject, retry }) {
  return <div className="state-message" role="alert"><p>{messageFor(error, subject)}</p>
    <button type="button" className="secondary-action" onClick={retry}>다시 시도</button></div>
}

function formatDateTime(value) {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

function AuthPanel({ mode, setMode, close, authenticated }) {
  const [nickname, setNickname] = useState('')
  const [password, setPassword] = useState('')
  const [visible, setVisible] = useState(false)
  const [state, setState] = useState({ loading: false, error: null, notice: null })

  async function submit(event) {
    event.preventDefault()
    setState({ loading: true, error: null, notice: null })
    try {
      if (mode === 'signup') {
        await signup(nickname, password)
        setPassword('')
        setMode('login')
        setState({ loading: false, error: null, notice: '회원가입이 완료됐습니다. 로그인해 주세요.' })
      } else {
        await login(nickname, password)
        authenticated(await getCurrentUser())
      }
    } catch (error) {
      let copy = '요청을 처리하지 못했습니다. 다시 시도해 주세요.'
      if (error.code === 'NICKNAME_ALREADY_EXISTS') copy = '사용할 수 없는 닉네임입니다.'
      else if (error.code === 'AUTHENTICATION_FAILED') copy = '닉네임 또는 비밀번호를 확인해 주세요.'
      else if (error.code === 'INVALID_REQUEST') {
        copy = error.fields?.[0]?.field === 'password'
          ? '비밀번호는 15자 이상 72자 이하로 입력해 주세요.'
          : '닉네임과 비밀번호 입력값을 확인해 주세요.'
      }
      setState({ loading: false, error: copy, notice: null })
    }
  }

  return <div className="auth-overlay" role="presentation">
    <section className="auth-panel" role="dialog" aria-modal="true" aria-labelledby="auth-title">
      <button type="button" className="close-button" onClick={close} aria-label="인증 창 닫기">×</button>
      <p className="eyebrow">AIRA ACCOUNT</p>
      <h2 id="auth-title">{mode === 'signup' ? '회원가입' : '로그인'}</h2>
      <p className="auth-intro">{mode === 'signup' ? '관심회사를 저장하고 다음 방문에도 이어서 확인하세요.' : '저장한 관심회사를 다시 확인하세요.'}</p>
      <div className="auth-tabs" role="tablist" aria-label="계정 메뉴">
        <button type="button" role="tab" aria-selected={mode === 'login'} onClick={() => setMode('login')}>로그인</button>
        <button type="button" role="tab" aria-selected={mode === 'signup'} onClick={() => setMode('signup')}>회원가입</button>
      </div>
      <form onSubmit={submit}>
        <label htmlFor="nickname">닉네임</label>
        <input id="nickname" value={nickname} onChange={event => setNickname(event.target.value)}
          minLength="3" maxLength="20" required autoComplete="username"
          aria-describedby={mode === 'signup' ? 'nickname-help' : undefined} />
        {mode === 'signup' && <small id="nickname-help">한글 또는 영문으로 시작하는 3–20자, 숫자 조합 가능</small>}
        <label htmlFor="password">비밀번호</label>
        <div className="password-field">
          <input id="password" type={visible ? 'text' : 'password'} value={password}
            onChange={event => setPassword(event.target.value)} minLength="15" maxLength="72" required
            autoComplete={mode === 'signup' ? 'new-password' : 'current-password'} />
          <button type="button" onClick={() => setVisible(value => !value)}
            aria-label={visible ? '비밀번호 숨기기' : '비밀번호 표시'}>{visible ? '숨기기' : '표시'}</button>
        </div>
        {mode === 'signup' && <small>15자 이상 72자 이하</small>}
        {state.notice && <p className="form-notice" role="status">{state.notice}</p>}
        {state.error && <p className="form-error" role="alert">{state.error}</p>}
        <button className="primary-action submit-action" disabled={state.loading}>
          {state.loading ? '처리 중…' : mode === 'signup' ? '회원가입' : '로그인'}
        </button>
      </form>
      {mode === 'login' && <button type="button" className="text-action" onClick={() => setMode('forgot')}>비밀번호를 잊으셨나요?</button>}
    </section>
  </div>
}

function RecoveryPanel({ mode, token, setMode, close, completeLink }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [state, setState] = useState({ loading: false, error: null, notice: null })

  async function submit(event) {
    event.preventDefault()
    setState({ loading: true, error: null, notice: null })
    try {
      if (mode === 'forgot') {
        await requestPasswordReset(email)
        setEmail('')
        setState({ loading: false, error: null, notice: '입력한 주소가 등록되어 있다면 비밀번호 재설정 안내를 보냈습니다.' })
      } else if (mode === 'reset') {
        if (password !== confirmation) {
          setState({ loading: false, error: '새 비밀번호가 서로 일치하지 않습니다.', notice: null })
          return
        }
        await confirmPasswordReset(token, password)
        setPassword('')
        setConfirmation('')
        completeLink()
        setState({ loading: false, error: null, notice: '비밀번호를 변경하고 기존 로그인 세션을 종료했습니다. 새 비밀번호로 로그인해 주세요.' })
      } else {
        await confirmRecoveryEmail(token)
        completeLink()
        setState({ loading: false, error: null, notice: '복구 이메일 확인 요청을 처리했습니다.' })
      }
    } catch (error) {
      const invalid = mode === 'reset' && error.code === 'INVALID_PASSWORD_RESET_TOKEN'
      setState({ loading: false, error: invalid
        ? '재설정 링크가 유효하지 않거나 만료되었습니다. 새 링크를 요청해 주세요.'
        : '요청을 처리하지 못했습니다. 입력값을 확인하고 다시 시도해 주세요.', notice: null })
    }
  }

  const reset = mode === 'reset'
  const verify = mode === 'recovery-confirm'
  const title = reset ? '새 비밀번호 설정' : verify ? '복구 이메일 확인' : '비밀번호 재설정'
  return <div className="auth-overlay" role="presentation">
    <section className="auth-panel" role="dialog" aria-modal="true" aria-labelledby="recovery-title">
      <button type="button" className="close-button" onClick={close} aria-label="계정 복구 창 닫기">×</button>
      <p className="eyebrow">AIRA ACCOUNT RECOVERY</p>
      <h2 id="recovery-title">{title}</h2>
      <p className="auth-intro">{reset ? '링크는 30분 동안 한 번만 사용할 수 있습니다.' : verify ? '이 링크로 복구 이메일 확인을 완료합니다.' : '계정에 등록한 복구 이메일을 입력해 주세요.'}</p>
      <form onSubmit={submit}>
        {mode === 'forgot' && <><label htmlFor="reset-email">복구 이메일</label><input id="reset-email" type="email" value={email} onChange={event => setEmail(event.target.value)} maxLength="254" required autoComplete="email" autoFocus /></>}
        {reset && <>
          <label htmlFor="new-password">새 비밀번호</label>
          <input id="new-password" type="password" value={password} onChange={event => setPassword(event.target.value)} minLength="15" maxLength="72" required autoComplete="new-password" autoFocus />
          <small>15자 이상 72자 이하</small>
          <label htmlFor="confirm-password">새 비밀번호 확인</label>
          <input id="confirm-password" type="password" value={confirmation} onChange={event => setConfirmation(event.target.value)} minLength="15" maxLength="72" required autoComplete="new-password" />
        </>}
        {state.notice && <p className="form-notice" role="status">{state.notice}</p>}
        {state.error && <p className="form-error" role="alert">{state.error}</p>}
        {!state.notice && <button className="primary-action submit-action" disabled={state.loading}>{state.loading ? '처리 중…' : reset ? '비밀번호 변경' : verify ? '이메일 확인' : '재설정 안내 요청'}</button>}
      </form>
      {state.notice && (reset
        ? <button type="button" className="primary-action submit-action" onClick={() => setMode('login')}>로그인으로 이동</button>
        : <button type="button" className="secondary-action submit-action" onClick={close}>닫기</button>)}
      {mode === 'forgot' && <button type="button" className="text-action" onClick={() => setMode('login')}>로그인으로 돌아가기</button>}
    </section>
  </div>
}

function RecoveryEmailSettings({ close }) {
  const [email, setEmail] = useState('')
  const [state, setState] = useState({ loading: false, error: null, notice: null })
  async function submit(event) {
    event.preventDefault()
    setState({ loading: true, error: null, notice: null })
    try {
      await requestRecoveryEmailVerification(email)
      setEmail('')
      setState({ loading: false, error: null, notice: '확인 메일을 보냈습니다. 30분 안에 메일의 링크를 열어 주세요.' })
    } catch {
      setState({ loading: false, error: '확인 메일을 보내지 못했습니다. 주소와 메일 전송 설정을 확인해 주세요.', notice: null })
    }
  }
  return <div className="auth-overlay" role="presentation">
    <section id="account-recovery" className="auth-panel recovery-section" role="dialog" aria-modal="true" aria-labelledby="account-recovery-title" onKeyDown={event => { if (event.key === 'Escape') close() }}>
      <button type="button" className="close-button" onClick={close} aria-label="계정 설정 닫기" autoFocus>×</button>
      <div className="section-heading"><span>ACCOUNT</span><h2 id="account-recovery-title">계정 복구 이메일</h2></div>
      <p className="status">비밀번호를 잊었을 때 사용할 이메일을 확인합니다. 현재 주소는 개인정보 보호를 위해 화면에 표시하지 않습니다.</p>
      <form className="recovery-email-form" onSubmit={submit}>
        <label htmlFor="recovery-email">복구 이메일</label>
        <input id="recovery-email" type="email" value={email} onChange={event => setEmail(event.target.value)} maxLength="254" required autoComplete="email" />
        <button className="secondary-action" disabled={state.loading}>{state.loading ? '처리 중…' : '확인 메일 보내기'}</button>
      </form>
      {state.notice && <p className="form-notice" role="status">{state.notice}</p>}
      {state.error && <p className="form-error" role="alert">{state.error}</p>}
    </section>
  </div>
}

function initialRecoveryEntry() {
  if (typeof window === 'undefined') return { mode: null, token: '' }
  const token = new URLSearchParams(window.location.search).get('token') ?? ''
  if (window.location.pathname === '/password-reset/confirm') return { mode: 'reset', token }
  if (window.location.pathname === '/recovery-email/confirm') return { mode: 'recovery-confirm', token }
  return { mode: null, token: '' }
}

export default function App() {
  const [search, setSearch] = useState('')
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [historicalId, setHistoricalId] = useState(null)
  const requestVersions = useRef({})
  const beginRequest = useCallback(key => {
    const version = (requestVersions.current[key] ?? 0) + 1
    requestVersions.current[key] = version
    return () => requestVersions.current[key] === version
  }, [])
  const [recoveryEntry, setRecoveryEntry] = useState(initialRecoveryEntry)
  const [session, setSession] = useState({ loading: true, user: null, error: null, notice: null })
  const [authMode, setAuthMode] = useState(recoveryEntry.mode)
  const [companiesState, setCompaniesState] = useState({ loading: true, data: [], error: null })
  const [exploreEventsState, setExploreEventsState] = useState({ loading: true, data: [], error: null })
  const [eventDetailState, setEventDetailState] = useState({ loading: false, data: null, error: null, eventId: null, contextCompanyId: null })
  const [officialEvidenceState, setOfficialEvidenceState] = useState({ loading: false, data: null, error: null, evidenceId: null })
  const [selectedCompany, setSelectedCompany] = useState(null)
  const [periodsState, setPeriodsState] = useState({ loading: false, data: [], error: null })
  const [selectedPeriod, setSelectedPeriod] = useState(null)
  const [factsState, setFactsState] = useState({ loading: false, data: [], error: null })
  const [eventsState, setEventsState] = useState({ loading: false, data: [], error: null })
  const [interestsState, setInterestsState] = useState({ loading: false, data: [], error: null })
  const [interestAction, setInterestAction] = useState({ loading: false, error: null, retry: null })
  const [briefingState, setBriefingState] = useState({ loading: false, data: null, error: null })
  const [alertsState, setAlertsState] = useState({ loading: false, data: [], emptyReason: null, error: null })
  const [alertDetailState, setAlertDetailState] = useState({ loading: false, data: null, error: null, alertId: null })
  const [pendingInsightTarget, setPendingInsightTarget] = useState(null)
  const [highlightedEventId, setHighlightedEventId] = useState(null)
  const exploring = typeof window !== 'undefined' && window.location.pathname === '/explore'
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [workflowContext, setWorkflowContext] = useState({ step: exploring ? 'MAIN' : null, hasTarget: false, hasEvent: false })

  const becomeAnonymous = useCallback((notice = null) => {
    for (const key of ['session', 'interests', 'briefing', 'alerts', 'alertDetail']) requestVersions.current[key] = (requestVersions.current[key] ?? 0) + 1
    setSettingsOpen(false)
    setSession({ loading: false, user: null, error: null, notice })
    setInterestsState({ loading: false, data: [], error: null })
    setBriefingState({ loading: false, data: null, error: null })
    setAlertsState({ loading: false, data: [], emptyReason: null, error: null })
    setAlertDetailState({ loading: false, data: null, error: null, alertId: null })
  }, [])

  const loadSession = useCallback(() => {
    const current = beginRequest('session')
    setSession(current => ({ ...current, loading: true, error: null }))
    getCurrentUser().then(user => current() && setSession({ loading: false, user, error: null, notice: null }))
      .catch(error => current() && (error.status === 401 || error.status === 403
        ? becomeAnonymous()
        : setSession({ loading: false, user: null, error, notice: null })))
  }, [becomeAnonymous, beginRequest])

  const loadCompanies = useCallback(() => {
    const controller = new AbortController()
    setCompaniesState({ loading: true, data: [], error: null })
    getCompanies(controller.signal)
      .then(body => setCompaniesState({ loading: false, data: body.companies ?? [], error: null }))
      .catch(error => error.name !== 'AbortError' && setCompaniesState({ loading: false, data: [], error }))
    return () => controller.abort()
  }, [])

  useEffect(loadSession, [loadSession])
  useEffect(loadCompanies, [loadCompanies])

  const loadExploreEvents = useCallback(() => {
    const controller = new AbortController()
    setExploreEventsState({ loading: true, data: [], error: null })
    getRecentEvents(controller.signal)
      .then(body => setExploreEventsState({ loading: false, data: body.events ?? [], error: null }))
      .catch(error => error.name !== 'AbortError'
        && setExploreEventsState({ loading: false, data: [], error }))
    return () => controller.abort()
  }, [])
  useEffect(loadExploreEvents, [loadExploreEvents])

  const openEventDetail = useCallback((eventId, contextCompanyId) => {
    const current = beginRequest('event')
    const controller = new AbortController()
    setEventDetailState({ loading: true, data: null, error: null, eventId, contextCompanyId })
    getEventDetail(eventId, controller.signal)
      .then(data => current() && setEventDetailState({ loading: false, data, error: null, eventId, contextCompanyId }))
      .catch(error => current() && error.name !== 'AbortError'
        && setEventDetailState({ loading: false, data: null, error, eventId, contextCompanyId }))
    return () => controller.abort()
  }, [beginRequest])

  const openOfficialEvidence = useCallback((evidenceId) => {
    const current = beginRequest('evidence')
    const controller = new AbortController()
    setOfficialEvidenceState({ loading: true, data: null, error: null, evidenceId })
    getOfficialEvidence(evidenceId, controller.signal)
      .then(data => current() && setOfficialEvidenceState({ loading: false, data, error: null, evidenceId }))
      .catch(error => current() && error.name !== 'AbortError'
        && setOfficialEvidenceState({ loading: false, data: null, error, evidenceId }))
    return () => controller.abort()
  }, [beginRequest])

  const openAlertDetail = useCallback((alertId) => {
    const current = beginRequest('alertDetail')
    setAlertDetailState({ loading: true, data: null, error: null, alertId })
    getAlert(alertId)
      .then(data => current() && setAlertDetailState({ loading: false, data, error: null, alertId }))
      .catch(error => current() && (error.status === 401 || error.status === 403
        ? becomeAnonymous()
        : setAlertDetailState({ loading: false, data: null, error, alertId })))
  }, [becomeAnonymous, beginRequest])

  const loadInterests = useCallback(() => {
    if (!session.user) return
    const current = beginRequest('interests')
    setInterestsState({ loading: true, data: [], error: null })
    getInterests().then(data => current() && setInterestsState({ loading: false, data: data ?? [], error: null }))
      .catch(error => current() && (error.status === 401 || error.status === 403
        ? becomeAnonymous('세션이 만료되었습니다. 공개 정보는 계속 볼 수 있습니다.')
        : setInterestsState({ loading: false, data: [], error })))
  }, [session.user, becomeAnonymous, beginRequest])

  useEffect(loadInterests, [loadInterests])

  const loadBriefing = useCallback(() => {
    if (!session.user) return
    const current = beginRequest('briefing')
    setBriefingState({ loading: true, data: null, error: null })
    getOrCreateBriefing().then(data => current() && setBriefingState({ loading: false, data, error: null }))
      .catch(error => current() && (error.status === 401 || error.status === 403
        ? becomeAnonymous('세션이 만료되었습니다. 공개 정보는 계속 볼 수 있습니다.')
        : setBriefingState({ loading: false, data: null, error })))
  }, [session.user, becomeAnonymous, beginRequest])

  useEffect(() => {
    if (session.user && !interestsState.loading && !interestsState.error) loadBriefing()
  }, [session.user, interestsState.loading, interestsState.error, interestsState.data, loadBriefing])

  const selectCompany = useCallback((company, eventId = null) => {
    const current = beginRequest('company')
    setSelectedCompany(company)
    setHighlightedEventId(eventId)
    setPendingInsightTarget(eventId ? { companyId: company.companyId, eventId } : null)
    setSelectedPeriod(null)
    setFactsState({ loading: false, data: [], error: null })
    setEventsState({ loading: true, data: [], error: null })
    setPeriodsState({ loading: true, data: [], error: null })
    getFinancialPeriods(company.companyId).then(body => {
      if (!current()) return
      const periods = body.periods ?? []
      setPeriodsState({ loading: false, data: periods, error: null })
      if (periods.length === 1) setSelectedPeriod(periods[0])
    }).catch(error => current() && setPeriodsState({ loading: false, data: [], error }))
    getCompanyEvents(company.companyId)
      .then(body => current() && setEventsState({ loading: false, data: body.events ?? [], error: null }))
      .catch(error => current() && setEventsState({ loading: false, data: [], error }))
  }, [beginRequest])

  useEffect(() => {
    if (!pendingInsightTarget || eventsState.loading || selectedCompany?.companyId !== pendingInsightTarget.companyId) return
    const target = document.getElementById(`event-${pendingInsightTarget.eventId}`)
      ?? document.getElementById('events-title')
    if (target) {
      target.focus({ preventScroll: true })
      target.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
      setPendingInsightTarget(null)
    }
  }, [eventsState.loading, eventsState.data, pendingInsightTarget, selectedCompany])

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
    factsState.data.forEach(fact => { if (!grouped.has(fact.evidenceId)) grouped.set(fact.evidenceId, fact) })
    return [...grouped.values()]
  }, [factsState.data])
  const interested = selectedCompany && interestsState.data.some(item => item.entityId === selectedCompany.companyId)
  const selectedInterest = selectedCompany && interestsState.data.find(item => item.entityId === selectedCompany.companyId)

  const loadAlerts = useCallback(() => {
    if (!session.user) return
    const current = beginRequest('alerts')
    setAlertsState({ loading: true, data: [], emptyReason: null, error: null })
    reconcileAlerts().then(body => current() && setAlertsState({ loading: false, data: body.alerts ?? [], emptyReason: body.emptyReason ?? null, error: null }))
      .catch(error => current() && (error.status === 401 || error.status === 403 ? becomeAnonymous() : setAlertsState({ loading: false, data: [], emptyReason: null, error })))
  }, [session.user, becomeAnonymous, beginRequest])

  useEffect(() => { if (session.user && !interestsState.loading) loadAlerts() }, [session.user, interestsState.loading, interestsState.data, loadAlerts])

  async function changeInterest(remove = false, entityId = selectedCompany?.companyId) {
    if (!entityId) return
    if (!session.user) { setAuthMode('login'); return }
    const retry = () => changeInterest(remove, entityId)
    setInterestAction({ loading: true, error: null, retry: null })
    try {
      if (remove) await removeInterest(entityId)
      else await addInterest(entityId)
      setInterestAction({ loading: false, error: null, retry: null })
      loadInterests()
    } catch (error) {
      if (error.status === 401 || error.status === 403) becomeAnonymous('세션이 만료되었습니다. 다시 로그인하면 관심회사를 저장할 수 있습니다.')
      else setInterestAction({ loading: false, error, retry })
    }
  }

  async function changeAlertSetting(enabled, entityId = selectedCompany?.companyId) {
    if (!entityId) return
    setInterestAction({ loading: true, error: null, retry: null })
    try {
      if (enabled) await enableInterestAlert(entityId)
      else await disableInterestAlert(entityId)
      setInterestAction({ loading: false, error: null, retry: null }); loadInterests()
    } catch (error) {
      if (error.status === 401 || error.status === 403) becomeAnonymous('세션이 만료되었습니다. 다시 로그인해 주세요.')
      else setInterestAction({ loading: false, error, retry: () => changeAlertSetting(enabled, entityId) })
    }
  }

  async function performLogout() {
    setSession(current => ({ ...current, loading: true, error: null }))
    try { await logout(); becomeAnonymous('로그아웃되었습니다. 공개 정보는 계속 볼 수 있습니다.') }
    catch (error) { setSession(current => ({ ...current, loading: false, error })) }
  }

  useEffect(() => {
    if (!exploring) return undefined
    const syncWorkflow = event => setWorkflowContext(current => typeof event.detail === 'string'
      ? { ...current, step: event.detail }
      : event.detail)
    window.addEventListener('aira-workflow-context', syncWorkflow)
    return () => window.removeEventListener('aira-workflow-context', syncWorkflow)
  }, [exploring])

  function workflowStepDisabled(step) {
    if (!exploring || step === 'MAIN') return false
    if (!workflowContext.hasTarget) return true
    return step === 'Assess' && !workflowContext.hasEvent
  }

  function chooseWorkflowStep(step) {
    if (exploring) window.dispatchEvent(new CustomEvent('aira-workflow-step', { detail: step }))
  }

  return <>
    <header className="site-header"><a className="brand" href="/" aria-label="AIRA 홈"><img src="/aira-logo.png" alt="AIRA" /></a>
      <form role="search" onSubmit={event => { event.preventDefault(); window.location.assign(`/explore?q=${encodeURIComponent(search.trim())}`) }}>
        <label htmlFor="company-search">검색</label><input id="company-search" type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="기업명·종목명·종목코드 검색" />
      </form>
      <nav className="account-nav" aria-label="계정 메뉴">
        {session.loading && <span className="session-label">세션 확인 중…</span>}
        {!session.loading && !session.user && <><button onClick={() => setAuthMode('login')}>관심회사</button><button onClick={() => setAuthMode('login')}>브리핑</button><button onClick={() => setAuthMode('login')}>알림</button><button type="button" onClick={() => setAuthMode('login')}>로그인</button><button type="button" className="nav-signup" onClick={() => setAuthMode('signup')}>회원가입</button></>}
        {!session.loading && session.user && <><a href={exploring ? '/#my-interests' : '#my-interests'}>관심회사</a><a href={exploring ? '/#my-briefing' : '#my-briefing'}>브리핑</a><a href={exploring ? '/#my-alerts' : '#my-alerts'}>알림</a><button type="button" aria-label="계정 설정 열기" onClick={() => setSettingsOpen(true)}>사용자 {session.user.nickname}</button><button type="button" onClick={performLogout}>로그아웃</button></>}
      </nav>
    </header>
    <aside className={`workflow-sidebar ${sidebarCollapsed ? 'collapsed' : ''}`} aria-label="탐색 단계">
      <button type="button" className="sidebar-toggle" aria-label={sidebarCollapsed ? '사이드바 펼치기' : '사이드바 접기'}
        onClick={() => setSidebarCollapsed(value => !value)}>{sidebarCollapsed ? '›' : '‹'}</button>
      <nav aria-label="AIRA 흐름">
        {[['MAIN', '메인', 'MAIN'], ['Ask', '질문', 'A'], ['Inspect', '살피기', 'I'],
          ['Relate', '잇기', 'R'], ['Assess', '판단', 'A']].map(([step, korean, code]) => exploring
          ? <button key={step} type="button" className={step === 'MAIN' ? 'workflow-main' : ''} data-workflow-step={step} aria-label={`${step} ${korean}`}
              aria-current={workflowContext.step === step ? 'step' : undefined} disabled={workflowStepDisabled(step)}
              onClick={() => chooseWorkflowStep(step)}><span className="workflow-code" aria-hidden="true">{sidebarCollapsed ? code : step}</span>
              <span className="workflow-label" aria-hidden="true">{korean}</span></button>
          : <a key={step} className={step === 'MAIN' ? 'workflow-main' : ''} data-workflow-step={step}
              href={step === 'MAIN' ? '#main' : '/explore'} aria-label={`${step} ${korean}`}>
              <span className="workflow-code" aria-hidden="true">{sidebarCollapsed ? code : step}</span>
              <span className="workflow-label" aria-hidden="true">{korean}</span></a>)}
      </nav>
      <button className="settings-action" aria-label="설정" onClick={() => { if (session.user) setSettingsOpen(true); else setAuthMode('login') }}>
        <span aria-hidden="true">{sidebarCollapsed ? '⚙' : '설정'}</span></button>
    </aside>
    {session.notice && <div className="session-notice" role="status">{session.notice}</div>}
    {session.error && <div className="session-notice error" role="alert">계정 요청을 처리하지 못했습니다. <button onClick={session.user ? performLogout : loadSession}>다시 시도</button></div>}
    <main id="main">
      {exploring ? <CanonicalExplorer embedded interest={{
        user: session.user, items: interestsState.data, loading: interestAction.loading, error: interestAction.error, retry: interestAction.retry,
        onLogin: () => setAuthMode('login'), onSave: entityId => changeInterest(false, entityId),
        onRemove: entityId => changeInterest(true, entityId), onAlert: (entityId, enabled) => changeAlertSetting(enabled, entityId),
      }} /> : <>
      <div className="home-orientation">
        <section className="intro" aria-labelledby="page-title"><p className="eyebrow">PUBLIC COMPANY FINANCIALS</p><h1 id="page-title">공식 데이터와 근거를<br />함께 확인하세요.</h1><p className="intro-copy">검색에서 정확한 기업·종목을 고른 뒤 Ask에서 관점을 정하고, Inspect·Relate·Assess를 필요한 순서로 확인합니다.</p><a className="primary-action" href="/explore">단계별 탐색 시작</a></section>
      </div>
      {historicalId && <HistoricalAssessment assessmentId={historicalId} openEvidence={openOfficialEvidence} />}

      {session.user && <section id="my-interests" className="content-section interests-section" aria-labelledby="interests-title">
        <div className="section-heading"><span>MY</span><h2 id="interests-title">내 관심회사</h2></div>
        {interestsState.loading && <Status busy>관심회사를 불러오는 중입니다.</Status>}
        {interestsState.error && <ErrorState error={interestsState.error} subject="관심회사" retry={loadInterests} />}
        {!interestsState.loading && !interestsState.error && interestsState.data.length === 0 && <div className="personalization-empty"><Status>아직 저장한 관심회사가 없습니다. 회사를 탐색하고 관심회사로 저장해 보세요.</Status><a className="secondary-action" href="/explore">회사 탐색하기</a></div>}
        <div className="interest-list">{interestsState.data.map(item => <a key={item.entityId} href={`/explore?q=${encodeURIComponent(item.canonicalName)}`}><strong>{item.canonicalName}</strong><span>단계별 탐색 이어가기 →</span></a>)}</div>
      </section>}

      {session.user && <section id="my-briefing" className="content-section briefing-section" aria-labelledby="briefing-title">
        <div className="section-heading"><span>BRIEFING</span><h2 id="briefing-title">내 브리핑</h2></div>
        {briefingState.loading && <Status busy>관심회사에서 확인된 내용을 모으는 중입니다.</Status>}
        {briefingState.error && <ErrorState error={briefingState.error} subject="브리핑" retry={loadBriefing} />}
        {!briefingState.loading && !briefingState.error && briefingState.data?.items?.length === 0 &&
          <div className="personalization-empty"><Status>{briefingState.data.emptyReason === 'NO_INTERESTS' ? '아직 관심 회사가 없습니다.' : '이 브리핑 기간에 새로 정리된 변화가 없습니다.'}</Status>{briefingState.data.emptyReason === 'NO_INTERESTS' && <p>계속 확인하고 싶은 회사를 저장하면 이후 새로 정리된 변화를 브리핑에서 모아볼 수 있습니다.</p>}<a className="secondary-action" href="/explore">관심회사 살펴보기</a></div>}
        {briefingState.data?.periodStart && briefingState.data?.periodEnd && <p className="insight-time">정리 기간 {formatDateTime(briefingState.data.periodStart)} — {formatDateTime(briefingState.data.periodEnd)}</p>}
        {briefingState.data?.generatedAt && <p className="insight-time">브리핑 생성 {formatDateTime(briefingState.data.generatedAt)}</p>}
        <div className="briefing-list">{briefingState.data?.items?.map(item => <article className="briefing-card" key={item.assessmentId}>
          <div className="briefing-main"><p className="eyebrow">관련 회사: {item.companies.map(company => company.companyName).join(' · ')}</p><h3>{item.eventTitle}</h3>
            <p className="insight-reason">관심회사로 저장한 회사의 AIRA 분석입니다.</p>
            <p className="event-meta">{EVENT_TYPE_LABELS[item.eventType] ?? item.eventType} · {item.occurredAt?.slice(0, 10) ?? '발생시각 미상'}</p>
            <div className="assessment"><h4>확인할 의미</h4><p>{item.summary}</p>
              <h4>아직 확인할 점</h4><p>{item.uncertainty}</p></div>
          </div>
          <aside className="briefing-side" aria-label="브리핑 근거와 이동"><p className="insight-identity">당시 판단 · 판단 식별자 {item.assessmentId} · {item.analysisVersion}</p>
            <button type="button" className="secondary-action" onClick={() => setHistoricalId(item.assessmentId)}>당시 판단 보기</button>
            <p className="briefing-current-note">사건 상세에서는 현재 상태와 현재 판단을 표시합니다.</p><div className="insight-actions"><button type="button" className="primary-action" onClick={() => openEventDetail(item.eventId, null)}>사건 상세 보기</button></div>
            {item.evidence?.map(reference => <div className="evidence-reference" key={reference.evidenceId}>
              <a className="official-evidence-action" href={reference.originalUrl} target="_blank" rel="noopener noreferrer">{reference.sourceName} 공식 근거 원문 <span aria-hidden="true">↗</span></a>
              <span>공시 접수번호 {reference.externalId}</span>
              <button type="button" className="secondary-action" onClick={() => openOfficialEvidence(reference.evidenceId)}>공식 자료 상세</button>
            </div>)}
          </aside>
        </article>)}</div>
      </section>}

      {session.user && <section id="my-alerts" className="content-section alert-section" aria-labelledby="alerts-title">
        <div className="section-heading"><span>IN APP</span><h2 id="alerts-title">관심회사 알림</h2></div>
        {alertsState.loading && <Status busy>새로 확인된 내용을 살펴보는 중입니다.</Status>}
        {alertsState.error && <ErrorState error={alertsState.error} subject="알림" retry={loadAlerts} />}
        {!alertsState.loading && !alertsState.error && alertsState.data.length === 0 && <div className="personalization-empty"><Status>{alertEmptyMessage(interestsState.data, alertsState.data, alertsState.emptyReason)}</Status><a className="secondary-action" href="/explore">회사와 알림 설정 보기</a></div>}
        <div className="alert-list">{alertsState.data.map(item => <article className="alert-card" key={item.alertId}>
          <div className="alert-main"><p className="eyebrow">관련 회사: {item.companies.map(company => company.companyName).join(' · ')}</p><h3>{item.eventTitle}</h3><p>{item.summary}</p>
            <p className="insight-reason">앱 알림을 켠 관심회사에 새로운 AIRA 분석이 준비되었습니다.</p>
            <h4>아직 확인할 점</h4><p>{item.uncertainty}</p>
          </div>
          <aside className="alert-side" aria-label="알림 시각과 이동"><div className="alert-times">
            <p className="insight-time">사건 발생 {formatDateTime(item.occurredAt) ?? '발생시각 미상'}</p>
            {item.completedAt && <p className="insight-time">판단 완료 {formatDateTime(item.completedAt)}</p>}
            {item.sentAt && <p className="insight-time">알림 전달 {formatDateTime(item.sentAt)}</p>}
          </div>
          <div className="insight-actions"><button type="button" className="primary-action" onClick={() => openAlertDetail(item.alertId)}>알림 상세 보기</button>
            <button type="button" className="secondary-action" onClick={() => openEventDetail(item.eventId, null)}>현재 사건 보기</button></div></aside>
        </article>)}</div>
        {alertDetailState.loading && <Status busy>정확한 알림 기록을 불러오는 중입니다.</Status>}
        {alertDetailState.error && <ErrorState error={alertDetailState.error} subject="알림 상세" retry={() => openAlertDetail(alertDetailState.alertId)} />}
        {alertDetailState.data && <article className="event-detail" aria-labelledby="alert-detail-title">
          <h3 id="alert-detail-title">정확한 알림 상세</h3>
          <p className="eyebrow">관련 회사: {alertDetailState.data.companies.map(company => company.companyName).join(' · ')}</p>
          <h4>{alertDetailState.data.eventTitle}</h4><p>{alertDetailState.data.summary}</p>
          <p>알림 전달 당시 판단</p><button onClick={() => setHistoricalId(alertDetailState.data.assessmentId)}>당시 판단 보기</button>
          <dl className="fact-list">
            <div className="fact-row"><dt>판단 식별자</dt><dd>{alertDetailState.data.assessmentId}</dd></div>
            <div className="fact-row"><dt>분석 버전</dt><dd>{alertDetailState.data.analysisVersion}</dd></div>
            <div className="fact-row"><dt>분석 방법</dt><dd>{ASSESSMENT_METHOD_LABELS[alertDetailState.data.method] ?? alertDetailState.data.method}</dd></div>
            <div className="fact-row"><dt>중요도</dt><dd>{IMPORTANCE_LABELS[alertDetailState.data.importance] ?? alertDetailState.data.importance}</dd></div>
            <div className="fact-row"><dt>확신 수준</dt><dd>{CONFIDENCE_LABELS[alertDetailState.data.confidence] ?? alertDetailState.data.confidence}</dd></div>
            <div className="fact-row"><dt>아직 확인할 점</dt><dd>{alertDetailState.data.uncertainty}</dd></div>
          </dl>
          <p className="insight-time">사건 발생 {formatDateTime(alertDetailState.data.occurredAt) ?? '발생시각 미상'}</p>
          {alertDetailState.data.completedAt && <p className="insight-time">판단 완료 {formatDateTime(alertDetailState.data.completedAt)}</p>}
          {alertDetailState.data.sentAt && <p className="insight-time">알림 전달 {formatDateTime(alertDetailState.data.sentAt)}</p>}
          <div className="insight-actions"><button type="button" className="secondary-action" onClick={() => openEventDetail(alertDetailState.data.eventId, null)}>현재 사건 보기</button></div>
          {alertDetailState.data.evidence.map(reference => <div className="evidence-reference" key={reference.evidenceId}>
            <strong>{reference.sourceName}</strong>
            <span>근거 식별자 {reference.evidenceId}</span>
            <span>공시 접수번호 {reference.externalId}</span>
            {reference.publishedAt && <span>근거 자료 발행 {formatDateTime(reference.publishedAt)}</span>}
            <span>근거 개정 번호 {reference.revision}</span>
            <a className="official-evidence-action" href={reference.originalUrl} target="_blank" rel="noopener noreferrer">공식 근거 원문 <span aria-hidden="true">↗</span></a>
            <button type="button" className="secondary-action" onClick={() => openOfficialEvidence(reference.evidenceId)}>공식 자료 상세</button>
          </div>)}
        </article>}
      </section>}

      <section id="events" className="content-section event-section" aria-labelledby="explore-events-title">
        <div className="section-heading"><span>RELATE</span><h2 id="explore-events-title">최근 확인된 사건</h2></div>
        {exploreEventsState.loading && <Status busy>확인된 사건을 불러오는 중입니다.</Status>}
        {exploreEventsState.error && <ErrorState error={exploreEventsState.error} subject="사건" retry={loadExploreEvents} />}
        {!exploreEventsState.loading && !exploreEventsState.error && exploreEventsState.data.length === 0
          && <Status>현재 AIRA에서 확인해 보여줄 수 있는 사건이 없습니다.</Status>}
        <div className="event-list">{exploreEventsState.data.map(item => <article className="event-card" key={item.eventId}>
          <p className="eyebrow">{item.companies.map(company => company.companyName).join(' · ')}</p><h3>{item.title}</h3>
          <p className="event-meta">{EVENT_TYPE_LABELS[item.eventType] ?? item.eventType} · {item.occurredAt?.slice(0, 10) ?? '발생시각 미상'}</p>
          <button type="button" className="secondary-action"
            onClick={() => openEventDetail(item.eventId, null)}>사건 상세 보기</button>
        </article>)}</div>
      </section>

      {
        <section id="event-detail" className="content-section event-section" aria-labelledby="event-detail-title">
          <div className="section-heading"><span>ASSESS · 현재 사건</span><h2 id="event-detail-title">사건 상세</h2></div>
          {!eventDetailState.loading && !eventDetailState.error && !eventDetailState.data && <Status>사건을 선택하면 현재 판단과 근거를 확인할 수 있습니다.</Status>}{eventDetailState.loading && <Status busy>사건 상세를 불러오는 중입니다.</Status>}
          {eventDetailState.error?.status === 404 &&
            <Status>이 사건은 현재 공개 상세로 제공되지 않습니다.</Status>}
          {eventDetailState.error && eventDetailState.error.status !== 404 &&
            <ErrorState error={eventDetailState.error} subject="사건 상세"
              retry={() => openEventDetail(eventDetailState.eventId, eventDetailState.contextCompanyId)} />}
          {eventDetailState.data && <article className="event-detail event-detail-grid">
            <div className="event-detail-main">
              <section aria-labelledby="event-fact-title"><p className="eyebrow">EVENT FACT · 사건 사실</p>
                <h3 id="event-fact-title">{eventDetailState.data.title}</h3>
                <p>{eventDetailState.data.companies.map(company => company.companyName).join(' · ')}</p>
                <p className="event-meta">{EVENT_TYPE_LABELS[eventDetailState.data.eventType] ?? eventDetailState.data.eventType} · {eventDetailState.data.occurredAt?.slice(0, 10) ?? '발생시각 미상'}</p>
              </section>
              <section className="event-assessment-panel" aria-labelledby="aira-assessment-title"><h3 id="aira-assessment-title">AIRA 판단</h3><p>현재 판단</p>
                {!eventDetailState.data.assessment && <Status>현재 표시할 AIRA 판단이 없습니다.</Status>}
                {eventDetailState.data.assessment && <div className="assessment"><p>{eventDetailState.data.assessment.summary}</p>
                  <p className="insight-identity">판단 식별자 {eventDetailState.data.assessment.assessmentId} · {eventDetailState.data.assessment.analysisVersion}</p>
                  <h4>아직 확인할 점</h4><p>{eventDetailState.data.assessment.uncertainty}</p>
                  <p className="assessment-meta">중요도 {IMPORTANCE_LABELS[eventDetailState.data.assessment.importance] ?? eventDetailState.data.assessment.importance} · 확신 {CONFIDENCE_LABELS[eventDetailState.data.assessment.confidence] ?? eventDetailState.data.assessment.confidence} · {ASSESSMENT_METHOD_LABELS[eventDetailState.data.assessment.method] ?? eventDetailState.data.assessment.method}</p>
                </div>}
              </section>
            </div>
            <aside className="event-detail-side" aria-label="사건과 판단의 근거">
              <section aria-labelledby="official-evidence-title"><h3 id="official-evidence-title">공식 근거</h3>
                {eventDetailState.data.eventEvidence.map(item => <div className="event-evidence" key={item.evidenceId}>
                  <strong>{item.sourceName}</strong><span>{item.title}</span>
                  <a href={item.originalUrl} target="_blank" rel="noopener noreferrer">공식 원문 보기 <span aria-hidden="true">↗</span></a>
                  <button type="button" className="secondary-action" onClick={() => openOfficialEvidence(item.evidenceId)}>공식 자료 상세</button>
                </div>)}
              </section>
              {eventDetailState.data.assessment && <section aria-labelledby="assessment-evidence-title">
                <h3 id="assessment-evidence-title">판단 근거</h3>
                {eventDetailState.data.assessment.evidence.map(item => <div className="event-evidence" key={item.evidenceId}>
                  <strong>{item.sourceName}</strong><span>{item.title}</span>
                  <a href={item.originalUrl} target="_blank" rel="noopener noreferrer">판단에 사용된 원문 보기 <span aria-hidden="true">↗</span></a>
                  <button type="button" className="secondary-action" onClick={() => openOfficialEvidence(item.evidenceId)}>공식 자료 상세</button>
                </div>)}
              </section>}
            </aside>
          </article>}
        </section>}

      {(officialEvidenceState.loading || officialEvidenceState.error || officialEvidenceState.data) &&
        <section id="official-evidence-detail" className="content-section evidence" aria-labelledby="official-evidence-detail-title">
          <div className="section-heading"><span>OFFICIAL EVIDENCE</span><h2 id="official-evidence-detail-title">공식 자료</h2></div>
          {officialEvidenceState.loading && <Status busy>공식 자료를 불러오는 중입니다.</Status>}
          {officialEvidenceState.error?.status === 404 &&
            <Status>이 근거 자료를 현재 공개 AIRA 경로에서 표시할 수 없습니다.</Status>}
          {officialEvidenceState.error && officialEvidenceState.error.status !== 404 &&
            <ErrorState error={officialEvidenceState.error} subject="공식 자료"
              retry={() => openOfficialEvidence(officialEvidenceState.evidenceId)} />}
          {officialEvidenceState.data && <article className="official-evidence-record">
            <div className="official-evidence-lead"><div><p className="eyebrow">{officialEvidenceState.data.source.sourceName}</p>
              <h3>{officialEvidenceState.data.title}</h3></div>
              {officialEvidenceState.data.originalUrl
                ? <a className="official-evidence-action primary-evidence-link" href={officialEvidenceState.data.originalUrl}
                  target="_blank" rel="noopener noreferrer">공식 원문 열기 <span aria-hidden="true">↗</span></a>
                : <Status>저장된 공식 원문 링크가 없습니다.</Status>}
            </div>
            <dl className="evidence-metadata">
              <div><dt>문서 식별자</dt><dd>{officialEvidenceState.data.externalId}</dd></div>
              <div><dt>자료·출처 유형</dt><dd>{EVIDENCE_TYPE_LABELS[officialEvidenceState.data.evidenceType] ?? officialEvidenceState.data.evidenceType} · {SOURCE_TYPE_LABELS[officialEvidenceState.data.source.sourceType] ?? officialEvidenceState.data.source.sourceType}</dd></div>
              {officialEvidenceState.data.source.canonicalDomain && <div><dt>출처 도메인</dt><dd>{officialEvidenceState.data.source.canonicalDomain}</dd></div>}
              <div><dt>공식 자료 발행</dt><dd>{formatDateTime(officialEvidenceState.data.publishedAt) ?? '저장된 발행 시각 없음'}</dd></div>
              <div><dt>AIRA 자료 수집</dt><dd>{formatDateTime(officialEvidenceState.data.collectedAt) ?? '저장된 수집 시각 없음'}</dd></div>
              <div><dt>근거 개정 번호</dt><dd>{officialEvidenceState.data.revision}</dd></div>
              {officialEvidenceState.data.locator && <div><dt>자료 위치</dt><dd>{officialEvidenceState.data.locator}</dd></div>}
            </dl>
            {officialEvidenceState.data.excerpt && <blockquote>{officialEvidenceState.data.excerpt}</blockquote>}
          </article>}
        </section>}

      <section id="companies" className="content-section" aria-labelledby="companies-title"><div className="section-heading"><span>INSPECT</span><h2 id="companies-title">기업 선택</h2></div>
        {!companiesState.loading && !companiesState.error && search.trim() && !companiesState.data.some(company => company.canonicalName.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase())) && <Status>이름이 일치하는 회사가 없습니다.</Status>}
        {companiesState.loading && <Status busy>기업을 불러오는 중입니다.</Status>}{companiesState.error && <ErrorState error={companiesState.error} subject="기업" retry={loadCompanies} />}{!companiesState.loading && !companiesState.error && companiesState.data.length === 0 && <Status>현재 확인할 수 있는 기업이 없습니다. 데이터가 준비되면 이곳에 표시됩니다.</Status>}
        <div className="company-list">{companiesState.data.filter(company => company.canonicalName.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase())).map(company => <button key={company.companyId} type="button" className={`company-option ${selectedCompany?.companyId === company.companyId ? 'selected' : ''}`} aria-pressed={selectedCompany?.companyId === company.companyId} onClick={() => selectCompany(company)}><span className="company-name">{company.canonicalName}</span><span className="company-meta">{company.countryCode === 'KR' ? '대한민국' : company.countryCode ?? '국가 미상'}</span><span aria-hidden="true">→</span></button>)}</div>
      </section>

      {selectedCompany && <section className="content-section" aria-labelledby="period-title">
        <div className="selection-heading"><div className="section-heading"><span>02</span><h2 id="period-title">보고 기간</h2></div><button type="button" className={interested ? 'saved-action' : 'secondary-action'} disabled={interestAction.loading} onClick={() => changeInterest(Boolean(interested))}>{interestAction.loading ? '처리 중…' : interested ? '관심회사에서 삭제' : session.user ? '관심회사에 저장' : '로그인하고 관심회사에 저장'}</button></div>
        {interested && <button type="button" className="secondary-action" disabled={interestAction.loading} onClick={() => changeAlertSetting(!selectedInterest?.alertEnabled)}>{selectedInterest?.alertEnabled ? '앱 알림 끄기' : '앱 알림 켜기'}</button>}
        {interestAction.error && <ErrorState error={interestAction.error} subject="관심회사" retry={interestAction.retry} />}
        {periodsState.loading && <Status busy>이용 가능한 기간을 불러오는 중입니다.</Status>}{periodsState.error && <ErrorState error={periodsState.error} subject="보고 기간" retry={() => selectCompany(selectedCompany)} />}{!periodsState.loading && !periodsState.error && periodsState.data.length === 0 && <Status>이 기업에서 이용 가능한 재무 기간이 없습니다.</Status>}
        {periodsState.data.length > 0 && <div className="period-control"><label htmlFor="reporting-period">정확한 보고 기간</label><select id="reporting-period" value={selectedPeriod ? `${selectedPeriod.periodStart}|${selectedPeriod.periodEnd}` : ''} onChange={event => { const [start, end] = event.target.value.split('|'); setSelectedPeriod(periodsState.data.find(period => period.periodStart === start && period.periodEnd === end)) }}>{periodsState.data.length > 1 && <option value="">기간을 선택하세요</option>}{periodsState.data.map(period => <option key={`${period.periodStart}|${period.periodEnd}`} value={`${period.periodStart}|${period.periodEnd}`}>{period.periodStart} — {period.periodEnd}</option>)}</select></div>}
      </section>}

      {selectedPeriod && <section className="content-section facts-section" aria-labelledby="facts-title"><div className="section-heading"><span>03</span><h2 id="facts-title">{selectedCompany.canonicalName} 핵심 재무정보</h2></div><p className="period-caption">{selectedPeriod.periodStart} — {selectedPeriod.periodEnd}</p>
        {factsState.loading && <Status busy>재무정보와 공식 근거를 확인하는 중입니다.</Status>}{factsState.error && <ErrorState error={factsState.error} subject="재무정보" retry={loadFacts} />}{!factsState.loading && !factsState.error && factsState.data.length === 0 && <Status>선택한 기간에 표시할 재무정보가 없습니다.</Status>}
        <dl className="fact-list">{factsState.data.map(fact => <div className="fact-row" key={`${fact.predicate}-${fact.evidenceId}`}><dt>{LABELS[fact.predicate] ?? fact.predicate}</dt><dd><strong>{new Intl.NumberFormat('ko-KR').format(fact.value)}</strong> <span>{fact.currency}</span></dd></div>)}</dl>
        {evidence.length > 0 && <aside className="evidence" aria-labelledby="evidence-title"><p className="eyebrow" id="evidence-title">OFFICIAL EVIDENCE</p>{evidence.map(item => <div key={item.evidenceId} className="evidence-row"><div><strong>{item.sourceName}</strong><span>공시 식별자 {item.evidenceExternalId}</span></div><a href={item.evidenceOriginalUrl} target="_blank" rel="noopener noreferrer">원문 확인 <span aria-hidden="true">↗</span></a><button type="button" className="secondary-action" onClick={() => openOfficialEvidence(item.evidenceId)}>공식 자료 상세</button></div>)}<p className="evidence-note">같은 공시에 포함된 재무 항목은 하나의 공식 근거로 묶어 표시합니다.</p></aside>}
      </section>}

      {selectedCompany && <section className="content-section event-section" aria-labelledby="events-title">
        <div className="section-heading"><span>04</span><h2 id="events-title" tabIndex="-1">관련 사건과 확인할 의미</h2></div>
        {eventsState.loading && <Status busy>관련 사건을 확인하는 중입니다.</Status>}
        {eventsState.error && <ErrorState error={eventsState.error} subject="관련 사건" retry={() => selectCompany(selectedCompany)} />}
        {!eventsState.loading && !eventsState.error && eventsState.data.length === 0 && <Status>현재 근거와 함께 확인할 사건이 없습니다.</Status>}
        <div className="event-list">{eventsState.data.map(item => <article id={`event-${item.eventId}`} tabIndex="-1" key={item.eventId} className={`event-card ${highlightedEventId === item.eventId ? 'insight-target' : ''}`}>
          <p className="eyebrow">WHAT HAPPENED</p><h3>{item.title}</h3>
          <p className="event-meta">{EVENT_TYPE_LABELS[item.eventType] ?? item.eventType} · {item.occurredAt?.slice(0, 10) ?? '발생시각 미상'}</p>
          {item.assessment
            ? <div className="assessment"><p>현재 판단</p><h4>AIRA가 확인한 의미</h4><p>{item.assessment.summary}</p>
              <h4>아직 확인할 점</h4><p>{item.assessment.uncertainty}</p>
              <p className="assessment-meta">중요도 {IMPORTANCE_LABELS[item.assessment.importance] ?? item.assessment.importance} · 확신 {CONFIDENCE_LABELS[item.assessment.confidence] ?? item.assessment.confidence} · {ASSESSMENT_METHOD_LABELS[item.assessment.method] ?? item.assessment.method}</p></div>
            : <Status>현재 표시할 AIRA 판단이 없습니다.</Status>}
          <button onClick={() => openEventDetail(item.eventId, selectedCompany.companyId)}>현재 사건과 판단 근거 보기</button>
          {item.evidence.map(reference => <div className="event-evidence" key={reference.evidenceId}><strong>{reference.sourceName}</strong><span>공시 식별자 {reference.externalId} · 근거 개정 번호 {reference.revision}</span>
            <a href={reference.originalUrl} target="_blank" rel="noopener noreferrer">근거 원문 확인 <span aria-hidden="true">↗</span></a></div>)}
        </article>)}</div>
      </section>}
      </>}
    </main>
    {session.user && settingsOpen && <RecoveryEmailSettings close={() => setSettingsOpen(false)} />}
    {window.location.pathname === '/privacy' && <footer><span>AIRA</span><p>개인정보와 이용자 보호 원칙</p></footer>}
    {(authMode === 'login' || authMode === 'signup') && <AuthPanel mode={authMode} setMode={setAuthMode} close={() => setAuthMode(null)} authenticated={user => { setSession({ loading: false, user, error: null, notice: null }); setAuthMode(null) }} />}
    {(authMode === 'forgot' || authMode === 'reset' || authMode === 'recovery-confirm') && <RecoveryPanel mode={authMode} token={recoveryEntry.token} setMode={setAuthMode} close={() => setAuthMode(null)} completeLink={() => { setRecoveryEntry({ mode: null, token: '' }); window.history.replaceState({}, '', '/') }} />}
  </>
}
