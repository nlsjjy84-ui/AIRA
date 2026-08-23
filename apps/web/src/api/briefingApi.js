import { request } from './http.js'

export const getOrCreateBriefing = () => request('/api/me/briefings/current', { method: 'POST' })
export const getBriefing = (briefingId) =>
  request(`/api/me/briefings/${encodeURIComponent(briefingId)}`)
