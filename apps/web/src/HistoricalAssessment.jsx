import { useEffect, useState } from 'react'
import { request } from './api/http.js'

export default function HistoricalAssessment({ assessmentId, openEvidence }) {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({ loading: true })
  useEffect(() => {
    const controller = new AbortController()
    let active = true
    setState({ loading: true })
    request(`/api/assessments/${assessmentId}`, { signal: controller.signal })
      .then(data => { if (active) setState({ data }) })
      .catch(error => { if (active && error.name !== 'AbortError') setState({ error }) })
    return () => { active = false; controller.abort() }
  }, [assessmentId, attempt])
  return <section className="content-section" aria-label="Historical Exact Assessment">
    <h2>Historical Exact — 당시 분석</h2>
    <p>Assessment ID {assessmentId}</p>
    {state.loading && <p role="status">당시 분석 근거를 불러오는 중입니다.</p>}
    {state.error && <div role="alert"><p>{state.error.status === 404 ? '이 분석 기록은 공개 조회할 수 없습니다.' : '분석 기록을 불러오지 못했습니다.'}</p><button onClick={() => setAttempt(value => value + 1)}>다시 시도</button></div>}
    {state.data && <>
      <p>Event ID {state.data.eventId}</p>
      <p>분석 버전 {state.data.analysisVersion} · {state.data.method} · 확신 {state.data.confidence}</p>
      <p>아직 확인할 점: {state.data.uncertainty}</p>
      <p>Assessment 완료 {state.data.completedAt}</p>
      {state.data.supersedesAssessmentId && <p>대체한 Assessment ID {state.data.supersedesAssessmentId}</p>}
      {state.data.evidenceIds.map(id => <button key={id} onClick={() => openEvidence(id)}>당시 근거 {id}</button>)}
    </>}
  </section>
}
