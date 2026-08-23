import { expect, test } from '@playwright/test'

const companyId = '5eafc0b5-c163-4cea-8dbd-131265004e95'
const password = process.env.AIRA_AUTH_E2E_PASSWORD
const userA = process.env.AIRA_AUTH_E2E_USER_A
const userB = process.env.AIRA_AUTH_E2E_USER_B

async function signupAndLogin(page, nickname) {
  await page.goto('/')
  await expect(page.getByRole('button', { name: /삼성전자/ })).toBeVisible()
  await page.getByRole('button', { name: '회원가입' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('닉네임').fill(nickname)
  await dialog.locator('#password').fill(password)
  await dialog.getByRole('button', { name: '회원가입' }).click()
  await expect(dialog.getByText(/회원가입이 완료됐습니다/)).toBeVisible()
  await dialog.locator('#password').fill(password)
  await dialog.getByRole('button', { name: '로그인' }).click()
  await expect(page.getByText(nickname)).toBeVisible()
  await expect(page.getByRole('heading', { name: '내 관심회사' })).toBeVisible()
}

async function logout(page) {
  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page.getByText(/로그아웃되었습니다/)).toBeVisible()
  await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeVisible()
}

test('signup, session return, interest isolation, removal, and public fallback', async ({ browser }) => {
  test.skip(!userA || !userB || !password, 'E2E user inputs are required')
  const contextA = await browser.newContext()
  const contextB = await browser.newContext()
  const pageA = await contextA.newPage()
  const pageB = await contextB.newPage()

  await signupAndLogin(pageA, userA)
  await pageA.getByRole('button', { name: /삼성전자/ }).click()
  await expect(pageA.getByLabel('정확한 보고 기간')).toHaveValue('2025-01-01|2025-12-31')
  await expect(pageA.getByText('333,605,938,000,000')).toBeVisible()
  await expect(pageA.getByText('43,601,051,000,000')).toBeVisible()
  await expect(pageA.getByText('OpenDART')).toHaveCount(1)
  await expect(pageA.getByText(/공시 식별자 20260310002820/)).toBeVisible()
  await expect(pageA.getByRole('link', { name: /원문 확인/ })).toHaveAttribute('href', /dart\.fss\.or\.kr/)
  await pageA.getByRole('button', { name: '관심회사에 저장' }).click()
  await expect(pageA.getByRole('button', { name: '관심회사에서 삭제' })).toBeVisible()

  await pageA.reload()
  await expect(pageA.getByText(userA)).toBeVisible()
  const interestsA = pageA.getByRole('region', { name: '내 관심회사' })
  await expect(interestsA.getByRole('button', { name: /삼성전자.*재무정보 다시 보기/ })).toBeVisible()
  await interestsA.getByRole('button', { name: /삼성전자.*재무정보 다시 보기/ }).click()
  await expect(pageA.getByText('333,605,938,000,000')).toBeVisible()

  await signupAndLogin(pageB, userB)
  const interestsB = pageB.getByRole('region', { name: '내 관심회사' })
  await expect(interestsB.getByText(/아직 저장한 관심회사가 없습니다/)).toBeVisible()
  const deleteStatus = await pageB.evaluate(async (id) => {
    const token = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='))?.split('=')[1]
    const response = await fetch(`/api/me/interests/${id}`, {
      method: 'DELETE', headers: token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {},
    })
    return response.status
  }, companyId)
  expect(deleteStatus).toBe(204)

  await pageA.reload()
  const reloadedA = pageA.getByRole('region', { name: '내 관심회사' })
  await expect(reloadedA.getByRole('button', { name: /삼성전자.*재무정보 다시 보기/ })).toBeVisible()
  await reloadedA.getByRole('button', { name: /삼성전자.*재무정보 다시 보기/ }).click()
  await pageA.getByRole('button', { name: '관심회사에서 삭제' }).click()
  await expect(pageA.getByText(/아직 저장한 관심회사가 없습니다/)).toBeVisible()
  await logout(pageA)
  await expect(pageA.getByText('333,605,938,000,000')).toBeVisible()

  await logout(pageB)
  await pageB.getByRole('button', { name: /삼성전자/ }).click()
  await expect(pageB.getByText('333,605,938,000,000')).toBeVisible()
  await pageB.setViewportSize({ width: 390, height: 844 })
  await expect(pageB.locator('body')).toHaveJSProperty('scrollWidth', 390)

  await contextA.close()
  await contextB.close()
})
