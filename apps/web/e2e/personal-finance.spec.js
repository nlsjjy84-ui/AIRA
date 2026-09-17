import { expect, test } from '@playwright/test'

test('finance lifecycle preserves imported data on revocation and clears it on deletion', async ({ page }, testInfo) => {
  let authorized = false
  let consent = null
  let spent = 0
  let budget = 0
  const writes = []
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.fallback()
    const method = request.method()
    const reply = body => route.fulfill({ status: 200, json: body })
    const empty = () => route.fulfill({ status: 204, body: '' })
    if (method !== 'GET' && path.startsWith('/api/me/finance/')) writes.push(path)
    if (path === '/api/me') return reply({ userId: 'user-1', nickname: '테스트사용자' })
    if (path === '/api/me/briefings/current') return reply({ status: 'EMPTY', emptyReason: 'NO_INTERESTS', items: [] })
    if (path === '/api/me/alerts/reconcile') return reply({ alerts: [], emptyReason: 'NO_INTERESTS' })
    if (path === '/api/me/finance/access') return reply({ authorized })
    if (path === '/api/me/finance/access/reauthenticate') {
      authorized = true
      return empty()
    }
    if (path.startsWith('/api/me/finance/') && !authorized) {
      return route.fulfill({ status: 403, json: {} })
    }
    if (path === '/api/me/finance/consents' && method === 'POST') {
      expect(request.postDataJSON()).toMatchObject({ sourceType: 'DEMO_IMPORT', providerKey: 'AIRA_DEMO_V1' })
      consent = { id: 'C-1', sourceType: 'DEMO_IMPORT', providerKey: 'AIRA_DEMO_V1', revokedAt: null }
      return reply(consent)
    }
    if (path === '/api/me/finance/consents') return reply(consent ? [consent] : [])
    if (path === '/api/me/finance/demo-import') {
      expect(consent?.revokedAt).toBeNull()
      spent = 180000
      return reply({ insertedTransactionCount: 3 })
    }
    if (path === '/api/me/finance/budgets' && method === 'POST') {
      const body = request.postDataJSON()
      expect(body).toMatchObject({ category: 'TOTAL', currencyCode: 'KRW' })
      expect(body.month).toMatch(/^\d{4}-\d{2}$/)
      budget = Number(body.amount)
      return reply(body)
    }
    if (path === '/api/me/finance/summary') return reply({
      totalSpent: spent, totalBudget: budget, totalRemaining: budget - spent,
      categories: spent ? [{ category: 'FOOD', spent }] : [],
    })
    if (path === '/api/me/finance/patterns') return reply({ method: 'RULE', total: null, categories: [] })
    if (path === '/api/me/finance/consents/C-1' && method === 'DELETE') {
      consent = { ...consent, revokedAt: '2026-09-17T00:00:00Z' }
      return empty()
    }
    if (path === '/api/me/finance/data' && method === 'DELETE') {
      spent = 0; budget = 0; consent = null; authorized = false
      return empty()
    }
    return reply([])
  })
  await page.goto('/finance')
  await page.getByLabel('AIRA 비밀번호').fill('test-password-for-finance')
  await page.getByRole('button', { name: '내 금융 열기' }).click()
  await page.getByRole('button', { name: '데모 데이터 연결', exact: true }).click()
  await expect(page.locator('.finance-total-value')).toHaveText('180,000원')
  await expect(page.getByRole('status')).toContainText('실제 금융기관 연결이 아닙니다')
  await page.getByLabel('전체 예산 설정').fill('500000')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.locator('.finance-total-context')).toContainText('남은 예산 320,000원')
  await page.evaluate(() => window.scrollTo(0, 0))
  const shell = await page.locator('.workflow-sidebar').boundingBox()
  const total = await page.locator('.finance-total-value').boundingBox()
  expect(total.x).toBeGreaterThanOrEqual(shell.x + shell.width + 24)
  await page.screenshot({ path: testInfo.outputPath('finance-budget-desktop.png'), animations: 'disabled' })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('html')).toHaveJSProperty('scrollWidth', 390)
  await page.screenshot({ path: testInfo.outputPath('finance-budget-mobile.png'), fullPage: true })
  await page.getByRole('button', { name: '데모 연결 동의 해제' }).click()
  await expect(page.getByRole('status')).toContainText('자동 삭제되지 않습니다')
  await expect(page.locator('.finance-total-value')).toHaveText('180,000원')
  await page.getByRole('button', { name: '금융데이터 관리' }).click()
  await page.getByRole('button', { name: '취소', exact: true }).click()
  expect(writes).not.toContain('/api/me/finance/data')
  await page.getByRole('button', { name: '금융데이터 관리' }).click()
  await page.getByRole('button', { name: '금융데이터 전체 삭제' }).click()
  await expect(page.getByRole('heading', { name: /내 금융을 열기 전/ })).toBeVisible()
  await page.getByLabel('AIRA 비밀번호').fill('test-password-for-finance')
  await page.getByRole('button', { name: '내 금융 열기' }).click()
  await expect(page.locator('.finance-total-value')).toHaveText('0원')
  await expect(page.locator('.finance-total-context')).toContainText('아직 설정 안 됨')
  await expect(page.getByRole('button', { name: '데모 데이터 연결', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '로그아웃', exact: true })).toBeVisible()
})


test('personal finance stays separate, readable, and scrollable beside the shell', async ({ page }) => {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.fallback()
    if (url.pathname === '/api/me/finance/data' && route.request().method() === 'DELETE') {
      await route.fulfill({ status: 204, body: '' })
      return
    }
    const bodies = {
      '/api/me': { userId: 'user-1', nickname: '테스트사용자' },
      '/api/me/interests': [],
      '/api/me/briefings/current': { status: 'EMPTY', emptyReason: 'NO_INTERESTS', items: [] },
      '/api/me/alerts/reconcile': { alerts: [], emptyReason: 'NO_INTERESTS' },
      '/api/me/finance/access': { authorized: true },
      '/api/me/finance/consents': [{ id: 'C-1', sourceType: 'DEMO_IMPORT', providerKey: 'AIRA_DEMO_V1', revokedAt: null }],
      '/api/me/finance/summary': { totalSpent: 1874000, totalBudget: 2500000, totalRemaining: 626000, categories: [] },
      '/api/me/finance/patterns': { method: 'RULE', total: null, categories: [] },
    }
    await route.fulfill({ status: url.pathname in bodies ? 200 : 404, json: bodies[url.pathname] ?? {} })
  })
  await page.goto('/finance')
  await expect(page.getByRole('heading', { name: '내 금융', exact: true })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'AIRA 흐름' }).getByRole('link')).toHaveCount(5)
  const sidebar = await page.locator('.workflow-sidebar').boundingBox()
  const title = await page.getByRole('heading', { name: '내 금융', exact: true }).boundingBox()
  expect(title.x).toBeGreaterThanOrEqual(sidebar.x + sidebar.width + 24)
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('html')).toHaveJSProperty('scrollWidth', 390)
  const explanation = page.getByRole('heading', { name: 'AI 설명' })
  await explanation.scrollIntoViewIfNeeded()
  await expect(explanation).toBeVisible()
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(0)

  await page.getByRole('button', { name: '금융데이터 관리' }).click()
  await expect(page.getByText(/AIRA 계정은 유지되고/)).toBeVisible()
  await expect(page.locator('html')).toHaveJSProperty('scrollWidth', 390)
  await page.getByRole('button', { name: '금융데이터 전체 삭제' }).click()
  await expect(page.getByRole('heading', { name: /내 금융을 열기 전/ })).toBeVisible()
})