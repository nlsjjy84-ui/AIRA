import Logo from './Logo.jsx'
import ThemeToggle from './ThemeToggle.jsx'
import { useCallback, useEffect, useRef, useState } from 'react'
import HistoricalAssessment from './HistoricalAssessment.jsx'
import CanonicalExplorer from './CanonicalExplorer.jsx'
import PersonalFinance from './PersonalFinance.jsx'
import HomeDashboard from './HomeDashboard.jsx'
import ClassPage from './ClassPage.jsx'
import { getScreener } from './api/stockApi.js'
import EconomicIndicatorBoard from './EconomicIndicatorBoard.jsx'
import MarketFilingBoard from './MarketFilingBoard.jsx'
import StockPage from './StockPage.jsx'
import ScreenerPage from './ScreenerPage.jsx'
import MarketPage from './MarketPage.jsx'
import StudyPage from './StudyPage.jsx'
import PlansPage from './PlansPage.jsx'
import DataStatusPage from './DataStatusPage.jsx'
import HomeMarketSummary from './HomeMarketSummary.jsx'
import ClaimCheck from './ClaimCheck.jsx'
import LandingPage from './LandingPage.jsx'
import AiGuide from './AiGuide.jsx'
import { HomePriceAlerts } from './PriceAlerts.jsx'
import InterestFilings from './InterestFilings.jsx'
import { searchEntities } from './api/canonicalApi.js'
import { readRecentStocks } from './stockMath.js'
import MarketNewsBoard from './MarketNewsBoard.jsx'
import { getLatestMarketIndices } from './api/marketIndexApi.js'
import { getLatestRealGdp } from './api/economicIndicatorApi.js'
import { getRecentEvents } from './api/eventApi.js'
import { getLatestMarketNews } from './api/newsApi.js'
import { getOfficialEvidence } from './api/evidenceApi.js'
import { addInterest, confirmPasswordReset, confirmRecoveryEmail, disableInterestAlert, enableInterestAlert, getCurrentUser, getInterests, login, logout, demoLogin, removeInterest, requestPasswordReset, requestRecoveryEmailVerification, signup } from './api/authApi.js'
import { getOrCreateBriefing } from './api/briefingApi.js'
import AiAssist from './AiAssist.jsx'
import { alertEmptyMessage } from './alertEmptyState.js'
import { getAlert, reconcileAlerts } from './api/alertApi.js'
import { assessmentExplorerState, EXPLORER_HISTORY_KEY } from './explorerState.js'

const EVIDENCE_TYPE_LABELS = { ARTICLE: '기사', DISCLOSURE: '공시', IR: 'IR 자료', PRESS_RELEASE: '보도자료', OFFICIAL_DATA: '공식 데이터', OTHER: '기타' }
const SOURCE_TYPE_LABELS = { NEWS: '뉴스', REGULATOR: '감독기관', EXCHANGE: '거래소', COMPANY_IR: '기업 IR', GOVERNMENT: '정부기관', OTHER: '기타' }
const EVENT_TYPE_LABELS = { EARNINGS: '실적', DISCLOSURE: '공시', BUSINESS: '사업', GOVERNANCE: '지배구조', POLICY_REGULATION: '정책·규제', RISK: '리스크', MARKET: '시장' }
const IMPORTANCE_LABELS = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음', CRITICAL: '매우 높음' }
const CONFIDENCE_LABELS = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음' }
const ASSESSMENT_METHOD_LABELS = { RULE: '규칙 기반', AI: 'AI 기반', HYBRID: '혼합', HUMAN_REVIEW: '사람 검토' }

export const DEMO_NICKNAME = '데모계정'
const DEMO_READ_ONLY_NOTICE = '데모 계정은 읽기 전용이라 저장·변경은 되지 않습니다. 둘러보기와 AI 읽기 도움은 그대로 쓸 수 있어요.'
/**
 * 출시 전 정책: 랜딩을 지나 앱 화면에 들어온 방문자는 자동으로 공용 데모 계정(읽기 전용)에 들어간다.
 * 서류·면접 평가자가 「시장 먼저 보기」나 바로가기 링크로 들어와도 주장 확인·AI 기능을 잠김 없이 다 써 보게 하려는 것.
 * 방문자가 늘면 false로 바꾸면 원래대로(로그인 안내) 돌아간다. 운영 빌드에서만 켜지고, 테스트·개발 실행에서는 꺼져 있다.
 */
export const AUTO_DEMO_FOR_VISITORS = true
/** 「데모 끝내기」·로그아웃을 누른 사람은 같은 탭에서 다시 자동 입장시키지 않는다. */
const AUTO_DEMO_OPT_OUT_KEY = 'aira-auto-demo-off'

function messageFor(error, subject) {
  if (error?.status === 423) return DEMO_READ_ONLY_NOTICE
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

// 첫 화면 오른쪽: 면접관·처음 온 사람이 가입 없이 로그인 전용 화면(관심회사·브리핑·알림·AI)을 바로 보게 한다.
function DemoCard({ user, onDemo, demoState, onLogout }) {
  if (user?.nickname === DEMO_NICKNAME) return <aside className="demo-card" aria-labelledby="demo-card-title">
    <p className="eyebrow">DEMO · 읽기 전용</p>
    <h2 id="demo-card-title">데모 계정으로 둘러보는 중</h2>
    <ol className="demo-steps">
      <li><a href="#my-interests"><strong>내 관심회사 → 종목 화면</strong><span>회사 3곳 · 누르면 주가·실적·공시를 깊게</span></a></li>
      <li><a href="#my-briefing"><strong>내 브리핑</strong><span>새 변화를 AI가 읽기 쉽게 정리</span></a></li>
      <li><a href="/classes"><strong>공부 반 · 강사 진도표</strong><span>예시 반에서 수강생 진도를 한눈에</span></a></li>
      <li><a href="/explore"><strong>질문·주장 확인</strong><span>공시 원문 인용과 숫자 대조</span></a></li>
    </ol>
    <button type="button" className="secondary-action" onClick={onLogout}>데모 끝내기</button>
  </aside>
  if (user) return <aside className="demo-card" aria-labelledby="demo-card-title">
    <p className="eyebrow">MY AIRA</p>
    <h2 id="demo-card-title">{user.nickname}님, 바로 가기</h2>
    <ol className="demo-steps">
      <li><a href="#my-interests"><strong>내 관심회사</strong><span>저장한 회사 모아 보기</span></a></li>
      <li><a href="#my-briefing"><strong>내 브리핑</strong><span>새로 정리된 변화</span></a></li>
      <li><a href="#my-alerts"><strong>알림</strong><span>알림을 켠 회사의 새 소식</span></a></li>
    </ol>
  </aside>
  return <aside className="demo-card" aria-labelledby="demo-card-title">
    <p className="eyebrow">처음 오셨나요?</p>
    <h2 id="demo-card-title">가입 없이 30초 둘러보기</h2>
    <ol className="demo-steps">
      <li><span className="demo-num" aria-hidden="true">1</span><div><strong>이 말, 사실이야?</strong><span>들은 말을 공식 값·접수번호로 판정</span></div></li>
      <li><span className="demo-num" aria-hidden="true">2</span><div><strong>공부 반 · 강사 진도표</strong><span>같은 회사 과제를 내고 수강생 진도를 한 화면에</span></div></li>
      <li><span className="demo-num" aria-hidden="true">3</span><div><strong>왜 그럴까 · 내 공부</strong><span>숫자의 이유를 회사 공시로 풀고, 알게 된 것을 저장</span></div></li>
    </ol>
    <button type="button" className="primary-action demo-action" onClick={onDemo} disabled={demoState.loading}>
      {demoState.loading ? '데모 계정으로 들어가는 중…' : '데모로 둘러보기'}</button>
    <p className="demo-note">{demoState.error ? '지금은 데모에 들어갈 수 없습니다. 잠시 후 다시 시도해 주세요.' : '읽기 전용 데모 계정이에요. 저장·변경은 되지 않습니다.'}</p>
  </aside>
}

function AuthPanel({ mode, setMode, close, authenticated, onDemo }) {
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
          ? '비밀번호는 10자 이상 72자 이하로 입력해 주세요.'
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
      {onDemo && <p className="auth-demo">가입 없이 먼저 보고 싶다면 <button type="button" onClick={onDemo}>데모로 둘러보기</button></p>}
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
            onChange={event => setPassword(event.target.value)} minLength="10" maxLength="72" required
            autoComplete={mode === 'signup' ? 'new-password' : 'current-password'} />
          <button type="button" onClick={() => setVisible(value => !value)}
            aria-label={visible ? '비밀번호 숨기기' : '비밀번호 표시'}>{visible ? '숨기기' : '표시'}</button>
        </div>
        {mode === 'signup' && <small>10자 이상 72자 이하</small>}
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
          <input id="new-password" type="password" value={password} onChange={event => setPassword(event.target.value)} minLength="10" maxLength="72" required autoComplete="new-password" autoFocus />
          <small>10자 이상 72자 이하</small>
          <label htmlFor="confirm-password">새 비밀번호 확인</label>
          <input id="confirm-password" type="password" value={confirmation} onChange={event => setConfirmation(event.target.value)} minLength="10" maxLength="72" required autoComplete="new-password" />
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

function PrivacyPage() {
  return <section className="privacy-page" aria-labelledby="privacy-title">
    <div className="privacy-lead"><p className="eyebrow">PRIVACY · 이용자 보호</p><h1 id="privacy-title">개인정보와 이용자 보호 원칙</h1>
      <p>AIRA는 계정 운영과 명시된 기능에 필요한 최소한의 사용자 데이터만 수집합니다.</p></div>
    <div className="privacy-principles">
      <section><span>01</span><div><h2>필요한 만큼만 수집합니다.</h2><p>새로운 사용자 데이터 수집 전 목적, 최소 대안, 저장 위치, 접근자, 보존 기간과 삭제 시점을 검토합니다.</p></div></section>
      <section><span>02</span><div><h2>필요하지 않은 추적 정보는 수집하지 않습니다.</h2><p>필요성이 없다면 실명·성별·생년월일·주소·직업·전화번호를 수집하지 않습니다. Wi-Fi·Bluetooth 물리 식별정보, 접속 이력, 이동 경로와 과거 접속 장소도 기본적으로 수집·저장하지 않습니다.</p></div></section>
      <section><span>03</span><div><h2>수집 확대는 자동으로 허용되지 않습니다.</h2><p>보안이나 모니터링을 이유로 영구적인 사용자 추적 데이터를 만들지 않습니다. 신규 수집은 목적과 수명주기를 문서화하고, 비수집 정책 변경은 Architecture 검토와 ADR을 거칩니다.</p></div></section>
    </div>
  </section>
}

/** 토스처럼 큰 메뉴 5개, 그 아래 작은 메뉴 한 줄. 작은 메뉴 하나가 화면 하나다. */
export const MAIN_MENUS = [
  ['market', '시장', [['시장 지도', '/market'], ['지수', '/market?tab=index'], ['환율·금리·금', '/market?tab=macro'], ['ETF', '/market?tab=etf'], ['업종', '/market?tab=sectors'], ['상승 상위', '/market?tab=up'],
    ['하락 상위', '/market?tab=down'], ['거래대금 상위', '/market?tab=value'], ['최고가 근처', '/market?tab=high'], ['최저가 근처', '/market?tab=low']]],
  ['find', '종목 찾기', [['조건으로 찾기', '/screen'], ['회사·사건 탐색', '/explore']]],
  ['check', '주장 확인', [['이 말, 사실이야?', '/check']]],
  ['study', '내 공부', [['공부 노트', '/study'], ['공부 반', '/classes'], ['관심회사·브리핑·알림', '/#my-interests']]],
  ['finance', '내 금융', [['내 금융', '/finance']]],
]

export function activeMenu(path, search = '') {
  const tab = new URLSearchParams(search).get('tab')
  const find = (key, href) => { const menu = MAIN_MENUS.find(m => m[0] === key); return { menu, item: href ? menu[2].find(i => i[1] === href) ?? null : null } }
  if (path === '/market') return find('market', tab ? `/market?tab=${tab}` : '/market')
  if (path === '/screen') return find('find', '/screen')
  if (path === '/explore') return find('find', '/explore')
  if (path.startsWith('/stock/')) return find('find')
  if (path === '/check') return find('check', '/check')
  if (path === '/study') return find('study', '/study')
  if (path === '/classes') return find('study', '/classes')
  if (path === '/finance') return find('finance', '/finance')
  return { menu: null, item: null }
}

function MainMenus({ path, onHover }) {
  const active = activeMenu(path, typeof window === 'undefined' ? '' : window.location.search)
  return <nav className="header-menus" aria-label="주 메뉴">{MAIN_MENUS.map(([key, label, items]) =>
    <a key={key} href={items[0][1]} aria-current={active.menu?.[0] === key ? 'page' : undefined}
      onMouseEnter={() => onHover(key)} onFocus={() => onHover(key)}>{label}</a>)}</nav>
}

function SubMenus({ path, hover, onHover }) {
  const active = activeMenu(path, typeof window === 'undefined' ? '' : window.location.search)
  const shown = MAIN_MENUS.find(m => m[0] === hover) ?? active.menu
  if (!shown) return null
  return <nav className="header-submenus" aria-label={`${shown[1]} 메뉴`} onMouseLeave={() => onHover(null)}>
    {shown[2].map(([label, href]) => <a key={href} href={href} aria-current={active.item?.[1] === href ? 'page' : undefined}>{label}</a>)}</nav>
}

export default function App() {
  const [search, setSearch] = useState(() => new URLSearchParams(window.location.search).get('q') ?? '')
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
  const [officialEvidenceState, setOfficialEvidenceState] = useState({ loading: false, data: null, error: null, evidenceId: null })
  const [interestsState, setInterestsState] = useState({ loading: false, data: [], error: null })
  const [interestAction, setInterestAction] = useState({ loading: false, error: null, retry: null })
  const [briefingState, setBriefingState] = useState({ loading: false, data: null, error: null })
  const [alertsState, setAlertsState] = useState({ loading: false, data: [], emptyReason: null, error: null })
  const [alertDetailState, setAlertDetailState] = useState({ loading: false, data: null, error: null, alertId: null })
  const [marketIndexState, setMarketIndexState] = useState({ loading: false, data: null, error: null })
  const [realGdpState, setRealGdpState] = useState({ loading: false, data: null, error: null })
  const [recentEventState, setRecentEventState] = useState({ loading: false, data: null, error: null })
  const [marketNewsState, setMarketNewsState] = useState({ loading: false, data: null, error: null })
  const [routePath, setRoutePath] = useState(() => typeof window !== 'undefined' ? window.location.pathname : '/')
  const exploring = routePath === '/explore'
  const finance = routePath === '/finance'
  const stockId = /^\/stock\/([0-9a-fA-F-]{36})$/.exec(routePath)?.[1] ?? null
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuHover, setMenuHover] = useState(null)
  const [recentStocks] = useState(readRecentStocks)
  const [demoState, setDemoState] = useState({ loading: false, error: null })
  const isDemo = session.user?.nickname === DEMO_NICKNAME
  const landing = routePath === '/' && !session.loading && !session.user
  const shouldOfferAlertSettings = interestsState.data.length > 0 && (alertsState.emptyReason === 'NO_ALERT_ENABLED_INTERESTS' || (!alertsState.emptyReason && !interestsState.data.some(item => item.alertEnabled)))
  const [workflowContext, setWorkflowContext] = useState(() => {
    const saved = exploring ? window.history.state?.[EXPLORER_HISTORY_KEY] : null
    return { step: saved?.step ?? (exploring ? 'MAIN' : null), hasTarget: Boolean(saved?.target), hasEvent: Boolean(saved?.eventId) }
  })

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
    // 서버가 잠깐 502·연결 오류를 내도 로그인(데모 포함)이 풀린 것처럼 보이지 않도록 두 번 더 확인한다.
    const fetchUser = (left) => getCurrentUser().catch(error => {
      if (left > 0 && !(error.status === 401 || error.status === 403)) {
        return new Promise(resolve => setTimeout(resolve, left === 2 ? 600 : 1500)).then(() => fetchUser(left - 1))
      }
      throw error
    })
    fetchUser(2).then(user => current() && setSession({ loading: false, user, error: null, notice: null }))
      .catch(error => current() && (error.status === 401 || error.status === 403
        ? becomeAnonymous()
        : setSession({ loading: false, user: null, error, notice: null })))
  }, [becomeAnonymous, beginRequest])

  useEffect(loadSession, [loadSession])

  // 익명 방문자가 랜딩(/)이 아닌 앱 화면에 있으면 한 번만 데모로 자동 입장시킨다(AUTO_DEMO_FOR_VISITORS 참고).
  const autoDemoTried = useRef(false)
  useEffect(() => {
    if (!AUTO_DEMO_FOR_VISITORS || !import.meta.env.PROD || autoDemoTried.current) return
    if (session.loading || session.user || session.error || routePath === '/') return
    let optedOut = false
    try { optedOut = window.sessionStorage.getItem(AUTO_DEMO_OPT_OUT_KEY) === '1' } catch { optedOut = false }
    if (optedOut) return
    autoDemoTried.current = true
    startDemo()
  }, [session.loading, session.user, session.error, routePath]) // eslint-disable-line react-hooks/exhaustive-deps

  const loadMarketIndices = useCallback(() => {
    const current = beginRequest('marketIndices')
    const controller = new AbortController()
    setMarketIndexState({ loading: true, data: null, error: null })
    getLatestMarketIndices(controller.signal)
      .then(data => current() && setMarketIndexState({ loading: false, data, error: null }))
      .catch(error => current() && error.name !== 'AbortError'
        && setMarketIndexState({ loading: false, data: null, error }))
    return () => controller.abort()
  }, [beginRequest])

  useEffect(() => {
    if (routePath !== '/') return undefined
    return loadMarketIndices()
  }, [routePath, loadMarketIndices])

  const [screenerState, setScreenerState] = useState({ loading: false, data: null })
  useEffect(() => {
    if (routePath !== '/') return undefined
    let alive = true
    setScreenerState({ loading: true, data: null })
    getScreener().then(data => alive && setScreenerState({ loading: false, data })).catch(() => alive && setScreenerState({ loading: false, data: null }))
    return () => { alive = false }
  }, [routePath])

  const loadRealGdp = useCallback(() => {
    const current = beginRequest('realGdp')
    const controller = new AbortController()
    setRealGdpState({ loading: true, data: null, error: null })
    getLatestRealGdp(controller.signal)
      .then(data => current() && setRealGdpState({ loading: false, data, error: null }))
      .catch(error => current() && error.name !== 'AbortError'
        && setRealGdpState({ loading: false, data: null, error }))
    return () => controller.abort()
  }, [beginRequest])

  useEffect(() => {
    if (routePath !== '/') return undefined
    return loadRealGdp()
  }, [routePath, loadRealGdp])

  const loadRecentEvents = useCallback(() => {
    const current = beginRequest('recentEvents')
    const controller = new AbortController()
    setRecentEventState({ loading: true, data: null, error: null })
    getRecentEvents(controller.signal)
      .then(data => current() && setRecentEventState({ loading: false, data, error: null }))
      .catch(error => current() && error.name !== 'AbortError'
        && setRecentEventState({ loading: false, data: null, error }))
    return () => controller.abort()
  }, [beginRequest])

  useEffect(() => {
    if (routePath !== '/') return undefined
    return loadRecentEvents()
  }, [routePath, loadRecentEvents])

  const loadMarketNews = useCallback(() => {
    const current = beginRequest('marketNews')
    const controller = new AbortController()
    setMarketNewsState({ loading: true, data: null, error: null })
    getLatestMarketNews(controller.signal)
      .then(data => current() && setMarketNewsState({ loading: false, data, error: null }))
      .catch(error => current() && error.name !== 'AbortError'
        && setMarketNewsState({ loading: false, data: null, error }))
    return () => controller.abort()
  }, [beginRequest])

  useEffect(() => {
    if (routePath !== '/') return undefined
    return loadMarketNews()
  }, [routePath, loadMarketNews])

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

  const loadAlerts = useCallback(() => {
    if (!session.user) return
    const current = beginRequest('alerts')
    setAlertsState({ loading: true, data: [], emptyReason: null, error: null })
    reconcileAlerts().then(body => current() && setAlertsState({ loading: false, data: body.alerts ?? [], emptyReason: body.emptyReason ?? null, error: null }))
      .catch(error => current() && (error.status === 401 || error.status === 403 ? becomeAnonymous() : setAlertsState({ loading: false, data: [], emptyReason: null, error })))
  }, [session.user, becomeAnonymous, beginRequest])

  useEffect(() => { if (session.user && !interestsState.loading) loadAlerts() }, [session.user, interestsState.loading, interestsState.data, loadAlerts])

  async function changeInterest(remove = false, entityId) {
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

  async function changeAlertSetting(enabled, entityId) {
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

  async function startDemo() {
    setMenuOpen(false)
    setDemoState({ loading: true, error: null })
    try {
      await demoLogin()
      const user = await getCurrentUser()
      setSession({ loading: false, user, error: null, notice: '데모 계정으로 들어왔습니다. 관심회사·브리핑·알림과 AI 읽기 도움을 둘러보세요.' })
      setAuthMode(null)
      setDemoState({ loading: false, error: null })
      return true
    } catch (error) { setDemoState({ loading: false, error }); return false }
  }

  useEffect(() => {
    const readOnly = () => setSession(current => ({ ...current, notice: DEMO_READ_ONLY_NOTICE }))
    const planLimit = () => setSession(current => ({ ...current, notice: '오늘 무료 한도를 다 썼어요. 내일 다시 쓸 수 있고, 「요금 안내」에서 AIRA 플러스를 볼 수 있어요.' }))
    window.addEventListener('aira-demo-readonly', readOnly)
    window.addEventListener('aira-plan-limit', planLimit)
    return () => { window.removeEventListener('aira-demo-readonly', readOnly); window.removeEventListener('aira-plan-limit', planLimit) }
  }, [])

  async function performLogout() {
    try { window.sessionStorage.setItem(AUTO_DEMO_OPT_OUT_KEY, '1') } catch { /* 저장소를 못 쓰면 자동 입장만 다시 될 뿐이다 */ }
    setSession(current => ({ ...current, loading: true, error: null }))
    try { await logout(); becomeAnonymous('로그아웃되었습니다. 공개 정보는 계속 볼 수 있습니다.') }
    catch (error) { setSession(current => ({ ...current, loading: false, error })) }
  }

  useEffect(() => {
    const syncRoute = () => {
      const path = window.location.pathname
      const saved = path === '/explore' ? window.history.state?.[EXPLORER_HISTORY_KEY] : null
      setRoutePath(path)
      setWorkflowContext({ step: saved?.step ?? (path === '/explore' ? 'MAIN' : null), hasTarget: Boolean(saved?.target), hasEvent: Boolean(saved?.eventId) })
    }
    window.addEventListener('popstate', syncRoute)
    return () => window.removeEventListener('popstate', syncRoute)
  }, [])

  function openAssessmentInExplorer(eventId, companies = []) {
    const next = assessmentExplorerState(eventId, companies)
    window.history.pushState({ [EXPLORER_HISTORY_KEY]: next }, '', '/explore')
    setWorkflowContext({ step: 'Assess', hasTarget: Boolean(next.target), hasEvent: true })
    setRoutePath('/explore')
  }

  useEffect(() => {
    if (!exploring) return undefined
    const syncWorkflow = event => setWorkflowContext(current => typeof event.detail === 'string'
      ? { ...current, step: event.detail }
      : event.detail)
    window.addEventListener('aira-workflow-context', syncWorkflow)
    return () => window.removeEventListener('aira-workflow-context', syncWorkflow)
  }, [exploring])

  // 막아 두지 않는다: 회사를 고르기 전이면 눌렀을 때 예시 회사로 열어 준다(흐리게만 표시).
  function workflowStepDisabled(step) {
    if (!exploring || step === 'MAIN' || workflowContext.step === step) return false
    if (!workflowContext.hasTarget) return true
    return step === 'Assess' && !workflowContext.hasEvent
  }

  function chooseWorkflowStep(step) {
    if (exploring) window.dispatchEvent(new CustomEvent('aira-workflow-step', { detail: step }))
  }

  async function openSearch(term, view = '') {
    const query = term.trim()
    if (!query) return
    try {
      const found = await searchEntities(query)
      const companies = (found?.entities ?? []).filter(item => item.entityType === 'COMPANY')
      const exact = companies.find(item => item.canonicalName === query) ?? (companies.length === 1 ? companies[0] : null)
      if (exact) { window.location.assign(`/stock/${exact.entityId}${view ? `?v=${view}` : ''}`); return }
    } catch { /* 검색이 안 되면 탐색 화면에서 다시 고른다. */ }
    window.location.assign(`/explore?q=${encodeURIComponent(query)}`)
  }

  return <>
    {!landing && <header className={`site-header ${menuOpen ? 'menu-open' : ''}`} onMouseLeave={() => setMenuHover(null)}><a className="brand" href="/" aria-label="AIRA 홈"><Logo height={40} /></a>
      <MainMenus path={routePath} onHover={setMenuHover} />
      <form role="search" onSubmit={event => { event.preventDefault(); openSearch(search) }}>
        <label htmlFor="company-search">검색</label><input id="company-search" type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="기업명·종목명·종목코드 검색" />
      </form>
      <div className="header-actions">
        <nav id="account-menu" className={`account-nav ${menuOpen ? 'open' : ''}`} aria-label="계정 메뉴" onClick={event => { if (event.target.closest('a,button')) setMenuOpen(false) }}>
          {session.loading && <span className="session-label">세션 확인 중…</span>}
          {!session.loading && !session.user && <><button type="button" className="nav-demo" onClick={startDemo} disabled={demoState.loading}>데모로 둘러보기</button><button type="button" onClick={() => setAuthMode('login')}>로그인</button><button type="button" className="nav-signup" onClick={() => setAuthMode('signup')}>회원가입</button></>}
          {!session.loading && session.user && <><span className="session-user">{isDemo ? '데모 · 읽기 전용' : `사용자 ${session.user.nickname}`}</span><button className="settings-action" aria-label="설정" onClick={() => setSettingsOpen(true)}>설정</button><button type="button" onClick={performLogout}>{isDemo ? '데모 끝내기' : '로그아웃'}</button></>}
        </nav>
        <ThemeToggle />
        <button type="button" className="nav-menu-toggle" aria-controls="account-menu" aria-expanded={menuOpen}
          aria-label={menuOpen ? '메뉴 닫기' : '메뉴 열기'} onClick={() => setMenuOpen(value => !value)}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
            {menuOpen ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}</svg>
        </button>
      </div>
      <SubMenus path={routePath} hover={menuHover} onHover={setMenuHover} />
    </header>}
    {exploring && <aside className={`workflow-sidebar ${sidebarCollapsed ? 'collapsed' : ''}`} aria-label="탐색 단계">
      <button type="button" className="sidebar-toggle" aria-label={sidebarCollapsed ? '사이드바 펼치기' : '사이드바 접기'}
        onClick={() => setSidebarCollapsed(value => !value)}>{sidebarCollapsed ? '›' : '‹'}</button>
      <nav aria-label="AIRA 흐름">
        {[['MAIN', '메인', 'M'], ['Ask', '묻다', 'A'], ['Inspect', '보다', 'I'],
          ['Relate', '잇다', 'R'], ['Assess', '가늠하다', 'A']].map(([step, korean, code]) => exploring
          ? <button key={step} type="button" className={step === 'MAIN' ? 'workflow-main' : ''} data-workflow-step={step} aria-label={`${step} ${korean}`}
              aria-current={workflowContext.step === step ? 'step' : undefined} data-needs-target={workflowStepDisabled(step) ? 'true' : undefined}
              onClick={() => chooseWorkflowStep(step)}><span className="workflow-code" aria-hidden="true">{sidebarCollapsed ? code : step === 'MAIN' ? step : <><b>{step[0]}</b>{step.slice(1)}</>}</span>
              <span className="workflow-label" aria-hidden="true">{korean}</span></button>
          : <a key={step} className={step === 'MAIN' ? 'workflow-main' : ''} data-workflow-step={step}
              href={step === 'MAIN' ? (routePath === '/' ? '#main' : '/') : '/explore'} aria-label={`${step} ${korean}`}>
              <span className="workflow-code" aria-hidden="true">{sidebarCollapsed ? code : step === 'MAIN' ? step : <><b>{step[0]}</b>{step.slice(1)}</>}</span>
              <span className="workflow-label" aria-hidden="true">{korean}</span></a>)}
      </nav>
    </aside>}
    {session.notice && <div className="session-notice" role="status">{session.notice}</div>}
    {session.error && <div className="session-notice error" role="alert">계정 요청을 처리하지 못했습니다. <button onClick={session.user ? performLogout : loadSession}>다시 시도</button></div>}
    <main id="main" className={`${exploring ? '' : 'no-sidebar'}${landing ? ' is-landing' : ''}`}>
      {routePath === '/check' ? <section className="stock-page check-page" aria-labelledby="check-title"><header className="stock-header"><div><p className="eyebrow">주장 확인</p>
        <h1 id="check-title">이 말, 사실이야?</h1><p className="stock-hint">기사나 커뮤니티에서 본 문장을 붙여 넣으면 사업보고서 공식 값과 비교해 맞는지 알려 드려요.</p></div></header>
        <ClaimCheck loggedIn={Boolean(session.user)} onNeedLogin={startDemo} /></section> : landing ? <LandingPage onDemo={startDemo} onLogin={() => setAuthMode('login')} onSignup={() => setAuthMode('signup')} demoState={demoState} />
      : routePath === '/' && session.loading ? <Status busy>불러오는 중입니다.</Status> : routePath === '/plans' ? <PlansPage loggedIn={Boolean(session.user)} /> : routePath === '/status' ? <DataStatusPage /> : routePath === '/classes' ? <ClassPage user={session.loading ? null : session.user} onLogin={() => setAuthMode('login')} onDemo={startDemo} /> : routePath === '/study' ? <StudyPage user={session.loading ? null : session.user} onLogin={() => setAuthMode('login')} /> : routePath === '/market' ? <MarketPage /> : routePath === '/screen' ? <ScreenerPage loggedIn={Boolean(session.user)} onLogin={() => setAuthMode('login')} /> : stockId ? <StockPage companyId={stockId} loggedIn={Boolean(session.user)} onOpenEvent={openAssessmentInExplorer} interest={{
        user: session.user, saved: interestsState.data.some(item => item.entityId === stockId), busy: interestAction.loading,
        error: interestAction.error, readOnly: isDemo, onLogin: () => setAuthMode('login'),
        onToggle: saved => changeInterest(saved, stockId) }} /> : routePath === '/privacy' ? <PrivacyPage /> : finance ? <PersonalFinance user={session.user} onLogin={() => setAuthMode('login')} /> : exploring ? <CanonicalExplorer embedded interest={{
        user: session.user, items: interestsState.data, loading: interestAction.loading, error: interestAction.error, retry: interestAction.retry,
        onLogin: () => setAuthMode('login'), onSave: entityId => changeInterest(false, entityId),
        onRemove: entityId => changeInterest(true, entityId), onAlert: (entityId, enabled) => changeAlertSetting(enabled, entityId),
      }} /> : <>
      <HomeDashboard indexState={marketIndexState} screener={screenerState} onEvidence={openOfficialEvidence}
        aside={<ClaimCheck hero loggedIn={Boolean(session.user)} onNeedLogin={startDemo} />} />
      <section className="home-explore" aria-label="회사 깊게 보기"><div className="home-explore-main">
        <p className="intro-more">회사를 골라 <b>A</b>sk 묻다 · <b>I</b>nspect 보다 · <b>R</b>elate 잇다 · <b>A</b>ssess 가늠하다로 주가·실적·재무·공시를 펼쳐 보세요. 종목 추천과 광고는 하지 않습니다.</p>
        <div className="quick-stocks" aria-label="바로 보기">{['삼성전자', 'SK하이닉스', 'NAVER', '현대자동차', '카카오', 'LG에너지솔루션'].map(name =>
          <button key={name} type="button" onClick={() => openSearch(name)}>{name}</button>)}</div>
        <div className="quick-stocks recent" aria-label="금융사 바로 보기"><span>금융사</span>{['KB금융', '신한지주', '하나금융지주', '우리금융지주', 'NH투자증권', '카카오뱅크', '삼성생명', '삼성카드'].map(name =>
          <button key={name} type="button" onClick={() => openSearch(name)}>{name}</button>)}</div>
        {recentStocks.length > 0 && <div className="quick-stocks recent" aria-label="최근 본 종목"><span>최근 본 종목</span>{recentStocks.map(item =>
          <a key={item.id} href={`/stock/${item.id}`}>{item.name}</a>)}</div>}
        <div className="intro-actions"><a className="secondary-action" href="/screen">조건으로 종목 찾기</a><a className="secondary-action" href="/explore">단계별 탐색 시작</a>
          <button type="button" className="secondary-action" onClick={() => openSearch('삼성전자', 'ask.course')}>처음이라면: 회사 읽기 코스</button></div></div>
        <DemoCard user={session.loading ? null : session.user} onDemo={startDemo} demoState={demoState} onLogout={performLogout} />
      </section>
      {(realGdpState.loading || (realGdpState.data?.observations?.length ?? 0) > 0) && <EconomicIndicatorBoard state={realGdpState} retry={loadRealGdp} onEvidence={openOfficialEvidence} />}
      <HomeMarketSummary screener={screenerState.loading ? null : screenerState} />
      <MarketFilingBoard />
      {!marketNewsState.error && (marketNewsState.loading || (marketNewsState.data?.items?.length ?? 0) > 0) && <MarketNewsBoard state={marketNewsState} retry={loadMarketNews} />}
      {historicalId && <HistoricalAssessment assessmentId={historicalId} openEvidence={openOfficialEvidence} />}

      {session.user && <section id="my-interests" className="content-section interests-section" aria-labelledby="interests-title">
        <div className="section-heading"><span>MY</span><h2 id="interests-title">내 관심회사</h2></div>
        {interestsState.loading && <Status busy>관심회사를 불러오는 중입니다.</Status>}
        {interestsState.error && <ErrorState error={interestsState.error} subject="관심회사" retry={loadInterests} />}
        {!interestsState.loading && !interestsState.error && interestsState.data.length === 0 && <div className="personalization-empty"><Status>아직 저장한 관심회사가 없습니다. 회사를 탐색하고 관심회사로 저장해 보세요.</Status><a className="secondary-action" href="/explore">회사 탐색하기</a></div>}
        <div className="interest-list">{interestsState.data.map(item => <a key={item.entityId} href={item.entityType === 'SECURITY' ? `/explore?q=${encodeURIComponent(item.canonicalName)}` : `/stock/${item.entityId}`}><strong>{item.canonicalName}</strong><span>종목 화면 →</span></a>)}</div>
      </section>}

      {session.user && <section id="my-briefing" className="content-section briefing-section" aria-labelledby="briefing-title">
        <div className="section-heading"><span>BRIEFING</span><h2 id="briefing-title">내 브리핑</h2></div>
        {briefingState.loading && <Status busy>관심회사에서 확인된 내용을 모으는 중입니다.</Status>}
        {briefingState.error && <ErrorState error={briefingState.error} subject="브리핑" retry={loadBriefing} />}
        {!briefingState.loading && !briefingState.error && briefingState.data?.items?.length === 0 &&
          <div className="personalization-empty"><Status>{briefingState.data.emptyReason === 'NO_INTERESTS' ? '관심회사를 저장하면 새로 정리된 변화를 브리핑에서 모아볼 수 있습니다.' : '이 브리핑 기간에 새로 정리된 변화가 없습니다.'}</Status>{briefingState.data.emptyReason !== 'NO_INTERESTS' && <a className="secondary-action" href="/explore">관심회사 살펴보기</a>}</div>}
        {!briefingState.loading && briefingState.data?.items?.length === 0 && interestsState.data.length > 0 && <InterestFilings interests={interestsState.data} />}
        {briefingState.data?.emptyReason !== 'NO_INTERESTS' && briefingState.data?.periodStart && briefingState.data?.periodEnd && <p className="insight-time">정리 기간 {formatDateTime(briefingState.data.periodStart)} — {formatDateTime(briefingState.data.periodEnd)}</p>}
        {briefingState.data?.emptyReason !== 'NO_INTERESTS' && briefingState.data?.generatedAt && <p className="insight-time">브리핑 생성 {formatDateTime(briefingState.data.generatedAt)}</p>}
        {(briefingState.data?.items?.length ?? 0) > 0 && <AiAssist kind="BRIEFING" loggedIn={Boolean(session.user)}
          facts={briefingState.data.items.slice(0, 10).map(item => ({ label: item.companies.map(company => company.companyName).join(' · '),
            value: `${item.eventTitle}${item.summary ? ` — ${item.summary}` : ''}`.slice(0, 600), period: item.occurredAt ? String(item.occurredAt).slice(0, 10) : '' }))} />}
        <div className="briefing-list">{briefingState.data?.items?.map(item => <article className="briefing-card" key={item.assessmentId}>
          <div className="briefing-main"><p className="eyebrow">관련 회사: {item.companies.map(company => company.companyName).join(' · ')}</p><h3>{item.eventTitle}</h3>
            <p className="insight-reason">관심회사 기반 AIRA 분석</p>
            <p className="event-meta">{EVENT_TYPE_LABELS[item.eventType] ?? item.eventType} · {item.occurredAt?.slice(0, 10) ?? '발생시각 미상'}</p>
            <div className="assessment assessment-points">
              <div className="assessment-point"><h4>확인할 의미</h4><p>{item.summary}</p></div>
              <div className="assessment-point uncertainty"><h4>아직 확인할 점</h4><p>{item.uncertainty}</p></div>
            </div>
          </div>
          <aside className="briefing-side" aria-label="브리핑 근거와 이동"><p className="insight-identity">당시 판단 · 판단 식별자 {item.assessmentId} · {item.analysisVersion}</p>
            <button type="button" className="briefing-support-action" onClick={() => setHistoricalId(item.assessmentId)}>당시 판단 보기</button>
            <p className="briefing-current-note">Assess에서는 현재 사건 사실과 현재 판단 근거를 표시합니다.</p><div className="insight-actions"><button type="button" className="primary-action" onClick={() => openAssessmentInExplorer(item.eventId, item.companies)}>현재 판단 보기</button></div>
            {item.evidence?.map(reference => <div className="evidence-reference" key={reference.evidenceId}>
              <a className="official-evidence-action" href={reference.originalUrl} target="_blank" rel="noopener noreferrer">{reference.sourceName} 공식 근거 원문 <span aria-hidden="true">↗</span></a>
              <span>공시 접수번호 {reference.externalId}</span>
              <button type="button" className="briefing-support-action" onClick={() => openOfficialEvidence(reference.evidenceId)}>공식 자료 상세</button>
            </div>)}
          </aside>
        </article>)}</div>
      </section>}

      {session.user && <section id="my-alerts" className="content-section alert-section" aria-labelledby="alerts-title">
        <div className="section-heading"><span>IN APP</span><h2 id="alerts-title">관심회사 알림</h2></div>
        <HomePriceAlerts />
        {alertsState.loading && <Status busy>새로 확인된 내용을 살펴보는 중입니다.</Status>}
        {alertsState.error && <ErrorState error={alertsState.error} subject="알림" retry={loadAlerts} />}
        {!alertsState.loading && !alertsState.error && alertsState.data.length === 0 && <div className="personalization-empty"><Status>{interestsState.data.length === 0 ? '관심회사를 저장하고 회사별 앱 알림을 켜면 새 알림이 이곳에 표시됩니다.' : alertEmptyMessage(interestsState.data, alertsState.data, alertsState.emptyReason)}</Status>{shouldOfferAlertSettings && <a className="secondary-action" href="/explore">회사와 알림 설정 보기</a>}</div>}
        {alertsState.data.length > 0 && <AiAssist kind="ALERT" loggedIn={Boolean(session.user)}
          facts={alertsState.data.slice(0, 5).map(item => ({ label: item.companies.map(company => company.companyName).join(' · '),
            value: `${item.eventTitle} — 새 분석: ${item.summary ?? ''} / 아직 확인할 점: ${item.uncertainty ?? ''}`.slice(0, 600),
            period: item.occurredAt ? String(item.occurredAt).slice(0, 10) : '' }))} />}
        <div className="alert-list">{alertsState.data.map(item => <article className="alert-card" key={item.alertId}>
          <div className="alert-main"><p className="eyebrow">관련 회사: {item.companies.map(company => company.companyName).join(' · ')}</p><h3>{item.eventTitle}</h3>
            <div className="alert-meaning"><div><h4>새 분석</h4><p>{item.summary}</p></div><div className="uncertainty"><h4>아직 확인할 점</h4><p>{item.uncertainty}</p></div></div>
          </div>
          <aside className="alert-side" aria-label="알림 시각과 이동"><div className="alert-times">
            <p className="insight-time">사건 발생 {formatDateTime(item.occurredAt) ?? '발생시각 미상'}</p>
            {item.completedAt && <p className="insight-time">판단 완료 {formatDateTime(item.completedAt)}</p>}
            {item.sentAt && <p className="insight-time">알림 전달 {formatDateTime(item.sentAt)}</p>}
          </div>
          <div className="insight-actions"><button type="button" className="primary-action" onClick={() => openAlertDetail(item.alertId)}>알림 상세 보기</button>
            <button type="button" className="alert-support-action" onClick={() => openAssessmentInExplorer(item.eventId, item.companies)}>현재 판단 보기</button></div></aside>
        </article>)}</div>
        {alertDetailState.loading && <Status busy>정확한 알림 기록을 불러오는 중입니다.</Status>}
        {alertDetailState.error && <ErrorState error={alertDetailState.error} subject="알림 상세" retry={() => openAlertDetail(alertDetailState.alertId)} />}
        {alertDetailState.data && <article className="event-detail" aria-labelledby="alert-detail-title">
          <h3 id="alert-detail-title">정확한 알림 상세</h3>
          <p className="eyebrow">관련 회사: {alertDetailState.data.companies.map(company => company.companyName).join(' · ')}</p>
          <h4>{alertDetailState.data.eventTitle}</h4><p>{alertDetailState.data.summary}</p>
          <p>알림 전달 당시 판단</p><button type="button" className="alert-history-action" onClick={() => setHistoricalId(alertDetailState.data.assessmentId)}>당시 판단 보기</button>
          <div className="alert-metadata-boundary"><strong>분석 메타데이터</strong><p>확신 수준은 사실 확률이나 미래 수익 확률이 아니며, 중요도는 추천 순위나 매매 강도가 아닙니다.</p></div>
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
          <div className="insight-actions"><button type="button" className="secondary-action" onClick={() => openAssessmentInExplorer(alertDetailState.data.eventId, alertDetailState.data.companies)}>현재 판단 보기</button></div>
          {alertDetailState.data.evidence.map(reference => <div className="evidence-reference" key={reference.evidenceId}>
            <strong>{reference.sourceName}</strong>
            <span>근거 식별자 {reference.evidenceId}</span>
            <span>공시 접수번호 {reference.externalId}</span>
            {reference.publishedAt && <span>근거 자료 발행 {formatDateTime(reference.publishedAt)}</span>}
            <span>근거 개정 번호 {reference.revision}</span>
            <a className="official-evidence-action" href={reference.originalUrl} target="_blank" rel="noopener noreferrer">공식 근거 원문 <span aria-hidden="true">↗</span></a>
            <button type="button" className="alert-evidence-action" onClick={() => openOfficialEvidence(reference.evidenceId)}>공식 자료 상세</button>
          </div>)}
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

      <nav className="home-foot-links" aria-label="AIRA 안내"><a href="/plans">요금 안내</a><a href="/status">자료 상태</a><a href="/privacy">개인정보와 이용자 보호</a><span>공식 자료: KRX · OpenDART · 한국은행 ECOS</span></nav>
      </>}
    </main>
    {session.user && settingsOpen && <RecoveryEmailSettings close={() => setSettingsOpen(false)} />}
    {routePath === '/privacy' && <footer><span>AIRA</span><p>개인정보와 이용자 보호 원칙</p></footer>}
    <AiGuide greet={!session.loading && (!session.user || isDemo)} menus={MAIN_MENUS} />
    {(authMode === 'login' || authMode === 'signup') && <AuthPanel mode={authMode} setMode={setAuthMode} close={() => setAuthMode(null)} authenticated={user => { setSession({ loading: false, user, error: null, notice: null }); setAuthMode(null) }} onDemo={startDemo} />}
    {(authMode === 'forgot' || authMode === 'reset' || authMode === 'recovery-confirm') && <RecoveryPanel mode={authMode} token={recoveryEntry.token} setMode={setAuthMode} close={() => setAuthMode(null)} completeLink={() => { setRecoveryEntry({ mode: null, token: '' }); window.history.replaceState({}, '', '/') }} />}
  </>
}
