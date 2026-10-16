import { useEffect, useState } from 'react'
import { getDataStatus } from './api/classApi.js'

/**
 * 자료 상태. 공식 자료마다 '어느 날짜까지의 자료인지'와 'AIRA가 마지막으로 가져온 시각'을 나눠 보여 준다.
 * 매일 들어와야 하는 자료가 멈추면 '늦음'으로 바로 보이게 해서, 오래된 숫자를 최신처럼 믿지 않게 한다.
 */
export const STATE_LABEL = {
  OK: { text: '정상', tone: 'is-ok' },
  LATE: { text: '늦음', tone: 'is-late' },
  UNKNOWN: { text: '확인 불가', tone: 'is-unknown' }
}

export function formatCollected(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const parts = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(date)
  const get = type => parts.find(part => part.type === type)?.value ?? ''
  return `${get('year')}-${get('month')}-${get('day')} ${get('hour')}:${get('minute')}`
}

export default function DataStatusPage() {
  const [state, setState] = useState({ loading: true, data: null, error: false })
  const load = () => {
    setState({ loading: true, data: null, error: false })
    getDataStatus().then(data => setState({ loading: false, data, error: false }))
      .catch(() => setState({ loading: false, data: null, error: true }))
  }
  useEffect(load, [])

  const data = state.data
  const overall = data ? STATE_LABEL[data.overall] ?? STATE_LABEL.UNKNOWN : null
  return <section className="data-status-page" aria-labelledby="data-status-title">
    <p className="eyebrow">DATA STATUS · 자료 상태</p>
    <h1 id="data-status-title">AIRA 자료는 지금 어디까지 들어와 있을까요?</h1>
    <p className="data-status-lead">자료마다 <b>어느 날짜까지의 자료인지</b>와 <b>AIRA가 마지막으로 가져온 시각</b>을 따로 보여 드려요.
      예전 자료를 오늘 다시 가져왔다고 최신 자료라고 하지 않습니다.</p>
    {state.loading && <p className="status" role="status">자료 상태를 확인하는 중입니다.</p>}
    {state.error && <div className="state-message" role="alert"><p>자료 상태를 불러오지 못했습니다.</p>
      <button type="button" className="secondary-action" onClick={load}>다시 시도</button></div>}
    {data && <>
      <p className={`data-status-overall ${overall.tone}`} role="status">
        전체 상태: <strong>{overall.text}</strong>
        <small> · 확인 시각 {formatCollected(data.checkedAt)} (한국 시각)</small>
      </p>
      <ul className="data-status-list">
        {data.sources.map(source => {
          const label = STATE_LABEL[source.state] ?? STATE_LABEL.UNKNOWN
          return <li key={source.key} className={`data-status-item ${label.tone}`}>
            <div className="data-status-head">
              <h2>{source.label}</h2>
              <span className="data-status-badge">{label.text}</span>
            </div>
            <dl>
              <div><dt>출처</dt><dd>{source.provider}</dd></div>
              <div><dt>갱신 주기</dt><dd>{source.cadence}</dd></div>
              <div><dt>자료 기준</dt><dd>{source.observedThrough ? `${source.observedThrough}까지` : '—'}</dd></div>
              <div><dt>마지막 수집</dt><dd>{formatCollected(source.collectedAt)}</dd></div>
            </dl>
            {source.note && <p className="data-status-note">{source.note}</p>}
          </li>
        })}
      </ul>
    </>}
  </section>
}
