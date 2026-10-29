import { useCallback, useEffect, useState } from 'react'
import { addAssignment, createClass, deleteClass, getClass, getMemberNotes, joinClass, leaveClass, listClasses } from './api/classApi.js'
import { searchEntities } from './api/canonicalApi.js'

const ERRORS = {
  NAME_REQUIRED: '반 이름을 적어 주세요.', TOO_MANY: '더 만들 수 없습니다(반 5개, 과제 50개까지).', BAD_CODE: '초대 코드는 영문·숫자 6~12자리입니다.',
  NOT_FOUND: '그 초대 코드의 반을 찾지 못했습니다.', OWNER: '내가 만든 반에는 수강생으로 들어갈 수 없습니다.', FULL: '반 정원(300명)이 찼습니다.',
  BAD_DATE: '마감일 형식이 올바르지 않습니다.', COMPANY_REQUIRED: '과제로 낼 회사를 골라 주세요.', DEMO_READ_ONLY: '데모 계정은 읽기 전용이라 만들거나 바꿀 수 없습니다. 예시 반을 둘러봐 주세요.',
}
const say = error => ERRORS[error?.code] ?? '지금은 처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요.'
const courseLink = companyId => `/stock/${companyId}?v=ask.course`

function Bar({ done, total }) {
  const pct = total ? Math.round(done / total * 100) : 0
  return <span className="class-bar" role="img" aria-label={`${total}단계 중 ${done}단계`}><i style={{ width: `${pct}%` }} data-full={done >= total ? 'true' : undefined} /><b>{done}/{total}</b></span>
}

function MemberNotes({ classId, member, assignment, close }) {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  useEffect(() => {
    getMemberNotes(classId, member.id, assignment.id).then(data => setState({ loading: false, data, error: null }))
      .catch(error => setState({ loading: false, data: null, error }))
  }, [classId, member.id, assignment.id])
  return <section className="class-notes" aria-label={`${member.name}의 ${assignment.companyName} 코스 노트`}>
    <div className="class-notes-head"><h3>{member.name} · {assignment.companyName} 코스 노트</h3><button type="button" className="secondary-action" onClick={close}>닫기</button></div>
    {state.loading && <p className="status" role="status">노트를 불러오는 중입니다.</p>}
    {state.error && <p className="stock-empty" role="alert">노트를 불러오지 못했습니다.</p>}
    {state.data && (state.data.notes.length === 0 ? <p className="stock-empty">아직 남긴 코스 노트가 없습니다.</p>
      : <ol>{state.data.notes.map((n, i) => <li key={i}><strong>{n.title}</strong><p>{n.body}</p><small>{n.createdAt.slice(0, 10)}</small></li>)}</ol>)}
  </section>
}

function OwnerBoard({ data, reload }) {
  const [open, setOpen] = useState(null)
  const [query, setQuery] = useState('')
  const [found, setFound] = useState([])
  const [pick, setPick] = useState(null)
  const [due, setDue] = useState('')
  const [state, setState] = useState({ busy: false, message: '' })
  const total = data.courseSteps
  const members = data.members ?? []
  const lastIndex = data.assignments.length - 1
  const latest = lastIndex >= 0 ? members.map(m => m.done[lastIndex] ?? 0) : []
  const finished = latest.filter(n => n >= total).length
  const average = latest.length ? Math.round(latest.reduce((s, n) => s + n, 0) / latest.length / total * 100) : 0

  async function find(event) {
    event.preventDefault()
    if (!query.trim()) return
    try {
      const result = await searchEntities(query.trim())
      setFound((result?.entities ?? []).filter(item => item.entityType === 'COMPANY').slice(0, 6))
    } catch { setFound([]) }
  }
  async function give() {
    if (!pick) return
    setState({ busy: true, message: '' })
    try {
      await addAssignment(data.id, { companyId: pick.entityId, companyName: pick.canonicalName, dueDate: due || null })
      setPick(null); setFound([]); setQuery(''); setDue('')
      setState({ busy: false, message: `${pick.canonicalName} 과제를 냈습니다.` })
      reload()
    } catch (error) { setState({ busy: false, message: say(error) }) }
  }
  async function copy() {
    try { await navigator.clipboard.writeText(data.inviteCode); setState(s => ({ ...s, message: '초대 코드를 복사했습니다.' })) }
    catch { setState(s => ({ ...s, message: `초대 코드: ${data.inviteCode}` })) }
  }

  return <>
    <div className="class-kpis" aria-label="반 숫자">
      <div className="kpi-tile"><span className="kpi-label">수강생</span><strong className="kpi-value">{members.length}명</strong><span className="kpi-sub">초대 코드로 들어온 사람</span></div>
      <div className="kpi-tile"><span className="kpi-label">과제</span><strong className="kpi-value">{data.assignments.length}개</strong><span className="kpi-sub">같은 회사 같이 읽기</span></div>
      <div className="kpi-tile"><span className="kpi-label">최근 과제 평균 진도</span><strong className="kpi-value">{lastIndex >= 0 ? `${average}%` : '—'}</strong><span className="kpi-sub">회사 읽기 코스 {total}단계 기준</span></div>
      <div className="kpi-tile"><span className="kpi-label">최근 과제 다 끝낸 사람</span><strong className="kpi-value">{lastIndex >= 0 ? `${finished}명` : '—'}</strong><span className="kpi-sub">{members.length ? `${members.length}명 중` : '아직 수강생 없음'}</span></div>
    </div>
    <div className="class-invite"><span>초대 코드</span><strong>{data.inviteCode}</strong><button type="button" className="secondary-action" onClick={copy}>복사</button>
      <small>수강생은 AIRA에 로그인한 뒤 「공부 반」에서 이 코드를 넣으면 들어옵니다.</small></div>
    <section className="class-section" aria-labelledby="progress-title">
      <h2 id="progress-title">진도표</h2>
      {data.assignments.length === 0 ? <p className="stock-empty">아직 낸 과제가 없습니다. 아래에서 회사를 골라 첫 과제를 내 보세요.</p>
        : members.length === 0 ? <p className="stock-empty">아직 들어온 수강생이 없습니다. 초대 코드를 수강생에게 알려 주세요.</p>
          : <div className="table-scroll"><table className="stock-table class-table"><thead><tr><th>수강생</th>{data.assignments.map(a => <th key={a.id}>{a.companyName}{a.dueDate && <small> · ~{a.dueDate.slice(5)}</small>}</th>)}</tr></thead>
            <tbody>{members.map((m, i) => <tr key={i}><td>{m.name}</td>{m.done.map((n, j) => <td key={j}><button type="button" className="class-cell" onClick={() => setOpen({ member: m, assignment: data.assignments[j] })}
              aria-label={`${m.name}의 ${data.assignments[j].companyName} 노트 보기`}><Bar done={n} total={total} /></button></td>)}</tr>)}</tbody></table></div>}
      {members.length > 0 && data.assignments.length > 0 && <p className="class-help">진도 막대를 누르면 그 수강생이 남긴 코스 노트를 읽을 수 있습니다.</p>}
      {open && <MemberNotes classId={data.id} member={open.member} assignment={open.assignment} close={() => setOpen(null)} />}
    </section>
    <section className="class-section" aria-labelledby="assign-title">
      <h2 id="assign-title">과제 내기</h2>
      <form className="class-row" onSubmit={find}><label htmlFor="assign-company">회사</label>
        <input id="assign-company" value={query} onChange={e => setQuery(e.target.value)} placeholder="예: 삼성전자" /><button type="submit" className="secondary-action">찾기</button></form>
      {found.length > 0 && <div className="quick-stocks" aria-label="찾은 회사">{found.map(c => <button key={c.entityId} type="button" aria-pressed={pick?.entityId === c.entityId} onClick={() => setPick(c)}>{c.canonicalName}</button>)}</div>}
      <div className="class-row"><label htmlFor="assign-due">마감일(선택)</label><input id="assign-due" type="date" value={due} onChange={e => setDue(e.target.value)} />
        <button type="button" className="primary-action" disabled={!pick || state.busy} onClick={give}>{pick ? `${pick.canonicalName} 과제 내기` : '회사를 먼저 고르세요'}</button></div>
      <p className="class-help">과제는 「회사 읽기 코스」 10단계입니다. 수강생이 단계마다 한 줄 노트를 남기면 진도표에 자동으로 올라옵니다.</p>
    </section>
    {state.message && <p className="class-message" role="status">{state.message}</p>}
  </>
}

function MemberView({ data }) {
  return <section className="class-section" aria-labelledby="my-assign-title">
    <h2 id="my-assign-title">내 과제</h2>
    {data.assignments.length === 0 ? <p className="stock-empty">아직 반장이 낸 과제가 없습니다.</p>
      : <ul className="class-assignments">{data.assignments.map((a, i) => <li key={a.id}>
        <div><strong>{a.title}{a.isNew && <span className="class-new">새 과제</span>}</strong>{a.dueDate && <small>마감 {a.dueDate}</small>}</div>
        <Bar done={data.myDone[i] ?? 0} total={data.courseSteps} />
        <a className="secondary-action" href={courseLink(a.companyId)}>{(data.myDone[i] ?? 0) > 0 ? '이어서 하기' : '시작하기'}</a></li>)}</ul>}
    <p className="class-help">진도와 이 반 과제 회사의 코스 노트는 반장에게만 보이고, 다른 수강생에게는 보이지 않습니다.</p>
  </section>
}

/** 공부 반: 강사·스터디장이 반을 만들고, 같은 회사를 같이 읽는 과제를 내고, 진도를 본다. */
export default function ClassPage({ user, onLogin, onDemo }) {
  const id = typeof window === 'undefined' ? null : new URLSearchParams(window.location.search).get('id')
  const [list, setList] = useState({ loading: true, data: null, error: null })
  const [detail, setDetail] = useState({ loading: Boolean(id), data: null, error: null })
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [message, setMessage] = useState('')

  const loadList = useCallback(() => {
    if (!user) return
    listClasses().then(data => setList({ loading: false, data, error: null })).catch(error => setList({ loading: false, data: null, error }))
  }, [user])
  const loadDetail = useCallback(() => {
    if (!user || !id) return
    getClass(id).then(data => setDetail({ loading: false, data, error: null })).catch(error => setDetail({ loading: false, data: null, error }))
  }, [user, id])
  useEffect(loadList, [loadList])
  useEffect(loadDetail, [loadDetail])

  if (!user) return <section className="stock-page class-page" aria-labelledby="class-title">
    <p className="eyebrow">STUDY CLASS · 강사·스터디용</p>
    <h1 id="class-title">공부 반</h1>
    <div className="class-kpis" aria-label="공부 반으로 할 수 있는 것">
      <div className="kpi-tile"><span className="kpi-label">1</span><strong className="kpi-value small">반 만들기</strong><span className="kpi-sub">초대 코드로 수강생 받기</span></div>
      <div className="kpi-tile"><span className="kpi-label">2</span><strong className="kpi-value small">같은 회사 과제</strong><span className="kpi-sub">회사 읽기 코스 10단계</span></div>
      <div className="kpi-tile"><span className="kpi-label">3</span><strong className="kpi-value small">진도표</strong><span className="kpi-sub">누가 어디까지 했는지 한 화면</span></div>
    </div>
    <p className="class-help">주식 강의·스터디에서 수강생이 실제 회사 공시를 직접 읽게 하는 실습 도구입니다. 종목 추천은 하지 않습니다.</p>
    <div className="intro-actions"><button type="button" className="primary-action" onClick={onDemo}>데모로 예시 반 보기</button><button type="button" className="secondary-action" onClick={onLogin}>로그인</button></div>
  </section>

  if (id) {
    const data = detail.data
    return <section className="stock-page class-page" aria-labelledby="class-title">
      <a className="link-more" href="/classes">← 공부 반 목록</a>
      {detail.loading && <p className="status" role="status">반을 불러오는 중입니다.</p>}
      {detail.error && <p className="stock-empty" role="alert">이 반을 볼 수 없습니다. 반장이거나 반에 들어간 사람만 볼 수 있습니다.</p>}
      {data && <>
        <p className="eyebrow">{data.role === 'OWNER' ? 'STUDY CLASS · 반장 화면' : 'STUDY CLASS · 수강생 화면'}{data.sample ? ' · 예시 데이터' : ''}</p>
        <h1 id="class-title">{data.name}</h1>
        {data.sample && <p className="class-help">면접·시연용 예시 반입니다. 수강생 A·B·C는 로그인할 수 없는 예시 데이터입니다.</p>}
        {data.role === 'OWNER' ? <OwnerBoard data={data} reload={loadDetail} /> : <MemberView data={data} />}
        <div className="class-foot">{data.role === 'OWNER'
          ? !data.sample && <button type="button" className="secondary-action" onClick={async () => { try { await deleteClass(data.id); window.location.assign('/classes') } catch (error) { setMessage(say(error)) } }}>반 닫기</button>
          : <button type="button" className="secondary-action" onClick={async () => { try { await leaveClass(data.id); window.location.assign('/classes') } catch (error) { setMessage(say(error)) } }}>반에서 나가기</button>}</div>
        {message && <p className="class-message" role="status">{message}</p>}
      </>}
    </section>
  }

  async function make(event) {
    event.preventDefault()
    setMessage('')
    // 빈칸이면 막지 않고 예시 이름을 넣어 준다. 한 번 더 누르면 만들어진다.
    if (!name.trim()) { setName('주식 첫걸음 1기'); setMessage('반 이름 예시를 넣어 드렸어요. 바꾸거나 그대로 「반 만들기」를 한 번 더 누르세요.'); return }
    try { const made = await createClass(name.trim()); window.location.assign(`/classes?id=${made.id}`) } catch (error) { setMessage(say(error)) }
  }
  async function enter(event) {
    event.preventDefault()
    setMessage('')
    if (code.trim().length < 6) { setMessage('반장에게 받은 초대 코드(6자리 이상)를 넣고 눌러 주세요. 코드가 없으면 왼쪽에서 반을 직접 만들어 볼 수 있어요.'); document.getElementById('class-code')?.focus(); return }
    try { const joined = await joinClass(code.trim()); window.location.assign(`/classes?id=${joined.id}`) } catch (error) { setMessage(say(error)) }
  }
  const owned = list.data?.owned ?? []
  const joined = list.data?.joined ?? []
  return <section className="stock-page class-page" aria-labelledby="class-title">
    <p className="eyebrow">STUDY CLASS · 강사·스터디용</p>
    <h1 id="class-title">공부 반</h1>
    {list.loading && <p className="status" role="status">공부 반을 불러오는 중입니다.</p>}
    {list.error && <p className="stock-empty" role="alert">공부 반을 불러오지 못했습니다.</p>}
    {list.data && <div className="class-lists">
      <section className="class-section" aria-labelledby="owned-title"><h2 id="owned-title">내가 만든 반</h2>
        {owned.length === 0 ? <p className="stock-empty">아직 만든 반이 없습니다.</p>
          : <ul className="class-cards">{owned.map(c => <li key={c.id}><a href={`/classes?id=${c.id}`}><strong>{c.name}</strong>
            <span>수강생 {c.members}명 · 과제 {c.assignments}개{c.sample ? ' · 예시' : ''}</span></a></li>)}</ul>}
        <form className="class-row" onSubmit={make}><label htmlFor="class-name">새 반 이름</label>
          <input id="class-name" value={name} maxLength={100} onChange={e => setName(e.target.value)} placeholder="예: 주식 첫걸음 4기" />
          <button type="submit" className="primary-action">반 만들기</button></form>
      </section>
      <section className="class-section" aria-labelledby="joined-title"><h2 id="joined-title">들어간 반</h2>
        {joined.length === 0 ? <p className="stock-empty">아직 들어간 반이 없습니다.</p>
          : <ul className="class-cards">{joined.map(c => <li key={c.id}><a href={`/classes?id=${c.id}`}><strong>{c.name}{c.newAssignments > 0 && <span className="class-new">새 과제 {c.newAssignments}</span>}</strong><span>과제 {c.assignments}개</span></a></li>)}</ul>}
        <form className="class-row" onSubmit={enter}><label htmlFor="class-code">초대 코드</label>
          <input id="class-code" value={code} maxLength={12} onChange={e => setCode(e.target.value)} placeholder="반장에게 받은 코드" />
          <button type="submit" className="secondary-action">들어가기</button></form>
        <p className="class-help">반에 들어가면 반장이 이 반 과제 회사에 대해 내가 남긴 코스 노트와 진도를 볼 수 있습니다. 다른 공부 노트는 보이지 않습니다.</p>
      </section>
    </div>}
    {message && <p className="class-message" role="status">{message}</p>}
  </section>
}
