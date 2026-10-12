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
