import { useMemo } from 'react'
import { MarketMap } from './MarketPage.jsx'
import { formatWon } from './stockMath.js'

const INDEX_NUMBER = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const SIGNED = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2, signDisplay: 'always' })

function IndexTile({ item, onEvidence }) {
  if (item?.state !== 'AVAILABLE') return <div className="kpi-tile"><span className="kpi-label">{item?.marketCode ?? '지수'}</span><strong className="kpi-value muted">—</strong><span className="kpi-sub">공식 지수 확인 중</span></div>
  const rate = Number(item.changeRate)
  const dir = rate > 0 ? 'up' : rate < 0 ? 'down' : 'flat'
  return <div className="kpi-tile" data-direction={dir}>
    <span className="kpi-label">{item.marketCode}</span>
    <strong className="kpi-value">{INDEX_NUMBER.format(Number(item.close))}</strong>
    <span className={`kpi-change ${dir}`}>{dir === 'up' ? '▲' : dir === 'down' ? '▼' : '–'} {SIGNED.format(Number(item.change))} ({SIGNED.format(rate)}%)</span>
    <span className="kpi-sub">{item.tradingDate} · KRX{item.evidenceId && <> · <button type="button" className="kpi-link" onClick={() => onEvidence(item.evidenceId)}>근거</button></>}</span>
  </div>
}

/** 첫 화면 맨 위: 글보다 숫자와 그림 먼저. 지수·오르내린 종목·거래대금, 그리고 시가총액 크기 시장 지도. */
export default function HomeDashboard({ indexState, screener, onEvidence, aside }) {
  const rows = useMemo(() => (screener.data?.rows ?? []).filter(r => r.close !== null && r.close !== undefined)
    .map(r => ({ ...r, changeRate: r.changeRate === null || r.changeRate === undefined ? null : Number(r.changeRate),
      tradingValue: r.tradingValue === null || r.tradingValue === undefined ? null : Number(r.tradingValue), marketCap: Number(r.marketCap) })), [screener.data])
  const up = rows.filter(r => r.changeRate > 0).length
  const down = rows.filter(r => r.changeRate < 0).length
  const flat = rows.length - up - down
  const value = rows.reduce((sum, r) => sum + (r.tradingValue > 0 ? r.tradingValue : 0), 0)
  const indices = indexState.data?.indices ?? [{ marketCode: 'KOSPI' }, { marketCode: 'KOSDAQ' }]
  const priceDate = screener.data?.priceDate
  const ready = rows.length >= 20
  return <section className="home-dash" aria-labelledby="page-title">
    <div className="home-dash-head">
      <p className="eyebrow">AIRA · 출처가 확인된 공식 숫자 · KRX · OpenDART</p>
      <h1 id="page-title">오늘 시장, 공식 숫자로 봅니다.</h1>
    </div>
    <div className="kpi-row" aria-label="오늘의 숫자">
      {indices.slice(0, 2).map(item => <IndexTile key={item.marketCode} item={item} onEvidence={onEvidence} />)}
      <div className="kpi-tile">
        <span className="kpi-label">오른 종목 · 내린 종목</span>
        <strong className="kpi-value">{ready ? <><span className="up">{up.toLocaleString('ko-KR')}</span><small> · </small><span className="down">{down.toLocaleString('ko-KR')}</span></> : '—'}</strong>
        {ready && <div className="kpi-bar" role="img" aria-label={`오른 종목 ${up}개, 보합 ${flat}개, 내린 종목 ${down}개`}>
          <i className="up" style={{ flexGrow: up }} /><i className="flat" style={{ flexGrow: flat }} /><i className="down" style={{ flexGrow: down }} /></div>}
        <span className="kpi-sub">{priceDate ? `${priceDate} 종가 기준` : '종가 확인 중'}</span>
      </div>
      <div className="kpi-tile">
        <span className="kpi-label">거래대금 합계</span>
        <strong className="kpi-value">{ready ? formatWon(value) : '—'}</strong>
        <span className="kpi-sub">{ready ? `코스피·코스닥 ${rows.length.toLocaleString('ko-KR')}개 종목` : '종가 확인 중'}</span>
      </div>
    </div>
    <div className="home-dash-main">
      <div className="home-dash-map">
        <div className="home-dash-map-head"><h2>시장 지도</h2><span>네모 크기 = 시가총액 · 색 = 등락률</span><a href="/market">크게 보기 →</a></div>
        {ready ? <MarketMap rows={rows} market="ALL" compact /> : <div className="home-dash-map-empty" role="status">{screener.loading ? '공식 종가로 시장 지도를 그리는 중입니다.' : '지금은 시장 지도를 그릴 자료가 없습니다.'}</div>}
      </div>
      <div className="home-dash-aside">{aside}</div>
    </div>
  </section>
}
