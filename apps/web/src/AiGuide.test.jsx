import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, beforeEach } from 'vitest'
import AiGuide, { SUBMENU_TEXT, advance, guideText, readGuide, startCourse, writeGuide } from './AiGuide.jsx'
import { courseIds } from './GuideOrb.jsx'

const MENUS = [['market', '시장', [['시장 지도', '/market'], ['지수', '/market?tab=index']]]]

function el(html, selector) {
  document.body.innerHTML = html
  return document.querySelector(selector)
}

describe('AI 길 안내 — 버튼 설명(넓이·깊이)', () => {
  it('큰 메뉴는 아래 작은 메뉴 개수와 이름을 알려 준다', () => {
    const a = el('<nav class="header-menus"><a href="/market">시장</a></nav>', 'a')
    expect(guideText(a, MENUS)).toEqual({ title: '시장', text: '작은 메뉴 <b>2개</b> — 시장 지도 · 지수' })
  })

  it('작은 메뉴는 화면 설명을 쓴다', () => {
    const a = el('<nav class="header-submenus"><a href="/market">시장 지도</a></nav>', 'a')
    expect(guideText(a, MENUS).text).toBe(SUBMENU_TEXT['시장 지도'])
  })

  it('종목 화면 큰 탭 → 작은 메뉴 → 세부 칸까지 설명한다', () => {
    const tab = el('<nav class="stock-tabs"><button>실적·재무</button></nav>', 'button')
    expect(guideText(tab).text).toContain('작은 메뉴 <b>5개</b>')
    const sub = el('<nav class="stock-subtabs"><button>분기 실적</button></nav>', 'button')
    expect(guideText(sub).text).toContain('석 달 단위')
    window.history.replaceState({}, '', '/stock/x?v=inspect.balance')
    const chip = el('<nav class="stock-chips"><button>현금흐름</button></nav>', 'button')
    expect(guideText(chip).text).toContain('현금')
  })

  it('설명할 수 없는 곳은 지나간다', () => {
    expect(guideText(el('<p>그냥 글</p>', 'p'))).toBeNull()
  })
})

describe('AI 길 안내 — 코스 진행', () => {
  beforeEach(() => window.sessionStorage.clear())

  it('코스를 고르면 입구부터, 걸음을 마치면 다음으로 간다', () => {
    const state = startCourse({ done: [] }, 'short')
    expect(state.index).toBe(0)
    const next = advance(state)
    expect(next.index).toBe(1)
    expect(next.done).toContain(courseIds('short')[0])
  })

  it('평가자용 걸음을 마치면 같은 화면의 일반 걸음은 다시 보여 주지 않는다', () => {
    let state = startCourse({ done: [] }, 'judge')
    state = advance(state) // entry
    state = advance(state) // jClaim → claim도 본 것으로
    expect(state.done).toEqual(expect.arrayContaining(['jClaim', 'claim']))
    const long = startCourse({ ...state, course: null }, 'long')
    expect(courseIds('long')[long.index]).not.toBe('claim')
  })

  it('페이지가 새로 열려도 이어 가도록 sessionStorage에 남긴다', () => {
    writeGuide({ course: 'long', index: 3, done: ['entry'], log: {} })
    expect(readGuide()).toMatchObject({ course: 'long', index: 3 })
  })

  it('메뉴는 「데모 둘러보기」 하나와 「설명 없이 둘러볼게요」 — 3분·10분·평가자용으로 나누지 않는다', () => {
    render(<AiGuide greet />)
    fireEvent.click(screen.getByRole('button', { name: /길 안내/ }))
    expect(screen.getByRole('button', { name: /데모 둘러보기/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /설명 없이 둘러볼게요/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /3분|10분|평가자/ })).toBeNull()
  })

  it('예전 「풍이와 결이」 상자 대신 글자 한 줄 「길 안내」만 띄운다', () => {
    render(<AiGuide greet />)
    expect(screen.getByRole('button', { name: /길 안내/ })).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: '풍이와 결이의 길 안내' })).toBeNull()
  })

  it('실제 가입 사용자에게는 설명 글을 띄우지 않는다(효과만 남는다)', () => {
    render(<AiGuide greet={false} />)
    expect(screen.queryByRole('button', { name: /길 안내/ })).toBeNull()
  })

  it('처음 메뉴에 「설명 없이 둘러볼게요」가 있고, 고르면 설명만 꺼진다', () => {
    render(<AiGuide greet />)
    fireEvent.click(screen.getByRole('button', { name: /길 안내/ }))
    fireEvent.click(screen.getByRole('button', { name: /설명 없이 둘러볼게요/ }))
    expect(window.sessionStorage.getItem('aira-guide-quiet')).toBe('1')
    expect(screen.getByRole('button', { name: /길 안내 켜기/ })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /길 안내 켜기/ }))
    expect(window.sessionStorage.getItem('aira-guide-quiet')).toBeNull()
  })
})
