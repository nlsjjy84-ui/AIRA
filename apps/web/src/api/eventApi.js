import { request } from './http.js'

export const getRecentEvents = (signal) => request('/api/events', { signal })
