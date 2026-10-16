import { useEffect, useMemo, useState } from 'react'
import { getIndices } from './api/stockApi.js'
import { closeDayLabel, formatPercent, formatWon } from './stockMath.js'

const SERIES = [['krx', 'KRX'], ['kospi', '코스피'], ['kosdaq', '코스닥']]
const SORTS = [['base', '거래소 순서'], ['up', '많이 오른'], ['down', '많이 내린']]

const formatIndex = v => (v === null || v === undefined || !Number.isFinite(Number(v)) ? '—' : Number(v).toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))

/** 시장 › 지수. 거래소 지수 시리즈(KRX·코스피·코스닥) 일별시세. 업종·테마 지수가 하루 동안 어떻게 움직였는지 한눈에 본다. */
export default function IndexBoard() {
  const [series, setSeries] = useState('krx')
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const [sort, setSort] = useState('base')
  const [query, setQuery] = useState('')
  useEffect(() => {
    let alive = true
    setState(s => ({ ...s, loading: true }))
    getIndices(series).then(data => alive && setState({ loading: false, data, error: null })).catch(error => alive && setState({ loading: false, data: null, error }))
    return () => { alive = false }
  }, [series])
  const rows = useMemo(() => {
    const q = query.trim()
    const list = (state.data?.items ?? []).filter(r => !q || r.name.includes(q))
    const rate = r => (r.changeRate === null || r.changeRate === undefined ? null : Number(r.changeRate))
    if (sort === 'up') return [...list].sort((a, b) => (rate(b) ?? -1e9) - (rate(a) ?? -1e9))
    if (sort === 'down') return [...list].sort((a, b) => (rate(a) ?? 1e9) - (rate(b) ?? 1e9))
    return list
  }, [state.data, sort, query])
  const widest = Math.max(1, ...rows.map(r => Math.abs(Number(r.changeRate) || 0)))
  const ok = state.data?.state === 'AVAILABLE'
  const up = rows.filter(r => Number(r.changeRate) > 0).length
  const down = rows.filter(r => Number(r.changeRate) < 0).length
  return <div className="index-board">
    <div className="etf-tools">
      <div className="stock-range" role="group" aria-label="지수 시리즈">{SERIES.map(([k, l]) => <button key={k} type="button" aria-pressed={series === k} onClick={() => setSeries(k)}>{l}</button>)}</div>
      <div className="stock-range" role="group" aria-label="정렬">{SORTS.map(([k, l]) => <button key={k} type="button" aria-pressed={sort === k} onClick={() => setSort(k)}>{l}</button>)}</div>
      <input type="search" aria-label="지수 검색" placeholder="지수 이름 (예: 반도체, 300, 배당)" value={query} onChange={e => setQuery(e.target.value)} />
    </div>
    {state.loading && <p className="status">지수를 불러오는 중입니다.</p>}
    {!state.loading && (state.error || !ok) && <p className="stock-empty">거래소 지수 자료를 지금 불러오지 못했습니다. 잠시 뒤 다시 열어 주세요.</p>}
    {!state.loading && ok && <>
      <p className="quarter-sentence">{closeDayLabel(state.data.date)} 장 마감 기준, 지수 {rows.length}개 가운데 <span className="up">{up}개 오르고</span> <span className="down">{down}개 내렸어요</span>.</p>
      <div className="table-scroll"><table className="stock-table index-table"><thead><tr><th>지수</th><th>종가</th><th>등락률</th><th aria-label="등락 막대" /><th>거래대금</th><th>시가총액</th></tr></thead>
        <tbody>{rows.map(r => { const n = Number(r.changeRate); const cls = n > 0 ? 'up' : n < 0 ? 'down' : ''
          return <tr key={`${r.group}-${r.name}`}><td><strong>{r.name}</strong></td><td>{formatIndex(r.close)}</td>
            <td className={cls}>{formatPercent(r.changeRate)}</td>
            <td className="index-bar-cell"><span className={`index-bar ${cls}`} style={{ '--w': `${Math.min(100, Math.abs(n || 0) / widest * 100)}%` }} /></td>
            <td>{r.value === null || r.value === undefined ? '—' : formatWon(r.value)}</td><td>{r.marketCap === null || r.marketCap === undefined ? '—' : formatWon(r.marketCap)}</td></tr> })}</tbody></table></div>
      {rows.length === 0 && <p className="stock-empty">찾는 지수가 없습니다.</p>}
    </>}
    <details className="stock-learn"><summary>📘 읽는 법 · 용어 풀이</summary><div><dl>
      <div><dt>지수</dt><dd>여러 종목의 가격을 하나의 숫자로 묶은 값입니다. 기준 시점을 100이나 1,000으로 두고 그때보다 얼마나 올랐는지 보여 줍니다.</dd></div>
      <div><dt>KRX 시리즈</dt><dd>코스피와 코스닥을 함께 담는 거래소 지수입니다. KRX 300 같은 대표 지수와 반도체·자동차·헬스케어 같은 업종 지수가 있습니다.</dd></div>
      <div><dt>지수와 ETF</dt><dd>ETF는 대개 이 지수들 가운데 하나를 따라갑니다. 「ETF」 화면의 ‘따라가는 지수’와 함께 보세요.</dd></div></dl></div></details>
    {state.data?.source && <p className="stock-source">출처 · {state.data.source}{state.data.date ? ` · ${state.data.date}` : ''}</p>}
  </div>
}
