import { expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import RecentEventBoard from './RecentEventBoard.jsx'

it('renders confirmed events as a compact timeline and opens the selected assessment', () => {
  const onOpen = vi.fn()
  render(<RecentEventBoard state={{ loading: false, error: null, data: { events: [
    { eventId: 'EV-1', eventType: 'EARNINGS', title: '분기 실적 공시', occurredAt: '2026-09-18T08:00:00Z', companies: [{ companyId: 'C-1', companyName: '테스트회사' }] },
    { eventId: 'EV-2', eventType: 'DISCLOSURE', title: '주요사항 공시', occurredAt: '2026-09-17T07:00:00Z', companies: [{ companyId: 'C-2', companyName: '다른회사' }] },
  ] } }} retry={vi.fn()} onOpen={onOpen} />)

  expect(screen.getByRole('heading', { name: '최근 확인된 공식 사건' })).toBeInTheDocument()
  expect(screen.getByText('2026-09-18')).toBeInTheDocument()
  expect(screen.getByText('실적')).toBeInTheDocument()
  expect(screen.getByText('테스트회사')).toBeInTheDocument()
  fireEvent.click(screen.getAllByRole('button', { name: '사건·판단 보기' })[0])
  expect(onOpen).toHaveBeenCalledWith('EV-1', [{ companyId: 'C-1', companyName: '테스트회사' }])
})

it('caps the MAIN timeline at five recent events', () => {
  const events = Array.from({ length: 8 }, (_, i) => ({ eventId: `EV-${i}`, eventType: 'DISCLOSURE', title: `공식 사건 ${i}`, occurredAt: `2026-09-${String(18 - i).padStart(2, '0')}T00:00:00Z`, companies: [] }))
  render(<RecentEventBoard state={{ loading: false, error: null, data: { events } }} retry={vi.fn()} onOpen={vi.fn()} />)
  expect(screen.getAllByRole('button', { name: '사건·판단 보기' })).toHaveLength(5)
})
