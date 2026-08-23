import { expect, test } from '@playwright/test'

const password = process.env.AIRA_AUTH_E2E_PASSWORD
const userA = process.env.AIRA_AUTH_E2E_USER_A
const userB = process.env.AIRA_AUTH_E2E_USER_B
const companies = JSON.parse(process.env.AIRA_MULTI_COMPANY_ORACLE ?? '[]')

async function signupAndLogin(page, nickname) {
  await page.goto('/')
  await expect(page.getByRole('button', { name: new RegExp(companies[0].name) })).toBeVisible()
  await page.getByRole('button', { name: '회원가입', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('닉네임').fill(nickname)
  await dialog.locator('#password').fill(password)
  await dialog.getByRole('button', { name: '회원가입', exact: true }).click()
  await expect(dialog.getByText(/회원가입이 완료됐습니다/)).toBeVisible()
  await dialog.locator('#password').fill(password)
  await dialog.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByText(nickname)).toBeVisible()
  await expect(page.getByRole('region', { name: '내 관심회사' })).toBeVisible()
}

async function openAndVerify(page, company) {
  await page.getByRole('button', { name: new RegExp(company.name) }).click()
  await expect(page.getByLabel('정확한 보고 기간')).toHaveValue(`${company.periodStart}|${company.periodEnd}`)
  await expect(page.getByText(company.revenue)).toBeVisible()
  await expect(page.getByText(company.operatingIncome)).toBeVisible()
  await expect(page.getByText('OpenDART')).toHaveCount(1)
  await expect(page.getByText(new RegExp(`공시 식별자 ${company.filingId}`))).toBeVisible()
  await expect(page.getByRole('link', { name: /원문 확인/ })).toHaveAttribute('href', company.officialUrl)
}

async function logout(page) {
  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page.getByText(/로그아웃되었습니다/)).toBeVisible()
  await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeVisible()
}

test('multi-company return, isolation, selective removal, and public fallback', async ({ browser }) => {
  test.skip(!userA || !userB || !password || companies.length < 3, 'Multi-company E2E inputs are required')
  const contextA = await browser.newContext()
  const contextB = await browser.newContext()
  const pageA = await contextA.newPage()
  const pageB = await contextB.newPage()

  await signupAndLogin(pageA, userA)
  for (const company of companies) {
    await openAndVerify(pageA, company)
    for (const other of companies.filter(item => item.companyId !== company.companyId)) {
      expect(await pageA.getByText(other.revenue).count()).toBe(0)
      expect(await pageA.getByText(other.operatingIncome).count()).toBe(0)
    }
    await pageA.getByRole('button', { name: '관심회사에 저장' }).click()
    await expect(pageA.getByRole('button', { name: '관심회사에서 삭제' })).toBeVisible()
  }

  const interestsA = pageA.getByRole('region', { name: '내 관심회사' })
  for (const company of companies) {
    await expect(interestsA.getByRole('button', { name: new RegExp(`${company.name}.*재무정보 다시 보기`) })).toBeVisible()
  }
  await pageA.reload()
  await expect(pageA.getByText(userA)).toBeVisible()
  const restoredA = pageA.getByRole('region', { name: '내 관심회사' })
  for (const company of companies) {
    await restoredA.getByRole('button', { name: new RegExp(`${company.name}.*재무정보 다시 보기`) }).click()
    await expect(pageA.getByText(company.revenue)).toBeVisible()
  }

  await signupAndLogin(pageB, userB)
  const interestsB = pageB.getByRole('region', { name: '내 관심회사' })
  await expect(interestsB.getByText(/아직 저장한 관심회사가 없습니다/)).toBeVisible()
  const deleteStatus = await pageB.evaluate(async (id) => {
    const token = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='))?.split('=')[1]
    const response = await fetch(`/api/me/interests/${id}`, {
      method: 'DELETE', headers: token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {},
    })
    return response.status
  }, companies[1].companyId)
  expect(deleteStatus).toBe(204)

  await pageA.reload()
  const unchangedA = pageA.getByRole('region', { name: '내 관심회사' })
  for (const company of companies) await expect(unchangedA.getByRole('button', { name: new RegExp(company.name) })).toBeVisible()
  await unchangedA.getByRole('button', { name: new RegExp(companies[1].name) }).click()
  await pageA.getByRole('button', { name: '관심회사에서 삭제' }).click()
  const remainingA = pageA.getByRole('region', { name: '내 관심회사' })
  await expect(remainingA.getByRole('button', { name: new RegExp(companies[1].name) })).toHaveCount(0)
  await expect(remainingA.getByRole('button', { name: new RegExp(companies[0].name) })).toBeVisible()
  await expect(remainingA.getByRole('button', { name: new RegExp(companies[2].name) })).toBeVisible()

  await logout(pageA)
  await openAndVerify(pageA, companies[0])
  await expect(pageA.getByRole('region', { name: '내 관심회사' })).toHaveCount(0)
  await logout(pageB)
  await openAndVerify(pageB, companies[2])
  await pageB.setViewportSize({ width: 390, height: 844 })
  await expect(pageB.locator('body')).toHaveJSProperty('scrollWidth', 390)
  await contextA.close()
  await contextB.close()
})
