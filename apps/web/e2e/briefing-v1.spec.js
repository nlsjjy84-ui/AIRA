import { expect, test } from '@playwright/test'

const companies = JSON.parse(process.env.AIRA_BRIEFING_COMPANIES ?? '[]')
const userA = process.env.AIRA_AUTH_E2E_USER_A
const userB = process.env.AIRA_AUTH_E2E_USER_B
const password = process.env.AIRA_AUTH_E2E_PASSWORD

async function signupAndLogin(page, nickname) {
  await page.goto('/')
  await page.getByRole('button', { name: '회원가입', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('닉네임').fill(nickname)
  await dialog.locator('#password').fill(password)
  await dialog.getByRole('button', { name: '회원가입', exact: true }).click()
  await expect(dialog.getByRole('status')).toBeVisible()
  await dialog.locator('#password').fill(password)
  await dialog.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByText(nickname)).toBeVisible()
}

async function saveCompany(page, company) {
  const picker = page.getByRole('region', { name: '기업 선택' })
  await picker.getByRole('button', { name: new RegExp(company.name) }).click()
  await page.getByRole('button', { name: '관심회사에 저장' }).click()
  await expect(page.getByRole('button', { name: '관심회사에서 삭제' })).toBeVisible()
}

async function currentBriefing(page) {
  return page.evaluate(async () => {
    const token = decodeURIComponent(document.cookie.split('; ').find(v => v.startsWith('XSRF-TOKEN='))?.split('=')[1] ?? '')
    const response = await fetch('/api/me/briefings/current', {
      method: 'POST', credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': token },
    })
    return { status: response.status, body: await response.json() }
  })
}

test('personal briefing reuses shared assessments with private ownership', async ({ browser }) => {
  test.skip(companies.length < 3 || !userA || !userB || !password, 'Briefing E2E inputs are required')
  const contextA = await browser.newContext()
  const pageA = await contextA.newPage()
  await signupAndLogin(pageA, userA)
  await saveCompany(pageA, companies[0])
  await saveCompany(pageA, companies[1])

  const briefing = pageA.getByRole('region', { name: '내 브리핑' })
  for (const company of companies.slice(0, 2)) {
    await expect(briefing.getByText(company.name, { exact: true })).toBeVisible()
  }
  await expect(briefing.getByRole('heading', { name: '확인할 의미' }).first()).toBeVisible()
  await expect(briefing.getByRole('heading', { name: '아직 확인할 점' }).first()).toBeVisible()
  await expect(briefing.getByText('OpenDART').first()).toBeVisible()
  await expect(briefing.getByText('관심회사로 저장한 회사의 AIRA 분석입니다.').first()).toBeVisible()
  await expect(briefing.getByRole('link', { name: /공식 근거 원문/ }).first()).toHaveAttribute('href', /dart\.fss\.or\.kr/)
  await expect(briefing.getByText(/매수|매도|추천|목표주가|알림/)).toHaveCount(0)

  const first = await currentBriefing(pageA)
  expect(first.status).toBe(200)
  expect(first.body.items).toHaveLength(2)
  await briefing.getByRole('button', { name: 'AIRA에서 회사 맥락 보기' }).first().click()
  const targetEvent = pageA.locator(`#event-${first.body.items[0].eventId}`)
  await expect(targetEvent).toBeVisible()
  await expect(targetEvent).toBeFocused()
  await expect(targetEvent).toHaveClass(/insight-target/)
  await pageA.reload()
  await expect(pageA.getByText(userA)).toBeVisible()
  const second = await currentBriefing(pageA)
  expect(second.body.briefingId).toBe(first.body.briefingId)
  expect(second.body.items.map(item => item.assessmentId)).toEqual(first.body.items.map(item => item.assessmentId))

  const contextB = await browser.newContext()
  const pageB = await contextB.newPage()
  await signupAndLogin(pageB, userB)
  await saveCompany(pageB, companies[2])
  const briefingB = await currentBriefing(pageB)
  expect(briefingB.body.items).toHaveLength(1)
  expect(briefingB.body.items[0].companyName).toBe(companies[2].name)
  const ownership = await pageB.evaluate(async id => (await fetch(`/api/me/briefings/${id}`)).status, first.body.briefingId)
  expect(ownership).toBe(404)

  await pageA.getByRole('button', { name: '로그아웃' }).click()
  await expect(pageA.getByRole('region', { name: '내 브리핑' })).toHaveCount(0)
  const privateStatus = await pageA.evaluate(async () => (await fetch('/api/me/briefings/current', { method: 'POST' })).status)
  expect([401, 403]).toContain(privateStatus)
  const picker = pageA.getByRole('region', { name: '기업 선택' })
  await picker.getByRole('button', { name: new RegExp(companies[0].name) }).click()
  await expect(pageA.getByRole('region', { name: '관련 사건과 확인할 의미' })).toBeVisible()

  await pageA.setViewportSize({ width: 390, height: 844 })
  await expect(pageA.locator('body')).toHaveJSProperty('scrollWidth', 390)
  await contextA.close()
  await contextB.close()
})
