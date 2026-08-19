async function request(path, signal) {
  const response = await fetch(path, { signal, headers: { Accept: 'application/json' } })
  let body = null
  try {
    body = await response.json()
  } catch {
    // A non-JSON gateway response is presented as a generic service failure.
  }
  if (!response.ok) {
    const error = new Error('Request failed')
    error.status = response.status
    error.code = body?.code
    throw error
  }
  return body
}

export const getCompanies = (signal) => request('/api/companies', signal)
export const getFinancialPeriods = (companyId, signal) =>
  request(`/api/companies/${encodeURIComponent(companyId)}/financial-periods`, signal)
export const getFinancialFacts = (companyId, period, signal) => {
  const params = new URLSearchParams({
    periodStart: period.periodStart,
    periodEnd: period.periodEnd,
  })
  return request(
    `/api/companies/${encodeURIComponent(companyId)}/financial-facts?${params}`,
    signal,
  )
}
