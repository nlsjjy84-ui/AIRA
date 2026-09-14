import { request } from './http.js'

export const searchEntities = (query, signal) =>
  request(`/api/search?${new URLSearchParams({ query })}`, { signal })

export const readFinancialExact = (companyId, { periodStart, periodEnd, receipt }, signal) =>
  request(`/api/companies/${encodeURIComponent(companyId)}/financial-facts/exact?${new URLSearchParams({ periodStart, periodEnd, receipt })}`, { signal })

export const readAssessmentCurrent = (eventId, signal) =>
  request(`/api/assessments/current?${new URLSearchParams({ eventId })}`, { signal })

export const readMarketCurrent = (securityId, predicate, signal) =>
  request(`/api/securities/${encodeURIComponent(securityId)}/market-current?${new URLSearchParams({ predicate })}`, { signal })

export const readAssessmentHistorical = (assessmentId, signal) =>
  request(`/api/assessments/${encodeURIComponent(assessmentId)}`, { signal })

export const readFinancialComparison = (companyId, a, b, predicates, signal) => {
  const params = new URLSearchParams({
    aStart: a.periodStart, aEnd: a.periodEnd, aReceipt: a.receipt,
    bStart: b.periodStart, bEnd: b.periodEnd, bReceipt: b.receipt,
  })
  predicates.forEach(predicate => params.append('predicates', predicate))
  return request(`/api/companies/${encodeURIComponent(companyId)}/financial-facts/compare?${params}`, { signal })
}

export const readMarketSeries = (securityId, predicate, from, to, signal) =>
  request(`/api/securities/${encodeURIComponent(securityId)}/market-series?${new URLSearchParams({ predicate, from, to })}`, { signal })

export const readMarketPrevious = (securityId, predicate, currentDate, currentFactId, signal) =>
  request(`/api/securities/${encodeURIComponent(securityId)}/market-previous?${new URLSearchParams({ predicate, currentDate, currentFactId })}`, { signal })
