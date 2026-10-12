import { request } from './http.js'

const base = id => `/api/stocks/${encodeURIComponent(id)}`

export const getStockOverview = id => request(`${base(id)}/overview`)
export const getStockPrices = (id, days = 370) => request(`${base(id)}/prices?days=${days}`)
export const getStockFinancials = id => request(`${base(id)}/financials`)
export const getStockFilings = id => request(`${base(id)}/filings`)
export const getStockNews = id => request(`${base(id)}/news`)
export const getMarketIndexSeries = (market, days = 370) => request(`/api/stocks/market-index/${market}?days=${days}`)
export const getMarketFilings = () => request('/api/stocks/market-filings')
export const getStockReportDetails = id => request(`${base(id)}/report-details`)
export const getStockPeers = id => request(`${base(id)}/peers`)
export const getScreener = () => request('/api/stocks/screener')
export const getStockQuarters = id => request(`${base(id)}/quarters`)
export const getStockOwnership = id => request(`${base(id)}/ownership`)
export const getStockCashFlow = id => request(`${base(id)}/cash-flow`)
export const getStockNote = id => request(`/api/me/stock-notes/${encodeURIComponent(id)}`)
export const putStockNote = (id, body) => request(`/api/me/stock-notes/${encodeURIComponent(id)}`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ body }) })
export const getPriceAlerts = () => request('/api/me/price-alerts')
export const createPriceAlert = (companyId, direction, target) => request('/api/me/price-alerts', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ companyId, direction, target }) })
export const deletePriceAlert = id => request(`/api/me/price-alerts/${encodeURIComponent(id)}`, { method: 'DELETE' })
