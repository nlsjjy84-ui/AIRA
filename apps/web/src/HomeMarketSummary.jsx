import { useEffect, useMemo, useState } from 'react'
import { getScreener } from './api/stockApi.js'
import { closeDayLabel, formatPercent, formatWon } from './stockMath.js'

/** 첫 화면: 가장 최근 장 마감 시장을 숫자 몇 개와 상위 종목으로 짧게. 거래소 종가는 다음 영업일 아침에 나오므로 '오늘' 대신 기준일을 쓴다. */
export default function HomeMarketSummary({ screener }) {
  const [own, setOwn] = useState({ loading: true, data: null })
  useEffect(() => { if (screener) return; getScreener().then(data => setOwn({ loading: false, data })).catch(() => setOwn({ loading: false, data: null })) }, [screener])
  const state = screener ?? own
  const rows = useMemo(() => (state.data?.rows ?? []).filter(r => r.close !== null && r.close !== undefined)
    .map(r => ({ ...r, changeRate: r.changeRate === null || r.changeRate === undefined ? null : Number(r.changeRate), tradingValue: r.tradingValue === null || r.tradingValue === undefined ? null : Number(r.tradingValue) })), [state.data])
  if (state.loading || rows.length < 20) return null
  const up = rows.filter(r => r.changeRate > 0).length
  const down = rows.filter(r => r.changeRate < 0).length
  const lists = [
    ['상승 상위', [...rows].filter(r => r.changeRate !== null).sort((a, b) => b.changeRate - a.changeRate).slice(0, 5), r => formatPercent(r.changeRate), 'up'],
    ['하락 상위', [...rows].filter(r => r.changeRate !== null).sort((a, b) => a.changeRate - b.changeRate).slice(0, 5), r => formatPercent(r.changeRate), 'down'],
    ['거래대금 상위', [...rows].filter(r => r.tradingValue !== null).sort((a, b) => b.tradingValue - a.tradingValue).slice(0, 5), r => formatWon(r.tradingValue), 'value'],
  ]
  return <section className="content-section home-market" aria-labelledby="home-market-title">
    <div className="recent-event-heading"><div><p className="eyebrow">LAST CLOSE · {state.data?.priceDate} 공식 종가</p><h2 id="home-market-title">{closeDayLabel(state.data?.priceDate) ?? '최근'} 장 마감 한눈에</h2></div>
      <p>오른 종목 <b className="up">{up.toLocaleString('ko-KR')}</b> · 내린 종목 <b className="down">{down.toLocaleString('ko-KR')}</b> · <a href="/market">시장 지도 전체 보기 →</a></p></div>
    <div className="home-market-grid">{lists.map(([title, items, show, tab]) => <div key={title} className="home-market-card"><h3>{title}</h3>
      <ol>{items.map(r => <li key={r.companyId}><a href={`/stock/${r.companyId}`}>{r.name}</a><span className={r.changeRate > 0 ? 'up' : r.changeRate < 0 ? 'down' : ''}>{show(r)}</span></li>)}</ol>
      <a className="link-more" href={`/market?tab=${tab}`}>더 보기 →</a></div>)}</div>
  </section>
}


