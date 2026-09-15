import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { AssessmentFlow, EventTimeline, EvidenceChain, FinancialOverview, FinancialSplit,
  MarketPreviousView, MarketSeriesView, formatQuantity, MarketOverview, OhlcCandle } from './visualizations.jsx'

const a = { state: 'AVAILABLE', periodStart: '2025-01-01', periodEnd: '2025-12-31', receipt: 'R-A',
  value: { facts: [{ predicate: 'REVENUE', value: '0', currency: 'KRW', evidenceId: 'e-a',
    periodStart: '2025-01-01', periodEnd: '2025-12-31', sourceName: 'OpenDART' }] } }
const comparison = { state: 'AVAILABLE', a: { periodStart: '2025-01-01', periodEnd: '2025-12-31', receipt: 'R-A' },
  b: { periodStart: '2024-01-01', periodEnd: '2024-12-31', receipt: 'R-B' },
  metrics: [{ predicate: 'REVENUE', a: { value: '0', currency: 'KRW', evidenceIds: ['e-a'] },
    b: { value: '100000000', currency: 'KRW', evidenceIds: ['e-b'] },
    changeAmountBMinusA: '100000000', changePercentBOverA: null, percentReason: 'BASE_NON_POSITIVE' }] }

describe('evidence-linked visuals', () => {
  it('formats zero as zero and missing as missing, with Korean units', () => {
    expect(formatQuantity('0', 'KRW')).toBe('0원')
    expect(formatQuantity(null, 'KRW')).toBe('자료 없음')
    expect(formatQuantity('100000000', 'KRW')).toBe('1억 원')
    expect(formatQuantity('2.5', '%p')).toBe('2.5%p')
  })

  it('opens an exact financial value and its evidence, then compares only explicit A and B', () => {
    const onEvidence = vi.fn()
    render(<><FinancialOverview observation={a} onEvidence={onEvidence} />
      <FinancialSplit comparison={comparison} onEvidence={onEvidence} /></>)
    expect(screen.getAllByText('0원').length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: '상세 보기' }))
    expect(screen.getByText('e-a')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '근거 확인' }))
    fireEvent.click(screen.getByRole('button', { name: 'B Evidence ID e-b' }))
    expect(onEvidence.mock.calls.map(call => call[0])).toEqual(['e-a', 'e-b'])
    expect(screen.getAllByRole('meter')).toHaveLength(2)
    expect(screen.getByText(/증감액 1억 원 · 증감률 계산 불가 \(BASE_NON_POSITIVE\)/)).toBeInTheDocument()
  })

  it('refuses an inferred candle when a value or same official D is missing', () => {
    const value = (name, date = '2026-09-14') => ({ state: 'AVAILABLE', value: {
      factId: name, tradingDate: date, value: { OPEN_PRICE: 100, HIGH_PRICE: 110, LOW_PRICE: 90, CLOSE_PRICE: 105 }[name] } })
    const observations = Object.fromEntries(['OPEN_PRICE', 'HIGH_PRICE', 'LOW_PRICE', 'CLOSE_PRICE'].map(name => [name, value(name)]))
    const { rerender } = render(<OhlcCandle observations={{ ...observations, LOW_PRICE: { state: 'NO_DATA' } }} />)
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    rerender(<OhlcCandle observations={{ ...observations, CLOSE_PRICE: value('CLOSE_PRICE', '2026-09-13') }} />)
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    rerender(<OhlcCandle observations={observations} />)
    expect(screen.getByRole('img')).toHaveAttribute('aria-label', expect.stringContaining('시가 100'))
  })

  it('keeps Event with unknown occurredAt separate and follows supersession IDs', () => {
    const select = vi.fn(), historical = vi.fn(), evidence = vi.fn()
    render(<><EventTimeline events={[{ eventId: 'unknown', title: '미상', occurredAt: null },
      { eventId: 'dated', title: '확정', occurredAt: '2026-09-14T00:00:00Z' }]} onSelect={select} />
      <AssessmentFlow assessment={{ assessmentId: 'A2', supersedesAssessmentId: 'A1', evidenceIds: ['E1'] }}
        onHistorical={historical} onEvidence={evidence} /></>)
    expect(screen.getAllByRole('listitem')[0]).toHaveTextContent('확정')
    expect(screen.getAllByRole('listitem')[1]).toHaveTextContent('발생시각 미상')
    fireEvent.click(screen.getByRole('button', { name: /이전 당시 판단.*A1/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Evidence ID E1' }))
    expect(historical).toHaveBeenCalledWith('A1')
    expect(evidence).toHaveBeenCalledWith('E1', { type: 'ASSESSMENT', label: 'A2' })
  })

  it('shows a single market observation without inventing a price trend', () => {
    render(<MarketOverview predicate="CLOSE_PRICE" observation={{ value: { factId: 'F1', tradingDate: '2026-09-14', value: '1000' } }} />)
    expect(screen.getByText(/공식 거래일 2026-09-14/)).toBeInTheDocument()
    expect(screen.getByText(/단일 관측값/)).toBeInTheDocument()
  })

  it('shows an evidence path only for the clicked known relation', () => {
    render(<EvidenceChain evidence={{ evidenceId: 'E1', title: '공식 자료', source: { sourceName: 'OpenDART', sourceType: 'REGULATOR' } }}
      relation={{ type: 'FACT', label: 'REVENUE · 2025-01-01 — 2025-12-31' }} />)
    expect(screen.getByRole('list')).toHaveTextContent('OpenDART')
    expect(screen.getByRole('list')).toHaveTextContent('Evidence ID E1')
    expect(screen.getByRole('list')).toHaveTextContent('FACT · REVENUE')
  })

  it('shows only returned official market dates and drills into exact Evidence', () => {
    const onEvidence = vi.fn()
    const series = { state: 'AVAILABLE', predicate: 'CLOSE_PRICE', from: '2026-09-10', to: '2026-09-14',
      points: [{ tradingDate: '2026-09-10', factId: 'F1', value: '1000', evidenceIds: ['E1'], evidenceExternalId: 'KRX:10' },
        { tradingDate: '2026-09-14', factId: 'F2', value: '1200', evidenceIds: ['E2'], evidenceExternalId: 'KRX:14' }] }
    render(<MarketSeriesView series={series} onEvidence={onEvidence} />)
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.queryByText('2026-09-11')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /2026-09-14/ }))
    expect(screen.getByText(/Fact ID F2/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Evidence ID E2' }))
    expect(onEvidence).toHaveBeenCalledWith('E2', { type: 'FACT', label: 'CLOSE_PRICE · 2026-09-14 · F2' })
  })

  it('uses backend previous date and change without choosing a neighbor', () => {
    const response = { state: 'AVAILABLE', predicate: 'CLOSE_PRICE', changeAmount: '200', changePercent: '20.0000',
      current: { tradingDate: '2026-09-14', factId: 'F2', value: '1200', evidenceIds: ['E2'] },
      previous: { tradingDate: '2026-09-10', factId: 'F1', value: '1000', evidenceIds: ['E1'] } }
    render(<MarketPreviousView comparison={response} onEvidence={vi.fn()} />)
    expect(screen.getByText(/직전 관측 · 2026-09-10/)).toBeInTheDocument()
    expect(screen.getByText(/증감액 200원 · 증감률 20%/)).toBeInTheDocument()
  })
})
