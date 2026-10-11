import { request } from './http.js'

const JSON_HEADERS = { 'Content-Type': 'application/json' }

/** Reading help for already-shown values. The text is a reference, never Evidence. */
export function requestAssist(body) {
  return request('/api/me/assist/summary', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
}

/** Turns a free-form question into a whitelisted navigation request; it never answers the question. */
export function requestRoute(question) {
  return request('/api/me/assist/route', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ question }) })
}

/** Quotes from one official filing. Every quote is checked word for word against the filing text. */
export function requestQuote(receiptNumber, question) {
  return request('/api/me/assist/quote', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ receiptNumber, question }) })
}

/** Shows which passages differ between two filings. It does not decide whether they are a correction pair. */
export function requestCompare(originalReceipt, amendedReceipt) {
  return request('/api/me/assist/compare', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ originalReceipt, amendedReceipt }) })
}

/** Pairs of already confirmed events worth reading together. Candidates are never confirmed relations. */
export function requestCandidates(subject, events) {
  return request('/api/me/assist/candidates', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ subject, events }) })
}

/** Splits a pasted text into checkable statements; the server compares each with stored official values. */
export function requestClaimCheck(text) {
  return request('/api/me/assist/claims', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ text }) })
}
