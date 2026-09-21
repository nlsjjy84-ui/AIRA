import { expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import MarketIndexBoard from './MarketIndexBoard.jsx'

it('shows official KOSPI and KOSDAQ values without pretending to be real-time', () => {
  const onEvidence = vi.fn()
  render(<MarketIndexBoard state={{ loading: false, error: null, data: { indices: [
    { marketCode: 'KOSPI', state: 'AVAILABLE', tradingDate: '2026-09-18', close: 3123.45,
      change: -12.3, changeRate: -0.39, evidenceId: 'E-KOSPI', sourceName: 'KRX Data Marketplace Open API' },
    { marketCode: 'KOSDAQ', state: 'AVAILABLE', tradingDate: '2026-09-18', close: 987.65,
      change: 4.2, changeRate: 0.43, evidenceId: 'E-KOSDAQ', sourceName: 'KRX Data Marketplace Open API' },
  ] } }} retry={vi.fn()} onEvidence={onEvidence} />)

  expect(screen.getByRole('heading', { name: '최근 공식 거래일 시장' })).toBeInTheDocument()
  expect(screen.getByText(/실시간 장중 시세가 아닙니다/)).toBeInTheDocument()
  expect(screen.getByText('3,123.45')).toBeInTheDocument()
  expect(screen.getByText('987.65')).toBeInTheDocument()
  expect(screen.getByLabelText(/직전 거래일 대비 -12.30, -0.39퍼센트/)).toBeInTheDocument()
  fireEvent.click(screen.getAllByRole('button', { name: '공식 근거 확인' })[0])
  expect(onEvidence).toHaveBeenCalledWith('E-KOSPI')
})

it('keeps the two market slots visible when official values are absent', () => {
  render(<MarketIndexBoard state={{ loading: false, error: null, data: { indices: [
    { marketCode: 'KOSPI', state: 'NO_DATA' }, { marketCode: 'KOSDAQ', state: 'NO_DATA' },
  ] } }} retry={vi.fn()} onEvidence={vi.fn()} />)
  expect(screen.getByLabelText('KOSPI 지수')).toBeInTheDocument()
  expect(screen.getByLabelText('KOSDAQ 지수')).toBeInTheDocument()
  expect(screen.getAllByText(/아직 표시할 수 있는 공식 KRX 지수값이 없습니다/)).toHaveLength(2)
})
