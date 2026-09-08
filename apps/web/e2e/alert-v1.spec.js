import { expect, test } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'

const companies = JSON.parse(process.env.AIRA_ALERT_COMPANIES ?? '[]')
const userA = process.env.AIRA_AUTH_E2E_USER_A
const userB = process.env.AIRA_AUTH_E2E_USER_B
const password = process.env.AIRA_AUTH_E2E_PASSWORD
const psql = process.env.AIRA_E2E_PSQL

function literal(value) {
  return String(value).replaceAll("'", "''")
}

function executeSql(command, capture = false) {
  if (!psql) throw new Error('AIRA_E2E_PSQL is required')
  return execFileSync(psql, ['--host', '127.0.0.1', '--port', '5432',
    '--username', 'postgres', '--dbname', 'aira', '--set', 'ON_ERROR_STOP=1',
    '--no-psqlrc', '--tuples-only', '--no-align', '--field-separator', '\t',
    '--command', command], {
    encoding: 'utf8',
    stdio: capture ? ['ignore', 'pipe', 'pipe'] : 'ignore',
  })
}

function currentTerminal(companyId) {
  const query = [
    'SELECT a.id,a.event_id,string_agg(ae.evidence_id::text,\',\' ORDER BY ae.evidence_id)',
    'FROM assessment a',
    "JOIN event ev ON ev.id=a.event_id AND ev.status='CONFIRMED'",
    'JOIN event_entity ee ON ee.event_id=ev.id',
    'JOIN assessment_evidence ae ON ae.assessment_id=a.id',
    "WHERE ee.entity_id='" + literal(companyId) + "' AND a.status='COMPLETED'",
    'AND NOT EXISTS (SELECT 1 FROM assessment successor',
    "  WHERE successor.event_id=a.event_id AND successor.status='COMPLETED'",
    '    AND successor.supersedes_assessment_id=a.id)',
    'GROUP BY a.id,a.event_id',
    'ORDER BY a.event_id,a.id',
    'LIMIT 1',
  ].join(' ')
  const row = executeSql(query, true).trim()
  if (!row) throw new Error('No terminal Assessment fixture found')
  const [assessmentId, eventId, evidenceIds] = row.split('\t')
  return { assessmentId, eventId, evidenceIds: evidenceIds.split(',') }
}

function insertSuccessor(predecessor, label, createdAssessments) {
  const assessmentId = randomUUID()
  const fingerprint = randomBytes(32).toString('hex')
  const sql = [
    'WITH inserted AS (',
    'INSERT INTO assessment(id,event_id,analysis_version,method,importance,summary,confidence,',
    'uncertainty,time_horizon,status,input_fingerprint,completed_at,',
    'supersedes_assessment_id,created_at,updated_at)',
    "VALUES('" + assessmentId + "','" + literal(predecessor.eventId) + "',",
    "'alert-e2e-" + literal(label) + "','RULE','HIGH','Alert E2E " + literal(label) + "',",
    "'HIGH','Alert E2E uncertainty " + literal(label) + "','SHORT_TERM','COMPLETED',",
    "decode('" + fingerprint + "','hex'),CURRENT_TIMESTAMP,",
    "'" + literal(predecessor.assessmentId) + "',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
    'RETURNING id)',
    "INSERT INTO assessment_evidence(assessment_id,evidence_id,usage_type)",
    "SELECT inserted.id,ae.evidence_id,'SUPPORTS' FROM inserted",
    "JOIN assessment_evidence ae ON ae.assessment_id='" + literal(predecessor.assessmentId) + "'",
  ].join(' ')
  executeSql(sql)
  createdAssessments.push(assessmentId)
  return { assessmentId, eventId: predecessor.eventId, evidenceIds: predecessor.evidenceIds }
}

function cleanupFixtures(createdAssessments) {
  const nicknames = [userA, userB].filter(Boolean)
    .map(value => "'" + literal(value.toLowerCase()) + "'").join(',')
  if (nicknames) executeSql('DELETE FROM app_user WHERE nickname_normalized IN (' + nicknames + ')')
  for (const assessmentId of [...createdAssessments].reverse()) {
    executeSql("DELETE FROM assessment_evidence WHERE assessment_id='" + literal(assessmentId)
      + "'; DELETE FROM assessment WHERE id='" + literal(assessmentId) + "'")
  }
}

async function signupLogin(page, nickname) {
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

async function selectCompany(page, company) {
  const picker = page.getByRole('region', { name: '기업 선택' })
  await picker.getByRole('button', { name: new RegExp(company.name) }).click()
}

async function saveCompany(page, company) {
  await selectCompany(page, company)
  await page.getByRole('button', { name: '관심회사에 저장' }).click()
  await expect(page.getByRole('button', { name: '관심회사에서 삭제' })).toBeVisible()
}

async function reconcile(page) {
  return page.evaluate(async () => {
    const token = decodeURIComponent(document.cookie.split('; ')
      .find(value => value.startsWith('XSRF-TOKEN='))?.split('=')[1] ?? '')
    const response = await fetch('/api/me/alerts/reconcile', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'X-XSRF-TOKEN': token },
    })
    return { status: response.status, body: await response.json() }
  })
}

test('assessment alerts preserve activation, successors, history, evidence, and ownership', async ({ browser }) => {
  test.setTimeout(120_000)
  test.skip(companies.length < 1 || !userA || !userB || !password || !psql,
    'Alert E2E inputs are required')
  const company = companies[0]
  const createdAssessments = []
  let contextA
  let contextB
  try {
    contextA = await browser.newContext()
    const pageA = await contextA.newPage()
    await signupLogin(pageA, userA)
    const alertsRegion = pageA.getByRole('region', { name: '관심회사 알림' })
    await expect(alertsRegion.getByText('아직 관심 회사가 없습니다.')).toBeVisible()

    await saveCompany(pageA, company)
    await expect(alertsRegion.getByText('아직 알림을 켠 관심 회사가 없습니다.')).toBeVisible()
    expect((await reconcile(pageA)).body.emptyReason).toBe('NO_ALERT_ENABLED_INTERESTS')

    const preOptIn = currentTerminal(company.id)
    await pageA.getByRole('button', { name: '앱 알림 켜기' }).click()
    await expect(pageA.getByRole('button', { name: '앱 알림 끄기' })).toBeVisible()
    const noRetroactive = await reconcile(pageA)
    expect(noRetroactive.status).toBe(200)
    expect(noRetroactive.body.alerts).toHaveLength(0)
    expect(noRetroactive.body.emptyReason).toBe('NO_ELIGIBLE_ASSESSMENTS')

    const a1 = insertSuccessor(preOptIn, 'A1', createdAssessments)
    const first = await reconcile(pageA)
    expect(first.body.alerts).toHaveLength(1)
    expect(first.body.alerts[0].assessmentId).toBe(a1.assessmentId)
    expect(first.body.alerts[0].evidence.every(reference => reference.evidenceId)).toBe(true)
    const alertA1 = first.body.alerts[0]
    await pageA.reload()
    await expect(pageA.getByText(userA)).toBeVisible()
    const a1Card = alertsRegion.locator('article.alert-card').filter({ hasText: 'Alert E2E A1' })
    await expect(a1Card).toBeVisible()

    const a2 = insertSuccessor(a1, 'A2', createdAssessments)
    const second = await reconcile(pageA)
    expect(second.body.alerts).toHaveLength(2)
    expect(second.body.alerts.filter(item => item.assessmentId === a2.assessmentId)).toHaveLength(1)
    expect(second.body.alerts.filter(item => item.assessmentId === a1.assessmentId)).toHaveLength(1)
    expect((await reconcile(pageA)).body.alerts.map(item => item.alertId))
      .toEqual(second.body.alerts.map(item => item.alertId))

    await pageA.reload()
    await expect(pageA.getByText(userA)).toBeVisible()
    const historicalCard = alertsRegion.locator('article.alert-card')
      .filter({ hasText: 'Alert E2E A1' })
    await historicalCard.getByRole('button', { name: '알림 상세 보기' }).click()
    const exactDetail = pageA.locator('article.event-detail')
      .filter({ has: pageA.getByRole('heading', { name: '정확한 알림 상세' }) })
    await expect(exactDetail.getByText('Assessment ID' + a1.assessmentId)).toBeVisible()
    await expect(exactDetail).toContainText('alert-e2e-A1')
    await expect(exactDetail).not.toContainText('alert-e2e-A2')
    const historicalResponse = await pageA.evaluate(async id => {
      const response = await fetch('/api/me/alerts/' + id)
      return { status: response.status, body: await response.json() }
    }, alertA1.alertId)
    expect(historicalResponse.status).toBe(200)
    expect(historicalResponse.body.assessmentId).toBe(a1.assessmentId)
    expect(historicalResponse.body.evidence.map(reference => reference.evidenceId))
      .toEqual(alertA1.evidence.map(reference => reference.evidenceId))

    const firstEvidence = alertA1.evidence[0]
    const evidenceRequest = pageA.waitForResponse(response =>
      response.url().endsWith('/api/evidence/' + firstEvidence.evidenceId))
    await exactDetail.getByRole('button', { name: '공식 자료 상세' }).first().click()
    expect((await evidenceRequest).status()).toBe(200)
    await expect(pageA.getByRole('region', { name: '공식 자료', exact: true })
      .getByText('문서 식별자 ' + firstEvidence.externalId, { exact: true })).toBeVisible()

    await selectCompany(pageA, company)
    await pageA.getByRole('button', { name: '앱 알림 끄기' }).click()
    await expect(pageA.getByRole('button', { name: '앱 알림 켜기' })).toBeVisible()
    const a3Disabled = insertSuccessor(a2, 'A3-disabled', createdAssessments)
    expect((await reconcile(pageA)).body.alerts).toHaveLength(2)
    await pageA.getByRole('button', { name: '앱 알림 켜기' }).click()
    await expect(pageA.getByRole('button', { name: '앱 알림 끄기' })).toBeVisible()
    expect((await reconcile(pageA)).body.alerts).toHaveLength(2)
    const a4Reenabled = insertSuccessor(a3Disabled, 'A4-reenabled', createdAssessments)
    const afterReenable = await reconcile(pageA)
    expect(afterReenable.body.alerts).toHaveLength(3)
    expect(afterReenable.body.alerts.some(item =>
      item.assessmentId === a3Disabled.assessmentId)).toBe(false)
    expect(afterReenable.body.alerts.some(item =>
      item.assessmentId === a4Reenabled.assessmentId)).toBe(true)

    contextB = await browser.newContext()
    const pageB = await contextB.newPage()
    await signupLogin(pageB, userB)
    const ownership = await pageB.evaluate(async id =>
      (await fetch('/api/me/alerts/' + id)).status, alertA1.alertId)
    expect(ownership).toBe(404)
  } finally {
    await contextA?.close()
    await contextB?.close()
    cleanupFixtures(createdAssessments)
  }
})
