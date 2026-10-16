import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import Logo from './Logo.jsx'

// 랜딩: 화면 하나. 머리글도 그 화면 안에 떠 있고, 스크롤은 장면의 진행도만 바꾼다.
// AIRA 로고의 조각(들은 말) → 공시와 맞는 조각만 제자리로 → 로고 A → A가 눕고 실적 막대 → 막대마다 공시로 이어지는 빛줄기 → A가 다시 선다.
// 글은 늘 같은 자리에서 서로 겹쳐 바뀐다. 숫자는 실제 공시 값(삼성전자 2025 사업보고서 · 2026 분기보고서 · 2026.10.08 잠정실적).

const LandingScene = lazy(() => import('./LandingScene.jsx'))

export const SCENES = [0, 0.16, 0.33, 0.52, 0.7, 0.87]
export const MOMENTS = [
  { title: ['숫자는 누구에게나,', '이해는 더 깊게'], text: 'AIRA는 공시로 확인된 숫자만 이어 붙입니다.', cta: true },
  { title: ['들은 말은', '조각일 뿐입니다.'], text: '기사, 커뮤니티, 단톡방. 같은 회사인데 숫자가 다 다릅니다.' },
  { title: ['공시와 맞는 조각만', '제자리를 찾습니다.'], text: '거래소, 전자공시, 한국은행. 세 곳의 공식 자료로 하나씩 맞춰 봅니다.' },
  { title: ['맞춰진 숫자는', '한 문장이 됩니다.'], text: '2026년 2분기 영업이익은 직전 분기보다 56% 늘었어요.' },
  { title: ['모든 숫자 뒤에는', '공시가 이어져 있습니다.'], text: '막대마다 원문과 접수번호. 분기보고서보다 먼저 나온 잠정실적까지.' },
  { title: ['사라·팔라고 하지 않습니다.', '이해를 돕습니다.'], text: '본 것은 내 공부 노트로 쌓입니다.', cta: true },
]
// 장면 옆에 잠깐 떠오르는 한 줄(들은 말 · 공시 값). [시작, 끝, 글, 종류]
const NOTES = [
  [0.17, 0.36, '“삼성전자 매출 400조 넘었대”', 'heard'],
  [0.21, 0.36, '“2분기 영업이익 반토막이래”', 'heard'],
  [0.4, 0.53, '공시 값 333.6조 원 · 2025 사업보고서', 'fact'],
  [0.57, 0.7, '2026년 2분기 영업이익 89.49조 원', 'fact'],
  [0.74, 0.87, '잠정실적 107.4조 원 · 접수번호 20261008800004', 'fact'],
]

const clamp = (n, a = 0, b = 1) => Math.max(a, Math.min(b, n))
const smooth = n => { n = clamp(n); return n * n * (3 - 2 * n) }

export default function LandingPage({ onDemo, onLogin, onSignup, demoState = { loading: false, error: null } }) {
  const root = useRef(null)
  const stage = useRef(null)
  const momentRefs = useRef([])
  const noteRefs = useRef([])
  const progress = useRef(0)
  const [active, setActive] = useState(0)
  const [paused, setPaused] = useState(() => typeof window !== 'undefined' && Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches))

  useEffect(() => {
    if (typeof window === 'undefined') return undefined
    let target = 0, raf = 0, last = performance.now(), shown = 0
    const read = () => {
      const r = root.current?.getBoundingClientRect()
      if (!r || !stage.current) return
      target = clamp(-r.top / Math.max(1, root.current.offsetHeight - stage.current.offsetHeight))
    }
    const paint = p => {
      momentRefs.current.forEach((el, i) => {
        if (!el) return
        const start = SCENES[i], end = SCENES[i + 1] ?? 1.2
        const fadeIn = i === 0 ? 1 : smooth((p - start) / 0.06)
        const fadeOut = i === MOMENTS.length - 1 ? 1 : 1 - smooth((p - (end - 0.06)) / 0.06)
        const a = clamp(fadeIn * fadeOut)
        el.style.opacity = a
        el.style.visibility = a > 0.005 ? 'visible' : 'hidden'
        el.style.transform = `translate3d(0, ${(1 - fadeIn) * 22 - (1 - fadeOut) * 16}px, 0)`
      })
      noteRefs.current.forEach((el, i) => {
        if (!el) return
        const [a, b] = NOTES[i]
        const o = smooth((p - a) / 0.04) * (1 - smooth((p - (b - 0.04)) / 0.04))
        el.style.opacity = o; el.style.visibility = o > 0.01 ? 'visible' : 'hidden'
        el.style.transform = `translate3d(0, ${(1 - o) * 10}px, 0)`
      })
      let sel = 0
      for (let i = 0; i < SCENES.length; i++) if (p >= SCENES[i] - 0.03) sel = i
      if (sel !== shown) { shown = sel; setActive(sel) }
    }
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    const loop = now => {
      raf = requestAnimationFrame(loop)
      const dt = Math.min(0.05, (now - last) / 1000); last = now
      progress.current += (target - progress.current) * (reduced ? 1 : 1 - Math.exp(-dt * 6))
      paint(progress.current)
    }
    read(); paint(0)
    window.addEventListener('scroll', read, { passive: true })
    window.addEventListener('resize', read)
    raf = requestAnimationFrame(loop)
    return () => { cancelAnimationFrame(raf); window.removeEventListener('scroll', read); window.removeEventListener('resize', read) }
  }, [])

  const actions = last => <div className="landing-actions">
    <button type="button" className="landing-action" onClick={onDemo} disabled={demoState.loading}>
      <span>{demoState.loading ? '들어가는 중…' : '가입 없이 둘러보기'}</span><i aria-hidden="true">↗</i></button>
    {last ? <button type="button" className="landing-link" onClick={onSignup}>회원가입</button> : <a className="landing-link" href="/market">시장 먼저 보기</a>}
  </div>

  return <div className="landing" ref={root}>
    <div className="landing-stage" ref={stage}>
      <div className="landing-backdrop" aria-hidden="true" />
      <Suspense fallback={null}><LandingScene progress={progress} paused={paused} /></Suspense>
      <div className="landing-veil" aria-hidden="true" />
      <header className="landing-masthead">
        <a className="landing-brand" href="/" aria-label="AIRA 처음으로"><Logo height={40} /></a>
        <nav className="landing-top" aria-label="계정">
          <a href="/market">시장</a>
          <button type="button" onClick={onLogin}>로그인</button>
          <button type="button" className="landing-top-strong" onClick={onDemo} disabled={demoState.loading}>둘러보기 <span aria-hidden="true">↗</span></button>
        </nav>
      </header>
      <div className="landing-narrative">
        {MOMENTS.map((m, i) => <section key={i} ref={el => { momentRefs.current[i] = el }} className={`landing-moment${active === i ? ' is-active' : ''}`}
          aria-hidden={active !== i} inert={active !== i ? '' : undefined}>
          {i === 0 ? <h1 id="landing-title">{m.title[0]}<br />{m.title[1]}</h1> : <h2>{m.title[0]}<br /><em>{m.title[1]}</em></h2>}
          <p>{m.text}</p>
          {m.cta && actions(i === MOMENTS.length - 1)}
        </section>)}
        {demoState.error && <p className="landing-note" role="status">지금은 둘러보기에 들어갈 수 없어요. 잠시 후 다시 눌러 주세요.</p>}
      </div>
      <div className="landing-notes" aria-hidden="true">
        {NOTES.map(([, , text, kind], i) => <span key={i} ref={el => { noteRefs.current[i] = el }} className={`landing-chip is-${kind} chip-${i}`}>{text}</span>)}
      </div>
      <div className="landing-progress" aria-hidden="true">{MOMENTS.map((_, i) => <i key={i} className={active >= i ? 'on' : ''} />)}</div>
      <button type="button" className="landing-motion" aria-pressed={paused} aria-label={paused ? '움직임 재생' : '움직임 일시정지'} onClick={() => setPaused(v => !v)}>
        <svg viewBox="0 0 20 20" aria-hidden="true"><path d={paused ? 'M7 4l8 6-8 6z' : 'M7 5v10M13 5v10'} /></svg></button>
      <nav className="landing-foot" aria-label="AIRA 안내"><a href="/plans">요금</a><a href="/status">자료 상태</a><a href="/privacy">개인정보</a><span>KRX · OpenDART · 한국은행 ECOS</span></nav>
    </div>
  </div>
}
