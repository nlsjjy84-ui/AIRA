// 종목 화면 계산 도우미. 모든 계산은 화면에 나온 공식 값으로만 하고, 추천·예측은 만들지 않는다.

export const FILING_CATEGORIES = [
  ['ALL', '전체'], ['PERIODIC', '정기보고서'], ['EARNINGS', '실적'], ['OWNERSHIP', '지분·주주'], ['MATERIAL', '주요사항'], ['IR', 'IR·설명회'], ['OTHER', '기타'],
]
export const FILING_LABEL = Object.fromEntries(FILING_CATEGORIES)

export const ACCOUNT_LABEL = {
  REVENUE: '매출', OPERATING_INCOME: '영업이익', NET_INCOME: '당기순이익', TOTAL_ASSETS: '자산총계',
  TOTAL_LIABILITIES: '부채총계', TOTAL_EQUITY: '자본총계', CURRENT_ASSETS: '유동자산', CURRENT_LIABILITIES: '유동부채',
}

/** 용어 풀이: 화면마다 붙는 "공부" 칸. better는 값이 클수록/작을수록 일반적으로 어떻게 읽는지(판단은 사용자 몫). */
export const RATIOS = [
  { key: 'operatingMargin', group: '수익성', label: '영업이익률', unit: '%', better: 'high', formula: '영업이익 ÷ 매출 × 100',
    meaning: '물건이나 서비스를 팔아 남긴 본업의 이익이 매출에서 몇 %인지 보여 줍니다.' },
  { key: 'netMargin', group: '수익성', label: '순이익률', unit: '%', better: 'high', formula: '당기순이익 ÷ 매출 × 100',
    meaning: '세금·이자 등을 모두 빼고 최종으로 남은 이익의 비율입니다.' },
  { key: 'roe', group: '수익성', label: 'ROE(자기자본이익률)', unit: '%', better: 'high', formula: '당기순이익 ÷ 자본총계 × 100',
    meaning: '주주의 돈(자본)으로 1년에 얼마를 벌었는지 나타냅니다.' },
  { key: 'roa', group: '수익성', label: 'ROA(총자산이익률)', unit: '%', better: 'high', formula: '당기순이익 ÷ 자산총계 × 100',
    meaning: '회사가 가진 전체 자산으로 얼마를 벌었는지 나타냅니다.' },
  { key: 'debtRatio', group: '안정성', label: '부채비율', unit: '%', better: 'low', formula: '부채총계 ÷ 자본총계 × 100',
    meaning: '자본 대비 빚이 얼마나 되는지 보여 줍니다. 100%면 빚과 자본이 같습니다.' },
  { key: 'currentRatio', group: '안정성', label: '유동비율', unit: '%', better: 'high', formula: '유동자산 ÷ 유동부채 × 100',
    meaning: '1년 안에 갚아야 할 빚을 1년 안에 현금화할 수 있는 자산으로 얼마나 감당하는지 보여 줍니다.' },
  { key: 'equityRatio', group: '안정성', label: '자기자본비율', unit: '%', better: 'high', formula: '자본총계 ÷ 자산총계 × 100',
    meaning: '전체 자산 중 빌리지 않은 내 돈(자본)의 비율입니다.' },
  { key: 'assetTurnover', group: '활동성', label: '총자산회전율', unit: '회', better: 'high', formula: '매출 ÷ 자산총계',
    meaning: '가진 자산으로 1년에 매출을 몇 배 만들었는지 보여 줍니다. 1회면 자산만큼 매출을 냈다는 뜻입니다.' },
  { key: 'equityTurnover', group: '활동성', label: '자기자본회전율', unit: '회', better: 'high', formula: '매출 ÷ 자본총계',
    meaning: '주주의 돈(자본)으로 매출을 몇 배 만들었는지 보여 줍니다.' },
  { key: 'revenueGrowth', group: '성장성', label: '매출 증가율', unit: '%', better: 'high', formula: '(올해 매출 − 작년 매출) ÷ 작년 매출 × 100',
    meaning: '작년보다 매출이 얼마나 늘거나 줄었는지 보여 줍니다.' },
  { key: 'operatingGrowth', group: '성장성', label: '영업이익 증가율', unit: '%', better: 'high', formula: '(올해 영업이익 − 작년) ÷ |작년| × 100',
    meaning: '본업 이익이 작년보다 얼마나 변했는지 보여 줍니다. 작년이 적자면 비율 대신 흑자 전환·적자 지속으로 표시합니다.' },
  { key: 'netGrowth', group: '성장성', label: '순이익 증가율', unit: '%', better: 'high', formula: '(올해 순이익 − 작년) ÷ |작년| × 100',
    meaning: '최종 이익이 작년보다 얼마나 변했는지 보여 줍니다.' },
]

const num = value => (value === null || value === undefined || value === '' ? null : Number(value))
const div = (a, b) => (a === null || b === null || !Number.isFinite(a) || !Number.isFinite(b) || b === 0 ? null : (a / b) * 100)
const growth = (now, before) => (now === null || before === null || before === 0 ? null : ((now - before) / Math.abs(before)) * 100)
const round = (value, digits = 1) => (value === null || !Number.isFinite(value) ? null : Math.round(value * 10 ** digits) / 10 ** digits)

/** 연도별 재무 값에 비율을 붙인다. years: [{year, values:{REVENUE:...}}] */
export function withRatios(years = []) {
  const sorted = [...years].sort((a, b) => a.year - b.year)
  return sorted.map((entry, index) => {
    const v = Object.fromEntries(Object.entries(entry.values ?? {}).map(([k, x]) => [k, num(x)]))
    const prev = index > 0 && sorted[index - 1].year === entry.year - 1
      ? Object.fromEntries(Object.entries(sorted[index - 1].values ?? {}).map(([k, x]) => [k, num(x)])) : {}
    const get = (o, k) => (o[k] === undefined ? null : o[k])
    const ratios = {
      operatingMargin: round(div(get(v, 'OPERATING_INCOME'), get(v, 'REVENUE'))),
      netMargin: round(div(get(v, 'NET_INCOME'), get(v, 'REVENUE'))),
      roe: round(div(get(v, 'NET_INCOME'), get(v, 'TOTAL_EQUITY'))),
      roa: round(div(get(v, 'NET_INCOME'), get(v, 'TOTAL_ASSETS'))),
      debtRatio: round(div(get(v, 'TOTAL_LIABILITIES'), get(v, 'TOTAL_EQUITY'))),
      currentRatio: round(div(get(v, 'CURRENT_ASSETS'), get(v, 'CURRENT_LIABILITIES'))),
      equityRatio: round(div(get(v, 'TOTAL_EQUITY'), get(v, 'TOTAL_ASSETS'))),
      assetTurnover: get(v, 'REVENUE') === null || !get(v, 'TOTAL_ASSETS') ? null : round(get(v, 'REVENUE') / get(v, 'TOTAL_ASSETS'), 2),
      equityTurnover: get(v, 'REVENUE') === null || !get(v, 'TOTAL_EQUITY') || get(v, 'TOTAL_EQUITY') <= 0 ? null : round(get(v, 'REVENUE') / get(v, 'TOTAL_EQUITY'), 2),
      revenueGrowth: round(growth(get(v, 'REVENUE'), get(prev, 'REVENUE'))),
      operatingGrowth: round(growth(get(v, 'OPERATING_INCOME'), get(prev, 'OPERATING_INCOME'))),
      netGrowth: round(growth(get(v, 'NET_INCOME'), get(prev, 'NET_INCOME'))),
    }
    const turn = (now, before) => (now === null || before === null ? null
      : before < 0 && now >= 0 ? '흑자 전환' : before >= 0 && now < 0 ? '적자 전환' : before < 0 && now < 0 ? '적자 지속' : null)
    ratios.operatingTurn = turn(get(v, 'OPERATING_INCOME'), get(prev, 'OPERATING_INCOME'))
    ratios.netTurn = turn(get(v, 'NET_INCOME'), get(prev, 'NET_INCOME'))
    if (ratios.operatingTurn) ratios.operatingGrowth = null
    if (ratios.netTurn) ratios.netGrowth = null
    return { ...entry, values: v, ratios }
  })
}

/** 원 단위 금액을 조·억으로 읽기 쉽게. */
export function formatWon(value) {
  const n = num(value)
  if (n === null || !Number.isFinite(n)) return '—'
  const sign = n < 0 ? '−' : ''
  const abs = Math.abs(n)
  if (abs >= 1e12) return `${sign}${(abs / 1e12).toLocaleString('ko-KR', { maximumFractionDigits: 2 })}조 원`
  if (abs >= 1e8) return `${sign}${Math.round(abs / 1e8).toLocaleString('ko-KR')}억 원`
  if (abs >= 1e4) return `${sign}${Math.round(abs / 1e4).toLocaleString('ko-KR')}만 원`
  return `${sign}${abs.toLocaleString('ko-KR')}원`
}

export function formatPrice(value) {
  const n = num(value)
  return n === null ? '—' : `${n.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}원`
}

export function formatCount(value, unit = '주') {
  const n = num(value)
  if (n === null) return '—'
  if (Math.abs(n) >= 1e8) return `${(n / 1e8).toLocaleString('ko-KR', { maximumFractionDigits: 2 })}억 ${unit}`
  if (Math.abs(n) >= 1e4) return `${Math.round(n / 1e4).toLocaleString('ko-KR')}만 ${unit}`
  return `${n.toLocaleString('ko-KR')} ${unit}`
}

export function formatPercent(value, { sign = true } = {}) {
  const n = num(value)
  if (n === null || !Number.isFinite(n)) return '—'
  const fixed = Math.abs(n) >= 100 ? n.toFixed(0) : n.toFixed(1)
  return `${sign && n > 0 ? '+' : ''}${fixed}%`
}

/** 기간 안의 주가 점만 남긴다(마지막 거래일 기준으로 days일 전부터). */
export function lastDays(points = [], days) {
  if (!points.length) return []
  const end = new Date(points[points.length - 1].date)
  const start = new Date(end)
  start.setDate(start.getDate() - days)
  const from = start.toISOString().slice(0, 10)
  return points.filter(point => point.date >= from)
}

export function priceStats(points = []) {
  const valid = points.filter(point => num(point.close) !== null)
  if (valid.length === 0) return null
  const first = valid[0]
  const last = valid[valid.length - 1]
  let high = valid[0]
  let low = valid[0]
  let peak = num(valid[0].close)
  let maxDrawdown = 0
  let up = 0
  let down = 0
  let volumeSum = 0
  let volumeDays = 0
  for (const point of valid) {
    const close = num(point.close)
    if (close > num(high.close)) high = point
    if (close < num(low.close)) low = point
    peak = Math.max(peak, close)
    maxDrawdown = Math.min(maxDrawdown, (close - peak) / peak * 100)
    const rate = num(point.changeRate)
    if (rate > 0) up += 1
    if (rate < 0) down += 1
    if (num(point.volume) !== null) { volumeSum += num(point.volume); volumeDays += 1 }
  }
  return {
    first, last, high, low, days: valid.length, up, down,
    change: round((num(last.close) - num(first.close)) / num(first.close) * 100, 2),
    maxDrawdown: round(maxDrawdown, 2),
    averageVolume: volumeDays ? Math.round(volumeSum / volumeDays) : null,
  }
}

/** 두 계열을 시작일=100으로 맞춰 같은 날짜끼리 비교한다. */
export function normalizeTogether(a = [], b = []) {
  const byDate = new Map(b.map(point => [point.date, num(point.close)]))
  const shared = a.filter(point => byDate.has(point.date) && num(point.close) !== null && byDate.get(point.date) !== null)
  if (shared.length < 2) return []
  const baseA = num(shared[0].close)
  const baseB = byDate.get(shared[0].date)
  return shared.map(point => ({ date: point.date, a: round(num(point.close) / baseA * 100, 2), b: round(byDate.get(point.date) / baseB * 100, 2) }))
}

/** 공시 접수일 전후 n거래일 종가 변화. 같은 기간에 함께 나타난 변화일 뿐 원인을 뜻하지 않는다. */
export function aroundFilings(points = [], filings = [], window = 5) {
  return filings.map(filing => {
    const i = points.findIndex(point => point.date >= filing.date)
    if (i <= 0) return null
    const before = points[i - 1]
    const afterIndex = Math.min(points.length - 1, i + window)
    const after = points[afterIndex]
    const days = afterIndex - i + 1
    if (num(before.close) === null || num(after.close) === null) return null
    return { ...filing, days, beforeDate: before.date, beforeClose: num(before.close), afterDate: after.date, afterClose: num(after.close),
      change: round((num(after.close) - num(before.close)) / num(before.close) * 100, 2) }
  }).filter(Boolean)
}

/** 최근 연도와 그 전 연도를 항목별로 비교한다(가늠하다 · 변화표). */
export function yearOverYear(rows = []) {
  if (rows.length < 2) return []
  const now = rows[rows.length - 1]
  const before = rows[rows.length - 2]
  const items = []
  for (const [key, label] of Object.entries(ACCOUNT_LABEL)) {
    const a = before.values[key]
    const b = now.values[key]
    if (a === null || a === undefined || b === null || b === undefined) continue
    const better = key === 'TOTAL_LIABILITIES' || key === 'CURRENT_LIABILITIES' ? 'low' : 'high'
    items.push({ key, label, before: a, now: b, change: round(growth(b, a)), direction: b > a ? 'up' : b < a ? 'down' : 'same', better, kind: 'amount' })
  }
  for (const ratio of RATIOS.filter(r => r.group !== '성장성')) {
    const a = before.ratios[ratio.key]
    const b = now.ratios[ratio.key]
    if (a === null || b === null) continue
    items.push({ key: ratio.key, label: ratio.label, before: a, now: b, change: round(b - a, 1), direction: b > a ? 'up' : b < a ? 'down' : 'same', better: ratio.better, kind: 'ratio' })
  }
  return items.map(item => ({ ...item,
    reading: item.direction === 'same' ? 'same' : (item.direction === 'up') === (item.better === 'high') ? 'better' : 'worse',
    years: [before.year, now.year] }))
}

/** 확인 체크리스트: 사실 확인 항목. 결과는 예/아니오/자료 없음이며 좋다·나쁘다를 단정하지 않는다. */
export function checklist(rows = [], stats = null, market = null, filings = []) {
  const last = rows[rows.length - 1]
  const lastThree = rows.slice(-4)
  const out = []
  const add = (label, value, basis) => out.push({ label, value, basis })
  if (last) {
    const oi = last.values.OPERATING_INCOME
    add(`${last.year}년 영업이익이 흑자인가요?`, oi === null || oi === undefined ? null : oi >= 0, `영업이익 ${formatWon(oi)}`)
    const ni = last.values.NET_INCOME
    add(`${last.year}년 당기순이익이 흑자인가요?`, ni === null || ni === undefined ? null : ni >= 0, `당기순이익 ${formatWon(ni)}`)
    const debt = last.ratios.debtRatio
    add('부채비율이 200% 아래인가요?', debt === null ? null : debt < 200, `부채비율 ${formatPercent(debt, { sign: false })}`)
    const current = last.ratios.currentRatio
    add('유동비율이 100% 이상인가요?', current === null ? null : current >= 100, `유동비율 ${formatPercent(current, { sign: false })}`)
  }
  if (lastThree.length === 4) {
    const rising = lastThree.every((row, i) => i === 0 || (row.values.REVENUE ?? null) !== null && (lastThree[i - 1].values.REVENUE ?? null) !== null && row.values.REVENUE > lastThree[i - 1].values.REVENUE)
    add('매출이 3년 연속 늘었나요?', rising, lastThree.map(row => `${row.year} ${formatWon(row.values.REVENUE)}`).join(' → '))
  }
  if (stats && market) add('최근 1년 주가 변화가 시장 지수보다 컸나요?', stats.change > market.change,
    `이 종목 ${formatPercent(stats.change)} · 지수 ${formatPercent(market.change)}`)
  const recentMaterial = filings.filter(item => item.category === 'MATERIAL').length
  add('최근 1년 주요사항 공시가 있었나요?', filings.length ? recentMaterial > 0 : null, `주요사항 공시 ${recentMaterial}건`)
  return out
}

/** 저장된 사건 제목의 조사(이/가)를 받침에 맞게 고쳐 보여 준다. 저장된 원래 제목은 바꾸지 않는다. */
export function fixSubjectParticle(title = '') {
  return String(title).replace(/^(.+?)(이|가) (\d{4} 회계연도)/, (whole, name, particle, rest) => `${name}${subjectParticle(name)} ${rest}`)
}

export function subjectParticle(name = '') {
  const last = String(name).trim().slice(-1)
  if (!last) return '이(가)'
  const code = last.charCodeAt(0)
  if (code >= 0xac00 && code <= 0xd7a3) return (code - 0xac00) % 28 === 0 ? '가' : '이'
  if (/[0-9]/.test(last)) return '013678'.includes(last) ? '이' : '가'
  const latin = /[A-Za-z]+$/.exec(String(name).trim())?.[0] ?? ''
  // 짧은 대문자 약자(LG, HL)는 글자 이름(엘·엠·엔·알)으로 읽고, 단어(NAVER)는 대개 모음으로 끝나 '가'를 쓴다.
  if (latin && latin.length <= 3 && latin === latin.toUpperCase()) return /[LMNR]$/.test(latin) ? '이' : '가'
  return '가'
}

/** 질문으로 찾기: 낱말로 맞는 화면을 고른다(AI 아님). */
export const QUESTION_ROUTES = [
  { words: ['주가', '가격', '올랐', '떨어', '얼마'], label: '최근 주가는 어떻게 움직였나요?', to: ['inspect', 'price', 'trend'] },
  { words: ['거래량', '거래대금', '거래'], label: '거래는 얼마나 활발했나요?', to: ['inspect', 'price', 'volume'] },
  { words: ['시가총액', '시총', '회사 크기', '규모'], label: '회사 크기(시가총액)는 어떻게 변했나요?', to: ['inspect', 'price', 'cap'] },
  { words: ['매출', '실적', '벌었'], label: '매출은 늘고 있나요?', to: ['inspect', 'earnings', 'REVENUE'] },
  { words: ['영업이익', '이익', '흑자', '적자'], label: '본업으로 이익을 내고 있나요?', to: ['inspect', 'earnings', 'OPERATING_INCOME'] },
  { words: ['빚', '부채', '안정', '갚'], label: '빚은 많은 편인가요?', to: ['inspect', 'ratios', '안정성'] },
  { words: ['자산', '자본', '재무상태'], label: '자산·부채·자본은 어떻게 이루어져 있나요?', to: ['inspect', 'balance', 'structure'] },
  { words: ['roe', '수익성', '이익률'], label: '얼마나 효율적으로 벌고 있나요?', to: ['inspect', 'ratios', '수익성'] },
  { words: ['공시', '발표', '보고서'], label: '최근에 어떤 공시를 냈나요?', to: ['inspect', 'filings', 'ALL'] },
  { words: ['뉴스', '기사'], label: '최근 기사는 무엇이 있나요?', to: ['inspect', 'news', 'list'] },
  { words: ['시장', '코스피', '코스닥', '지수'], label: '시장보다 많이 움직였나요?', to: ['relate', 'market', 'compare'] },
  { words: ['공시 후', '전후', '반응'], label: '공시 전후로 주가는 어땠나요?', to: ['relate', 'around', 'list'] },
  { words: ['작년', '좋아', '나빠', '변화'], label: '작년보다 무엇이 달라졌나요?', to: ['assess', 'yoy', 'table'] },
  { words: ['체크', '확인', '위험'], label: '기본 확인 항목을 점검해 볼까요?', to: ['assess', 'check', 'list'] },
  { words: ['회사', '대표', '설립', '어떤'], label: '어떤 회사인가요?', to: ['inspect', 'company', 'basic'] },
]

export function routeQuestion(text = '') {
  const q = text.trim().toLowerCase()
  if (!q) return []
  return QUESTION_ROUTES.map(route => ({ route, score: route.words.filter(word => q.includes(word)).length }))
    .filter(item => item.score > 0).sort((a, b) => b.score - a.score).map(item => item.route)
}

/** 시장 평가 지표: 마지막 거래일 시가총액과 최근 사업연도 값으로 계산한다. 순이익·자본이 0 이하이면 계산하지 않는다. */
export const VALUATIONS = [
  { key: 'per', label: 'PER(주가수익비율)', unit: '배', formula: '시가총액 ÷ 당기순이익', meaning: '회사가 1년에 버는 순이익의 몇 배 가격에 거래되는지 보여 줍니다. 순이익이 적자면 계산하지 않습니다.' },
  { key: 'pbr', label: 'PBR(주가순자산비율)', unit: '배', formula: '시가총액 ÷ 자본총계', meaning: '장부상 순자산(자본)의 몇 배 가격에 거래되는지 보여 줍니다. 1배보다 낮으면 장부 가치보다 싸게 거래된다는 뜻입니다.' },
  { key: 'psr', label: 'PSR(주가매출비율)', unit: '배', formula: '시가총액 ÷ 매출', meaning: '1년 매출의 몇 배 가격에 거래되는지 보여 줍니다. 이익이 적자인 회사도 볼 수 있습니다.' },
  { key: 'eps', label: 'EPS(주당순이익)', unit: '원', formula: '당기순이익 ÷ 상장주식수', meaning: '주식 1주가 1년에 번 순이익입니다.' },
  { key: 'bps', label: 'BPS(주당순자산)', unit: '원', formula: '자본총계 ÷ 상장주식수', meaning: '주식 1주에 해당하는 장부상 순자산입니다.' },
]

export function valuation(point, year) {
  if (!point || !year) return null
  const cap = num(point.marketCap)
  const shares = num(point.listedShares)
  const ni = year.values?.NET_INCOME ?? null
  const eq = year.values?.TOTAL_EQUITY ?? null
  const rev = year.values?.REVENUE ?? null
  const ratio = (a, b) => (a === null || b === null || b <= 0 ? null : round(a / b, 2))
  return {
    date: point.date, year: year.year, marketCap: cap, shares,
    per: ratio(cap, ni), pbr: ratio(cap, eq), psr: ratio(cap, rev),
    eps: shares ? round(ni / shares, 0) : null, bps: shares ? round(eq / shares, 0) : null,
  }
}

/** 용어 사전: 화면 곳곳의 풀이를 한곳에 모은다. */
export const GLOSSARY = [
  ...RATIOS.map(r => ({ term: r.label, group: r.group, text: `${r.meaning} 계산: ${r.formula}` })),
  ...VALUATIONS.map(v => ({ term: v.label, group: '시장 평가', text: `${v.meaning} 계산: ${v.formula}` })),
  { term: '매출', group: '실적', text: '물건·서비스를 팔아 들어온 돈 전체입니다.' },
  { term: '영업이익', group: '실적', text: '매출에서 원가·판매비·관리비를 뺀 본업의 이익입니다.' },
  { term: '당기순이익', group: '실적', text: '영업이익에 이자·투자손익·세금까지 반영한 최종 이익입니다.' },
  { term: '연결재무제표', group: '실적', text: '자회사까지 합친 재무제표입니다. AIRA는 연결을 우선 쓰고, 없을 때만 별도 재무제표를 씁니다.' },
  { term: '자산', group: '재무상태', text: '회사가 가진 모든 것(현금·공장·재고 등)입니다. 자산 = 부채 + 자본.' },
  { term: '부채', group: '재무상태', text: '갚아야 할 돈입니다.' },
  { term: '자본', group: '재무상태', text: '자산에서 부채를 뺀 주주의 몫입니다.' },
  { term: '유동자산 / 유동부채', group: '재무상태', text: '1년 안에 현금이 되는 자산 / 1년 안에 갚아야 하는 빚입니다.' },
  { term: '종가', group: '주가', text: '그날 마지막에 거래된 가격입니다.' },
  { term: '시가 · 고가 · 저가', group: '주가', text: '그날 처음 거래된 가격 · 가장 높았던 가격 · 가장 낮았던 가격입니다.' },
  { term: '등락률', group: '주가', text: '전날 종가 대비 오늘 종가의 변화율입니다.' },
  { term: '거래량 · 거래대금', group: '주가', text: '그날 사고판 주식 수 · 금액입니다.' },
  { term: '시가총액', group: '주가', text: '주가 × 상장주식수. 시장이 매긴 회사 전체의 값입니다.' },
  { term: '52주 최고·최저', group: '주가', text: '최근 1년 동안 가장 높았던·낮았던 종가입니다(AIRA는 일별 종가 기준).' },
  { term: '최고점 대비 최대 하락', group: '주가', text: '기간 중 가장 높았던 가격에서 가장 많이 떨어졌던 폭입니다.' },
  { term: '시작=100 비교', group: '잇다', text: '크기가 다른 두 값을 같은 출발선에서 보려고 첫날을 100으로 맞춘 것입니다.' },
  { term: '%p(퍼센트포인트)', group: '잇다', text: '비율끼리의 차이입니다. 10%에서 12%가 되면 2%p 오른 것입니다.' },
  { term: '사업보고서', group: '공시', text: '1년에 한 번 내는 보고서로 실적·재무·사업 내용이 모두 담깁니다.' },
  { term: '잠정실적', group: '공시', text: '정식 보고서 전에 실적 숫자를 미리 알리는 공시입니다.' },
  { term: '주요사항보고서', group: '공시', text: '증자·자사주·합병처럼 회사에 중요한 결정을 알리는 공시입니다.' },
  { term: '접수번호', group: '공시', text: 'DART가 공시마다 붙이는 14자리 번호입니다. 앞 8자리가 접수일입니다.' },
  { term: 'DART 고유번호', group: '공시', text: '전자공시에서 회사를 구분하는 8자리 번호입니다.' },
]

const RECENT_KEY = 'aira-recent-stocks'
export function readRecentStocks() {
  try { return JSON.parse(window.localStorage.getItem(RECENT_KEY) ?? '[]').filter(item => item?.id && item?.name).slice(0, 8) } catch { return [] }
}
export function rememberStock(id, name) {
  try {
    const next = [{ id, name }, ...readRecentStocks().filter(item => item.id !== id)].slice(0, 8)
    window.localStorage.setItem(RECENT_KEY, JSON.stringify(next))
  } catch { /* 저장이 막혀도 화면은 그대로 동작한다. */ }
}

/** n일 이동평균(종가). 앞쪽 n−1일은 비운다. */
export function movingAverage(points = [], n = 20) {
  const out = []
  let sum = 0
  for (let i = 0; i < points.length; i += 1) {
    sum += Number(points[i].close)
    if (i >= n) sum -= Number(points[i - n].close)
    out.push(i >= n - 1 ? Math.round(sum / n) : null)
  }
  return out
}

/** DART 보고서 칸의 숫자 문자열("1,446", "-", "")을 숫자로. */
export function dartNumber(raw) {
  if (raw === null || raw === undefined) return null
  const token = String(raw).replaceAll(',', '').trim()
  if (!token || token === '-' || !/^[-+]?\d+(\.\d+)?$/.test(token)) return null
  return Number(token)
}

/** 일별 시세를 주봉·월봉으로 묶는다. 시가=첫날 시가, 종가=마지막 날 종가, 고가·저가=기간 최고·최저, 거래량=합. */
export function aggregateCandles(points = [], unit = 'day') {
  if (unit === 'day') return points
  const groups = new Map()
  for (const p of points) {
    const d = new Date(`${p.date}T00:00:00Z`)
    let key
    if (unit === 'month') key = p.date.slice(0, 7)
    else { const monday = new Date(d); monday.setUTCDate(d.getUTCDate() - ((d.getUTCDay() + 6) % 7)); key = monday.toISOString().slice(0, 10) }
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(p)
  }
  return [...groups.values()].map(list => ({
    date: list[list.length - 1].date, from: list[0].date, open: list[0].open, close: list[list.length - 1].close,
    high: Math.max(...list.map(p => Number(p.high ?? p.close))), low: Math.min(...list.map(p => Number(p.low ?? p.close))),
    volume: list.reduce((sum, p) => sum + Number(p.volume ?? 0), 0), days: list.length,
  }))
}

/** 통계청 한국표준산업분류(10차) 중분류 이름. DART 업종코드 앞 두 자리로 찾는다. */
const KSIC = { '01': '농업', '02': '임업', '03': '어업', '05': '석탄·원유·천연가스 광업', '06': '금속 광업', '07': '비금속광물 광업', '08': '광업 지원 서비스업',
  '10': '식료품 제조업', '11': '음료 제조업', '12': '담배 제조업', '13': '섬유제품 제조업', '14': '의복·모피제품 제조업', '15': '가죽·가방·신발 제조업',
  '16': '목재·나무제품 제조업', '17': '펄프·종이 제조업', '18': '인쇄·기록매체 복제업', '19': '코크스·석유정제품 제조업', '20': '화학물질·화학제품 제조업',
  '21': '의약품 제조업', '22': '고무·플라스틱제품 제조업', '23': '비금속 광물제품 제조업', '24': '1차 금속 제조업', '25': '금속가공제품 제조업',
  '26': '전자부품·컴퓨터·통신장비 제조업', '27': '의료·정밀·광학기기 제조업', '28': '전기장비 제조업', '29': '기타 기계·장비 제조업', '30': '자동차·트레일러 제조업',
  '31': '기타 운송장비 제조업', '32': '가구 제조업', '33': '기타 제품 제조업', '34': '산업용 기계 수리업', '35': '전기·가스·증기 공급업', '36': '수도업',
  '37': '하수·폐수 처리업', '38': '폐기물 처리·원료 재생업', '39': '환경 정화·복원업', '41': '종합 건설업', '42': '전문직별 공사업', '45': '자동차·부품 판매업',
  '46': '도매·상품 중개업', '47': '소매업', '49': '육상 운송업', '50': '수상 운송업', '51': '항공 운송업', '52': '창고·운송 서비스업', '55': '숙박업',
  '56': '음식점·주점업', '58': '출판업', '59': '영상·오디오 제작·배급업', '60': '방송업', '61': '우편·통신업', '62': '컴퓨터 프로그래밍·시스템 통합업',
  '63': '정보서비스업', '64': '금융업', '65': '보험·연금업', '66': '금융·보험 서비스업', '68': '부동산업', '70': '연구개발업', '71': '전문 서비스업',
  '72': '건축기술·엔지니어링 서비스업', '73': '기타 과학기술 서비스업', '74': '사업시설 관리·조경 서비스업', '75': '사업 지원 서비스업', '76': '임대업',
  '84': '공공행정', '85': '교육 서비스업', '86': '보건업', '87': '사회복지 서비스업', '90': '창작·예술·여가 서비스업', '91': '스포츠·오락 서비스업',
  '94': '협회·단체', '95': '개인·소비용품 수리업', '96': '기타 개인 서비스업' }
export function industryName(code) {
  if (!code) return '업종 정보 없음'
  return KSIC[String(code).slice(0, 2)] ?? `업종코드 ${code}`
}

/** 같은 업종 회사 하나의 비교 지표. */
export function peerMetrics(item) {
  const f = item.financials ?? {}
  const p = item.price ?? {}
  const n = key => (f[key] === undefined || f[key] === null ? null : Number(f[key]))
  const cap = p.marketCap === undefined || p.marketCap === null ? null : Number(p.marketCap)
  const pct = (a, b) => (a === null || b === null || b === 0 ? null : round(a / b * 100))
  const times = (a, b) => (a === null || b === null || b <= 0 ? null : round(a / b, 2))
  return { marketCap: cap, revenue: n('REVENUE'), year: f.year ?? null,
    operatingMargin: pct(n('OPERATING_INCOME'), n('REVENUE')), roe: pct(n('NET_INCOME'), n('TOTAL_EQUITY')),
    debtRatio: pct(n('TOTAL_LIABILITIES'), n('TOTAL_EQUITY')), per: times(cap, n('NET_INCOME')), pbr: times(cap, n('TOTAL_EQUITY')) }
}

export const PEER_METRICS = [
  { key: 'marketCap', label: '시가총액', better: 'high', kind: 'won' }, { key: 'revenue', label: '매출', better: 'high', kind: 'won' },
  { key: 'operatingMargin', label: '영업이익률', better: 'high', kind: 'pct' }, { key: 'roe', label: 'ROE', better: 'high', kind: 'pct' },
  { key: 'debtRatio', label: '부채비율', better: 'low', kind: 'pct' }, { key: 'per', label: 'PER', better: null, kind: 'x' }, { key: 'pbr', label: 'PBR', better: null, kind: 'x' },
]

export function median(values = []) {
  const list = values.filter(v => v !== null && v !== undefined && Number.isFinite(v)).sort((a, b) => a - b)
  if (!list.length) return null
  const mid = Math.floor(list.length / 2)
  return list.length % 2 ? list[mid] : round((list[mid - 1] + list[mid]) / 2, 2)
}

/** 업종 안에서 이 회사의 순위(큰 값이 1위)와 중간값. */
export function peerPosition(items = [], key) {
  const rows = items.map(item => ({ self: item.self, value: peerMetrics(item)[key] })).filter(row => row.value !== null && Number.isFinite(row.value))
  const sorted = [...rows].sort((a, b) => b.value - a.value)
  const index = sorted.findIndex(row => row.self)
  return { count: sorted.length, rank: index >= 0 ? index + 1 : null, value: index >= 0 ? sorted[index].value : null, median: median(rows.map(r => r.value)),
    min: sorted.length ? sorted[sorted.length - 1].value : null, max: sorted.length ? sorted[0].value : null }
}

/** 다섯 방향 요약: 같은 업종 안에서 이 회사가 몇 번째 백분위인지(100 = 가장 높음). 점수·등급이 아니다. */
export const RADAR_AXES = [
  { key: 'revenueGrowth', label: '성장', hint: '매출 증가율' }, { key: 'operatingMargin', label: '수익성', hint: '영업이익률' },
  { key: 'roe', label: '효율', hint: 'ROE' }, { key: 'debtRatio', label: '안정성', hint: '부채비율이 낮은 순', invert: true },
  { key: 'marketCap', label: '규모', hint: '시가총액' },
]

export function percentileOf(values, value, invert = false) {
  const list = values.filter(v => v !== null && v !== undefined && Number.isFinite(v))
  if (value === null || value === undefined || !Number.isFinite(value) || list.length < 2) return null
  const below = list.filter(v => (invert ? v > value : v < value)).length
  const equal = list.filter(v => v === value).length
  return Math.round(((below + (equal - 1) / 2) / (list.length - 1)) * 100)
}

/** 비율 하나를 단위에 맞게 쓴다(%, 회). */
export function ratioText(ratio, value, sign = false) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '—'
  if (ratio.unit === '회') return `${Number(value).toLocaleString('ko-KR', { maximumFractionDigits: 2 })}회`
  return formatPercent(value, { sign })
}

/** 표를 CSV로 만든다(엑셀에서 한글이 깨지지 않도록 BOM을 붙인다). */
export function toCsv(header, rows) {
  const cell = value => {
    const text = value === null || value === undefined ? '' : String(value)
    return /[",\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text
  }
  return '﻿' + [header, ...rows].map(row => row.map(cell).join(',')).join('\r\n')
}

export function downloadCsv(filename, header, rows) {
  try {
    const blob = new Blob([toCsv(header, rows)], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch { /* 내려받기를 지원하지 않는 환경에서는 아무것도 하지 않는다. */ }
}

/**
 * 이해 확인 퀴즈: 이 회사의 실제 값으로 문제를 만든다(규칙 기반, AI 아님).
 * 정답 뒤에는 "왜 그런지"를 계산과 함께 설명해 지식으로 남게 한다.
 */
export function buildQuiz(name, rows = [], stats = null) {
  const out = []
  const y = rows[rows.length - 1]
  const prev = rows[rows.length - 2]
  if (y && y.ratios.debtRatio !== null) {
    const line = y.ratios.debtRatio >= 100 ? 100 : 50
    out.push({ term: '부채비율', question: `${name}의 ${y.year}년 말 부채비율은 ${line}%보다 높을까요?`, choices: ['높다', '낮다'],
      answer: y.ratios.debtRatio > line ? 0 : 1,
      why: `부채비율은 부채총계 ÷ 자본총계 × 100이에요. ${formatWon(y.values.TOTAL_LIABILITIES)} ÷ ${formatWon(y.values.TOTAL_EQUITY)} = ${formatPercent(y.ratios.debtRatio, { sign: false })}. 자본 100원당 빚이 약 ${Math.round(y.ratios.debtRatio)}원 있다는 뜻이에요.` })
  }
  if (y && prev && y.ratios.operatingMargin !== null && prev.ratios.operatingMargin !== null && y.ratios.operatingMargin !== prev.ratios.operatingMargin) {
    out.push({ term: '영업이익률', question: `${name}의 영업이익률은 ${prev.year}년보다 ${y.year}년에 올랐을까요?`, choices: ['올랐다', '내렸다'],
      answer: y.ratios.operatingMargin > prev.ratios.operatingMargin ? 0 : 1,
      why: `영업이익률은 영업이익 ÷ 매출이에요. ${prev.year}년 ${formatPercent(prev.ratios.operatingMargin, { sign: false })} → ${y.year}년 ${formatPercent(y.ratios.operatingMargin, { sign: false })}. 1,000원어치를 팔았을 때 본업으로 남는 돈이 약 ${Math.round(prev.ratios.operatingMargin * 10)}원에서 ${Math.round(y.ratios.operatingMargin * 10)}원이 된 셈이에요.` })
  }
  if (y && y.values.NET_INCOME !== undefined && y.values.NET_INCOME !== null) {
    out.push({ term: 'PER', question: `${name}처럼 ${y.year}년에 ${y.values.NET_INCOME >= 0 ? '흑자' : '적자'}인 회사는 PER을 계산할 수 있을까요?`, choices: ['계산할 수 있다', '계산하지 않는다'],
      answer: y.values.NET_INCOME > 0 ? 0 : 1,
      why: 'PER은 시가총액 ÷ 당기순이익이에요. 순이익이 0 이하(적자)면 나누는 값이 0이나 음수가 되어 뜻이 없어지기 때문에 계산하지 않아요.' })
  }
  if (stats) {
    out.push({ term: '최고점 대비 최대 하락', question: `최근 1년 동안 ${name} 주가가 가장 높았던 때보다 가장 많이 떨어졌던 폭은 20%보다 컸을까요?`, choices: ['컸다', '작았다'],
      answer: Math.abs(stats.maxDrawdown) > 20 ? 0 : 1,
      why: `최고점 대비 최대 하락은 기간 중 가장 높았던 종가에서 가장 많이 떨어졌던 폭이에요. 이 회사는 ${formatPercent(stats.maxDrawdown)}였어요. 흔들림이 얼마나 컸는지 가늠할 때 봐요.` })
  }
  if (y && y.ratios.currentRatio !== null) {
    out.push({ term: '유동비율', question: `${name}은 1년 안에 갚을 빚(유동부채)보다 1년 안에 현금이 되는 자산(유동자산)이 더 많을까요?`, choices: ['더 많다', '더 적다'],
      answer: y.ratios.currentRatio >= 100 ? 0 : 1,
      why: `유동비율 = 유동자산 ÷ 유동부채 × 100 = ${formatPercent(y.ratios.currentRatio, { sign: false })}. 100%보다 크면 1년 안의 빚을 1년 안의 자산으로 감당할 수 있다는 뜻이에요.` })
  }
  return out
}

/** 저장된 주가 기간에 맞는 이름. 1년 가까이 있으면 「1년」「52주」, 아니면 「N개월」(보관 기간을 줄였을 때 거짓 이름이 안 나오게). */
export function spanName(stats) {
  if (!stats?.first?.date || !stats?.last?.date) return { change: '1년', range: '52주' }
  const days = (new Date(`${stats.last.date}T00:00:00`) - new Date(`${stats.first.date}T00:00:00`)) / 86400000
  if (days >= 330) return { change: '1년', range: '52주' }
  const months = Math.max(1, Math.round(days / 30.4))
  return { change: `${months}개월`, range: `${months}개월` }
}
