export class ApiError extends Error {
  constructor(status, body) {
    super('Request failed')
    this.status = status
    this.code = body?.code
    this.fields = body?.errors ?? []
  }
}

function csrfToken() {
  if (typeof document === 'undefined') return null
  const cookie = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : null
}

export async function request(path, options = {}) {
  const method = options.method ?? 'GET'
  const headers = { Accept: 'application/json', ...options.headers }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  const response = await fetch(path, { ...options, method, headers, credentials: 'same-origin' })
  let body = null
  if (response.status !== 204) {
    try { body = await response.json() } catch { /* Non-JSON failures stay generic. */ }
  }
  if (!response.ok) throw new ApiError(response.status, body)
  return body
}
