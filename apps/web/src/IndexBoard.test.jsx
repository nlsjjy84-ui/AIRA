import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

vi.mock('./api/stockApi.js', () => ({
  getIndices: vi.fn(series => Promise.resolve({ state: 'AVAILABLE', series, date: '2026-10-16', source: 'KRX', items: series === 'krx'
    ? [{ group: 'KRX', name: 'KRX 300', close: 2000.5, changeRate: -0.5 }, { group: 'KRX', name: 'KRX 반도체', close: 5123.45, changeRate: 2.1 }, { group: 'KRX', name: 'KRX 은행', close: 900, changeRate: 0 }]
    : [{ group: 'KOSDAQ', name: '코스닥 150', close: 1300, changeRate: 1 }] })),
}))

import IndexBoard from './IndexBoard.jsx'

describe('IndexBoard', () => {
  it('counts risers and fallers, sorts by change, and switches series', async () => {
    render(<IndexBoard />)
    expect(await screen.findByText(/지수 3개 가운데/)).toBeTruthy()
    expect(screen.getByText('1개 오르고')).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: '많이 오른' }))
    const first = screen.getAllByRole('row')[1]
    expect(within(first).getByText('KRX 반도체')).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: '코스닥' }))
    expect(await screen.findByText('코스닥 150')).toBeTruthy()
  })
})
