import { useEffect, useState } from 'react'
import { getGold, getMarketIndicators } from './api/stockApi.js'
import { LineChart } from './StockCharts.jsx'

const GROUPS = ['환율', '금리', '원자재']

function formatValue(item, value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  if (item.unit === '%') return `${n.toFixed(2)}%`
  return `${n.toLocaleString('ko-KR', { maximumFractionDigits: 2 })} ${item.unit}`
}

function formatChange(item) {
  if (item.change === undefined || item.change === null) return null
  const n = Number(item.change)
  if (!Number.isFinite(n)) return null
  const text = item.unit === '%' ? `${Math.abs(n * 100).toFixed(0)}bp` : Math.abs(n).toLocaleString('ko-KR', { maximumFractionDigits: 2 })
  return { text: n === 0 ? '변동 없음' : `${n > 0 ? '▲' : '▼'} ${text}`, cls: n > 0 ? 'up' : n < 0 ? 'down' : '' }
}

/** 환율·금리·원자재(한국은행 ECOS)와 국내 금 시세(KRX). ECOS는 시세가 아니라 공식 통계라서 기준일을 함께 적는다. */
export default function MarketIndicators() {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const [picked, setPicked] = useState('USD')
  const [gold, setGold] = useState(null)
  useEffect(() => { getMarketIndicators().then(data => setState({ loading: false, data, error: null })).catch(error => setState({ loading: false, data: null, error })) }, [])
  useEffect(() => { getGold().then(setGold).catch(() => setGold(null)) }, [])
  if (state.loading) return <p className="status">환율·금리를 불러오는 중입니다.</p>
  const items = state.data?.items ?? []
  const goldItems = gold?.state === 'AVAILABLE' ? gold.items ?? [] : []
  const goldBlock = goldItems.length > 0 && <section className="macro-group" aria-label="국내 금 시세"><h3 className="stock-h3">금 · 국내 시세(KRX 금시장)</h3>
    <div className="macro-tiles">{goldItems.map(g => { const n = Number(g.change)
      return <div key={g.code || g.name} className="macro-tile is-static"><span className="macro-label">{g.name.replace('_', ' ')}</span>
        <strong>{Number(g.close).toLocaleString('ko-KR')} 원/g</strong>
        {Number.isFinite(n) && g.change !== null && <span className={`macro-change ${n > 0 ? 'up' : n < 0 ? 'down' : ''}`}>{n === 0 ? '변동 없음' : `${n > 0 ? '▲' : '▼'} ${Math.abs(n).toLocaleString('ko-KR')}원`}{g.changeRate !== null && g.changeRate !== undefined ? ` (${Number(g.changeRate) > 0 ? '+' : ''}${Number(g.changeRate).toFixed(2)}%)` : ''}</span>}
        <small>{gold.date} 장 마감 · 1g당</small></div> })}</div></section>
  if (state.error || !items.length) return goldBlock ? <div className="macro-board">{goldBlock}<p className="stock-empty">한국은행 통계를 지금 불러오지 못했습니다. 잠시 뒤 다시 열어 주세요.</p></div>
    : <p className="stock-empty">한국은행 통계를 지금 불러오지 못했습니다. 잠시 뒤 다시 열어 주세요.</p>
  const chosen = items.find(i => i.key === picked) ?? items[0]
  return <div className="macro-board">
    {GROUPS.map(group => {
      const list = items.filter(i => i.group === group)
      if (!list.length) return null
      return <section key={group} className="macro-group" aria-label={group}><h3 className="stock-h3">{group}{group === '원자재' ? ' · 월평균' : ''}</h3>
        <div className="macro-tiles">{list.map(item => { const ch = formatChange(item)
          return <button key={item.key} type="button" className="macro-tile" aria-pressed={chosen.key === item.key} onClick={() => setPicked(item.key)}>
            <span className="macro-label">{item.label}</span><strong>{formatValue(item, item.value)}</strong>
            {ch && <span className={`macro-change ${ch.cls}`}>{ch.text}</span>}<small>{item.time} 기준</small></button> })}</div></section>
    })}
    {goldBlock}
    {chosen?.series?.length > 1 && <div className="macro-chart"><h3 className="stock-h3">{chosen.label} · {chosen.cycle === '월평균' ? '최근 1년(월평균)' : '최근 2개월(일별)'}</h3>
      <LineChart ariaLabel={`${chosen.label} 추이`} height={180} dates={chosen.series.map(p => p.time)}
        series={[{ key: chosen.key, label: chosen.label, color: 'var(--series-a)', values: chosen.series.map(p => Number(p.value)) }]}
        format={x => formatValue(chosen, x)} /></div>}
    <details className="stock-learn"><summary>📘 읽는 법 · 용어 풀이</summary><div><dl>
      <div><dt>매매기준율</dt><dd>은행이 외화를 사고팔 때 기준으로 삼는 환율입니다. 실제로 바꿀 때는 수수료가 붙습니다.</dd></div>
      <div><dt>bp(베이시스 포인트)</dt><dd>금리 변화를 말할 때 쓰는 단위로, 1bp는 0.01%포인트입니다.</dd></div>
      <div><dt>국고채·회사채 금리</dt><dd>나라와 회사가 돈을 빌릴 때의 이자율입니다. 금리가 오르면 회사의 이자 부담이 커지고, 주식보다 채권이 매력적으로 보일 수 있습니다.</dd></div>
      <div><dt>월평균</dt><dd>원유·금·구리는 한국은행이 국제 가격을 달마다 평균 내어 올립니다. 하루하루 시세가 아닙니다.</dd></div>
      <div><dt>국내 금 시세</dt><dd>한국거래소 금시장에서 실제로 거래된 1g당 가격(원)입니다. 위의 국제 금값(달러/트로이온스, 월평균)과 단위가 달라 바로 비교하지 않습니다.</dd></div></dl></div></details>
    <p className="stock-source">출처 · {state.data?.source}{goldItems.length > 0 ? ` · ${gold.source}` : ''}</p>
  </div>
}
