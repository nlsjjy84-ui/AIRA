import { expect, test } from '@playwright/test'
import { execFileSync } from 'node:child_process'

const companies = JSON.parse(process.env.AIRA_BRIEFING_COMPANIES ?? '[]')
const userA = process.env.AIRA_AUTH_E2E_USER_A
const userB = process.env.AIRA_AUTH_E2E_USER_B
const password = process.env.AIRA_AUTH_E2E_PASSWORD
const psql = process.env.AIRA_E2E_PSQL

function activateFixtureInterests(nickname, selectedCompanies) {
  if (!psql) throw new Error('AIRA_E2E_PSQL is required')
  const ids = selectedCompanies.map(company => `'${company.id.replaceAll("'", "''")}'`).join(',')
  const safeNickname = nickname.replaceAll("'", "''").toLowerCase()
  execFileSync(psql, ['--host', '127.0.0.1', '--port', '5432', '--username', 'postgres',
    '--dbname', 'aira', '--set', 'ON_ERROR_STOP=1', '--command',
    `UPDATE user_interest ui SET created_at=a.completed_at-interval '1 second', updated_at=a.completed_at-interval '1 second'
     FROM app_user u, event_entity ee, assessment a
     WHERE ui.user_id=u.id AND u.nickname_normalized='${safeNickname}'
       AND ui.entity_id IN (${ids}) AND ee.entity_id=ui.entity_id
       AND a.event_id=ee.event_id AND a.status='COMPLETED'`], { stdio: 'ignore' })
}

function insertSuccessor(item) {
  const predecessor = item.assessmentId.replaceAll("'", "''")
  const eventId = item.eventId.replaceAll("'", "''")
  const evidenceId = item.evidence[0].evidenceId.replaceAll("'", "''")
  execFileSync(psql, ['--host', '127.0.0.1', '--port', '5432', '--username', 'postgres',
    '--dbname', 'aira', '--set', 'ON_ERROR_STOP=1', '--command',
    `WITH inserted AS (
       INSERT INTO assessment(id,event_id,analysis_version,method,importance,summary,confidence,
         uncertainty,time_horizon,status,input_fingerprint,completed_at,supersedes_assessment_id)
       VALUES('96000000-0000-0000-0000-000000000001','${eventId}','briefing-e2e-v2','RULE',
         'HIGH','Successor assessment','HIGH','Successor uncertainty','SHORT_TERM','COMPLETED',
         decode('21','hex'),CURRENT_TIMESTAMP,'${predecessor}') RETURNING id)
     INSERT INTO assessment_evidence(assessment_id,evidence_id,usage_type)
     SELECT id,'${evidenceId}','SUPPORTS' FROM inserted`], { stdio: 'ignore' })
}

async function deleteInterest(page, companyId) {
  return page.evaluate(async id => {
    const token = decodeURIComponent(document.cookie.split('; ').find(v => v.startsWith('XSRF-TOKEN='))?.split('=')[1] ?? '')
    return (await fetch(`/api/me/interests/${id}`, {
      method: 'DELETE', credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': token },
    })).status
  }, companyId)
}

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
  const briefing = pageA.getByRole('region', { name: '내 브리핑' })
  await expect(briefing.getByText('아직 관심 회사가 없습니다.')).toBeVisible()
  await saveCompany(pageA, companies[0])
  await expect(briefing.getByText('이 Briefing 기간에 새로 정리된 변화가 없습니다.')).toBeVisible()
  await saveCompany(pageA, companies[1])
  activateFixtureInterests(userA, companies.slice(0, 2))
  await pageA.reload()
  await expect(pageA.getByText(userA)).toBeVisible()

  for (const company of companies.slice(0, 2)) {
    await expect(briefing.getByText(`관련 회사: ${company.name}`, { exact: true })).toBeVisible()
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
  expect(first.body.items.every(item => item.analysisVersion && item.completedAt)).toBe(true)
  expect(first.body.items.every(item => item.evidence.length > 0
    && item.evidence.every(reference => reference.evidenceId))).toBe(true)
  const firstEvidence = first.body.items[0].evidence[0]
  const evidenceRequest = pageA.waitForResponse(response => response.url().endsWith(`/api/evidence/${firstEvidence.evidenceId}`))
  await briefing.getByRole('button', { name: '공식 자료 상세' }).first().click()
  expect((await evidenceRequest).status()).toBe(200)
  await expect(pageA.getByRole('region', { name: '공식 자료', exact: true })
    .getByText(`문서 식별자 ${firstEvidence.externalId}`, { exact: true })).toBeVisible()

  insertSuccessor(first.body.items[0])
  const historicalAfterSuccessor = await pageA.evaluate(async id => {
    const response = await fetch(`/api/me/briefings/${id}`)
    return { status: response.status, body: await response.json() }
  }, first.body.briefingId)
  expect(historicalAfterSuccessor.status).toBe(200)
  expect(historicalAfterSuccessor.body.items.map(item => item.assessmentId))
    .toEqual(first.body.items.map(item => item.assessmentId))
  expect(await deleteInterest(pageA, companies[0].id)).toBe(204)
  const historicalAfterInterestDelete = await pageA.evaluate(async id =>
    (await fetch(`/api/me/briefings/${id}`)).json(), first.body.briefingId)
  expect(historicalAfterInterestDelete.items.map(item => item.assessmentId))
    .toEqual(first.body.items.map(item => item.assessmentId))
  await pageA.reload()
  await expect(pageA.getByText(userA)).toBeVisible()
  const second = await currentBriefing(pageA)
  expect(second.body.briefingId).toBe(first.body.briefingId)
  expect(second.body.items.map(item => item.assessmentId)).toEqual(first.body.items.map(item => item.assessmentId))

  const contextB = await browser.newContext()
  const pageB = await contextB.newPage()
  await signupAndLogin(pageB, userB)
  await saveCompany(pageB, companies[2])
  activateFixtureInterests(userB, companies.slice(2, 3))
  const briefingB = await currentBriefing(pageB)
  expect(briefingB.body.items).toHaveLength(1)
  expect(briefingB.body.items[0].companies.map(company => company.companyName)).toContain(companies[2].name)
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
