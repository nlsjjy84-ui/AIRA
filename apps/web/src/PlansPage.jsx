import { useCallback, useEffect, useState } from 'react'
import { getPlan } from './api/stockApi.js'
import { PRICE, planQuote, refundOf, won, ymd } from './planQuote.js'
import { cancelPlan, confirmPayment, createPaymentOrder, getMyPlan, getPaymentConfig, loadTossPayments, readPaymentReturn } from './api/paymentApi.js'

/**
 * 요금 안내 → 플랜 고르기 → 결제 직전 확인 → 토스페이먼츠 테스트 결제창 → 서버 승인 → 플러스 적용 → 해지·환불.
 * 테스트 모드라 실제 돈은 오가지 않는다. 결제 금액은 서버가 주문을 만들 때 정하고, 승인 때 다시 맞춰 본다.
 * 결제 직전 화면에서 낼 돈·부가세·다음 결제일·자동 갱신·환불 기준을 한 번에 보여 주고, 동의를 받은 뒤에만 버튼이 켜진다.
 */

// 무료와 AIRA 플러스의 차이(가로: 무료 / 플러스)
const FEATURES = [
  ['종목 화면 · 그래프 · 재무제표 원문', '모두', '모두'],
  ['공시 원문 문장 찾기 · 사실 확인', '모두', '모두'],
  ['회사 읽기 코스 · 용어 사전 · 퀴즈', '모두', '모두'],
  ['AI 쉬운 설명 · 「왜 그럴까」', '하루 5번', '무제한'],
  ['AI 조건 찾기(말로 종목 고르기)', '하루 5번에 포함', '무제한'],
  ['공부 노트', '30개', '무제한'],
  ['가격 알림', '3개', '무제한'],
  ['회사 공부 리포트(노트 + 공식 숫자 한 문서)', '—', '있음'],
  ['재무 · 조건 검색 결과 내려받기', '—', '있음'],
  ['관심 회사 새 공시 아침 요약', '—', '있음'],
]

function Usage({ loggedIn }) {
  const [plan, setPlan] = useState(null)
  useEffect(() => { if (loggedIn) getPlan().then(setPlan).catch(() => setPlan(null)) }, [loggedIn])
  if (!loggedIn) return <p className="stock-hint">로그인하면 내 사용량(오늘 AI 설명·공부 노트·가격 알림)을 여기서 볼 수 있어요.</p>
  if (!plan) return null
  const rows = [['오늘 AI 설명', plan.ai], ['공부 노트', plan.notes], ['가격 알림', plan.alerts]]
  return <section className="plan-usage" aria-label="내 사용량">
    <h2 className="stock-h3">내 사용량 <small className="muted">{plan.demo ? '공용 데모 계정은 한도를 걸지 않아요' : plan.enforced ? '무료 한도 적용 중' : '무료 한도는 아직 걸지 않고 세기만 해요'}</small></h2>
    <ul>{rows.map(([label, v]) => <li key={label}><span>{label}</span>
      <div className="usage-bar" aria-hidden="true"><span style={{ width: `${Math.min(100, v.used / v.freeLimit * 100)}%` }} /></div>
      <strong>{v.used} / 무료 {v.freeLimit}</strong></li>)}</ul>
  </section>
}

function CycleSwitch({ cycle, setCycle }) {
  return <div className="cycle-switch" role="radiogroup" aria-label="결제 주기">
    {[['month', '월마다'], ['year', '1년에 한 번 · 두 달 무료']].map(([key, label]) =>
      <button key={key} type="button" role="radio" aria-checked={cycle === key} className={cycle === key ? 'on' : ''} onClick={() => setCycle(key)}>{label}</button>)}
  </div>
}

function Choose({ cycle, setCycle, onPick }) {
  const plus = PRICE.plus[cycle]
  const cls = PRICE.class[cycle]
  return <>
    <CycleSwitch cycle={cycle} setCycle={setCycle} />
    <div className="plans-grid">
      <article className="plan-card free"><h2>무료</h2>
        <p className="plan-price">0원</p>
        <p className="muted">공식 자료를 보고 확인하는 기능은 모두 무료예요. AI 설명만 하루 5번까지.</p>
        <p className="plan-note">가입만 하면 바로 써요</p></article>
      <article className="plan-card plus"><h2>AIRA 플러스</h2>
        <p className="plan-price">{won(plus)} <small>/ {cycle === 'year' ? '년' : '월'}</small></p>
        <p className="muted">{cycle === 'year' ? `월 ${won(plus / 12)}꼴 · 월 결제보다 ${won(PRICE.plus.month * 12 - plus)} 덜 내요` : `1년 결제면 ${won(PRICE.plus.year)}(월 ${won(PRICE.plus.year / 12)}꼴)`}</p>
        <button type="button" className="primary-action" onClick={() => onPick('plus')}>AIRA 플러스 시작하기</button></article>
      <article className="plan-card class"><h2>공부 반 (강사·스터디)</h2>
        <p className="plan-price">수강생 1명당 {won(cls)} <small>/ {cycle === 'year' ? '년' : '월'}</small></p>
        <p className="muted">반 만들기 · 초대 코드 · 같이 읽기 과제 · 진도표. 수강생은 플러스 기능을 함께 써요. {PRICE.class.minSeats}명부터.</p>
        <button type="button" className="secondary-action" onClick={() => onPick('class')}>공부 반 만들기</button></article>
    </div>
    <div className="table-scroll"><table className="plan-compare">
      <caption>무료와 AIRA 플러스 비교</caption>
      <thead><tr><th scope="col">기능</th><th scope="col">무료</th><th scope="col">AIRA 플러스</th></tr></thead>
      <tbody>{FEATURES.map(([name, free, plus]) => <tr key={name}><th scope="row">{name}</th><td>{free}</td><td className={free !== plus ? 'diff' : ''}>{plus}</td></tr>)}</tbody>
    </table></div>
  </>
}

function Checkout({ plan, cycle, setCycle, onBack, loggedIn, payment }) {
  const [seats, setSeats] = useState(PRICE.class.minSeats)
  const [agree, setAgree] = useState({ order: false, refund: false })
  const [paying, setPaying] = useState({ busy: false, error: null })
  const q = planQuote({ plan, cycle, seats })
  const name = plan === 'plus' ? 'AIRA 플러스' : '공부 반'
  const yearRefund = refundOf({ plan, cycle: 'year', seats: q.seats, paidAt: new Date(2026, 0, 1), cancelAt: new Date(2026, 2, 10) })
  const ready = agree.order && agree.refund
  const canPay = plan === 'plus' && payment?.enabled && loggedIn
  async function pay() {
    setPaying({ busy: true, error: null })
    try {
      const order = await createPaymentOrder(cycle)
      const TossPayments = await loadTossPayments()
      const base = `${window.location.origin}/plans`
      await TossPayments(payment.clientKey).requestPayment('카드', {
        amount: order.amount, orderId: order.orderId, orderName: order.orderName,
        successUrl: `${base}?pay=success`, failUrl: `${base}?pay=fail` })
    } catch (error) {
      const closed = error?.code === 'USER_CANCEL'
      setPaying({ busy: false, error: closed ? '결제창을 닫았어요. 다시 누르면 처음부터 결제할 수 있어요.' : (error?.message || '결제창을 열지 못했어요. 잠시 뒤 다시 시도해 주세요.') })
    }
  }
  return <section className="checkout" aria-labelledby="checkout-title">
    <button type="button" className="text-action" onClick={onBack}>← 플랜 다시 고르기</button>
    <h2 id="checkout-title">결제 전에 확인해 주세요</h2>
    <CycleSwitch cycle={cycle} setCycle={setCycle} />
    {plan === 'class' && <label className="seat-input">수강생 수
      <input type="number" min={PRICE.class.minSeats} max={PRICE.class.maxSeats} value={seats} onChange={e => setSeats(e.target.value)} />
      <small className="muted">{PRICE.class.minSeats}~{PRICE.class.maxSeats}명. 반 운영 중에 늘리면 남은 기간만큼만 더 내요.</small></label>}
    <dl className="order-sum">
      <div><dt>상품</dt><dd>{name}{plan === 'class' ? ` · 수강생 ${q.seats}명` : ''} · {cycle === 'year' ? '1년' : '1개월'}</dd></div>
      <div><dt>오늘 낼 돈</dt><dd className="big">{won(q.total)}</dd></div>
      <div><dt>공급가 · 부가세</dt><dd>{won(q.supply)} + {won(q.vat)}</dd></div>
      {q.saving > 0 && <div><dt>월 결제보다</dt><dd>{won(q.saving)} 덜 내요(월 {won(q.perMonth)}꼴)</dd></div>}
      <div><dt>다음 결제일</dt><dd>{ymd(q.nextCharge)} · 같은 금액으로 자동 갱신</dd></div>
      <div><dt>전액 환불 기한</dt><dd>{ymd(q.refundUntil)}까지(7일)</dd></div>
    </dl>
    <p className="pay-method">결제 수단: 신용·체크카드와 간편결제는 토스페이먼츠 결제창에서 골라요.</p>
    <div className="refund-rule">
      <h3 className="stock-h3">해지와 환불</h3>
      <ul>
        <li>결제하고 7일 안에 해지하면 기능을 썼어도 전액 돌려드려요.</li>
        <li>{cycle === 'month' ? '7일이 지나 해지하면 다음 결제일부터 끝나고, 남은 기간은 그대로 써요.' : `7일이 지나 해지하면 쓴 달을 월 요금(${won(PRICE[plan].month * q.seats)})으로 빼고 돌려드려요. 예: 1월 1일 결제 → 3월 10일 해지 → ${won(yearRefund.amount)} 환불.`}</li>
        <li>해지는 「내 공부 → 결제 관리」에서 버튼 하나로 해요. 상담원 연결이나 설문을 거치지 않아요.</li>
        <li>자동 갱신 7일 전에 금액과 날짜를 메일로 먼저 알려드려요. 값이 바뀌면 미리 다시 동의를 받아요.</li>
      </ul>
    </div>
    <div className="agree">
      <label><input type="checkbox" checked={agree.order} onChange={e => setAgree({ ...agree, order: e.target.checked })} /> (필수) 주문 내용과 {ymd(q.nextCharge)} 자동 갱신을 확인했어요</label>
      <label><input type="checkbox" checked={agree.refund} onChange={e => setAgree({ ...agree, refund: e.target.checked })} /> (필수) 해지·환불 기준을 확인했어요</label>
    </div>
    <button type="button" className="primary-action pay-btn" disabled={!canPay || !ready || paying.busy} aria-describedby="pay-off" onClick={pay}>
      {paying.busy ? '결제창을 여는 중…' : `${won(q.total)} 테스트 결제하기`}</button>
    {paying.error && <p className="ai-assist-note" role="alert">{paying.error}</p>}
    <p id="pay-off" className="stock-hint">{plan !== 'plus' ? '공부 반 결제는 준비 중이에요. 지금은 AIRA 플러스만 테스트 결제를 해 볼 수 있어요.'
      : !payment?.enabled ? '테스트 결제가 아직 켜져 있지 않아요.'
      : !loggedIn ? '로그인하거나 데모로 둘러보기를 누르면 테스트 결제를 해 볼 수 있어요.'
      : ready ? '토스페이먼츠 테스트 결제창이 열려요. 실제로 돈이 나가지 않아요.' : '두 가지에 동의하면 결제 버튼이 켜져요.'}</p>
  </section>
}

function fmtDate(value) {
  if (!value) return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? '—' : ymd(d)
}

/** 결제창에서 돌아온 결과와 내 AIRA 플러스(기간·해지). */
function PaymentPanel({ loggedIn, returned, onDone }) {
  const [state, setState] = useState({ status: returned?.kind === 'success' ? 'confirming' : 'idle', result: null, error: returned?.kind === 'fail' ? (returned.message || '결제를 마치지 못했어요.') : null })
  const [mine, setMine] = useState(null)
  const [cancel, setCancel] = useState({ asking: false, busy: false, result: null, error: null })

  useEffect(() => {
    if (returned?.kind !== 'success') return undefined
    let alive = true
    confirmPayment(returned).then(result => { if (alive) { setState({ status: 'done', result, error: null }); setMine(result.plan) } })
      .catch(error => alive && setState({ status: 'error', result: null, error: error?.message === 'Request failed' ? '결제를 승인하지 못했어요. 금액이 주문과 다르거나 결제가 거절됐어요.' : (error?.message || '결제를 승인하지 못했어요.') }))
      .finally(() => onDone?.())
    return () => { alive = false }
  }, [returned, onDone])

  useEffect(() => {
    if (!loggedIn || returned?.kind === 'success') return
    getMyPlan().then(setMine).catch(() => setMine(null))
  }, [loggedIn, returned])

  async function doCancel() {
    setCancel(c => ({ ...c, busy: true, error: null }))
    try {
      const result = await cancelPlan()
      setCancel({ asking: false, busy: false, result, error: null })
      setMine(result.plan)
    } catch {
      setCancel(c => ({ ...c, busy: false, error: '해지하지 못했어요. 잠시 뒤 다시 시도해 주세요.' }))
    }
  }

  const result = state.result
  return <>
    {state.status === 'confirming' && <p className="status" role="status">결제를 확인하는 중이에요. 창을 닫지 마세요.</p>}
    {state.error && <div className="pay-result is-fail" role="alert"><h2 className="stock-h3">결제를 마치지 못했어요</h2><p>{state.error}</p></div>}
    {result && <div className="pay-result" role="status">
      <h2 className="stock-h3">{result.demoCanceled ? '테스트 결제가 끝까지 됐어요 · 데모 계정이라 바로 취소했어요' : '테스트 결제 완료 · AIRA 플러스가 켜졌어요'}</h2>
      <dl className="order-sum">
        <div><dt>상품</dt><dd>{result.orderName}</dd></div>
        <div><dt>결제 금액</dt><dd>{won(result.amount)} <small className="muted">(테스트 결제라 실제로 나간 돈은 없어요)</small></dd></div>
        <div><dt>주문 번호</dt><dd>{result.orderId}</dd></div>
        {!result.demoCanceled && result.plan?.endsAt && <div><dt>이용 기간</dt><dd>{fmtDate(result.plan.startedAt)} ~ {fmtDate(result.plan.endsAt)}</dd></div>}
      </dl>
      {result.demoCanceled && <p className="stock-hint">여럿이 함께 쓰는 데모 계정이라 승인 직후 전액 취소하고 플러스는 켜지 않았어요. 가입하면 내 계정에서 플러스가 켜지고 해지까지 해 볼 수 있어요.</p>}
    </div>}
    {mine?.plan === 'PLUS' && <section className="my-plan" aria-label="내 AIRA 플러스">
      <h2 className="stock-h3">내 AIRA 플러스 <small className="muted">테스트 결제</small></h2>
      <p>{mine.cycle === 'year' ? '1년' : '1개월'} · {fmtDate(mine.startedAt)} ~ {fmtDate(mine.endsAt)}{mine.canceled ? ' · 해지함(기간이 끝나면 무료로 돌아가요)' : ''}</p>
      {!mine.canceled && !cancel.asking && <button type="button" className="secondary-action" onClick={() => setCancel(c => ({ ...c, asking: true }))}>해지하기</button>}
      {cancel.asking && <div className="cancel-confirm" role="group" aria-label="해지 확인">
        <p>정말 해지할까요? 7일 안이면 전액, 그 뒤 연 결제는 쓴 달을 빼고 돌려드려요.</p>
        <button type="button" className="primary-action" disabled={cancel.busy} onClick={doCancel}>{cancel.busy ? '해지하는 중…' : '네, 해지할게요'}</button>
        <button type="button" className="text-action" onClick={() => setCancel(c => ({ ...c, asking: false }))}>그대로 둘게요</button>
      </div>}
    </section>}
    {cancel.result && <p className="pay-result" role="status">해지했어요. 돌려받는 돈 {won(cancel.result.refund)} · {cancel.result.reason}</p>}
    {cancel.error && <p className="ai-assist-note" role="alert">{cancel.error}</p>}
  </>
}

export default function PlansPage({ loggedIn = false }) {
  const [cycle, setCycle] = useState('year')
  const [plan, setPlan] = useState(null)
  const [payment, setPayment] = useState(null)
  const [returned] = useState(() => typeof window === 'undefined' ? null : readPaymentReturn(window.location.search))
  useEffect(() => { getPaymentConfig().then(setPayment).catch(() => setPayment(null)) }, [])
  const clearReturn = useCallback(() => {
    if (typeof window !== 'undefined' && window.location.search) window.history.replaceState(window.history.state, '', '/plans')
  }, [])
  const pick = key => { setPlan(key); if (typeof window !== 'undefined') window.scrollTo?.(0, 0) }
  return <section className="plans-page" aria-labelledby="plans-title">
    <header><p className="eyebrow">AIRA 요금 안내 · AIRA 플러스 판매 중</p>
      <h1 id="plans-title">숫자는 누구에게나, 이해는 더 깊게</h1>
      <p>AIRA는 「사라·팔라」를 말하지 않고 광고도 싣지 않습니다. 대신 내가 이해하고 쌓아 가는 데 드는 AI와 기록 기능을 유료로 둡니다.
        <strong>AIRA 플러스 판매 중</strong>이에요(가격은 부가세 포함). 결제는 <strong>토스페이먼츠 테스트 모드</strong>라 실제로 돈이 나가지 않아요.
        무료 한도는 아직 걸지 않아 지금은 모든 기능을 무료로 쓸 수 있어요.</p></header>
    <PaymentPanel loggedIn={loggedIn} returned={returned} onDone={clearReturn} />
    <Usage loggedIn={loggedIn} />
    {plan ? <Checkout plan={plan} cycle={cycle} setCycle={setCycle} onBack={() => setPlan(null)} loggedIn={loggedIn} payment={payment} /> : <Choose cycle={cycle} setCycle={setCycle} onPick={pick} />}
    <section className="stock-learn" aria-label="왜 이렇게 나눴나"><div>
      <h2 className="stock-h3">왜 이렇게 나눴나요?</h2>
      <p>공식 자료를 보는 것은 누구에게나 열려 있어야 한다고 생각해서 무료로 둡니다. 돈을 받는 부분은 AI 설명처럼 쓸 때마다 비용이 드는 기능과,
        내가 쌓은 공부를 정리해 주는 기능입니다. 광고나 추천으로 돈을 벌면 사용자의 판단보다 광고주의 이익을 앞세우게 되기 때문입니다.</p>
      <p className="muted">거래소 시세는 이용약관상 파일로 다시 나눠 줄 수 없어 내려받기에서 뺐습니다. 상용 출시 때는 거래소 데이터 이용 계약을 맺습니다.</p>
    </div></section>
  </section>
}
