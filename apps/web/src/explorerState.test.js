import { describe, expect, it } from 'vitest'
import { advance, contextTrail, initialExplorerState, selectTarget, stateCopy } from './explorerState.js'

describe('canonical exploration context', () => {
  const company = { entityId: 'company-1', entityType: 'COMPANY', canonicalName: '같은 이름' }
  const security = { entityId: 'security-1', entityType: 'SECURITY', canonicalName: '같은 이름' }

  it('keeps exact context across steps and clears it on a different canonical type', () => {
    const selected = { ...selectTarget(initialExplorerState(), company), perspective: '공식 사실과 근거',
      category: '재무', detail: 'Historical Exact', periodStart: '2025-01-01', periodEnd: '2025-12-31',
      receipt: '20260101000001', comparison: '전년' }
    expect(contextTrail(advance(selected, 'Inspect'))).toContain('2025-01-01 — 2025-12-31')
    expect(contextTrail(selected)).toContain('비교 전년')
    const changed = selectTarget(selected, security)
    expect(changed.target.entityType).toBe('SECURITY')
    expect(changed.periodStart).toBe('')
    expect(changed.receipt).toBe('')
    expect(changed.comparison).toBe(null)
    expect(changed.step).toBe('Ask')
  })

  it('requires an event before Assess and distinguishes API states', () => {
    expect(advance(initialExplorerState(), 'Assess').step).toBe('MAIN')
    for (const state of ['NO_DATA', 'PARTIAL', 'CONFLICTING', 'BLOCKED', 'UNSUPPORTED', 'UNAVAILABLE'])
      expect(stateCopy(state)).toBeTruthy()
  })
})
