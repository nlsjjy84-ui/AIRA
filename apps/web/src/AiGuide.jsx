import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import './aiGuide/aiFluid.js'
import { createAiGuide } from './aiGuide/aiGuide.js'
import { STEPS, COURSES, SAME_PLACE, courseIds, isAt, hrefFor, fillInput, iyeyo, placeName, recap, zoneOf } from './GuideOrb.jsx'
import { STOCK_TABS, STOCK_TREE } from './StockPage.jsx'
import { GUIDE } from './stockGuide.js'
import { answerQuestion } from './guideAsk.js'

/**
 * AI 길 안내(2026-10-29 새 모양). 상자(말풍선)를 없애고,
 * - 마우스 화살표 대신 꺾쇠 커서가 버튼을 찰칵 잡아 주고, 커서가 다가가면 가까운 버튼 글씨가 커진다.
 * - 커서 뒤로 빛이 옅게 번진다(천천히 움직이면 거의 안 보이고, 빠르게 움직일 때만 짧게).
 * - 설명은 상자 없이 빛나는 글씨로 짚은 버튼 옆에만 뜬다. 아무것도 가리거나 막지 않는다.
 * - 화면의 버튼을 하나씩(넓이), 탭 안의 작은 메뉴·세부 칸까지(깊이) 설명한다.
 * 코스(3분·10분·평가자용)와 걸음(STEPS)은 그대로 쓰고, 페이지가 새로 열려도 sessionStorage로 이어 간다.
 */

const STORE_KEY = 'aira-guide-v2'
const GREETED_KEY = 'aira-guide-greeted'
const ACCENT = ['#b7e36a', '#5fd4c4', '#7aa2ff', '#f0c96a']

/** 큰 메뉴 아래 작은 메뉴 한 줄 설명(AIRA 화면 설명과 같은 말). */
export const SUBMENU_TEXT = {
  '시장 지도': '시가총액 크기로 그린 시장 한 장이에요. 칸 크기는 회사 크기, 색은 등락률이에요.',
  지수: '코스피·코스닥과 KRX 지수 시리즈가 오늘 몇 개 오르고 내렸는지 봐요.',
  '환율·금리·금': '한국은행 ECOS 환율·금리·원자재와 KRX 국내 금 시세예요.',
  ETF: '상장 ETF 전체의 NAV·괴리율·따라가는 지수를 봐요.',
  업종: '업종별로 오늘 얼마나 오르고 내렸는지 봐요.',
  '상승 상위': '오늘 가장 많이 오른 종목이에요. 추천이 아니라 공식 종가 순서예요.',
  '하락 상위': '오늘 가장 많이 내린 종목이에요.',
  '거래대금 상위': '오늘 돈이 가장 많이 오간 종목이에요. 관심이 몰린 곳이에요.',
  '최고가 근처': '저장된 기간 최고가에 가까운 종목이에요.',
  '최저가 근처': '저장된 기간 최저가에 가까운 종목이에요.',
  '조건으로 찾기': '말로 조건을 적으면 AI가 검색 조건으로 바꾸고, 공식 숫자로 회사를 골라요.',
  '회사·사건 탐색': '묻다 · 보다 · 잇다 · 가늠하다 네 단계로 한 회사를 따라가요.',
  '이 말, 사실이야?': '들은 말을 붙여 넣으면 공시 숫자와 맞는지 판정하고 접수번호를 붙여요.',
  '공부 노트': '이해한 용어와 공부 노트가 회사별로 모여요.',
  '공부 반': '강사가 같은 회사 과제를 내고 수강생 진도를 한 화면에서 봐요.',
  '관심회사·브리핑·알림': '관심회사의 새 공시·가격 알림이 모여요.',
  '내 금융': '내 금융 기록을 보는 곳이에요. 개인 정보라 가입 후 한 번 더 확인해야 열려요.',
}

const textOf = el => (el?.childNodes?.[0]?.textContent || el?.textContent || '').trim()

function stockItem(name) {
  for (const tab of STOCK_TABS) for (const item of tab[2]) if (item[0] === name) return item
  return null
}
function nodeFor(path) {
  const top = STOCK_TREE.find(node => node[0] === path[0])
  const second = top && (top[4] ?? []).find(node => node[0] === path[1])
  return { top, second }
}

/** 마우스가 머문 버튼이 무엇인지 한 줄로. 설명할 수 없는 곳은 null(그냥 지나간다). */
export function guideText(el, menus = []) {
  if (!el?.matches) return null
  const name = textOf(el)
  if (el.matches('.ld-action')) return { title: '가입 없이 둘러보기', text: '데모 계정으로 바로 들어가요. 로그인 기능까지 다 써 볼 수 있어요.' }
  if (el.matches('.header-menus a')) {
    const menu = menus.find(m => m[1] === name)
    return menu ? { title: name, text: `작은 메뉴 <b>${menu[2].length}개</b> — ${menu[2].map(i => i[0]).join(' · ')}` } : null
  }
  if (el.matches('.header-submenus a')) return SUBMENU_TEXT[name] ? { title: name, text: SUBMENU_TEXT[name] } : null
  if (el.matches('.quick-stocks button, .quick-stocks a')) return { title: name, text: `${name} 종목 화면으로 가요. 주가·실적·재무·공시를 큰 탭 6개로 펼쳐 봐요.` }
  if (el.matches('.claim-check-examples button')) return { title: '예시 문장', text: `「${name}」 — 누르면 공시 숫자와 맞춰 일치·다름을 판정해요.` }
  if (el.matches('.stock-tabs button')) {
    const tab = STOCK_TABS.find(t => t[1] === name)
    return tab ? { title: name, text: `작은 메뉴 <b>${tab[2].length}개</b> — ${tab[2].map(i => i[0]).join(' · ')}` } : null
  }
  if (el.matches('.stock-subtabs button')) {
    const item = stockItem(name)
    if (!item) return null
    const guide = GUIDE[item[1].join('/')] ?? GUIDE[item[1].slice(0, 2).join('/')]
    const { second } = nodeFor(item[1])
    const text = guide?.why ?? second?.[2]
    return text ? { title: name, text } : null
  }
  if (el.matches('.stock-chips button')) {
    const path = (new URLSearchParams(window.location.search).get('v') || 'inspect.summary').split('.')
    const { top, second } = nodeFor(path)
    const node = second && (second[3] ?? []).find(n => n[1] === name)
    const guide = node && top && GUIDE[`${top[0]}/${second[0]}/${node[0]}`]
    return node ? { title: name, text: guide?.why ?? `${second[1]} 안의 「${name}」이에요.` } : null
  }
  if (el.matches('.home-foot-links a')) {
    const foot = { '요금 안내': '무료와 AIRA 플러스를 견줘 봐요.', '자료 상태': '공식 자료 세 곳을 언제 가져왔는지 기준일과 함께 보여 줘요.', '개인정보와 이용자 보호': '어떤 정보를 받고 받지 않는지 적어 두었어요.' }[name]
    return foot ? { title: name, text: foot } : null
  }
  return null
}

/** 지금 위치(빵 부스러기). */
export function whereNow() {
  if (typeof document === 'undefined') return ''
  if (document.querySelector('.ld-action')) return '첫 화면'
  const path = window.location.pathname
  if (path.startsWith('/stock/')) {
    const parts = [document.querySelector('#stock-title')?.textContent, document.querySelector('.stock-tabs [aria-current="page"]')?.textContent,
      document.querySelector('.stock-subtabs [aria-current="page"]')?.textContent, textOf(document.querySelector('.stock-chips [aria-current="page"]'))]
    return parts.filter(Boolean).join(' › ')
  }
  return placeName(path)
}

export function readGuide() { try { return JSON.parse(window.sessionStorage.getItem(STORE_KEY) ?? 'null') ?? { course: null, index: 0, done: [], log: {} } } catch { return { course: null, index: 0, done: [], log: {} } } }
export function writeGuide(state) { try { window.sessionStorage.setItem(STORE_KEY, JSON.stringify(state)) } catch { /* 저장이 안 되면 이 화면에서만 기억한다. */ } }

/** 걸음을 마치면 다음으로. 이미 들른 곳(같은 화면의 평가자용 걸음 포함)은 건너뛴다. */
export function advance(state) {
  const list = courseIds(state.course)
  const id = list[state.index]
  const done = [...new Set([...(state.done ?? []), id, ...(SAME_PLACE[id] ? [SAME_PLACE[id]] : [])])]
  let index = state.index + 1
  while (index < list.length && done.includes(list[index])) index += 1
  return { ...state, index, done }
}

export function startCourse(state, course) {
  const list = courseIds(course)
  let index = 0
  while (index < list.length && (state.done ?? []).includes(list[index])) index += 1
  return { ...state, course, index }
}

const isTestEnv = () => typeof navigator !== 'undefined' && /jsdom/i.test(navigator.userAgent)

export default function AiGuide({ greet = false, menus = [], navigate = url => window.location.assign(url) }) {
  const guideRef = useRef(null)
  const runningRef = useRef(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [asking, setAsking] = useState({ text: '', busy: false })

  useEffect(() => {
    if (isTestEnv() || typeof window === 'undefined') return undefined
    const g = createAiGuide({
      name: 'AIRA AI', accent: ACCENT, effect: 'splash', calm: true, fade: 7, radius: 0.1, sim: 96, dye: 640,
      explain: el => guideText(el, menus), where: whereNow,
    })
    guideRef.current = g
    return () => { g.stop(); guideRef.current = null }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // 코스를 따라간다. 페이지가 새로 열리면 저장된 걸음부터 이어 간다.
  async function drive() {
    const g = guideRef.current
    if (!g || runningRef.current) return
    runningRef.current = true
    try {
      let state = readGuide()
      while (state.course && state.index < courseIds(state.course).length) {
        const id = courseIds(state.course)[state.index]
        const step = STEPS[id]
        if (step.skip?.()) { state = advance(state); writeGuide(state); continue }
        const loc = { path: window.location.pathname, v: new URLSearchParams(window.location.search).get('v') }
        if (!isAt(step, loc)) {
          const go = await g.speak(`지금은 ${step.landing ? '다른 화면' : placeName(loc.path)}에 계세요. 다음 목적지는 ${iyeyo(`「${step.place}」`)}.`,
            { kicker: 'AIRA AI · 길 안내', buttons: [{ label: '데려다 드릴게요', value: 'go', main: true }, { label: '안내 끝내기', value: 'end' }] })
          if (go === 'end') { writeGuide({ ...state, course: null, index: 0 }); return }
          const door = step.landing || step.path === '/' ? document.querySelector('.ld-action') : null
          if (door) { door.click(); await new Promise(r => setTimeout(r, 1500)); continue }
          navigate(hrefFor(step)); return
        }
        const zone = zoneOf(state.course, id)
        const where = id === 'entry' ? '입구' : `${COURSES[state.course].name} · ${zone?.name ?? ''} ${zone ? `${zone.ids.indexOf(id) + 1}/${zone.ids.length}` : ''}`
        const ok = await g.point({
          target: step.target, say: step.say, look: step.look, react: step.react, after: step.after, label: step.place,
          fill: step.fill ? () => fillInput(step.fill.selector, step.fill.value) : undefined,
        }, where)
        if (!ok) await g.speak('화면을 불러오는 중이라 이 걸음은 건너뛸게요.', { kicker: 'AIRA AI' })
        if (id === 'claim' || id === 'jClaim') { const verdict = document.querySelector('.claim-check-hero .claim-check-verdict strong')?.textContent?.trim(); if (verdict) state = { ...state, log: { ...state.log, verdict } } }
        state = advance(state); writeGuide(state)
      }
      if (state.course) {
        const lines = recap(state.done ?? [], state.log ?? {}, state.course).filter(line => !line.small.includes('풍이'))
        const judge = state.course === 'judge', short = state.course === 'short'
        const choice = await g.speak(`<b>${COURSES[state.course].name} 끝!</b> ${lines.map(line => `${line.big} ${line.small}`).join(' · ')}. 이제 아무 버튼에나 마우스를 올려 보세요. 무엇인지 바로 알려 드려요.`,
          { kicker: 'AIRA AI · 오늘의 길 결산', buttons: [...(short || judge ? [{ label: '10분 자세히 이어 가기', value: 'long', main: true }] : []), ...(judge ? [{ label: 'GitHub 열기', value: 'gh' }] : []), { label: '여기까지 볼게요', value: 'end' }] })
        if (choice === 'gh') window.open('https://github.com/nlsjjy84-ui/AIRA', '_blank', 'noopener')
        writeGuide({ ...state, course: null, index: 0 })
        if (choice === 'long') { writeGuide(startCourse({ ...state, course: null }, 'long')); runningRef.current = false; drive(); return }
      }
    } finally { runningRef.current = false }
  }

  function choose(course) {
    setMenuOpen(false)
    writeGuide(startCourse(readGuide(), course))
    drive()
  }

  // 페이지가 열릴 때: 코스 중이면 이어 가고, 처음 온 사람에게는 코스 고르기를 한 번 띄운다.
  useEffect(() => {
    if (isTestEnv()) return undefined
    const timer = setTimeout(() => {
      if (readGuide().course) { drive(); return }
      if (!greet) return
      try { if (window.sessionStorage.getItem(GREETED_KEY) === '1') return; window.sessionStorage.setItem(GREETED_KEY, '1') } catch { /* 한 번만 */ }
      setMenuOpen(true)
    }, 1200)
    return () => clearTimeout(timer)
  }, [greet]) // eslint-disable-line react-hooks/exhaustive-deps

  async function onAsk(event) {
    event.preventDefault()
    const question = asking.text.trim()
    if (!question || !guideRef.current) return
    setAsking({ text: question, busy: true })
    let answer = null
    try { answer = await answerQuestion(question) } catch { answer = { say: '지금은 답을 찾지 못했어요. 잠시 뒤 다시 물어봐 주세요.', href: null } }
    setAsking({ text: '', busy: false })
    setMenuOpen(false)
    if (!answer) return
    const go = await guideRef.current.speak(answer.say, { kicker: answer.method === 'AI' ? 'AIRA AI · 질문을 이해했어요' : 'AIRA AI · 화면 설명에서 찾았어요',
      buttons: answer.href ? [{ label: answer.place ? `${iyeyo(`「${answer.place}」`).replace(/(이에요|예요)$/, '')}로 데려다 드릴게요` : '데려다 드릴게요', value: 'go', main: true }, { label: '괜찮아요', value: 'no' }] : [] })
    if (go === 'go' && answer.href) navigate(answer.href)
  }

  if (typeof document === 'undefined') return null
  const resume = readGuide().course
  return createPortal(<div className="aig-launch-wrap">
    {menuOpen && <div className="aig-menu" role="dialog" aria-label="AIRA 길 안내">
      <p className="aig-menu-q">처음이세요? AIRA를 둘러볼까요?</p>
      <button type="button" onClick={() => choose('short')}>3분 간략<small>핵심 네 곳</small></button>
      <button type="button" onClick={() => choose('long')}>10분 자세히<small>4구역 열세 곳</small></button>
      <button type="button" onClick={() => choose('judge')}>평가자용 빠른 코스<small>5분 · 면접관·선생님께 · AI를 믿을 수 있게 만든 다섯 곳</small></button>
      <form className="aig-ask" onSubmit={onAsk}>
        <label htmlFor="aig-ask-input">AI에게 길 묻기</label>
        <input id="aig-ask-input" value={asking.text} maxLength={120} disabled={asking.busy} onChange={event => setAsking({ text: event.target.value, busy: false })} placeholder="예: 배당은 어디서 봐? · PER이 뭐야?" />
      </form>
      <button type="button" className="aig-menu-later" onClick={() => setMenuOpen(false)}>나중에 볼게요</button>
    </div>}
    <button type="button" className="aig-launch" aria-expanded={menuOpen} onClick={() => { if (resume && !menuOpen) { drive(); return } setMenuOpen(open => !open) }}>
      ✦ {resume ? '이어서 안내' : '길 안내'}
    </button>
  </div>, document.body)
}
