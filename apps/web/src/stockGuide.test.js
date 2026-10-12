import { describe, expect, it } from 'vitest'
import { GUIDE, guideFor, sectionColumns, buildCalendar, eventLabel, courseDone, COURSE, coursePrefix, SECTION_META } from './stockGuide.js'
import { STOCK_TREE, nodeFor } from './StockPage.jsx'

describe('stockGuide', () => {
  it('gives every menu node a why and a how', () => {
    const missing = []
    for (const [top, , , , seconds] of STOCK_TREE) for (const second of seconds) {
      const leaves = second[3] ? second[3].map(t => [top, second[0], t[0]]) : [[top, second[0]]]
      for (const path of leaves) { const g = guideFor(path); if (!g?.why || !g?.how) missing.push(path.join('/')) }
    }
    expect(missing).toEqual([])
    expect(Object.keys(GUIDE).length).toBeGreaterThan(60)
  })

  it('course steps point at real menu nodes', () => {
    for (const step of COURSE) {
      const { top, second, third } = nodeFor(step.to)
      expect([top[0], second[0], third?.[0]].filter(Boolean)).toEqual(step.to)
    }
  })

  it('every report section shown on screen has labels', () => {
    for (const name of ['audit', 'execs', 'payAll', 'payTop', 'affiliates', 'shares', 'treasury', 'capital', 'minority', 'holderChange', 'bonds'])
      expect(SECTION_META[name].cols.length).toBeGreaterThan(2)
  })

  it('keeps only columns that have values and falls back to raw keys', () => {
    const rows = [{ adtor: '삼정회계법인', adt_opinion: '적정의견', emphs_matter: '-', rcept_no: '1' }]
    expect(sectionColumns('audit', rows).map(c => c[1])).toEqual(['감사인', '감사의견'])
    expect(sectionColumns('audit', [{ unknown_field: 'x', rcept_no: '1' }])).toEqual([['unknown_field', 'unknown_field']])
  })

  it('finds schedule filings and builds upcoming dates from law and last year', () => {
    expect(eventLabel('[기재정정]사업보고서 (2025.12)')).toBe('사업보고서')
    expect(eventLabel('연결재무제표기준영업(잠정)실적(공정공시)')).toBe('잠정 실적 발표')
    expect(eventLabel('임원ㆍ주요주주특정증권등소유상황보고서')).toBeNull()
    const filings = [{ receiptNo: 'a', date: '2025-10-30', title: '연결재무제표기준영업(잠정)실적(공정공시)' },
      { receiptNo: 'b', date: '2026-03-10', title: '사업보고서 (2025.12)' }]
    const { past, upcoming } = buildCalendar(filings, '2026-10-12')
    expect(past.map(p => p.receiptNo)).toEqual(['b', 'a'])
    expect(upcoming[0]).toMatchObject({ date: '2026-10-30', kind: 'LAST_YEAR' })
    expect(upcoming.find(u => u.kind === 'LAW')).toMatchObject({ date: '2026-11-14', label: '3분기보고서 제출 기한' })
    expect(buildCalendar(filings, '2026-10-12', 6).upcoming.some(u => u.kind === 'LAW')).toBe(false)
  })

  it('reads finished course steps from study notes of this company only', () => {
    const notes = [{ kind: 'COURSE', companyId: 'c1', title: `${coursePrefix(0)}어떤 회사인가` }, { kind: 'COURSE', companyId: 'c2', title: `${coursePrefix(3)}x` },
      { kind: 'WHY', companyId: 'c1', title: `${coursePrefix(5)}x` }]
    expect([...courseDone(notes, 'c1')]).toEqual([0])
  })
})

describe('report section rows', () => {
  it('drops rows whose values are all dashes but keeps label-only differences', async () => {
    const { visibleRows } = await import('./stockGuide.js')
    const rows = [{ acqs_mth1: '총계', acqs_mth2: '총계', acqs_mth3: '총계', stock_knd: '보통주', bsis_qy: '1', change_qy_acqs: '2' },
      { acqs_mth1: '기타취득', acqs_mth2: '기타', acqs_mth3: '기타', stock_knd: '우선주', bsis_qy: '-', change_qy_acqs: '-' }]
    expect(visibleRows('treasury', rows)).toHaveLength(1)
    expect(visibleRows('capital', [{ isu_dcrs_de: '-', isu_dcrs_stle: '-' }])).toHaveLength(0)
  })
})

describe('statement order and nearest schedule', () => {
  it('reads the income statement top-down even when DART order is scrambled', async () => {
    const { orderStatement } = await import('./stockGuide.js')
    const lines = [['영업이익', 6], ['매출원가', 10], ['당기순이익', 18], ['매출액', 23], ['판매비와관리비', 9], ['매출총이익', 16], ['기타항목', 30]]
      .map(([account, order]) => ({ account, order: String(order) }))
    expect(orderStatement('IS', lines).map(l => l.account)).toEqual(['매출액', '매출원가', '매출총이익', '판매비와관리비', '영업이익', '당기순이익', '기타항목'])
    expect(orderStatement('BS', lines)[0].account).toBe('영업이익')
  })

  it('takes the nearest last-year date for each kind', () => {
    const filings = [{ receiptNo: 'a', date: '2025-10-14', title: '연결재무제표기준영업(잠정)실적(공정공시)' },
      { receiptNo: 'b', date: '2026-01-29', title: '연결재무제표기준영업(잠정)실적(공정공시)' }]
    const { upcoming } = buildCalendar(filings, '2026-10-12')
    expect(upcoming.filter(u => u.kind === 'LAST_YEAR').map(u => u.date)).toEqual(['2026-10-14'])
  })
})
