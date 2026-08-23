import { expect, test } from '@playwright/test'

const companies = JSON.parse(process.env.AIRA_EVENT_COMPANIES ?? '[]')
const nickname = process.env.AIRA_AUTH_E2E_USER_A
const password = process.env.AIRA_AUTH_E2E_PASSWORD

async function verifyExperience(page, company) {
  const companyPicker = page.getByRole('region', { name: '기업 선택' })
  await companyPicker.getByRole('button', { name: new RegExp(company.name) }).click()
  const events = page.getByRole('region', { name: '관련 사건과 확인할 의미' })
  await expect(events.getByRole('heading', { name: new RegExp(`${company.name}.*공식 공시`) })).toBeVisible()
  await expect(events.getByRole('heading', { name: 'AIRA가 확인한 의미' })).toBeVisible()
  await expect(events.getByRole('heading', { name: '아직 확인할 점' })).toBeVisible()
  await expect(events.getByText(/향후 실적이나 시장 영향을 판단할 수 없/)).toBeVisible()
  await expect(events.getByText('OpenDART')).toBeVisible()
  await expect(events.getByText(new RegExp(company.filingId))).toBeVisible()
  await expect(events.getByRole('link', { name: /근거 원문 확인/ })).toHaveAttribute('href', company.officialUrl)
  await expect(events.getByText(/매수|매도|추천|목표주가/)).toHaveCount(0)
}

test('public event assessment remains company-scoped across interest return', async ({ page }) => {
  test.skip(companies.length < 3 || !nickname || !password, 'Event assessment E2E inputs are required')
  await page.goto('/')
  for (const company of companies) await verifyExperience(page, company)

  await page.getByRole('button', { name: '회원가입', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('닉네임').fill(nickname)
  await dialog.locator('#password').fill(password)
  await dialog.getByRole('button', { name: '회원가입', exact: true }).click()
  await expect(dialog.getByText(/회원가입이 완료됐습니다/)).toBeVisible()
  await dialog.locator('#password').fill(password)
  await dialog.getByRole('button', { name: '로그인', exact: true }).click()
  await verifyExperience(page, companies[0])
  await page.getByRole('button', { name: '관심회사에 저장' }).click()
  await page.reload()
  await expect(page.getByText(nickname)).toBeVisible()
  const interests = page.getByRole('region', { name: '내 관심회사' })
  await interests.getByRole('button', { name: new RegExp(companies[0].name) }).click()
  await verifyExperience(page, companies[0])
  await page.getByRole('button', { name: '로그아웃' }).click()
  await verifyExperience(page, companies[0])
  await expect(page.getByRole('region', { name: '내 관심회사' })).toHaveCount(0)
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('body')).toHaveJSProperty('scrollWidth', 390)
})
