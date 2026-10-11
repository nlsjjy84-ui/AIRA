import { afterEach, describe, expect, it, vi } from 'vitest'
import { request } from './http.js'

afterEach(() => vi.restoreAllMocks())

describe('demo read-only responses', () => {
  it('keeps the session (no 403) and announces the read-only notice', async () => {
    global.fetch = vi.fn(() => Promise.resolve({ ok: false, status: 403, json: () => Promise.resolve({ code: 'DEMO_READ_ONLY' }) }))
    const heard = vi.fn()
    window.addEventListener('aira-demo-readonly', heard)
    await expect(request('/api/me/interests/x', { method: 'POST' })).rejects.toMatchObject({ status: 423, code: 'DEMO_READ_ONLY' })
    expect(heard).toHaveBeenCalledTimes(1)
    window.removeEventListener('aira-demo-readonly', heard)
  })

  it('leaves ordinary 403 responses unchanged', async () => {
    global.fetch = vi.fn(() => Promise.resolve({ ok: false, status: 403, json: () => Promise.resolve({ code: 'FORBIDDEN' }) }))
    await expect(request('/api/me', {})).rejects.toMatchObject({ status: 403 })
  })
})
