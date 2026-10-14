import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import ClaimCheck from './ClaimCheck.jsx'

const ok = body => ({ ok: true, status: 200, json: async () => body })

beforeEach(() => { vi.restoreAllMocks() })

describe('claim check', () => {
  it('shows only a login hint when logged out', () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    render(<ClaimCheck loggedIn={false} />)
    expect(screen.getByText(/로그인하면/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '사실 확인' })).toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('needs some text before the check can run', () => {
    render(<ClaimCheck loggedIn />)
    expect(screen.getByRole('button', { name: '사실 확인' })).toBeDisabled()
  })

  it('shows each verdict with the official value and a DART link', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(ok({ method: 'AI', providerKey: 'GEMINI', limitations: ['AI는 문장을 나누기만 했습니다.'],
      results: [
        { claim: '영업이익 50조', verdict: 'MISMATCH', company: '삼성전자', item: '영업이익', year: 2025, stated: '50조원',
          official: '43.60조 원 (43,601,051,000,000원)', period: '2025-01-01 ~ 2025-12-31', receiptNumber: '20260310002820',
          evidenceUrl: 'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260310002820', note: '말한 금액이 공식 값과 다릅니다.' },
        { claim: '좋은 회사', verdict: 'NOT_CHECKABLE', note: '의견이나 평가라 공식 자료로 확인할 수 없습니다.' }
      ] }))
    vi.stubGlobal('fetch', fetchMock)
    render(<ClaimCheck loggedIn />)
    fireEvent.change(screen.getByLabelText('확인하고 싶은 말'), { target: { value: '삼성전자 영업이익이 50조라던데' } })
    fireEvent.click(screen.getByRole('button', { name: '사실 확인' }))
    expect(await screen.findByText('다름')).toBeInTheDocument()
    expect(screen.getByText('43.60조 원 (43,601,051,000,000원)')).toBeInTheDocument()
    expect(screen.getByText('20260310002820')).toBeInTheDocument()
    expect(screen.getByText('확인 불가')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'DART에서 공시 열기' })).toHaveAttribute('href', expect.stringContaining('20260310002820'))
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ text: '삼성전자 영업이익이 50조라던데' })
  })

  it('says so when nothing could be checked', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(ok({ method: 'NONE', results: [], limitations: ['a', '확인할 만한 말을 찾지 못했습니다.'] })))
    render(<ClaimCheck loggedIn />)
    fireEvent.change(screen.getByLabelText('확인하고 싶은 말'), { target: { value: '오늘 날씨 좋네요' } })
    fireEvent.click(screen.getByRole('button', { name: '사실 확인' }))
    expect(await screen.findByText('확인할 만한 말을 찾지 못했습니다.')).toBeInTheDocument()
  })

  it('shows a plain message when the server fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) }))
    render(<ClaimCheck loggedIn />)
    fireEvent.change(screen.getByLabelText('확인하고 싶은 말'), { target: { value: '삼성전자 영업이익 43.6조' } })
    fireEvent.click(screen.getByRole('button', { name: '사실 확인' }))
    expect(await screen.findByText(/사실 확인을 지금 사용할 수 없습니다/)).toBeInTheDocument()
  })

  it('shows both years and both source links for a change claim', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(ok({ method: 'AI', limitations: [], results: [
      { claim: '영업이익 33% 증가', verdict: 'MATCH', company: '삼성전자', item: '영업이익', year: 2025, stated: '전년 대비 +33.0%',
        official: '2024 32.73조 원 → 2025 43.60조 원 (+33.2%)', period: '2024-01-01 ~ 2025-12-31', receiptNumber: '20260310002820',
        evidenceUrl: 'https://dart.fss.or.kr/a', priorReceiptNumber: '20250311001085', priorEvidenceUrl: 'https://dart.fss.or.kr/b', note: 'ok' }] })))
    render(<ClaimCheck loggedIn />)
    fireEvent.change(screen.getByLabelText('확인하고 싶은 말'), { target: { value: '삼성전자 영업이익이 33% 늘었다' } })
    fireEvent.click(screen.getByRole('button', { name: '사실 확인' }))
    expect(await screen.findByText('일치')).toBeInTheDocument()
    expect(screen.getByText('20250311001085')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '전년 공시 열기' })).toHaveAttribute('href', 'https://dart.fss.or.kr/b')
  })

  it('home hero works before login: an example fills the box and the check enters the demo account first', async () => {
    const order = []
    const onNeedLogin = vi.fn(async () => { order.push('login'); return true })
    const fetchMock = vi.fn(async url => {
      if (String(url).includes('claim-stats')) return ok({ total: 0, mismatches: 0, byItem: [] })
      order.push('check'); return ok({ method: 'AI', limitations: [], results: [
      { claim: '매출 400조', verdict: 'MISMATCH', company: '삼성전자', item: '매출액', stated: '400조원', official: '333.61조 원' }] }) })
    vi.stubGlobal('fetch', fetchMock)
    render(<ClaimCheck hero loggedIn={false} onNeedLogin={onNeedLogin} />)
    expect(screen.getByText(/읽기 전용 데모 계정으로 바로 확인/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /매출이 400조/ }))
    expect(screen.getByLabelText('확인하고 싶은 말').value).toContain('400조')
    fireEvent.click(screen.getByRole('button', { name: '사실 확인' }))
    expect(await screen.findByText('다름')).toBeInTheDocument()
    expect(order).toEqual(['login', 'check'])
  })

  it('home hero says so when the demo account cannot be entered, and does not call the check', async () => {
    const fetchMock = vi.fn(async () => ok({ total: 0, mismatches: 0, byItem: [] }))
    vi.stubGlobal('fetch', fetchMock)
    render(<ClaimCheck hero loggedIn={false} onNeedLogin={async () => false} />)
    fireEvent.change(screen.getByLabelText('확인하고 싶은 말'), { target: { value: '삼성전자 영업이익 43.6조' } })
    fireEvent.click(screen.getByRole('button', { name: '사실 확인' }))
    expect(await screen.findByText(/데모 계정에 들어갈 수 없습니다/)).toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([url]) => !String(url).includes('claim-stats'))).toHaveLength(0)
  })

  it('home hero shows the 30-day verdict numbers only once there are enough checks', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ok({ total: 37, mismatches: 11, byItem: [{ item: '영업이익', checks: 20, mismatches: 7 }] })))
    render(<ClaimCheck hero loggedIn={false} onNeedLogin={async () => true} />)
    expect(await screen.findByLabelText('최근 30일 사실 확인 통계')).toHaveTextContent('최근 30일 확인한 말 37건 · 공식 값과 다름 11건 · 가장 많이 확인한 숫자: 영업이익')
  })
  it('names the basis of the official value and opens the 연결·별도·잠정 guide on a mismatch', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(ok({ method: 'AI', providerKey: 'GEMINI', limitations: [],
      results: [
        { claim: '영업이익 50조', verdict: 'MISMATCH', company: '삼성전자', item: '영업이익', year: 2025, stated: '50조원',
          official: '43.60조 원', period: '2025-01-01 ~ 2025-12-31', note: '말한 금액이 공식 값과 다릅니다.' },
        { claim: '매출 300조', verdict: 'MATCH', company: '삼성전자', item: '매출', year: 2025, stated: '300조원',
          official: '300.87조 원', period: '2025-01-01 ~ 2025-12-31', note: '공식 값과 같습니다.' },
        { claim: '좋은 회사', verdict: 'NOT_CHECKABLE', note: '의견이라 확인할 수 없습니다.' }
      ] })))
    const { container } = render(<ClaimCheck loggedIn />)
    fireEvent.change(screen.getByLabelText('확인하고 싶은 말'), { target: { value: '삼성전자 영업이익 50조, 매출 300조' } })
    fireEvent.click(screen.getByRole('button', { name: '사실 확인' }))
    expect(await screen.findByText('다름')).toBeInTheDocument()
    expect(screen.getAllByText('연결 · 사업보고서 확정치')).toHaveLength(2)
    const guides = container.querySelectorAll('details.claim-basis')
    expect(guides).toHaveLength(2)
    expect(guides[0]).toHaveAttribute('open')
    expect(guides[1]).not.toHaveAttribute('open')
    expect(screen.getAllByText(/본사 한 곳만의 숫자/)).toHaveLength(2)
    expect(screen.getByText(/별도나 잠정 기준이면/)).toBeInTheDocument()
  })
})
