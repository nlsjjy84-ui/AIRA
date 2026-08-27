import { request } from './http.js'

export const getRecentEvents = (signal) => request('/api/events', { signal })
export const getEventDetail = (eventId, signal) => request(`/api/events/${eventId}`, { signal })
