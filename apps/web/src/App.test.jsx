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
const interest = { entityId: company.companyId, entityType: 'COMPANY', canonicalName: '삼성전자', countryCode: 'KR', interestLevel: null, alertEnabled: true }
const eventExperience = { eventId: 'event-1', eventType: 'EARNINGS', title: '삼성전자가 2025 회계연도 연간 재무결과를 공식 공시했습니다.', occurredAt: '2025-12-31T00:00:00Z', status: 'CANDIDATE', assessment: { importance: 'MEDIUM', summary: '공식 연간 연결재무제표 공시는 해당 회계연도의 재무 결과를 확인하는 기준점입니다.', confidence: 'MEDIUM', uncertainty: '이 공시만으로 향후 실적이나 시장 영향을 판단할 수 없으며, 전기 비교와 후속 공시를 함께 확인해야 합니다.', timeHorizon: 'UNSPECIFIED', method: 'RULE' }, evidence: { sourceName: 'OpenDART', externalId: '20260310002820', originalUrl: 'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260310002820', title: 'OpenDART annual CFS filing' } }

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
    if (path.includes('/financial-periods')) return json({ companyId: company.companyId, periods: [period] })
    if (path.includes('/financial-facts')) return json({ companyId: company.companyId, facts })
    if (path.includes('/events')) return json({ companyId: company.companyId, events: [eventExperience] })
    if (key === 'GET /api/me/interests') return json(state.interests)
    if (key === 'POST /api/auth/signup') return json({ user: { id: 'new-user', nickname: 'ReturnUser' } }, 201)
    if (key === 'POST /api/auth/login') { state.user = { userId: 'user-1', nickname: 'ReturnUser' }; return json({ user: { id: 'user-1', nickname: 'ReturnUser' } }) }
    if (key === 'POST /api/auth/logout') { state.user = null; return json(null, 204) }
    if (method === 'POST' && path.startsWith('/api/me/interests/')) { state.interests = [interest]; return json(interest, 201) }
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
afterEach(() => { vi.restoreAllMocks(); document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/' })

describe('authenticated interest and return experience', () => {
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

  it('separates an official event, rule assessment, uncertainty, and source evidence', async () => {
    const backend = server()
    global.fetch = backend.fetch
    const user = userEvent.setup()
    render(<App />)
    await selectSamsung(user)
    expect(await screen.findByRole('heading', { name: eventExperience.title })).toBeInTheDocument()
    expect(screen.getByText(eventExperience.assessment.summary)).toBeInTheDocument()
    expect(screen.getByText(eventExperience.assessment.uncertainty)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /근거 원문 확인/ })).toHaveAttribute('href', eventExperience.evidence.originalUrl)
    expect(screen.queryByText(/매수|매도|추천/)).not.toBeInTheDocument()
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
    expect(await screen.findByText('아직 저장한 관심회사가 없습니다. 아래에서 기업을 선택해 저장할 수 있습니다.')).toBeInTheDocument()
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
    await user.click(within(section).getByRole('button', { name: /삼성전자.*재무정보 다시 보기/ }))
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
})
