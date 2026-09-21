const INDEX_NUMBER = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const CHANGE_NUMBER = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2, signDisplay: 'always' })

function direction(value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number === 0) return 'flat'
  return number > 0 ? 'up' : 'down'
}

function MarketIndexItem({ item, onEvidence }) {
  if (item?.state !== 'AVAILABLE') return <article className="market-index-item unavailable" aria-label={`${item?.marketCode ?? '시장'} 지수`}>
    <div className="market-index-name"><strong>{item?.marketCode ?? '시장'}</strong><span>공식 지수</span></div>
    <p>아직 표시할 수 있는 공식 KRX 지수값이 없습니다.</p>
  </article>

  const move = direction(item.change)
  return <article className="market-index-item" data-direction={move} aria-label={`${item.marketCode} 최근 공식 거래일 지수`}>
    <div className="market-index-name"><strong>{item.marketCode}</strong><span>최근 공식 거래일</span></div>
    <strong className="market-index-value">{INDEX_NUMBER.format(Number(item.close))}</strong>
    <div className="market-index-change" aria-label={`직전 거래일 대비 ${CHANGE_NUMBER.format(Number(item.change))}, ${CHANGE_NUMBER.format(Number(item.changeRate))}퍼센트`}>
      <span aria-hidden="true">{move === 'up' ? '↑' : move === 'down' ? '↓' : '–'}</span>
      <strong>{CHANGE_NUMBER.format(Number(item.change))}</strong>
      <em>{CHANGE_NUMBER.format(Number(item.changeRate))}%</em>
    </div>
    <div className="market-index-meta"><time>{item.tradingDate}</time><span>{item.sourceName ?? 'KRX 공식 데이터'}</span></div>
    {item.evidenceId && <button type="button" className="market-index-evidence" onClick={() => onEvidence(item.evidenceId)}>공식 근거 확인</button>}
  </article>
}

export default function MarketIndexBoard({ state, retry, onEvidence }) {
  return <section className="content-section market-index-section" aria-labelledby="market-index-title">
    <div className="market-index-heading">
      <div><p className="eyebrow">MARKET SNAPSHOT</p><h2 id="market-index-title">최근 공식 거래일 시장</h2></div>
      <p>KRX 일별 공식 데이터 · 실시간 장중 시세가 아닙니다.</p>
    </div>
    {state.loading && <p className="status" role="status">KOSPI·KOSDAQ 공식 지수를 확인하는 중입니다.</p>}
    {state.error && <div className="state-message" role="alert"><p>시장 지수를 불러오지 못했습니다.</p><button type="button" className="secondary-action" onClick={retry}>다시 시도</button></div>}
    {!state.loading && !state.error && <div className="market-index-strip">
      {(state.data?.indices ?? [{ marketCode: 'KOSPI', state: 'NO_DATA' }, { marketCode: 'KOSDAQ', state: 'NO_DATA' }])
        .map(item => <MarketIndexItem key={item.marketCode} item={item} onEvidence={onEvidence} />)}
    </div>}
  </section>
}
