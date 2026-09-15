import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import CanonicalExplorer from './CanonicalExplorer.jsx'

beforeEach(() => { window.history.replaceState({}, '', '/explore'); vi.restoreAllMocks() })

describe('canonical explorer', () => {
  it('runs a header search query on entry', async () => {
    window.history.replaceState({}, '', '/explore?q=005930')
    const fetchMock = vi.fn(async () => ({ ok: true, status: 200,
      json: async () => ({ state: 'NO_DATA', entities: [] }) }))
    vi.stubGlobal('fetch', fetchMock)
    render(<CanonicalExplorer />)
    expect(await screen.findByText('정확히 일치하는 자료가 없습니다.')).toBeInTheDocument()
    expect(fetchMock.mock.calls[0][0]).toContain('query=005930')
  })

  it('searches for typed identities and restores selected context on browser back', async () => {
    vi.stubGlobal('fetch', vi.fn(async path => ({ ok: true, status: 200, json: async () => path.startsWith('/api/search')
      ? { state: 'AVAILABLE', entities: [
        { entityId: '00000000-0000-0000-0000-000000000001', entityType: 'COMPANY', canonicalName: '삼성', canonicalKey: 'COMPANY:1' },
        { entityId: '00000000-0000-0000-0000-000000000002', entityType: 'SECURITY', canonicalName: '삼성', canonicalKey: 'SECURITY:2', symbol: '005930' },
      ] } : {} })))
    render(<CanonicalExplorer />)
    expect(screen.getByPlaceholderText('기업명·종목명·종목코드 검색')).toBeInTheDocument()
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: '삼성' } })
    fireEvent.click(screen.getByRole('button', { name: '검색' }))
    expect(await screen.findByRole('button', { name: /삼성 종목.*005930/ })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /삼성 종목/ }))
    expect(screen.queryByRole('heading', { name: '정확한 대상을 선택하세요.' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /어떤 관점으로 볼지 선택하세요/ })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '현재 탐색 경로' })).toHaveTextContent('종목')
    expect(screen.getByRole('region', { name: '현재 탐색 문맥' })).toHaveTextContent('대상')
    expect(screen.getByRole('region', { name: '현재 탐색 문맥' })).toHaveTextContent('삼성 · 종목')
    fireEvent.click(screen.getByRole('button', { name: '공식 사실과 근거' }))
    expect(screen.getByRole('heading', { name: /자료를 하위 분류로 좁혀 확인하세요/ })).toBeInTheDocument()
    window.history.back()
    window.dispatchEvent(new PopStateEvent('popstate'))
    await waitFor(() => expect(screen.getByRole('heading', { name: /어떤 관점으로 볼지 선택하세요/ })).toBeInTheDocument())
  })

  it('uses exact company selection and displays the server state without inventing a value', async () => {
    window.history.replaceState({ airaCanonicalExplorer12B: {
      step: 'Inspect', target: { entityId: 'company-1', entityType: 'COMPANY', canonicalName: '회사' },
      perspective: '공식 사실과 근거', category: '재무', detail: 'Historical Exact',
      periodStart: '2025-01-01', periodEnd: '2025-12-31', receipt: '20260101000001',
      predicate: 'CLOSE_PRICE', eventId: '', comparison: null, assessmentId: null, evidenceId: null,
    } }, '', '/explore')
    const fetchMock = vi.fn(async () => ({ ok: true, status: 200, json: async () => ({ state: 'NO_DATA', selection: 'HISTORICAL_EXACT', value: null }) }))
    vi.stubGlobal('fetch', fetchMock)
    render(<CanonicalExplorer />)
    fireEvent.click(screen.getByRole('button', { name: '정확한 자료 확인' }))
    const label = await screen.findByText('결과 없음')
    const notice = label.closest('[role="status"]')
    expect(notice).toHaveTextContent('결과 없음')
    expect(notice).toHaveTextContent('정확히 일치하는 자료가 없습니다.')
    expect(notice).not.toHaveTextContent('NO_DATA')
    expect(fetchMock.mock.calls[0][0]).toContain('periodStart=2025-01-01')
    expect(fetchMock.mock.calls[0][0]).toContain('receipt=20260101000001')
  })

  it('opens B only on demand and keeps exact periods and Evidence IDs separate', async () => {
    window.history.replaceState({ airaCanonicalExplorer12B: {
      step: 'Inspect', target: { entityId: 'company-1', entityType: 'COMPANY', canonicalName: '회사' },
      perspective: '공식 사실과 근거', category: '재무', detail: 'Historical Exact',
      periodStart: '2025-01-01', periodEnd: '2025-12-31', receipt: '20260101000001',
      predicate: 'CLOSE_PRICE', eventId: '', comparison: null, assessmentId: null, evidenceId: null,
    } }, '', '/explore')
    const fetchMock = vi.fn(async path => ({ ok: true, status: 200, json: async () => path.includes('/compare?')
      ? { state: 'AVAILABLE', a: { periodStart: '2025-01-01', periodEnd: '2025-12-31', receipt: '20260101000001' },
        b: { periodStart: '2024-01-01', periodEnd: '2024-12-31', receipt: '20250101000001' },
        metrics: [{ predicate: 'REVENUE', a: { value: '100', currency: 'KRW', evidenceIds: ['E-A'] },
          b: { value: '80', currency: 'KRW', evidenceIds: ['E-B'] }, changeAmountBMinusA: '-20',
          changePercentBOverA: '-20.0000', percentReason: null }] }
      : { state: 'AVAILABLE', selection: 'HISTORICAL_EXACT', periodStart: '2025-01-01', periodEnd: '2025-12-31',
        receipt: '20260101000001', value: { facts: [{ predicate: 'REVENUE', value: '100',
          currency: 'KRW', evidenceId: 'E-A', periodStart: '2025-01-01', periodEnd: '2025-12-31' }] } } }))
    vi.stubGlobal('fetch', fetchMock)
    render(<CanonicalExplorer />)
    fireEvent.click(screen.getByRole('button', { name: '정확한 자료 확인' }))
    expect(await screen.findByText(/정확한 기간·공시.*공시 접수번호 20260101000001/)).toBeInTheDocument()
    expect(screen.queryByText(/HISTORICAL_EXACT/)).not.toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Inspect 하위 메뉴' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '확인된 사실' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('region', { name: '현재 탐색 문맥' })).toHaveTextContent('정확한 기간·공시')
    expect(screen.queryByRole('button', { name: '공식 근거 상세' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '공식 근거' }))
    expect(screen.getByRole('button', { name: '공식 근거' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: '공식 근거 상세' })).toBeInTheDocument()
    expect(screen.queryByText('100원')).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: '기간 비교' }))
    expect(screen.getByRole('button', { name: '기간 비교' })).toHaveAttribute('aria-current', 'page')
    fireEvent.click(screen.getByRole('button', { name: '분할보기 열기' }))
    expect(fetchMock).toHaveBeenCalledTimes(1)
    fireEvent.change(screen.getByLabelText('B 기간 시작'), { target: { value: '2024-01-01' } })
    fireEvent.change(screen.getByLabelText('B 기간 종료'), { target: { value: '2024-12-31' } })
    fireEvent.change(screen.getByLabelText('B 공시 접수번호'), { target: { value: '20250101000001' } })
    fireEvent.click(screen.getByRole('button', { name: 'B 관측값 확인' }))
    expect(await screen.findByRole('button', { name: 'B 근거 식별자 · E-B' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'A 근거 식별자 · E-A' })).toBeInTheDocument()
    expect(screen.getByText('증감액 -20원')).toBeInTheDocument()
    expect(screen.getByText('증감률 -20%')).toBeInTheDocument()
    expect(fetchMock.mock.calls[1][0]).toContain('bReceipt=20250101000001')
    expect(fetchMock.mock.calls[1][0]).toContain('aReceipt=20260101000001')
  })

  it('keeps an unavailable exact B as comparison unavailable without another period request', async () => {
    window.history.replaceState({ airaCanonicalExplorer12B: {
      step: 'Inspect', target: { entityId: 'company-1', entityType: 'COMPANY', canonicalName: '회사' },
      perspective: '공식 사실과 근거', category: '재무', detail: 'Historical Exact',
      periodStart: '2025-01-01', periodEnd: '2025-12-31', receipt: '20260101000001',
      predicate: 'CLOSE_PRICE', eventId: '', comparison: null, assessmentId: null, evidenceId: null,
    } }, '', '/explore')
    const fetchMock = vi.fn(async path => ({ ok: true, status: 200, json: async () => path.includes('/compare?')
      ? { state: 'NO_DATA', reason: 'B_FACTS_NOT_FOUND', metrics: [] }
      : { state: 'AVAILABLE', periodStart: '2025-01-01', periodEnd: '2025-12-31', receipt: '20260101000001',
        value: { facts: [{ predicate: 'REVENUE', value: '0', currency: 'KRW', evidenceId: 'E-A',
          periodStart: '2025-01-01', periodEnd: '2025-12-31' }] } } }))
    vi.stubGlobal('fetch', fetchMock)
    render(<CanonicalExplorer />)
    fireEvent.click(screen.getByRole('button', { name: '정확한 자료 확인' }))
    await screen.findByText(/정확한 기간·공시.*공시 접수번호 20260101000001/)
    fireEvent.click(screen.getByRole('button', { name: '기간 비교' }))
    fireEvent.click(screen.getByRole('button', { name: '분할보기 열기' }))
    fireEvent.change(screen.getByLabelText('B 기간 시작'), { target: { value: '2024-01-01' } })
    fireEvent.change(screen.getByLabelText('B 기간 종료'), { target: { value: '2024-12-31' } })
    fireEvent.change(screen.getByLabelText('B 공시 접수번호'), { target: { value: '20250101000001' } })
    fireEvent.click(screen.getByRole('button', { name: 'B 관측값 확인' }))
    expect(await screen.findByText(/B 기간에서 비교할 정확한 재무 값을 찾지 못했습니다/)).toBeInTheDocument()
    expect(screen.queryByText(/B_FACTS_NOT_FOUND/)).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'A와 B 분할 비교' })).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('uses official series dates and exact D fact for previous comparison', async () => {
    window.history.replaceState({ airaCanonicalExplorer12B: {
      step: 'Inspect', target: { entityId: 'security-1', entityType: 'SECURITY', canonicalName: '종목' },
      perspective: '공식 사실과 근거', category: '시장', detail: 'KRX Current',
      periodStart: '', periodEnd: '', receipt: '', predicate: 'CLOSE_PRICE', eventId: '',
      comparison: null, assessmentId: null, evidenceId: null,
    } }, '', '/explore')
    const first = { tradingDate: '2026-09-10', factId: 'F1', value: '1000', evidenceIds: ['E1'], evidenceExternalId: 'KRX:10' }
    const current = { tradingDate: '2026-09-14', factId: 'F2', value: '1200', evidenceIds: ['E2'], evidenceExternalId: 'KRX:14' }
    const fetchMock = vi.fn(async path => ({ ok: true, status: 200, json: async () => path.includes('/api/evidence/')
      ? { evidenceId: 'E2', title: 'KRX 공식 자료', source: { sourceName: 'KRX', sourceType: 'EXCHANGE' } }
      : path.includes('/market-series?')
      ? { state: 'AVAILABLE', predicate: 'CLOSE_PRICE', from: '2026-09-10', to: '2026-09-14', points: [first, current] }
      : path.includes('/market-previous?')
        ? { state: 'AVAILABLE', predicate: 'CLOSE_PRICE', current, previous: first, changeAmount: '200', changePercent: '20.0000' }
        : { state: 'AVAILABLE', periodStart: '2026-09-14', periodEnd: '2026-09-14', value: current } }))
    vi.stubGlobal('fetch', fetchMock)
    render(<CanonicalExplorer />)
    fireEvent.click(screen.getByRole('button', { name: '정확한 자료 확인' }))
    await screen.findByText(/KRX 공식 거래일 2026-09-14/)
    expect(screen.getByText(/공식 거래일 현재값.*2026-09-14 — 2026-09-14/)).toBeInTheDocument()
    expect(screen.queryByText(/LATEST_OFFICIAL_MARKET_D/)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '공식 관측 흐름 열기' }))
    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-09-10' } })
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-09-14' } })
    fireEvent.click(screen.getByRole('button', { name: '공식 시계열 확인' }))
    expect(await screen.findByRole('region', { name: '공식 시장 관측 흐름' })).toBeInTheDocument()
    expect(screen.queryByText('2026-09-11')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /2026-09-14.*1,200원/ }))
    fireEvent.click(screen.getByRole('button', { name: '근거 식별자 · E2' }))
    expect(await screen.findByRole('region', { name: '근거 연결' })).toHaveTextContent('KRX 공식 자료')
    expect(screen.getByRole('region', { name: '열린 근거 상세' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.find(([path]) => path.includes('/api/evidence/'))[0]).toBe('/api/evidence/E2')
    fireEvent.click(screen.getByRole('button', { name: '근거 상세 닫기' }))
    expect(screen.queryByRole('region', { name: '열린 근거 상세' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'D와 직전 실제 관측일 비교' }))
    expect(await screen.findByRole('region', { name: 'D와 직전 공식 관측 비교' })).toHaveTextContent('2026-09-10')
    expect(fetchMock.mock.calls.find(([path]) => path.includes('/market-previous?'))[0]).toContain('currentFactId=F2')
  })

  it('does not turn historical series into Current when the exact D fact is absent', async () => {
    window.history.replaceState({ airaCanonicalExplorer12B: {
      step: 'Inspect', target: { entityId: 'security-1', entityType: 'SECURITY', canonicalName: '종목' },
      perspective: '공식 사실과 근거', category: '시장', detail: 'KRX Current',
      periodStart: '', periodEnd: '', receipt: '', predicate: 'CLOSE_PRICE', eventId: '',
      comparison: null, assessmentId: null, evidenceId: null,
    } }, '', '/explore')
    const fetchMock = vi.fn(async path => ({ ok: true, status: 200, json: async () => path.includes('/market-series?')
      ? { state: 'AVAILABLE', predicate: 'CLOSE_PRICE', from: '2026-09-10', to: '2026-09-14',
        points: [{ tradingDate: '2026-09-10', factId: 'OLD', value: '1000', evidenceIds: ['E1'], evidenceExternalId: 'KRX:10' }] }
      : { state: 'NO_DATA', selection: 'LATEST_OFFICIAL_MARKET_D', periodStart: '2026-09-14', periodEnd: '2026-09-14', value: null } }))
    vi.stubGlobal('fetch', fetchMock)
    render(<CanonicalExplorer />)
    fireEvent.click(screen.getByRole('button', { name: '정확한 자료 확인' }))
    await screen.findByText('결과 없음')
    expect(screen.queryByText(/NO_DATA:/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'D와 직전 실제 관측일 비교' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '공식 관측 흐름 열기' }))
    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-09-10' } })
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-09-14' } })
    fireEvent.click(screen.getByRole('button', { name: '공식 시계열 확인' }))
    expect(await screen.findByRole('region', { name: '공식 시장 관측 흐름' })).toHaveTextContent('2026-09-10')
    expect(screen.queryByRole('region', { name: '시장 한눈에 보기' })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([path]) => path.includes('/market-previous?'))).toBe(false)
  })

  it('shows only the selected Relate subsection and never invents unconfirmed links', async () => {
    window.history.replaceState({ airaCanonicalExplorer12B: {
      step: 'Relate', target: { entityId: 'company-1', entityType: 'COMPANY', canonicalName: '회사' },
      perspective: '사건과 분석', category: null, detail: null, periodStart: '', periodEnd: '', receipt: '',
      predicate: 'CLOSE_PRICE', eventId: '', comparison: null, assessmentId: null, evidenceId: null,
    } }, '', '/explore')
    const event = { eventId: 'EV-1', eventType: 'EARNINGS', title: '확인된 실적 공시', occurredAt: '2026-01-02T00:00:00Z',
      evidence: [{ evidenceId: 'E-1', sourceName: 'OpenDART', revision: 1 }] }
    vi.stubGlobal('fetch', vi.fn(async path => ({ ok: true, status: 200, json: async () => path.includes('/events')
      ? { companyId: 'company-1', events: [event] } : {} })))
    render(<CanonicalExplorer />)
    expect(screen.getByRole('region', { name: '현재 탐색 문맥' })).toHaveTextContent('관련 사건')
    expect(screen.getByRole('button', { name: '확인된 연결' })).toHaveAttribute('aria-current', 'page')
    fireEvent.click(screen.getByRole('button', { name: '확인된 사건 불러오기' }))
    expect(await screen.findByText('확인된 실적 공시')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '확인된 사건 다시 불러오기' })).toHaveClass('secondary-action')
    expect(screen.getByRole('button', { name: '공식 근거 · OpenDART' })).toHaveClass('relation-evidence-action')
    expect(screen.getByRole('button', { name: '이 사건 판단 보기' })).toHaveClass('primary-action')
    fireEvent.click(screen.getByRole('button', { name: '정정 이력 연결' }))
    expect(screen.getByRole('button', { name: '정정 이력 연결' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByText('현재 확인된 정정 이력 연결이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByText('확인된 실적 공시')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '추가로 살펴볼 연결' }))
    expect(screen.getByText(/공식 자료에서 직접 확인되지 않은 인과관계는 자동으로 만들지 않습니다/)).toBeInTheDocument()
  })

  it('separates the six Assess subsections and keeps Current and Historical Exact distinct', async () => {
    window.history.replaceState({ airaCanonicalExplorer12B: {
      step: 'Assess', target: { entityId: 'company-1', entityType: 'COMPANY', canonicalName: '회사' },
      perspective: '사건과 분석', category: null, detail: null, periodStart: '', periodEnd: '', receipt: '',
      predicate: 'CLOSE_PRICE', eventId: 'EV-1', comparison: null, assessmentId: null, evidenceId: null,
    } }, '', '/explore')
    const detail = { eventId: 'EV-1', eventType: 'EARNINGS', title: '확인된 실적 공시', occurredAt: '2026-01-02T00:00:00Z',
      companies: [{ companyName: '회사' }], eventEvidence: [], assessment: { assessmentId: 'A2', summary: '현재 AIRA 해석',
        uncertainty: '후속 공시는 아직 확인되지 않았습니다.', confidence: 'HIGH', importance: 'MEDIUM', method: 'RULE', analysisVersion: 'v2', evidence: [] } }
    const current = { state: 'AVAILABLE', value: { assessmentId: 'A2', eventId: 'EV-1', analysisVersion: 'v2', method: 'RULE', confidence: 'HIGH',
      uncertainty: detail.assessment.uncertainty, completedAt: '2026-01-03T00:00:00Z', supersedesAssessmentId: 'A1', evidenceIds: [] } }
    const historical = { assessmentId: 'A1', eventId: 'EV-1', analysisVersion: 'v1', method: 'RULE', confidence: 'MEDIUM',
      uncertainty: '당시 미확인 정보', completedAt: '2026-01-01T00:00:00Z', supersedesAssessmentId: null, evidenceIds: [] }
    vi.stubGlobal('fetch', vi.fn(async path => ({ ok: true, status: 200, json: async () => path === '/api/events/EV-1' ? detail
      : path.startsWith('/api/assessments/current') ? current : path === '/api/assessments/A1' ? historical : {} })))
    render(<CanonicalExplorer />)
    expect(await screen.findByText('확인된 실적 공시')).toBeInTheDocument()
    expect(screen.queryByText(/사건 식별자/)).not.toBeInTheDocument()
    expect(screen.getByRole('region', { name: '현재 탐색 문맥' })).toHaveTextContent('현재 분석')
    expect(screen.getByRole('button', { name: '확인된 사실' })).toHaveAttribute('aria-current', 'page')
    fireEvent.click(screen.getByRole('button', { name: 'AIRA 해석' }))
    expect(screen.getByRole('region', { name: '현재 탐색 문맥' })).toHaveTextContent('현재 분석')
    expect(screen.getByRole('button', { name: 'AIRA 해석' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByText('현재 AIRA 해석')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '현재·이전 판단 연결 확인' }))
    fireEvent.click(await screen.findByRole('button', { name: /이전 당시 판단.*A1/ }))
    expect(await screen.findByText('당시 미확인 정보')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '아직 모르는 것' }))
    expect(screen.getByRole('region', { name: '현재 탐색 문맥' })).toHaveTextContent('현재 분석')
    expect(screen.getByRole('button', { name: '아직 모르는 것' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByText('후속 공시는 아직 확인되지 않았습니다.')).toBeInTheDocument()
    expect(screen.queryByText('현재 AIRA 해석')).not.toBeInTheDocument()
  })
  it('distinguishes Assess event loading, not-found and request failure', async () => {
    window.history.replaceState({ airaCanonicalExplorer12B: {
      step: 'Assess', target: null, perspective: '사건과 분석', category: null, detail: null,
      periodStart: '', periodEnd: '', receipt: '', predicate: 'CLOSE_PRICE', eventId: 'EV-ERR',
      comparison: null, assessmentId: null, evidenceId: null,
    } }, '', '/explore')
    let rejectEvent
    vi.stubGlobal('fetch', vi.fn(() => new Promise((resolve, reject) => { rejectEvent = reject })))
    render(<CanonicalExplorer />)
    expect(screen.getByText('사건 사실 확인 중…')).toBeInTheDocument()
    rejectEvent({ status: 500 })
    expect(await screen.findByRole('alert')).toHaveTextContent('사건 상세를 불러오지 못했습니다.')
    expect(screen.getByRole('button', { name: 'AIRA 해석' })).toBeDisabled()
  })

  it('keeps a public not-found Assess event distinct from a request failure', async () => {
    window.history.replaceState({ airaCanonicalExplorer12B: {
      step: 'Assess', target: null, perspective: '사건과 분석', category: null, detail: null,
      periodStart: '', periodEnd: '', receipt: '', predicate: 'CLOSE_PRICE', eventId: 'EV-404',
      comparison: null, assessmentId: null, evidenceId: null,
    } }, '', '/explore')
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 404, json: async () => ({}) })))
    render(<CanonicalExplorer />)
    expect(await screen.findByText('이 사건은 현재 공개 상세로 제공되지 않습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

})
