import { request } from './http.js'

export const getLatestMarketNews = (signal) =>
  request('/api/news/latest', { signal })
