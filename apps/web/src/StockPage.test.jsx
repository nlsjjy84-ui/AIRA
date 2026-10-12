import { describe, expect, it, beforeEach, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import StockPage, { nodeFor, STOCK_TREE } from './StockPage.jsx'

const ID = '24f7883e-6992-452e-a5e0-099b886a923c'
const points = Array.from({ length: 30 }, (_, i) => {
  const d = new Date(Date.UTC(2026, 8, 1 + i)); const date = d.toISOString().slice(0, 10)
  return { date, open: 100 + i, high: 110 + i, low: 95 + i, close: 105 + i, changeRate: 1, volume: 1000 + i, value: 100000, marketCap: 1e12 + i * 1e10, listedShares: 1e7 }
})
const filings = [{ receiptNo: '20260910000001', date: '2026-09-10', title: '주요사항보고서(자기주식취득결정)', filer: '삼성전자', category: 'MATERIAL', url: 'https://dart.fss.or.kr/x' },
  { receiptNo: '20260905000001', date: '2026-09-05', title: '반기보고서 (2026.06)', filer: '삼성전자', category: 'PERIODIC', url: 'https://dart.fss.or.kr/y' }]
const years = [2024, 2025].map((year, i) => ({ year, receiptNo: `${year + 1}0310000001`, fsDiv: 'CFS', dartUrl: 'https://dart.fss.or.kr/z',
  values: { REVENUE: 300 + i * 30, OPERATING_INCOME: 30 + i * 13, NET_INCOME: 20 + i * 10, TOTAL_ASSETS: 500, TOTAL_LIABILITIES: 200 - i * 20, TOTAL_EQUITY: 300 + i * 40, CURRENT_ASSETS: 150, CURRENT_LIABILITIES: 100 } }))
const routes = {
  [`/api/stocks/${ID}/overview`]: { companyId: ID, name: '삼성전자', corpCode: '00126380', shortCode: '005930', market: 'KOSPI', profile: { ceo_nm: '전영현', est_dt: '19690113' } },
  [`/api/stocks/${ID}/prices`]: { state: 'AVAILABLE', source: 'KRX', points },
  [`/api/stocks/${ID}/financials`]: { state: 'AVAILABLE', source: 'OpenDART', years },
  [`/api/stocks/${ID}/filings`]: { state: 'AVAILABLE', source: 'OpenDART', from: '2025-10-01', to: '2026-10-01', items: filings },
  '/api/stocks/market-index/KOSPI': { state: 'AVAILABLE', points: points.map(p => ({ ...p, close: 50 + p.close / 10 })) },
  [`/api/companies/${ID}/events`]: { events: [{ eventId: 'e1', title: '삼양패키징가 2024 회계연도 연간 재무결과를 공식 공시했습니다.', evidence: [{ externalId: '20250311001085' }] }] },
}

beforeEach(() => {
  window.history.replaceState({}, '', `/stock/${ID}`)
  global.fetch = vi.fn(async path => {
    const key = String(path).split('?')[0]
    const body = routes[key]
    return { ok: Boolean(body), status: body ? 200 : 404, json: async () => body ?? {} }
  })
})

describe('StockPage', () => {
  it('resolves menu paths with sensible defaults', () => {
    expect(nodeFor(['inspect', 'price', 'daily']).third[0]).toBe('daily')
    expect(nodeFor(['nope']).top[0]).toBe('inspect')
    expect(STOCK_TREE.map(node => node[2])).toEqual(['묻다', '보다', '잇다', '가늠하다'])
  })

  it('shows the header facts, the four stages and drills down through the daily table to one day', async () => {
    const user = userEvent.setup()
    render(<StockPage companyId={ID} />)
    expect(await screen.findByRole('heading', { name: '삼성전자' })).toBeInTheDocument()
    expect(await screen.findByText('134원')).toBeInTheDocument()
    const stages = screen.getByRole('navigation', { name: 'AIRA 단계' })
    expect(within(stages).getAllByRole('button').map(b => b.textContent)).toEqual(['Ask묻다', 'Inspect보다', 'Relate잇다', 'Assess가늠하다'])
    await user.click(screen.getByRole('button', { name: '일별 시세' }))
    await user.click(await screen.findByRole('button', { name: '2026-09-10' }))
    expect(window.location.search).toContain('inspect.price.daily.2026-09-10')
    expect(await screen.findByText('이날 접수된 공시 1건')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /자기주식취득결정/ }))
    expect(await screen.findByRole('link', { name: 'DART 원문 열기 ↗' })).toHaveAttribute('href', 'https://dart.fss.or.kr/x')
    const crumbs = screen.getByRole('list', { name: '지금 위치' })
    expect(within(crumbs).getByText(/자기주식취득결정/)).toHaveAttribute('aria-current', 'location')
    expect(screen.getByRole('button', { name: '← 뒤로' })).toBeInTheDocument()
  })

  it('filters filings by category and shows ratios with the study notes', async () => {
    const user = userEvent.setup()
    window.history.replaceState({}, '', `/stock/${ID}?v=inspect.filings.PERIODIC`)
    render(<StockPage companyId={ID} />)
    expect(await screen.findByText('반기보고서 (2026.06)')).toBeInTheDocument()
    expect(screen.queryByText('주요사항보고서(자기주식취득결정)')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '비율' }))
    await user.click(screen.getByRole('button', { name: '안정성' }))
    expect(await screen.findByRole('button', { name: '부채비율' })).toBeInTheDocument()
    expect(screen.getAllByText('📘 읽는 법 · 용어 풀이').length).toBeGreaterThan(0)
  })

  it('assesses year over year without recommending and fixes stored event titles', async () => {
    const user = userEvent.setup()
    window.history.replaceState({}, '', `/stock/${ID}?v=assess.yoy`)
    render(<StockPage companyId={ID} />)
    expect(await screen.findByRole('button', { name: '부채총계' })).toBeInTheDocument()
    expect(screen.getByText(/매수·매도를 권하거나/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Relate 잇다' }))
    await user.click(screen.getByRole('button', { name: '관련 사건' }))
    expect(await screen.findByText('삼양패키징이 2024 회계연도 연간 재무결과를 공식 공시했습니다.')).toBeInTheDocument()
  })

  it('explains when prices are still being filled', async () => {
    routes[`/api/stocks/${ID}/prices`] = { state: 'NO_DATA', points: [] }
    render(<StockPage companyId={ID} />)
    expect(await screen.findByText(/1년치 주가를 거래소에서 받아 채우는 중/)).toBeInTheDocument()
    routes[`/api/stocks/${ID}/prices`] = { state: 'AVAILABLE', source: 'KRX', points }
  })
})
