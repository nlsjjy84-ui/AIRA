import { useEffect, useMemo, useRef, useState } from 'react'
import { getScreener } from './api/stockApi.js'
import { screenRow } from './ScreenerPage.jsx'
import { squarify } from './treemap.js'
import { formatPercent, formatPrice, formatWon, industryName } from './stockMath.js'

const TABS = [['map', '시장 지도'], ['sectors', '업종별 등락'], ['up', '상승 상위'], ['down', '하락 상위'], ['value', '거래대금 상위'], ['high', '52주 신고가 근처'], ['low', '52주 신저가 근처']]

function tileColor(rate) {
  if (rate === null || rate === undefined || !Number.isFinite(rate)) return 'var(--surface)'
  const strength = Math.min(1, Math.abs(rate) / 5) * 70 + 10
  return `color-mix(in srgb, ${rate >= 0 ? 'var(--up)' : 'var(--down)'} ${rate === 0 ? 0 : strength}%, var(--surface))`
}

function MarketMap({ rows, market }) {
  const ref = useRef(null)
  const [width, setWidth] = useState(960)
  const [hover, setHover] = useState(null)
  useEffect(() => {
    const node = ref.current
    if (!node || typeof ResizeObserver === 'undefined') return undefined
    const observer = new ResizeObserver(entries => { const w = Math.round(entries[0]?.contentRect?.width ?? 0); if (w > 0) setWidth(w) })
    observer.observe(node)
    return () => observer.disconnect()
  }, [])
  const height = Math.max(420, Math.round(width * 0.62))
  const groups = useMemo(() => {
    const byIndustry = new Map()
    for (const row of rows) {
      if (!(row.marketCap > 0) || (market !== 'ALL' && row.market !== market)) continue
      const code = String(row.indutyCode ?? '').slice(0, 2) || '??'
      if (!byIndustry.has(code)) byIndustry.set(code, [])
      byIndustry.get(code).push(row)
    }
    const list = [...byIndustry.entries()].map(([code, items]) => ({ code, items: items.sort((a, b) => b.marketCap - a.marketCap), value: items.reduce((s, r) => s + r.marketCap, 0) }))
      .sort((a, b) => b.value - a.value)
    const top = list.slice(0, 24)
    const rectangles = squarify(top, 0, 0, width, height)
    return rectangles.map(group => {
      const inner = { x: group.x + 2, y: group.y + 18, w: Math.max(0, group.w - 4), h: Math.max(0, group.h - 20) }
      const shown = group.items.slice(0, 30)
      const rest = group.items.slice(30)
      const tiles = [...shown.map(r => ({ ...r, value: r.marketCap })), ...(rest.length ? [{ name: `그 밖의 ${rest.length}곳`, value: rest.reduce((s, r) => s + r.marketCap, 0), rest: true }] : [])]
      return { ...group, tiles: squarify(tiles, inner.x, inner.y, inner.w, inner.h) }
    })
  }, [rows, market, width, height])
  return <div className="market-map" ref={ref}>
    <svg width={width} height={height} role="img" aria-label="업종별 시장 지도: 네모 크기는 시가총액, 색은 등락률">
      {groups.map(group => <g key={group.code}>
        <rect x={group.x} y={group.y} width={group.w} height={group.h} className="map-group" />
        {group.w > 60 && <text x={group.x + 5} y={group.y + 13} className="map-group-label">{industryName(group.code).slice(0, Math.max(4, Math.floor(group.w / 13)))}</text>}
        {group.tiles.map((tile, i) => <g key={tile.companyId ?? `rest-${i}`} onPointerEnter={() => setHover(tile)} onPointerLeave={() => setHover(null)}
          onClick={() => tile.companyId && window.location.assign(`/stock/${tile.companyId}`)} style={{ cursor: tile.companyId ? 'pointer' : 'default' }}>
          <rect x={tile.x + 0.5} y={tile.y + 0.5} width={Math.max(0, tile.w - 1)} height={Math.max(0, tile.h - 1)} style={{ fill: tile.rest ? 'var(--accent-faint)' : tileColor(tile.changeRate) }} className="map-tile" />
          {tile.w > 46 && tile.h > 26 && <text x={tile.x + tile.w / 2} y={tile.y + tile.h / 2 - (tile.h > 40 ? 4 : -4)} textAnchor="middle" className="map-tile-name"
            style={{ fontSize: Math.max(10, Math.min(18, tile.w / 7)) }}>{tile.name.length > tile.w / 11 ? `${tile.name.slice(0, Math.max(2, Math.floor(tile.w / 12)))}…` : tile.name}</text>}
          {!tile.rest && tile.w > 46 && tile.h > 40 && <text x={tile.x + tile.w / 2} y={tile.y + tile.h / 2 + 14} textAnchor="middle" className="map-tile-rate">{formatPercent(tile.changeRate)}</text>}
        </g>)}
      </g>)}
    </svg>
    {hover && !hover.rest && <div className="chart-tip map-tip"><strong>{hover.name}</strong><span>{industryName(hover.indutyCode)}</span>
      <span>등락률 {formatPercent(hover.changeRate)} · 종가 {formatPrice(hover.close)}</span><span>시가총액 {formatWon(hover.marketCap)}</span><small className="chart-tip-hint">누르면 종목 화면 →</small></div>}
  </div>
}

export default function MarketPage() {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const [tab, setTab] = useState(() => new URLSearchParams(window.location.search).get('tab') ?? 'map')
  const [market, setMarket] = useState('ALL')
  useEffect(() => { getScreener().then(data => setState({ loading: false, data, error: null })).catch(error => setState({ loading: false, data: null, error })) }, [])
  const rows = useMemo(() => (state.data?.rows ?? []).map(screenRow).map(r => ({ ...r, changeRate: r.changeRate === null || r.changeRate === undefined ? null : Number(r.changeRate),
    tradingValue: r.tradingValue === null || r.tradingValue === undefined ? null : Number(r.tradingValue), close: r.close === null || r.close === undefined ? null : Number(r.close),
    high52: r.high52 === null || r.high52 === undefined ? null : Number(r.high52), low52: r.low52 === null || r.low52 === undefined ? null : Number(r.low52) })), [state.data])
  const scoped = rows.filter(r => market === 'ALL' || r.market === market)
  const list = useMemo(() => {
    const valid = scoped.filter(r => r.close !== null)
    if (tab === 'up') return [...valid].filter(r => r.changeRate !== null).sort((a, b) => b.changeRate - a.changeRate).slice(0, 50)
    if (tab === 'down') return [...valid].filter(r => r.changeRate !== null).sort((a, b) => a.changeRate - b.changeRate).slice(0, 50)
    if (tab === 'value') return [...valid].filter(r => r.tradingValue !== null).sort((a, b) => b.tradingValue - a.tradingValue).slice(0, 50)
    if (tab === 'high') return valid.filter(r => r.high52 && r.days52 >= 120 && r.close >= r.high52 * 0.98).sort((a, b) => b.marketCap - a.marketCap)
    if (tab === 'low') return valid.filter(r => r.low52 && r.days52 >= 120 && r.close <= r.low52 * 1.02).sort((a, b) => b.marketCap - a.marketCap)
    return []
  }, [scoped, tab])
  const [sector, setSector] = useState(null)
  const sectors = useMemo(() => {
    const groups = new Map()
    for (const r of scoped) {
      if (r.changeRate === null || !(r.marketCap > 0)) continue
      const code = String(r.indutyCode ?? '').slice(0, 2) || '??'
      if (!groups.has(code)) groups.set(code, { code, items: [], cap: 0, weighted: 0, up: 0, down: 0 })
      const g = groups.get(code)
      g.items.push(r); g.cap += r.marketCap; g.weighted += r.marketCap * r.changeRate
      if (r.changeRate > 0) g.up += 1
      if (r.changeRate < 0) g.down += 1
    }
    return [...groups.values()].map(g => ({ ...g, change: Math.round(g.weighted / g.cap * 100) / 100 })).sort((a, b) => b.change - a.change)
  }, [scoped])
  const up = scoped.filter(r => r.changeRate > 0).length
  const down = scoped.filter(r => r.changeRate < 0).length
  return <section className="stock-page market-page" aria-labelledby="market-title">
    <header className="stock-header"><div><p className="eyebrow">MARKET · {state.data?.priceDate ?? ''} 공식 종가 기준</p><h1 id="market-title">오늘의 시장</h1>
      <p className="stock-hint">코스피·코스닥 전 종목의 최근 거래일 공식 시세입니다. 실시간 시세가 아닙니다.</p></div>
      {!state.loading && <dl className="stock-keyfacts"><div><dt>오른 종목</dt><dd className="up">{up.toLocaleString('ko-KR')}개</dd></div><div><dt>내린 종목</dt><dd className="down">{down.toLocaleString('ko-KR')}개</dd></div>
        <div><dt>전체</dt><dd>{scoped.length.toLocaleString('ko-KR')}개</dd></div></dl>}</header>
    <nav className="stock-subnav" aria-label="시장 보기">{TABS.map(([key, label]) => <button key={key} type="button" aria-current={tab === key ? 'page' : undefined} onClick={() => setTab(key)}>{label}</button>)}</nav>
    <div className="stock-range" role="group" aria-label="시장">{[['ALL', '전체'], ['KOSPI', '코스피'], ['KOSDAQ', '코스닥']].map(([k, l]) => <button key={k} type="button" aria-pressed={market === k} onClick={() => setMarket(k)}>{l}</button>)}</div>
    <div className="stock-body">
      {state.loading && <p className="status">전 종목 시세를 모으는 중입니다.</p>}
      {state.error && <p className="status">시세를 불러오지 못했습니다.</p>}
      {!state.loading && !state.error && rows.length === 0 && <p className="stock-empty">종목 자료를 채우는 중입니다(서버가 처음 켜진 뒤 30분쯤).</p>}
      {!state.loading && rows.length > 0 && tab === 'map' && <><MarketMap rows={rows} market={market} />
        <div className="chart-legend"><span><i style={{ background: tileColor(-5) }} />−5% 이하</span><span><i style={{ background: tileColor(-1) }} />−1%</span><span><i style={{ background: tileColor(0) }} />0%</span>
          <span><i style={{ background: tileColor(1) }} />+1%</span><span><i style={{ background: tileColor(5) }} />+5% 이상</span></div>
        <p className="stock-hint">네모 크기는 시가총액, 색은 하루 등락률입니다. 업종별로 묶었고, 업종이 많으면 큰 업종 24개까지 보입니다. 네모를 누르면 종목 화면으로 갑니다.</p></>}
      {!state.loading && rows.length > 0 && tab === 'sectors' && <>
        <div className="table-scroll"><table className="stock-table"><thead><tr><th>업종</th><th>시가총액 가중 등락률</th><th>오른 / 내린 종목</th><th>회사 수</th><th>업종 시가총액</th></tr></thead>
          <tbody>{sectors.map(g => <tr key={g.code} className="clickable" onClick={() => setSector(sector === g.code ? null : g.code)}>
            <td><button type="button" className="link-button">{industryName(g.code)}</button></td><td className={g.change > 0 ? 'up' : g.change < 0 ? 'down' : ''}>{formatPercent(g.change)}</td>
            <td><span className="up">{g.up}</span> / <span className="down">{g.down}</span></td><td>{g.items.length}</td><td>{formatWon(g.cap)}</td></tr>)}</tbody></table></div>
        {sector && <><h3 className="stock-h3">{industryName(sector)} 회사들</h3><div className="table-scroll"><table className="stock-table"><thead><tr><th>회사</th><th>등락률</th><th>종가</th><th>시가총액</th></tr></thead>
          <tbody>{(sectors.find(g => g.code === sector)?.items ?? []).sort((a, b) => b.marketCap - a.marketCap).map(r => <tr key={r.companyId}><td><a href={`/stock/${r.companyId}`}>{r.name}</a></td>
            <td className={r.changeRate > 0 ? 'up' : r.changeRate < 0 ? 'down' : ''}>{formatPercent(r.changeRate)}</td><td>{formatPrice(r.close)}</td><td>{formatWon(r.marketCap)}</td></tr>)}</tbody></table></div></>}
        <p className="stock-hint">업종 등락률은 큰 회사일수록 더 크게 반영한 값(시가총액 가중)입니다. 업종을 누르면 그 업종 회사들이 보입니다.</p></>}
      {!state.loading && rows.length > 0 && tab !== 'map' && tab !== 'sectors' && <div className="table-scroll"><table className="stock-table"><thead><tr><th>회사</th><th>시장</th><th>종가</th><th>등락률</th><th>거래대금</th><th>시가총액</th>{(tab === 'high' || tab === 'low') && <th>52주 범위</th>}</tr></thead>
        <tbody>{list.map(r => <tr key={r.companyId}><td><a href={`/stock/${r.companyId}`}>{r.name}</a></td><td>{r.market === 'KOSPI' ? '코스피' : '코스닥'}</td><td>{formatPrice(r.close)}</td>
          <td className={r.changeRate > 0 ? 'up' : r.changeRate < 0 ? 'down' : ''}>{formatPercent(r.changeRate)}</td><td>{formatWon(r.tradingValue)}</td><td>{formatWon(r.marketCap)}</td>
          {(tab === 'high' || tab === 'low') && <td>{formatPrice(r.low52)} ~ {formatPrice(r.high52)}</td>}</tr>)}</tbody></table></div>}
      {!state.loading && tab !== 'map' && tab !== 'sectors' && list.length === 0 && rows.length > 0 && <p className="stock-empty">조건에 맞는 종목이 없습니다.</p>}
      <details className="stock-learn"><summary>📘 읽는 법 · 용어 풀이</summary><div><dl>
        <div><dt>52주 신고가 근처</dt><dd>최근 1년 일별 종가 최고값의 98% 이상인 종목입니다(장중 가격 아님, 120거래일 이상 자료가 있는 종목만).</dd></div>
        <div><dt>거래대금</dt><dd>그날 사고판 금액의 합. 관심이 몰린 종목을 볼 때 씁니다.</dd></div>
        <div><dt>등락률 순위</dt><dd>하루 변화일 뿐 이유를 뜻하지 않습니다. 종목 화면의 「잇다 › 주가 × 공시」에서 같은 날 공시를 함께 보세요.</dd></div></dl></div></details>
      <p className="stock-source">출처 · KRX 일별 매매정보 · OpenDART 기업개황(업종)</p>
    </div>
    <p className="stock-disclaimer">AIRA는 공식 자료를 보여 주고 정리할 뿐, 매수·매도를 권하거나 주가를 예측하지 않습니다.</p>
  </section>
}
