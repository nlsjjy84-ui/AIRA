// AIRA 플러스·공부 반 결제 직전 계산 — 화면과 떨어진 순수 함수라 테스트로 바로 확인한다.
// 가격은 출시 전 가정값이다(부가세 포함 표시). 결제는 아직 붙이지 않았다.
//   AIRA 플러스: 월 9,900원 / 연 99,000원(월 8,250원꼴, 두 달 값 할인)
//   공부 반: 수강생 1명당 월 3,000원 / 연 30,000원, 5명부터
// 환불 기준(정책안): 전자상거래법 제17조의 청약철회 기간 7일 안에는 기능을 썼어도 전액 환불.
//   7일이 지나면 월 결제는 다음 결제일부터 끝나고(남은 기간은 그대로 사용),
//   연 결제는 쓴 달을 월 요금으로 계산해 빼고 나머지를 돌려준다.

export const PRICE = {
  plus: { month: 9900, year: 99000 },
  class: { month: 3000, year: 30000, minSeats: 5, maxSeats: 200 },
}

export const won = n => `${Math.round(n).toLocaleString('ko-KR')}원`

const addMonths = (date, n) => {
  const d = new Date(date.getFullYear(), date.getMonth() + n, 1)
  const last = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()
  d.setDate(Math.min(date.getDate(), last))
  return d
}

export const ymd = d => `${d.getFullYear()}.${d.getMonth() + 1}.${d.getDate()}`

/** 결제 직전 요약: 오늘 낼 돈, 부가세, 다음 결제일, 월 환산, 할인 */
export function planQuote({ plan, cycle, seats = 1, today = new Date() }) {
  const p = PRICE[plan]
  if (!p || (cycle !== 'month' && cycle !== 'year')) return null
  const n = plan === 'class' ? Math.min(p.maxSeats, Math.max(p.minSeats, Math.floor(Number(seats) || 0))) : 1
  const total = p[cycle] * n
  const supply = Math.round(total / 1.1)
  const months = cycle === 'year' ? 12 : 1
  return {
    plan,
    cycle,
    seats: n,
    total,
    supply,
    vat: total - supply,
    perMonth: Math.round(total / months),
    saving: cycle === 'year' ? p.month * 12 * n - total : 0,
    nextCharge: addMonths(today, months),
    refundUntil: new Date(today.getFullYear(), today.getMonth(), today.getDate() + 7),
  }
}

/** 해지할 때 돌려받는 돈(정책안). 결제일과 해지일 사이 날짜로 계산한다. */
export function refundOf({ plan, cycle, seats = 1, paidAt, cancelAt }) {
  const q = planQuote({ plan, cycle, seats, today: paidAt })
  if (!q) return null
  const days = Math.floor((cancelAt - paidAt) / 86400000)
  if (days < 7) return { amount: q.total, reason: '7일 안 해지 — 전액 환불' }
  if (cycle === 'month') return { amount: 0, reason: '다음 결제일부터 끝나요. 남은 기간은 그대로 써요' }
  let used = 0
  while (used < 12 && addMonths(paidAt, used) <= cancelAt) used += 1
  const amount = Math.max(0, q.total - PRICE[plan].month * q.seats * used)
  return { amount, reason: `쓴 ${used}달을 월 요금으로 빼고 돌려드려요` }
}
