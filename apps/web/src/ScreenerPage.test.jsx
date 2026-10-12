import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ScreenerPage, { applyFilters, screenRow } from './ScreenerPage.jsx'

const rows = [
  { companyId: 'a', name: '가회사', market: 'KOSPI', indutyCode: '264', revenue: 100e8, revenuePrev: 80e8, operatingIncome: 20e8, netIncome: 10e8, equity: 100e8, liabilities: 50e8, marketCap: 50e8, changeRate: 1 },
  { companyId: 'b', name: '나회사', market: 'KOSDAQ', indutyCode: '582', revenue: 100e8, revenuePrev: 100e8, operatingIncome: -5e8, netIncome: -5e8, equity: 100e8, liabilities: 300e8, marketCap: 500e8, changeRate: -1 },
].map(screenRow)

describe('screener', () => {
  it('computes metrics and filters by thresholds, market and industry', () => {
    expect(rows[0]).toMatchObject({ operatingMargin: 20, roe: 10, debtRatio: 50, pbr: 0.5, per: 5, revenueGrowth: 25 })
    expect(rows[1].per).toBeNull()
    expect(applyFilters(rows, { opMin: '0.1', debtMax: '100' }).map(r => r.name)).toEqual(['가회사'])
    expect(applyFilters(rows, { capMin: '100' }).map(r => r.name)).toEqual(['나회사'])
    expect(applyFilters(rows, { perMax: '10' }).map(r => r.name)).toEqual(['가회사'])
    expect(applyFilters(rows, {}, 'KOSDAQ').map(r => r.name)).toEqual(['나회사'])
    expect(applyFilters(rows, {}, 'ALL', '26').map(r => r.name)).toEqual(['가회사'])
  })

  it('applies a preset and links each company to its stock page', async () => {
    global.fetch = vi.fn(async () => ({ ok: true, status: 200, json: async () => ({ state: 'AVAILABLE', priceDate: '2026-10-08', source: 'OpenDART', rows: rows.map(r => ({ ...r })) }) }))
    const user = userEvent.setup()
    render(<ScreenerPage />)
    expect(await screen.findByText('2개')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '흑자이면서 빚이 적은 회사' }))
    expect(screen.getByText('1개')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '가회사' })).toHaveAttribute('href', '/stock/a')
  })
})
