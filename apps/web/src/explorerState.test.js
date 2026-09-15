import { describe, expect, it } from 'vitest'
import { advance, assessmentExplorerState, contextTrail, initialExplorerState, selectTarget, stateCopy } from './explorerState.js'

describe('canonical exploration context', () => {
  const company = { entityId: 'company-1', entityType: 'COMPANY', canonicalName: '같은 이름' }
  const security = { entityId: 'security-1', entityType: 'SECURITY', canonicalName: '같은 이름' }

  it('keeps exact context across steps and clears it on a different canonical type', () => {
    const blank = initialExplorerState()
    expect(advance(blank, 'Ask')).toBe(blank)
    expect(advance(blank, 'Inspect')).toBe(blank)
    expect(advance(blank, 'Relate')).toBe(blank)
    const selected = { ...selectTarget(initialExplorerState(), company), perspective: '공식 사실과 근거',
      category: '재무', detail: 'Historical Exact', periodStart: '2025-01-01', periodEnd: '2025-12-31',
      receipt: '20260101000001', comparison: '전년' }
    const inspect = advance(selected, 'Inspect')
    expect(advance(inspect, 'Inspect')).toBe(inspect)
    expect(contextTrail(inspect)).toContain('2025-01-01 — 2025-12-31')
    expect(contextTrail(inspect)).toContain('비교 전년')
    const relate = advance(selected, 'Relate')
    expect(contextTrail(relate)).toContain('사건')
    expect(contextTrail(relate)).toContain('관련 사건')
    expect(contextTrail(relate)).not.toContain('2025-01-01 — 2025-12-31')
    expect(contextTrail(relate)).not.toContain('공시 접수번호 20260101000001')
    expect(relate.periodStart).toBe('2025-01-01')
    expect(contextTrail(advance(relate, 'Inspect'))).toContain('2025-01-01 — 2025-12-31')
    const changed = selectTarget(selected, security)
    expect(changed.target.entityType).toBe('SECURITY')
    expect(changed.periodStart).toBe('')
    expect(changed.receipt).toBe('')
    expect(changed.comparison).toBe(null)
    expect(changed.step).toBe('Ask')
  })

  it('deep-links Assess without inventing a company target for multi-company events', () => {
    const single = assessmentExplorerState('event-1', [{ companyId: 'company-1', companyName: '회사 A' }])
    expect(single.step).toBe('Assess')
    expect(single.perspective).toBe('사건과 분석')
    expect(single.eventId).toBe('event-1')
    expect(single.target).toEqual({ entityId: 'company-1', entityType: 'COMPANY', canonicalName: '회사 A' })

    const multiple = assessmentExplorerState('event-2', [
      { companyId: 'company-1', companyName: '회사 A' },
      { companyId: 'company-2', companyName: '회사 B' },
    ])
    expect(multiple.step).toBe('Assess')
    expect(multiple.eventId).toBe('event-2')
    expect(multiple.target).toBe(null)
  })

  it('requires an event before Assess and distinguishes API states', () => {
    expect(advance(initialExplorerState(), 'Assess').step).toBe('MAIN')
    const assess = { ...initialExplorerState(), step: 'Assess', eventId: 'event-internal-id', target: company }
    expect(contextTrail(assess)).toContain('선택한 사건')
    expect(contextTrail(assess).join(' ')).not.toContain('event-internal-id')
    for (const state of ['NO_DATA', 'PARTIAL', 'CONFLICTING', 'BLOCKED', 'UNSUPPORTED', 'UNAVAILABLE'])
      expect(stateCopy(state)).toBeTruthy()
  })
})
