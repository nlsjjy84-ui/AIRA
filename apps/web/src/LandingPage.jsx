import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import Logo from './Logo.jsx'
import './landing.css'

// 랜딩: A·I·R·A 네 정거장. 글은 스크롤을 따라 흘러가고, 뒤의 3D 장면은 그 자리에 맞춰 이어서 바뀐다.
// 첫 화면 → Ask 묻다 → Inspect 보다 → Relate 잇다 → Assess 가늠하다 → 공식 자료 세 곳 → 끝.
// 메뉴 설명은 종목 화면의 메뉴 나무(StockPage STOCK_TREE)와 같은 말이다.
// 숫자는 실제 공시 값(삼성전자 2025 사업보고서 · 2026 분기보고서 · 2026.10.08 잠정실적).

const LandingScene = lazy(() => import('./LandingScene.jsx'))

export const STATIONS = [['A', 'sk', '묻다'], ['I', 'nspect', '보다'], ['R', 'elate', '잇다'], ['A', 'ssess', '가늠하다']]
const CLAIM = '“삼성전자 매출 400조 넘었대”'
const QUARTERS = [['2025', '3분기', '12.17'], ['2025', '4분기', '20.07'], ['2026', '1분기', '57.23'], ['2026', '2분기', '89.49'], ['2026', '3분기 잠정', '107.4']]
const INSPECT = ['회사 개요', '매출 구성', '주가', '봉 차트', '분기 실적', '재무상태', '재무제표 원문', '비율', '배당', '자기주식', '최대주주', '직원', '공시', '뉴스']
const RELATE = [['주가 × 공시', '주가 그래프 위에 공시 날짜를 점으로'], ['주가 × 시장', '시작일을 100으로 맞춰 지수와 견주기'], ['공시 전후 주가', '공시 전날과 5거래일 뒤 종가'],
  ['업종 안에서', '같은 업종 회사들과 나란히'], ['여러 종목 비교', '최대 4개 회사를 한 화면에'], ['회사 일정', '보고서 제출 기한과 작년 이맘때 공시']]
const AXES = ['성장', '수익성', '효율', '안정성', '규모']
const ASSESS = ['한눈에 정리', '작년 대비 변화표', '업종 중간값 대비', '확인 체크리스트', '이해 확인 퀴즈', '아직 모르는 것']
const SOURCES = [['KRX', '한국거래소', '하루 단위 공식 시세 · 지수 · 금 시세. 실시간 시세는 아닙니다.'],
  ['OpenDART', '금융감독원 전자공시', '사업보고서 · 분기보고서 · 잠정실적, 그리고 접수번호'], ['ECOS', '한국은행 경제통계', '환율 · 금리 같은 경제 지표']]

const clamp = (n, a = 0, b = 1) => Math.max(a, Math.min(b, n))
const smooth = n => { n = clamp(n); return n * n * (3 - 2 * n) }

export default function LandingPage({ onDemo, onLogin, onSignup, demoState = { loading: false, error: null } }) {
  const root = useRef(null)
  const progress = useRef(0)
  const claimRef = useRef(null)
  const claimBox = useRef(null)
  const quarterRefs = useRef([])
  const [active, setActive] = useState(0)
  const [paused, setPaused] = useState(() => typeof window !== 'undefined' && Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches))

  useEffect(() => {
    if (typeof window === 'undefined' || !root.current) return undefined
    const el = root.current
    const secs = [...el.querySelectorAll('[data-sec]')]
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    let raf = 0, last = performance.now(), s = 0, shown = -1, typed = -1, counted = false
    const countUp = () => {
      quarterRefs.current.forEach((b, k) => {
        if (!b) return
        const to = Number(b.dataset.to), dec = (b.dataset.to.split('.')[1] || '').length
        if (reduced) { b.textContent = to.toFixed(dec); return }
        const t0 = performance.now() + k * 140
        const step = now => { const e = smooth((now - t0) / 1300); b.textContent = (to * e).toFixed(dec); if (e < 1) requestAnimationFrame(step) }
        requestAnimationFrame(step)
      })
    }
    const loop = now => {
      raf = requestAnimationFrame(loop)
      const dt = clamp((now - last) / 1000, 0, 0.05); last = now
      const mid = window.innerHeight * 0.5
      const locals = secs.map(sec => { const r = sec.getBoundingClientRect(); return clamp((mid - r.top) / Math.max(1, r.height)) })
      let target = 0
      secs.forEach((sec, i) => { const r = sec.getBoundingClientRect(); if (r.top <= mid && r.bottom > mid) target = i - 0.5 + locals[i] })
      if (secs.length && secs[secs.length - 1].getBoundingClientRect().bottom <= mid) target = secs.length - 1
      target = clamp(target, 0, secs.length - 1)
      s += (target - s) * (reduced ? 1 : 1 - Math.exp(-dt * 6))
      progress.current = s
      const st = Math.round(s)
      if (st !== shown) { shown = st; el.dataset.station = String(st); setActive(st) }
      // 묻다: 들은 말이 쳐지고, 공시 값과 맞춰 본다
      const la = locals[1] ?? 0
      const n = Math.round(clamp((la - 0.06) / 0.24) * CLAIM.length)
      if (n !== typed && claimRef.current) { typed = n; claimRef.current.textContent = CLAIM.slice(0, n) }
      claimBox.current?.classList.toggle('is-checked', la > 0.36)
      claimBox.current?.classList.toggle('is-judged', la > 0.46)
      // 보다: 숫자가 올라간다
      if (!counted && (locals[2] ?? 0) > 0.12) { counted = true; countUp() }
    }
    raf = requestAnimationFrame(loop)
    const reveal = [...el.querySelectorAll('[data-reveal]')]
    const io = 'IntersectionObserver' in window ? new IntersectionObserver(entries => entries.forEach(e => { if (e.isIntersecting) { e.target.classList.add('is-in'); io.unobserve(e.target) } }), { threshold: 0.2 }) : null
    reveal.forEach(n => (io ? io.observe(n) : n.classList.add('is-in')))
    return () => { cancelAnimationFrame(raf); io?.disconnect() }
  }, [])

  const goTo = i => root.current?.querySelectorAll('[data-sec]')[i]?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  const actions = last => <div className="ld-actions">
    <button type="button" className="ld-action" onClick={onDemo} disabled={demoState.loading}>
      <span>{demoState.loading ? '들어가는 중…' : '가입 없이 둘러보기'}</span><i aria-hidden="true">↗</i></button>
    {last ? <button type="button" className="ld-link" onClick={onSignup}>회원가입</button> : <a className="ld-link" href="/market">시장 먼저 보기</a>}
  </div>
  const code = (i, kick) => <p className="ld-code" data-reveal><b>{STATIONS[i][0]}</b>{STATIONS[i][1]}<span>{STATIONS[i][2]}</span>{kick && <small>{kick}</small>}</p>

  return <div className="landing" ref={root} data-station="0">
    <div className="ld-stage" aria-hidden="true">
      <div className="ld-bg" />
      <Suspense fallback={null}><LandingScene progress={progress} paused={paused} /></Suspense>
      <div className="ld-veil" />
    </div>
    <header className="ld-mast">
      <a className="ld-brand" href="/" aria-label="AIRA 처음으로"><Logo height={38} /></a>
      <nav className="ld-top" aria-label="계정">
        <a href="/market">시장</a>
        <button type="button" onClick={onLogin}>로그인</button>
        <button type="button" className="ld-top-strong" onClick={onDemo} disabled={demoState.loading}>둘러보기 <span aria-hidden="true">↗</span></button>
      </nav>
    </header>
    <nav className="ld-rail" aria-label="네 가지 일">
      {STATIONS.map(([c, rest, ko], i) => <button key={i} type="button" className={active === i + 1 ? 'is-on' : active > i + 1 ? 'is-past' : ''} onClick={() => goTo(i + 1)}
        aria-label={`${c}${rest} ${ko}`}><b>{c}</b><span>{ko}</span></button>)}
    </nav>

    <div className="ld-flow">
      <section className="ld-sec ld-hero" data-sec aria-labelledby="landing-title">
        <div className="ld-copy">
          <p className="ld-kick">공식 숫자로 보는 시장 정보</p>
          <h1 id="landing-title">숫자는 누구에게나,<br />이해는 더 깊게</h1>
          <p className="ld-lead">AIRA는 공시로 확인된 숫자만 이어 붙입니다. 이름은 네 가지 일, <b>A</b>sk · <b>I</b>nspect · <b>R</b>elate · <b>A</b>ssess의 첫 글자예요.</p>
          {actions(false)}
        </div>
        <p className="ld-cue" aria-hidden="true">스크롤<i /></p>
      </section>

      <section className="ld-sec" data-sec aria-label="Ask 묻다">
        <div className="ld-copy">
          {code(0)}
          <h2 data-reveal>들은 말을<br /><em>공식 값에 물어봅니다.</em></h2>
          <p className="ld-lead" data-reveal>기사나 커뮤니티에서 본 숫자를 붙여 넣으면 공식 값과 접수번호로 맞는지 확인합니다.</p>
          <div className="ld-claim" ref={claimBox}>
            <p className="ld-heard"><q ref={claimRef} aria-label={CLAIM} /><i className="ld-caret" aria-hidden="true" /></p>
            <p className="ld-fact">공시 값 <b>333.6조 원</b> · 2025 사업보고서</p>
            <p className="ld-verdict">들은 말과 다릅니다 · 66.4조 원 차이</p>
          </div>
          <p className="ld-list" data-reveal>AI에게 묻기 · 이 말, 사실이야? · 공시 원문에 묻기 · 용어 사전<small>AI는 공식 값만 가지고 답하고, 자료에 없는 것은 모른다고 합니다.</small></p>
        </div>
      </section>

      <section className="ld-sec" data-sec aria-label="Inspect 보다">
        <div className="ld-copy">
          {code(1)}
          <h2 data-reveal>모아 둔 공식 자료를<br /><em>펼쳐 봅니다.</em></h2>
          <p className="ld-lead" data-reveal>그래프 · 표 · 용어 풀이가 함께 있고, 숫자마다 원문과 접수번호가 이어져 있습니다.</p>
          <ol className="ld-quarters" data-reveal aria-label="삼성전자 분기 영업이익">
            {QUARTERS.map(([y, q, v], k) => <li key={k} className={k === QUARTERS.length - 1 ? 'is-prelim' : ''}>
              <span>{y}<br />{q}</span><b ref={n => { quarterRefs.current[k] = n }} data-to={v}>{v}</b><small>조 원</small></li>)}
          </ol>
          <p className="ld-note" data-reveal>삼성전자 분기 영업이익 · 마지막은 2026.10.08 잠정실적(접수번호 20261008800004)</p>
          <div className="ld-marquee" aria-hidden="true"><div>{[...INSPECT, ...INSPECT].map((w, k) => <span key={k}>{w}</span>)}</div></div>
        </div>
      </section>

      <section className="ld-sec" data-sec aria-label="Relate 잇다">
        <div className="ld-copy">
          {code(2)}
          <h2 data-reveal>다른 자료를<br /><em>같은 날짜에 겹쳐 봅니다.</em></h2>
          <p className="ld-lead" data-reveal>주가 위에 공시 날짜를 찍고, 시장 지수와 시작일을 맞춰 견줍니다.</p>
          <p className="ld-caveat" data-reveal>함께 나타났다는 것이지, 원인이라는 뜻은 아닙니다.</p>
          <ul className="ld-rel">{RELATE.map(([a, b], k) => <li key={k} data-reveal style={{ '--d': `${k * 70}ms` }}><b>{a}</b><span>{b}</span></li>)}</ul>
        </div>
      </section>

      <section className="ld-sec" data-sec aria-label="Assess 가늠하다">
        <div className="ld-copy">
          {code(3)}
          <h2 data-reveal>판단은 내가,<br /><em>근거는 AIRA가.</em></h2>
          <p className="ld-lead" data-reveal>같은 업종 안에서 어디쯤인지 다섯 방향을 한 도형에 그립니다. 점수나 등급이 아닙니다.</p>
          <p className="ld-axes" data-reveal>{AXES.map(a => <span key={a}>{a}</span>)}</p>
          <p className="ld-list" data-reveal>{ASSESS.join(' · ')}<small>사라 · 팔라는 말은 하지 않습니다.</small></p>
        </div>
      </section>

      <section className="ld-sec ld-sources" data-sec aria-labelledby="ld-sources-title">
        <div className="ld-copy">
          <p className="ld-kick" data-reveal>공식 자료 세 곳</p>
          <h2 id="ld-sources-title" data-reveal>출처가 분명한 숫자만<br /><em>씁니다.</em></h2>
          <dl className="ld-src">{SOURCES.map(([k, name, text], i) => <div key={k} data-reveal style={{ '--d': `${i * 90}ms` }}><dt><b>{k}</b>{name}</dt><dd>{text}</dd></div>)}</dl>
        </div>
      </section>

      <section className="ld-sec ld-final" data-sec aria-labelledby="ld-final-title">
        <div className="ld-copy">
          <h2 id="ld-final-title" data-reveal>사라·팔라고 하지 않습니다.<br /><em>이해를 돕습니다.</em></h2>
          <p className="ld-lead" data-reveal>본 것은 내 공부 노트로 쌓입니다.</p>
          {actions(true)}
          {demoState.error && <p className="ld-error" role="status">지금은 둘러보기에 들어갈 수 없어요. 잠시 후 다시 눌러 주세요.</p>}
        </div>
        <nav className="ld-foot" aria-label="AIRA 안내"><a href="/plans">요금</a><a href="/status">자료 상태</a><a href="/privacy">개인정보</a><span>KRX · OpenDART · 한국은행 ECOS</span></nav>
      </section>
    </div>

    <button type="button" className="ld-motion" aria-pressed={paused} aria-label={paused ? '움직임 재생' : '움직임 일시정지'} onClick={() => setPaused(v => !v)}>
      <svg viewBox="0 0 20 20" aria-hidden="true"><path d={paused ? 'M7 4l8 6-8 6z' : 'M7 5v10M13 5v10'} /></svg></button>
  </div>
}
