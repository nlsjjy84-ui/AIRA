import { request } from './http.js'

const json = (body) => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })

export const getCurrentUser = () => request('/api/me')
export const signup = (nickname, password) => request('/api/auth/signup', json({ nickname, password }))
export const login = (nickname, password) => request('/api/auth/login', json({ nickname, password }))
export const logout = () => request('/api/auth/logout', { method: 'POST' })
export const requestRecoveryEmailVerification = (email) => request('/api/auth/recovery-email/verifications', json({ email }))
export const confirmRecoveryEmail = (token) => request('/api/auth/recovery-email/verifications/confirm', json({ token }))
export const requestPasswordReset = (email) => request('/api/auth/password-reset/requests', json({ email }))
export const confirmPasswordReset = (token, newPassword) => request('/api/auth/password-reset/confirm', json({ token, newPassword }))
export const getInterests = () => request('/api/me/interests')
export const addInterest = (entityId) => request(`/api/me/interests/${encodeURIComponent(entityId)}`, { method: 'POST' })
export const removeInterest = (entityId) => request(`/api/me/interests/${encodeURIComponent(entityId)}`, { method: 'DELETE' })
export const enableInterestAlert = (entityId) => request(`/api/me/interests/${encodeURIComponent(entityId)}/alert`, { method: 'POST' })
export const disableInterestAlert = (entityId) => request(`/api/me/interests/${encodeURIComponent(entityId)}/alert`, { method: 'DELETE' })
