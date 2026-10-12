import { PLANS } from './stockGuide.js'

/** 무료·유료 안내. 결제는 아직 붙이지 않았고, 지금은 모든 기능을 무료로 써 볼 수 있다. */
export default function PlansPage() {
  return <section className="plans-page" aria-labelledby="plans-title">
    <header><p className="eyebrow">AIRA 요금 안내 · 출시 계획</p>
      <h1 id="plans-title">광고도, 종목 추천도 없이 이해를 돕는 데에만 돈을 받습니다</h1>
      <p>AIRA는 「사라·팔라」를 말하지 않고 광고도 싣지 않습니다. 대신 내가 이해하고 쌓아 가는 데 드는 AI와 기록 기능을 유료로 둡니다.
        지금은 출시 전이라 <strong>모든 기능을 무료로 열어 두었습니다.</strong></p></header>
    <div className="plans-grid">{PLANS.map(plan => <article key={plan.key} className={`plan-card ${plan.key}`}>
      <h2>{plan.name}</h2><p className="plan-price">{plan.price}</p>
      <ul>{plan.lines.map(line => <li key={line}>{line}</li>)}</ul></article>)}</div>
    <section className="stock-learn" aria-label="왜 이렇게 나눴나"><div>
      <h2 className="stock-h3">왜 이렇게 나눴나요?</h2>
      <p>공식 자료를 보는 것은 누구에게나 열려 있어야 한다고 생각해서 무료로 둡니다. 돈을 받는 부분은 AI 설명처럼 쓸 때마다 비용이 드는 기능과,
        내가 쌓은 공부를 정리해 주는 기능입니다. 광고나 추천으로 돈을 벌면 사용자의 판단보다 광고주의 이익을 앞세우게 되기 때문입니다.</p>
      <p className="muted">거래소 시세는 이용약관상 파일로 다시 나눠 줄 수 없어 내려받기에서 뺐습니다. 상용 출시 때는 거래소 데이터 이용 계약을 맺습니다.</p>
    </div></section>
  </section>
}
