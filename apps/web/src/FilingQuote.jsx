import { useEffect, useState } from 'react'
import { requestCompare, requestQuote } from './api/assistApi.js'

const BADGE = {
  AI: ['AI가 고른 원문 인용 · 서버 일치 확인', 'is-ai'],
  QUOTES: ['AI가 고른 원문 인용 · 서버 일치 확인', 'is-ai'],
  RULE: ['원문 문단 · 규칙 기반', 'is-rule'],
}

/**
 * Asks a question about one filing and answers only with literal quotes from that filing.
 * The optional plain-language note is a reference sentence, never Evidence.
 */
export default function FilingQuote({ receipt = '', loggedIn }) {
  const [mode, setMode] = useState('ask')
  const [question, setQuestion] = useState('')
  const [amended, setAmended] = useState('')
  const [state, setState] = useState({ status: 'idle', result: null })
  useEffect(() => { setState({ status: 'idle', result: null }) }, [receipt, mode])

  if (!loggedIn) {
    return <section className="ai-assist ai-assist-locked" aria-label="AI 공시 원문 확인">
      <p className="ai-assist-lead">로그인하면 공시 원문에서 질문과 맞는 문장을 그대로 찾아볼 수 있습니다. 이 기능은 선택이며 화면의 값은 그대로입니다.</p>
    </section>
  }

  const validReceipt = /^\d{14}$/.test(receipt)
  const validAmended = /^\d{14}$/.test(amended.trim())

  async function submit(event) {
    event.preventDefault()
    setState({ status: 'loading', result: null })
    try {
      const result = mode === 'ask'
        ? await requestQuote(receipt, question.trim())
        : await requestCompare(receipt, amended.trim())
      setState({ status: 'done', result })
    } catch {
      setState({ status: 'error', result: null })
    }
  }

  const result = state.result
  const badge = result ? BADGE[result.method] : null
  return <section className="ai-assist filing-quote" aria-label="AI 공시 원문 확인">
    <div className="ai-assist-head">
      <div><p className="eyebrow">AI · 선택 기능</p><h3>공시 원문에서 확인하기</h3></div>
      <div className="filing-quote-modes" role="group" aria-label="확인 방식">
        <button type="button" aria-pressed={mode === 'ask'} onClick={() => setMode('ask')}>질문하기</button>
        <button type="button" aria-pressed={mode === 'compare'} onClick={() => setMode('compare')}>정정 전후 비교</button>
      </div>
    </div>
    <p className="ai-assist-lead">{mode === 'ask'
      ? '선택한 공시 원문에서 질문과 맞는 문장을 글자 그대로 보여 드립니다. AI는 문장을 고를 뿐 새 내용을 만들지 않으며, 인용은 서버가 원문과 일치하는지 확인합니다.'
      : '위 접수번호를 이전 공시로 보고, 정정 후 접수번호와 글자가 달라진 문단을 나란히 보여 드립니다. 왜 바뀌었는지는 설명하지 않습니다.'}</p>
    {!validReceipt && <p className="ai-assist-note">위에서 공시를 고르거나 접수번호 14자리를 입력하면 사용할 수 있습니다.</p>}
    <form className="ai-route-form" onSubmit={submit}>
      {mode === 'ask'
        ? <><label htmlFor="filing-quote-question">공시 원문에 대한 질문</label>
          <input id="filing-quote-question" value={question} maxLength={120} placeholder="예: 계약금액이 얼마인가요?"
            onChange={event => setQuestion(event.target.value)} />
          <button type="submit" disabled={!validReceipt || !question.trim() || state.status === 'loading'}>
            {state.status === 'loading' ? '원문을 찾는 중…' : '원문에서 찾기'}</button></>
        : <><label htmlFor="filing-quote-amended">정정 후 접수번호</label>
          <input id="filing-quote-amended" value={amended} inputMode="numeric" maxLength={14} placeholder="정정 후 접수번호 14자리"
            onChange={event => setAmended(event.target.value)} />
          <button type="submit" disabled={!validReceipt || !validAmended || amended.trim() === receipt || state.status === 'loading'}>
            {state.status === 'loading' ? '원문을 비교하는 중…' : '달라진 문단 보기'}</button></>}
    </form>
    {state.status === 'error' && <p className="ai-assist-note" role="status">원문 확인을 지금 사용할 수 없습니다. 접수번호를 확인하거나 DART에서 직접 확인하세요.</p>}
    {result && <div className="ai-assist-result" role="status">
      {badge && <span className={`ai-assist-badge ${badge[1]}`}>{result.method === 'RULE' && mode === 'compare' ? '달라진 문단 · 규칙 기반' : badge[0]}</span>}
      {mode === 'ask' && (result.quotes ?? []).map(quote => <blockquote className="filing-quote-quote" key={`${quote.passageNo}:${quote.text}`}>
        <span>원문 문단 {quote.passageNo} · 글자 그대로</span><p>{quote.text}</p></blockquote>)}
      {mode === 'compare' && (result.changes ?? []).map((change, index) => <div className="filing-quote-change" key={index}>
        <div><span>이전 원문</span><p>{change.before || '(해당 문단 없음)'}</p></div>
        <div><span>정정 후 원문</span><p>{change.after || '(해당 문단 없음)'}</p></div></div>)}
      {result.explanation && <div className="filing-quote-note"><span className="ai-assist-badge is-ai">AI 풀이 · 근거 아님</span>
        <p className="ai-assist-text">{result.explanation}</p></div>}
      {(result.limitations ?? []).length > 0 && <ul className="ai-assist-limits">{result.limitations.map(item => <li key={item}>{item}</li>)}</ul>}
      {mode === 'compare' && result.truncated && <p className="ai-assist-note">차이가 많아 일부만 보여 드립니다.</p>}
      {validReceipt && <a className="ai-assist-link" href={`https://dart.fss.or.kr/dsaf001/main.do?rcpNo=${receipt}`} target="_blank" rel="noreferrer">DART에서 원문 열기</a>}
    </div>}
  </section>
}
