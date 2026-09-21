import { request } from './http.js'

export const getLatestMarketIndices = (signal) =>
  request('/api/market-indices/latest', { signal })
