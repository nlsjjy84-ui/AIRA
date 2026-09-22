import { request } from './http.js'

export const getLatestRealGdp = (signal) =>
  request('/api/economic-indicators/real-gdp/latest', { signal })
