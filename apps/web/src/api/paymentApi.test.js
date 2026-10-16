import { describe, expect, it } from 'vitest'
import { readPaymentReturn } from './paymentApi.js'

describe('payment return address', () => {
  it('reads a successful return from the Toss test payment window', () => {
    expect(readPaymentReturn('?pay=success&paymentKey=tgen_1&orderId=aira-1&amount=99000&paymentType=NORMAL'))
      .toEqual({ kind: 'success', paymentKey: 'tgen_1', orderId: 'aira-1', amount: 99000 })
  })

  it('reads a failed or closed payment with Toss message', () => {
    expect(readPaymentReturn('?pay=fail&code=PAY_PROCESS_CANCELED&message=%EC%B7%A8%EC%86%8C'))
      .toEqual({ kind: 'fail', code: 'PAY_PROCESS_CANCELED', message: '취소' })
  })

  it('ignores ordinary visits and incomplete returns', () => {
    expect(readPaymentReturn('')).toBeNull()
    expect(readPaymentReturn('?pay=success&orderId=aira-1')).toBeNull()
  })
})
