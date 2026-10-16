const NEWS_TIME = new Intl.DateTimeFormat('ko-KR', {
  month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit',
  hour12: false, timeZone: 'Asia/Seoul',
})

const KST_DAY = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'Asia/Seoul' })
const KST_CLOCK = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Seoul' })

/** 한국 시각 기준 오늘이면 "오늘 14:20", 어제면 "어제 23:47", 그 전이면 날짜와 시각. 뉴스는 하루 중 계속 받으므로 '오늘'이 사실과 맞다. */
export function formatSeenAt(value, now = new Date()) {
  if (!value) return '시각 미상'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '시각 미상'
  const day = KST_DAY.format(date)
  if (day === KST_DAY.format(now)) return `오늘 ${KST_CLOCK.format(date)}`
  if (day === KST_DAY.format(new Date(now.getTime() - 864e5))) return `어제 ${KST_CLOCK.format(date)}`
  return NEWS_TIME.format(date)
}

export function isTodayKst(value, now = new Date()) {
  const date = new Date(value ?? '')
  return !Number.isNaN(date.getTime()) && KST_DAY.format(date) === KST_DAY.format(now)
}

/** 뉴스 색인(GDELT)은 제목을 낱말 단위로 끊어 "[ 마켓뷰 ] 코스피 , 2 % 상승"처럼 문장부호 앞뒤에 빈칸을 넣는다. 보기 좋게 붙인다. */
export function tidyTitle(title) {
  return (title ?? '')
    .replace(/\s+([,.!?%…\]\)』」’”·])/g, '$1')
    .replace(/([\[\(『「‘“])\s+/g, '$1')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

export default function MarketNewsBoard({ state, retry, now }) {
  const items = state.data?.items ?? []
  const todayCount = items.filter(item => isTodayKst(item.seenAt, now)).length
  return <section className="content-section market-news-section" aria-labelledby="market-news-title">
    <div className="market-news-heading">
      <div><p className="eyebrow">MARKET NEWS</p><h2 id="market-news-title">{todayCount > 0 ? '오늘 시장 뉴스' : '최근 시장 뉴스'}</h2></div>
      <p>최근 24시간 기사 링크 · 뉴스는 AIRA의 Fact·Event·판단과 분리합니다.</p>
    </div>
    {state.loading && <p className="status" role="status">최근 시장 뉴스를 확인하는 중입니다.</p>}
    {state.error && <div className="state-message" role="alert"><p>뉴스 제공자 응답이 지연되고 있습니다. 시장 지수와 공식 사건은 계속 확인할 수 있습니다.</p><button type="button" className="secondary-action" onClick={retry}>다시 시도</button></div>}
    {!state.loading && !state.error && state.data?.stale && <p className="market-news-stale" role="status">뉴스 제공자 응답이 지연되어 직전 수집 결과를 표시합니다.</p>}
    {!state.loading && !state.error && items.length === 0 && <p className="status">최근 24시간에 표시할 한국 시장 뉴스 링크가 없습니다.</p>}
    {!state.loading && !state.error && items.length > 0 && <ol className="market-news-list">{items.map(item => <li key={item.originalUrl}>
      <div className="market-news-meta"><time dateTime={item.seenAt} title="뉴스 색인이 이 기사를 확인한 시각(기사 작성 시각과 다를 수 있음)">{formatSeenAt(item.seenAt, now)} 확인</time><span>{item.domain}</span></div>
      <h3>{tidyTitle(item.title)}</h3>
      <a href={item.originalUrl} target="_blank" rel="noopener noreferrer">원문 <span aria-hidden="true">↗</span></a>
    </li>)}</ol>}
    {!state.loading && !state.error && items.length > 0 && <p className="market-news-provider">기사 메타데이터: <a href="https://www.gdeltproject.org/" target="_blank" rel="noopener noreferrer">{state.data?.provider ?? 'GDELT Project'}</a></p>}
  </section>
}
