import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import GuideOrb, { bubbleSide, recap, COURSES, STEPS, courseIds, fillInput, isAt, iyeyo, placeName, SAMSUNG } from './GuideOrb.jsx'
import { answerQuestion, findTerm, shorten } from './guideAsk.js'

function page(html, path = '/') {
  window.history.pushState({}, '', path)
  document.body.innerHTML = `<div id="app">${html}</div>`
}

beforeEach(() => { window.sessionStorage.clear(); document.body.innerHTML = '' })
afterEach(() => { vi.useRealTimers(); window.history.pushState({}, '', '/') })

describe('풍이와 결이의 길 안내', () => {
  it('짧은 코스는 핵심 네 곳, 긴 코스는 4구역 열세 곳이고 모두 입구에서 시작한다', () => {
    expect(COURSES.short.zones[0].ids).toEqual(['claim', 'easy', 'quote', 'course'])
    expect(COURSES.long.zones).toHaveLength(4)
    expect(courseIds('long')).toHaveLength(14)
    expect(courseIds('short')[0]).toBe('entry')
    for (const id of courseIds('long')) {
      expect(STEPS[id].say.length).toBeLessThanOrEqual(140)
      if (STEPS[id].after) expect(STEPS[id].after.length).toBeLessThanOrEqual(140)
    }
  })

  it('처음 온 사람에게 한 번 인사하고 3분 간략 / 10분 자세히를 고르게 한다', () => {
    vi.useFakeTimers()
    page('<main class="is-landing"><button class="ld-action">가입 없이 둘러보기</button></main>')
    render(<GuideOrb greet tickMs={10_000} />)
    act(() => { vi.advanceTimersByTime(1300) })
    expect(screen.getByRole('dialog', { name: '풍이와 결이의 길 안내' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /3분 간략/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /10분 자세히/ })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /3분 간략/ }))
    expect(screen.getByText(/가입 없이 둘러보기」를 눌러 주세요/)).toBeInTheDocument()
  })

  it('다른 화면에 있으면 지금 위치와 다음 목적지를 말하고 데려다준다', () => {
    window.sessionStorage.setItem('aira-guide-v1', JSON.stringify({ course: 'short', index: 2, open: true, done: ['entry', 'claim'] }))
    page('<span class="session-user">데모</span>', '/market')
    const navigate = vi.fn()
    render(<GuideOrb navigate={navigate} tickMs={10_000} />)
    expect(screen.getByText('지금은 시장에 계세요. 다음 목적지는 「삼성전자 › 종목정보 › 한눈에」예요.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '데려다 드릴게요' }))
    expect(navigate).toHaveBeenCalledWith(`${SAMSUNG}?v=inspect.summary`)
  })

  it('빛나는 곳을 직접 눌러야 「다음」이 켜지고, 결과를 읽고 반응한다', () => {
    window.sessionStorage.setItem('aira-guide-v1', JSON.stringify({ course: 'short', index: 1, open: true, done: ['entry'] }))
    page(`<span class="session-user">데모</span><section class="claim-check-hero"><div class="claim-check-examples">
      <button>맞는 말</button><button id="wrong">틀린 말</button></div><p class="claim-check-verdict"><strong></strong></p></section>`, '/')
    const { rerender } = render(<GuideOrb tickMs={10_000} />)
    expect(screen.getByText(/「틀린 말」 예시를 눌러 보세요/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '다음' })).toBeNull()
    fireEvent.click(document.getElementById('wrong'))
    expect(screen.getByRole('button', { name: '다음' })).toBeInTheDocument()
    document.querySelector('.claim-check-verdict strong').textContent = '다름'
    rerender(<GuideOrb tickMs={5} />)
    return vi.waitFor(() => expect(screen.getByText(/틀린 말이었네요!/)).toBeInTheDocument())
  })

  it('「대신 적어 드릴게요」는 입력칸에 질문을 넣는다', () => {
    page('<form><input id="filing-quote-question" /></form>')
    expect(fillInput('#filing-quote-question', '영업이익은 얼마인가요?')).toBe(true)
    expect(document.getElementById('filing-quote-question').value).toBe('영업이익은 얼마인가요?')
  })

  it('종목 화면은 ?v= 경로 앞부분이 맞으면 같은 자리로 본다', () => {
    page('<span class="session-user">데모</span>', SAMSUNG)
    expect(isAt(STEPS.easy, { path: SAMSUNG, v: null })).toBe(true)
    expect(isAt(STEPS.quote, { path: SAMSUNG, v: 'ask.quote' })).toBe(true)
    expect(isAt(STEPS.quote, { path: SAMSUNG, v: 'ask.ai' })).toBe(false)
    expect(placeName('/stock/00000000-0000-0000-0000-000000000000')).toBe('다른 종목 화면')
  })

  it('3분 코스를 다 돌면 10분 자세히가 열리고, 이미 들른 곳은 건너뛴다', () => {
    window.sessionStorage.setItem('aira-guide-v1', JSON.stringify({ course: 'short', index: 5, open: true, done: ['entry', 'claim', 'easy', 'quote', 'course'] }))
    page('<span class="session-user">데모</span>', '/')
    render(<GuideOrb tickMs={10_000} />)
    expect(screen.getByText(/10분 자세히 코스가 열렸어요/)).toBeInTheDocument()
    expect(screen.getByLabelText('오늘의 AIRA 길 결산')).toHaveTextContent('4곳')
    fireEvent.click(screen.getByRole('button', { name: '10분 자세히 이어 가기' }))
    expect(JSON.parse(window.sessionStorage.getItem('aira-guide-v1')).index).toBe(courseIds('long').indexOf('askAi'))
  })

  it('풍이에게 물으면 답하고 그 화면으로 데려다준다', async () => {
    page('<span class="session-user">데모</span>', '/')
    const navigate = vi.fn()
    const ask = vi.fn(async () => ({ method: 'AI', say: 'SK하이닉스의 「한눈에」로 모실게요.', place: 'SK하이닉스 › 한눈에', href: '/stock/x?v=inspect.summary' }))
    window.sessionStorage.setItem('aira-guide-v1', JSON.stringify({ course: null, index: 0, open: true, done: [] }))
    render(<GuideOrb navigate={navigate} ask={ask} tickMs={10_000} />)
    fireEvent.change(screen.getByLabelText('풍이에게 묻기'), { target: { value: '하이닉스 어때' } })
    fireEvent.click(screen.getByRole('button', { name: '묻기' }))
    expect(await screen.findByText('SK하이닉스의 「한눈에」로 모실게요.')).toBeInTheDocument()
    expect(screen.getByText('AI가 질문을 이해했어요')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '「SK하이닉스 › 한눈에」로 데려다 드릴게요' }))
    expect(navigate).toHaveBeenCalledWith('/stock/x?v=inspect.summary')
  })

  it('예시 질문을 누르면 바로 묻는다', async () => {
    page('<span class="session-user">데모</span>', '/')
    const ask = vi.fn(async () => ({ method: 'RULE', say: 'PER은 …', place: '삼성전자 › 용어 사전', href: '/x' }))
    window.sessionStorage.setItem('aira-guide-v1', JSON.stringify({ course: null, index: 0, open: true, done: [] }))
    render(<GuideOrb ask={ask} tickMs={10_000} />)
    fireEvent.click(screen.getByRole('button', { name: 'PER이 뭐야?' }))
    expect(await screen.findByText('PER은 …')).toBeInTheDocument()
    expect(ask).toHaveBeenCalledWith('PER이 뭐야?')
  })
})

describe('평가자용 빠른 코스', () => {
  it('면접관·선생님용으로 AI를 믿을 수 있게 만든 다섯 곳을 보여 주고, 끝에 GitHub를 연다', () => {
    expect(COURSES.judge.zones[0].ids).toEqual(['jClaim', 'jQuote', 'jAskAi', 'jScreen', 'jStatus'])
    for (const id of courseIds('judge')) {
      expect(STEPS[id].say.length).toBeLessThanOrEqual(140)
      if (STEPS[id].after) expect(STEPS[id].after.length).toBeLessThanOrEqual(140)
    }
    window.sessionStorage.setItem('aira-guide-v1', JSON.stringify({ course: null, index: 0, open: true, done: [] }))
    page('<span class="session-user">데모</span>', '/')
    const { unmount } = render(<GuideOrb tickMs={10_000} />)
    fireEvent.click(screen.getByRole('button', { name: /평가자용 빠른 코스/ }))
    expect(JSON.parse(window.sessionStorage.getItem('aira-guide-v1')).course).toBe('judge')
    unmount()
    window.sessionStorage.setItem('aira-guide-v1', JSON.stringify({ course: 'judge', index: 6, open: true, done: courseIds('judge') }))
    render(<GuideOrb tickMs={10_000} />)
    expect(screen.getByText(/평가자용 코스 끝!/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'GitHub 열기' })).toHaveAttribute('href', 'https://github.com/nlsjjy84-ui/AIRA')
  })

  it('평가자용을 마치고 10분 코스로 이어 가면 이미 본 네 곳은 건너뛴다', () => {
    window.sessionStorage.setItem('aira-guide-v1', JSON.stringify({ course: 'judge', index: 5, open: true, done: ['entry', 'jClaim', 'jQuote', 'jAskAi', 'jScreen', 'claim', 'quote', 'askAi', 'screen'] }))
    page('<span class="session-user">데모</span><h1 id="data-status-title">AIRA 자료는 지금 어디까지</h1>', '/status')
    render(<GuideOrb tickMs={10_000} />)
    fireEvent.click(screen.getByRole('button', { name: '다음' }))
    fireEvent.click(screen.getByRole('button', { name: '나머지 기능 이어 보기(10분 코스)' }))
    const saved = JSON.parse(window.sessionStorage.getItem('aira-guide-v1'))
    expect(saved.course).toBe('long')
    expect(courseIds('long')[saved.index]).toBe('market')
    const left = courseIds('long').filter(id => !saved.done.includes(id))
    expect(left).toHaveLength(9)
  })
})

describe('완주 결산', () => {
  it('들른 곳·판정·퀴즈·질문 수를 큰 글씨 한 줄씩 정리한다', () => {
    const lines = recap(['entry', 'claim', 'quiz'], { verdict: '다름', quiz: 'right', asks: 2 })
    expect(lines.map(l => l.big)).toEqual(['2곳', '‘다름’', '정답!', '2번'])
  })
  it('평가자용 코스는 같은 화면으로 함께 체크된 일반 걸음을 두 번 세지 않는다', () => {
    const done = ['entry', 'jClaim', 'claim', 'jQuote', 'quote', 'jAskAi', 'askAi', 'jScreen', 'screen', 'jStatus']
    expect(recap(done, {}, 'judge')[0].big).toBe('5곳')
  })
})

describe('말풍선 자리', () => {
  it('목적지를 가리면 비켜선다', () => {
    const box = { width: 368, height: 300 }
    expect(bubbleSide({ left: 100, right: 300, top: 100, bottom: 160 }, box, 1366, 820)).toBe('')
    expect(bubbleSide({ left: 1090, right: 1260, top: 600, bottom: 660 }, box, 1366, 820)).toBe('is-left')
    expect(bubbleSide({ left: 40, right: 300, top: 600, bottom: 660 }, box, 390, 844)).toBe('is-top')
    // 화면 폭 전체를 차지하는 목적지가 아래쪽에 있으면 위로 비켜선다(조건 검색 입력칸).
    expect(bubbleSide({ left: 90, right: 1280, top: 500, bottom: 620 }, box, 1366, 820)).toBe('is-top')
  })
})

describe('풍이에게 묻기 — 답 고르기', () => {
  const noRoute = async () => { throw new Error('offline') }

  it('사라·팔라·오를까에는 판단하지 않는다고 말한다', async () => {
    const answer = await answerQuestion('삼성전자 지금 사도 돼?', { route: noRoute, path: '/' })
    expect(answer.say).toMatch(/사라·팔라, 오를지·내릴지는 말하지 않아요/)
  })

  it('용어를 물으면 용어 사전 글로 짧게 답한다', async () => {
    expect(findTerm('PER이 뭐야?').term).toMatch(/^PER/)
    const answer = await answerQuestion('PER이 뭐야?', { route: noRoute, path: '/' })
    expect(answer.say.length).toBeLessThanOrEqual(124)
    expect(answer.href).toMatch(/v=ask\.glossary$/)
  })

  it('화면 이름이 들어 있으면 그 화면으로, 다른 회사 이름은 AI 길찾기로 찾는다', async () => {
    expect((await answerQuestion('요금은 얼마야?', { route: noRoute, path: '/' })).href).toBe('/plans')
    const answer = await answerQuestion('하이닉스 영업이익 알려줘', {
      path: '/',
      route: async () => ({ understood: true, entityQuery: 'SK하이닉스', perspective: 'INSPECT', items: ['OPERATING_INCOME'] }),
      search: async () => ({ entities: [{ entityType: 'COMPANY', entityId: 'abc', canonicalName: 'SK하이닉스' }] }),
    })
    expect(answer.method).toBe('AI')
    expect(answer.href).toBe('/stock/abc?v=inspect.earnings.OPERATING_INCOME')
    expect(answer.say).toMatch(/「실적\(손익\)」으로 모실게요/)
  })

  it('AI를 못 쓰면 지금 보는 회사에서 낱말로 찾고, 그래도 모르면 질문으로 찾기로 보낸다', async () => {
    const id = '11111111-1111-1111-1111-111111111111'
    expect((await answerQuestion('배당 얼마나 줘?', { route: noRoute, path: `/stock/${id}` })).href).toMatch(new RegExp(`^/stock/${id}`))
    expect((await answerQuestion('ㅎㅎㅎ', { route: noRoute, path: '/' })).href).toMatch(/v=ask\.find$/)
    expect(shorten('가'.repeat(200)).length).toBeLessThanOrEqual(121)
  })
})

describe('받침에 맞춘 안내 말', () => {
  it('받침이 있으면 이에요, 없으면 예요를 붙인다', () => {
    expect(iyeyo('「조건 검색」')).toBe('「조건 검색」이에요')
    expect(iyeyo('「분기 실적」')).toBe('「분기 실적」이에요')
    expect(iyeyo('「시장 지도」')).toBe('「시장 지도」예요')
    expect(iyeyo('「삼성전자 › 종목정보 › 한눈에」')).toBe('「삼성전자 › 종목정보 › 한눈에」예요')
  })
})
