const EVENT_LABELS = { EARNINGS: '실적', DISCLOSURE: '공시', BUSINESS: '사업', GOVERNANCE: '지배구조', POLICY_REGULATION: '정책·규제', RISK: '리스크', MARKET: '시장' }

export default function RecentEventBoard({ state, retry, onOpen }) {
  const events = state.data?.events?.slice(0, 5) ?? []
  return <section className="content-section recent-event-section" aria-labelledby="recent-event-title">
    <div className="recent-event-heading">
      <div><p className="eyebrow">CONFIRMED EVENTS</p><h2 id="recent-event-title">최근 확인된 공식 사건</h2></div>
      <p>공식 근거와 회사 연결이 확인된 사건만 표시합니다.</p>
    </div>
    {state.loading && <p className="status" role="status">최근 공식 사건을 확인하는 중입니다.</p>}
    {state.error && <div className="state-message" role="alert"><p>최근 공식 사건을 불러오지 못했습니다.</p><button type="button" className="secondary-action" onClick={retry}>다시 시도</button></div>}
    {!state.loading && !state.error && events.length === 0 && <p className="status">현재 공개할 수 있는 확인된 사건이 없습니다.</p>}
    {!state.loading && !state.error && events.length > 0 && <ol className="recent-event-timeline">{events.map(event => <li key={event.eventId}>
      <div className="recent-event-date"><time>{event.occurredAt?.slice(0, 10) ?? '발생시각 미상'}</time><span>{EVENT_LABELS[event.eventType] ?? event.eventType}</span></div>
      <div className="recent-event-main"><h3>{event.title}</h3><p>{(event.companies ?? []).map(company => company.companyName).join(' · ') || '관련 회사 확인 중'}</p></div>
      <button type="button" onClick={() => onOpen(event.eventId, event.companies ?? [])}>사건·판단 보기</button>
    </li>)}</ol>}
  </section>
}
