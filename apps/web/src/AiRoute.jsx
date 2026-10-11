import { useState } from 'react'
import { requestRoute } from './api/assistApi.js'

const PERSPECTIVE_LABEL = { INSPECT: '공식 사실과 근거', RELATE: '사건과 분석' }

/**
 * AI route finder. The AI does not answer the question; it only proposes search conditions,
 * which the user confirms before anything opens. Values always come from stored official facts.
 */
export default function AiRoute({ loggedIn, labels = {}, onFollow }) {
  const [question, setQuestion] = useState('')
  const [state, setState] = useState({ status: 'idle', route: null })

  if (!loggedIn) {
    return <section className="ai-route ai-route-locked" aria-label="AI 길찾기">
      <p className="eyebrow">AI 길찾기 · 선택 기능</p>
      <p className="ai-assist-lead">로그인하면 "삼전 작년 영업이익"처럼 말하듯 입력해서 확인할 조건을 정리할 수 있습니다. 위 검색창은 로그인 없이 그대로 쓸 수 있습니다.</p>
    </section>
  }

  async function submit(event) {
    event.preventDefault()
    const text = question.trim()
    if (!text) return
    setState({ status: 'loading', route: null })
    try {
      setState({ status: 'done', route: await requestRoute(text) })
    } catch {
      setState({ status: 'error', route: null })
    }
  }

  const route = state.route
  return <section className="ai-route" aria-label="AI 길찾기">
    <p className="eyebrow">AI 길찾기 · 선택 기능</p>
    <h2>말하듯 입력하면 확인할 조건으로 바꿔 드려요.</h2>
    <p className="ai-assist-lead">AI는 질문에 답하지 않고 대상·관점·기간만 정리합니다. 확인을 누르면 공식 자료에서 값을 읽어옵니다.</p>
    <form onSubmit={submit} className="ai-route-form">
      <label htmlFor="ai-route-input">질문</label>
      <input id="ai-route-input" type="text" value={question} maxLength={120} onChange={event => setQuestion(event.target.value)}
        placeholder="예: 삼전 작년 영업이익" />
      <button type="submit" disabled={state.status === 'loading'}>{state.status === 'loading' ? '정리하는 중…' : '길 찾기'}</button>
    </form>
    {state.status === 'error' && <p className="ai-assist-note" role="status">AI 길찾기를 지금 사용할 수 없습니다. 위 검색창으로 찾아 주세요.</p>}
    {route && <div className="ai-route-card" role="status">
      <span className={`ai-assist-badge ${route.understood ? 'is-ai' : 'is-rule'}`}>{route.understood ? '이렇게 이해했어요' : '입력한 말로 검색'}</span>
      <dl className="ai-route-conditions">
        <div><dt>대상</dt><dd>{route.entityQuery}</dd></div>
        {route.understood && <div><dt>관점</dt><dd>{PERSPECTIVE_LABEL[route.perspective] ?? route.perspective}</dd></div>}
        {route.understood && route.category && <div><dt>분류</dt><dd>{route.category}</dd></div>}
        {route.understood && route.perspective === 'INSPECT' && <div><dt>기간</dt><dd>{route.year ? `${route.year}년 사업연도` : '가장 최근 공시'}</dd></div>}
        {route.understood && (route.items ?? []).length > 0 && <div><dt>항목</dt><dd>{route.items.map(item => labels[item] ?? item).join(' · ')}</dd></div>}
      </dl>
      {route.note && <p className="ai-assist-note">{route.note}</p>}
      <div className="ai-route-actions">
        <button type="button" className="ai-assist-action" onClick={() => onFollow(route)}>{route.understood ? '이대로 확인하기' : '이 말로 검색하기'}</button>
        <button type="button" className="ai-assist-link" onClick={() => setState({ status: 'idle', route: null })}>다시 쓰기</button>
      </div>
    </div>}
  </section>
}
