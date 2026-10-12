import { useEffect, useMemo, useState } from 'react'
import { getScreener } from './api/stockApi.js'
import { formatPercent, formatPrice, formatWon, industryName } from './stockMath.js'

const num = v => (v === null || v === undefined || v === '' ? null : Number(v))
const pct = (a, b) => (a === null || b === null || b === 0 ? null : Math.round(a / b * 1000) / 10)
const times = (a, b) => (a === null || b === null || b <= 0 ? null : Math.round(a / b * 100) / 100)

export function screenRow(row) {
  const rev = num(row.revenue), oi = num(row.operatingIncome), ni = num(row.netIncome), eq = num(row.equity), li = num(row.liabilities), cap = num(row.marketCap)
  const prev = num(row.revenuePrev)
  return { ...row, marketCap: cap, revenue: rev, operatingIncome: oi, operatingMargin: pct(oi, rev), roe: pct(ni, eq), debtRatio: pct(li, eq),
    per: times(cap, ni), pbr: times(cap, eq), revenueGrowth: prev && rev !== null ? Math.round((rev - prev) / Math.abs(prev) * 1000) / 10 : null }
}

/** 조건 키: [이름, 단위, 방향(min/max)] */
export const FILTERS = [
  ['capMin', '시가총액 최소', '억 원', 'marketCap', 'min', 1e8], ['opMin', '영업이익률 최소', '%', 'operatingMargin', 'min', 1],
  ['roeMin', 'ROE 최소', '%', 'roe', 'min', 1], ['debtMax', '부채비율 최대', '%', 'debtRatio', 'max', 1],
  ['growthMin', '매출 증가율 최소', '%', 'revenueGrowth', 'min', 1], ['perMax', 'PER 최대', '배', 'per', 'max', 1], ['pbrMax', 'PBR 최대', '배', 'pbr', 'max', 1],
]

export const PRESETS = [
  ['흑자이면서 빚이 적은 회사', { opMin: '0.1', debtMax: '100' }],
  ['매출이 20% 넘게 는 회사', { growthMin: '20' }],
  ['장부가치보다 싸게 거래되는 회사 (PBR 1배 미만)', { pbrMax: '0.99', opMin: '0.1' }],
  ['큰 회사 (시가총액 10조 원 이상)', { capMin: '100000' }],
  ['효율이 높은 회사 (ROE 15% 이상)', { roeMin: '15' }],
]

export function applyFilters(rows, filters, market = 'ALL', industry = 'ALL') {
  return rows.filter(row => (market === 'ALL' || row.market === market) && (industry === 'ALL' || String(row.indutyCode ?? '').startsWith(industry))
    && FILTERS.every(([key, , , field, dir, scale]) => {
      const raw = filters[key]
      if (raw === undefined || raw === '') return true
      const limit = Number(raw) * scale
      const value = row[field]
      if (value === null || value === undefined || !Number.isFinite(value)) return false
      return dir === 'min' ? value >= limit : value <= limit
    }))
}

const COLUMNS = [['name', '회사'], ['marketCap', '시가총액'], ['revenue', '매출'], ['operatingMargin', '영업이익률'], ['roe', 'ROE'], ['debtRatio', '부채비율'],
  ['revenueGrowth', '매출 증가율'], ['per', 'PER'], ['pbr', 'PBR'], ['changeRate', '등락률']]

export default function ScreenerPage() {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const [filters, setFilters] = useState({})
  const [market, setMarket] = useState('ALL')
  const [industry, setIndustry] = useState('ALL')
  const [sort, setSort] = useState(['marketCap', 'desc'])
  const [limit, setLimit] = useState(50)
  useEffect(() => { getScreener().then(data => setState({ loading: false, data, error: null })).catch(error => setState({ loading: false, data: null, error })) }, [])
  const rows = useMemo(() => (state.data?.rows ?? []).map(screenRow), [state.data])
  const industries = useMemo(() => [...new Set(rows.map(r => String(r.indutyCode ?? '').slice(0, 2)).filter(Boolean))].sort(), [rows])
  const found = useMemo(() => {
    const list = applyFilters(rows, filters, market, industry)
    const [key, dir] = sort
    return [...list].sort((a, b) => {
      if (key === 'name') return dir === 'asc' ? a.name.localeCompare(b.name) : b.name.localeCompare(a.name)
      const x = a[key] ?? (dir === 'asc' ? Infinity : -Infinity)
      const y = b[key] ?? (dir === 'asc' ? Infinity : -Infinity)
      return dir === 'asc' ? Number(x) - Number(y) : Number(y) - Number(x)
    })
  }, [rows, filters, market, industry, sort])
  const cell = (row, key) => key === 'name' ? <a href={`/stock/${row.companyId}`}>{row.name}</a>
    : key === 'marketCap' || key === 'revenue' ? formatWon(row[key])
    : key === 'per' || key === 'pbr' ? (row[key] === null ? '—' : `${Number(row[key]).toLocaleString('ko-KR')}배`)
    : key === 'changeRate' ? formatPercent(row[key]) : formatPercent(row[key], { sign: key === 'revenueGrowth' })
  return <section className="stock-page screener" aria-labelledby="screen-title">
    <header className="stock-header"><div><p className="eyebrow">SCREENER · 조건으로 찾기</p><h1 id="screen-title">조건으로 종목 찾기</h1>
      <p className="stock-hint">코스피·코스닥 전 종목을 공식 재무 값과 시세로 걸러 봅니다. 결과는 조건에 맞는 회사 목록일 뿐 추천이 아닙니다.</p></div></header>
    <div className="stock-body">
      <div className="stock-range" role="group" aria-label="자주 쓰는 조건">{PRESETS.map(([label, preset]) =>
        <button key={label} type="button" onClick={() => setFilters(preset)}>{label}</button>)}
        <button type="button" onClick={() => { setFilters({}); setMarket('ALL'); setIndustry('ALL') }}>조건 지우기</button></div>
      <div className="screen-filters">
        <label>시장<select value={market} onChange={e => setMarket(e.target.value)}><option value="ALL">전체</option><option value="KOSPI">코스피</option><option value="KOSDAQ">코스닥</option></select></label>
        <label>업종<select value={industry} onChange={e => setIndustry(e.target.value)}><option value="ALL">전체</option>{industries.map(code => <option key={code} value={code}>{industryName(code)}</option>)}</select></label>
        {FILTERS.map(([key, label, unit]) => <label key={key}>{label}<span className="screen-input"><input type="number" inputMode="decimal" value={filters[key] ?? ''}
          onChange={e => setFilters(f => ({ ...f, [key]: e.target.value }))} /><small>{unit}</small></span></label>)}
      </div>
      {state.loading && <p className="status">전 종목 자료를 모으는 중입니다.</p>}
      {state.error && <p className="status">자료를 불러오지 못했습니다.</p>}
      {!state.loading && !state.error && <>
        <p className="stock-hint"><strong>{found.length.toLocaleString('ko-KR')}개</strong> 회사가 조건에 맞습니다 (전체 {rows.length.toLocaleString('ko-KR')}개). 시세 기준일 {state.data?.priceDate ?? '—'}.</p>
        <div className="table-scroll"><table className="stock-table"><thead><tr>{COLUMNS.map(([key, label]) => <th key={key}>
          <button type="button" className="link-button" onClick={() => setSort(([k, d]) => [key, k === key && d === 'desc' ? 'asc' : 'desc'])}>{label}{sort[0] === key ? (sort[1] === 'desc' ? ' ↓' : ' ↑') : ''}</button></th>)}</tr></thead>
          <tbody>{found.slice(0, limit).map(row => <tr key={row.companyId}>{COLUMNS.map(([key]) => <td key={key}>{cell(row, key)}</td>)}</tr>)}</tbody></table></div>
        {found.length > limit && <button type="button" className="secondary-action" onClick={() => setLimit(limit + 100)}>더 보기 ({found.length - limit}개)</button>}
      </>}
      <details className="stock-learn"><summary>📘 읽는 법 · 용어 풀이</summary><div>
        <p>재무 값은 각 회사의 가장 최근 사업보고서(연결 우선) 기준이고, 시가총액·PER·PBR은 최근 거래일 시가총액으로 계산했습니다. 자료가 없는 회사는 그 조건에서 빠집니다.</p>
        <dl><div><dt>PER</dt><dd>시가총액 ÷ 당기순이익. 적자면 계산하지 않습니다.</dd></div><div><dt>PBR</dt><dd>시가총액 ÷ 자본총계. 1배 미만이면 장부 가치보다 싸게 거래된다는 뜻입니다.</dd></div>
          <div><dt>부채비율</dt><dd>부채총계 ÷ 자본총계 × 100</dd></div><div><dt>ROE</dt><dd>당기순이익 ÷ 자본총계 × 100</dd></div></dl></div></details>
      <p className="stock-source">출처 · {state.data?.source ?? 'OpenDART · KRX'}</p>
    </div>
    <p className="stock-disclaimer">AIRA는 공식 자료를 보여 주고 정리할 뿐, 매수·매도를 권하거나 주가를 예측하지 않습니다.</p>
  </section>
}
