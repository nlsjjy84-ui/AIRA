import { expect, test } from '@playwright/test'

const briefingItems = Array.from({ length: 8 }, (_, i) => ({ assessmentId: `A-${i}`, eventId: `E-${i}`, eventTitle: `공식 사건 ${i + 1}`, eventType: 'EARNINGS', occurredAt: '2026-09-20T00:00:00Z', analysisVersion: 'v1', summary: '확인된 사실과 해석을 분리해 보여주는 설명입니다.', uncertainty: '추가 확인이 필요한 점입니다.', companies: [{ companyId: `C-${i}`, companyName: `회사 ${i + 1}` }], evidence: [] }))
const alertItems = Array.from({ length: 8 }, (_, i) => ({ alertId: `AL-${i}`, assessmentId: `A-${i}`, eventId: `E-${i}`, eventTitle: `알림 사건 ${i + 1}`, summary: '새 분석이 준비되었습니다.', uncertainty: '추가 확인 필요', occurredAt: '2026-09-20T00:00:00Z', completedAt: '2026-09-20T01:00:00Z', sentAt: '2026-09-20T02:00:00Z', companies: [{ companyId: `C-${i}`, companyName: `회사 ${i + 1}` }] }))

async function mockLongHome(page) {
  await page.route('**/api/me', route => route.fulfill({ status: 200, json: { userId: 'U1', nickname: 'ScrollUser' } }))
  await page.route('**/api/me/interests', route => route.fulfill({ status: 200, json: Array.from({ length: 6 }, (_, i) => ({ entityId: `C-${i}`, canonicalName: `회사 ${i + 1}`, alertEnabled: true })) }))
  await page.route('**/api/me/briefings/current', route => route.fulfill({ status: 200, json: { status: 'AVAILABLE', periodStart: '2026-09-01T00:00:00Z', periodEnd: '2026-09-21T00:00:00Z', generatedAt: '2026-09-21T00:00:00Z', items: briefingItems } }))
  await page.route('**/api/me/alerts/reconcile', route => route.fulfill({ status: 200, json: { alerts: alertItems, emptyReason: null } }))
  await page.route('**/api/market-indices/latest', route => route.fulfill({ status: 200, json: { indices: [
    { marketCode: 'KOSPI', state: 'AVAILABLE', tradingDate: '2026-09-18', close: 3123.45, change: -12.3, changeRate: -0.39, evidenceId: 'E-KOSPI', sourceName: 'KRX Data Marketplace Open API' },
    { marketCode: 'KOSDAQ', state: 'AVAILABLE', tradingDate: '2026-09-18', close: 987.65, change: 4.2, changeRate: 0.43, evidenceId: 'E-KOSDAQ', sourceName: 'KRX Data Marketplace Open API' },
  ] } }))
  await page.route('**/api/events', route => route.fulfill({ status: 200, json: { events: Array.from({ length: 5 }, (_, i) => ({ eventId: `EV-${i}`, eventType: 'DISCLOSURE', title: `공식 사건 ${i + 1}`, occurredAt: `2026-09-${String(18 - i).padStart(2, '0')}T00:00:00Z`, companies: [{ companyId: `EC-${i}`, companyName: `사건회사 ${i + 1}` }] })) } }))
}

for (const [name, width, height] of [['desktop', 1440, 900], ['mobile', 390, 844]]) test(`long MAIN remains vertically scrollable on ${name}`, async ({ page }) => {
  await page.setViewportSize({ width, height }); await mockLongHome(page); await page.goto('/');
  await expect(page.locator('.market-index-item')).toHaveCount(2); await expect(page.locator('.recent-event-timeline li')).toHaveCount(5); await expect(page.locator('.briefing-card')).toHaveCount(8); await expect(page.locator('.alert-card')).toHaveCount(8)
  const before = await page.evaluate(() => ({ scrollHeight: document.documentElement.scrollHeight, clientHeight: document.documentElement.clientHeight }))
  expect(before.scrollHeight).toBeGreaterThan(before.clientHeight); expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
  for (let i = 0; i < 30; i += 1) await page.mouse.wheel(0, 1000)
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(0)
  const end = await page.evaluate(() => ({ y: window.scrollY, max: document.documentElement.scrollHeight - document.documentElement.clientHeight }))
  expect(Math.abs(end.max - end.y)).toBeLessThan(5)
})
