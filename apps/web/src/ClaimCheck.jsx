import { useState } from 'react'
import { requestClaimCheck } from './api/assistApi.js'

const VERDICT = {
  MATCH: { mark: '✔', label: '일치', tone: 'is-match' },
  APPROX: { mark: '≈', label: '거의 일치', tone: 'is-approx' },
  MISMATCH: { mark: '✘', label: '다름', tone: 'is-mismatch' },
  NO_DATA: { mark: '?', label: '확인할 자료 없음', tone: 'is-nodata' },
  NOT_CHECKABLE: { mark: '◇', label: '확인 불가', tone: 'is-skip' }
}

/**
 * "Is this statement true?" The AI only splits the pasted text into statements. The verdict on each one comes
 * from comparing the stated amount with the stored official value, and the source is shown next to it.
 */
export default function ClaimCheck({ loggedIn }) {
  const [text, setText] = useState('')
  const [state, setState] = useState({ status: 'idle', response: null })

  if (!loggedIn) {
    return <section className="claim-check claim-check-locked" aria-label="이 말, 사실이야?">
      <p className="eyebrow">이 말, 사실이야? · 선택 기능</p>
      <p className="ai-assist-lead">로그인하면 기사·리포트·커뮤니티 글을 붙여 넣고, 숫자로 확인할 수 있는 말이 공식 공시 값과 맞는지 볼 수 있습니다.</p>
    </section>
  }

  async function submit(event) {
    event.preventDefault()
    const value = text.trim()
    if (value.length < 4) return
    setState({ status: 'loading', response: null })
    try {
      setState({ status: 'done', response: await requestClaimCheck(value) })
    } catch {
      setState({ status: 'error', response: null })
    }
  }

  const response = state.response
  return <section className="claim-check" aria-label="이 말, 사실이야?">
    <p className="eyebrow">이 말, 사실이야? · 선택 기능</p>
    <h2>들은 말, 공식 숫자와 맞는지 확인해 보세요.</h2>
    <p className="ai-assist-lead">기사나 글의 문장을 붙여 넣으면 AI가 확인할 말만 나누고, 맞는지는 AIRA에 저장된 공식 값과 직접 비교해 보여 드립니다.</p>
    <form onSubmit={submit} className="claim-check-form">
      <label htmlFor="claim-check-input">확인하고 싶은 말</label>
      <textarea id="claim-check-input" value={text} maxLength={600} rows={3} onChange={event => setText(event.target.value)}
        placeholder="예: 삼성전자는 작년 영업이익이 43.6조 원이라고 한다." />
      <div className="claim-check-actions">
        <button type="submit" disabled={state.status === 'loading' || text.trim().length < 4}>{state.status === 'loading' ? '확인하는 중…' : '사실 확인'}</button>
        <span className="claim-check-count">{text.length}/600</span>
      </div>
    </form>
    {state.status === 'error' && <p className="ai-assist-note" role="status">사실 확인을 지금 사용할 수 없습니다. 잠시 뒤 다시 시도해 주세요.</p>}
    {response && response.results.length === 0 && <p className="ai-assist-note" role="status">{(response.limitations ?? []).slice(-1)[0] ?? '확인할 만한 말을 찾지 못했습니다.'}</p>}
    {response && response.results.length > 0 && <ul className="claim-check-list">
      {response.results.map((result, index) => {
        const verdict = VERDICT[result.verdict] ?? VERDICT.NOT_CHECKABLE
        return <li key={index} className={`claim-check-item ${verdict.tone}`}>
          <p className="claim-check-sentence">“{result.claim}”</p>
          <p className="claim-check-verdict"><span aria-hidden="true">{verdict.mark}</span> <strong>{verdict.label}</strong></p>
          {(result.official || result.stated) && <dl className="claim-check-compare">
            {result.company && <div><dt>대상</dt><dd>{result.company} · {result.item}</dd></div>}
            {result.stated && <div><dt>말한 값</dt><dd>{result.stated}</dd></div>}
            {result.official && <div><dt>공식 값</dt><dd>{result.official}</dd></div>}
            {result.period && <div><dt>기간</dt><dd>{result.period}</dd></div>}
            {result.receiptNumber && <div><dt>접수번호</dt><dd>{result.receiptNumber}</dd></div>}
            {result.priorReceiptNumber && <div><dt>전년 접수번호</dt><dd>{result.priorReceiptNumber}</dd></div>}
          </dl>}
          {result.note && <p className="ai-assist-note">{result.note}</p>}
          <div className="claim-check-links">
            {result.evidenceUrl && <a className="ai-assist-link" href={result.evidenceUrl} target="_blank" rel="noreferrer">DART에서 공시 열기</a>}
            {result.priorEvidenceUrl && <a className="ai-assist-link" href={result.priorEvidenceUrl} target="_blank" rel="noreferrer">전년 공시 열기</a>}
          </div>
        </li>
      })}
    </ul>}
    {response && (response.limitations ?? []).length > 0 && response.results.length > 0 && <ul className="ai-assist-limits">
      {response.limitations.map(item => <li key={item}>{item}</li>)}
    </ul>}
  </section>
}
