import { useCallback, useEffect, useState } from 'react'
import { createPriceAlert, deletePriceAlert, getPriceAlerts } from './api/stockApi.js'
import { formatPrice } from './stockMath.js'

const ERRORS = { INVALID_TARGET: '가격을 확인해 주세요.', NO_SECURITY: '이 회사는 종목 코드를 찾지 못했습니다.', TOO_MANY: '가격 알림은 50개까지 만들 수 있어요.' }

function describe(alert) {
  const condition = `종가가 ${formatPrice(alert.target)} ${alert.direction === 'ABOVE' ? '이상' : '이하'}`
  return alert.triggeredAt ? `🔔 ${alert.triggeredDate} 종가 ${formatPrice(alert.triggeredClose)} — ${condition} 조건에 닿았어요` : `⏳ ${condition}이 되면 알려 드려요`
}

/** 종목 화면: 이 종목의 종가 기준 가격 알림 만들기·보기. 가격은 사용자가 정하며 AIRA가 제안하지 않는다. */
export function StockPriceAlerts({ companyId, lastClose }) {
  const [alerts, setAlerts] = useState([])
  const [form, setForm] = useState({ direction: 'ABOVE', target: '', message: null, busy: false })
  const load = useCallback(() => { getPriceAlerts().then(list => setAlerts((list ?? []).filter(a => a.companyId === companyId))).catch(() => {}) }, [companyId])
  useEffect(load, [load])
  async function submit(event) {
    event.preventDefault()
    const target = Number(String(form.target).replaceAll(',', ''))
    if (!(target > 0)) { setForm(f => ({ ...f, message: '가격을 숫자로 적어 주세요.' })); return }
    setForm(f => ({ ...f, busy: true, message: null }))
    try { await createPriceAlert(companyId, form.direction, target); setForm({ direction: form.direction, target: '', busy: false, message: '알림을 만들었어요.' }); load() }
    catch (error) { setForm(f => ({ ...f, busy: false, message: error.status === 423 ? '데모 계정은 알림을 만들 수 없어요.' : ERRORS[error.code] ?? '만들지 못했습니다.' })) }
  }
  async function remove(id) { try { await deletePriceAlert(id); load() } catch { /* 다음에 다시 시도 */ } }
  return <details className="stock-note"><summary>🔔 가격 알림{alerts.length ? ` (${alerts.length})` : ''}</summary>
    <form className="price-alert-form" onSubmit={submit}>
      <span>종가가</span>
      <input inputMode="numeric" value={form.target} placeholder={lastClose ? String(Math.round(Number(lastClose))) : '가격'} aria-label="알림 가격"
        onChange={event => setForm(f => ({ ...f, target: event.target.value }))} />
      <span>원</span>
      <select value={form.direction} aria-label="조건" onChange={event => setForm(f => ({ ...f, direction: event.target.value }))}>
        <option value="ABOVE">이상</option><option value="BELOW">이하</option></select>
      <span>이 되면</span>
      <button type="submit" className="secondary-action" disabled={form.busy}>알림 만들기</button>
    </form>
    {form.message && <small className="muted">{form.message}</small>}
    {alerts.length > 0 && <ul className="price-alert-list">{alerts.map(a => <li key={a.id}><span>{describe(a)}</span>
      <button type="button" className="link-button" onClick={() => remove(a.id)}>지우기</button></li>)}</ul>}
    <p className="stock-hint">하루에 한 번, 거래소 공식 종가가 들어온 뒤 확인합니다(실시간 아님). 알림은 첫 화면 「알림」에서도 볼 수 있어요.</p>
  </details>
}

/** 첫 화면 알림 칸: 내 가격 알림 모아 보기. */
export function HomePriceAlerts() {
  const [alerts, setAlerts] = useState(null)
  useEffect(() => { getPriceAlerts().then(list => setAlerts(list ?? [])).catch(() => setAlerts([])) }, [])
  if (!alerts || alerts.length === 0) return null
  const rung = alerts.filter(a => a.triggeredAt)
  return <div className="interest-filings"><h3 className="stock-h3">내 가격 알림 · 닿은 알림 {rung.length}개</h3>
    <ul className="price-alert-list">{alerts.slice(0, 12).map(a => <li key={a.id} className={a.triggeredAt ? 'rung' : ''}>
      <a href={`/stock/${a.companyId}`}><strong>{a.companyName}</strong></a><span>{describe(a)}</span></li>)}</ul></div>
}
