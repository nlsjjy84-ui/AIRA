import { useEffect, useState } from 'react'
import { getMarketFilings } from './api/stockApi.js'
import { FILING_CATEGORIES, FILING_LABEL } from './stockMath.js'

/** 첫 화면: 최근 1주일 코스피·코스닥 공시. 누르면 그 회사의 종목 화면(보다 › 공시)으로 들어간다. */
export default function MarketFilingBoard() {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const [category, setCategory] = useState('ALL')
  const [market, setMarket] = useState('ALL')
  const [limit, setLimit] = useState(12)
  const load = () => {
    setState({ loading: true, data: null, error: null })
    getMarketFilings().then(data => setState({ loading: false, data, error: null })).catch(error => setState({ loading: false, data: null, error }))
  }
  useEffect(load, [])
  const all = state.data?.items ?? []
  const items = all.filter(item => (category === 'ALL' || item.category === category) && (market === 'ALL' || item.market === market))
  if (!state.loading && (state.error || state.data?.state === 'UNAVAILABLE')) return null
  return <section className="content-section market-filing-section" aria-labelledby="market-filing-title">
    <div className="recent-event-heading">
      <div><p className="eyebrow">LATEST FILINGS · OpenDART</p><h2 id="market-filing-title">최근 1주일 공시</h2></div>
      <p>코스피·코스닥 회사가 금융감독원에 낸 공시입니다. 회사 이름을 누르면 그 종목 화면으로 들어갑니다.</p>
    </div>
    <div className="stock-range" role="group" aria-label="시장">{[['ALL', '전체'], ['KOSPI', '코스피'], ['KOSDAQ', '코스닥']].map(([k, l]) =>
      <button key={k} type="button" aria-pressed={market === k} onClick={() => setMarket(k)}>{l}</button>)}</div>
    <div className="stock-range" role="group" aria-label="공시 분류">{FILING_CATEGORIES.map(([k, l]) =>
      <button key={k} type="button" aria-pressed={category === k} onClick={() => setCategory(k)}>{l}
        <small>{k === 'ALL' ? all.filter(i => market === 'ALL' || i.market === market).length : all.filter(i => i.category === k && (market === 'ALL' || i.market === market)).length}</small></button>)}</div>
    {state.loading && <p className="status" role="status">최근 공시를 불러오는 중입니다.</p>}
    {!state.loading && items.length === 0 && <p className="status">이 조건의 공시가 없습니다.</p>}
    <ul className="stock-filings">{items.slice(0, limit).map(item => <li key={item.receiptNo}>
      <a href={item.companyId ? `/stock/${item.companyId}?v=inspect.filings.ALL.${item.receiptNo}` : item.url}
        {...(item.companyId ? {} : { target: '_blank', rel: 'noopener noreferrer' })}>
        <time>{item.date}</time><span className="filing-tag">{FILING_LABEL[item.category]}</span>
        <strong>{item.companyName} · {item.title}</strong><small>{item.market === 'KOSPI' ? '코스피' : '코스닥'}{item.companyId ? ' · 종목 화면 →' : ' · DART ↗'}</small></a></li>)}</ul>
    {items.length > limit && <button type="button" className="secondary-action" onClick={() => setLimit(limit + 24)}>더 보기 ({items.length - limit}건)</button>}
    <p className="market-news-provider">출처 · {state.data?.source ?? 'OpenDART 공시검색'} · {state.data?.from} ~ {state.data?.to}</p>
  </section>
}
