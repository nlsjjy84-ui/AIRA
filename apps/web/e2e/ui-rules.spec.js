import { expect, test } from '@playwright/test'

async function mockPublicShell(page) {
  await page.route('**/api/me', route => route.fulfill({ status: 401, json: null }))
  await page.route('**/api/market-indices/latest', route => route.fulfill({ status: 200, json: { indices: [] } }))
  await page.route('**/api/events', route => route.fulfill({ status: 200, json: { events: [] } }))
  await page.route('**/api/news/latest', route => route.fulfill({
    status: 200,
    json: { provider: 'GDELT DOC 2.0', fetchedAt: '2026-09-21T02:00:00Z', stale: false, items: [] },
  }))
}

test('shared shell keeps search in Header and workflow responsibilities in Sidebar', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await mockPublicShell(page)
  await page.goto('/explore')

  await expect(page.locator('.site-header').getByRole('search')).toHaveCount(1)
  await expect(page.locator('.canonical-explorer .explorer-search')).toHaveCount(0)

  const sidebar = page.locator('.workflow-sidebar')
  for (const name of ['MAIN 메인', 'Ask 질문', 'Inspect 살피기', 'Relate 잇기', 'Assess 판단']) {
    await expect(sidebar.getByLabel(name)).toHaveCount(1)
  }
  await expect(sidebar.getByLabel('내 금융')).toHaveCount(1)
  await expect(sidebar.getByLabel('설정')).toHaveCount(1)
  await expect(page.locator('.account-nav').getByLabel('설정')).toHaveCount(0)

  expect(await page.evaluate(() => getComputedStyle(document.documentElement).colorScheme)).toContain('light')

  await sidebar.getByLabel('사이드바 접기').click()
  const visibleCodes = await sidebar.locator('.workflow-code:visible').allTextContents()
  expect(visibleCodes).toEqual(['Q', 'I', 'R', 'A'])
  expect(new Set(visibleCodes).size).toBe(visibleCodes.length)
})

test('personal finance remains a separate entry and uses the shared title scale', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await mockPublicShell(page)
  await page.goto('/finance')

  await expect(page.locator('.finance-sidebar-entry')).toHaveAttribute('aria-current', 'page')
  await expect(page.getByRole('heading', { level: 1, name: /내 금융은.*AIRA 탐색과 분리합니다/ })).toBeVisible()
  await expect(page.locator('.workflow-sidebar nav')).not.toContainText('내 금융')

  const fontSize = await page.locator('.finance-page h1').evaluate(node => parseFloat(getComputedStyle(node).fontSize))
  expect(fontSize).toBeLessThanOrEqual(60)
})

test('mobile shell has one search and no horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockPublicShell(page)
  await page.goto('/')

  await expect(page.getByRole('search')).toHaveCount(1)
  const dimensions = await page.evaluate(() => ({
    viewport: innerWidth,
    scrollWidth: document.documentElement.scrollWidth,
    searchWidth: document.querySelector('.site-header input[type="search"]')?.getBoundingClientRect().width ?? 0,
  }))
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.searchWidth).toBeLessThanOrEqual(dimensions.viewport)
})
