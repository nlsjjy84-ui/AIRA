import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, within } from '@testing-library/react'
import ClassPage from './ClassPage.jsx'

const ok = body => ({ ok: true, status: 200, json: async () => body })
const user = { nickname: '강사' }

afterEach(() => { vi.restoreAllMocks(); window.history.replaceState({}, '', '/') })

describe('study class page', () => {
  it('explains the class idea and offers the demo when logged out', () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    render(<ClassPage user={null} onLogin={() => {}} onDemo={() => {}} />)
    expect(screen.getByRole('heading', { name: '공부 반' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '데모로 예시 반 보기' })).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('lists my classes with member and assignment counts', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ok({ owned: [{ id: 'c1', name: '주식 첫걸음 3기', inviteCode: 'ABCD2345', sample: true, members: 3, assignments: 1 }], joined: [] })))
    render(<ClassPage user={user} onLogin={() => {}} onDemo={() => {}} />)
    expect(await screen.findByText('주식 첫걸음 3기')).toBeInTheDocument()
    expect(screen.getByText('수강생 3명 · 과제 1개 · 예시')).toBeInTheDocument()
    const make = screen.getByRole('button', { name: '반 만들기' })
    expect(make).toBeEnabled()
    fireEvent.click(make)
    expect(screen.getByLabelText('새 반 이름')).toHaveValue('주식 첫걸음 1기')
    expect(screen.getByText(/한 번 더 누르세요/)).toBeInTheDocument()
  })

  it('shows the teacher numbers first, then every student progress', async () => {
    window.history.replaceState({}, '', '/classes?id=c1')
    vi.stubGlobal('fetch', vi.fn(async () => ok({ id: 'c1', name: '주식 첫걸음 3기', role: 'OWNER', sample: true, courseSteps: 10, inviteCode: 'ABCD2345',
      assignments: [{ id: 'a1', companyId: 'x', companyName: '삼성전자', title: '삼성전자 회사 읽기 코스 끝내기', dueDate: '2026-10-20' }],
      members: [{ name: '예시 수강생 A', done: [10] }, { name: '예시 수강생 B', done: [4] }, { name: '예시 수강생 C', done: [1] }] })))
    render(<ClassPage user={user} onLogin={() => {}} onDemo={() => {}} />)
    const kpis = await screen.findByLabelText('반 숫자')
    expect(within(kpis).getByText('3명')).toBeInTheDocument()
    expect(within(kpis).getByText('50%')).toBeInTheDocument()
    expect(within(kpis).getByText('1명')).toBeInTheDocument()
    expect(screen.getByText('ABCD2345')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: '10단계 중 4단계' })).toBeInTheDocument()
    expect(screen.getByText(/로그인할 수 없는 예시 데이터/)).toBeInTheDocument()
  })

  it('shows a student only their own assignments and a link into the course', async () => {
    window.history.replaceState({}, '', '/classes?id=c1')
    vi.stubGlobal('fetch', vi.fn(async () => ok({ id: 'c1', name: '주식 첫걸음 3기', role: 'MEMBER', sample: false, courseSteps: 10,
      assignments: [{ id: 'a1', companyId: 'abc', companyName: '삼성전자', title: '삼성전자 회사 읽기 코스', dueDate: null }], myDone: [3] })))
    render(<ClassPage user={{ nickname: '김수강' }} onLogin={() => {}} onDemo={() => {}} />)
    expect(await screen.findByRole('link', { name: '이어서 하기' })).toHaveAttribute('href', '/stock/abc?v=ask.course')
    expect(screen.queryByText('진도표')).toBeNull()
    expect(screen.getByRole('img', { name: '10단계 중 3단계' })).toBeInTheDocument()
  })
})

describe('study class extras', () => {
  it('lets the teacher open one student notes from the progress table', async () => {
    window.history.replaceState({}, '', '/classes?id=c1')
    const { fireEvent } = await import('@testing-library/react')
    vi.stubGlobal('fetch', vi.fn(async url => String(url).includes('/notes?')
      ? ok({ member: '김수강', companyName: '삼성전자', notes: [{ title: '회사 읽기 코스 1. 어떤 회사인가', body: '반도체와 스마트폰', createdAt: '2026-10-13T01:00:00Z' }] })
      : ok({ id: 'c1', name: '1기', role: 'OWNER', sample: false, courseSteps: 10, inviteCode: 'ABCD2345',
        assignments: [{ id: 'a1', companyId: 'x', companyName: '삼성전자', title: 't', dueDate: null, isNew: true }], members: [{ id: 'u1', name: '김수강', done: [1] }] })))
    render(<ClassPage user={user} onLogin={() => {}} onDemo={() => {}} />)
    fireEvent.click(await screen.findByRole('button', { name: '김수강의 삼성전자 노트 보기' }))
    expect(await screen.findByText('반도체와 스마트폰')).toBeInTheDocument()
  })

  it('marks new assignments for students', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ok({ owned: [], joined: [{ id: 'c2', name: '스터디', assignments: 2, newAssignments: 1 }] })))
    render(<ClassPage user={user} onLogin={() => {}} onDemo={() => {}} />)
    expect(await screen.findByText('새 과제 1')).toBeInTheDocument()
    expect(screen.getByText(/반장이 이 반 과제 회사에 대해 내가 남긴 코스 노트/)).toBeInTheDocument()
  })
})
