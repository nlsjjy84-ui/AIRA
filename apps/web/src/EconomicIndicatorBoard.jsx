const GDP_NUMBER = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
const GDP_CHANGE = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 1, maximumFractionDigits: 1, signDisplay: 'always' })

function quarter(period) {
  const match = /^(\d{4})Q([1-4])$/.exec(period ?? '')
  return match ? `${match[1]}년 ${match[2]}분기` : period
}

export default function EconomicIndicatorBoard({ state, retry, onEvidence }) {
  const observations = state.data?.observations ?? []
  const latest = observations[0]
  const previous = observations[1]
  const available = state.data?.state === 'AVAILABLE' && latest
  const delta = available && previous ? Number(latest.value) - Number(previous.value) : null

  return <section className="content-section economic-indicator-section" aria-labelledby="real-gdp-title">
    <div className="economic-indicator-heading">
      <div><p className="eyebrow">ECONOMIC CONTEXT</p><h2 id="real-gdp-title">한국 경제의 분기별 실질 GDP</h2></div>
      <p>한국은행 ECOS 공식 통계 · 계절조정 실질 국내총생산</p>
    </div>
    {state.loading && <p className="status" role="status">한국은행 공식 통계를 확인하는 중입니다.</p>}
    {state.error && <div className="state-message" role="alert"><p>실질 GDP를 불러오지 못했습니다.</p><button type="button" className="secondary-action" onClick={retry}>다시 시도</button></div>}
    {!state.loading && !state.error && !available && <p className="status">아직 표시할 수 있는 한국은행 공식 실질 GDP가 없습니다.</p>}
    {!state.loading && !state.error && available && <div className="economic-indicator-reading">
      <div className="economic-indicator-value">
        <span>{quarter(latest.period)}</span>
        <strong>{GDP_NUMBER.format(Number(latest.value))}</strong>
        <small>{state.data.unitName ?? '십억원'}</small>
      </div>
      <div className="economic-indicator-context">
        {delta !== null && <p className="economic-indicator-delta">직전 분기 대비 {GDP_CHANGE.format(delta)} {state.data.unitName ?? '십억원'}</p>}
        <p className="economic-indicator-caveat">공식 통계의 최근 두 관측값을 그대로 보여주며, 투자 판단이나 경기 진단이 아닙니다.</p>
        <div><span>{state.data.sourceName ?? '한국은행 ECOS'}</span>{latest.evidenceId && <button type="button" className="market-index-evidence" onClick={() => onEvidence(latest.evidenceId)}>한국은행 공식 근거 확인</button>}</div>
      </div>
    </div>}
  </section>
}
