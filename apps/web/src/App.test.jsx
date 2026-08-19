import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.jsx'

const company = { companyId: '5eafc0b5-c163-4cea-8dbd-131265004e95', canonicalName: '삼성전자', countryCode: 'KR' }
const period = { periodStart: '2025-01-01', periodEnd: '2025-12-31', predicates: ['REVENUE', 'OPERATING_INCOME'] }
const facts = [
  { predicate: 'REVENUE', value: 333605938000000, currency: 'KRW', evidenceId: 'evidence-1', sourceName: 'OpenDART', evidenceExternalId: '20260310002820', evidenceOriginalUrl: 'https://dart.fss.or.kr/report/viewer.do?rcept_no=20260310002820' },
  { predicate: 'OPERATING_INCOME', value: 43601051000000, currency: 'KRW', evidenceId: 'evidence-1', sourceName: 'OpenDART', evidenceExternalId: '20260310002820', evidenceOriginalUrl: 'https://dart.fss.or.kr/report/viewer.do?rcept_no=20260310002820' },
]

function json(body, status = 200) {
  return Promise.resolve({ ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) })
}

function successfulFetch() {
  return vi.fn()
    .mockImplementationOnce(() => json({ companies: [company] }))
    .mockImplementationOnce(() => json({ companyId: company.companyId, periods: [period] }))
    .mockImplementationOnce(() => json({ companyId: company.companyId, facts }))
}

beforeEach(() => { global.fetch = successfulFetch() })
afterEach(() => vi.restoreAllMocks())

describe('public company financial journey', () => {
  it('renders entry, browses Samsung, selects the sole period, and groups shared evidence', async () => {
    const user = userEvent.setup()
    render(<App />)
    expect(screen.getByRole('heading', { name: /공식 데이터와 근거/ })).toBeInTheDocument()
    expect(screen.getByText('기업을 불러오는 중입니다.')).toBeInTheDocument()

    await user.click(await screen.findByRole('button', { name: /삼성전자/ }))
    expect(await screen.findByLabelText('정확한 보고 기간')).toHaveValue('2025-01-01|2025-12-31')
    expect(await screen.findByText('333,605,938,000,000')).toBeInTheDocument()
    expect(screen.getByText('43,601,051,000,000')).toBeInTheDocument()
    expect(screen.getByText('매출')).toBeInTheDocument()
    expect(screen.getByText('영업이익')).toBeInTheDocument()
    expect(screen.getAllByText('OpenDART')).toHaveLength(1)
    expect(screen.getAllByText(/20260310002820/)).toHaveLength(1)
    const link = screen.getByRole('link', { name: /원문 확인/ })
    expect(link).toHaveAttribute('href', facts[0].evidenceOriginalUrl)
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('announces period and fact loading states', async () => {
    const user = userEvent.setup()
    let releasePeriods
    let releaseFacts
    global.fetch = vi.fn()
      .mockImplementationOnce(() => json({ companies: [company] }))
      .mockImplementationOnce(() => new Promise(resolve => { releasePeriods = () => resolve({
        ok: true, status: 200, json: () => Promise.resolve({ periods: [period] }),
      }) }))
      .mockImplementationOnce(() => new Promise(resolve => { releaseFacts = () => resolve({
        ok: true, status: 200, json: () => Promise.resolve({ facts }),
      }) }))
    render(<App />)
    await user.click(await screen.findByRole('button', { name: /삼성전자/ }))
    expect(screen.getByText('이용 가능한 기간을 불러오는 중입니다.')).toBeInTheDocument()
    releasePeriods()
    expect(await screen.findByText('재무정보와 공식 근거를 확인하는 중입니다.')).toBeInTheDocument()
    releaseFacts()
    expect(await screen.findByText('333,605,938,000,000')).toBeInTheDocument()
  })

  it('shows company empty state', async () => {
    global.fetch = vi.fn(() => json({ companies: [] }))
    render(<App />)
    expect(await screen.findByText(/현재 확인할 수 있는 기업이 없습니다/)).toBeInTheDocument()
  })

  it('shows period and fact empty states', async () => {
    const user = userEvent.setup()
    global.fetch = vi.fn()
      .mockImplementationOnce(() => json({ companies: [company] }))
      .mockImplementationOnce(() => json({ periods: [] }))
    const { unmount } = render(<App />)
    await user.click(await screen.findByRole('button', { name: /삼성전자/ }))
    expect(await screen.findByText(/이 기업에서 이용 가능한 재무 기간이 없습니다/)).toBeInTheDocument()
    unmount()

    global.fetch = vi.fn()
      .mockImplementationOnce(() => json({ companies: [company] }))
      .mockImplementationOnce(() => json({ periods: [period] }))
      .mockImplementationOnce(() => json({ facts: [] }))
    render(<App />)
    await user.click(await screen.findByRole('button', { name: /삼성전자/ }))
    expect(await screen.findByText(/선택한 기간에 표시할 재무정보가 없습니다/)).toBeInTheDocument()
  })

  it.each([[400, /요청을 확인/], [404, /찾지 못했습니다/], [409, /공식 근거를 일관되게/], [500, /서비스에 연결하지 못했습니다/]])(
    'maps backend status %s to actionable copy and retries', async (status, copy) => {
      const user = userEvent.setup()
      global.fetch = vi.fn()
        .mockImplementationOnce(() => json({ code: 'failure' }, status))
        .mockImplementationOnce(() => json({ companies: [company] }))
      render(<App />)
      const alert = await screen.findByRole('alert')
      expect(within(alert).getByText(copy)).toBeInTheDocument()
      await user.click(within(alert).getByRole('button', { name: '다시 시도' }))
      await waitFor(() => expect(screen.getByRole('button', { name: /삼성전자/ })).toBeInTheDocument())
    },
  )
})
