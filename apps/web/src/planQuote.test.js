import { describe, expect, it } from 'vitest'
import { planQuote, refundOf } from './planQuote.js'

describe('planQuote — 결제 직전 계산', () => {
  it('플러스 연 결제: 99,000원, 부가세 9,000원, 월 8,250원꼴, 월 결제보다 19,800원 덜 냄', () => {
    const q = planQuote({ plan: 'plus', cycle: 'year', today: new Date(2026, 9, 14) })
    expect(q).toMatchObject({ total: 99000, supply: 90000, vat: 9000, perMonth: 8250, saving: 19800 })
    expect(q.nextCharge).toEqual(new Date(2027, 9, 14))
    expect(q.refundUntil).toEqual(new Date(2026, 9, 21))
  })
  it('월 결제 다음 결제일은 달 말일을 넘지 않는다(1월 31일 → 2월 28일)', () => {
    expect(planQuote({ plan: 'plus', cycle: 'month', today: new Date(2027, 0, 31) }).nextCharge).toEqual(new Date(2027, 1, 28))
  })
  it('공부 반은 5명부터, 200명까지로 맞춘다', () => {
    expect(planQuote({ plan: 'class', cycle: 'month', seats: 2 })).toMatchObject({ seats: 5, total: 15000 })
    expect(planQuote({ plan: 'class', cycle: 'year', seats: 12 })).toMatchObject({ seats: 12, total: 360000, saving: 72000 })
    expect(planQuote({ plan: 'class', cycle: 'month', seats: 999 }).seats).toBe(200)
  })
  it('없는 플랜·주기는 null', () => {
    expect(planQuote({ plan: 'gold', cycle: 'year' })).toBeNull()
    expect(planQuote({ plan: 'plus', cycle: 'week' })).toBeNull()
  })
})

describe('refundOf — 해지 환불(정책안)', () => {
  const paidAt = new Date(2026, 0, 1)
  it('7일 안이면 전액', () => {
    expect(refundOf({ plan: 'plus', cycle: 'year', paidAt, cancelAt: new Date(2026, 0, 7) }).amount).toBe(99000)
  })
  it('월 결제는 7일 뒤 해지 시 환불 없이 다음 결제일에 끝난다', () => {
    expect(refundOf({ plan: 'plus', cycle: 'month', paidAt, cancelAt: new Date(2026, 0, 20) }).amount).toBe(0)
  })
  it('연 결제는 쓴 달을 월 요금으로 뺀다(3월 10일 해지 = 3달 사용 → 69,300원)', () => {
    expect(refundOf({ plan: 'plus', cycle: 'year', paidAt, cancelAt: new Date(2026, 2, 10) }).amount).toBe(69300)
  })
  it('11달 넘게 쓰면 돌려줄 돈이 0보다 작아지지 않는다', () => {
    expect(refundOf({ plan: 'plus', cycle: 'year', paidAt, cancelAt: new Date(2026, 11, 20) }).amount).toBe(0)
  })
})
