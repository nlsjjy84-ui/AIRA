import { expect, test } from '@playwright/test'

test('AIRA perspective, Current, Historical Exact and settings work in the browser', async ({ page }) => {
  const company = { companyId: 'company-1', canonicalName: '감사회사', countryCode: 'KR' }
  const item = { eventId: 'event-1', title: '공식 사건', eventType: 'EARNINGS', occurredAt: null, companies: [{ companyId: company.companyId, companyName: company.canonicalName }] }
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (!path.startsWith('/api/')) return route.fallback()
    const bodies = {
      '/api/me': { userId: 'user-1', nickname: 'AuditUser' },
      '/api/companies': { companies: [company] },
      '/api/events': { events: [item] },
      '/api/events/event-1': { ...item, eventEvidence: [], assessment: null },
      '/api/me/interests': [{ entityId: 'company-1', canonicalName: '감사회사', alertEnabled: false }],
      '/api/me/alerts/reconcile': { alerts: [], emptyReason: 'NO_INTERESTS' },
      '/api/me/briefings/current': { items: [{ ...item, eventTitle: item.title, assessmentId: 'old-assessment', analysisVersion: 'v1', summary: '당시 해석', uncertainty: '당시 불확실성', evidence: [] }] },
      '/api/assessments/old-assessment': { assessmentId: 'old-assessment', eventId: 'event-1', analysisVersion: 'v1', method: 'RULE', confidence: 'LOW', uncertainty: '당시 불확실성', evidenceIds: [] },
    }
    await route.fulfill({ status: path in bodies ? 200 : 404, json: bodies[path] ?? {} })
  })
  await page.goto('/')
  await expect(page.getByRole('navigation', { name: 'AIRA 흐름' }).getByRole('link')).toHaveCount(5)
  await page.getByRole('radio', { name: '사건과 관련 회사' }).check()
  await expect(page.getByRole('link', { name: '선택한 관점으로 확인' })).toHaveAttribute('href', '#events')
  await page.getByRole('region', { name: '최근 확인된 Event' }).getByRole('button', { name: 'Event 상세 보기', exact: true }).click()
  await expect(page.getByRole('region', { name: 'Event 상세' })).toContainText('발생시각 미상')
  await expect(page.getByRole('region', { name: 'Event 상세' })).toContainText('현재 표시할 AIRA 해석이 없습니다.')
  await page.getByRole('button', { name: '당시 Assessment 보기' }).click()
  await expect(page.getByRole('region', { name: 'Historical Exact Assessment' })).toContainText('당시 불확실성')
  await page.getByRole('button', { name: '설정', exact: true }).click()
  await expect(page.getByRole('heading', { name: '계정 복구 이메일' })).toBeVisible()
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('body')).toHaveJSProperty('scrollWidth', 390)
})
