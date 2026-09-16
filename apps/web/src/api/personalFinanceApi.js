import { request } from './http.js'

const JSON_HEADERS = { 'Content-Type': 'application/json' }

export function getFinanceAccess() {
  return request('/api/me/finance/access')
}

export function reauthenticateFinance(password) {
  return request('/api/me/finance/access/reauthenticate', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ password }),
  })
}

export function revokeFinanceAccess() {
  return request('/api/me/finance/access', { method: 'DELETE' })
}

export function getFinanceConsents() {
  return request('/api/me/finance/consents')
}

export function createDemoFinanceConsent() {
  return request('/api/me/finance/consents', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({
      sourceType: 'DEMO_IMPORT',
      providerKey: 'AIRA_DEMO_V1',
      policyVersion: 'pf-demo-v1',
      allowAccounts: true,
      allowTransactions: true,
    }),
  })
}
export function revokeFinanceConsent(consentId) {
  return request(`/api/me/finance/consents/${consentId}`, { method: 'DELETE' })
}

export function importDemoFinanceData() {
  return request('/api/me/finance/demo-import', { method: 'POST' })
}

export function getFinanceSummary(month, currency = 'KRW') {
  return request(`/api/me/finance/summary?month=${encodeURIComponent(month)}&currency=${encodeURIComponent(currency)}`)
}

export function getFinanceBudgets(month, currency = 'KRW') {
  return request(`/api/me/finance/budgets?month=${encodeURIComponent(month)}&currency=${encodeURIComponent(currency)}`)
}

export function upsertFinanceBudget(month, category, amount, currencyCode = 'KRW') {
  return request('/api/me/finance/budgets', {
    method: 'POST', headers: JSON_HEADERS,
    body: JSON.stringify({ month, category, amount, currencyCode }),
  })
}

export function getFinancePatterns(month, currency = 'KRW') {
  return request(`/api/me/finance/patterns?month=${encodeURIComponent(month)}&currency=${encodeURIComponent(currency)}`)
}

export function requestFinanceAiExplanation(month, currency = 'KRW') {
  return request(`/api/me/finance/ai/explanation?month=${encodeURIComponent(month)}&currency=${encodeURIComponent(currency)}`, { method: 'POST' })
}

export function deleteFinanceData() {
  return request('/api/me/finance/data', { method: 'DELETE' })
}