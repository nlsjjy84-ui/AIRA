import { useEffect, useState } from 'react'
import { getStockFilings } from './api/stockApi.js'
import { FILING_LABEL } from './stockMath.js'

/** 브리핑에 새 분석이 없을 때도 관심회사의 최근 공식 소식(공시)을 보여 준다. */
export default function InterestFilings({ interests = [] }) {
  const companies = interests.filter(item => item.entityType !== 'SECURITY').slice(0, 6)
  const [rows, setRows] = useState({ loading: true, items: [] })
  const key = companies.map(item => item.entityId).join(',')
  useEffect(() => {
    let alive = true
    Promise.all(companies.map(company => getStockFilings(company.entityId)
      .then(data => (data?.items ?? []).slice(0, 5).map(item => ({ ...item, companyId: company.entityId, companyName: company.canonicalName })))
      .catch(() => [])))
      .then(lists => alive && setRows({ loading: false, items: lists.flat().sort((a, b) => String(b.receiptNo).localeCompare(String(a.receiptNo))).slice(0, 12) }))
    return () => { alive = false }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
  if (!companies.length) return null
  return <div className="interest-filings"><h3 className="stock-h3">관심회사 최근 공시</h3>
    {rows.loading ? <p className="status">관심회사 공시를 불러오는 중입니다.</p> : rows.items.length === 0 ? <p className="status">최근 1년 공시를 찾지 못했습니다.</p>
      : <ul className="stock-filings">{rows.items.map(item => <li key={`${item.companyId}-${item.receiptNo}`}>
        <a href={`/stock/${item.companyId}?v=inspect.filings.ALL.${item.receiptNo}`}><time>{item.date}</time><span className="filing-tag">{FILING_LABEL[item.category]}</span>
          <strong>{item.companyName} · {item.title}</strong><small>종목 화면 →</small></a></li>)}</ul>}
    <p className="market-news-provider">출처 · OpenDART 공시검색</p></div>
}
