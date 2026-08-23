import { request } from './http.js'

export const getCompanies = (signal) => request('/api/companies', { signal })
export const getFinancialPeriods = (companyId, signal) =>
  request(`/api/companies/${encodeURIComponent(companyId)}/financial-periods`, { signal })
export const getFinancialFacts = (companyId, period, signal) => {
  const params = new URLSearchParams({
    periodStart: period.periodStart,
    periodEnd: period.periodEnd,
  })
  return request(`/api/companies/${encodeURIComponent(companyId)}/financial-facts?${params}`,
    { signal })
}
