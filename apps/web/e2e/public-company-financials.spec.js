import { expect, test } from '@playwright/test'

test('anonymous user browses Samsung financials and official provenance', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /공식 데이터와 근거/ })).toBeVisible()
  await page.getByRole('button', { name: /삼성전자/ }).click()
  await expect(page.getByLabel('정확한 보고 기간')).toHaveValue('2025-01-01|2025-12-31')
  await expect(page.getByText('333,605,938,000,000')).toBeVisible()
  await expect(page.getByText('43,601,051,000,000')).toBeVisible()
  await expect(page.getByText('OpenDART')).toHaveCount(1)
  await expect(page.getByText(/공시 식별자 20260310002820/)).toBeVisible()
  await expect(page.getByRole('link', { name: /원문 확인/ })).toHaveAttribute(
    'href', /dart\.fss\.or\.kr/,
  )

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('body')).toHaveJSProperty('scrollWidth', 390)
})
