import { request } from './http.js'

const JSON_HEADERS = { 'Content-Type': 'application/json' }
export const listClasses = () => request('/api/me/classes')
export const createClass = name => request('/api/me/classes', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ name }) })
export const joinClass = code => request('/api/me/classes/join', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ code }) })
export const getClass = id => request(`/api/me/classes/${encodeURIComponent(id)}`)
export const addAssignment = (id, body) => request(`/api/me/classes/${encodeURIComponent(id)}/assignments`, { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
export const getMemberNotes = (id, member, assignment) => request(`/api/me/classes/${encodeURIComponent(id)}/notes?${new URLSearchParams({ member, assignment })}`)
export const deleteClass = id => request(`/api/me/classes/${encodeURIComponent(id)}`, { method: 'DELETE' })
export const leaveClass = id => request(`/api/me/classes/${encodeURIComponent(id)}/membership`, { method: 'DELETE' })
export const getClaimStats = (days = 30) => request(`/api/stocks/claim-stats?days=${days}`)
export const getDataStatus = () => request('/api/stocks/data-status')
