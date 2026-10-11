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
})
