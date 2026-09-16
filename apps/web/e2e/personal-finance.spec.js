import { expect, test } from '@playwright/test'

test('personal finance stays separate, readable, and scrollable beside the shell', async ({ page }) => {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.fallback()
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
})