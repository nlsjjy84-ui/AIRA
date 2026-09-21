const NEWS_TIME = new Intl.DateTimeFormat('ko-KR', {
  month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit',
  hour12: false, timeZone: 'Asia/Seoul',
})

function formatSeenAt(value) {
  if (!value) return '시각 미상'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '시각 미상' : NEWS_TIME.format(date)
}

export default function MarketNewsBoard({ state, retry }) {
  const items = state.data?.items ?? []
  return <section className="content-section market-news-section" aria-labelledby="market-news-title">
    <div className="market-news-heading">
      <div><p className="eyebrow">MARKET NEWS</p><h2 id="market-news-title">오늘 확인할 시장 뉴스</h2></div>
      <p>최근 24시간 기사 링크 · 뉴스는 AIRA의 Fact·Event·판단과 분리합니다.</p>
    </div>
    {state.loading && <p className="status" role="status">최근 시장 뉴스를 확인하는 중입니다.</p>}
    {state.error && <div className="state-message" role="alert"><p>뉴스 제공자 응답이 지연되고 있습니다. 시장 지수와 공식 사건은 계속 확인할 수 있습니다.</p><button type="button" className="secondary-action" onClick={retry}>다시 시도</button></div>}
    {!state.loading && !state.error && state.data?.stale && <p className="market-news-stale" role="status">뉴스 제공자 응답이 지연되어 직전 수집 결과를 표시합니다.</p>}
    {!state.loading && !state.error && items.length === 0 && <p className="status">최근 24시간에 표시할 한국 시장 뉴스 링크가 없습니다.</p>}
    {!state.loading && !state.error && items.length > 0 && <ol className="market-news-list">{items.map(item => <li key={item.originalUrl}>
      <div className="market-news-meta"><time dateTime={item.seenAt}>{formatSeenAt(item.seenAt)}</time><span>{item.domain}</span></div>
      <h3>{item.title}</h3>
      <a href={item.originalUrl} target="_blank" rel="noopener noreferrer">원문 <span aria-hidden="true">↗</span></a>
    </li>)}</ol>}
    {!state.loading && !state.error && items.length > 0 && <p className="market-news-provider">기사 메타데이터: <a href="https://www.gdeltproject.org/" target="_blank" rel="noopener noreferrer">{state.data?.provider ?? 'GDELT Project'}</a></p>}
  </section>
}
