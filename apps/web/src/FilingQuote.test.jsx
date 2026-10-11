import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import FilingQuote from './FilingQuote.jsx'
import AiRelate from './AiRelate.jsx'

const ok = body => ({ ok: true, status: 200, json: async () => body })
const RECEIPT = '20260101000001'

beforeEach(() => { vi.restoreAllMocks() })

describe('filing quote answers', () => {
  it('only shows a login hint when logged out and never calls the server', () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    render(<FilingQuote receipt={RECEIPT} loggedIn={false} />)
    expect(screen.getByText(/로그인하면/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '원문에서 찾기' })).toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('needs a 14 digit receipt and a question before it can search', () => {
    render(<FilingQuote receipt="" loggedIn />)
    expect(screen.getByText(/접수번호 14자리를 입력하면/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '원문에서 찾기' })).toBeDisabled()
  })

  it('shows literal quotes with the passage number and a not-evidence note', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(ok({ method: 'AI', receiptNumber: RECEIPT,
      quotes: [{ passageNo: 3, text: '계약금액 | 1,234억원' }], explanation: '계약금액은 1,234억원이라고 적혀 있습니다.',
      limitations: ['인용은 서버가 원문과 글자 그대로 일치하는지 확인했습니다.'] }))
    vi.stubGlobal('fetch', fetchMock)
    render(<FilingQuote receipt={RECEIPT} loggedIn />)
    fireEvent.change(screen.getByLabelText('공시 원문에 대한 질문'), { target: { value: '계약금액이 얼마인가요?' } })
    fireEvent.click(screen.getByRole('button', { name: '원문에서 찾기' }))
    expect(await screen.findByText('계약금액 | 1,234억원')).toBeInTheDocument()
    expect(screen.getByText('원문 문단 3 · 글자 그대로')).toBeInTheDocument()
    expect(screen.getByText('AI 풀이 · 근거 아님')).toBeInTheDocument()
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ receiptNumber: RECEIPT, question: '계약금액이 얼마인가요?' })
    expect(screen.getByRole('link', { name: 'DART에서 원문 열기' })).toHaveAttribute('href', expect.stringContaining(RECEIPT))
  })

  it('compares two receipts and shows before and after passages', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(ok({ method: 'RULE', changes: [{ before: '계약금액 | 1,234억원', after: '계약금액 | 1,300억원' }],
      totalChanges: 1, truncated: false, limitations: ['두 접수번호가 같은 공시의 정정 관계인지는 확인하지 않습니다.'] }))
    vi.stubGlobal('fetch', fetchMock)
    render(<FilingQuote receipt={RECEIPT} loggedIn />)
    fireEvent.click(screen.getByRole('button', { name: '정정 전후 비교' }))
    fireEvent.change(screen.getByLabelText('정정 후 접수번호'), { target: { value: '20260101000002' } })
    fireEvent.click(screen.getByRole('button', { name: '달라진 문단 보기' }))
    expect(await screen.findByText('계약금액 | 1,300억원')).toBeInTheDocument()
    expect(screen.getByText('계약금액 | 1,234억원')).toBeInTheDocument()
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ originalReceipt: RECEIPT, amendedReceipt: '20260101000002' })
  })

  it('keeps the page usable when the call fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) }))
    render(<FilingQuote receipt={RECEIPT} loggedIn />)
    fireEvent.change(screen.getByLabelText('공시 원문에 대한 질문'), { target: { value: '계약금액' } })
    fireEvent.click(screen.getByRole('button', { name: '원문에서 찾기' }))
    expect(await screen.findByText(/원문 확인을 지금 사용할 수 없습니다/)).toBeInTheDocument()
  })
})

describe('AI relation candidates', () => {
  const events = [
    { eventId: 'e1', title: '유상증자 결정', eventType: 'EARNINGS', occurredAt: '2026-03-04T00:00:00Z' },
    { eventId: 'e2', title: '유상증자 결과', eventType: 'EARNINGS', occurredAt: '2026-03-20T00:00:00Z' },
  ]

  it('needs two confirmed events and a login', () => {
    const { rerender } = render(<AiRelate loggedIn={false} events={events} />)
    expect(screen.getByText(/로그인하면/)).toBeInTheDocument()
    rerender(<AiRelate loggedIn events={events.slice(0, 1)} />)
    expect(screen.getByText(/2건 이상/)).toBeInTheDocument()
  })

  it('labels every candidate as unconfirmed and ignores ids it does not know', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(ok({ method: 'AI', candidates: [
      { firstId: 'e1', secondId: 'e2', reason: '두 사건 제목에 모두 유상증자가 나옵니다.' },
      { firstId: 'e1', secondId: 'zz', reason: '모르는 사건' }], limitations: ['후보는 관계를 확정하지 않습니다.'] }))
    vi.stubGlobal('fetch', fetchMock)
    const onSelect = vi.fn()
    render(<AiRelate loggedIn subject="가나다" events={events} typeLabels={{ EARNINGS: '실적' }} onSelect={onSelect} />)
    fireEvent.click(screen.getByRole('button', { name: '함께 볼 사건 쌍 찾기' }))
    expect(await screen.findByText('두 사건 제목에 모두 유상증자가 나옵니다.')).toBeInTheDocument()
    expect(screen.getByText('AI 후보 · 미확인')).toBeInTheDocument()
    expect(screen.getByText('검토 필요 · 공식 근거로 확인 전')).toBeInTheDocument()
    expect(screen.queryByText('모르는 사건')).toBeNull()
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).events[0]).toEqual({ id: 'e1', title: '유상증자 결정', type: 'EARNINGS', date: '2026-03-04' })
    fireEvent.click(screen.getAllByRole('button', { name: '이 사건 판단 보기' })[0])
    expect(onSelect).toHaveBeenCalledWith(events[0])
  })
})
