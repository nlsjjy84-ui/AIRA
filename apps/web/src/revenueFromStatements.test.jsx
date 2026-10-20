import { describe, expect, it } from 'vitest'
import { revenueFromStatements } from './StockPage.jsx'

const row = (account, current, previous = 1, beforePrevious = 1) => ({ account, current, previous, beforePrevious })

describe('주요계정에 매출이 없을 때 손익계산서 원문에서 꺼내기', () => {
  it('일반 회사는 원문의 매출액 줄을 그대로 쓴다', () => {
    const r = revenueFromStatements({ groups: { CIS: [row('영업이익(손실)', -7), row('매출액', 59259188126), row('매출원가', 4)] } })
    expect(r.kind).toBe('revenue')
    expect(r.rows[0].account).toBe('매출액')
  })
  it('은행처럼 번호가 붙은 「I. 영업수익」도 찾는다', () => {
    const r = revenueFromStatements({ groups: { CIS: [row('IV. 영업이익', 1), row('I. 영업수익', 3086291000000), row('1. 이자수익', 2)] } })
    expect(r.kind).toBe('revenue')
    expect(r.rows[0].account).toBe('I. 영업수익')
  })
  it('영업수익 줄이 없는 금융지주는 이자 · 수수료 · 보험 수익 줄을 이름 그대로 모은다', () => {
    const r = revenueFromStatements({ groups: { CIS: [row('보험수익', 12), row('수수료수익', 5), row('순이자손익', 13), row('이자수익', 20), row('보험수익', 11)] } })
    expect(r.kind).toBe('financial')
    expect(r.rows.map(x => x.account)).toEqual(['이자수익', '순이자손익', '수수료수익', '보험수익'])
  })
  it('찾을 줄이 없으면 아무것도 지어내지 않는다', () => {
    expect(revenueFromStatements({ groups: { CIS: [row('영업이익', 1)] } })).toBeNull()
    expect(revenueFromStatements(null)).toBeNull()
  })
})
