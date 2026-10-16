import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import DataStatusPage, { formatCollected } from './DataStatusPage.jsx'

const ok = body => ({ ok: true, status: 200, json: async () => body })

beforeEach(() => { vi.restoreAllMocks() })

describe('data status page', () => {
  it('shows the data date and the collection time separately, and flags a stopped daily source', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ok({ checkedAt: '2026-10-16T03:00:00Z', overall: 'LATE', sources: [
      { key: 'STOCK_PRICE', label: '종목 주가', provider: 'KRX', cadence: '매일', observedThrough: '2026-10-14',
        collectedAt: '2026-10-14T09:30:00Z', state: 'LATE', note: '마지막 수집이 41시간 전이라 자동 수집이 멈췄을 수 있어요.' },
      { key: 'REAL_GDP', label: '실질 GDP', provider: '한국은행 ECOS', cadence: '분기마다', observedThrough: '2026-06-30',
        collectedAt: '2026-08-01T00:00:00Z', state: 'OK', note: '발표 주기가 길어 날짜만 보여 드려요.' }
    ] })))
    render(<DataStatusPage />)
    expect(await screen.findByText('종목 주가')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('전체 상태: 늦음')
    expect(screen.getByText('2026-10-14까지')).toBeInTheDocument()
    expect(screen.getByText('2026-10-14 18:30')).toBeInTheDocument()
    expect(screen.getByText(/자동 수집이 멈췄을 수 있어요/)).toBeInTheDocument()
    expect(screen.getByText('2026-06-30까지')).toBeInTheDocument()
  })

  it('offers a retry when the status cannot be read', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 503, json: async () => ({}) })))
    render(<DataStatusPage />)
    expect(await screen.findByText('자료 상태를 불러오지 못했습니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })

  it('formats times in Korea time and tolerates missing values', () => {
    expect(formatCollected('2026-10-15T15:05:00Z')).toBe('2026-10-16 00:05')
    expect(formatCollected(null)).toBe('—')
  })
})
