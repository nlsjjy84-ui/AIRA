import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import AiAssist from './AiAssist.jsx'
import AiRoute from './AiRoute.jsx'

const facts = [{ label: '매출', value: '12.04조 원', period: '2025-01-01 — 2025-12-31' }]
const ok = body => ({ ok: true, status: 200, json: async () => body })

beforeEach(() => { vi.restoreAllMocks() })

describe('AI reading assist', () => {
  it('only offers a login hint when logged out and never calls the server', () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    render(<AiAssist kind="INSPECT" subject="NAVER" facts={facts} loggedIn={false} />)
    expect(screen.getByText(/로그인하면/)).toBeInTheDocument()
    expect(screen.queryByRole('button')).toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows the AI text with a not-evidence badge and can translate it', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(ok({ method: 'AI', text: '매출은 12.04조 원입니다.', limitations: ['근거(Evidence)가 아닙니다.'] }))
      .mockResolvedValueOnce(ok({ method: 'AI', text: 'Revenue is 12.04 trillion won.', limitations: [] }))
    vi.stubGlobal('fetch', fetchMock)
    render(<AiAssist kind="INSPECT" subject="NAVER" facts={facts} loggedIn />)
    fireEvent.click(screen.getByRole('button', { name: '쉬운 말로 풀어보기' }))
    expect(await screen.findByText('매출은 12.04조 원입니다.')).toBeInTheDocument()
    expect(screen.getByText('AI 참고 · 근거 아님')).toBeInTheDocument()
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toMatchObject({ kind: 'INSPECT', subject: 'NAVER' })
    fireEvent.click(screen.getByRole('button', { name: 'English' }))
    expect(await screen.findByText('Revenue is 12.04 trillion won.')).toBeInTheDocument()
    expect(JSON.parse(fetchMock.mock.calls[1][1].body).kind).toBe('TRANSLATE')
  })

  it('labels fallback text as rule based and keeps the page usable when the call fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(ok({ method: 'RULE', text: 'NAVER의 화면 값: 매출 12.04조 원', limitations: ['AI를 사용할 수 없어 화면의 값만 정리했습니다.'] }))
      .mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) }))
    const { rerender } = render(<AiAssist kind="INSPECT" subject="NAVER" facts={facts} loggedIn />)
    fireEvent.click(screen.getByRole('button', { name: '쉬운 말로 풀어보기' }))
    expect(await screen.findByText('규칙 기반 정리')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'English' })).toBeNull()
    rerender(<AiAssist kind="INSPECT" subject="NAVER" facts={[{ label: '영업이익', value: '2.21조 원', period: '2025' }]} loggedIn />)
    fireEvent.click(screen.getByRole('button', { name: '쉬운 말로 풀어보기' }))
    expect(await screen.findByText(/AI 보조를 지금 사용할 수 없습니다/)).toBeInTheDocument()
  })
})

describe('AI route finder', () => {
  it('shows the understood conditions and only follows them after confirmation', async () => {
    const route = { method: 'AI', understood: true, entityQuery: '삼성전자', perspective: 'INSPECT', category: '재무', year: 2025, items: ['OPERATING_INCOME'], note: '확인 후 읽습니다.' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ok(route)))
    const onFollow = vi.fn()
    render(<AiRoute loggedIn labels={{ OPERATING_INCOME: '영업이익' }} onFollow={onFollow} />)
    fireEvent.change(screen.getByLabelText('질문'), { target: { value: '삼전 작년 영업이익' } })
    fireEvent.click(screen.getByRole('button', { name: '길 찾기' }))
    expect(await screen.findByText('이렇게 이해했어요')).toBeInTheDocument()
    expect(screen.getByText('2025년 사업연도')).toBeInTheDocument()
    expect(screen.getByText('영업이익')).toBeInTheDocument()
    expect(onFollow).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '이대로 확인하기' }))
    await waitFor(() => expect(onFollow).toHaveBeenCalledWith(route))
  })

  it('falls back to a plain search when the question is not understood and hides for logged-out users', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ok({ method: 'NONE', understood: false, entityQuery: '네이버', items: [], note: 'AI를 사용할 수 없어 입력하신 말로 검색합니다.' })))
    const onFollow = vi.fn()
    const { unmount } = render(<AiRoute loggedIn onFollow={onFollow} />)
    fireEvent.change(screen.getByLabelText('질문'), { target: { value: '네이버' } })
    fireEvent.click(screen.getByRole('button', { name: '길 찾기' }))
    fireEvent.click(await screen.findByRole('button', { name: '이 말로 검색하기' }))
    expect(onFollow).toHaveBeenCalledWith(expect.objectContaining({ understood: false, entityQuery: '네이버' }))
    unmount()
    render(<AiRoute loggedIn={false} onFollow={onFollow} />)
    expect(screen.queryByLabelText('질문')).toBeNull()
  })
})
