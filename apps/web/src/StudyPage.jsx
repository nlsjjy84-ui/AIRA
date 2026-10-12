import { useEffect, useMemo, useState } from 'react'
import { deleteStudyNote, getStudy } from './api/stockApi.js'
import { GLOSSARY } from './stockMath.js'

const KIND_LABEL = { WHY: '왜 그럴까', EXPLAIN: '쉬운 설명', TERM: '용어', QA: 'AI에게 물은 것', DAY: '하루 정리', QUIZ: '퀴즈', CHECK: '체크리스트', COMPARE: '비교', INSPECT: '쉬운 풀이', MEMO: '메모' }

/** 내 공부: 저장한 설명과 이해한 용어가 쌓이는 곳. "정보를 본 것"이 아니라 "알게 된 것"을 모은다. */
export default function StudyPage({ user, onLogin }) {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const [company, setCompany] = useState('ALL')
  const [kind, setKind] = useState('ALL')
  const [q, setQ] = useState('')
  const load = () => getStudy().then(data => setState({ loading: false, data, error: null })).catch(error => setState({ loading: false, data: null, error }))
  useEffect(() => { if (user) load(); else setState({ loading: false, data: null, error: null }) }, [user])
  const notes = useMemo(() => state.data?.notes ?? [], [state.data])
  const companies = [...new Map(notes.filter(n => n.companyId).map(n => [n.companyId, n.companyName])).entries()]
  const shown = notes.filter(n => (company === 'ALL' || n.companyId === company) && (kind === 'ALL' || n.kind === kind)
    && (!q.trim() || `${n.title} ${n.body}`.toLowerCase().includes(q.trim().toLowerCase())))
  const learned = new Set((state.data?.terms ?? []).map(t => t.term))
  const next = GLOSSARY.filter(g => !learned.has(g.term)).slice(0, 6)
  if (!user) return <section className="stock-page"><div className="stock-body"><h1>📚 내 공부</h1>
    <p>AI 설명, 「왜 그럴까」 답, 퀴즈로 이해한 용어가 이곳에 쌓입니다.</p><button type="button" className="primary-action" onClick={onLogin}>로그인하고 시작하기</button></div></section>
  return <section className="stock-page study-page" aria-labelledby="study-title">
    <header className="stock-header"><div><p className="eyebrow">MY STUDY · 알게 된 것들</p><h1 id="study-title">📚 내 공부</h1>
      <p className="stock-hint">종목 화면에서 「📚 공부 노트에 저장」을 누르거나, 용어 사전에서 「이해했어요」, 퀴즈를 맞히면 여기에 쌓여요.</p></div>
      {state.data && <dl className="stock-keyfacts"><div><dt>저장한 설명</dt><dd>{state.data.stats.notes}개</dd></div><div><dt>이해한 용어</dt><dd>{state.data.stats.terms} / {GLOSSARY.length}개</dd></div>
        <div><dt>공부한 회사</dt><dd>{state.data.stats.companies}곳</dd></div></dl>}</header>
    {state.loading && <p className="status">불러오는 중입니다.</p>}
    {state.data && <div className="stock-body">
      <section><h2 className="stock-h3">이해한 용어</h2>
        <div className="progress"><i style={{ width: `${Math.round(learned.size / GLOSSARY.length * 100)}%` }} /></div>
        <div className="quick-stocks">{[...learned].map(t => <span key={t} className="term-chip">✔ {t}</span>)}{learned.size === 0 && <span className="muted">아직 없어요. 종목 화면 「묻다 › 용어 사전」에서 시작해 보세요.</span>}</div>
        {next.length > 0 && <p className="stock-hint">다음에 공부해 볼 용어: {next.map(g => g.term).join(', ')}</p>}</section>
      <section><h2 className="stock-h3">공부 노트 {shown.length}개</h2>
        <div className="screen-filters">
          <label>회사<select value={company} onChange={e => setCompany(e.target.value)}><option value="ALL">전체</option>{companies.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label>
          <label>종류<select value={kind} onChange={e => setKind(e.target.value)}><option value="ALL">전체</option>{Object.entries(KIND_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></label>
          <label>찾기<span className="screen-input"><input value={q} onChange={e => setQ(e.target.value)} placeholder="낱말로 찾기" /></span></label>
        </div>
        {shown.length === 0 && <p className="stock-empty">저장한 설명이 없어요. 종목 화면의 「🤔 왜 그럴까?」나 「AI에게 묻기」에서 설명을 듣고 저장해 보세요.</p>}
        <ul className="study-notes">{shown.map(n => <li key={n.id}>
          <div className="study-note-head"><span className="filing-tag">{KIND_LABEL[n.kind] ?? n.kind}</span>{n.companyId ? <a href={`/stock/${n.companyId}`}>{n.companyName}</a> : <span className="muted">일반</span>}
            <small className="muted">{n.createdAt.slice(0, 10)}</small></div>
          <strong>{n.title}</strong><p>{n.body}</p>{n.source && <small className="muted">근거: {n.source}</small>}
          <button type="button" className="link-button" onClick={async () => { try { await deleteStudyNote(n.id); load() } catch { /* 다음에 */ } }}>지우기</button></li>)}</ul>
      </section></div>}
  </section>
}
