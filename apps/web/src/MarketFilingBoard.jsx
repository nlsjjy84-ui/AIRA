import { useEffect, useState } from 'react'
import { getMarketFilings } from './api/stockApi.js'
import { FILING_CATEGORIES, FILING_LABEL } from './stockMath.js'

const CLOCK = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Seoul' })

/** 서버가 금융감독원에서 마지막으로 받아 온 시각("14:20"). 없으면 null. */
export function checkedClock(value) {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : CLOCK.format(date)
}

/**
 * 첫 화면 공시. 공시는 하루 중에도 계속 새로 받으므로(20분마다) '오늘'이라고 쓸 수 있다. 주가와 달리 사실과 맞다.
 * 오늘 나온 공시가 있으면 그것부터, 없으면(주말·이른 아침) 최근 1주일을 보여 준다. 누르면 그 회사의 종목 화면(공시)으로 들어간다.
 */
export default function MarketFilingBoard() {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const [category, setCategory] = useState('ALL')
  const [market, setMarket] = useState('ALL')
  const [period, setPeriod] = useState(null)
  const [limit, setLimit] = useState(12)
  const load = () => {
    setState({ loading: true, data: null, error: null })
    getMarketFilings().then(data => setState({ loading: false, data, error: null })).catch(error => setState({ loading: false, data: null, error }))
  }
  useEffect(load, [])
  const today = state.data?.to ?? null
  const all = state.data?.items ?? []
  const todayItems = all.filter(item => item.date === today)
  const view = period ?? (todayItems.length > 0 ? 'TODAY' : 'WEEK')
  const pool = view === 'TODAY' ? todayItems : all
  const items = pool.filter(item => (category === 'ALL' || item.category === category) && (market === 'ALL' || item.market === market))
  const clock = checkedClock(state.data?.checkedAt)
  if (!state.loading && (state.error || state.data?.state === 'UNAVAILABLE')) return null
  return <section className="content-section market-filing-section" aria-labelledby="market-filing-title">
    <div className="recent-event-heading">
      <div><p className="eyebrow">FILINGS · OpenDART{clock ? ` · ${clock} 확인` : ''}</p>
        <h2 id="market-filing-title">{view === 'TODAY' ? `오늘 나온 공시 ${todayItems.length.toLocaleString('ko-KR')}건${state.data?.truncated ? ' 이상' : ''}` : '최근 1주일 공시'}</h2></div>
      <p>코스피·코스닥 회사가 금융감독원에 낸 공시입니다. 하루 중에도 계속 새로 받아요. 회사 이름을 누르면 그 종목 화면으로 들어갑니다.</p>
    </div>
    <div className="stock-range" role="group" aria-label="기간">{[['TODAY', `오늘 ${todayItems.length}`], ['WEEK', '최근 1주일']].map(([k, l]) =>
      <button key={k} type="button" aria-pressed={view === k} onClick={() => { setPeriod(k); setLimit(12) }}>{l}</button>)}</div>
    {view === 'TODAY' || todayItems.length > 0 ? null : !state.loading && <p className="stock-hint">오늘은 아직 나온 공시가 없어요. 최근 1주일 공시를 보여 드립니다.</p>}
    <div className="stock-range" role="group" aria-label="시장">{[['ALL', '전체'], ['KOSPI', '코스피'], ['KOSDAQ', '코스닥']].map(([k, l]) =>
      <button key={k} type="button" aria-pressed={market === k} onClick={() => setMarket(k)}>{l}</button>)}</div>
    <div className="stock-range" role="group" aria-label="공시 분류">{FILING_CATEGORIES.map(([k, l]) =>
      <button key={k} type="button" aria-pressed={category === k} onClick={() => setCategory(k)}>{l}
        <small>{k === 'ALL' ? pool.filter(i => market === 'ALL' || i.market === market).length : pool.filter(i => i.category === k && (market === 'ALL' || i.market === market)).length}</small></button>)}</div>
    {state.loading && <p className="status" role="status">공시를 불러오는 중입니다.</p>}
    {!state.loading && items.length === 0 && <p className="status">이 조건의 공시가 없습니다.</p>}
    <ul className="stock-filings">{items.slice(0, limit).map(item => <li key={item.receiptNo}>
      <a href={item.companyId ? `/stock/${item.companyId}?v=inspect.filings.ALL.${item.receiptNo}` : item.url}
        {...(item.companyId ? {} : { target: '_blank', rel: 'noopener noreferrer' })}>
        <time>{item.date === today ? '오늘' : item.date}</time><span className="filing-tag">{FILING_LABEL[item.category]}</span>
        <strong>{item.companyName} · {item.title}</strong><small>{item.market === 'KOSPI' ? '코스피' : '코스닥'}{item.companyId ? ' · 종목 화면 →' : ' · DART ↗'}</small></a></li>)}</ul>
    {items.length > limit && <button type="button" className="secondary-action" onClick={() => setLimit(limit + 24)}>더 보기 ({items.length - limit}건)</button>}
    <p className="market-news-provider">출처 · {state.data?.source ?? 'OpenDART 공시검색'} · {state.data?.from} ~ {state.data?.to}{clock ? ` · ${clock}에 마지막으로 확인` : ''}</p>
  </section>
}
