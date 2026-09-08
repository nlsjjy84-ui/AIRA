import { useCallback, useEffect, useMemo, useState } from 'react'
import { getCompanies, getCompanyEvents, getFinancialFacts, getFinancialPeriods } from './api/companyApi.js'
import { getEventDetail, getRecentEvents } from './api/eventApi.js'
import { getOfficialEvidence } from './api/evidenceApi.js'
import { addInterest, confirmPasswordReset, confirmRecoveryEmail, disableInterestAlert, enableInterestAlert, getCurrentUser, getInterests, login, logout, removeInterest, requestPasswordReset, requestRecoveryEmailVerification, signup } from './api/authApi.js'
import { getOrCreateBriefing } from './api/briefingApi.js'
import { alertEmptyMessage } from './alertEmptyState.js'
import { reconcileAlerts } from './api/alertApi.js'

const LABELS = { REVENUE: '매출', OPERATING_INCOME: '영업이익' }

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

function RecoveryEmailSettings() {
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
  return <section id="account-recovery" className="content-section recovery-section" aria-labelledby="account-recovery-title">
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
}

function initialRecoveryEntry() {
  if (typeof window === 'undefined') return { mode: null, token: '' }
  const token = new URLSearchParams(window.location.search).get('token') ?? ''
  if (window.location.pathname === '/password-reset/confirm') return { mode: 'reset', token }
  if (window.location.pathname === '/recovery-email/confirm') return { mode: 'recovery-confirm', token }
  return { mode: null, token: '' }
}

export default function App() {
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
  const [alertsState, setAlertsState] = useState({ loading: false, data: [], error: null })
  const [pendingInsightTarget, setPendingInsightTarget] = useState(null)
  const [highlightedEventId, setHighlightedEventId] = useState(null)

  const becomeAnonymous = useCallback((notice = null) => {
    setSession({ loading: false, user: null, error: null, notice })
    setInterestsState({ loading: false, data: [], error: null })
    setBriefingState({ loading: false, data: null, error: null })
    setAlertsState({ loading: false, data: [], error: null })
  }, [])

  const loadSession = useCallback(() => {
    setSession(current => ({ ...current, loading: true, error: null }))
    getCurrentUser().then(user => setSession({ loading: false, user, error: null, notice: null }))
      .catch(error => error.status === 401 || error.status === 403
        ? becomeAnonymous()
        : setSession({ loading: false, user: null, error, notice: null }))
  }, [becomeAnonymous])

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
    const controller = new AbortController()
    setEventDetailState({ loading: true, data: null, error: null, eventId, contextCompanyId })
    getEventDetail(eventId, controller.signal)
      .then(data => setEventDetailState({ loading: false, data, error: null, eventId, contextCompanyId }))
      .catch(error => error.name !== 'AbortError'
        && setEventDetailState({ loading: false, data: null, error, eventId, contextCompanyId }))
    return () => controller.abort()
  }, [])

  const openOfficialEvidence = useCallback((evidenceId) => {
    const controller = new AbortController()
    setOfficialEvidenceState({ loading: true, data: null, error: null, evidenceId })
    getOfficialEvidence(evidenceId, controller.signal)
      .then(data => setOfficialEvidenceState({ loading: false, data, error: null, evidenceId }))
      .catch(error => error.name !== 'AbortError'
        && setOfficialEvidenceState({ loading: false, data: null, error, evidenceId }))
    return () => controller.abort()
  }, [])

  const loadInterests = useCallback(() => {
    if (!session.user) return
    setInterestsState({ loading: true, data: [], error: null })
    getInterests().then(data => setInterestsState({ loading: false, data: data ?? [], error: null }))
      .catch(error => error.status === 401 || error.status === 403
        ? becomeAnonymous('세션이 만료되었습니다. 공개 정보는 계속 볼 수 있습니다.')
        : setInterestsState({ loading: false, data: [], error }))
  }, [session.user, becomeAnonymous])

  useEffect(loadInterests, [loadInterests])

  const loadBriefing = useCallback(() => {
    if (!session.user) return
    setBriefingState({ loading: true, data: null, error: null })
    getOrCreateBriefing().then(data => setBriefingState({ loading: false, data, error: null }))
      .catch(error => error.status === 401 || error.status === 403
        ? becomeAnonymous('세션이 만료되었습니다. 공개 정보는 계속 볼 수 있습니다.')
        : setBriefingState({ loading: false, data: null, error }))
  }, [session.user, becomeAnonymous])

  useEffect(() => {
    if (session.user && !interestsState.loading && !interestsState.error) loadBriefing()
  }, [session.user, interestsState.loading, interestsState.error, interestsState.data, loadBriefing])

  const selectCompany = useCallback((company, eventId = null) => {
    setSelectedCompany(company)
    setHighlightedEventId(eventId)
    setPendingInsightTarget(eventId ? { companyId: company.companyId, eventId } : null)
    setSelectedPeriod(null)
    setFactsState({ loading: false, data: [], error: null })
    setEventsState({ loading: true, data: [], error: null })
    setPeriodsState({ loading: true, data: [], error: null })
    getFinancialPeriods(company.companyId).then(body => {
      const periods = body.periods ?? []
      setPeriodsState({ loading: false, data: periods, error: null })
      if (periods.length === 1) setSelectedPeriod(periods[0])
    }).catch(error => setPeriodsState({ loading: false, data: [], error }))
    getCompanyEvents(company.companyId)
      .then(body => setEventsState({ loading: false, data: body.events ?? [], error: null }))
      .catch(error => setEventsState({ loading: false, data: [], error }))
  }, [])

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
    setAlertsState({ loading: true, data: [], error: null })
    reconcileAlerts().then(body => setAlertsState({ loading: false, data: body.alerts ?? [], error: null }))
      .catch(error => error.status === 401 || error.status === 403 ? becomeAnonymous() : setAlertsState({ loading: false, data: [], error }))
  }, [session.user, becomeAnonymous])

  useEffect(() => { if (session.user && !interestsState.loading) loadAlerts() }, [session.user, interestsState.loading, interestsState.data, loadAlerts])

  async function changeInterest(remove = false) {
    if (!session.user) { setAuthMode('login'); return }
    const retry = () => changeInterest(remove)
    setInterestAction({ loading: true, error: null, retry: null })
    try {
      if (remove) await removeInterest(selectedCompany.companyId)
      else await addInterest(selectedCompany.companyId)
      setInterestAction({ loading: false, error: null, retry: null })
      loadInterests()
    } catch (error) {
      if (error.status === 401 || error.status === 403) becomeAnonymous('세션이 만료되었습니다. 다시 로그인하면 관심회사를 저장할 수 있습니다.')
      else setInterestAction({ loading: false, error, retry })
    }
  }

  async function changeAlertSetting(enabled) {
    setInterestAction({ loading: true, error: null, retry: null })
    try {
      if (enabled) await enableInterestAlert(selectedCompany.companyId)
      else await disableInterestAlert(selectedCompany.companyId)
      setInterestAction({ loading: false, error: null, retry: null }); loadInterests()
    } catch (error) { setInterestAction({ loading: false, error, retry: () => changeAlertSetting(enabled) }) }
  }

  async function performLogout() {
    setSession(current => ({ ...current, loading: true, error: null }))
    try { await logout(); becomeAnonymous('로그아웃되었습니다. 공개 정보는 계속 볼 수 있습니다.') }
    catch (error) { setSession(current => ({ ...current, loading: false, error })) }
  }

  return <>
    <header className="site-header"><a className="brand" href="/" aria-label="AIRA 홈">AIRA</a>
      <nav className="account-nav" aria-label="계정 메뉴">
        {session.loading && <span className="session-label">세션 확인 중…</span>}
        {!session.loading && !session.user && <><button type="button" onClick={() => setAuthMode('login')}>로그인</button><button type="button" className="nav-signup" onClick={() => setAuthMode('signup')}>회원가입</button></>}
        {!session.loading && session.user && <><a href="#my-interests">관심회사</a><a href="#my-briefing">브리핑</a><a href="#my-alerts">알림</a><span className="session-label">{session.user.nickname}</span><button type="button" onClick={performLogout}>로그아웃</button></>}
      </nav>
    </header>
    {session.notice && <div className="session-notice" role="status">{session.notice}</div>}
    {session.error && <div className="session-notice error" role="alert">계정 요청을 처리하지 못했습니다. <button onClick={session.user ? performLogout : loadSession}>다시 시도</button></div>}
    <main>
      <section className="intro" aria-labelledby="page-title"><p className="eyebrow">PUBLIC COMPANY FINANCIALS</p><h1 id="page-title">공식 데이터와 근거를<br />함께 확인하세요.</h1><p className="intro-copy">현재 제공되는 기업을 선택하면 정확한 보고 기간의 핵심 재무정보와 원문 공시를 볼 수 있습니다.</p><a className="primary-action" href="#companies">기업 둘러보기</a></section>

      {session.user && <section id="my-interests" className="content-section interests-section" aria-labelledby="interests-title">
        <div className="section-heading"><span>MY</span><h2 id="interests-title">내 관심회사</h2></div>
        {interestsState.loading && <Status busy>관심회사를 불러오는 중입니다.</Status>}
        {interestsState.error && <ErrorState error={interestsState.error} subject="관심회사" retry={loadInterests} />}
        {!interestsState.loading && !interestsState.error && interestsState.data.length === 0 && <div className="personalization-empty"><Status>아직 저장한 관심회사가 없습니다. 회사를 탐색하고 관심회사로 저장해 보세요.</Status><a className="secondary-action" href="#companies">회사 탐색하기</a></div>}
        <div className="interest-list">{interestsState.data.map(item => <button type="button" key={item.entityId} onClick={() => selectCompany({ companyId: item.entityId, canonicalName: item.canonicalName, countryCode: item.countryCode })}><strong>{item.canonicalName}</strong><span>재무정보 다시 보기 →</span></button>)}</div>
      </section>}

      {session.user && <RecoveryEmailSettings />}

      {session.user && <section id="my-briefing" className="content-section briefing-section" aria-labelledby="briefing-title">
        <div className="section-heading"><span>BRIEFING</span><h2 id="briefing-title">내 브리핑</h2></div>
        {briefingState.loading && <Status busy>관심회사에서 확인된 내용을 모으는 중입니다.</Status>}
        {briefingState.error && <ErrorState error={briefingState.error} subject="브리핑" retry={loadBriefing} />}
        {!briefingState.loading && !briefingState.error && briefingState.data?.items?.length === 0 &&
          <div className="personalization-empty"><Status>{briefingState.data.emptyReason === 'NO_INTERESTS' ? '아직 관심 회사가 없습니다.' : '이 Briefing 기간에 새로 정리된 변화가 없습니다.'}</Status>{briefingState.data.emptyReason === 'NO_INTERESTS' && <p>계속 확인하고 싶은 회사를 저장하면 이후 새로 정리된 변화를 Briefing에서 모아볼 수 있습니다.</p>}<a className="secondary-action" href="#companies">관심회사 살펴보기</a></div>}
        {briefingState.data?.periodStart && briefingState.data?.periodEnd && <p className="insight-time">정리 기간 {formatDateTime(briefingState.data.periodStart)} — {formatDateTime(briefingState.data.periodEnd)}</p>}
        {briefingState.data?.generatedAt && <p className="insight-time">브리핑 생성 {formatDateTime(briefingState.data.generatedAt)}</p>}
        <div className="briefing-list">{briefingState.data?.items?.map(item => <article className="briefing-card" key={item.assessmentId}>
          <p className="eyebrow">관련 회사: {item.companies.map(company => company.companyName).join(' · ')}</p><h3>{item.eventTitle}</h3>
          <p className="insight-reason">관심회사로 저장한 회사의 AIRA 분석입니다.</p>
          <p className="event-meta">{item.eventType} · {item.occurredAt?.slice(0, 10)}</p>
          <div className="assessment"><h4>확인할 의미</h4><p>{item.summary}</p>
            <h4>아직 확인할 점</h4><p>{item.uncertainty}</p></div>
          <div className="insight-actions"><button type="button" className="primary-action" onClick={() => openEventDetail(item.eventId, null)}>Event 상세 보기</button></div>
          {item.evidence?.map(reference => <div className="evidence-reference" key={reference.evidenceId}>
            <a className="official-evidence-action" href={reference.originalUrl} target="_blank" rel="noopener noreferrer">{reference.sourceName} 공식 근거 원문 <span aria-hidden="true">↗</span></a>
            <span>공시 접수번호 {reference.externalId}</span>
            <button type="button" className="secondary-action" onClick={() => openOfficialEvidence(reference.evidenceId)}>공식 자료 상세</button>
          </div>)}
        </article>)}</div>
      </section>}

      {session.user && <section id="my-alerts" className="content-section alert-section" aria-labelledby="alerts-title">
        <div className="section-heading"><span>IN APP</span><h2 id="alerts-title">관심회사 알림</h2></div>
        {alertsState.loading && <Status busy>새로 확인된 내용을 살펴보는 중입니다.</Status>}
        {alertsState.error && <ErrorState error={alertsState.error} subject="알림" retry={loadAlerts} />}
        {!alertsState.loading && !alertsState.error && alertsState.data.length === 0 && <div className="personalization-empty"><Status>{alertEmptyMessage(interestsState.data, alertsState.data)}</Status><a className="secondary-action" href="#companies">회사와 알림 설정 보기</a></div>}
        <div className="alert-list">{alertsState.data.map(item => <article className="alert-card" key={item.alertId}>
          <p className="eyebrow">관련 회사: {item.companies.map(company => company.companyName).join(' · ')}</p><h3>{item.eventTitle}</h3><p>{item.summary}</p>
          <p className="insight-reason">앱 알림을 켠 관심회사에 새로운 AIRA 분석이 준비되었습니다.</p>
          {item.sentAt && <p className="insight-time">알림 전달 {formatDateTime(item.sentAt)}</p>}
          <h4>아직 확인할 점</h4><p>{item.uncertainty}</p>
          <div className="insight-actions"><button type="button" className="primary-action" onClick={() => openEventDetail(item.eventId, null)}>Event 상세 보기</button>
            <a className="official-evidence-action" href={item.evidenceOriginalUrl} target="_blank" rel="noopener noreferrer">{item.sourceName} 공식 근거 원문 <span aria-hidden="true">↗</span></a></div>
          <p className="evidence-reference">공시 접수번호 {item.evidenceExternalId}</p>
        </article>)}</div>
      </section>}

      <section id="events" className="content-section event-section" aria-labelledby="explore-events-title">
        <div className="section-heading"><span>EXPLORE</span><h2 id="explore-events-title">최근 확인된 Event</h2></div>
        {exploreEventsState.loading && <Status busy>확인된 Event를 불러오는 중입니다.</Status>}
        {exploreEventsState.error && <ErrorState error={exploreEventsState.error} subject="Event" retry={loadExploreEvents} />}
        {!exploreEventsState.loading && !exploreEventsState.error && exploreEventsState.data.length === 0
          && <Status>현재 AIRA에서 확인해 보여줄 수 있는 Event가 없습니다.</Status>}
        <div className="event-list">{exploreEventsState.data.map(item => <article className="event-card" key={`${item.eventId}-${item.companyId}`}>
          <p className="eyebrow">{item.companyName}</p><h3>{item.title}</h3>
          <p className="event-meta">{item.eventType} · {item.occurredAt?.slice(0, 10)}</p>
          <button type="button" className="secondary-action"
            onClick={() => openEventDetail(item.eventId, item.companyId)}>Event 상세 보기</button>
        </article>)}</div>
      </section>

      {(eventDetailState.loading || eventDetailState.error || eventDetailState.data) &&
        <section id="event-detail" className="content-section event-section" aria-labelledby="event-detail-title">
          <div className="section-heading"><span>EVENT DETAIL</span><h2 id="event-detail-title">Event 상세</h2></div>
          {eventDetailState.loading && <Status busy>Event 상세를 불러오는 중입니다.</Status>}
          {eventDetailState.error?.status === 404 &&
            <Status>이 Event는 현재 공개 상세로 제공되지 않습니다.</Status>}
          {eventDetailState.error && eventDetailState.error.status !== 404 &&
            <ErrorState error={eventDetailState.error} subject="Event 상세"
              retry={() => openEventDetail(eventDetailState.eventId, eventDetailState.contextCompanyId)} />}
          {eventDetailState.data && <article className="event-detail">
            <section aria-labelledby="event-fact-title"><p className="eyebrow">EVENT FACT</p>
              <h3 id="event-fact-title">{eventDetailState.data.title}</h3>
              <p>{eventDetailState.data.companies.map(company => company.companyName).join(' · ')}</p>
              <p className="event-meta">{eventDetailState.data.eventType} · {eventDetailState.data.occurredAt?.slice(0, 10)}</p>
            </section>
            <section aria-labelledby="official-evidence-title"><h3 id="official-evidence-title">공식 근거</h3>
              {eventDetailState.data.eventEvidence.map(item => <div className="event-evidence" key={item.evidenceId}>
                <strong>{item.sourceName}</strong><span>{item.title}</span>
                <a href={item.originalUrl} target="_blank" rel="noopener noreferrer">공식 원문 보기 <span aria-hidden="true">↗</span></a>
                <button type="button" className="secondary-action" onClick={() => openOfficialEvidence(item.evidenceId)}>공식 자료 상세</button>
              </div>)}
            </section>
            <section aria-labelledby="aira-assessment-title"><h3 id="aira-assessment-title">AIRA 해석</h3>
              {!eventDetailState.data.assessment && <Status>현재 표시할 AIRA 해석이 없습니다.</Status>}
              {eventDetailState.data.assessment && <div className="assessment"><p>{eventDetailState.data.assessment.summary}</p>
                <h4>아직 확인할 점</h4><p>{eventDetailState.data.assessment.uncertainty}</p>
                <p className="assessment-meta">중요도 {eventDetailState.data.assessment.importance} · 확신 {eventDetailState.data.assessment.confidence} · {eventDetailState.data.assessment.method}</p>
              </div>}
            </section>
            {eventDetailState.data.assessment && <section aria-labelledby="assessment-evidence-title">
              <h3 id="assessment-evidence-title">해석 근거</h3>
              {eventDetailState.data.assessment.evidence.map(item => <div className="event-evidence" key={item.evidenceId}>
                <strong>{item.sourceName}</strong><span>{item.title}</span>
                <a href={item.originalUrl} target="_blank" rel="noopener noreferrer">해석에 사용된 원문 보기 <span aria-hidden="true">↗</span></a>
                <button type="button" className="secondary-action" onClick={() => openOfficialEvidence(item.evidenceId)}>공식 자료 상세</button>
              </div>)}
            </section>}
          </article>}
        </section>}

      {(officialEvidenceState.loading || officialEvidenceState.error || officialEvidenceState.data) &&
        <section id="official-evidence-detail" className="content-section evidence" aria-labelledby="official-evidence-detail-title">
          <div className="section-heading"><span>OFFICIAL EVIDENCE</span><h2 id="official-evidence-detail-title">공식 자료</h2></div>
          {officialEvidenceState.loading && <Status busy>공식 자료를 불러오는 중입니다.</Status>}
          {officialEvidenceState.error?.status === 404 &&
            <Status>이 Evidence를 현재 public AIRA 경로에서 표시할 수 없습니다.</Status>}
          {officialEvidenceState.error && officialEvidenceState.error.status !== 404 &&
            <ErrorState error={officialEvidenceState.error} subject="공식 자료"
              retry={() => openOfficialEvidence(officialEvidenceState.evidenceId)} />}
          {officialEvidenceState.data && <article>
            <p className="eyebrow">{officialEvidenceState.data.source.sourceName}</p>
            <h3>{officialEvidenceState.data.title}</h3>
            <p>문서 식별자 {officialEvidenceState.data.externalId}</p>
            <p>자료 유형 {officialEvidenceState.data.evidenceType} · 출처 유형 {officialEvidenceState.data.source.sourceType}</p>
            {officialEvidenceState.data.source.canonicalDomain && <p>출처 도메인 {officialEvidenceState.data.source.canonicalDomain}</p>}
            <p>공식 자료 발행 {formatDateTime(officialEvidenceState.data.publishedAt) ?? '저장된 발행 시각 없음'}</p>
            <p>AIRA 자료 수집 {formatDateTime(officialEvidenceState.data.collectedAt) ?? '저장된 수집 시각 없음'}</p>
            <p>Revision {officialEvidenceState.data.revision}</p>
            {officialEvidenceState.data.locator && <p>자료 위치 {officialEvidenceState.data.locator}</p>}
            {officialEvidenceState.data.excerpt && <blockquote>{officialEvidenceState.data.excerpt}</blockquote>}
            {officialEvidenceState.data.originalUrl
              ? <a className="official-evidence-action" href={officialEvidenceState.data.originalUrl}
                target="_blank" rel="noopener noreferrer">공식 원문 열기 <span aria-hidden="true">↗</span></a>
              : <Status>저장된 공식 원문 링크가 없습니다.</Status>}
          </article>}
        </section>}

      <section id="companies" className="content-section" aria-labelledby="companies-title"><div className="section-heading"><span>01</span><h2 id="companies-title">기업 선택</h2></div>
        {companiesState.loading && <Status busy>기업을 불러오는 중입니다.</Status>}{companiesState.error && <ErrorState error={companiesState.error} subject="기업" retry={loadCompanies} />}{!companiesState.loading && !companiesState.error && companiesState.data.length === 0 && <Status>현재 확인할 수 있는 기업이 없습니다. 데이터가 준비되면 이곳에 표시됩니다.</Status>}
        <div className="company-list">{companiesState.data.map(company => <button key={company.companyId} type="button" className={`company-option ${selectedCompany?.companyId === company.companyId ? 'selected' : ''}`} aria-pressed={selectedCompany?.companyId === company.companyId} onClick={() => selectCompany(company)}><span className="company-name">{company.canonicalName}</span><span className="company-meta">{company.countryCode === 'KR' ? '대한민국' : company.countryCode ?? '국가 미상'}</span><span aria-hidden="true">→</span></button>)}</div>
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
          <p className="event-meta">{item.eventType} · {item.occurredAt?.slice(0, 10)}</p>
          {item.assessment
            ? <div className="assessment"><h4>AIRA가 확인한 의미</h4><p>{item.assessment.summary}</p>
              <h4>아직 확인할 점</h4><p>{item.assessment.uncertainty}</p>
              <p className="assessment-meta">중요도 {item.assessment.importance} · 확신 {item.assessment.confidence} · 규칙 기반 분석</p></div>
            : <Status>현재 표시할 AIRA 해석이 없습니다.</Status>}
          <div className="event-evidence"><strong>{item.evidence.sourceName}</strong><span>공시 식별자 {item.evidence.externalId}</span>
            <a href={item.evidence.originalUrl} target="_blank" rel="noopener noreferrer">근거 원문 확인 <span aria-hidden="true">↗</span></a></div>
        </article>)}</div>
      </section>}
    </main>
    <footer><span>AIRA</span><p>공식 시장정보를 근거와 함께 제공합니다.</p></footer>
    {(authMode === 'login' || authMode === 'signup') && <AuthPanel mode={authMode} setMode={setAuthMode} close={() => setAuthMode(null)} authenticated={user => { setSession({ loading: false, user, error: null, notice: null }); setAuthMode(null) }} />}
    {(authMode === 'forgot' || authMode === 'reset' || authMode === 'recovery-confirm') && <RecoveryPanel mode={authMode} token={recoveryEntry.token} setMode={setAuthMode} close={() => setAuthMode(null)} completeLink={() => { setRecoveryEntry({ mode: null, token: '' }); window.history.replaceState({}, '', '/') }} />}
  </>
}
