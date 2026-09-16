import { useEffect, useState } from 'react'
import { request } from './api/http.js'

const CONFIDENCE_LABELS = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음' }
const ASSESSMENT_METHOD_LABELS = { RULE: '규칙 기반', AI: 'AI 기반', HYBRID: '혼합', HUMAN_REVIEW: '사람 검토' }

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
  return <section className="content-section" aria-label="당시 판단">
    <h2>당시 판단</h2>
    <p className="viz-contract-term">Historical Exact</p>
    <p>판단 식별자 {assessmentId}</p>
    {state.loading && <p role="status">당시 분석 근거를 불러오는 중입니다.</p>}
    {state.error && <div role="alert"><p>{state.error.status === 404 ? '이 분석 기록은 공개 조회할 수 없습니다.' : '분석 기록을 불러오지 못했습니다.'}</p><button onClick={() => setAttempt(value => value + 1)}>다시 시도</button></div>}
    {state.data && <>
      <p>사건 식별자 {state.data.eventId}</p>
      <div className="historical-metadata-boundary"><strong>당시 분석 메타데이터</strong><p>확신 수준은 사실 확률이나 미래 수익 확률이 아니며, 당시 저장된 분석 조건을 설명합니다.</p></div>
      <p>분석 버전 {state.data.analysisVersion} · {ASSESSMENT_METHOD_LABELS[state.data.method] ?? state.data.method} · 확신 {CONFIDENCE_LABELS[state.data.confidence] ?? state.data.confidence}</p>
      <p>아직 확인할 점: {state.data.uncertainty}</p>
      <p>판단 완료 {state.data.completedAt}</p>
      {state.data.supersedesAssessmentId && <p>대체한 이전 판단 식별자 {state.data.supersedesAssessmentId}</p>}
      {state.data.evidenceIds.length > 0 && <div className="viz-evidence-links historical-evidence-links"><span>당시 근거 식별자</span>{state.data.evidenceIds.map(id => <button key={id} type="button" className="viz-evidence-action" onClick={() => openEvidence(id)}>{id}</button>)}</div>}
    </>}
  </section>
}
