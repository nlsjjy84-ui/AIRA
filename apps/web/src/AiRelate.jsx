import { useEffect, useState } from 'react'
import { requestCandidates } from './api/assistApi.js'

/**
 * Suggests pairs of already confirmed events that are worth reading together. A pair is a
 * candidate to review, never a confirmed relation, and it never says one event explains another.
 */
export default function AiRelate({ loggedIn, subject = '', events = [], typeLabels = {}, onSelect }) {
  const [state, setState] = useState({ status: 'idle', result: null })
  const signature = JSON.stringify(events.map(item => item.eventId))
  useEffect(() => { setState({ status: 'idle', result: null }) }, [signature])

  if (!loggedIn) {
    return <aside className="ai-assist ai-assist-locked" aria-label="AI 연결 후보">
      <p className="ai-assist-lead">로그인하면 확인된 사건 중 함께 살펴볼 만한 쌍을 후보로 받아볼 수 있습니다. 후보는 확인 전까지 연결로 취급하지 않습니다.</p>
    </aside>
  }
  if (events.length < 2) {
    return <aside className="ai-assist" aria-label="AI 연결 후보">
      <p className="ai-assist-lead">확인된 사건이 2건 이상일 때 함께 살펴볼 후보를 받을 수 있습니다.</p>
    </aside>
  }

  async function run() {
    setState({ status: 'loading', result: null })
    try {
      const payload = events.slice(0, 20).map(item => ({ id: item.eventId, title: item.title ?? '', type: item.eventType ?? '', date: item.occurredAt?.slice(0, 10) ?? '' }))
      setState({ status: 'done', result: await requestCandidates(subject, payload) })
    } catch {
      setState({ status: 'error', result: null })
    }
  }

  const byId = Object.fromEntries(events.map(item => [item.eventId, item]))
  const result = state.result
  const candidates = (result?.candidates ?? []).filter(item => byId[item.firstId] && byId[item.secondId])
  return <aside className="ai-assist ai-relate" aria-label="AI 연결 후보">
    <div className="ai-assist-head">
      <div><p className="eyebrow">AI · 선택 기능</p><h3>함께 살펴볼 사건 후보</h3></div>
      <button type="button" className="ai-assist-action" onClick={run} disabled={state.status === 'loading'}>
        {state.status === 'loading' ? '후보를 찾는 중…' : '함께 볼 사건 쌍 찾기'}</button>
    </div>
    <p className="ai-assist-lead">이미 공식 근거로 확인된 사건 사이에서만 고릅니다. 같은 유형이거나 날짜·제목이 겹치는 쌍이며, 한 사건이 다른 사건의 원인이라는 뜻이 아닙니다.</p>
    {state.status === 'error' && <p className="ai-assist-note" role="status">후보 찾기를 지금 사용할 수 없습니다. 확인된 사건은 그대로 볼 수 있습니다.</p>}
    {result && <div className="ai-assist-result" role="status">
      <span className={`ai-assist-badge ${result.method === 'AI' ? 'is-ai' : 'is-rule'}`}>{result.method === 'AI' ? 'AI 후보 · 미확인' : '규칙 기반 후보 · 미확인'}</span>
      {candidates.length === 0 && <p className="ai-assist-text">함께 살펴볼 만한 사건 쌍을 찾지 못했습니다.</p>}
      {candidates.map(item => <article className="relate-candidate" key={`${item.firstId}:${item.secondId}`}>
        <div className="relate-candidate-pair">{[byId[item.firstId], byId[item.secondId]].map(event => <div key={event.eventId}>
          <strong>{event.title}</strong>
          <span>{typeLabels[event.eventType] ?? event.eventType} · {event.occurredAt?.slice(0, 10) ?? '발생시각 미상'}</span>
          <button type="button" className="ai-assist-link" onClick={() => onSelect?.(event)}>이 사건 판단 보기</button></div>)}</div>
        <p className="ai-assist-text">{item.reason}</p>
        <span className="relate-candidate-state">검토 필요 · 공식 근거로 확인 전</span>
      </article>)}
      {(result.limitations ?? []).length > 0 && <ul className="ai-assist-limits">{result.limitations.map(item => <li key={item}>{item}</li>)}</ul>}
    </div>}
  </aside>
}
