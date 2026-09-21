import { expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import MarketNewsBoard from './MarketNewsBoard.jsx'

it('renders recent market news as external metadata links without AIRA interpretation', () => {
  render(<MarketNewsBoard state={{ loading: false, error: null, data: {
    provider: 'GDELT DOC 2.0', stale: false, items: [
      { title: '코스피 관련 시장 기사', originalUrl: 'https://news.example/a', domain: 'news.example', seenAt: '2026-09-21T01:30:00Z', language: 'Korean' },
    ],
  } }} retry={vi.fn()} />)
  expect(screen.getByRole('heading', { name: '오늘 확인할 시장 뉴스' })).toBeInTheDocument()
  expect(screen.getByText('코스피 관련 시장 기사')).toBeInTheDocument()
  expect(screen.getByText('news.example')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /원문/ })).toHaveAttribute('href', 'https://news.example/a')
  expect(screen.getByText(/뉴스는 AIRA의 Fact·Event·판단과 분리합니다/)).toBeInTheDocument()
  expect(screen.queryByText(/추천/)).not.toBeInTheDocument()
})

it('keeps provider failure isolated from the rest of MAIN', () => {
  render(<MarketNewsBoard state={{ loading: false, error: new Error('provider unavailable'), data: null }} retry={vi.fn()} />)
  expect(screen.getByRole('alert')).toHaveTextContent('시장 지수와 공식 사건은 계속 확인할 수 있습니다')
  expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
})

it('marks stale provider data without hiding the existing links', () => {
  render(<MarketNewsBoard state={{ loading: false, error: null, data: {
    provider: 'GDELT DOC 2.0', stale: true, items: [
      { title: '직전 수집 기사', originalUrl: 'https://news.example/stale', domain: 'news.example', seenAt: '2026-09-21T00:00:00Z' },
    ],
  } }} retry={vi.fn()} />)
  expect(screen.getByRole('status')).toHaveTextContent('직전 수집 결과')
  expect(screen.getByText('직전 수집 기사')).toBeInTheDocument()
})
