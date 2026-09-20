import { expect, test } from '@playwright/test'

test('AIRA staged exploration preserves exact facts, Current, Historical Exact and settings', async ({ page }) => {
  const company = { entityId: 'company-1', entityType: 'COMPANY', canonicalName: '감사회사', externalIdentifier: '00126380' }
  const event = { eventId: 'event-1', title: '공식 사건', eventType: 'EARNINGS', occurredAt: null,
    companies: [{ companyId: company.entityId, companyName: company.canonicalName }], evidence: [] }
  const current = { assessmentId: 'current-assessment', eventId: event.eventId, analysisVersion: 'v2',
    method: 'RULE', confidence: 'LOW', importance: 'MEDIUM', summary: '현재 해석', uncertainty: '현재 불확실성',
    completedAt: '2026-09-14T00:00:00Z', supersedesAssessmentId: 'old-assessment', evidenceIds: [], evidence: [] }
  const requests = []
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (!path.startsWith('/api/')) return route.fallback()
    requests.push(path + url.search)
    const bodies = {
      '/api/me': { userId: 'user-1', nickname: 'AuditUser' },
      '/api/companies': { companies: [] },
      '/api/search': { state: 'AVAILABLE', entities: [company] },
      '/api/events': { events: [event] },
      '/api/companies/company-1/events': { companyId: company.entityId, events: [event] },
      '/api/events/event-1': { ...event, eventEvidence: [], assessment: current },
      '/api/me/interests': [],
      '/api/me/alerts/reconcile': { alerts: [], emptyReason: 'NO_INTERESTS' },
      '/api/me/briefings/current': { status: 'EMPTY', items: [], emptyReason: 'NO_INTERESTS' },
      '/api/assessments/current': { state: 'AVAILABLE', value: current },
      '/api/assessments/old-assessment': { ...current, assessmentId: 'old-assessment', analysisVersion: 'v1',
        uncertainty: '당시 불확실성', supersedesAssessmentId: null },
      '/api/companies/company-1/financial-facts/exact': { state: 'AVAILABLE', selection: 'HISTORICAL_EXACT',
        periodStart: '2025-01-01', periodEnd: '2025-12-31', receipt: '20260101000001',
        value: { facts: [{ predicate: 'REVENUE', value: '100', currency: 'KRW', evidenceId: 'E-1' }] } },
    }
    await route.fulfill({ status: path in bodies ? 200 : 404, json: bodies[path] ?? {} })
  })
  await page.goto('/')
  await expect(page.getByRole('navigation', { name: 'AIRA 흐름' }).getByRole('link')).toHaveCount(5)
  const search = page.getByRole('searchbox', { name: '검색' })
  await search.fill('감사회사')
  await search.press('Enter')
  await page.getByRole('button', { name: /감사회사 기업/ }).click()
  await expect(page.getByRole('heading', { name: /어떤 관점으로 볼지 선택하세요/ })).toBeVisible()
  await expect(page.getByRole('search')).toHaveCount(1)
  await page.getByRole('button', { name: '공식 사실과 근거', exact: true }).click()
  await page.getByRole('button', { name: /^재무/ }).click()
  await page.getByRole('button', { name: /^정확한 기간·공시/ }).click()
  await page.getByLabel('기간 시작', { exact: true }).fill('2025-01-01')
  await page.getByLabel('기간 종료', { exact: true }).fill('2025-12-31')
  await page.getByLabel('공시 접수번호', { exact: true }).fill('20260101000001')
  await page.getByRole('button', { name: '정확한 자료 확인' }).click()
  await expect(page.getByRole('region', { name: '재무 Inspect 결과' })).toContainText('100원')
  const exact = new URL(requests.find(path => path.includes('/financial-facts/exact?')), 'http://localhost')
  expect(exact.searchParams.get('receipt')).toBe('20260101000001')
  expect(exact.searchParams.get('periodStart')).toBe('2025-01-01')
  expect(exact.searchParams.get('periodEnd')).toBe('2025-12-31')
  await page.getByRole('navigation', { name: 'AIRA 흐름' }).getByRole('button', { name: 'Ask 질문' }).click()
  await page.getByRole('button', { name: '사건과 분석', exact: true }).click()
  await page.getByRole('button', { name: '확인된 사건 불러오기' }).click()
  await page.getByRole('button', { name: '이 사건 판단 보기' }).click()
  await expect(page.getByText('발생시각 미상', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'AIRA 해석', exact: true }).click()
  await expect(page.getByText('현재 해석', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '현재·이전 판단 연결 확인' }).click()
  await page.getByRole('button', { name: /이전 당시 판단.*old-assessment/ }).click()
  await expect(page.locator('.historical-summary')).toContainText('당시 불확실성')
  await expect(page.getByText('현재 해석', { exact: true })).toBeVisible()
  expect(requests).toContain('/api/assessments/old-assessment')
  await page.getByRole('button', { name: '설정', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '계정 복구 이메일' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('html')).toHaveJSProperty('scrollWidth', 390)
  expect(pageErrors).toEqual([])
})
