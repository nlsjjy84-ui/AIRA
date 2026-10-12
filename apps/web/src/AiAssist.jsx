import { useEffect, useState } from 'react'
import { requestAssist } from './api/assistApi.js'

const COPY = {
  INSPECT: { button: '쉬운 말로 풀어보기', title: 'AI 쉬운 풀이', lead: '화면에 나온 값과 기간만 쉬운 말로 풀어 줍니다.' },
  BRIEFING: { button: '변화 3줄 요약', title: 'AI 변화 요약', lead: '새로 정리된 변화를 짧게 묶어 줍니다. 같은 기간의 변화이며 원인을 뜻하지 않습니다.' },
  ASK: { button: '관점 설명 보기', title: 'AI 관점 안내', lead: '각 관점이 무엇을 보여주는지 설명만 합니다. 어느 쪽을 고를지는 직접 정합니다.' },
  COMPARE: { button: '비교 쉬운 풀이', title: 'AI 비교 풀이', lead: '서버가 계산한 증감 값을 문장으로 풀어 줍니다.' },
  ALERT: { button: '알림 쉬운 풀이', title: 'AI 알림 풀이', lead: '이 알림이 어떤 내용인지 쉽게 설명합니다.' },
  EVIDENCE: { button: '이 근거는 무엇인가요', title: 'AI 근거 안내', lead: '근거 자료의 출처와 종류를 설명합니다. 원문 내용은 읽지 않습니다.' },
  TERM: { button: '이 회사 값으로 설명 듣기', title: 'AI 용어 선생님', lead: '용어를 이 회사의 실제 값으로 계산해 보이며 설명합니다. 좋고 나쁨은 판단하지 않습니다.' },
  DAY: { button: '그날 있었던 일 정리', title: 'AI 하루 정리', lead: '그날 시세와 같은 날 공시를 사실만 정리합니다. 원인을 단정하지 않습니다.' },
  WHY: { button: '왜 이렇게 변했지?', title: 'AI 변화 풀이', lead: '지표를 이루는 값이 각각 얼마나 바뀌었는지로만 설명합니다. 회사 밖의 원인이나 전망은 말하지 않습니다.' },
  CHECK: { button: '체크리스트 쉬운 풀이', title: 'AI 체크리스트 풀이', lead: '각 항목이 무엇을 확인하는지 풀어 줍니다. 투자 판단은 하지 않습니다.' },
}

/** Optional reading help. It never replaces the shown values or the official evidence. */
export default function AiAssist({ kind, subject = '', facts, loggedIn }) {
  const [state, setState] = useState({ status: 'idle', result: null, english: null })
  const signature = JSON.stringify([kind, subject, facts])
  useEffect(() => { setState({ status: 'idle', result: null, english: null }) }, [signature])
  const copy = COPY[kind]
  if (!copy || !Array.isArray(facts) || facts.length === 0) return null

  if (!loggedIn) {
    return <aside className="ai-assist ai-assist-locked" aria-label={copy.title}>
      <p className="ai-assist-lead">로그인하면 {copy.button} 기능을 쓸 수 있습니다. 이 기능은 선택이며 화면의 값은 그대로입니다.</p>
    </aside>
  }

  async function run() {
    setState({ status: 'loading', result: null, english: null })
    try {
      const result = await requestAssist({ kind, subject, facts })
      setState({ status: 'done', result, english: null })
    } catch {
      setState({ status: 'error', result: null, english: null })
    }
  }

  async function translate() {
    const text = state.result?.text
    if (!text) return
    setState(value => ({ ...value, english: { status: 'loading' } }))
    try {
      const result = await requestAssist({ kind: 'TRANSLATE', subject: '', facts: [{ label: 'text', value: text, period: '' }] })
      setState(value => ({ ...value, english: result.method === 'AI' ? { status: 'done', text: result.text } : { status: 'failed' } }))
    } catch {
      setState(value => ({ ...value, english: { status: 'failed' } }))
    }
  }

  const result = state.result
  return <aside className="ai-assist" aria-label={copy.title}>
    <div className="ai-assist-head">
      <div><p className="eyebrow">AI · 선택 기능</p><h3>{copy.title}</h3></div>
      <button type="button" className="ai-assist-action" onClick={run} disabled={state.status === 'loading'}>
        {state.status === 'loading' ? '풀이를 만드는 중…' : copy.button}</button>
    </div>
    <p className="ai-assist-lead">{copy.lead}</p>
    {state.status === 'error' && <p className="ai-assist-note" role="status">AI 보조를 지금 사용할 수 없습니다. 화면의 값과 공식 근거는 그대로 확인할 수 있습니다.</p>}
    {result && <div className="ai-assist-result" role="status">
      <span className={`ai-assist-badge ${result.method === 'AI' ? 'is-ai' : 'is-rule'}`}>
        {result.method === 'AI' ? 'AI 참고 · 근거 아님' : '규칙 기반 정리'}</span>
      <p className="ai-assist-text">{result.text}</p>
      {(result.limitations ?? []).length > 0 && <ul className="ai-assist-limits">{result.limitations.map(item => <li key={item}>{item}</li>)}</ul>}
      {result.method === 'AI' && !state.english && <button type="button" className="ai-assist-link" onClick={translate}>English</button>}
      {state.english?.status === 'loading' && <p className="ai-assist-note">번역하는 중…</p>}
      {state.english?.status === 'failed' && <p className="ai-assist-note">번역을 사용할 수 없습니다.</p>}
      {state.english?.status === 'done' && <div className="ai-assist-english" lang="en"><span className="ai-assist-badge is-ai">AI translation · not evidence</span><p className="ai-assist-text">{state.english.text}</p></div>}
    </div>}
  </aside>
}
