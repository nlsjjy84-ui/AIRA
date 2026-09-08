import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.jsx'

const company = { companyId: '5eafc0b5-c163-4cea-8dbd-131265004e95', canonicalName: '삼성전자', countryCode: 'KR' }
const relatedCompanies = [
  { companyId: '00000000-0000-0000-0000-000000000001', companyName: '관련회사 A' },
  { companyId: '00000000-0000-0000-0000-000000000002', companyName: '관련회사 B' },
]
const period = { periodStart: '2025-01-01', periodEnd: '2025-12-31', predicates: ['REVENUE', 'OPERATING_INCOME'] }
const facts = [
  { predicate: 'REVENUE', value: 333605938000000, currency: 'KRW', evidenceId: 'evidence-1', sourceName: 'OpenDART', evidenceExternalId: '20260310002820', evidenceOriginalUrl: 'https://dart.fss.or.kr/report/viewer.do?rcept_no=20260310002820' },
  { predicate: 'OPERATING_INCOME', value: 43601051000000, currency: 'KRW', evidenceId: 'evidence-1', sourceName: 'OpenDART', evidenceExternalId: '20260310002820', evidenceOriginalUrl: 'https://dart.fss.or.kr/report/viewer.do?rcept_no=20260310002820' },
]
const interest = { entityId: company.companyId, entityType: 'COMPANY', canonicalName: '삼성전자', countryCode: 'KR', interestLevel: null, alertEnabled: true }
const eventExperience = { eventId: 'event-1', eventType: 'EARNINGS', title: '삼성전자가 2025 회계연도 연간 재무결과를 공식 공시했습니다.', occurredAt: '2025-12-31T00:00:00Z', status: 'CONFIRMED', assessment: { importance: 'MEDIUM', summary: '공식 연간 연결재무제표 공시는 해당 회계연도의 재무 결과를 확인하는 기준점입니다.', confidence: 'MEDIUM', uncertainty: '이 공시만으로 향후 실적이나 시장 영향을 판단할 수 없으며, 전기 비교와 후속 공시를 함께 확인해야 합니다.', timeHorizon: 'UNSPECIFIED', method: 'RULE' }, evidence: { sourceName: 'OpenDART', externalId: '20260310002820', originalUrl: 'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260310002820', title: 'OpenDART annual CFS filing' } }
const exploreEvent = { eventId: eventExperience.eventId, companyId: company.companyId,
  companyName: company.canonicalName, eventType: eventExperience.eventType,
  title: eventExperience.title, occurredAt: eventExperience.occurredAt }
const eventDetail = { eventId: exploreEvent.eventId, eventType: exploreEvent.eventType,
  title: exploreEvent.title, occurredAt: exploreEvent.occurredAt,
  companies: [{ companyId: company.companyId, companyName: company.canonicalName }],
  eventEvidence: [
    { evidenceId: 'event-evidence-1', sourceName: 'Official Source', externalId: 'EVENT-1', title: 'Event official evidence', originalUrl: 'https://official.example/event-1', publishedAt: '2025-12-31T01:00:00Z' },
    { evidenceId: 'event-evidence-2', sourceName: 'Official Source', externalId: 'EVENT-2', title: 'Second event evidence', originalUrl: 'https://official.example/event-2', publishedAt: '2025-12-31T02:00:00Z' },
  ],
  assessment: { assessmentId: 'assessment-current', summary: 'Current AIRA context', uncertainty: 'Current uncertainty',
    confidence: 'MEDIUM', importance: 'MEDIUM', timeHorizon: 'UNSPECIFIED', method: 'RULE', analysisVersion: 'v1',
    evidence: [{ evidenceId: 'assessment-evidence-1', sourceName: 'Assessment Source', externalId: 'ASSESS-1', title: 'Assessment-used evidence', originalUrl: 'https://official.example/assessment-1', publishedAt: null }] } }
const officialEvidence = { evidenceId: 'event-evidence-1', evidenceType: 'DISCLOSURE', externalId: 'EVENT-1',
  title: 'Stored official document', originalUrl: 'https://official.example/stored-document',
  publishedAt: '2025-12-31T01:00:00Z', collectedAt: '2026-01-01T02:00:00Z', revision: 2,
  locator: 'section-1', excerpt: 'Stored evidence excerpt',
  source: { sourceId: 'source-1', sourceName: 'Official Registry', sourceType: 'REGULATORY_FILING', canonicalDomain: 'official.example' } }

const briefingItem = { displayOrder: 1, companies: relatedCompanies,
  eventId: eventExperience.eventId, eventType: eventExperience.eventType, eventTitle: eventExperience.title,
  occurredAt: eventExperience.occurredAt, assessmentId: 'assessment-1', analysisVersion: 'rule-v1',
  summary: eventExperience.assessment.summary, uncertainty: eventExperience.assessment.uncertainty,
  importance: 'MEDIUM', confidence: 'MEDIUM', completedAt: '2026-08-22T01:00:00Z',
  evidence: [{ evidenceId: eventExperience.evidence.evidenceId,
    externalId: eventExperience.evidence.externalId, originalUrl: eventExperience.evidence.originalUrl,
    sourceName: 'OpenDART' }] }
const alertItem = { alertId: 'alert-1', companies: relatedCompanies,
  eventId: eventExperience.eventId, eventTitle: eventExperience.title, eventType: eventExperience.eventType,
  occurredAt: eventExperience.occurredAt, assessmentId: 'assessment-1', summary: eventExperience.assessment.summary,
  uncertainty: eventExperience.assessment.uncertainty, sourceName: 'OpenDART',
  evidenceExternalId: eventExperience.evidence.externalId, evidenceOriginalUrl: eventExperience.evidence.originalUrl,
  createdAt: '2026-08-23T03:30:00Z', sentAt: '2026-08-24T04:45:00Z' }

function json(body, status = 200) {
  return Promise.resolve({ ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) })
}

function server({ user = null, interests = [], overrides = {} } = {}) {
  const state = { user, interests: [...interests] }
  const fetch = vi.fn((input, options = {}) => {
    const path = String(input)
    const method = options.method ?? 'GET'
    const key = `${method} ${path}`
    if (overrides[key]) return overrides[key](state, options)
    if (key === 'GET /api/me') return state.user ? json(state.user) : json({ code: 'UNAUTHORIZED' }, 401)
    if (key === 'GET /api/companies') return json({ companies: [company] })
    if (key === 'GET /api/events') return json({ events: [exploreEvent] })
    if (key === `GET /api/events/${exploreEvent.eventId}`) return json(eventDetail)
    if (path.startsWith('/api/evidence/')) return json({ ...officialEvidence, evidenceId: path.split('/').at(-1) })
    if (path.includes('/financial-periods')) return json({ companyId: company.companyId, periods: [period] })
    if (path.includes('/financial-facts')) return json({ companyId: company.companyId, facts })
    if (path.includes('/events')) return json({ companyId: company.companyId, events: [eventExperience] })
    if (key === 'GET /api/me/interests') return json(state.interests)
    if (key === 'POST /api/me/briefings/current') return json({ briefingId: state.interests.length ? 'briefing-1' : null,
      title: '내 브리핑', status: state.interests.length ? 'READY' : 'EMPTY',
      briefingType: 'ON_DEMAND', periodStart: '2026-08-22T00:00:00Z', periodEnd: '2026-08-23T00:00:00Z',
      generatedAt: '2026-08-23T00:00:01Z', emptyReason: state.interests.length ? null : 'NO_INTERESTS',
      items: state.interests.length ? [briefingItem] : [] })
    if (key === 'POST /api/me/alerts/reconcile') return json({ alerts: [] })
    if (key === 'POST /api/auth/signup') return json({ user: { id: 'new-user', nickname: 'ReturnUser' } }, 201)
    if (key === 'POST /api/auth/login') { state.user = { userId: 'user-1', nickname: 'ReturnUser' }; return json({ user: { id: 'user-1', nickname: 'ReturnUser' } }) }
    if (key === 'POST /api/auth/logout') { state.user = null; return json(null, 204) }
    if (method === 'POST' && path.endsWith('/alert')) { state.interests = state.interests.map(item => ({ ...item, alertEnabled: true })); return json(state.interests[0]) }
    if (method === 'DELETE' && path.endsWith('/alert')) { state.interests = state.interests.map(item => ({ ...item, alertEnabled: false })); return json(state.interests[0]) }
    if (method === 'POST' && path.startsWith('/api/me/interests/')) { state.interests = [{ ...interest, alertEnabled: false }]; return json(state.interests[0], 201) }
    if (method === 'DELETE' && path.startsWith('/api/me/interests/')) { state.interests = []; return json(null, 204) }
    throw new Error(`Unexpected request: ${key}`)
  })
  return { state, fetch }
}

async function selectSamsung(user) {
  await user.click(await screen.findByRole('button', { name: /삼성전자.*대한민국/ }))
  return screen.findByLabelText('정확한 보고 기간')
}

beforeEach(() => { document.cookie = 'XSRF-TOKEN=test-csrf; path=/' })
afterEach(() => { vi.restoreAllMocks(); document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'; window.history.replaceState({}, '', '/') })

describe('authenticated interest and return experience', () => {
  it('opens event detail from explore and separates factual event, event evidence, assessment, and assessment evidence', async () => {
    const backend = server()
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    const explore = (await screen.findByRole('heading', { name: '최근 확인된 Event' })).closest('section')
    expect(await within(explore).findByRole('heading', { name: exploreEvent.title })).toBeInTheDocument()
    expect(within(explore).getByText(company.canonicalName)).toBeInTheDocument()
    expect(within(explore).getByText(/EARNINGS.*2025-12-31/)).toBeInTheDocument()
    expect(explore).not.toHaveTextContent(eventExperience.assessment.summary)
    await user.click(within(explore).getByRole('button', { name: 'Event 상세 보기' }))
    const detail = (await screen.findByRole('heading', { name: 'Event 상세' })).closest('section')
    expect(await within(detail).findByRole('heading', { name: exploreEvent.title })).toBeInTheDocument()
    expect(within(detail).getByText(company.canonicalName)).toBeInTheDocument()
    const official = within(detail).getByRole('heading', { name: '공식 근거' }).closest('section')
    expect(within(official).getByText('Event official evidence')).toBeInTheDocument()
    expect(within(official).getByText('Second event evidence')).toBeInTheDocument()
    const assessment = within(detail).getByRole('heading', { name: 'AIRA 해석' }).closest('section')
    expect(within(assessment).getByText('Current AIRA context')).toBeInTheDocument()
    const assessmentEvidence = within(detail).getByRole('heading', { name: '해석 근거' }).closest('section')
    expect(within(assessmentEvidence).getByText('Assessment-used evidence')).toBeInTheDocument()
  })

  it('shows an event detail data-boundary state when no current assessment exists', async () => {
    const backend = server({ overrides: {
      [`GET /api/events/${exploreEvent.eventId}`]: () => json({ ...eventDetail, assessment: null }),
    } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    const explore = (await screen.findByRole('heading', { name: '최근 확인된 Event' })).closest('section')
    await user.click(within(explore).getByRole('button', { name: 'Event 상세 보기' }))
    expect(await screen.findByText('현재 표시할 AIRA 해석이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByText(/중요하지 않음|분석할 가치가 없음/)).not.toBeInTheDocument()
  })

  it('distinguishes event detail loading, request failure, and public not-found', async () => {
    let rejectDetail
    const backend = server({ overrides: {
      [`GET /api/events/${exploreEvent.eventId}`]: () => new Promise((resolve, reject) => { rejectDetail = reject }),
    } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    const explore = (await screen.findByRole('heading', { name: '최근 확인된 Event' })).closest('section')
    await user.click(within(explore).getByRole('button', { name: 'Event 상세 보기' }))
    expect(screen.getByText('Event 상세를 불러오는 중입니다.')).toBeInTheDocument()
    rejectDetail({ status: 500 })
    const detail = screen.getByRole('heading', { name: 'Event 상세' }).closest('section')
    expect(await within(detail).findByRole('alert')).toBeInTheDocument()

    backend.fetch.mockImplementationOnce(() => json({}, 404))
    await user.click(within(detail).getByRole('button', { name: '다시 시도' }))
    expect(await within(detail).findByText('이 Event는 현재 공개 상세로 제공되지 않습니다.')).toBeInTheDocument()
  })

  it('opens official evidence from event evidence and shows stored source document and timestamp semantics', async () => {
    const backend = server()
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    const explore = (await screen.findByRole('heading', { name: '최근 확인된 Event' })).closest('section')
    await user.click(within(explore).getByRole('button', { name: 'Event 상세 보기' }))
    const eventEvidence = (await screen.findByRole('heading', { name: '공식 근거' })).closest('section')
    await user.click(within(eventEvidence).getAllByRole('button', { name: '공식 자료 상세' })[0])
    const detail = (await screen.findByRole('heading', { name: '공식 자료' })).closest('section')
    expect(await within(detail).findByRole('heading', { name: 'Stored official document' })).toBeInTheDocument()
    expect(within(detail).getByText('Official Registry')).toBeInTheDocument()
    expect(within(detail).getByText(/공식 자료 발행/)).toBeInTheDocument()
    expect(within(detail).getByText(/AIRA 자료 수집/)).toBeInTheDocument()
    expect(within(detail).getByRole('link', { name: /공식 원문 열기/ })).toHaveAttribute('href', officialEvidence.originalUrl)
  })

  it('opens the same official evidence view from assessment evidence and company fact identity', async () => {
    const backend = server()
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    const explore = (await screen.findByRole('heading', { name: '최근 확인된 Event' })).closest('section')
    await user.click(within(explore).getByRole('button', { name: 'Event 상세 보기' }))
    const assessmentEvidence = (await screen.findByRole('heading', { name: '해석 근거' })).closest('section')
    await user.click(within(assessmentEvidence).getByRole('button', { name: '공식 자료 상세' }))
    expect(await screen.findByRole('heading', { name: 'Stored official document' })).toBeInTheDocument()
    expect(backend.fetch).toHaveBeenCalledWith('/api/evidence/assessment-evidence-1', expect.anything())

    await selectSamsung(user)
    const factEvidence = screen.getByText(/같은 공시에 포함된 재무 항목/).closest('aside')
    await user.click(within(factEvidence).getByRole('button', { name: '공식 자료 상세' }))
    expect(backend.fetch).toHaveBeenCalledWith('/api/evidence/evidence-1', expect.anything())
  })

  it('shows an exact official evidence unavailable-link state without claiming deletion', async () => {
    const backend = server({ overrides: {
      'GET /api/evidence/event-evidence-1': () => json({ ...officialEvidence, originalUrl: null }),
    } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    const explore = (await screen.findByRole('heading', { name: '최근 확인된 Event' })).closest('section')
    await user.click(within(explore).getByRole('button', { name: 'Event 상세 보기' }))
    const eventEvidence = (await screen.findByRole('heading', { name: '공식 근거' })).closest('section')
    await user.click(within(eventEvidence).getAllByRole('button', { name: '공식 자료 상세' })[0])
    expect(await screen.findByText('저장된 공식 원문 링크가 없습니다.')).toBeInTheDocument()
    expect(screen.queryByText(/삭제되었습니다|존재하지 않습니다|링크가 깨졌습니다/)).not.toBeInTheDocument()
  })

  it('distinguishes official evidence loading request failure and public not-found', async () => {
    let rejectEvidence
    const backend = server({ overrides: {
      'GET /api/evidence/event-evidence-1': () => new Promise((resolve, reject) => { rejectEvidence = reject }),
    } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    const explore = (await screen.findByRole('heading', { name: '최근 확인된 Event' })).closest('section')
    await user.click(within(explore).getByRole('button', { name: 'Event 상세 보기' }))
    const eventEvidence = (await screen.findByRole('heading', { name: '공식 근거' })).closest('section')
    await user.click(within(eventEvidence).getAllByRole('button', { name: '공식 자료 상세' })[0])
    expect(screen.getByText('공식 자료를 불러오는 중입니다.')).toBeInTheDocument()
    rejectEvidence({ status: 500 })
    const detail = screen.getByRole('heading', { name: '공식 자료' }).closest('section')
    expect(await within(detail).findByRole('alert')).toBeInTheDocument()
    backend.fetch.mockImplementationOnce(() => json({}, 404))
    await user.click(within(detail).getByRole('button', { name: '다시 시도' }))
    expect(await within(detail).findByText('이 Evidence를 현재 public AIRA 경로에서 표시할 수 없습니다.')).toBeInTheDocument()
  })

  it('distinguishes an empty explore event feed without claiming reality has no events', async () => {
    const backend = server({ overrides: { 'GET /api/events': () => json({ events: [] }) } })
    global.fetch = backend.fetch
    render(<App />)
    expect(await screen.findByText('현재 AIRA에서 확인해 보여줄 수 있는 Event가 없습니다.')).toBeInTheDocument()
    expect(screen.queryByText(/시장에 사건이 없음|아무 변화도 없음|중요한 뉴스가 없음|투자 기회가 없음/)).not.toBeInTheDocument()
  })

  it('distinguishes explore event loading and request failure', async () => {
    let rejectEvents
    const backend = server({ overrides: {
      'GET /api/events': () => new Promise((resolve, reject) => { rejectEvents = reject }),
    } })
    global.fetch = backend.fetch
    render(<App />)
    expect(screen.getByText('확인된 Event를 불러오는 중입니다.')).toBeInTheDocument()
    rejectEvents({ status: 500 })
    const explore = screen.getByRole('heading', { name: '최근 확인된 Event' }).closest('section')
    expect(await within(explore).findByRole('alert')).toBeInTheDocument()
  })

  it('preserves anonymous navigation and the public financial journey', async () => {
    const backend = server()
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument()
    await selectSamsung(user)
    expect(await screen.findByText('333,605,938,000,000')).toBeInTheDocument()
    expect(screen.getByText('43,601,051,000,000')).toBeInTheDocument()
    expect(screen.getAllByText('OpenDART')).toHaveLength(2)
    expect(screen.getByRole('button', { name: '로그인하고 관심회사에 저장' })).toBeInTheDocument()
  })

  it('renders the semantic compatibility terminal company assessment normally', async () => {
    const backend = server()
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    await selectSamsung(user)
    const events = (await screen.findByRole('heading', { name: '관련 사건과 확인할 의미' })).closest('section')
    expect(within(events).getByRole('heading', { name: eventExperience.title })).toBeInTheDocument()
    expect(within(events).getByText(eventExperience.assessment.summary)).toBeInTheDocument()
    expect(within(events).getByText(eventExperience.assessment.uncertainty)).toBeInTheDocument()
    expect(within(events).getByRole('link', { name: /근거 원문 확인/ })).toHaveAttribute('href', eventExperience.evidence.originalUrl)
    expect(within(events).queryByText(/매수|매도|추천/)).not.toBeInTheDocument()
  })

  it('renders the semantic compatibility company event without a current assessment safely', async () => {
    const backend = server({ overrides: {
      [`GET /api/companies/${company.companyId}/events`]: () => json({
        companyId: company.companyId, events: [{ ...eventExperience, assessment: null }],
      }),
    } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    await selectSamsung(user)
    expect(await screen.findByText('현재 표시할 AIRA 해석이 없습니다.')).toBeInTheDocument()
  })

  it('signs up without creating a session and leads naturally to login', async () => {
    const backend = server()
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: '회원가입' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('닉네임'), 'ReturnUser')
    await user.type(within(dialog).getByLabelText('비밀번호'), 'long-secure-password')
    await user.click(within(dialog).getByRole('button', { name: '회원가입' }))
    expect(await within(dialog).findByText(/회원가입이 완료됐습니다/)).toBeInTheDocument()
    expect(within(dialog).getByRole('heading', { name: '로그인' })).toBeInTheDocument()
    const call = backend.fetch.mock.calls.find(([path, options]) => path === '/api/auth/signup' && options.method === 'POST')
    expect(call[1].headers['X-XSRF-TOKEN']).toBe('test-csrf')
  })

  it('logs in, confirms /api/me, and shows an empty interest state', async () => {
    const backend = server()
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: '로그인' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('닉네임'), 'ReturnUser')
    await user.type(within(dialog).getByLabelText('비밀번호'), 'long-secure-password')
    await user.click(within(dialog).getByRole('button', { name: '로그인' }))
    expect(await screen.findByText(/아직 저장한 관심회사가 없습니다/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '회사 탐색하기' })).toHaveAttribute('href', '#companies')
    expect(screen.getByText('ReturnUser')).toBeInTheDocument()
    expect(backend.fetch.mock.calls.filter(([path]) => path === '/api/me')).toHaveLength(2)
  })

  it('restores an existing session and re-enters financials from saved interests', async () => {
    const backend = server({ user: { userId: 'user-1', nickname: 'ReturnUser' }, interests: [interest] })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    expect(await screen.findByText('ReturnUser')).toBeInTheDocument()
    const section = screen.getByRole('heading', { name: '내 관심회사' }).closest('section')
    await user.click(await within(section).findByRole('button', { name: /삼성전자.*재무정보 다시 보기/ }))
    expect(await screen.findByText('333,605,938,000,000')).toBeInTheDocument()
  })

  it('saves and removes Samsung without duplicate posts or UUID display', async () => {
    const backend = server({ user: { userId: 'user-1', nickname: 'ReturnUser' } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    const { container } = render(<App />)
    await selectSamsung(user)
    await user.click(screen.getByRole('button', { name: '관심회사에 저장' }))
    expect(await screen.findByRole('button', { name: '관심회사에서 삭제' })).toBeInTheDocument()
    expect(container).not.toHaveTextContent(company.companyId)
    expect(backend.fetch.mock.calls.filter(([path, options]) => path.includes('/api/me/interests/') && options.method === 'POST')).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: '관심회사에서 삭제' }))
    expect(await screen.findByRole('button', { name: '관심회사에 저장' })).toBeInTheDocument()
  })

  it('logs out while leaving public company browsing available', async () => {
    const backend = server({ user: { userId: 'user-1', nickname: 'ReturnUser' } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: '로그아웃' }))
    expect(await screen.findByText(/로그아웃되었습니다/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /삼성전자.*대한민국/ })).toBeInTheDocument()
  })

  it('turns an expired interest session into a safe anonymous state', async () => {
    const backend = server({ user: { userId: 'user-1', nickname: 'ReturnUser' }, overrides: {
      'GET /api/me/interests': () => json({ code: 'UNAUTHORIZED' }, 401),
    } })
    global.fetch = backend.fetch
    render(<App />)
    expect(await screen.findByText(/세션이 만료되었습니다/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /삼성전자.*대한민국/ })).toBeInTheDocument()
  })

  it.each([
    ['NICKNAME_ALREADY_EXISTS', '사용할 수 없는 닉네임입니다.'],
    ['AUTHENTICATION_FAILED', '닉네임 또는 비밀번호를 확인해 주세요.'],
  ])('maps %s without exposing account details', async (code, copy) => {
    const endpoint = code === 'NICKNAME_ALREADY_EXISTS' ? 'POST /api/auth/signup' : 'POST /api/auth/login'
    const backend = server({ overrides: { [endpoint]: () => json({ code }, code === 'NICKNAME_ALREADY_EXISTS' ? 409 : 401) } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: code === 'NICKNAME_ALREADY_EXISTS' ? '회원가입' : '로그인' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('닉네임'), 'ReturnUser')
    await user.type(within(dialog).getByLabelText('비밀번호'), 'long-secure-password')
    await user.click(within(dialog).getByRole('button', { name: code === 'NICKNAME_ALREADY_EXISTS' ? '회원가입' : '로그인' }))
    expect(await within(dialog).findByText(copy)).toBeInTheDocument()
  })

  it('shows interest failure with retry and succeeds without duplicating UI state', async () => {
    let attempts = 0
    const backend = server({ user: { userId: 'user-1', nickname: 'ReturnUser' }, overrides: {
      [`POST /api/me/interests/${company.companyId}`]: (state) => {
        attempts += 1
        if (attempts === 1) return json({ code: 'FAILURE' }, 500)
        state.interests = [interest]
        return json(interest, 201)
      },
    } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    await selectSamsung(user)
    await user.click(screen.getByRole('button', { name: '관심회사에 저장' }))
    const alert = await screen.findByRole('alert')
    await user.click(within(alert).getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('button', { name: '관심회사에서 삭제' })).toBeInTheDocument()
    expect(attempts).toBe(2)
  })

  it('announces session and interest loading states', async () => {
    let releaseSession
    const backend = server({ overrides: {
      'GET /api/me': () => new Promise(resolve => { releaseSession = () => resolve({ ok: false, status: 401, json: () => Promise.resolve({}) }) }),
    } })
    global.fetch = backend.fetch
    render(<App />)
    expect(screen.getByText('세션 확인 중…')).toBeInTheDocument()
    releaseSession()
    await waitFor(() => expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument())
  })

  it('enters password recovery from login and preserves a generic request response', async () => {
    const backend = server({ overrides: {
      'POST /api/auth/password-reset/requests': () => json(null, 202),
    } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    await user.click(await screen.findByRole('button', { name: '로그인' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '비밀번호를 잊으셨나요?' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('복구 이메일'), 'person@example.com')
    await user.click(within(dialog).getByRole('button', { name: '재설정 안내 요청' }))
    expect(await within(dialog).findByText('입력한 주소가 등록되어 있다면 비밀번호 재설정 안내를 보냈습니다.')).toBeInTheDocument()
    const call = backend.fetch.mock.calls.find(([path]) => path === '/api/auth/password-reset/requests')
    expect(JSON.parse(call[1].body)).toEqual({ email: 'person@example.com' })
  })

  it('handles an invalid or expired reset link without exposing the token', async () => {
    window.history.replaceState({}, '', '/password-reset/confirm?token=sensitive-reset-token')
    const backend = server({ overrides: {
      'POST /api/auth/password-reset/confirm': () => json({ code: 'INVALID_PASSWORD_RESET_TOKEN' }, 400),
    } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    const dialog = screen.getByRole('dialog')
    expect(dialog).not.toHaveTextContent('sensitive-reset-token')
    await user.type(within(dialog).getByLabelText('새 비밀번호'), 'changed-secure-password')
    await user.type(within(dialog).getByLabelText('새 비밀번호 확인'), 'changed-secure-password')
    await user.click(within(dialog).getByRole('button', { name: '비밀번호 변경' }))
    expect(await within(dialog).findByText('재설정 링크가 유효하지 않거나 만료되었습니다. 새 링크를 요청해 주세요.')).toBeInTheDocument()
  })

  it('completes password reset, removes the token URL, and returns toward login', async () => {
    window.history.replaceState({}, '', '/password-reset/confirm?token=one-time-token')
    const backend = server({ overrides: {
      'POST /api/auth/password-reset/confirm': () => json(null, 204),
    } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('새 비밀번호'), 'changed-secure-password')
    await user.type(within(dialog).getByLabelText('새 비밀번호 확인'), 'changed-secure-password')
    await user.click(within(dialog).getByRole('button', { name: '비밀번호 변경' }))
    expect(await within(dialog).findByText(/기존 로그인 세션을 종료했습니다/)).toBeInTheDocument()
    expect(window.location.pathname).toBe('/')
    expect(window.location.search).toBe('')
    await user.click(within(dialog).getByRole('button', { name: '로그인으로 이동' }))
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
  })

  it('lets an authenticated user request recovery-email verification without displaying the address', async () => {
    const backend = server({ user: { userId: 'user-1', nickname: 'ReturnUser' }, overrides: {
      'POST /api/auth/recovery-email/verifications': () => json(null, 202),
    } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    const section = (await screen.findByRole('heading', { name: '계정 복구 이메일' })).closest('section')
    await user.type(within(section).getByLabelText('복구 이메일'), 'private@example.com')
    await user.click(within(section).getByRole('button', { name: '확인 메일 보내기' }))
    expect(await within(section).findByText(/30분 안에 메일의 링크/)).toBeInTheDocument()
    expect(section).not.toHaveTextContent('private@example.com')
  })

  it('confirms recovery email from the delivered link without rendering its token', async () => {
    window.history.replaceState({}, '', '/recovery-email/confirm?token=private-verification-token')
    const backend = server({ overrides: {
      'POST /api/auth/recovery-email/verifications/confirm': () => json(null, 204),
    } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    const dialog = screen.getByRole('dialog')
    expect(dialog).not.toHaveTextContent('private-verification-token')
    await user.click(within(dialog).getByRole('button', { name: '이메일 확인' }))
    expect(await within(dialog).findByText('복구 이메일 확인 요청을 처리했습니다.')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/')
  })

  it('renders semantic compatibility briefing companies and navigates by event identity', async () => {
    const backend = server({ user: { userId: 'user-1', nickname: 'ReturnUser' }, interests: [interest] })
    global.fetch = backend.fetch
    render(<App />)
    const briefing = (await screen.findByRole('heading', { name: '내 브리핑' })).closest('section')
    expect(await within(briefing).findByText('관련 회사: 관련회사 A · 관련회사 B')).toBeInTheDocument()
    expect(within(briefing).getByText(eventExperience.assessment.summary)).toBeInTheDocument()
    expect(within(briefing).getByText(eventExperience.assessment.uncertainty)).toBeInTheDocument()
    expect(within(briefing).getByText('관심회사로 저장한 회사의 AIRA 분석입니다.')).toBeInTheDocument()
    expect(within(briefing).getByText(/정리 기간/)).toBeInTheDocument()
    expect(within(briefing).getByText(/브리핑 생성/)).toBeInTheDocument()
    expect(within(briefing).getByRole('link', { name: /OpenDART 공식 근거 원문/ })).toHaveAttribute('href', eventExperience.evidence.originalUrl)
    expect(within(briefing).queryByText(/매수|매도|추천|알림/)).not.toBeInTheDocument()
    await userEvent.setup().click(within(briefing).getByRole('button', { name: 'Event 상세 보기' }))
    expect(await screen.findByRole('heading', { name: 'Event 상세' })).toBeInTheDocument()
    expect(backend.fetch).toHaveBeenCalledWith(`/api/events/${eventExperience.eventId}`, expect.anything())
  })

  it('shows a safe briefing empty state without interests', async () => {
    const backend = server({ user: { userId: 'user-1', nickname: 'ReturnUser' } })
    global.fetch = backend.fetch
    render(<App />)
    const briefing = (await screen.findByRole('heading', { name: '내 브리핑' })).closest('section')
    expect(await within(briefing).findByText('아직 관심 회사가 없습니다.')).toBeInTheDocument()
    expect(within(briefing).getByText('계속 확인하고 싶은 회사를 저장하면 이후 새로 정리된 변화를 Briefing에서 모아볼 수 있습니다.')).toBeInTheDocument()
    expect(within(briefing).getByRole('link', { name: '관심회사 살펴보기' })).toHaveAttribute('href', '#companies')
    expect(screen.getByRole('link', { name: '회사와 알림 설정 보기' })).toHaveAttribute('href', '#companies')
  })

  it('describes an empty catch-up window without claiming that no facts exist', async () => {
    const backend = server({ user: { userId: 'user-1', nickname: 'ReturnUser' }, interests: [interest], overrides: {
      'POST /api/me/briefings/current': () => json({ briefingId: null, title: '내 브리핑', status: 'EMPTY',
        briefingType: 'ON_DEMAND', periodStart: '2026-08-22T00:00:00Z', periodEnd: '2026-08-23T00:00:00Z',
        generatedAt: '2026-08-23T00:00:01Z', emptyReason: 'NO_ELIGIBLE_ASSESSMENTS', items: [] }),
    } })
    global.fetch = backend.fetch
    render(<App />)
    expect(await screen.findByText('이 Briefing 기간에 새로 정리된 변화가 없습니다.')).toBeInTheDocument()
    expect(screen.queryByText(/아무 변화가 없습니다|새로운 일이 없습니다|공시가 없습니다/)).not.toBeInTheDocument()
    expect(screen.getByText(/정리 기간/)).toBeInTheDocument()
  })

  it('renders semantic compatibility alert companies and navigates by event identity', async () => {
    const backend = server({ user: { userId: 'user-1', nickname: 'ReturnUser' }, interests: [interest], overrides: {
      'POST /api/me/alerts/reconcile': () => json({ alerts: [alertItem] }),
    } })
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    const alerts = (await screen.findByRole('heading', { name: '관심회사 알림' })).closest('section')
    expect(await within(alerts).findByText('관련 회사: 관련회사 A · 관련회사 B')).toBeInTheDocument()
    expect(await within(alerts).findByText('앱 알림을 켠 관심회사에 새로운 AIRA 분석이 준비되었습니다.')).toBeInTheDocument()
    expect(within(alerts).getByText(/알림 전달/)).toBeInTheDocument()
    expect(within(alerts).queryByText(/알림 생성/)).not.toBeInTheDocument()
    expect(within(alerts).getByRole('link', { name: /OpenDART 공식 근거 원문/ })).toHaveAttribute('href', alertItem.evidenceOriginalUrl)
    await user.click(within(alerts).getByRole('button', { name: 'Event 상세 보기' }))
    expect(await screen.findByRole('heading', { name: 'Event 상세' })).toBeInTheDocument()
    expect(backend.fetch).toHaveBeenCalledWith(`/api/events/${eventExperience.eventId}`, expect.anything())
  })
})
