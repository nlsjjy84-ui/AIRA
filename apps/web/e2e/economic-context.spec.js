import { expect, test } from '@playwright/test'

const gdp = { state: 'AVAILABLE', reason: null, seriesName: '실질 국내총생산',
  unitName: '십억원', sourceName: '한국은행 ECOS', observations: [
    { period: '2025Q2', value: 617220.6, evidenceId: 'ecos-evidence' },
    { period: '2025Q1', value: 614115.3, evidenceId: 'ecos-evidence' },
  ] }

test('MAIN presents exact ECOS observations and remains scrollable at 390px', async ({ page }) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (!path.startsWith('/api/')) return route.fallback()
    const bodies = {
      '/api/me': {}, '/api/market-indices/latest': { indices: [] },
      '/api/economic-indicators/real-gdp/latest': gdp,
      '/api/events/recent': { state: 'NO_DATA', events: [] },
      '/api/market-news/latest': { state: 'NO_DATA', articles: [] },
    }
    await route.fulfill({ status: path in bodies ? (path === '/api/me' ? 401 : 200) : 404,
      json: bodies[path] ?? {} })
  })

  await page.goto('/')
  const heading = page.getByRole('heading', { name: '한국 경제의 분기별 실질 GDP' })
  await expect(heading).toBeVisible()
  const [headingBox, sidebarBox] = await Promise.all([
    heading.boundingBox(), page.locator('.workflow-sidebar').boundingBox(),
  ])
  expect(headingBox.x).toBeGreaterThanOrEqual(sidebarBox.x + sidebarBox.width)
  const board = heading.locator('xpath=ancestor::section[1]')
  await expect(board).toContainText('2025년 2분기')
  await expect(board).toContainText('617,220.6')
  await expect(board).toContainText('직전 분기 대비 +3,105.3')
  await page.screenshot({ path: 'test-results/economic-context-desktop.png', fullPage: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('html')).toHaveJSProperty('scrollWidth', 390)
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
  await expect.poll(() => page.evaluate(() => Math.ceil(window.scrollY + window.innerHeight)))
    .toBeGreaterThanOrEqual(await page.evaluate(() => document.documentElement.scrollHeight))
  await page.screenshot({ path: 'test-results/economic-context-mobile.png', fullPage: true })
  expect(errors).toEqual([])
})
