import { request } from './http.js'

const JSON_HEADERS = { 'Content-Type': 'application/json' }
export const getPaymentConfig = () => request('/api/payments/config')
export const getMyPlan = () => request('/api/me/payments/plan')
export const createPaymentOrder = cycle => request('/api/me/payments/orders', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ cycle }) })
export const confirmPayment = ({ paymentKey, orderId, amount }) => request('/api/me/payments/confirm', {
  method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ paymentKey, orderId, amount: Number(amount) }) })
export const cancelPlan = () => request('/api/me/payments/cancel', { method: 'POST', headers: JSON_HEADERS, body: '{}' })

const TOSS_SDK = 'https://js.tosspayments.com/v1/payment'

/** 토스페이먼츠 결제창 SDK(v1)를 한 번만 불러온다. */
export function loadTossPayments() {
  if (typeof window === 'undefined') return Promise.reject(new Error('no window'))
  if (window.TossPayments) return Promise.resolve(window.TossPayments)
  return new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[src="${TOSS_SDK}"]`)
    const script = existing ?? document.createElement('script')
    script.addEventListener('load', () => window.TossPayments ? resolve(window.TossPayments) : reject(new Error('sdk missing')))
    script.addEventListener('error', () => reject(new Error('sdk failed')))
    if (!existing) { script.src = TOSS_SDK; script.async = true; document.head.appendChild(script) }
  })
}

/** 결제창에서 돌아온 주소(/plans?pay=success&paymentKey=…)를 읽는다. 결제와 상관없는 주소면 null. */
export function readPaymentReturn(search) {
  const params = new URLSearchParams(search ?? '')
  const pay = params.get('pay')
  if (pay === 'success' && params.get('paymentKey') && params.get('orderId') && params.get('amount')) {
    return { kind: 'success', paymentKey: params.get('paymentKey'), orderId: params.get('orderId'), amount: Number(params.get('amount')) }
  }
  if (pay === 'fail') return { kind: 'fail', code: params.get('code') ?? '', message: params.get('message') ?? '' }
  return null
}
