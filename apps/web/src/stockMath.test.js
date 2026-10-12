import { describe, expect, it } from 'vitest'
import { aroundFilings, checklist, dartNumber, fixSubjectParticle, formatWon, lastDays, movingAverage, normalizeTogether, priceStats,
  routeQuestion, subjectParticle, valuation, withRatios, yearOverYear } from './stockMath.js'

const years = withRatios([
  { year: 2024, values: { REVENUE: '300', OPERATING_INCOME: '-10', NET_INCOME: '20', TOTAL_ASSETS: '500', TOTAL_LIABILITIES: '200', TOTAL_EQUITY: '300', CURRENT_ASSETS: '150', CURRENT_LIABILITIES: '100' } },
  { year: 2025, values: { REVENUE: '330', OPERATING_INCOME: '33', NET_INCOME: '30', TOTAL_ASSETS: '520', TOTAL_LIABILITIES: '180', TOTAL_EQUITY: '340', CURRENT_ASSETS: '150', CURRENT_LIABILITIES: '120' } },
])
const points = ['2026-01-02', '2026-01-05', '2026-01-06', '2026-01-07', '2026-01-08'].map((date, i) => ({ date, close: [100, 110, 99, 120, 108][i], changeRate: [0, 10, -10, 21.2, -10][i], volume: 10 }))

describe('stock math', () => {
  it('computes ratios, growth and profit turns from official values', () => {
    expect(years[1].ratios.operatingMargin).toBe(10)
    expect(years[1].ratios.debtRatio).toBe(52.9)
    expect(years[1].ratios.currentRatio).toBe(125)
    expect(years[1].ratios.revenueGrowth).toBe(10)
    expect(years[1].ratios.operatingTurn).toBe('흑자 전환')
    expect(years[1].ratios.operatingGrowth).toBeNull()
    expect(years[0].ratios.revenueGrowth).toBeNull()
  })

  it('formats won in 조·억 units', () => {
    expect(formatWon(333605938000000)).toBe('333.61조 원')
    expect(formatWon(-1234000000)).toBe('−12억 원')
    expect(formatWon(null)).toBe('—')
  })

  it('summarizes a price range', () => {
    const stats = priceStats(points)
    expect(stats.change).toBe(8)
    expect(stats.high.date).toBe('2026-01-07')
    expect(stats.low.date).toBe('2026-01-06')
    expect(stats.maxDrawdown).toBe(-10)
    expect([stats.up, stats.down]).toEqual([2, 2])
    expect(lastDays(points, 2).map(p => p.date)).toEqual(['2026-01-06', '2026-01-07', '2026-01-08'])
  })

  it('compares with the market on shared dates only, starting at 100', () => {
    const index = [{ date: '2026-01-02', close: 50 }, { date: '2026-01-06', close: 55 }]
    expect(normalizeTogether(points, index)).toEqual([{ date: '2026-01-02', a: 100, b: 100 }, { date: '2026-01-06', a: 99, b: 110 }])
  })

  it('looks at prices around a filing and shortens the window at the end', () => {
    const [row] = aroundFilings(points, [{ date: '2026-01-05', receiptNo: '1' }], 5)
    expect(row.beforeDate).toBe('2026-01-02')
    expect(row.afterDate).toBe('2026-01-08')
    expect(row.days).toBe(4)
    expect(row.change).toBe(8)
    expect(aroundFilings(points, [{ date: '2025-12-01' }])).toEqual([])
  })

  it('reads year-over-year direction with liabilities reversed', () => {
    const items = yearOverYear(years)
    expect(items.find(i => i.key === 'TOTAL_LIABILITIES').reading).toBe('better')
    expect(items.find(i => i.key === 'REVENUE').reading).toBe('better')
    expect(items.find(i => i.key === 'currentRatio').reading).toBe('worse')
  })

  it('answers checklist questions with facts and marks missing data', () => {
    const list = checklist(years, { change: 5 }, { change: 10 }, [])
    expect(list.find(i => i.label.includes('영업이익')).value).toBe(true)
    expect(list.find(i => i.label.includes('시장 지수')).value).toBe(false)
    expect(list.find(i => i.label.includes('주요사항')).value).toBeNull()
  })

  it('computes valuation only when the denominator is positive', () => {
    const v = valuation({ date: '2026-10-08', marketCap: 3400, listedShares: 10 }, years[1])
    expect(v.per).toBe(113.33)
    expect(v.pbr).toBe(10)
    expect(v.eps).toBe(3)
    expect(valuation({ marketCap: 1 }, { year: 1, values: { NET_INCOME: -5 } }).per).toBeNull()
  })

  it('fixes the subject particle of stored titles', () => {
    expect(fixSubjectParticle('삼양패키징가 2024 회계연도 연간 재무결과를 공식 공시했습니다.')).toBe('삼양패키징이 2024 회계연도 연간 재무결과를 공식 공시했습니다.')
    expect(fixSubjectParticle('NAVER가 2025 회계연도 연간')).toBe('NAVER가 2025 회계연도 연간')
    expect(subjectParticle('LG')).toBe('가')
    expect(subjectParticle('SK텔레콤')).toBe('이')
    expect(subjectParticle('KCC')).toBe('가')
    expect(subjectParticle('HL')).toBe('이')
  })

  it('routes questions by words and averages prices', () => {
    expect(routeQuestion('빚이 많아?')[0].to).toEqual(['inspect', 'ratios', '안정성'])
    expect(routeQuestion('')).toEqual([])
    expect(movingAverage(points, 2)).toEqual([null, 105, 105, 110, 114])
    expect(dartNumber('1,446')).toBe(1446)
    expect(dartNumber('-')).toBeNull()
  })
})

describe('candles', () => {
  it('groups days into weeks and months', async () => {
    const { aggregateCandles } = await import('./stockMath.js')
    const days = [['2026-09-28', 10, 12, 9, 11], ['2026-09-29', 11, 15, 10, 14], ['2026-10-01', 14, 14, 8, 9], ['2026-10-05', 9, 10, 7, 8]]
      .map(([date, open, high, low, close]) => ({ date, open, high, low, close, volume: 1 }))
    const weeks = aggregateCandles(days, 'week')
    expect(weeks.map(w => [w.from, w.open, w.high, w.low, w.close, w.volume])).toEqual([['2026-09-28', 10, 15, 8, 9, 3], ['2026-10-05', 9, 10, 7, 8, 1]])
    expect(aggregateCandles(days, 'month').map(m => m.close)).toEqual([14, 8])
  })
})

describe('industry', () => {
  it('names industries and ranks this company among peers', async () => {
    const { industryName, median, peerPosition, peerMetrics } = await import('./stockMath.js')
    expect(industryName('264')).toBe('전자부품·컴퓨터·통신장비 제조업')
    expect(industryName(null)).toBe('업종 정보 없음')
    expect(median([3, 1, 2])).toBe(2)
    expect(median([1, 2, 3, 4])).toBe(2.5)
    const items = [
      { self: true, financials: { REVENUE: 100, OPERATING_INCOME: 10, NET_INCOME: 8, TOTAL_EQUITY: 80, TOTAL_LIABILITIES: 40 }, price: { marketCap: 800 } },
      { self: false, financials: { REVENUE: 50, OPERATING_INCOME: 10, NET_INCOME: 5, TOTAL_EQUITY: 50, TOTAL_LIABILITIES: 100 }, price: { marketCap: 100 } },
      { self: false, financials: {}, price: {} },
    ]
    expect(peerMetrics(items[0])).toMatchObject({ operatingMargin: 10, roe: 10, debtRatio: 50, per: 100, pbr: 10 })
    expect(peerPosition(items, 'operatingMargin')).toMatchObject({ count: 2, rank: 2, value: 10, median: 15 })
    expect(peerPosition(items, 'marketCap')).toMatchObject({ rank: 1, max: 800, min: 100 })
  })
})

describe('radar', () => {
  it('computes percentiles inside the industry, inverted for debt', async () => {
    const { percentileOf } = await import('./stockMath.js')
    expect(percentileOf([10, 20, 30, 40, 50], 50)).toBe(100)
    expect(percentileOf([10, 20, 30, 40, 50], 30)).toBe(50)
    expect(percentileOf([10, 20, 30, 40, 50], 10, true)).toBe(100)
    expect(percentileOf([10, null], 10)).toBeNull()
  })
})

describe('csv', () => {
  it('quotes cells that need it and adds a BOM for Excel', async () => {
    const { toCsv } = await import('./stockMath.js')
    expect(toCsv(['a', 'b'], [[1, 'x,y'], [null, 'say "hi"']])).toBe('﻿a,b\r\n1,"x,y"\r\n,"say ""hi"""')
  })
})

describe('quiz', () => {
  it('builds questions with answers from the real values and explains why', async () => {
    const { buildQuiz } = await import('./stockMath.js')
    const quiz = buildQuiz('가회사', years, { maxDrawdown: -25 })
    const debt = quiz.find(q => q.term === '부채비율')
    expect(debt.question).toContain('50%')
    expect(debt.choices[debt.answer]).toBe('높다')
    expect(debt.why).toContain('52.9%')
    expect(quiz.find(q => q.term === '영업이익률').choices[quiz.find(q => q.term === '영업이익률').answer]).toBe('올랐다')
    expect(quiz.find(q => q.term === 'PER').answer).toBe(0)
    expect(quiz.find(q => q.term === '최고점 대비 최대 하락').answer).toBe(0)
    expect(buildQuiz('x', [], null)).toEqual([])
  })
})

describe('normalizeMany', () => {
  it('starts every company at 100 on the first shared day', async () => {
    const { normalizeMany } = await import('./stockMath.js')
    const a = [{ date: 'd1', close: 10 }, { date: 'd2', close: 20 }, { date: 'd3', close: 15 }]
    const b = [{ date: 'd2', close: 50 }, { date: 'd3', close: 100 }]
    expect(normalizeMany(a, [b])).toEqual([{ date: 'd2', values: [100, 100] }, { date: 'd3', values: [75, 200] }])
    expect(normalizeMany(a, [[{ date: 'd9', close: 1 }]])).toEqual([])
  })
})
