import { expect, test } from '@playwright/test'

const companies = JSON.parse(process.env.AIRA_ALERT_COMPANIES ?? '[]')
const userA = process.env.AIRA_AUTH_E2E_USER_A
const userB = process.env.AIRA_AUTH_E2E_USER_B
const password = process.env.AIRA_AUTH_E2E_PASSWORD

async function signupLogin(page, nickname) {
  await page.goto('/'); await page.getByRole('button', { name: '회원가입', exact: true }).click()
  const dialog = page.getByRole('dialog'); await dialog.getByLabel('닉네임').fill(nickname)
  await dialog.locator('#password').fill(password); await dialog.getByRole('button', { name: '회원가입', exact: true }).click()
  await expect(dialog.getByRole('status')).toBeVisible(); await dialog.locator('#password').fill(password)
  await dialog.getByRole('button', { name: '로그인', exact: true }).click(); await expect(page.getByText(nickname)).toBeVisible()
}

async function save(page, company, enable = true) {
  const picker = page.getByRole('region', { name: '기업 선택' })
  await picker.getByRole('button', { name: new RegExp(company) }).click()
  await page.getByRole('button', { name: '관심회사에 저장' }).click()
  await expect(page.getByRole('button', { name: '앱 알림 켜기' })).toBeVisible()
  if (enable) { await page.getByRole('button', { name: '앱 알림 켜기' }).click(); await expect(page.getByRole('button', { name: '앱 알림 끄기' })).toBeVisible() }
}

async function reconcile(page) {
  return page.evaluate(async () => {
    const token = decodeURIComponent(document.cookie.split('; ').find(v => v.startsWith('XSRF-TOKEN='))?.split('=')[1] ?? '')
    const response = await fetch('/api/me/alerts/reconcile', { method: 'POST', credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': token } })
    return { status: response.status, body: await response.json() }
  })
}

test('in-app alerts reconcile new shared assessments with private ownership', async ({ browser }) => {
  test.skip(companies.length < 3 || !userA || !userB || !password, 'Alert E2E inputs required')
  const contextA = await browser.newContext(); const pageA = await contextA.newPage(); await signupLogin(pageA, userA)
  await save(pageA, companies[0]); await save(pageA, companies[1]); await save(pageA, companies[2], false)
  const first = await reconcile(pageA); expect(first.status).toBe(200); expect(first.body.alerts).toHaveLength(2)
  const alerts = pageA.getByRole('region', { name: '관심회사 알림' })
  for (const company of companies.slice(0, 2)) await expect(alerts.getByText(company, { exact: true })).toBeVisible()
  await expect(alerts.getByText('AIRA E2E TEST').first()).toBeVisible()
  await expect(alerts.getByText('앱 알림을 켠 관심회사에 새로운 AIRA 분석이 준비되었습니다.').first()).toBeVisible()
  await expect(alerts.getByRole('link', { name: /공식 근거 원문/ }).first()).toBeVisible()
  await alerts.getByRole('button', { name: 'AIRA에서 회사 맥락 보기' }).first().click()
  const targetEvent = pageA.locator(`#event-${first.body.alerts[0].eventId}`)
  await expect(targetEvent).toBeVisible()
  await expect(targetEvent).toBeFocused()
  await expect(targetEvent).toHaveClass(/insight-target/)
  await expect(alerts.getByText(/매수|매도|추천|목표주가|실시간|즉시/)).toHaveCount(0)
  const second = await reconcile(pageA); expect(second.body.alerts.map(a => a.alertId)).toEqual(first.body.alerts.map(a => a.alertId))
  await pageA.reload(); await expect(pageA.getByText(userA)).toBeVisible(); expect((await reconcile(pageA)).body.alerts).toHaveLength(2)

  const contextB = await browser.newContext(); const pageB = await contextB.newPage(); await signupLogin(pageB, userB)
  await save(pageB, companies[2]); const own = await reconcile(pageB); expect(own.body.alerts).toHaveLength(1)
  expect(own.body.alerts[0].companyName).toBe(companies[2])
  const forbidden = await pageB.evaluate(async id => (await fetch(`/api/me/alerts/${id}`)).status, first.body.alerts[0].alertId)
  expect(forbidden).toBe(404)

  await pageA.getByRole('button', { name: '로그아웃' }).click(); await expect(pageA.getByRole('region', { name: '관심회사 알림' })).toHaveCount(0)
  expect([401, 403]).toContain(await pageA.evaluate(async () => (await fetch('/api/me/alerts')).status))
  const picker = pageA.getByRole('region', { name: '기업 선택' }); await picker.getByRole('button', { name: new RegExp(companies[0]) }).click()
  await expect(pageA.getByRole('region', { name: '관련 사건과 확인할 의미' })).toBeVisible()
  await pageA.setViewportSize({ width: 390, height: 844 }); await expect(pageA.locator('body')).toHaveJSProperty('scrollWidth', 390)
  await contextA.close(); await contextB.close()
})
