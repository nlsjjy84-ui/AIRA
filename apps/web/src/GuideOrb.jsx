import { useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { answerQuestion, ro } from './guideAsk.js'

/**
 * 풍이와 결이의 길 안내.
 * 풍이(망원경으로 앞을 내다보는 탐험가)와 결이(배운 것을 보따리에 담는 아이)는 잇결과 같은 대표 캐릭터다.
 * 작은 불빛이 다음에 누를 곳으로 앞서가고, 두 캐릭터가 한두 줄로 "이게 뭔지, 왜 좋은지"만 말한다.
 *
 * 반영한 원칙(2026 온보딩 사례 조사):
 *  - 처음에 3분 간략 / 10분 자세히를 고르게 한다(목적별 2~5개 선택지).
 *  - 3분은 핵심 4곳, 10분은 4구역(구역당 2~4곳)으로 나눈다(4단계를 넘으면 이탈이 늘어남).
 *  - 직접 눌러 봐야 「다음」이 빛난다(행동으로 배우는 투어). 결과를 읽고 반응한다(맞았어요/틀린 말이었네요).
 *  - 말풍선은 140자 이내. 길을 벗어나면 "지금 ○○ → 다음 △△"와 [데려다 드릴게요].
 *  - 진행은 등불이 하나씩 켜지는 것으로 보여 주고, 저장해 두었다가 언제든 이어서 한다. 한 번 닫으면 다시 강요하지 않는다.
 */

export const SAMSUNG = '/stock/24f7883e-6992-452e-a5e0-099b886a923c'
const STORE_KEY = 'aira-guide-v1'
const GREETED_KEY = 'aira-guide-greeted'

export const SPEAKERS = {
  pung: { name: '풍이', img: '/guide/pung.webp', role: '망원경으로 길을 내다봐요' },
  gyeol: { name: '결이', img: '/guide/gyeol.webp', role: '배운 걸 보따리에 담아요' },
}

const one = selector => () => document.querySelector(selector)
const nth = (selector, index) => () => document.querySelectorAll(selector)[index] ?? null
const withText = (selector, text) => () => [...document.querySelectorAll(selector)].find(el => el.textContent.trim().includes(text)) ?? null
const formOf = selector => () => { const input = document.querySelector(selector); return input?.closest('form') ?? input ?? null }
const textOf = selector => document.querySelector(selector)?.textContent?.trim() ?? ''

export const STEPS = {
  entry: {
    who: 'pung', place: '첫 화면', landing: true, target: one('.ld-action'),
    skip: () => Boolean(document.querySelector('.session-user')),
    say: '가입 없이 바로 들어가 볼게요. 빛나는 「가입 없이 둘러보기」를 눌러 주세요.',
  },
  claim: {
    who: 'pung', place: '홈 · 이 말, 사실이야?', path: '/', target: nth('.claim-check-hero .claim-check-examples button', 1),
    say: '‘삼성전자 작년 매출이 400조를 넘었대’라는 말, 맞을까요? 빛나는 「틀린 말」 예시를 눌러 보세요.',
    after: '판정이 나왔어요. AI는 문장만 나누고, 맞는지는 공시 숫자와 직접 비교해서 정해요.',
    react: () => {
      const verdict = textOf('.claim-check-hero .claim-check-verdict strong')
      if (!verdict) return null
      if (verdict === '다름') return '틀린 말이었네요! 사업보고서 공식 값과 다르다고 나왔어요. 접수번호로 원문도 바로 열 수 있어요.'
      return `‘${verdict}’으로 나왔어요. AI는 문장만 나누고, 맞는지는 공시 숫자와 직접 비교해요.`
    },
  },
  quote: {
    who: 'pung', place: '삼성전자 › 확인 › 공시 원문에 묻기', path: SAMSUNG, v: 'ask.quote', target: formOf('#filing-quote-question'),
    fill: { selector: '#filing-quote-question', value: '영업이익은 얼마인가요?' },
    say: '사업보고서 원문에서 답을 글자 그대로 찾아요. 「대신 적어 드릴게요」를 누르고 「원문에서 찾기」를 눌러 보세요.',
    after: '요약이 아니라 원문 문장 그대로예요. 문단 번호로 어디에 적혀 있는지 바로 확인해요.',
  },
  askAi: {
    who: 'gyeol', place: '삼성전자 › 확인 › AI에게 묻기', path: SAMSUNG, v: 'ask.ai', target: withText('.stock-find .stock-range button', '매출은 늘고 있어?'),
    say: '이 회사 공식 값만 가지고 AI가 답해요. 「매출은 늘고 있어?」를 눌러 보세요.',
    after: 'AI가 참고한 자료가 아래에 다 나와요. 자료에 없는 숫자가 섞이면 AI 답을 버리고 규칙으로 정리해요.',
  },
  market: {
    who: 'pung', place: '시장 지도', path: '/market', target: one('svg[aria-label^="업종별 시장 지도"]'), look: true,
    say: '시장 전체를 한 장으로 봐요. 칸 크기는 시가총액, 색은 등락률이에요. 큰 칸이 시장을 움직이는 회사이고, 모두 한국거래소 공식 종가예요.',
  },
  screen: {
    who: 'pung', place: '조건 검색', path: '/screen', target: formOf('#screen-ai-q'),
    fill: { selector: '#screen-ai-q', value: '영업이익률 10% 이상이고 부채비율 100% 이하인 회사' },
    say: '조건을 말로 적으면 AI가 검색 조건으로 바꿔 줘요. 「대신 적어 드릴게요」 다음 「조건으로 바꾸기」를 눌러 보세요.',
    after: 'AI는 말을 조건으로 바꾸기만 해요. 회사를 고르는 건 공식 숫자로 계산해요.',
  },
  easy: {
    who: 'gyeol', place: '삼성전자 › 종목정보 › 한눈에', path: SAMSUNG, v: 'inspect.summary', target: withText('.ai-assist-action', '쉬운 말로 풀어보기'),
    say: '삼성전자에 도착했어요. 「쉬운 말로 풀어보기」를 누르면 화면 숫자를 쉬운 말로 풀어 드려요.',
    after: '화면에 있는 값과 기간만 썼어요. 자료에 없는 말은 지어내지 않아요.',
  },
  filingsChart: {
    who: 'pung', place: '삼성전자 › 차트 › 공시와 함께', path: SAMSUNG, v: 'relate.filings.chart', target: one('#stock-body svg'),
    say: '주가 그래프 위에 공시 날짜를 점으로 찍었어요. 점을 눌러 그날 어떤 공시가 있었는지 보세요.',
    after: '함께 일어났다는 뜻이지 원인이라는 뜻은 아니에요. AIRA는 원인을 단정하지 않아요.',
  },
  quarters: {
    who: 'gyeol', place: '삼성전자 › 실적·재무 › 분기 실적', path: SAMSUNG, v: 'inspect.earnings.quarters', target: one('#stock-body svg'),
    say: '분기마다 얼마를 팔고 얼마를 남겼는지예요. 막대를 눌러 숫자를 확인해 보세요.',
    after: '분기 실적은 계절에 따라 오르내려요. 작년 같은 분기와 견주면 흐름이 보여요.',
  },
  radar: {
    who: 'gyeol', place: '삼성전자 › 정리·공부 › 다섯 방향 요약', path: SAMSUNG, v: 'assess.radar', target: one('svg[aria-label="다섯 방향 요약 도형"]'), look: true,
    say: '성장·수익성·효율·안정성·규모를 같은 업종 안 위치로 그렸어요. 점수나 등급이 아니에요.',
  },
  quiz: {
    who: 'gyeol', place: '삼성전자 › 정리·공부 › 퀴즈', path: SAMSUNG, v: 'assess.quiz', target: one('.quiz-list'),
    say: '이 회사 실제 숫자로 낸 퀴즈예요. 첫 문제의 답을 하나 골라 보세요.',
    after: '풀이에 계산까지 나와 있어요. 맞힌 용어는 「내 공부」에 쌓여요.',
    react: () => {
      const result = textOf('.quiz-why strong')
      if (result.startsWith('⭕')) return '맞았어요! 맞힌 용어는 제 보따리처럼 「내 공부」에 차곡차곡 쌓여요.'
      if (result.startsWith('❌')) return '아쉬워요! 아래 풀이에 왜 그런지 계산까지 나와 있어요. 한 번 읽어 보세요.'
      return null
    },
  },
  course: {
    who: 'gyeol', place: '삼성전자 › 정리·공부 › 회사 읽기 코스', path: SAMSUNG, v: 'ask.course', target: one('.course-steps'), look: true,
    say: '회사를 읽는 기본 순서 10단계예요. 따라가며 적은 한 줄이 제 보따리처럼 「내 공부」에 쌓여요.',
    note: '데모 계정은 읽기 전용이라 저장은 되지 않아요.',
  },
  study: {
    who: 'gyeol', place: '내 공부', path: '/study', target: one('#study-title'), look: true,
    say: '여기가 「내 공부」예요. 이해한 용어와 공부 노트가 회사별로 모여요.',
  },
  plans: {
    who: 'pung', place: '요금 안내', path: '/plans', target: one('.plan-card.plus'), look: true,
    say: '무료로 충분히 쓰고, 더 깊게 보려면 AIRA 플러스가 있어요. 아직 무료 한도를 걸기 전이라 지금은 다 무료예요.',
  },
}

/** 평가자(면접관·선생님)용: 화면 기능보다 "AI를 어떻게 믿을 수 있게 만들었는지"를 보여 주는 다섯 곳. */
const claimVerdictReaction = () => {
  const verdict = textOf('.claim-check-hero .claim-check-verdict strong')
  if (!verdict) return null
  return `‘${verdict}’ 판정이에요. 이 판정은 AI가 아니라 서버가 사업보고서 공식 값·접수번호와 직접 비교해 낸 거예요. AI는 문장만 나눴어요.`
}
Object.assign(STEPS, {
  jClaim: {
    who: 'pung', place: '홈 · 이 말, 사실이야?', path: '/', target: nth('.claim-check-hero .claim-check-examples button', 1),
    say: 'AI 판정을 그대로 믿지 않게 만든 곳이에요. 빛나는 「틀린 말」 예시를 눌러 보세요.',
    after: 'AI는 문장만 나누고, 맞는지는 서버가 공식 값과 직접 비교해서 정해요.', react: claimVerdictReaction,
  },
  jQuote: {
    who: 'pung', place: '삼성전자 › 확인 › 공시 원문에 묻기', path: SAMSUNG, v: 'ask.quote', target: formOf('#filing-quote-question'),
    fill: { selector: '#filing-quote-question', value: '영업이익은 얼마인가요?' },
    say: 'AI가 원문을 지어내지 못하게 막았어요. 「대신 적어 드릴게요」 다음 「원문에서 찾기」를 눌러 보세요.',
    after: '보이는 인용은 서버가 원문과 글자 하나까지 같은지 확인한 것만이에요. 다르면 버리고 원문 문단을 그대로 보여 줘요.',
  },
  jAskAi: {
    who: 'gyeol', place: '삼성전자 › 확인 › AI에게 묻기', path: SAMSUNG, v: 'ask.ai', target: withText('.stock-find .stock-range button', '매출은 늘고 있어?'),
    say: 'AI 답 속 숫자를 서버가 검사해요. 「매출은 늘고 있어?」를 눌러 보세요.',
    after: '답의 숫자가 아래 ‘참고한 자료’에 없으면 서버가 AI 답을 버리고 규칙 정리로 바꿔요. 사라·팔라는 말도 막아요.',
  },
  jScreen: {
    who: 'pung', place: '조건 검색', path: '/screen', target: formOf('#screen-ai-q'),
    fill: { selector: '#screen-ai-q', value: '영업이익률 10% 이상이고 부채비율 100% 이하인 회사' },
    say: 'AI에게 일을 좁게 맡긴 예예요. 「대신 적어 드릴게요」 다음 「조건으로 바꾸기」를 눌러 보세요.',
    after: 'AI는 정해진 조건으로 바꾸기만 하고, 회사를 고르는 계산은 서버가 공식 숫자로 해요.',
  },
  jStatus: {
    who: 'gyeol', place: '자료 상태', path: '/status', target: one('#data-status-title'), look: true,
    say: '공식 자료 세 곳(KRX·OpenDART·ECOS)을 하루 네 번 자동으로 모아요. 자료마다 기준일과 가져온 시각을 따로 보여 줘요.',
  },
})

/** 평가자용 걸음을 마치면 같은 화면의 일반 걸음도 본 것으로 친다(10분 코스로 이어 갈 때 같은 화면을 다시 보여 주지 않게). */
export const SAME_PLACE = { jClaim: 'claim', jQuote: 'quote', jAskAi: 'askAi', jScreen: 'screen' }

export const COURSES = {
  short: { name: '3분 간략', zones: [{ name: '핵심 네 곳', ids: ['claim', 'easy', 'quote', 'course'] }] },
  long: { name: '10분 자세히', zones: [
    { name: '① 사실 확인', ids: ['claim', 'quote', 'askAi'] },
    { name: '② 시장', ids: ['market', 'screen'] },
    { name: '③ 회사 읽기', ids: ['easy', 'filingsChart', 'quarters', 'radar'] },
    { name: '④ 공부', ids: ['quiz', 'course', 'study', 'plans'] },
  ] },
  judge: { name: '평가자용 빠른 코스', zones: [{ name: '기술 검증 다섯 곳', ids: ['jClaim', 'jQuote', 'jAskAi', 'jScreen', 'jStatus'] }] },
}

/** 코스의 걸음 순서. 맨 앞은 늘 입구(로그인돼 있으면 저절로 지나간다). */
export function courseIds(course) {
  return ['entry', ...COURSES[course].zones.flatMap(zone => zone.ids)]
}

export function zoneOf(course, id) {
  return COURSES[course].zones.find(zone => zone.ids.includes(id)) ?? null
}

export function placeName(path) {
  if (path === '/') return '홈'
  if (path === '/market') return '시장'
  if (path === '/screen') return '조건 검색'
  if (path === SAMSUNG) return '삼성전자'
  if (path.startsWith('/stock/')) return '다른 종목 화면'
  if (path === '/study') return '내 공부'
  if (path === '/plans') return '요금 안내'
  if (path === '/check') return '사실 확인'
  if (path === '/explore') return '길 찾기'
  if (path === '/classes') return '공부 반'
  if (path === '/status') return '자료 상태'
  return '다른 화면'
}

export function hrefFor(step) {
  return step.v ? `${step.path}?v=${step.v}` : step.path
}

/** 지금 그 걸음의 자리에 와 있는지. 종목 화면은 ?v= 경로의 앞부분이 맞으면 같은 자리다. */
export function isAt(step, loc) {
  const landingShown = Boolean(document.querySelector('.ld-action'))
  if (step.landing) return landingShown
  if (step.path !== loc.path) return false
  if (step.path === '/') return !landingShown
  if (step.v) return (loc.v || 'inspect.summary').startsWith(step.v)
  return true
}

/** React가 관리하는 입력칸에 글자를 넣는다(값을 바꾼 뒤 input 이벤트를 보내야 화면 상태도 바뀐다). */
export function fillInput(selector, value) {
  const input = document.querySelector(selector)
  if (!input) return false
  const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(input), 'value')?.set
  if (setter) setter.call(input, value); else input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  input.focus?.()
  return true
}

function readStore() {
  try { return JSON.parse(window.sessionStorage.getItem(STORE_KEY) ?? 'null') } catch { return null }
}
function writeStore(value) {
  try { window.sessionStorage.setItem(STORE_KEY, JSON.stringify(value)) } catch { /* 저장이 안 되면 이 화면에서만 기억한다. */ }
}
function greetedOnce() {
  try {
    if (window.sessionStorage.getItem(GREETED_KEY) === '1') return true
    window.sessionStorage.setItem(GREETED_KEY, '1')
  } catch { /* 저장이 안 되면 한 번 인사하고 만다. */ }
  return false
}

function docRect(el) {
  const r = el.getBoundingClientRect()
  return { top: Math.round(r.top + window.scrollY), left: Math.round(r.left + window.scrollX), width: Math.round(r.width), height: Math.round(r.height) }
}

/** 말풍선 자리: 기본은 오른쪽 아래. 목적지를 가리면 왼쪽 아래(넓은 화면) 또는 위쪽(좁은 화면). */
export function bubbleSide(target, box, vw, vh) {
  const hits = r => target.left < r.right && target.right > r.left && target.top < r.bottom && target.bottom > r.top
  const w = box.width, h = box.height
  const bottom = { top: vh - 74 - h, bottom: vh - 74 }
  const top = { top: 72, bottom: 72 + h }
  const right = { left: vw - 16 - w, right: vw - 16 }
  const left = { left: 16, right: 16 + w }
  // 기본 자리(오른쪽 아래)부터, 목적지를 가리지 않는 첫 자리를 고른다. 모두 가리면 기본 자리.
  const spots = [['', { ...right, ...bottom }], ['is-left', { ...left, ...bottom }], ['is-top', { ...right, ...top }], ['is-top is-left', { ...left, ...top }]]
  return (spots.find(([, spot]) => !hits(spot)) ?? spots[0])[0]
}

const EMPTY = { course: null, index: 0, open: false, done: [], log: {} }

/** 완주 결산(Spotify Wrapped처럼 오늘 한 일을 큰 글씨로). */
export function recap(done, log = {}) {
  const lines = [{ big: `${done.filter(id => id !== 'entry').length}곳`, small: '불빛을 따라 들른 곳' }]
  if (log.verdict) lines.push({ big: `‘${log.verdict}’`, small: '들은 말을 공시로 확인한 판정' })
  if (log.quiz) lines.push({ big: log.quiz === 'right' ? '정답!' : '한 번 더', small: '이 회사 실제 숫자로 푼 퀴즈' })
  lines.push({ big: `${log.asks ?? 0}번`, small: '풍이에게 직접 물어본 질문' })
  return lines
}

function Face({ who, small = false }) {
  const speaker = SPEAKERS[who]
  return <img className={small ? 'guide-face is-small' : 'guide-face'} src={speaker.img} alt="" width={small ? 30 : 44} height={small ? 30 : 44} />
}

/** 등불 줄: 걸음마다 하나, 들른 곳은 켜지고 지금 걸음은 깜빡인다. */
function Lanterns({ course, current, done }) {
  return <div className="guide-lanterns" aria-label="진행">
    {COURSES[course].zones.map(zone => <span key={zone.name} className="guide-zone">
      {zone.ids.map(id => <i key={id} className={done.includes(id) ? 'is-lit' : id === current ? 'is-now' : ''} title={STEPS[id].place} />)}
    </span>)}
  </div>
}

/** 처음 묻는 사람이 막막하지 않게 눌러서 바로 물어볼 예시(퍼플렉시티의 질문 예시처럼). */
export const ASK_IDEAS = ['배당은 어디서 봐?', 'PER이 뭐야?', 'SK하이닉스 실적 보여줘']

/** 말풍선 아래 입력칸: 풍이에게 말로 묻는다. */
function AskForm({ busy, onAsk }) {
  const [text, setText] = useState('')
  return <form className="guide-ask" onSubmit={event => { event.preventDefault(); if (text.trim()) onAsk(text.trim()) }}>
    <label htmlFor="guide-ask-input">풍이에게 묻기</label>
    <div><span className="guide-ask-field"><input id="guide-ask-input" value={text} maxLength={120} onChange={event => setText(event.target.value)} placeholder="예: 배당은 어디서 봐? · PER이 뭐야?" /></span>
      <button type="submit" className="guide-ask-go" disabled={busy || !text.trim()} aria-label="묻기">{busy ? '…' : '➜'}</button></div>
    <div className="guide-ask-ideas" aria-label="이렇게 물어보세요">{ASK_IDEAS.map(idea => <button key={idea} type="button" disabled={busy}
      onClick={() => { setText(idea); onAsk(idea) }}>{idea}</button>)}</div>
  </form>
}

export default function GuideOrb({ greet = false, navigate = url => window.location.assign(url), tickMs = 450, ask = answerQuestion }) {
  const [asked, setAsked] = useState({ status: 'idle', answer: null })
  const [state, setState] = useState(() => ({ ...EMPTY, ...(readStore() ?? {}) }))
  const [view, setView] = useState({ path: '/', v: null, at: false, rect: null, reaction: null })
  const [tried, setTried] = useState(false)
  const targetRef = useRef(null)
  const bubbleRef = useRef(null)
  const [side, setSide] = useState('')
  const scrolledFor = useRef('')

  const ids = state.course ? courseIds(state.course) : []
  const finished = Boolean(state.course) && state.index >= ids.length
  const stepId = state.course && !finished ? ids[state.index] : null
  const step = stepId ? STEPS[stepId] : null

  const update = useCallback(patch => setState(current => {
    const change = typeof patch === 'function' ? patch(current) : patch
    if (!change || Object.keys(change).length === 0) return current
    const next = { ...current, ...change }
    writeStore(next)
    return next
  }), [])

  // 처음 온 사람(비로그인·데모)에게 한 번만 인사한다. 닫으면 다시 띄우지 않는다.
  useEffect(() => {
    if (!greet || state.course || state.open) return undefined
    const timer = setTimeout(() => { if (!greetedOnce()) update({ open: true }) }, 1200)
    return () => clearTimeout(timer)
  }, [greet]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { setTried(false) }, [state.course, state.index])

  // 지금 위치와 목적지를 주기적으로 살핀다(데이터가 늦게 그려지는 화면이 많다).
  useEffect(() => {
    function tick() {
      const loc = { path: window.location.pathname, v: new URLSearchParams(window.location.search).get('v') }
      if (step?.skip?.()) { update(current => ({ index: current.index + 1, done: [...new Set([...current.done, stepId])] })); return }
      const at = step ? isAt(step, loc) : false
      const el = at ? step.target() : null
      if (targetRef.current && targetRef.current !== el) delete targetRef.current.dataset.guideTarget
      if (el) el.dataset.guideTarget = '1'
      targetRef.current = el
      const rect = el ? docRect(el) : null
      const reaction = at && step.react ? step.react() : null
      if (reaction && (stepId === 'claim' || stepId === 'jClaim')) { const verdict = textOf('.claim-check-hero .claim-check-verdict strong'); if (verdict) update(current => current.log?.verdict === verdict ? {} : { log: { ...current.log, verdict } }) }
      if (reaction && stepId === 'quiz') { const quiz = textOf('.quiz-why strong').startsWith('⭕') ? 'right' : 'wrong'; update(current => current.log?.quiz === quiz ? {} : { log: { ...current.log, quiz } }) }
      // 말풍선이 목적지를 가리면 반대편(넓은 화면은 왼쪽, 좁은 화면은 위쪽)으로 비켜선다.
      const box = bubbleRef.current?.getBoundingClientRect?.()
      const live = el?.getBoundingClientRect?.()
      if (box && live && box.width) setSide(bubbleSide(live, box, window.innerWidth, window.innerHeight))
      else if (!el) setSide('')
      setView(current => {
        const same = current.path === loc.path && current.v === loc.v && current.at === at && current.reaction === reaction
          && JSON.stringify(current.rect) === JSON.stringify(rect)
        return same ? current : { path: loc.path, v: loc.v, at, rect, reaction }
      })
      const key = `${state.course}:${state.index}`
      if (el && scrolledFor.current !== key) {
        scrolledFor.current = key
        // 목적지를 화면 위쪽 1/5쯤에 두어, 아래쪽 말풍선이 목적지를 가리지 않게 한다.
        const r = el.getBoundingClientRect()
        const y = Math.max(0, window.scrollY + r.top - window.innerHeight * 0.2)
        if (typeof navigator !== 'undefined' && /jsdom/i.test(navigator.userAgent)) el.scrollIntoView?.({ block: 'center' })
        else window.scrollTo({ top: y, behavior: 'smooth' })
      }
    }
    tick()
    const timer = setInterval(tick, tickMs)
    return () => clearInterval(timer)
  }, [step, stepId, state.course, state.index, tickMs, update])

  // 빛나는 곳을 직접 누르면 「다음」이 켜진다.
  useEffect(() => {
    const onClick = event => { const el = targetRef.current; if (el && el.contains(event.target)) setTried(true) }
    document.addEventListener('click', onClick, true)
    return () => document.removeEventListener('click', onClick, true)
  }, [])

  function start(course) {
    const done = state.done
    const list = courseIds(course)
    let index = 0
    while (index < list.length && done.includes(list[index])) index += 1
    update({ course, index, open: true })
  }
  function next() {
    update(current => {
      const list = courseIds(current.course)
      const id = list[current.index]
      const done = [...new Set([...current.done, id, ...(SAME_PLACE[id] ? [SAME_PLACE[id]] : [])])]
      let index = current.index + 1
      while (index < list.length && done.includes(list[index])) index += 1
      return { index, done }
    })
  }
  function back() { update(current => ({ index: Math.max(1, current.index - 1) })) }
  function finish() { update({ course: null, index: 0, open: false }) }
  async function onAsk(question) {
    update(current => ({ log: { ...current.log, asks: (current.log?.asks ?? 0) + 1 } }))
    setAsked({ status: 'loading', answer: null })
    try {
      const answer = await ask(question)
      setAsked({ status: answer ? 'done' : 'idle', answer, question })
    } catch {
      setAsked({ status: 'done', answer: { method: 'RULE', say: '지금은 답을 찾지 못했어요. 잠시 뒤 다시 물어봐 주세요.', href: null }, question })
    }
  }

  function goThere() {
    if (step.path === '/' || step.landing) {
      const door = document.querySelector('.ld-action')
      if (door) { door.click(); return }
    }
    navigate(hrefFor(step))
  }

  const reacted = Boolean(view.reaction)
  const acted = tried || reacted || Boolean(step?.look)
  const zone = step && state.course ? zoneOf(state.course, stepId) : null
  const position = step && zone ? `${zone.ids.indexOf(stepId) + 1} / ${zone.ids.length}` : ''

  let body = null
  if (state.open && asked.answer) {
    const answer = asked.answer
    body = <>
      <header className="guide-who"><Face who="pung" />
        <div><strong>풍이</strong><small>{answer.method === 'AI' ? 'AI가 질문을 이해했어요' : '화면 설명에서 찾았어요'}</small></div>
        <button type="button" className="guide-x" aria-label="안내 접기" onClick={() => update({ open: false })}>×</button></header>
      <p className="guide-asked">“{asked.question}”</p>
      <p className="guide-say" aria-live="polite">{answer.say}</p>
      <div className="guide-actions">
        {answer.href && <button type="button" className="guide-glow" onClick={() => { setAsked({ status: 'idle', answer: null }); navigate(answer.href) }}>{answer.place ? `${ro(`「${answer.place}」`)} 데려다 드릴게요` : '데려다 드릴게요'}</button>}
      </div>
      <div className="guide-links">
        <button type="button" className="guide-link" onClick={() => setAsked({ status: 'idle', answer: null })}>{state.course ? '길 안내로 돌아가기' : '다른 것 묻기'}</button>
      </div>
      <AskForm busy={asked.status === 'loading'} onAsk={onAsk} />
    </>
  } else if (state.open && !state.course) {
    body = <>
      <header className="guide-who"><span className="guide-faces"><Face who="pung" /><Face who="gyeol" /></span>
        <div><strong>풍이와 결이</strong><small>AIRA 길 안내</small></div></header>
      <p className="guide-say">처음이세요? 불빛을 따라 AIRA를 둘러보세요.</p>
      <div className="guide-actions is-choice">
        <button type="button" className="guide-glow" onClick={() => start('short')}>3분 간략<small>핵심 네 곳</small></button>
        <button type="button" className="guide-glow is-soft" onClick={() => start('long')}>10분 자세히<small>4구역 열세 곳</small></button>
        <button type="button" className="guide-glow is-soft is-wide" onClick={() => start('judge')}>평가자용 빠른 코스<small>5분 · 면접관·선생님께 · AI를 믿을 수 있게 만든 다섯 곳</small></button>
      </div>
      <button type="button" className="guide-link" onClick={() => update({ open: false })}>나중에 볼게요</button>
      <AskForm busy={asked.status === 'loading'} onAsk={onAsk} />
    </>
  } else if (state.open && finished) {
    const short = state.course === 'short'
    const judge = state.course === 'judge'
    body = <>
      <header className="guide-who"><span className="guide-faces"><Face who="pung" /><Face who="gyeol" /></span>
        <div><strong>풍이와 결이</strong><small>{COURSES[state.course].name} 완주</small></div></header>
      <Lanterns course={state.course} current={null} done={state.done} />
      <div className="guide-recap" aria-label="오늘의 AIRA 길 결산"><p className="guide-recap-title">오늘의 AIRA 길 결산</p>
        {recap(state.done, state.log).map(line => <div key={line.small}><b>{line.big}</b><span>{line.small}</span></div>)}</div>
      <p className="guide-say">{judge ? '평가자용 코스 끝! 설계 원칙 114개와 자동 테스트 기록은 GitHub와 포트폴리오 PDF에 정리해 두었어요.'
        : short ? '3분 길 안내 끝! 핵심 네 곳을 다 들렀어요. 10분 자세히 코스가 열렸어요.' : '4구역을 모두 돌았어요! 이제 자유롭게 둘러보세요. 오른쪽 아래 풍이를 누르면 언제든 다시 안내해요.'}</p>
      <div className="guide-actions">
        {judge && <a className="guide-glow" href="https://github.com/nlsjjy84-ui/AIRA" target="_blank" rel="noreferrer">GitHub 열기</a>}
        {(short || judge) && <button type="button" className={judge ? 'guide-glow is-soft' : 'guide-glow'} onClick={() => start('long')}>{judge ? '나머지 기능 이어 보기(10분 코스)' : '10분 자세히 이어 가기'}</button>}
        <button type="button" className="guide-link" onClick={finish}>{short || judge ? '여기까지 볼게요' : '안내 닫기'}</button>
      </div>
      <AskForm busy={asked.status === 'loading'} onAsk={onAsk} />
    </>
  } else if (state.open && step) {
    const speaker = SPEAKERS[step.who]
    const offRoute = !view.at
    const text = offRoute
      ? `지금은 ${step.landing ? '다른 화면' : placeName(view.path)}에 계세요. 다음 목적지는 「${step.place}」예요.`
      : view.reaction ?? (tried && step.after ? step.after : step.say)
    body = <>
      <header className="guide-who"><Face who={step.who} />
        <div><strong>{speaker.name}</strong><small>{stepId === 'entry' ? '입구' : `${COURSES[state.course].name} · ${zone?.name} ${position}`}</small></div>
        <button type="button" className="guide-x" aria-label="안내 접기" onClick={() => update({ open: false })}>×</button></header>
      {stepId !== 'entry' && <Lanterns course={state.course} current={stepId} done={state.done} />}
      <p className="guide-say" aria-live="polite">{text}</p>
      {!offRoute && step.note && <p className="guide-note">{step.note}</p>}
      {!offRoute && !view.rect && <p className="guide-note">화면을 불러오는 중이에요. 잠시만요.</p>}
      <div className="guide-actions">
        {offRoute && <button type="button" className="guide-glow" onClick={goThere}>데려다 드릴게요</button>}
        {!offRoute && step.fill && !tried && <button type="button" className="guide-glow is-soft" onClick={() => { fillInput(step.fill.selector, step.fill.value) }}>대신 적어 드릴게요</button>}
        {!offRoute && acted && stepId !== 'entry' && <button type="button" className="guide-glow" onClick={next}>다음</button>}
        {!offRoute && !acted && <span className="guide-hint">빛나는 곳을 눌러 보세요</span>}
      </div>
      <div className="guide-links">
        {state.index > 1 && <button type="button" className="guide-link" onClick={back}>이전</button>}
        {stepId !== 'entry' && !acted && <button type="button" className="guide-link" onClick={next}>건너뛰기</button>}
        <button type="button" className="guide-link" onClick={finish}>안내 끝내기</button>
      </div>
      <AskForm busy={asked.status === 'loading'} onAsk={onAsk} />
    </>
  }

  const showLight = Boolean(step) && view.at && view.rect && !finished
  const orb = showLight ? { top: view.rect.top - 9, left: view.rect.left + view.rect.width - 9 } : null
  const landing = typeof document !== 'undefined' && Boolean(document.querySelector('.ld-action'))

  const ui = <div className={`guide-root${landing ? ' on-landing' : ''}`}>
    {showLight && state.open && <div className="guide-spot" aria-hidden="true" style={{ top: view.rect.top - 8, left: view.rect.left - 8, width: view.rect.width + 16, height: view.rect.height + 16 }} />}
    {showLight && <div className="guide-halo" aria-hidden="true" style={{ top: view.rect.top - 8, left: view.rect.left - 8, width: view.rect.width + 16, height: view.rect.height + 16 }} />}
    {orb && <span className="guide-orb" aria-hidden="true" style={orb} />}
    {body && <section ref={bubbleRef} className={`guide-bubble ${side}`} role="dialog" aria-label="풍이와 결이의 길 안내">{body}</section>}
    <button type="button" className={`guide-launcher${state.course && !state.open ? ' is-waiting' : ''}`} aria-expanded={state.open}
      aria-label="풍이와 결이의 길 안내" onClick={() => update(current => ({ open: !current.open }))}>
      <Face who="pung" small /><span>{state.course && !finished ? '이어서 안내' : '길 안내'}</span>
    </button>
  </div>
  return typeof document === 'undefined' ? ui : createPortal(ui, document.body)
}
