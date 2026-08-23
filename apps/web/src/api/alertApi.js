import { request } from './http.js'

export const reconcileAlerts = () => request('/api/me/alerts/reconcile', { method: 'POST' })
export const getAlerts = () => request('/api/me/alerts')
