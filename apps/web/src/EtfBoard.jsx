import { useEffect, useMemo, useState } from 'react'
import { getEtfs } from './api/stockApi.js'
import { closeDayLabel, formatPercent, formatPrice, formatWon } from './stockMath.js'

const SORTS = [['value', '거래대금'], ['netAsset', '순자산'], ['up', '많이 오른'], ['down', '많이 내린']]

/** 시장 › ETF. 거래소 ETF 일별매매정보(가장 최근 거래일). NAV와 괴리율, 따라가는 지수를 함께 본다. 추천하지 않는다. */
export default function EtfBoard() {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const [sort, setSort] = useState('value')
  const [query, setQuery] = useState('')
  const [limit, setLimit] = useState(50)
  useEffect(() => { getEtfs().then(data => setState({ loading: false, data, error: null })).catch(error => setState({ loading: false, data: null, error })) }, [])
  const rows = useMemo(() => {
    const n = v => (v === null || v === undefined ? null : Number(v))
    let list = (state.data?.items ?? []).filter(r => !query.trim() || r.name.includes(query.trim()) || (r.indexName ?? '').includes(query.trim()) || r.code.includes(query.trim()))
    const by = { value: r => -(n(r.value) ?? 0), netAsset: r => -(n(r.netAsset) ?? 0), up: r => -(n(r.changeRate) ?? -1e9), down: r => n(r.changeRate) ?? 1e9 }[sort]
    return [...list].sort((a, b) => by(a) - by(b))
  }, [state.data, sort, query])
  if (state.loading) return <p className="status">ETF 시세를 불러오는 중입니다.</p>
  if (state.error || state.data?.state !== 'AVAILABLE') return <p className="stock-empty">거래소 ETF 자료를 지금 불러오지 못했습니다. 잠시 뒤 다시 열어 주세요.</p>
  return <div className="etf-board">
    <p className="quarter-sentence">{closeDayLabel(state.data.date)} 장 마감 기준, 상장 ETF {Number(state.data.count).toLocaleString('ko-KR')}개</p>
    <div className="etf-tools">
      <div className="stock-range" role="group" aria-label="정렬">{SORTS.map(([k, l]) => <button key={k} type="button" aria-pressed={sort === k} onClick={() => { setSort(k); setLimit(50) }}>{l}</button>)}</div>
      <input type="search" aria-label="ETF 검색" placeholder="이름·지수·코드 (예: 반도체, S&P500)" value={query} onChange={e => { setQuery(e.target.value); setLimit(50) }} />
    </div>
    <div className="table-scroll"><table className="stock-table"><thead><tr><th>ETF</th><th>종가</th><th>등락률</th><th>NAV</th><th>괴리율</th><th>거래대금</th><th>순자산</th><th>따라가는 지수</th></tr></thead>
      <tbody>{rows.slice(0, limit).map(r => <tr key={r.code}><td><strong>{r.name}</strong><br /><small className="muted">{r.code}</small></td>
        <td>{formatPrice(r.close)}</td><td className={Number(r.changeRate) > 0 ? 'up' : Number(r.changeRate) < 0 ? 'down' : ''}>{formatPercent(r.changeRate)}</td>
        <td>{r.nav === null ? '—' : formatPrice(r.nav)}</td><td>{r.premium === null ? '—' : formatPercent(r.premium)}</td>
        <td>{formatWon(r.value)}</td><td>{formatWon(r.netAsset)}</td>
        <td>{r.indexName || '—'}{r.indexChangeRate !== null && r.indexChangeRate !== undefined ? <small className="muted"> ({formatPercent(r.indexChangeRate)})</small> : null}</td></tr>)}</tbody></table></div>
    {rows.length > limit && <button type="button" className="secondary-action" onClick={() => setLimit(limit + 100)}>더 보기 ({rows.length - limit}개)</button>}
    {rows.length === 0 && <p className="stock-empty">찾는 ETF가 없습니다.</p>}
    <details className="stock-learn"><summary>📘 읽는 법 · 용어 풀이</summary><div><dl>
      <div><dt>ETF</dt><dd>지수를 따라가도록 만든 펀드를 주식처럼 사고파는 상품입니다. 하나만 사도 그 지수의 여러 종목을 함께 산 효과가 납니다.</dd></div>
      <div><dt>NAV(순자산가치)</dt><dd>ETF가 담고 있는 자산의 1주당 실제 값어치입니다.</dd></div>
      <div><dt>괴리율</dt><dd>(종가 − NAV) ÷ NAV × 100. 시장 가격이 실제 값어치보다 비싸게(+) 또는 싸게(−) 거래됐는지 보여 줍니다.</dd></div>
      <div><dt>순자산</dt><dd>그 ETF에 모인 돈의 크기입니다. 너무 작으면 상장폐지될 수 있어요.</dd></div></dl></div></details>
    <p className="stock-source">출처 · {state.data.source} · {state.data.date}</p>
  </div>
}
