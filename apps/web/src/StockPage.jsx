import { useCallback, useEffect, useMemo, useState } from 'react'
import { getStockOverview, getStockPrices, getStockFinancials, getStockFilings, getStockNews, getMarketIndexSeries, getStockReportDetails, getStockPeers, getStockQuarters, getStockOwnership, getStockCashFlow, getStockNote, putStockNote } from './api/stockApi.js'
import { getCompanyEvents } from './api/companyApi.js'
import { searchEntities } from './api/canonicalApi.js'
import { getScreener } from './api/stockApi.js'
import { screenRow } from './ScreenerPage.jsx'
import { LineChart, BarChart, CandleChart } from './StockCharts.jsx'
import ClaimCheck from './ClaimCheck.jsx'
import FilingQuote from './FilingQuote.jsx'
import AiAssist from './AiAssist.jsx'
import { requestAssist, requestQuote } from './api/assistApi.js'
import { StockPriceAlerts } from './PriceAlerts.jsx'
import SaveToStudy from './SaveToStudy.jsx'
import { getStudy, learnTerm, getStockSection, getStockStatements, saveStudyNote } from './api/stockApi.js'
import { guideFor, SECTION_META, sectionColumns, visibleRows, orderStatement, COURSE, coursePrefix, courseDone, buildCalendar } from './stockGuide.js'
import {
  ACCOUNT_LABEL, FILING_CATEGORIES, FILING_LABEL, QUESTION_ROUTES, RATIOS, aroundFilings, checklist, fixSubjectParticle,
  formatCount, formatPercent, formatPrice, formatWon, lastDays, normalizeTogether, priceStats, routeQuestion, withRatios, yearOverYear,
  GLOSSARY, VALUATIONS, valuation, rememberStock, movingAverage, dartNumber, aggregateCandles, industryName, peerMetrics, PEER_METRICS, peerPosition, RADAR_AXES, percentileOf, ratioText, downloadCsv, buildQuiz, spanName, normalizeMany,
} from './stockMath.js'

const COLORS = { a: 'var(--series-a)', b: 'var(--series-b)', c: 'var(--series-c)', d: 'var(--series-d)', n: 'var(--series-neutral)', up: 'var(--up)', down: 'var(--down)' }
const CATEGORY_COLOR = { PERIODIC: COLORS.b, EARNINGS: COLORS.a, OWNERSHIP: COLORS.d, MATERIAL: COLORS.c, IR: 'var(--stage-main)', OTHER: COLORS.n }
const RANGES = [['30', '1개월'], ['90', '3개월'], ['180', '6개월'], ['365', '1년']]

/** 메뉴 나무. 각 칸: [키, 이름, 여기서 알 수 있는 것, 하위 칸] — 하위 칸이 없으면 그 칸이 내용 화면이다. */
export const STOCK_TREE = [
  ['ask', 'Ask', '묻다', '궁금한 것을 말로 물어 맞는 화면을 찾고, 들은 말이 사실인지 공식 값으로 확인합니다.', [
    ['ai', 'AI에게 묻기', '이 회사의 공식 값만 가지고 AI가 답합니다. 자료에 없는 것은 모른다고 하고, 사라·팔라는 말은 하지 않습니다.'],
    ['course', '회사 읽기 코스', '회사를 읽는 기본 순서 10단계입니다. 따라가며 본 것을 한 줄씩 적으면 「내 공부」에 쌓입니다.'],
    ['faq', '자주 묻는 질문', '처음 오신 분이 많이 궁금해하는 질문입니다. 누르면 답이 있는 화면으로 바로 갑니다.'],
    ['find', '질문으로 찾기', '궁금한 것을 낱말로 적으면 관련 화면을 골라 드립니다(AI가 아닌 낱말 맞추기).'],
    ['claim', '이 말, 사실이야?', '기사나 커뮤니티에서 본 숫자를 붙여 넣으면 공식 값과 접수번호로 맞는지 확인합니다.'],
    ['quote', '공시 원문에 묻기', '사업보고서 원문에서 질문과 맞는 문장을 글자 그대로 찾아 보여 줍니다.'],
    ['glossary', '용어 사전', '화면에 나오는 용어를 한곳에 모았습니다. 공부하듯 찾아볼 수 있습니다.'],
  ]],
  ['inspect', 'Inspect', '보다', '모아 둔 공식 자료를 분류별로 펼쳐 봅니다. 그래프·표·용어 풀이가 함께 있습니다.', [
    ['summary', '한눈에', '주가·실적·재무·시장 평가·최근 공시를 한 화면에 모았습니다. 칸을 누르면 그 자료로 깊게 들어갑니다.'],
    ['company', '회사 개요', '어떤 회사인지, 누가 이끌고 장부는 믿을 만한지 봅니다.', [['basic', '기본 정보'], ['listing', '상장 정보'], ['audit', '감사의견'], ['execs', '임원'], ['pay', '임원 보수'], ['affiliates', '다른 회사 출자'], ['shares', '주식 수']]],
    ['price', '주가', '한국거래소(KRX)의 하루 단위 공식 시세입니다. 실시간 시세가 아니며, 지금은 약 6개월치를 보관합니다.',
      [['trend', '가격 흐름'], ['candle', '봉 차트'], ['daily', '일별 시세'], ['volume', '거래량·거래대금'], ['cap', '시가총액'], ['range', '기간 최고·최저']]],
    ['earnings', '실적(손익)', '1년 동안 얼마를 팔고 얼마를 남겼는지 사업보고서 기준으로 봅니다.',
      [['REVENUE', '매출'], ['OPERATING_INCOME', '영업이익'], ['NET_INCOME', '당기순이익'], ['quarters', '분기 실적'], ['all', '한 표로 보기']]],
    ['balance', '재무상태', '연말 기준으로 회사가 가진 것(자산)과 빚(부채), 내 돈(자본)을 봅니다.',
      [['structure', '자산·부채·자본'], ['liquidity', '유동자산·유동부채'], ['cash', '현금흐름'], ['bonds', '회사채 만기']]],
    ['statements', '재무제표 원문', '사업보고서의 재무제표 네 가지를 한 줄도 빼지 않고 그대로 봅니다.',
      [['BS', '재무상태표'], ['IS', '손익계산서'], ['CF', '현금흐름표'], ['SCE', '자본변동표']]],
    ['ratios', '비율', '재무 값으로 계산한 비율입니다. 같은 회사의 해마다 변화를 보는 데 씁니다.',
      [['수익성', '수익성'], ['안정성', '안정성'], ['활동성', '활동성'], ['성장성', '성장성'], ['시장 평가', '시장 평가(PER·PBR)']]],
    ['owners', '주주·배당·직원', '누가 주인인지, 주주에게 무엇을 돌려주는지(배당·자기주식), 주식 수가 어떻게 바뀌었는지, 직원은 어떤지 사업보고서로 봅니다.', [['dividend', '배당'], ['treasury', '자기주식'], ['holders', '최대주주·지분'], ['holderChange', '최대주주 변동'], ['capital', '증자·감자'], ['minority', '소액주주'], ['insiders', '임원·주요주주 거래'], ['major', '5% 대량보유'], ['employees', '직원']]],
    ['filings', '공시', '회사가 금융감독원 전자공시(DART)에 낸 최근 1년 공시입니다.', FILING_CATEGORIES.map(([k, l]) => [k, l])],
    ['news', '뉴스', '회사 이름이 들어간 최근 2주 기사 링크입니다. 기사 내용은 공식 근거가 아닙니다.', [['list', '기사 목록'], ['links', '더 찾아보기']]],
  ]],
  ['relate', 'Relate', '잇다', '서로 다른 자료를 같은 날짜·같은 기간으로 겹쳐 봅니다. 함께 나타났다는 것이지 원인이라는 뜻은 아닙니다.', [
    ['filings', '주가 × 공시', '주가 그래프 위에 공시 날짜를 점으로 찍어 봅니다.', [['chart', '그래프'], ['list', '날짜별 목록']]],
    ['market', '주가 × 시장', '같은 기간 이 종목과 시장 지수(코스피·코스닥)를 시작일 100으로 맞춰 비교합니다.', [['compare', '그래프'], ['table', '표']]],
    ['around', '공시 전후 주가', '공시 전날 종가와 5거래일 뒤 종가를 나란히 봅니다.', [['list', '목록']]],
    ['industry', '업종 안에서', '같은 업종(통계청 업종코드) 회사들과 규모·수익성·안정성·시장 평가를 나란히 봅니다.', [['table', '같은 업종 회사'], ['rank', '이 회사의 위치']]],
    ['peer', '여러 종목 비교', '다른 회사를 최대 3개 골라 4개 회사의 주가·실적·비율을 나란히 비교합니다.', [['compare', '나란히 보기']]],
    ['events', '관련 사건', 'AIRA가 공식 근거로 확인해 둔 이 회사의 사건입니다.', [['timeline', '시간순']]],
    ['calendar', '회사 일정', '보고서 제출 기한과 작년 같은 시기에 낸 공시로 다가올 일정을 봅니다.', [['upcoming', '다가올 일정'], ['past', '지난 1년']]],
  ]],
  ['assess', 'Assess', '가늠하다', '사실을 모아 스스로 가늠해 볼 수 있게 정리합니다. 사라·팔라는 말은 하지 않습니다.', [
    ['summary', '한눈에 정리', '최근 연도에 늘어난 것과 줄어든 것을 숫자로 모았습니다.'],
    ['radar', '다섯 방향 요약', '성장·수익성·효율·안정성·규모를 같은 업종 안 위치(백분위)로 한 도형에 그렸습니다. 점수나 등급이 아닙니다.'],
    ['yoy', '작년 대비 변화표', '항목마다 작년 값과 올해 값을 나란히 놓았습니다. 누르면 그 항목으로 더 들어갑니다.'],
    ['industry', '업종 중간값 대비', '이 회사의 비율을 같은 업종 회사들의 중간값과 나란히 놓았습니다.'],
    ['check', '확인 체크리스트', '많이 확인하는 기본 질문에 자료로 예·아니오를 답합니다. 좋다·나쁘다를 정하지 않습니다.'],
    ['quiz', '이해 확인 퀴즈', '이 회사의 실제 숫자로 낸 짧은 문제예요. 답을 고르면 왜 그런지 계산과 함께 알려 드리고, 맞힌 용어는 「내 공부」에 쌓여요.'],
    ['report', '공부 리포트', '공식 숫자와 내가 쌓은 노트를 한 장으로 묶어 PDF로 저장합니다(AIRA 플러스 기능 · 출시 전이라 지금은 무료).'],
    ['unknown', '아직 모르는 것', '이 화면만으로는 알 수 없는 것과 자료의 한계입니다.'],
  ]],
]

export function readPath() {
  if (typeof window === 'undefined') return ['inspect', 'summary']
  const raw = new URLSearchParams(window.location.search).get('v')
  return raw ? raw.split('.').filter(Boolean).slice(0, 8) : ['inspect', 'summary']
}

export function nodeFor(path) {
  const top = STOCK_TREE.find(node => node[0] === path[0]) ?? STOCK_TREE[1]
  const second = (top[4] ?? []).find(node => node[0] === path[1]) ?? top[4][0]
  const third = second[3] ? (second[3].find(node => node[0] === path[2]) ?? second[3][0]) : null
  return { top, second, third }
}

/** 서버가 다시 켜지는 중(배포·재시작)이면 잠깐 실패할 수 있어, 네트워크 오류·5xx는 두 번까지 다시 묻는다. */
export function withRetry(loader, waits = [2000, 5000]) {
  const attempt = n => loader().catch(error => {
    const transient = !error?.status || error.status >= 500
    if (!transient || n >= waits.length) throw error
    return new Promise(resolve => setTimeout(resolve, waits[n])).then(() => attempt(n + 1))
  })
  return attempt(0)
}

function useLoad(loader, deps) {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const reload = useCallback(() => {
    let alive = true
    setState(current => ({ ...current, loading: true, error: null }))
    withRetry(loader).then(data => alive && setState({ loading: false, data, error: null }))
      .catch(error => alive && setState({ loading: false, data: null, error }))
    return () => { alive = false }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  useEffect(reload, [reload])
  return [state, reload]
}

function Learn({ children, terms }) {
  return <details className="stock-learn"><summary>📘 읽는 법 · 용어 풀이</summary><div>{children}
    {terms && <dl>{terms.map(([term, text]) => <div key={term}><dt>{term}</dt><dd>{text}</dd></div>)}</dl>}</div></details>
}

function Source({ children }) { return <p className="stock-source">출처 · {children}</p> }

function Empty({ children, action }) {
  return <div className="stock-empty"><p>{children}</p>{action}</div>
}

export default function StockPage({ companyId, loggedIn = false, onOpenEvent, interest = null }) {
  const [path, setPath] = useState(readPath)
  const [range, setRange] = useState('365')
  const [overview] = useLoad(() => getStockOverview(companyId), [companyId])
  const [pricesState, reloadPrices] = useLoad(() => getStockPrices(companyId, 370), [companyId])
  const [finState, reloadFin] = useLoad(() => getStockFinancials(companyId), [companyId])
  const [filingState, reloadFilings] = useLoad(() => getStockFilings(companyId), [companyId])
  const market = overview.data?.market === 'KOSDAQ' ? 'KOSDAQ' : 'KOSPI'
  const [indexState] = useLoad(() => getMarketIndexSeries(market, 370), [market])
  const [eventState] = useLoad(() => getCompanyEvents(companyId), [companyId])
  const [newsState, setNewsState] = useState({ loading: false, data: null, error: null, asked: false })
  const [peerState, setPeerState] = useState({ loading: false, data: null, error: null, asked: false })
  const loadPeers = useCallback(() => {
    if (peerState.asked) return
    setPeerState({ loading: true, data: null, error: null, asked: true })
    getStockPeers(companyId).then(data => setPeerState({ loading: false, data, error: null, asked: true }))
      .catch(error => setPeerState({ loading: false, data: null, error, asked: true }))
  }, [companyId, peerState.asked])
  useEffect(() => { if (path[1] === 'industry' || path[1] === 'radar') loadPeers() }, [path, loadPeers])

  useEffect(() => {
    const onPop = () => { setPath(readPath()); setSteps(value => Math.max(0, value - 1)) }
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  const [steps, setSteps] = useState(0)
  const go = useCallback(next => {
    setSteps(value => value + 1)
    setPath(next)
    const url = new URL(window.location.href)
    url.searchParams.set('v', next.join('.'))
    window.history.pushState(window.history.state, '', url)
    if (typeof window.scrollTo === 'function') document.getElementById('stock-body')?.scrollIntoView?.({ block: 'start', behavior: 'smooth' })
  }, [])

  const loadNews = useCallback((force = false) => {
    if (newsState.asked && !force) return
    setNewsState({ loading: true, data: null, error: null, asked: true })
    getStockNews(companyId).then(data => {
      if (data?.state === 'BUSY') setTimeout(() => getStockNews(companyId).then(again => setNewsState({ loading: false, data: again, error: null, asked: true }))
        .catch(error => setNewsState({ loading: false, data: null, error, asked: true })), 7000)
      else setNewsState({ loading: false, data, error: null, asked: true })
    }).catch(error => setNewsState({ loading: false, data: null, error, asked: true }))
  }, [companyId, newsState.asked])
  useEffect(() => { if (path[0] === 'inspect' && path[1] === 'news') loadNews() }, [path, loadNews])

  const points = useMemo(() => pricesState.data?.points ?? [], [pricesState.data])
  const indexPoints = useMemo(() => indexState.data?.points ?? [], [indexState.data])
  const years = useMemo(() => withRatios(finState.data?.years ?? []), [finState.data])
  const filings = useMemo(() => filingState.data?.items ?? [], [filingState.data])
  const yearStats = useMemo(() => priceStats(lastDays(points, 365)), [points])
  const marketStats = useMemo(() => priceStats(lastDays(indexPoints, 365)), [indexPoints])
  const name = overview.data?.name ?? '종목'
  useEffect(() => { if (overview.data?.name) rememberStock(companyId, overview.data.name) }, [companyId, overview.data])
  const ctx = { peerState, companyId, name, overview, pricesState, reloadPrices, finState, reloadFin, filingState, reloadFilings, indexState, eventState,
    newsState, loadNews, points, indexPoints, years, filings, yearStats, marketStats, range, setRange, path, go, loggedIn, onOpenEvent, market }

  if (overview.error?.status === 404) return <section className="stock-page"><Empty>이 회사를 찾지 못했습니다. <a href="/explore">회사 검색으로 가기</a></Empty></section>

  const { top, second, third } = nodeFor(path)
  const crumbs = breadcrumbs(path, ctx)
  const last = points[points.length - 1]
  const latestYear = years[years.length - 1]

  return <section className="stock-page" aria-labelledby="stock-title">
    <header className="stock-header">
      <div className="stock-title-row">
        <div><p className="eyebrow">{overview.data?.market ?? '상장 정보 확인 중'}{overview.data?.shortCode ? ` · ${overview.data.shortCode}` : ''}</p>
          <h1 id="stock-title">{overview.loading ? '불러오는 중…' : name}</h1></div>
        {interest && <InterestButton interest={interest} />}
        {last && <div className="stock-price-now"><strong>{formatPrice(last.close)}</strong>
          <span className={Number(last.changeRate) > 0 ? 'up' : Number(last.changeRate) < 0 ? 'down' : ''}>{formatPercent(last.changeRate)}</span>
          <small>{last.date} 종가 · KRX</small></div>}
      </div>
      <dl className="stock-keyfacts">
        <div><dt>시가총액</dt><dd>{last ? formatWon(last.marketCap) : '—'}</dd></div>
        <div><dt>{spanName(yearStats).change} 주가 변화</dt><dd>{yearStats ? formatPercent(yearStats.change) : '—'}</dd></div>
        <div><dt>{spanName(yearStats).range} 범위</dt><dd>{yearStats ? `${formatPrice(yearStats.low.close)} ~ ${formatPrice(yearStats.high.close)}` : '—'}</dd></div>
        <div><dt>{latestYear ? `${latestYear.year} 매출` : '최근 매출'}</dt><dd>{latestYear ? formatWon(latestYear.values.REVENUE) : '—'}</dd></div>
        <div><dt>영업이익률</dt><dd>{latestYear ? formatPercent(latestYear.ratios.operatingMargin, { sign: false }) : '—'}</dd></div>
        <div><dt>PER · PBR</dt><dd>{(() => { const v = valuation(last, latestYear); return v ? `${v.per ?? '—'}배 · ${v.pbr ?? '—'}배` : '—' })()}</dd></div>
        <div><dt>최근 1년 공시</dt><dd>{filingState.data ? `${filings.length}건${filingState.data.truncated ? ' 이상' : ''}` : '—'}</dd></div>
      </dl>
      {(finState.error || pricesState.error || filingState.error) && <p className="stock-hint" role="status">일부 자료를 불러오지 못했어요(서버가 잠깐 다시 켜지는 중일 수 있어요).{' '}
        <button type="button" className="link-button" onClick={() => { if (finState.error) reloadFin(); if (pricesState.error) reloadPrices(); if (filingState.error) reloadFilings() }}>다시 불러오기</button></p>}
    </header>

    <Guide go={go} />
    {loggedIn && <div className="stock-tools"><NotePanel companyId={companyId} /><StockPriceAlerts companyId={companyId} lastClose={last?.close} /></div>}
    {last && latestYear && <AiAssist kind="INSPECT" save={{ companyId, companyName: name }} subject={`${name} · 한눈에`} loggedIn={loggedIn} facts={[
      { label: '종가', value: formatPrice(last.close), period: last.date }, { label: '시가총액', value: formatWon(last.marketCap), period: last.date },
      ...(yearStats ? [{ label: '1년 주가 변화', value: formatPercent(yearStats.change), period: `${yearStats.first.date} — ${yearStats.last.date}` }] : []),
      { label: '매출', value: formatWon(latestYear.values.REVENUE), period: `${latestYear.year} 사업연도` },
      { label: '영업이익', value: formatWon(latestYear.values.OPERATING_INCOME), period: `${latestYear.year} 사업연도` },
      { label: '부채비율', value: formatPercent(latestYear.ratios.debtRatio, { sign: false }), period: `${latestYear.year} 말` }]} />}

    <nav className="stock-stages" aria-label="AIRA 단계">
      {STOCK_TREE.map(([key, english, korean]) => <button key={key} type="button" className={`stock-stage stage-${key}`} aria-label={`${english} ${korean}`} aria-current={top[0] === key ? 'page' : undefined}
        onClick={() => go([key])}><span className="stage-initial">{english[0]}</span><span className="stage-rest">{english.slice(1)}</span><span className="stage-korean">{korean}</span></button>)}
    </nav>

    <nav className="stock-subnav" aria-label={`${top[2]} 분류`}>
      {top[4].map(node => <button key={node[0]} type="button" aria-current={second[0] === node[0] ? 'page' : undefined} onClick={() => go([top[0], node[0]])}>{node[1]}</button>)}
    </nav>
    {second[3] && <nav className="stock-chips" aria-label={`${second[1]} 세부`}>
      {second[3].map(node => <button key={node[0]} type="button" aria-current={third?.[0] === node[0] ? 'page' : undefined} onClick={() => go([top[0], second[0], node[0]])}>{node[1]}
        {top[0] === 'inspect' && second[0] === 'filings' && filingState.data && <small>{node[0] === 'ALL' ? filings.length : filings.filter(f => f.category === node[0]).length}</small>}</button>)}
    </nav>}

    <div className="stock-backbar">
      <button type="button" className="stock-back" onClick={() => {
        if (steps > 0) { window.history.back(); return }
        const up = path.length > 3 ? path.slice(0, -1) : path.length > 1 ? [path[0]] : null
        if (up) go(up); else window.location.assign('/')
      }}>← 뒤로</button>
    <ol className="stock-crumbs" aria-label="지금 위치">{crumbs.map((crumb, i) => <li key={i}>{i < crumbs.length - 1
      ? <button type="button" onClick={() => go(crumb.path)}>{crumb.label}</button> : <span aria-current="location">{crumb.label}</span>}</li>)}</ol>
    </div>

    <div id="stock-body" className="stock-body">
      <p className="stock-intro"><strong>여기서 알 수 있는 것</strong> {path.length > 3 ? deepIntro(path) : (second[2] ?? top[3])}</p>
      <NodeGuide path={path} />
      <StockView ctx={ctx} />
    </div>
    <p className="stock-disclaimer">AIRA는 공식 자료를 보여 주고 정리할 뿐, 매수·매도를 권하거나 주가를 예측하지 않습니다.</p>
  </section>
}

function deepIntro(path) {
  if (path[1] === 'price') return '그날 하루의 공식 시세와 그날 접수된 공시를 함께 봅니다.'
  if (path[1] === 'earnings' || path[1] === 'balance') return '그해 사업보고서의 모든 항목과 비율을 한곳에 모았습니다.'
  if (path[1] === 'ratios' || path[0] === 'assess') return '이 비율을 어떻게 계산하는지, 해마다 어떻게 변했는지 봅니다.'
  if (path[1] === 'filings' || path[1] === 'around') return '공시 하나의 상세와, 접수일 전후 주가를 함께 봅니다.'
  return '더 자세한 내용입니다.'
}

function breadcrumbs(path, ctx) {
  const { top, second, third } = nodeFor(path)
  const out = [{ label: `${top[1]} ${top[2]}`, path: [top[0]] }, { label: second[1], path: [top[0], second[0]] }]
  if (third) out.push({ label: third[1], path: [top[0], second[0], third[0]] })
  const extra = path.slice(third ? 3 : 2)
  extra.forEach((key, i) => {
    const label = /^\d{14}$/.test(key) ? (ctx.filings.find(f => f.receiptNo === key)?.title ?? `공시 ${key}`)
      : /^\d{4}-\d{2}-\d{2}$/.test(key) ? key : /^\d{4}$/.test(key) ? `${key}년`
      : /^[0-9a-f-]{36}(,[0-9a-f-]{36})*$/i.test(key) ? `${key.split(',').length}개 회사와 비교`
      : RATIOS.find(r => r.key === key)?.label ?? ACCOUNT_LABEL[key] ?? key
    out.push({ label, path: path.slice(0, (third ? 3 : 2) + i + 1) })
  })
  return out
}

function StockView({ ctx }) {
  const { path } = ctx
  const { top, second, third } = nodeFor(path)
  const key = `${top[0]}/${second[0]}`
  const deeper = path.slice(third ? 3 : 2)
  switch (key) {
    case 'ask/faq': return <AskFaq ctx={ctx} />
    case 'ask/ai': return <AskAi ctx={ctx} />
    case 'ask/course': return <CourseView ctx={ctx} />
    case 'ask/find': return <AskFind ctx={ctx} />
    case 'ask/claim': return <><ClaimCheck loggedIn={ctx.loggedIn} /><Learn terms={[['일치', '공식 값과 같습니다.'], ['거의 일치', '반올림 차이 정도입니다.'], ['다름', '공식 값과 다릅니다.'], ['확인 불가', '의견·전망·원인처럼 숫자로 확인할 수 없는 말입니다.']]}>
      예: "{ctx.name} 작년 영업이익은 전년보다 30% 늘었다"처럼 숫자가 들어간 문장을 넣어 보세요.</Learn></>
    case 'ask/quote': return <AskQuote ctx={ctx} />
    case 'ask/glossary': return <Glossary ctx={ctx} />
    case 'inspect/summary': return <SummaryView ctx={ctx} />
    case 'inspect/company': return third[0] === 'pay' ? <><SectionView ctx={ctx} name="payAll" /><SectionView ctx={ctx} name="payTop" /></>
      : SECTION_META[third[0]] ? <SectionView ctx={ctx} name={third[0]} /> : <CompanyView ctx={ctx} view={third[0]} />
    case 'inspect/statements': return <StatementsView ctx={ctx} view={third[0]} />
    case 'inspect/price': return deeper[0] ? <DayDetail ctx={ctx} date={deeper[0]} /> : <PriceView ctx={ctx} view={third[0]} />
    case 'inspect/earnings': return third[0] === 'quarters' ? <QuarterView ctx={ctx} /> : deeper[0] ? <YearDetail ctx={ctx} year={Number(deeper[0])} /> : <EarningsView ctx={ctx} view={third[0]} />
    case 'inspect/balance': return third[0] === 'bonds' ? <SectionView ctx={ctx} name="bonds" /> : third[0] === 'cash' ? <CashFlowView ctx={ctx} /> : deeper[0] ? <YearDetail ctx={ctx} year={Number(deeper[0])} /> : <BalanceView ctx={ctx} view={third[0]} />
    case 'inspect/ratios': return third[0] === '시장 평가' ? <ValuationView ctx={ctx} /> : deeper[0] ? <RatioDetail ctx={ctx} ratioKey={deeper[0]} /> : <RatioGroup ctx={ctx} group={third[0]} />
    case 'inspect/filings': return deeper[0] ? <FilingDetail ctx={ctx} receiptNo={deeper[0]} /> : <FilingList ctx={ctx} category={third[0]} />
    case 'inspect/news': return <NewsView ctx={ctx} view={third[0]} />
    case 'inspect/owners': return SECTION_META[third[0]] ? <SectionView ctx={ctx} name={third[0]} /> : third[0] === 'insiders' || third[0] === 'major' ? <OwnershipView ctx={ctx} view={third[0]} /> : <OwnersView ctx={ctx} view={third[0]} />
    case 'relate/filings': return deeper[0] ? <FilingDetail ctx={ctx} receiptNo={deeper[0]} /> : <RelateFilings ctx={ctx} view={third[0]} />
    case 'relate/market': return <RelateMarket ctx={ctx} view={third[0]} />
    case 'relate/around': return deeper[0] ? <FilingDetail ctx={ctx} receiptNo={deeper[0]} /> : <RelateAround ctx={ctx} />
    case 'relate/events': return <RelateEvents ctx={ctx} />
    case 'relate/calendar': return deeper[0] ? <FilingDetail ctx={ctx} receiptNo={deeper[0]} /> : <CalendarView ctx={ctx} view={third[0]} />
    case 'relate/peer': return <PeerCompare ctx={ctx} peerId={deeper[0]} />
    case 'relate/industry': return <IndustryView ctx={ctx} view={third[0]} />
    case 'assess/industry': return <IndustryView ctx={ctx} view="assess" />
    case 'assess/radar': return <RadarView ctx={ctx} />
    case 'assess/summary': return <AssessSummary ctx={ctx} />
    case 'assess/yoy': return deeper[0] ? (RATIOS.some(r => r.key === deeper[0]) ? <RatioDetail ctx={ctx} ratioKey={deeper[0]} /> : <AccountDetail ctx={ctx} account={deeper[0]} />) : <AssessYoy ctx={ctx} />
    case 'assess/check': return <AssessCheck ctx={ctx} />
    case 'assess/quiz': return <QuizView ctx={ctx} />
    case 'assess/unknown': return <AssessUnknown ctx={ctx} />
    case 'assess/report': return <ReportView ctx={ctx} />
    default: return null
  }
}

// ---------------- 묻다 ----------------
function AskFaq({ ctx }) {
  return <ul className="stock-question-list">{QUESTION_ROUTES.map(route => <li key={route.label}>
    <button type="button" onClick={() => ctx.go(route.to)}>{route.label}<span aria-hidden="true">→</span></button></li>)}</ul>
}

function AskFind({ ctx }) {
  const [text, setText] = useState('')
  const found = routeQuestion(text)
  return <div className="stock-find">
    <label htmlFor="stock-question">무엇이 궁금하세요?</label>
    <input id="stock-question" value={text} onChange={event => setText(event.target.value)} placeholder="예: 빚이 많아? / 시장보다 많이 올랐어? / 최근 공시" />
    {text.trim() && !found.length && <p className="stock-hint">맞는 화면을 찾지 못했습니다. "주가, 매출, 이익, 부채, 공시, 시장, 작년" 같은 낱말로 적어 보세요.</p>}
    <ul className="stock-question-list">{found.map(route => <li key={route.label}><button type="button" onClick={() => ctx.go(route.to)}>{route.label}<span aria-hidden="true">→</span></button></li>)}</ul>
    <Learn>낱말을 맞춰 화면을 고르는 기능이라 질문에 직접 답하지 않습니다. 답은 고른 화면의 공식 값에서 확인합니다.
      로그인하면 <a href="/explore">탐색 화면</a>의 AI 길찾기도 쓸 수 있습니다.</Learn>
  </div>
}

function AskQuote({ ctx }) {
  const periodic = ctx.filings.filter(f => f.category === 'PERIODIC')
  const annual = [...ctx.years].reverse().find(y => y.receiptNo)
  const options = [...(annual ? [{ receiptNo: annual.receiptNo, title: `${annual.year} 사업보고서` }] : []),
    ...periodic.filter(f => f.receiptNo !== annual?.receiptNo)]
  const [receipt, setReceipt] = useState(options[0]?.receiptNo ?? '')
  useEffect(() => { if (!receipt && options[0]) setReceipt(options[0].receiptNo) }, [receipt, options])
  return <div>
    {options.length > 0 && <div className="stock-pick"><label htmlFor="quote-filing">물어볼 보고서</label>
      <select id="quote-filing" value={receipt} onChange={event => setReceipt(event.target.value)}>{options.map(o => <option key={o.receiptNo} value={o.receiptNo}>{o.title} · {o.receiptNo}</option>)}</select></div>}
    {receipt ? <FilingQuote key={receipt} receipt={receipt} loggedIn={ctx.loggedIn} /> : <Empty>물어볼 보고서를 찾는 중이거나 아직 없습니다.</Empty>}
  </div>
}

// ---------------- 보다 · 회사 ----------------
function CompanyView({ ctx, view }) {
  const o = ctx.overview.data
  if (ctx.overview.loading) return <p className="status">회사 정보를 불러오는 중입니다.</p>
  const p = o?.profile ?? {}
  const rows = view === 'basic'
    ? [['회사 이름', o?.name], ['영문 이름', p.corp_name_eng], ['대표자', p.ceo_nm], ['설립일', p.est_dt && `${p.est_dt.slice(0, 4)}.${p.est_dt.slice(4, 6)}.${p.est_dt.slice(6, 8)}`],
      ['결산월', p.acc_mt && `${Number(p.acc_mt)}월`], ['본사 주소', p.adres], ['홈페이지', p.hm_url], ['업종 코드(통계청)', p.induty_code]]
    : [['시장', o?.market], ['종목 코드', o?.shortCode], ['DART 고유번호', o?.corpCode],
      ['상장주식수', ctx.points.length ? formatCount(ctx.points[ctx.points.length - 1].listedShares) : null],
      ['시가총액', ctx.points.length ? formatWon(ctx.points[ctx.points.length - 1].marketCap) : null]]
  return <>
    <dl className="stock-table-dl">{rows.map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v ? (k === '홈페이지'
      ? <a href={v.startsWith('http') ? v : `https://${v}`} target="_blank" rel="noopener noreferrer">{v} ↗</a> : v) : <span className="muted">자료 없음</span>}</dd></div>)}</dl>
    <Source>{view === 'basic' ? 'OpenDART 기업개황' : 'OpenDART 기업개황 · KRX 일별 매매정보'}</Source>
    <Learn terms={[['결산월', '1년 장부를 마감하는 달입니다. 12월이면 1~12월이 한 사업연도입니다.'],
      ['DART 고유번호', '금융감독원 전자공시에서 회사를 구분하는 8자리 번호입니다.'], ['상장주식수', '거래소에서 거래되는 이 회사 주식의 총수입니다.'],
      ['시가총액', '주가 × 상장주식수. 시장이 매긴 회사의 크기입니다.']]}>
      회사 이름만 같고 다른 회사인 경우가 있어, AIRA는 DART 고유번호로 회사를 구분합니다.</Learn>
  </>
}

// ---------------- 보다 · 주가 ----------------
function RangePicker({ ctx }) {
  return <div className="stock-range" role="group" aria-label="기간">{RANGES.map(([days, label]) =>
    <button key={days} type="button" aria-pressed={ctx.range === days} onClick={() => ctx.setRange(days)}>{label}</button>)}</div>
}

function PriceGate({ ctx, children }) {
  if (ctx.pricesState.loading) return <p className="status">주가를 불러오는 중입니다.</p>
  if (ctx.pricesState.error) return <Empty action={<button type="button" className="secondary-action" onClick={ctx.reloadPrices}>다시 시도</button>}>주가를 불러오지 못했습니다.</Empty>
  if (ctx.pricesState.data?.state === 'NO_SECURITY') return <Empty>이 회사는 코스피·코스닥 종목 코드를 찾지 못했습니다(비상장이거나 코드가 바뀐 경우).</Empty>
  if (ctx.points.length < 2) return <Empty>1년치 주가를 거래소에서 받아 채우는 중입니다. 서버가 처음 켜진 뒤 20분쯤 걸리며, 그동안 받은 날짜부터 보입니다.</Empty>
  return children
}

function PriceView({ ctx, view }) {
  return <PriceGate ctx={ctx}><PriceBody ctx={ctx} view={view} /></PriceGate>
}

function PriceBody({ ctx, view }) {
  const shown = lastDays(ctx.points, Number(ctx.range))
  const stats = priceStats(shown)
  const dates = shown.map(p => p.date)
  const filingMarkers = ctx.filings.map(f => ({ date: f.date, label: f.title, color: CATEGORY_COLOR[f.category] }))
  const [ma, setMa] = useState([])
  const [unit, setUnit] = useState('day')
  if (!stats) return <Empty>이 기간에는 시세가 없습니다. 기간을 넓혀 보세요.</Empty>
  return <>
    {view !== 'daily' && <RangePicker ctx={ctx} />}
    {view === 'trend' && <>
      <div className="stock-range" role="group" aria-label="이동평균선">{[5, 20, 60].map(n => <button key={n} type="button" aria-pressed={ma.includes(n)}
        onClick={() => setMa(list => list.includes(n) ? list.filter(x => x !== n) : [...list, n])}>{n}일 평균선</button>)}</div>
      <LineChart ariaLabel={`${ctx.name} 종가 흐름`} dates={dates} series={[{ key: 'close', label: '종가', color: COLORS.a, values: shown.map(p => Number(p.close)) },
        ...ma.map(n => { const all = movingAverage(ctx.points, n); const offset = ctx.points.length - shown.length
          return { key: `ma${n}`, label: `${n}일 평균`, color: n === 5 ? COLORS.c : n === 20 ? COLORS.b : COLORS.d, values: all.slice(offset) } })]}
        format={v => Number(v).toLocaleString('ko-KR')} />
      <dl className="stock-stats">
        <div><dt>기간 변화</dt><dd className={stats.change > 0 ? 'up' : stats.change < 0 ? 'down' : ''}>{formatPercent(stats.change)}</dd></div>
        <div><dt>시작 → 끝</dt><dd>{formatPrice(stats.first.close)} → {formatPrice(stats.last.close)}</dd></div>
        <div><dt>오른 날 / 내린 날</dt><dd>{stats.up}일 / {stats.down}일</dd></div>
        <div><dt>최고점 대비 최대 하락</dt><dd>{formatPercent(stats.maxDrawdown)}</dd></div>
      </dl>
      <AiAssist kind="INSPECT" save={{ companyId: ctx.companyId, companyName: ctx.name }} subject={`${ctx.name} · 주가 ${stats.first.date} — ${stats.last.date}`} loggedIn={ctx.loggedIn}
        facts={[{ label: '시작 종가', value: formatPrice(stats.first.close), period: stats.first.date }, { label: '마지막 종가', value: formatPrice(stats.last.close), period: stats.last.date },
          { label: '기간 변화율', value: formatPercent(stats.change), period: `${stats.first.date} — ${stats.last.date}` }, { label: '최고 종가', value: formatPrice(stats.high.close), period: stats.high.date },
          { label: '최저 종가', value: formatPrice(stats.low.close), period: stats.low.date }]} />
      {(() => { const days = [...shown].filter(x => x.changeRate !== null).sort((a, b) => Math.abs(Number(b.changeRate)) - Math.abs(Number(a.changeRate))).slice(0, 3)
        const mkt = priceStats(ctx.indexPoints.filter(x => x.date >= stats.first.date))
        const annual = [...ctx.years].reverse().find(y => y.receiptNo)
        return <section className="why-box"><h3 className="stock-h3">🤔 이 기간 주가, 왜 이렇게 움직였을까? ({formatPercent(stats.change)})</h3>
          <ExplainStory subject={`${ctx.name} 주가 ${stats.first.date} — ${stats.last.date}`} loggedIn={ctx.loggedIn} save={{ companyId: ctx.companyId, companyName: ctx.name }}
            receipt={annual?.receiptNo} question="주가 변동 요인 시장 환경 실적"
            facts={[{ label: '기간 주가 변화율', value: formatPercent(stats.change), period: `${stats.first.date} — ${stats.last.date}` },
              ...(mkt ? [{ label: `같은 기간 ${ctx.market === 'KOSDAQ' ? '코스닥' : '코스피'} 변화율`, value: formatPercent(mkt.change), period: '같은 기간' }] : []),
              { label: '최고점 대비 최대 하락', value: formatPercent(stats.maxDrawdown), period: '같은 기간' },
              ...days.flatMap(d => [{ label: '크게 움직인 날', value: `${d.date} ${formatPercent(d.changeRate)}`, period: d.date },
                ...ctx.filings.filter(f => f.date === d.date).slice(0, 2).map(f => ({ label: '그날 공시', value: f.title, period: f.date }))]),
              ...(ctx.years.length >= 2 ? [{ label: `${ctx.years[ctx.years.length - 1].year} 영업이익 증가율`, value: ctx.years[ctx.years.length - 1].ratios.operatingTurn ?? formatPercent(ctx.years[ctx.years.length - 1].ratios.operatingGrowth), period: '사업연도' }] : [])]} />
          <p className="muted why-note">주가는 실적·시장 분위기·금리·수급 등 여러 이유가 겹쳐 움직여요. 시장 전체와 견주고, 크게 움직인 날의 공시를 함께 보면 이해에 도움이 돼요.</p></section> })()}
      <Learn terms={[['이동평균선', '최근 n일 종가의 평균을 이은 선입니다. 짧은 선(5일)은 최근 흐름, 긴 선(60일)은 큰 흐름을 보여 줍니다.'], ['종가', '그날 마지막에 거래된 가격입니다.'], ['최고점 대비 최대 하락', '기간 중 가장 높았던 가격에서 가장 많이 떨어졌던 폭입니다. 흔들림의 크기를 볼 때 씁니다.'],
        ['오른 날 / 내린 날', '전날보다 종가가 오른 날과 내린 날의 수입니다.']]}>그래프 위에 마우스를 올리거나 손가락으로 누르면 그날 값이 보입니다. 더 자세한 하루는 「일별 시세」에서 날짜를 누르세요.</Learn>
    </>}
    {view === 'daily' && <DailyTable ctx={ctx} />}
    {view === 'candle' && <>
      <div className="stock-range" role="group" aria-label="봉 단위">{[['day', '일봉'], ['week', '주봉'], ['month', '월봉']].map(([k, l]) =>
        <button key={k} type="button" aria-pressed={unit === k} onClick={() => setUnit(k)}>{l}</button>)}</div>
      <CandleChart ariaLabel={`${ctx.name} 봉 차트`} points={aggregateCandles(unit === 'day' ? shown : lastDays(ctx.points, 365), unit)} onPick={date => ctx.go(['inspect', 'price', 'daily', date])} />
      <Learn terms={[['봉(캔들)', '하루의 시가·고가·저가·종가를 막대 하나로 그린 것입니다. 몸통은 시가와 종가 사이, 위아래 선(꼬리)은 고가와 저가입니다.'],
        ['빨간 봉 / 파란 봉', '종가가 시가보다 높으면 빨강(상승), 낮으면 파랑(하락)입니다. 국내 표기 방식입니다.'],
        ['주봉 / 월봉', '한 주 / 한 달을 봉 하나로 묶은 것입니다. 긴 흐름을 볼 때 씁니다(1년치 기준).']]}>봉을 누르면 그날 상세로 들어갑니다. 기간을 1개월·3개월로 줄이면 봉이 더 굵게 보입니다.</Learn>
    </>}
    {view === 'volume' && <>
      <BarChart ariaLabel="거래량" categories={dates.map(d => d.slice(5))} series={[{ key: 'v', label: '거래량(주)', color: COLORS.b, values: shown.map(p => p.volume === null ? null : Number(p.volume)) }]}
        format={v => formatCount(v, '')} height={200} />
      <LineChart ariaLabel="거래대금" dates={dates} series={[{ key: 'value', label: '거래대금', color: COLORS.c, values: shown.map(p => p.value === null ? null : Number(p.value)) }]} format={formatWon} height={200} />
      <dl className="stock-stats"><div><dt>하루 평균 거래량</dt><dd>{formatCount(stats.averageVolume)}</dd></div>
        <div><dt>가장 많이 거래된 날</dt><dd>{(() => { const top = [...shown].sort((a, b) => Number(b.volume) - Number(a.volume))[0]; return top ? <button type="button" className="link-button" onClick={() => ctx.go(['inspect', 'price', 'daily', top.date])}>{top.date} · {formatCount(top.volume)}</button> : '—' })()}</dd></div></dl>
      <Learn terms={[['거래량', '그날 사고판 주식 수입니다.'], ['거래대금', '그날 사고판 금액의 합입니다(가격 × 수량).']]}>거래가 갑자기 많아진 날은 「일별 시세」에서 그날 공시가 있었는지 함께 볼 수 있습니다.</Learn>
    </>}
    {view === 'cap' && <>
      <LineChart ariaLabel="시가총액" dates={dates} series={[{ key: 'cap', label: '시가총액', color: COLORS.d, values: shown.map(p => p.marketCap === null ? null : Number(p.marketCap)) }]} format={formatWon} />
      <dl className="stock-stats"><div><dt>시작</dt><dd>{formatWon(stats.first.marketCap)}</dd></div><div><dt>끝</dt><dd>{formatWon(stats.last.marketCap)}</dd></div>
        <div><dt>상장주식수</dt><dd>{formatCount(stats.last.listedShares)}</dd></div></dl>
      <Learn terms={[['시가총액', '주가 × 상장주식수. 시장에서 매긴 회사 전체의 값입니다.']]}>주식 수가 늘거나(유상증자) 줄면(소각) 주가가 같아도 시가총액이 달라집니다. 「공시」에서 주식 수 관련 공시를 함께 확인해 보세요.</Learn>
    </>}
    {view === 'range' && <>
      <RangeBar stats={priceStats(lastDays(ctx.points, 365))} />
      <dl className="stock-stats">{(() => { const s = priceStats(lastDays(ctx.points, 365)); const span = spanName(s).range; return <>
        <div><dt>{span} 최고 종가</dt><dd><button type="button" className="link-button" onClick={() => ctx.go(['inspect', 'price', 'daily', s.high.date])}>{formatPrice(s.high.close)} · {s.high.date}</button></dd></div>
        <div><dt>{span} 최저 종가</dt><dd><button type="button" className="link-button" onClick={() => ctx.go(['inspect', 'price', 'daily', s.low.date])}>{formatPrice(s.low.close)} · {s.low.date}</button></dd></div>
        <div><dt>지금 위치</dt><dd>최저가보다 {formatPercent((Number(s.last.close) - Number(s.low.close)) / Number(s.low.close) * 100)} · 최고가보다 {formatPercent((Number(s.last.close) - Number(s.high.close)) / Number(s.high.close) * 100)}</dd></div></> })()}</dl>
      <Learn terms={[['52주', '최근 1년(52주) 동안을 뜻합니다. 여기서는 일별 종가 기준입니다(장중 최고·최저가 아님).'], ['보관 기간', 'AIRA는 지금 주가를 약 6개월치 보관합니다. 기간이 1년보다 짧으면 「52주」 대신 실제 개월 수로 표시합니다.']]}>지금 가격이 저장된 기간 안에서 어디쯤인지 보는 화면입니다.</Learn>
    </>}
    <Source>{ctx.pricesState.data?.source} · 단위 원</Source>
  </>
}

function RangeBar({ stats }) {
  if (!stats) return null
  const lo = Number(stats.low.close)
  const hi = Number(stats.high.close)
  const at = hi === lo ? 50 : ((Number(stats.last.close) - lo) / (hi - lo)) * 100
  return <div className="stock-rangebar" aria-label="52주 범위에서 지금 위치"><span>{formatPrice(lo)}</span>
    <div><i style={{ left: `${Math.max(0, Math.min(100, at))}%` }} /></div><span>{formatPrice(hi)}</span></div>
}

function DailyTable({ ctx }) {
  const rows = [...ctx.points].reverse()
  const [limit, setLimit] = useState(30)
  const filingDays = new Set(ctx.filings.map(f => f.date))
  return <>
    <div className="table-scroll"><table className="stock-table">
      <thead><tr><th>날짜</th><th>종가</th><th>등락률</th><th>시가</th><th>고가</th><th>저가</th><th>거래량</th><th>공시</th></tr></thead>
      <tbody>{rows.slice(0, limit).map(p => <tr key={p.date} onClick={() => ctx.go(['inspect', 'price', 'daily', p.date])} className="clickable">
        <td><button type="button" className="link-button">{p.date}</button></td><td>{formatPrice(p.close)}</td>
        <td className={Number(p.changeRate) > 0 ? 'up' : Number(p.changeRate) < 0 ? 'down' : ''}>{formatPercent(p.changeRate)}</td>
        <td>{formatPrice(p.open)}</td><td>{formatPrice(p.high)}</td><td>{formatPrice(p.low)}</td><td>{formatCount(p.volume, '')}</td>
        <td>{filingDays.has(p.date) ? '●' : ''}</td></tr>)}</tbody></table></div>
    <div className="stock-actions">{limit < rows.length && <button type="button" className="secondary-action" onClick={() => setLimit(limit + 60)}>더 보기 ({rows.length - limit}일 남음)</button>}
</div>
    <p className="stock-hint">거래소 시세는 이용약관상 파일로 다시 내보낼 수 없어 화면에서만 보여 드려요. 재무·조건 검색 결과는 내려받을 수 있어요.</p>
    <Learn terms={[['시가', '그날 처음 거래된 가격'], ['고가 / 저가', '그날 가장 높았던 / 낮았던 가격'], ['등락률', '전날 종가 대비 오늘 종가의 변화'], ['● 공시', '그날 접수된 공시가 있다는 표시']]}>
      날짜를 누르면 그날 하루를 더 자세히 봅니다.</Learn>
  </>
}

function DayDetail({ ctx, date }) {
  const index = ctx.points.findIndex(p => p.date === date)
  const p = ctx.points[index]
  if (!p) return <PriceGate ctx={ctx}><Empty>{date}의 시세가 없습니다(휴장일이거나 아직 받지 않은 날).</Empty></PriceGate>
  const prev = ctx.points[index - 1]
  const next = ctx.points[index + 1]
  const dayFilings = ctx.filings.filter(f => f.date === date)
  const idx = ctx.indexPoints.find(x => x.date === date)
  return <>
    <div className="stock-day-nav">{prev && <button type="button" className="secondary-action" onClick={() => ctx.go(['inspect', 'price', 'daily', prev.date])}>← {prev.date}</button>}
      {next && <button type="button" className="secondary-action" onClick={() => ctx.go(['inspect', 'price', 'daily', next.date])}>{next.date} →</button>}</div>
    <dl className="stock-table-dl">{[['종가', formatPrice(p.close)], ['등락률', formatPercent(p.changeRate)], ['시가', formatPrice(p.open)], ['고가', formatPrice(p.high)], ['저가', formatPrice(p.low)],
      ['하루 변동폭(고가−저가)', p.high && p.low ? `${formatPrice(Number(p.high) - Number(p.low))} (${formatPercent((Number(p.high) - Number(p.low)) / Number(p.low) * 100, { sign: false })})` : '—'],
      ['거래량', formatCount(p.volume)], ['거래대금', formatWon(p.value)], ['시가총액', formatWon(p.marketCap)], ['상장주식수', formatCount(p.listedShares)],
      [`같은 날 ${ctx.market} 지수`, idx ? `${Number(idx.close).toLocaleString('ko-KR')} (${formatPercent(idx.changeRate)})` : '자료 없음']]
      .map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}</dl>
    <AiAssist kind="DAY" save={{ companyId: ctx.companyId, companyName: ctx.name }} subject={`${ctx.name} ${date}`} loggedIn={ctx.loggedIn} facts={[
      { label: '종가', value: formatPrice(p.close), period: date }, { label: '등락률', value: formatPercent(p.changeRate), period: date },
      { label: '거래량', value: formatCount(p.volume), period: date },
      ...(idx ? [{ label: `${ctx.market} 지수 등락률`, value: formatPercent(idx.changeRate), period: date }] : []),
      ...dayFilings.slice(0, 8).map(f => ({ label: '같은 날 공시', value: f.title, period: f.date }))]} />
    {(() => { const main = dayFilings.find(f => f.category !== 'OWNERSHIP') ?? dayFilings[0]
      return <section className="why-box"><h3 className="stock-h3">🤔 이날 왜 {Number(p.changeRate) > 0 ? '올랐을까' : Number(p.changeRate) < 0 ? '내렸을까' : '이랬을까'}? ({formatPercent(p.changeRate)})</h3>
        <ExplainStory subject={`${ctx.name} ${date} 주가 ${formatPercent(p.changeRate)}`} loggedIn={ctx.loggedIn} save={{ companyId: ctx.companyId, companyName: ctx.name }} receipt={main?.receiptNo} question="주요 내용과 결정 이유, 목적"
          facts={[{ label: '종가', value: formatPrice(p.close), period: date }, { label: '등락률', value: formatPercent(p.changeRate), period: date },
            { label: '거래량', value: formatCount(p.volume), period: date },
            ...(idx ? [{ label: `${ctx.market === 'KOSDAQ' ? '코스닥' : '코스피'} 등락률`, value: formatPercent(idx.changeRate), period: date }] : []),
            ...dayFilings.slice(0, 6).map(f => ({ label: '같은 날 공시 제목', value: f.title, period: f.date }))]} />
        <details className="stock-learn"><summary>근거를 하나씩 보기</summary>
        <div className="why-steps">
          <div><strong>① 시장 전체는?</strong><p className="stock-hint">{idx ? `같은 날 ${ctx.market === 'KOSDAQ' ? '코스닥' : '코스피'} ${formatPercent(idx.changeRate)}. ${Math.sign(Number(idx.changeRate)) === Math.sign(Number(p.changeRate)) ? '시장 전체와 같은 방향이었습니다.' : '시장 전체와 반대 방향이었습니다.'}` : '같은 날 지수 자료가 아직 없습니다.'}</p></div>
          <div>{main ? <CompanyReason receipt={main.receiptNo} question="주요 내용과 결정 이유, 목적" loggedIn={ctx.loggedIn} title={`② 같은 날 회사 공시: ${main.title}`} />
            : <><strong>② 회사 공시</strong><p className="stock-hint">이날 회사가 낸 공시는 없습니다.</p></>}</div>
          <div><strong>③ 그 무렵 기사</strong><p className="stock-hint">언론의 해석은 「뉴스」에서 볼 수 있습니다(공식 근거는 아님).</p>
            <button type="button" className="secondary-action" onClick={() => ctx.go(['inspect', 'news', 'list'])}>최근 기사 보기 →</button></div>
        </div></details>
        <p className="muted why-note">주가는 여러 이유가 겹쳐 움직여서 하나의 원인으로 단정할 수 없습니다. 같은 날 함께 일어난 사실과 회사가 직접 밝힌 내용을 모아 보여 드립니다.</p></section> })()}
    <h3 className="stock-h3">이날 접수된 공시 {dayFilings.length}건</h3>
    {dayFilings.length ? <FilingRows ctx={ctx} items={dayFilings} base={['inspect', 'filings', 'ALL']} /> : <p className="muted">이날 접수된 공시가 없습니다.</p>}
    <Source>KRX 일별 매매정보 {date.replaceAll('-', '')} · OpenDART 공시검색</Source>
  </>
}

// ---------------- 보다 · 실적/재무 ----------------
function FinGate({ ctx, children }) {
  if (ctx.finState.loading) return <p className="status">재무 자료를 불러오는 중입니다.</p>
  if (ctx.finState.error) return <Empty action={<button type="button" className="secondary-action" onClick={ctx.reloadFin}>다시 시도</button>}>재무 자료를 불러오지 못했습니다.</Empty>
  if (!ctx.years.length) return <Empty>사업보고서 재무 값을 DART에서 받아 채우는 중이거나, 이 회사의 사업보고서 주요계정이 없습니다(금융업·신규 상장 등). 공식 근거로 확인된 값은 <a href={`/explore?q=${encodeURIComponent(ctx.name)}`}>탐색 화면</a>에서 볼 수 있습니다.</Empty>
  return children
}

function YearTable({ ctx, accounts }) {
  return <div className="table-scroll"><table className="stock-table">
    <thead><tr><th>항목</th>{ctx.years.map(y => <th key={y.year}><button type="button" className="link-button" onClick={() => ctx.go([...ctx.path.slice(0, 3), String(y.year)])}>{y.year}</button></th>)}</tr></thead>
    <tbody>{accounts.map(key => <tr key={key}><th scope="row">{ACCOUNT_LABEL[key]}</th>{ctx.years.map(y => <td key={y.year} className={y.values[key] < 0 ? 'down' : ''}>{formatWon(y.values[key])}</td>)}</tr>)}</tbody>
  </table></div>
}

function EarningsView({ ctx, view }) {
  const labels = ctx.years.map(y => String(y.year))
  const finLearn = <Learn terms={[['매출', '물건·서비스를 팔아 들어온 돈 전체'], ['영업이익', '매출에서 원가·판매비·관리비를 뺀 본업의 이익'],
    ['당기순이익', '영업이익에 이자·투자손익·세금까지 반영한 최종 이익'], ['연결', '자회사까지 합친 재무제표. AIRA는 연결을 우선 씁니다.']]}>연도를 누르면 그해의 모든 항목과 비율을 봅니다.</Learn>
  return <FinGate ctx={ctx}>
    {view === 'all' ? <><YearTable ctx={ctx} accounts={['REVENUE', 'OPERATING_INCOME', 'NET_INCOME']} />
      <div className="stock-actions"><button type="button" className="secondary-action" onClick={() => downloadCsv(`${ctx.name}_재무_연도별.csv`,
        ['사업연도', ...Object.values(ACCOUNT_LABEL), ...RATIOS.map(r => r.label), '접수번호'],
        ctx.years.map(y => [y.year, ...Object.keys(ACCOUNT_LABEL).map(k => y.values[k]), ...RATIOS.map(r => y.ratios[r.key]), y.receiptNo]))}>⬇ 재무·비율 CSV로 내려받기</button></div>
      <BarChart ariaLabel="매출·영업이익·순이익" categories={labels} series={[['REVENUE', COLORS.a], ['OPERATING_INCOME', COLORS.b], ['NET_INCOME', COLORS.c]]
        .map(([k, color]) => ({ key: k, label: ACCOUNT_LABEL[k], color, values: ctx.years.map(y => y.values[k] ?? null) }))} format={formatWon} /></>
      : <>
        <BarChart ariaLabel={ACCOUNT_LABEL[view]} categories={labels} series={[{ key: view, label: ACCOUNT_LABEL[view], color: COLORS.a, values: ctx.years.map(y => y.values[view] ?? null) }]} format={formatWon} />
        <div className="table-scroll"><table className="stock-table"><thead><tr><th>사업연도</th><th>{ACCOUNT_LABEL[view]}</th><th>작년 대비</th><th>보고서</th></tr></thead>
          <tbody>{[...ctx.years].reverse().map(y => { const g = { REVENUE: y.ratios.revenueGrowth, OPERATING_INCOME: y.ratios.operatingGrowth, NET_INCOME: y.ratios.netGrowth }[view]
            const turn = view === 'OPERATING_INCOME' ? y.ratios.operatingTurn : view === 'NET_INCOME' ? y.ratios.netTurn : null
            return <tr key={y.year} className="clickable" onClick={() => ctx.go(['inspect', 'earnings', view, String(y.year)])}><td><button type="button" className="link-button">{y.year}</button></td>
              <td className={y.values[view] < 0 ? 'down' : ''}>{formatWon(y.values[view])}</td><td>{turn ?? formatPercent(g)}</td>
              <td><a href={y.dartUrl} target="_blank" rel="noopener noreferrer" onClick={e => e.stopPropagation()}>원문 ↗</a></td></tr> })}</tbody></table></div>
        <AiAssist kind="INSPECT" save={{ companyId: ctx.companyId, companyName: ctx.name }} subject={`${ctx.name} · ${ACCOUNT_LABEL[view]} 연도별`} loggedIn={ctx.loggedIn}
          facts={ctx.years.filter(y => y.values[view] !== undefined).map(y => ({ label: `${ACCOUNT_LABEL[view]}`, value: formatWon(y.values[view]), period: `${y.year} 사업연도` }))} />
        <WhyBox ctx={ctx} account={view} />
      </>}
    {finLearn}
    <Source>{ctx.finState.data?.source}</Source>
  </FinGate>
}

function BalanceView({ ctx, view }) {
  const labels = ctx.years.map(y => String(y.year))
  return <FinGate ctx={ctx}>
    {view === 'structure' ? <>
      <BarChart ariaLabel="부채와 자본" stacked categories={labels} series={[{ key: 'L', label: '부채', color: COLORS.c, values: ctx.years.map(y => y.values.TOTAL_LIABILITIES ?? null) },
        { key: 'E', label: '자본', color: COLORS.b, values: ctx.years.map(y => y.values.TOTAL_EQUITY ?? null) }]} format={formatWon} />
      <YearTable ctx={ctx} accounts={['TOTAL_ASSETS', 'TOTAL_LIABILITIES', 'TOTAL_EQUITY']} />
      <Learn terms={[['자산', '회사가 가진 모든 것(현금·공장·재고 등)'], ['부채', '갚아야 할 돈'], ['자본', '자산에서 부채를 뺀 주주의 몫'], ['자산 = 부채 + 자본', '막대 전체 높이가 자산입니다.']]}>
        막대에서 부채(위)와 자본(아래)이 차지하는 비율을 해마다 비교해 보세요.</Learn></>
      : <>
        <BarChart ariaLabel="유동자산과 유동부채" categories={labels} series={[{ key: 'CA', label: '유동자산', color: COLORS.b, values: ctx.years.map(y => y.values.CURRENT_ASSETS ?? null) },
          { key: 'CL', label: '유동부채', color: COLORS.c, values: ctx.years.map(y => y.values.CURRENT_LIABILITIES ?? null) }]} format={formatWon} />
        <YearTable ctx={ctx} accounts={['CURRENT_ASSETS', 'CURRENT_LIABILITIES']} />
        <Learn terms={[['유동자산', '1년 안에 현금으로 바꿀 수 있는 자산'], ['유동부채', '1년 안에 갚아야 하는 빚'], ['유동비율', '유동자산 ÷ 유동부채 × 100. 「비율 › 안정성」에서 볼 수 있습니다.']]}>
          유동자산이 유동부채보다 크면 1년 안의 빚을 1년 안의 자산으로 감당할 수 있다는 뜻입니다.</Learn></>}
    <Source>{ctx.finState.data?.source} · 연말 기준</Source>
  </FinGate>
}

function YearDetail({ ctx, year }) {
  const y = ctx.years.find(item => item.year === year)
  return <FinGate ctx={ctx}>{!y ? <Empty>{year}년 자료가 없습니다.</Empty> : <>
    <div className="stock-day-nav">{ctx.years.some(i => i.year === year - 1) && <button type="button" className="secondary-action" onClick={() => ctx.go([...ctx.path.slice(0, -1), String(year - 1)])}>← {year - 1}년</button>}
      {ctx.years.some(i => i.year === year + 1) && <button type="button" className="secondary-action" onClick={() => ctx.go([...ctx.path.slice(0, -1), String(year + 1)])}>{year + 1}년 →</button>}</div>
    <h3 className="stock-h3">{year} 사업연도 · 금액</h3>
    <dl className="stock-table-dl">{Object.entries(ACCOUNT_LABEL).map(([k, l]) => <div key={k}><dt>{l}</dt><dd>{formatWon(y.values[k])}</dd></div>)}</dl>
    <h3 className="stock-h3">{year} 사업연도 · 비율</h3>
    <dl className="stock-table-dl">{RATIOS.map(r => <div key={r.key}><dt><button type="button" className="link-button" onClick={() => ctx.go(['inspect', 'ratios', r.group, r.key])}>{r.label}</button></dt>
      <dd>{r.key === 'operatingGrowth' && y.ratios.operatingTurn ? y.ratios.operatingTurn : r.key === 'netGrowth' && y.ratios.netTurn ? y.ratios.netTurn
        : ratioText(r, y.ratios[r.key], r.group === '성장성')}</dd></div>)}</dl>
    <div className="stock-actions"><a className="secondary-action" href={y.dartUrl} target="_blank" rel="noopener noreferrer">사업보고서 원문 (DART) ↗</a>
      <a className="secondary-action" href={`/explore?q=${encodeURIComponent(ctx.name)}`}>공식 근거로 확인 (Inspect)</a>
      <button type="button" className="secondary-action" onClick={() => ctx.go(['ask', 'quote'])}>이 보고서 원문에 묻기</button></div>
    <Source>OpenDART 주요계정 · 접수번호 {y.receiptNo} · {y.fsDiv === 'CFS' ? '연결' : '별도'} 재무제표</Source>
  </>}</FinGate>
}

function RatioGroup({ ctx, group }) {
  const list = RATIOS.filter(r => r.group === group)
  return <FinGate ctx={ctx}>
    <div className="table-scroll"><table className="stock-table"><thead><tr><th>비율</th>{ctx.years.map(y => <th key={y.year}>{y.year}</th>)}</tr></thead>
      <tbody>{list.map(r => <tr key={r.key} className="clickable" onClick={() => ctx.go(['inspect', 'ratios', group, r.key])}>
        <th scope="row"><button type="button" className="link-button">{r.label}</button></th>
        {ctx.years.map(y => <td key={y.year}>{r.key === 'operatingGrowth' && y.ratios.operatingTurn ? y.ratios.operatingTurn : r.key === 'netGrowth' && y.ratios.netTurn ? y.ratios.netTurn : ratioText(r, y.ratios[r.key], group === '성장성')}</td>)}</tr>)}</tbody></table></div>
    <LineChart ariaLabel={`${group} 비율`} dates={ctx.years.map(y => `${y.year}-12-31`)} series={list.map((r, i) => ({ key: r.key, label: r.label, color: [COLORS.a, COLORS.b, COLORS.c, COLORS.d][i], values: ctx.years.map(y => y.ratios[r.key]) }))}
      format={v => group === '활동성' ? `${Number(v).toFixed(1)}회` : `${Number(v).toFixed(0)}%`} baseline={group === '성장성' ? 0 : null} />
    <Learn terms={list.map(r => [r.label, `${r.meaning} 계산: ${r.formula}`])}>비율 이름을 누르면 계산 과정과 연도별 값을 더 자세히 봅니다.</Learn>
    <Source>OpenDART 주요계정으로 AIRA가 계산</Source>
  </FinGate>
}

function RatioDetail({ ctx, ratioKey }) {
  const r = RATIOS.find(item => item.key === ratioKey)
  if (!r) return <Empty>알 수 없는 비율입니다.</Empty>
  const parts = { operatingMargin: ['OPERATING_INCOME', 'REVENUE'], netMargin: ['NET_INCOME', 'REVENUE'], roe: ['NET_INCOME', 'TOTAL_EQUITY'], roa: ['NET_INCOME', 'TOTAL_ASSETS'],
    debtRatio: ['TOTAL_LIABILITIES', 'TOTAL_EQUITY'], currentRatio: ['CURRENT_ASSETS', 'CURRENT_LIABILITIES'], equityRatio: ['TOTAL_EQUITY', 'TOTAL_ASSETS'],
    assetTurnover: ['REVENUE', 'TOTAL_ASSETS'], equityTurnover: ['REVENUE', 'TOTAL_EQUITY'],
    revenueGrowth: ['REVENUE'], operatingGrowth: ['OPERATING_INCOME'], netGrowth: ['NET_INCOME'] }[r.key]
  return <FinGate ctx={ctx}>
    <div className="stock-formula"><strong>{r.label}</strong><span>= {r.formula}</span><p>{r.meaning}</p>
      <AiAssist kind="TERM" save={{ companyId: ctx.companyId, companyName: ctx.name }} subject={r.label} loggedIn={ctx.loggedIn} facts={[{ label: '계산식', value: r.formula, period: '' },
        ...ctx.years.slice(-3).flatMap(y => [...parts.map(k => ({ label: ACCOUNT_LABEL[k], value: formatWon(y.values[k]), period: `${y.year}` })),
          { label: r.label, value: ratioText(r, y.ratios[r.key], r.group === '성장성'), period: `${y.year}` }])].slice(0, 20)} />
      {ctx.years.length >= 2 && <AiAssist kind="WHY" save={{ companyId: ctx.companyId, companyName: ctx.name }} subject={`${r.label} (${ctx.years[ctx.years.length - 2].year} → ${ctx.years[ctx.years.length - 1].year})`} loggedIn={ctx.loggedIn}
        facts={ctx.years.slice(-2).flatMap(y => [...parts.map(k => ({ label: ACCOUNT_LABEL[k], value: formatWon(y.values[k]), period: `${y.year}` })),
          { label: r.label, value: ratioText(r, y.ratios[r.key], r.group === '성장성'), period: `${y.year}` }])} />}
      <p className="muted">일반적으로 {r.better === 'high' ? '높을수록' : '낮을수록'} 여유 있게 읽지만, 업종마다 기준이 달라 같은 회사의 해마다 변화를 함께 보는 것이 좋습니다.</p></div>
    <LineChart ariaLabel={r.label} dates={ctx.years.map(y => `${y.year}-12-31`)} series={[{ key: r.key, label: r.label, color: COLORS.a, values: ctx.years.map(y => y.ratios[r.key]) }]}
      format={v => r.unit === '회' ? `${Number(v).toFixed(2)}회` : `${Number(v).toFixed(1)}%`} baseline={r.group === '성장성' ? 0 : null} />
    <div className="table-scroll"><table className="stock-table"><thead><tr><th>사업연도</th>{parts.map(k => <th key={k}>{ACCOUNT_LABEL[k]}</th>)}<th>{r.label}</th></tr></thead>
      <tbody>{[...ctx.years].reverse().map(y => <tr key={y.year} className="clickable" onClick={() => ctx.go(['inspect', 'earnings', 'all', String(y.year)])}>
        <td><button type="button" className="link-button">{y.year}</button></td>{parts.map(k => <td key={k}>{formatWon(y.values[k])}</td>)}
        <td>{ratioText(r, y.ratios[r.key], r.group === '성장성')}</td></tr>)}</tbody></table></div>
    <WhyBox ctx={ctx} ratio={r} />
    <Source>OpenDART 주요계정으로 AIRA가 계산 · 소수 첫째 자리 반올림</Source>
  </FinGate>
}

function AccountDetail({ ctx, account }) {
  if (!ACCOUNT_LABEL[account]) return <Empty>알 수 없는 항목입니다.</Empty>
  return <FinGate ctx={ctx}>
    <BarChart ariaLabel={ACCOUNT_LABEL[account]} categories={ctx.years.map(y => String(y.year))} series={[{ key: account, label: ACCOUNT_LABEL[account], color: COLORS.a, values: ctx.years.map(y => y.values[account] ?? null) }]} format={formatWon} />
    <YearTable ctx={{ ...ctx, path: ['inspect', 'earnings', 'all'] }} accounts={[account]} />
    <Source>{ctx.finState.data?.source}</Source>
  </FinGate>
}

// ---------------- 보다 · 공시 ----------------
function FilingRows({ ctx, items, base }) {
  return <ul className="stock-filings">{items.map(f => <li key={f.receiptNo}>
    <button type="button" onClick={() => ctx.go([...base, f.receiptNo])}><time>{f.date}</time><span className="filing-tag" style={{ '--tag': CATEGORY_COLOR[f.category] }}>{FILING_LABEL[f.category]}</span>
      <strong>{f.title}</strong><small>{f.filer}</small></button></li>)}</ul>
}

function FilingList({ ctx, category }) {
  if (ctx.filingState.loading) return <p className="status">공시 목록을 불러오는 중입니다.</p>
  if (ctx.filingState.error || ctx.filingState.data?.state === 'UNAVAILABLE') return <Empty action={<button type="button" className="secondary-action" onClick={ctx.reloadFilings}>다시 시도</button>}>DART 공시 목록을 지금 불러오지 못했습니다.</Empty>
  const items = category === 'ALL' ? ctx.filings : ctx.filings.filter(f => f.category === category)
  return <>
    {items.length ? <FilingRows ctx={ctx} items={items} base={['inspect', 'filings', category]} /> : <Empty>최근 1년 동안 이 분류의 공시가 없습니다.</Empty>}
    <Learn terms={[['정기보고서', '사업·반기·분기보고서. 실적과 사업 내용을 정해진 때 알립니다.'], ['실적', '잠정 실적 등 숫자를 미리 알리는 공시'], ['지분·주주', '큰 주주나 임원의 주식 보유 변화'],
      ['주요사항', '증자·자사주·합병·큰 계약·배당처럼 회사에 중요한 결정'], ['제출인', '공시를 낸 사람이나 회사(회사 본인이 아닐 수 있습니다)']]}>분류는 AIRA가 제목으로 묶은 것이며 공시의 법적 종류를 판정하지 않습니다. 공시를 누르면 상세와 전후 주가를 봅니다.</Learn>
    <Source>{ctx.filingState.data?.source} · {ctx.filingState.data?.from} ~ {ctx.filingState.data?.to}</Source>
  </>
}

function FilingDetail({ ctx, receiptNo }) {
  const f = ctx.filings.find(item => item.receiptNo === receiptNo)
  if (ctx.filingState.loading) return <p className="status">공시를 불러오는 중입니다.</p>
  if (!f) return <Empty>이 공시를 최근 1년 목록에서 찾지 못했습니다. <a href={`https://dart.fss.or.kr/dsaf001/main.do?rcpNo=${receiptNo}`} target="_blank" rel="noopener noreferrer">DART에서 열기 ↗</a></Empty>
  const around = aroundFilings(ctx.points, [f], 5)[0]
  const day = ctx.points.find(p => p.date >= f.date)
  return <>
    <h3 className="stock-h3">{f.title}</h3>
    <dl className="stock-table-dl">{[['접수일', f.date], ['분류(AIRA)', FILING_LABEL[f.category]], ['제출인', f.filer], ['접수번호', f.receiptNo], ['비고', f.remark || '없음']]
      .map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}</dl>
    <h3 className="stock-h3">접수일 전후 주가</h3>
    {around ? <dl className="stock-stats"><div><dt>전날 종가 ({around.beforeDate})</dt><dd>{formatPrice(around.beforeClose)}</dd></div>
      <div><dt>{around.days}거래일 뒤 종가 ({around.afterDate})</dt><dd>{formatPrice(around.afterClose)}</dd></div>
      <div><dt>변화</dt><dd className={around.change > 0 ? 'up' : around.change < 0 ? 'down' : ''}>{formatPercent(around.change)}</dd></div></dl>
      : <p className="muted">전후 주가를 비교할 날짜가 아직 충분하지 않습니다.</p>}
    <p className="muted">같은 기간에 함께 나타난 변화일 뿐, 이 공시 때문이라는 뜻은 아닙니다.</p>
    <div className="stock-actions"><a className="primary-action" href={f.url} target="_blank" rel="noopener noreferrer">DART 원문 열기 ↗</a>
      {day && <button type="button" className="secondary-action" onClick={() => ctx.go(['inspect', 'price', 'daily', day.date])}>{day.date} 시세 보기</button>}
      </div>
    <h3 className="stock-h3">🤖 이 공시 원문에 묻기</h3>
    <FilingQuote key={f.receiptNo} receipt={f.receiptNo} loggedIn={ctx.loggedIn} />
    <Source>OpenDART 공시검색 · KRX 일별 매매정보</Source>
  </>
}

// ---------------- 보다 · 뉴스 ----------------
function NewsView({ ctx, view }) {
  const s = ctx.newsState
  const searchUrl = s.data?.searchUrl ?? `https://search.naver.com/search.naver?where=news&query=${encodeURIComponent(ctx.name)}`
  if (view === 'links') return <ul className="stock-question-list">
    <li><a href={searchUrl} target="_blank" rel="noopener noreferrer">네이버 뉴스에서 「{ctx.name}」 찾기 ↗</a></li>
    <li><a href={`https://dart.fss.or.kr/dsab007/main.do?option=corp&textCrpNm=${encodeURIComponent(ctx.name)}`} target="_blank" rel="noopener noreferrer">DART에서 이 회사 공시 전체 보기 ↗</a></li>
    {ctx.overview.data?.profile?.ir_url && <li><a href={ctx.overview.data.profile.ir_url} target="_blank" rel="noopener noreferrer">회사 IR 페이지 ↗</a></li>}
  </ul>
  return <>
    {s.loading && <p className="status">최근 기사를 찾는 중입니다(최대 10초).</p>}
    {!s.loading && s.data?.items?.length > 0 && <ul className="stock-news">{s.data.items.map(item => <li key={item.url}>
      <a href={item.url} target="_blank" rel="noopener noreferrer"><strong>{item.title}</strong>{item.summary && <span className="stock-news-summary">{item.summary}</span>}<small>{item.domain} · {String(item.seenAt).slice(0, 10)}</small></a></li>)}</ul>}
    {!s.loading && !(s.data?.items?.length > 0) && <Empty action={<><a className="secondary-action" href={searchUrl} target="_blank" rel="noopener noreferrer">네이버 뉴스에서 보기 ↗</a>
      <button type="button" className="secondary-action" onClick={() => ctx.go(['inspect', 'filings', 'ALL'])}>회사가 직접 낸 소식(공시) 보기</button>
      <button type="button" className="secondary-action" onClick={() => ctx.loadNews(true)}>다시 찾기</button></>}>
      {s.data?.state === 'NO_DATA' ? '최근 2주 동안 찾은 기사가 없습니다.' : '기사 제공처와 연결이 잠시 원활하지 않아요. 네이버 뉴스에서 바로 볼 수 있고, 회사가 직접 밝힌 소식은 「공시」에 항상 있어요.'}</Empty>}
    <Learn>기사는 언론사의 해석이 담긴 글이라 AIRA의 공식 근거로 쓰지 않습니다. 기사 속 숫자가 맞는지는 「묻다 › 이 말, 사실이야?」에서 확인해 보세요.</Learn>
    <Source>{s.data?.source ?? 'GDELT DOC 2.0'}</Source>
  </>
}

// ---------------- 잇다 ----------------
function RelateFilings({ ctx, view }) {
  const shown = lastDays(ctx.points, Number(ctx.range))
  const from = shown[0]?.date ?? ''
  const items = ctx.filings.filter(f => f.date >= from)
  const [only, setOnly] = useState('ALL')
  const visible = only === 'ALL' ? items : items.filter(f => f.category === only)
  return <PriceGate ctx={ctx}>
    <RangePicker ctx={ctx} />
    <div className="stock-range" role="group" aria-label="공시 분류">{FILING_CATEGORIES.map(([k, l]) => <button key={k} type="button" aria-pressed={only === k} onClick={() => setOnly(k)}>{l}</button>)}</div>
    {view === 'chart' ? <LineChart ariaLabel="주가와 공시 날짜" dates={shown.map(p => p.date)} series={[{ key: 'close', label: '종가', color: COLORS.a, values: shown.map(p => Number(p.close)) }]}
      markers={visible.map(f => ({ date: f.date, receiptNo: f.receiptNo, label: `[${FILING_LABEL[f.category]}] ${f.title}`, color: CATEGORY_COLOR[f.category] }))} format={v => Number(v).toLocaleString('ko-KR')}
      onMarker={m => ctx.go(['relate', 'filings', 'chart', m.receiptNo])} />
      : <FilingRows ctx={ctx} items={visible} base={['relate', 'filings', 'list']} />}
    <div className="chart-legend">{FILING_CATEGORIES.slice(1).map(([k, l]) => <span key={k}><i style={{ background: CATEGORY_COLOR[k] }} />{l}</span>)}</div>
    <Learn>점은 공시가 접수된 날입니다(휴장일에 접수된 공시는 표시되지 않을 수 있습니다). 그래프에서 점 근처를 누르면 공시 제목이 보입니다. 함께 나타난 것이지 원인이라는 뜻은 아닙니다.</Learn>
    <Source>KRX 일별 매매정보 · OpenDART 공시검색</Source>
  </PriceGate>
}

function RelateMarket({ ctx, view }) {
  const shown = lastDays(ctx.points, Number(ctx.range))
  const pair = normalizeTogether(shown, ctx.indexPoints)
  const label = ctx.market === 'KOSDAQ' ? '코스닥' : '코스피'
  if (!ctx.indexPoints.length) return <Empty>{label} 지수 1년치를 거래소에서 받아 채우는 중입니다.</Empty>
  const last = pair[pair.length - 1]
  return <PriceGate ctx={ctx}>
    <RangePicker ctx={ctx} />
    {view === 'compare' ? <LineChart ariaLabel={`${ctx.name}과 ${label} 비교`} dates={pair.map(p => p.date)} baseline={100}
      series={[{ key: 'a', label: ctx.name, color: COLORS.a, values: pair.map(p => p.a) }, { key: 'b', label: `${label} 지수`, color: COLORS.n, values: pair.map(p => p.b) }]} format={v => Number(v).toFixed(0)} />
      : <div className="table-scroll"><table className="stock-table"><thead><tr><th>날짜</th><th>{ctx.name} (시작=100)</th><th>{label} (시작=100)</th><th>차이</th></tr></thead>
        <tbody>{[...pair].reverse().slice(0, 60).map(p => <tr key={p.date}><td>{p.date}</td><td>{p.a}</td><td>{p.b}</td><td>{(p.a - p.b).toFixed(1)}</td></tr>)}</tbody></table></div>}
    {last && <dl className="stock-stats"><div><dt>{ctx.name}</dt><dd>{formatPercent(last.a - 100)}</dd></div><div><dt>{label}</dt><dd>{formatPercent(last.b - 100)}</dd></div>
      <div><dt>차이</dt><dd>{(last.a - last.b).toFixed(1)}포인트</dd></div></dl>}
    <Learn terms={[['시작=100', '두 값의 크기가 달라도 같은 출발선에서 비교하려고 첫날을 100으로 맞춘 것입니다. 120이면 20% 오른 것입니다.']]}>종목이 시장 전체와 비슷하게 움직였는지, 따로 움직였는지 볼 수 있습니다.</Learn>
    <Source>KRX 일별 매매정보 · KRX 지수 일별 시세</Source>
  </PriceGate>
}

function RelateAround({ ctx }) {
  const rows = aroundFilings(ctx.points, ctx.filings.filter(f => ['PERIODIC', 'EARNINGS', 'MATERIAL'].includes(f.category)), 5)
  return <PriceGate ctx={ctx}>
    {rows.length ? <div className="table-scroll"><table className="stock-table"><thead><tr><th>접수일</th><th>분류</th><th>공시</th><th>전날 종가</th><th>뒤 종가(거래일)</th><th>변화</th></tr></thead>
      <tbody>{rows.map(r => <tr key={r.receiptNo} className="clickable" onClick={() => ctx.go(['relate', 'around', 'list', r.receiptNo])}><td>{r.date}</td><td>{FILING_LABEL[r.category]}</td>
        <td><button type="button" className="link-button">{r.title}</button></td><td>{formatPrice(r.beforeClose)}</td><td>{formatPrice(r.afterClose)} ({r.days}일)</td>
        <td className={r.change > 0 ? 'up' : r.change < 0 ? 'down' : ''}>{formatPercent(r.change)}</td></tr>)}</tbody></table></div>
      : <Empty>비교할 정기보고서·실적·주요사항 공시가 아직 주가 기간 안에 없습니다.</Empty>}
    <Learn>정기보고서·실적·주요사항 공시만 골랐습니다. 같은 기간 시장 전체 움직임도 함께 봐야 해서 「주가 × 시장」과 같이 보시길 권합니다.</Learn>
    <Source>KRX 일별 매매정보 · OpenDART 공시검색</Source>
  </PriceGate>
}

function RelateEvents({ ctx }) {
  const events = ctx.eventState.data?.events ?? []
  if (ctx.eventState.loading) return <p className="status">확인된 사건을 불러오는 중입니다.</p>
  return <>{events.length ? <ol className="stock-filings">{[...events].sort((a, b) => String(b.title).localeCompare(String(a.title))).map(e => <li key={e.eventId}>
    <button type="button" onClick={() => ctx.onOpenEvent?.(e.eventId, e.companies ?? [{ companyId: ctx.companyId, companyName: ctx.name }])}>
      <time>{e.occurredAt?.slice(0, 10) ?? (e.evidence?.[0]?.externalId ? `${e.evidence[0].externalId.slice(0, 4)}.${e.evidence[0].externalId.slice(4, 6)}.${e.evidence[0].externalId.slice(6, 8)} 접수` : '날짜 확인 중')}</time>
      <strong>{fixSubjectParticle(e.title)}</strong><small>판단 보기 →</small></button></li>)}</ol>
    : <Empty>AIRA가 공식 근거로 확인해 둔 사건이 아직 없습니다.</Empty>}
    <Learn>사건을 누르면 「가늠하다」 화면에서 확인된 사실과 AIRA 해석을 나눠 봅니다.</Learn></>
}

// ---------------- 가늠하다 ----------------
function AssessSummary({ ctx }) {
  const items = yearOverYear(ctx.years)
  const better = items.filter(i => i.reading === 'better')
  const worse = items.filter(i => i.reading === 'worse')
  const show = i => i.kind === 'amount' ? `${formatWon(i.before)} → ${formatWon(i.now)} (${formatPercent(i.change)})` : `${formatPercent(i.before, { sign: false })} → ${formatPercent(i.now, { sign: false })}`
  return <FinGate ctx={ctx}>
    <div className="stock-two">
      <section><h3 className="stock-h3">늘거나 좋아진 쪽 {better.length}</h3><ul className="stock-bullets">{better.map(i => <li key={i.key}><button type="button" className="link-button" onClick={() => ctx.go(['assess', 'yoy', i.key])}>{i.label}</button> {show(i)}</li>)}</ul></section>
      <section><h3 className="stock-h3">줄거나 나빠진 쪽 {worse.length}</h3><ul className="stock-bullets">{worse.map(i => <li key={i.key}><button type="button" className="link-button" onClick={() => ctx.go(['assess', 'yoy', i.key])}>{i.label}</button> {show(i)}</li>)}</ul></section>
    </div>
    {ctx.yearStats && <p className="stock-hint">최근 1년 주가 {formatPercent(ctx.yearStats.change)}{ctx.marketStats ? ` · 같은 기간 ${ctx.market === 'KOSDAQ' ? '코스닥' : '코스피'} ${formatPercent(ctx.marketStats.change)}` : ''}</p>}
    <Learn>"좋아진 쪽"은 매출·이익·비율이 일반적으로 읽히는 방향으로 움직였다는 뜻일 뿐입니다(부채는 줄면 좋아진 쪽). 판단은 직접 하세요.</Learn>
    <Source>OpenDART 주요계정 · {ctx.years.slice(-2).map(y => y.year).join(' vs ')}</Source>
  </FinGate>
}

function AssessYoy({ ctx }) {
  const items = yearOverYear(ctx.years)
  return <FinGate ctx={ctx}>{items.length ? <div className="table-scroll"><table className="stock-table">
    <thead><tr><th>항목</th><th>{items[0].years[0]}</th><th>{items[0].years[1]}</th><th>변화</th><th>일반적 읽기</th></tr></thead>
    <tbody>{items.map(i => <tr key={i.key} className="clickable" onClick={() => ctx.go(['assess', 'yoy', i.key])}><th scope="row"><button type="button" className="link-button">{i.label}</button></th>
      <td>{i.kind === 'amount' ? formatWon(i.before) : formatPercent(i.before, { sign: false })}</td><td>{i.kind === 'amount' ? formatWon(i.now) : formatPercent(i.now, { sign: false })}</td>
      <td>{i.kind === 'amount' ? formatPercent(i.change) : `${i.change > 0 ? '+' : ''}${i.change}%p`}</td>
      <td className={i.reading === 'better' ? 'good' : i.reading === 'worse' ? 'bad' : ''}>{i.reading === 'better' ? '좋아진 쪽' : i.reading === 'worse' ? '나빠진 쪽' : '같음'}</td></tr>)}</tbody></table></div>
    : <Empty>비교할 두 해의 자료가 아직 없습니다.</Empty>}
    <Learn terms={[['%p(퍼센트포인트)', '비율끼리의 차이입니다. 10%에서 12%가 되면 2%p 오른 것입니다.']]}>항목을 누르면 그 항목의 연도별 상세로 들어갑니다.</Learn>
  </FinGate>
}

function AssessCheck({ ctx }) {
  const items = checklist(ctx.years, ctx.yearStats, ctx.marketStats, ctx.filings)
  return <><ul className="stock-checks">{items.map(item => <li key={item.label}><span className={`check-badge ${item.value === true ? 'yes' : item.value === false ? 'no' : 'na'}`}>{item.value === true ? '예' : item.value === false ? '아니오' : '자료 없음'}</span>
    <strong>{item.label}</strong><small>{item.basis}</small></li>)}</ul>
    <AiAssist kind="CHECK" save={{ companyId: ctx.companyId, companyName: ctx.name }} subject={ctx.name} loggedIn={ctx.loggedIn} facts={items.slice(0, 10).map(item => ({ label: item.label,
      value: `${item.value === true ? '예' : item.value === false ? '아니오' : '자료 없음'} (${item.basis})`, period: '' }))} />
    <Learn>체크리스트는 공부와 점검을 돕는 질문 모음입니다. "예"가 많다고 좋은 회사라는 뜻이 아니고, 업종에 따라 기준이 다릅니다.</Learn></>
}

function AssessUnknown({ ctx }) {
  return <ul className="stock-bullets">
    <li>재무는 사업보고서·분기보고서의 주요 계정 기준입니다. 회사가 정정 공시를 내면 숫자가 바뀔 수 있습니다.</li>
    <li>주가는 하루 단위 종가이며 실시간 시세가 아닙니다. 보관 기간이 1년보다 짧을 때는 화면에 실제 개월 수를 적습니다.</li>
    <li>투자자별 매매동향(외국인·기관), 외국인 지분율, 공매도, 증권사 목표주가·컨센서스는 무료 공식 자료로 받을 수 없어 넣지 않았습니다.</li>
    <li>업종 비교는 통계청 업종 코드가 같은 회사끼리 묶은 것이라, 실제 사업이 다른 회사가 섞일 수 있습니다.</li>
    <li>공시 분류는 제목으로 묶은 것입니다. 정확한 내용은 원문에서 확인하세요. 「왜 그럴까」는 회사가 쓴 원문 문장만 근거로 씁니다.</li>
    <li>기사는 공식 근거가 아니며, 기사 제공처 사정에 따라 목록이 비어 있을 수 있습니다.</li>
    <li>미래 실적·주가는 예측하지 않고, 사라·팔라는 말을 하지 않습니다.</li>
    {!ctx.years.length && <li>이 회사는 사업보고서 주요계정을 아직 받지 못했습니다.</li>}
  </ul>
}

// ---------------- 추가: 용어 사전 · 시장 평가 · 종목 비교 ----------------
function Glossary({ ctx }) {
  const [q, setQ] = useState('')
  const [learned, setLearned] = useState(new Set())
  useEffect(() => { if (ctx.loggedIn) getStudy().then(d => setLearned(new Set((d.terms ?? []).map(t => t.term)))).catch(() => {}) }, [ctx.loggedIn])
  async function learn(term) { try { await learnTerm(term); setLearned(prev => new Set([...prev, term])) } catch { /* 데모·비로그인은 저장 안 함 */ } }
  const list = GLOSSARY.filter(item => !q.trim() || `${item.term} ${item.text} ${item.group}`.toLowerCase().includes(q.trim().toLowerCase()))
  const groups = [...new Set(list.map(item => item.group))]
  return <div className="stock-find">
    <label htmlFor="glossary-q">용어 찾기</label>
    <input id="glossary-q" value={q} onChange={event => setQ(event.target.value)} placeholder="예: PER, 부채비율, 시가총액" />
    {groups.map(group => <section key={group}><h3 className="stock-h3">{group}</h3>
      <dl className="stock-glossary">{list.filter(item => item.group === group).map(item => <div key={item.term}><dt>{item.term}
        {ctx.loggedIn && (learned.has(item.term) ? <span className="learned-badge">✔ 이해함</span>
          : <button type="button" className="link-button learn-button" onClick={() => learn(item.term)}>이해했어요</button>)}</dt><dd>{item.text}</dd></div>)}</dl></section>)}
    {ctx.loggedIn && <p className="stock-hint">이해한 용어 {learned.size}개 / 전체 {GLOSSARY.length}개 · <a href="/study">내 공부에서 모아 보기</a></p>}
    {!list.length && <p className="stock-hint">찾는 용어가 없습니다.</p>}
  </div>
}

function ValuationView({ ctx }) {
  const last = ctx.points[ctx.points.length - 1]
  const year = ctx.years[ctx.years.length - 1]
  const v = valuation(last, year)
  if (!v) return <FinGate ctx={ctx}><PriceGate ctx={ctx}><Empty>시가총액과 재무 값이 모두 있어야 계산할 수 있습니다.</Empty></PriceGate></FinGate>
  const shown = (key, unit) => v[key] === null ? '계산 안 함' : unit === '원' ? formatPrice(v[key]) : `${v[key]}배`
  return <>
    <dl className="stock-table-dl">{VALUATIONS.map(item => <div key={item.key}><dt>{item.label}</dt><dd>{shown(item.key, item.unit)}</dd></div>)}</dl>
    <p className="stock-hint">기준: {v.date} 시가총액 {formatWon(v.marketCap)} · {v.year} 사업연도 재무 값 · 상장주식수 {formatCount(v.shares)}</p>
    <div className="table-scroll"><table className="stock-table"><thead><tr><th>지표</th><th>계산식</th><th>이 회사 계산</th></tr></thead>
      <tbody>{VALUATIONS.map(item => <tr key={item.key}><th scope="row">{item.label}</th><td>{item.formula}</td>
        <td>{item.key === 'per' ? `${formatWon(v.marketCap)} ÷ ${formatWon(year.values.NET_INCOME)}`
          : item.key === 'pbr' ? `${formatWon(v.marketCap)} ÷ ${formatWon(year.values.TOTAL_EQUITY)}`
          : item.key === 'psr' ? `${formatWon(v.marketCap)} ÷ ${formatWon(year.values.REVENUE)}`
          : item.key === 'eps' ? `${formatWon(year.values.NET_INCOME)} ÷ ${formatCount(v.shares)}` : `${formatWon(year.values.TOTAL_EQUITY)} ÷ ${formatCount(v.shares)}`}</td></tr>)}</tbody></table></div>
    <Learn terms={VALUATIONS.map(item => [item.label, item.meaning])}>지금 주가에는 오늘의 시가총액이, 재무 값에는 지난 사업연도 값이 쓰여 시점이 다릅니다. 같은 업종 회사와 함께 보면 이해가 쉽습니다(「잇다 › 종목 × 종목」).</Learn>
    <Source>KRX 일별 매매정보(시가총액·상장주식수) · OpenDART 주요계정으로 AIRA가 계산</Source>
  </>
}

const PEER_COLORS = [COLORS.a, COLORS.b, COLORS.c, COLORS.d]

function PeerCompare({ ctx, peerId }) {
  const ids = String(peerId ?? '').split(',').filter(id => /^[0-9a-f-]{36}$/i.test(id) && id !== ctx.companyId).slice(0, 3)
  const key = ids.join(',')
  const [term, setTerm] = useState('')
  const [found, setFound] = useState({ loading: false, items: [], error: null })
  const [peers, setPeers] = useState({ loading: false, list: [] })
  useEffect(() => {
    if (!key) { setPeers({ loading: false, list: [] }); return undefined }
    let alive = true
    setPeers(current => ({ ...current, loading: true }))
    Promise.all(key.split(',').map(id => Promise.all([getStockOverview(id), getStockPrices(id, 370), getStockFinancials(id)])
      .then(([overview, prices, financials]) => ({ id, overview, prices, financials })).catch(() => ({ id, error: true }))))
      .then(list => alive && setPeers({ loading: false, list }))
    return () => { alive = false }
  }, [key])
  const setIds = next => ctx.go(next.length ? ['relate', 'peer', 'compare', next.join(',')] : ['relate', 'peer', 'compare'])
  async function search(event) {
    event.preventDefault()
    if (!term.trim()) return
    setFound({ loading: true, items: [], error: null })
    try {
      const data = await searchEntities(term.trim())
      setFound({ loading: false, items: (data?.entities ?? []).filter(item => item.entityType === 'COMPANY' && item.entityId !== ctx.companyId), error: null })
    } catch (error) { setFound({ loading: false, items: [], error }) }
  }
  const companies = [{ id: ctx.companyId, name: ctx.name, points: ctx.points, years: ctx.years },
    ...peers.list.filter(p => !p.error && p.overview).map(p => ({ id: p.id, name: p.overview.name, points: p.prices?.points ?? [], years: withRatios(p.financials?.years ?? []) }))]
    .map(c => ({ ...c, last: c.points[c.points.length - 1], year: c.years[c.years.length - 1], stats: priceStats(lastDays(c.points, 365)) }))
  const chart = normalizeMany(lastDays(ctx.points, Number(ctx.range)), companies.slice(1).map(c => c.points))
  const span = spanName(companies[0].stats)
  const rows = [
    ['시가총액', x => formatWon(x.last?.marketCap)], [`${span.change} 주가 변화`, x => formatPercent(x.stats?.change)],
    ['최근 사업연도', x => x.year ? `${x.year.year}` : '—'], ['매출', x => formatWon(x.year?.values?.REVENUE)], ['영업이익', x => formatWon(x.year?.values?.OPERATING_INCOME)],
    ['영업이익률', x => formatPercent(x.year?.ratios?.operatingMargin, { sign: false })], ['ROE', x => formatPercent(x.year?.ratios?.roe, { sign: false })],
    ['부채비율', x => formatPercent(x.year?.ratios?.debtRatio, { sign: false })], ['매출 증가율', x => formatPercent(x.year?.ratios?.revenueGrowth)],
    ['PER · PBR', x => { const v = valuation(x.last, x.year); return v ? `${v.per ?? '—'}배 · ${v.pbr ?? '—'}배` : '—' }],
  ]
  const failed = peers.list.filter(p => p.error).length
  return <>
    <form className="stock-find stock-peer-search" onSubmit={search}>
      <label htmlFor="peer-q">비교할 회사 더하기 <small className="muted">({ids.length}/3)</small></label>
      <div><input id="peer-q" value={term} onChange={event => setTerm(event.target.value)} placeholder="예: SK하이닉스" disabled={ids.length >= 3} /><button type="submit" className="secondary-action" disabled={ids.length >= 3}>찾기</button></div>
    </form>
    {found.loading && <p className="status">찾는 중입니다.</p>}
    {found.items.length > 0 && <ul className="stock-question-list">{found.items.slice(0, 8).map(item => <li key={item.entityId}>
      <button type="button" disabled={ids.includes(item.entityId) || ids.length >= 3} onClick={() => { setFound({ loading: false, items: [], error: null }); setTerm(''); setIds([...ids, item.entityId]) }}>
        {item.canonicalName}<span aria-hidden="true">{ids.includes(item.entityId) ? '이미 비교 중' : '＋ 더하기'}</span></button></li>)}</ul>}
    {ids.length > 0 && <div className="peer-chips" aria-label="비교 중인 회사">{companies.slice(1).map((c, i) => <span key={c.id} className="peer-chip" style={{ borderColor: PEER_COLORS[i + 1] }}>
      <a href={`/stock/${c.id}`}>{c.name}</a><button type="button" aria-label={`${c.name} 빼기`} onClick={() => setIds(ids.filter(id => id !== c.id))}>✕</button></span>)}</div>}
    {!ids.length && !found.items.length && <p className="stock-hint">회사 이름을 찾아 더하면 최대 4개 회사를 나란히 비교합니다. 같은 업종끼리 비교하면 비율을 읽기 쉽습니다.</p>}
    {peers.loading && <p className="status">비교 회사 자료를 불러오는 중입니다.</p>}
    {failed > 0 && <p className="stock-hint">{failed}개 회사 자료를 불러오지 못했습니다.</p>}
    {companies.length > 1 && !peers.loading && <>
      <RangePicker ctx={ctx} />
      {chart.length > 1 ? <LineChart ariaLabel={`${companies.map(c => c.name).join(', ')} 주가 비교`} dates={chart.map(p => p.date)} baseline={100}
        series={companies.map((c, i) => ({ key: c.id, label: c.name, color: PEER_COLORS[i], values: chart.map(p => p.values[i]) }))} format={value => Number(value).toFixed(0)} />
        : <Empty>같은 날짜의 주가가 모자라 그래프를 그릴 수 없습니다.</Empty>}
      <div className="table-scroll"><table className="stock-table"><thead><tr><th>항목</th>{companies.map((c, i) => <th key={c.id} style={{ color: PEER_COLORS[i] }}>{c.name}</th>)}</tr></thead>
        <tbody>{rows.map(([label, pick]) => <tr key={label}><th scope="row">{label}</th>{companies.map(c => <td key={c.id}>{pick(c)}</td>)}</tr>)}</tbody></table></div>
      <AiAssist kind="COMPARE" save={{ companyId: ctx.companyId, companyName: ctx.name }} subject={companies.map(c => c.name).join(' vs ')} loggedIn={ctx.loggedIn}
        facts={rows.slice(0, 9).flatMap(([label, pick]) => companies.map((c, i) => ({ label: `${String.fromCharCode(65 + i)} ${c.name} ${label}`, value: String(pick(c)), period: '' }))).slice(0, 24)} />
      <Learn>주가 그래프는 모든 회사를 시작일 100으로 맞췄습니다. 비율은 각 회사의 최근 사업연도 기준이라 연도가 다를 수 있으니 표의 「최근 사업연도」를 함께 보세요.</Learn>
      <Source>KRX 일별 매매정보 · OpenDART 주요계정</Source>
    </>}
  </>
}

// ---------------- 보다 · 주주·배당·직원 ----------------
function OwnersView({ ctx, view }) {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  useEffect(() => {
    let alive = true
    getStockReportDetails(ctx.companyId).then(data => alive && setState({ loading: false, data, error: null }))
      .catch(error => alive && setState({ loading: false, data: null, error }))
    return () => { alive = false }
  }, [ctx.companyId])
  if (state.loading) return <p className="status">사업보고서 주요정보를 불러오는 중입니다.</p>
  if (state.error || state.data?.state === 'UNAVAILABLE') return <Empty>DART에서 사업보고서 주요정보를 지금 불러오지 못했습니다. 잠시 뒤 다시 열어 주세요.</Empty>
  if (state.data?.state === 'NO_DATA') return <Empty>최근 2년 사업보고서에 이 정보가 없습니다(신규 상장·금융업 등).</Empty>
  const d = state.data
  const year = d.year
  const footer = <><Source>{d.source} · {year} 사업연도{d.receiptNo ? ` · 접수번호 ${d.receiptNo}` : ''}</Source>
    {d.dartUrl && <div className="stock-actions"><a className="secondary-action" href={d.dartUrl} target="_blank" rel="noopener noreferrer">사업보고서 원문 (DART) ↗</a></div>}</>
  if (view === 'dividend') {
    const rows = d.dividends ?? []
    const dps = rows.find(r => /주당\s*현금배당금/.test(r.se ?? '') && /보통/.test(r.stock_knd ?? '보통'))
    const yieldRow = rows.find(r => /현금배당수익률/.test(r.se ?? '') && /보통/.test(r.stock_knd ?? '보통'))
    const payout = rows.find(r => /현금배당성향/.test(r.se ?? ''))
    return <>
      {dps && <BarChart ariaLabel="주당 현금배당금" categories={[String(year - 2), String(year - 1), String(year)]}
        series={[{ key: 'dps', label: '주당 현금배당금(보통주)', color: COLORS.a, values: [dartNumber(dps.lwfr), dartNumber(dps.frmtrm), dartNumber(dps.thstrm)] }]} format={v => `${Number(v).toLocaleString('ko-KR')}원`} height={200} />}
      <dl className="stock-stats">{[['주당 현금배당금', dps, '원'], ['현금배당수익률', yieldRow, '%'], ['현금배당성향', payout, '%']].map(([label, row, unit]) =>
        <div key={label}><dt>{label} ({year})</dt><dd>{row && dartNumber(row.thstrm) !== null ? `${dartNumber(row.thstrm).toLocaleString('ko-KR')}${unit}` : '자료 없음'}</dd></div>)}</dl>
      {rows.length ? <div className="table-scroll"><table className="stock-table"><thead><tr><th>구분</th><th>주식 종류</th><th>{year}</th><th>{year - 1}</th><th>{year - 2}</th></tr></thead>
        <tbody>{rows.map((r, i) => <tr key={i}><td>{r.se}</td><td>{r.stock_knd || '—'}</td><td>{r.thstrm || '—'}</td><td>{r.frmtrm || '—'}</td><td>{r.lwfr || '—'}</td></tr>)}</tbody></table></div>
        : <Empty>배당 자료가 없습니다(배당을 하지 않았거나 보고서에 없음).</Empty>}
      {dps && <section className="why-box"><h3 className="stock-h3">🤔 배당, 왜 이만큼 줄까?</h3>
        <ExplainStory subject={`${ctx.name} 배당 (${year - 2} → ${year})`} loggedIn={ctx.loggedIn} save={{ companyId: ctx.companyId, companyName: ctx.name }}
          receipt={d.receiptNo} question="배당 정책 주주환원 배당금 결정 기준"
          facts={rows.filter(r => /주당 현금배당금|현금배당성향|현금배당수익률|당기순이익/.test(r.se ?? '')).slice(0, 6)
            .flatMap(r => [{ label: `${r.se} ${r.stock_knd && r.stock_knd !== '-' ? r.stock_knd : ''}`.trim(), value: `${year}: ${r.thstrm} / ${year - 1}: ${r.frmtrm} / ${year - 2}: ${r.lwfr}`, period: '사업보고서' }])} /></section>}
      <Learn terms={[['주당 현금배당금', '주식 1주에 현금으로 나눠 준 돈입니다.'], ['현금배당수익률', '주당 배당금 ÷ 주가 × 100. 주가 대비 배당이 몇 %인지입니다.'],
        ['현금배당성향', '배당금 총액 ÷ 당기순이익 × 100. 번 돈 중 얼마를 주주에게 나눠 줬는지입니다.']]}>숫자는 사업보고서에 회사가 적은 그대로이며 단위(백만원·원·%)는 「구분」에 적혀 있습니다.</Learn>
      {footer}</>
  }
  if (view === 'holders') {
    const rows = (d.holders ?? []).filter(r => (r.nm ?? '').trim() && !/^계$|합계/.test((r.nm ?? '').trim()))
    const total = (d.holders ?? []).find(r => /^계$|합계/.test((r.nm ?? '').trim()))
    const top = [...rows].sort((a, b) => (dartNumber(b.trmend_posesn_stock_qota_rt) ?? 0) - (dartNumber(a.trmend_posesn_stock_qota_rt) ?? 0)).slice(0, 10)
    return <>
      {total && <dl className="stock-stats"><div><dt>최대주주와 특수관계인 합계 지분율 (기말)</dt><dd>{total.trmend_posesn_stock_qota_rt ?? '—'}%</dd></div>
        <div><dt>기초 대비</dt><dd>{total.bsis_posesn_stock_qota_rt ?? '—'}% → {total.trmend_posesn_stock_qota_rt ?? '—'}%</dd></div></dl>}
      {top.length > 0 && <BarChart ariaLabel="주주별 기말 지분율" categories={top.map(r => r.nm.length > 6 ? `${r.nm.slice(0, 6)}…` : r.nm)}
        series={[{ key: 'q', label: '기말 지분율(%)', color: COLORS.d, values: top.map(r => dartNumber(r.trmend_posesn_stock_qota_rt)) }]} format={v => `${Number(v).toFixed(1)}%`} height={220} />}
      {rows.length ? <div className="table-scroll"><table className="stock-table"><thead><tr><th>이름</th><th>관계</th><th>주식 종류</th><th>기초 주식수</th><th>기초 지분율</th><th>기말 주식수</th><th>기말 지분율</th></tr></thead>
        <tbody>{rows.map((r, i) => <tr key={i}><td>{r.nm}</td><td>{r.relate || '—'}</td><td>{r.stock_knd || '—'}</td><td>{r.bsis_posesn_stock_co || '—'}</td><td>{r.bsis_posesn_stock_qota_rt || '—'}%</td>
          <td>{r.trmend_posesn_stock_co || '—'}</td><td>{r.trmend_posesn_stock_qota_rt || '—'}%</td></tr>)}</tbody></table></div>
        : <Empty>최대주주 현황 자료가 없습니다.</Empty>}
      <Learn terms={[['최대주주', '가장 많은 주식을 가진 주주입니다.'], ['특수관계인', '최대주주의 가족·계열사처럼 함께 묶어 보는 사람·회사입니다.'],
        ['기초 / 기말', '사업연도 시작 시점 / 끝 시점입니다.']]}>최근 지분 변화는 「공시 › 지분·주주」에서 더 최신 내용을 볼 수 있습니다.</Learn>
      {footer}</>
  }
  const rows = d.employees ?? []
  const total = rows.reduce((sum, r) => sum + (dartNumber(r.sm) ?? 0), 0)
  return <>
    <dl className="stock-stats"><div><dt>직원 수 합계 ({year})</dt><dd>{total ? `${total.toLocaleString('ko-KR')}명` : '자료 없음'}</dd></div></dl>
    {rows.length ? <div className="table-scroll"><table className="stock-table"><thead><tr><th>사업부문</th><th>성별</th><th>정규직</th><th>계약직</th><th>합계</th><th>평균 근속</th><th>1인 평균 급여</th></tr></thead>
      <tbody>{rows.map((r, i) => <tr key={i}><td>{r.fo_bbm || '—'}</td><td>{r.sexdstn || '—'}</td><td>{r.rgllbr_co || '—'}</td><td>{r.cnttk_co || '—'}</td><td>{r.sm || '—'}</td>
        <td>{r.avrg_cnwk_sdytrn || '—'}</td><td>{r.jan_salary_am || '—'}</td></tr>)}</tbody></table></div>
      : <Empty>직원 현황 자료가 없습니다.</Empty>}
    <Learn terms={[['평균 근속', '직원이 회사에 다닌 평균 기간입니다.'], ['1인 평균 급여', '회사가 보고서에 적은 1년 1인 평균 급여액입니다(단위는 보고서 기준).']]}>직원 수와 급여는 회사 규모와 사람에 대한 투자를 가늠하는 데 참고합니다.</Learn>
    {footer}</>
}

// ---------------- 잇다·가늠하다 · 업종 ----------------
function metricText(kind, value) {
  if (value === null || value === undefined) return '—'
  if (kind === 'won') return formatWon(value)
  if (kind === 'pct') return formatPercent(value, { sign: false })
  return `${value}배`
}

function IndustryView({ ctx, view }) {
  const s = ctx.peerState
  if (s.loading || !s.asked) return <p className="status">같은 업종 회사를 모으는 중입니다.</p>
  if (s.error) return <Empty>같은 업종 자료를 불러오지 못했습니다.</Empty>
  const d = s.data
  if (d?.state === 'NO_INDUSTRY') return <Empty>이 회사의 업종코드를 아직 받지 못했습니다. 서버가 전 회사의 기업개황을 받는 중이면 잠시 뒤 다시 열어 주세요.</Empty>
  if (d?.state === 'TOO_FEW') return <Empty>같은 업종 회사 자료가 아직 모이지 않았습니다(서버가 처음 켜진 뒤 30분쯤 걸립니다).</Empty>
  const items = d.items ?? []
  const title = `${industryName(d.matchedCode)} · 업종코드 ${d.matchedCode}${d.matchedCode !== d.indutyCode ? ` (이 회사 ${d.indutyCode})` : ''} · ${d.total}개 회사`
  const intro = <p className="stock-hint">{title}</p>
  const learn = <Learn terms={[['업종코드', '통계청 한국표준산업분류 코드입니다. 회사가 DART에 신고한 코드를 쓰며, 같은 회사가 5곳보다 적으면 더 넓은 분류로 묶습니다.'],
    ['중간값', '값을 순서대로 세웠을 때 가운데 값입니다. 아주 큰 회사 하나에 끌려가지 않아 평균보다 업종의 보통 모습을 잘 보여 줍니다.']]}>
    업종 안에서도 사업 내용이 다를 수 있습니다. 회사 이름을 눌러 각 회사 화면으로 들어가 보세요.</Learn>
  const source = <Source>{d.source} · 각 회사의 최근 사업연도·최근 거래일 기준</Source>
  if (view === 'table') return <>{intro}
    <div className="table-scroll"><table className="stock-table"><thead><tr><th>회사</th><th>시장</th>{PEER_METRICS.map(m => <th key={m.key}>{m.label}</th>)}<th>재무 연도</th></tr></thead>
      <tbody>{items.map(item => { const m = peerMetrics(item)
        return <tr key={item.companyId} className={item.self ? 'self-row' : ''}><td>{item.self ? <strong>{item.name} (이 회사)</strong>
          : <a href={`/stock/${item.companyId}?v=relate.industry.table`}>{item.name}</a>}</td><td>{item.market === 'KOSPI' ? '코스피' : '코스닥'}</td>
          {PEER_METRICS.map(metric => <td key={metric.key}>{metricText(metric.kind, m[metric.key])}</td>)}<td>{m.year ?? '—'}</td></tr> })}</tbody></table></div>
    {learn}{source}</>
  const rows = PEER_METRICS.map(metric => ({ metric, pos: peerPosition(items, metric.key) }))
  if (view === 'rank') return <>{intro}
    <div className="chart-legend"><span><i style={{ background: 'var(--ink)' }} />이 회사</span><span><i style={{ background: 'var(--muted)', borderRadius: 2 }} />업종 중간값</span></div>
    <ul className="stock-ranks">{rows.map(({ metric, pos }) => <li key={metric.key}>
      <div><strong>{metric.label}</strong><span>{pos.rank ? `${pos.count}곳 중 ${pos.rank}위 (큰 값 기준)` : '이 회사 값 없음'}</span></div>
      <PositionBar pos={pos} kind={metric.kind} />
    </li>)}</ul>
    {learn}{source}</>
  return <>{intro}
    <div className="table-scroll"><table className="stock-table"><thead><tr><th>지표</th><th>이 회사</th><th>업종 중간값</th><th>비교</th></tr></thead>
      <tbody>{rows.map(({ metric, pos }) => <tr key={metric.key}><th scope="row">{metric.label}</th><td>{metricText(metric.kind, pos.value)}</td><td>{metricText(metric.kind, pos.median)}</td>
        <td>{pos.value === null || pos.median === null ? '—' : pos.value > pos.median ? '중간값보다 높음' : pos.value < pos.median ? '중간값보다 낮음' : '같음'}</td></tr>)}</tbody></table></div>
    {(() => { const annual = [...ctx.years].reverse().find(y => y.receiptNo)
      return <section className="why-box"><h3 className="stock-h3">🤔 이 회사는 왜 업종과 다를까?</h3>
        <ExplainStory subject={`${ctx.name}과 같은 업종(${industryName(d.matchedCode)}) 비교`} loggedIn={ctx.loggedIn} save={{ companyId: ctx.companyId, companyName: ctx.name }}
          receipt={annual?.receiptNo} question="경쟁력 시장점유율 주요 제품 사업 구조"
          facts={rows.flatMap(({ metric, pos }) => [{ label: `이 회사 ${metric.label}`, value: metricText(metric.kind, pos.value), period: '최근' }, { label: `업종 중간값 ${metric.label}`, value: metricText(metric.kind, pos.median), period: `${pos.count}곳` }])
            .filter(f => f.value !== '—').slice(0, 14)} /></section> })()}
    <Learn>높다·낮다는 사실만 적었습니다. PER·PBR은 높고 낮음이 좋고 나쁨을 뜻하지 않아 해석을 붙이지 않았습니다. 부채비율은 낮을수록 빚이 적다는 뜻입니다.</Learn>
    {source}</>
}

function PositionBar({ pos, kind }) {
  if (pos.value === null || pos.min === null || pos.max === null || pos.max === pos.min) return <div className="stock-rangebar"><span>—</span><div /><span>—</span></div>
  const at = v => ((v - pos.min) / (pos.max - pos.min)) * 100
  return <div className="stock-rangebar peer"><span>{metricText(kind, pos.min)}</span>
    <div><i style={{ left: `${Math.max(0, Math.min(100, at(pos.value)))}%` }} title="이 회사" /><b style={{ left: `${Math.max(0, Math.min(100, at(pos.median)))}%` }} title="업종 중간값" /></div>
    <span>{metricText(kind, pos.max)}</span></div>
}

// ---------------- 보다 · 분기 실적 ----------------
function useLazy(loader, key) {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  useEffect(() => {
    let alive = true
    setState({ loading: true, data: null, error: null })
    loader().then(data => alive && setState({ loading: false, data, error: null })).catch(error => alive && setState({ loading: false, data: null, error }))
    return () => { alive = false }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
  return state
}

function QuarterView({ ctx }) {
  const state = useLazy(() => getStockQuarters(ctx.companyId), ctx.companyId)
  const [account, setAccount] = useState('REVENUE')
  if (state.loading) return <p className="status">분기 실적을 불러오는 중입니다.</p>
  const items = state.data?.items ?? []
  if (state.error || !items.length) return <Empty>분기 보고서 값을 DART에서 받아 채우는 중이거나 아직 없습니다. 연간 실적은 「매출」「영업이익」에서 볼 수 있습니다.</Empty>
  const value = (item, key) => (item.values?.[key] === undefined ? null : Number(item.values[key]))
  const sameQuarterLastYear = item => items.find(other => other.year === item.year - 1 && other.quarter === item.quarter)
  return <>
    <div className="stock-range" role="group" aria-label="항목">{[['REVENUE', '매출'], ['OPERATING_INCOME', '영업이익'], ['NET_INCOME', '당기순이익']].map(([k, l]) =>
      <button key={k} type="button" aria-pressed={account === k} onClick={() => setAccount(k)}>{l}</button>)}</div>
    <BarChart ariaLabel={`분기 ${ACCOUNT_LABEL[account]}`} categories={items.map(i => `${String(i.year).slice(2)}.${i.quarter}Q`)}
      series={[{ key: account, label: `${ACCOUNT_LABEL[account]} (3개월)`, color: COLORS.a, values: items.map(i => value(i, account)) }]} format={formatWon} />
    <div className="table-scroll"><table className="stock-table"><thead><tr><th>분기</th><th>매출</th><th>영업이익</th><th>당기순이익</th><th>영업이익률</th><th>전년 같은 분기 대비({ACCOUNT_LABEL[account]})</th><th>보고서</th></tr></thead>
      <tbody>{[...items].reverse().map(item => { const prev = sameQuarterLastYear(item); const a = value(item, account); const b = prev ? value(prev, account) : null
        return <tr key={item.label}><td>{item.label}</td><td>{formatWon(value(item, 'REVENUE'))}</td><td className={value(item, 'OPERATING_INCOME') < 0 ? 'down' : ''}>{formatWon(value(item, 'OPERATING_INCOME'))}</td>
          <td className={value(item, 'NET_INCOME') < 0 ? 'down' : ''}>{formatWon(value(item, 'NET_INCOME'))}</td>
          <td>{value(item, 'REVENUE') ? formatPercent(value(item, 'OPERATING_INCOME') / value(item, 'REVENUE') * 100, { sign: false }) : '—'}</td>
          <td>{a !== null && b ? formatPercent((a - b) / Math.abs(b) * 100) : '—'}</td>
          <td>{item.receiptNo ? <a href={`https://dart.fss.or.kr/dsaf001/main.do?rcpNo=${item.receiptNo}`} target="_blank" rel="noopener noreferrer">원문 ↗</a> : '—'}</td></tr> })}</tbody></table></div>
    <Learn terms={[['분기(3개월) 값', '분기·반기·3분기 보고서는 1월부터의 누적 금액을 적습니다. AIRA는 누적끼리 빼서 3개월 값을 만듭니다(4분기 = 연간 − 3분기 누적).'],
      ['전년 같은 분기 대비', '계절에 따라 실적이 달라지는 회사가 많아, 바로 앞 분기보다 작년 같은 분기와 비교하는 경우가 많습니다.']]}>막대 위에 마우스를 올리면 그 분기 값을 봅니다.</Learn>
    <Source>{state.data?.source}</Source>
  </>
}

// ---------------- 보다 · 임원·주요주주 거래 / 5% 대량보유 ----------------
function OwnershipView({ ctx, view }) {
  const state = useLazy(() => getStockOwnership(ctx.companyId), ctx.companyId)
  const [limit, setLimit] = useState(30)
  if (state.loading) return <p className="status">지분 공시를 불러오는 중입니다.</p>
  if (state.error || state.data?.state === 'UNAVAILABLE') return <Empty>DART 지분 공시를 지금 불러오지 못했습니다.</Empty>
  const n = v => dartNumber(v)
  const date = r => (r.rcept_dt ? (r.rcept_dt.length === 8 ? `${r.rcept_dt.slice(0, 4)}-${r.rcept_dt.slice(4, 6)}-${r.rcept_dt.slice(6)}` : r.rcept_dt) : '—')
  const link = r => r.rcept_no ? <a href={`https://dart.fss.or.kr/dsaf001/main.do?rcpNo=${r.rcept_no}`} target="_blank" rel="noopener noreferrer">원문 ↗</a> : '—'
  if (view === 'insiders') {
    const rows = state.data?.insiders ?? []
    const recent = rows.filter(r => (r.rcept_no ?? '') >= new Date(Date.now() - 90 * 864e5).toISOString().slice(0, 10).replaceAll('-', ''))
    const plus = recent.filter(r => (n(r.sp_stock_lmp_irds_cnt) ?? 0) > 0).length
    const minus = recent.filter(r => (n(r.sp_stock_lmp_irds_cnt) ?? 0) < 0).length
    return <>
      <dl className="stock-stats"><div><dt>최근 90일 보고</dt><dd>{recent.length}건</dd></div><div><dt>주식이 늘어난 보고</dt><dd className="up">{plus}건</dd></div>
        <div><dt>주식이 줄어든 보고</dt><dd className="down">{minus}건</dd></div></dl>
      {rows.length ? <div className="table-scroll"><table className="stock-table"><thead><tr><th>접수일</th><th>보고자</th><th>직위</th><th>주요주주</th><th>보유 주식수</th><th>증감</th><th>보유 비율</th><th>원문</th></tr></thead>
        <tbody>{rows.slice(0, limit).map((r, i) => <tr key={`${r.rcept_no}-${i}`}><td>{date(r)}</td><td>{r.repror ?? '—'}</td><td>{r.isu_exctv_ofcps && r.isu_exctv_ofcps !== '-' ? r.isu_exctv_ofcps : (r.isu_exctv_rgist_at ?? '—')}</td>
          <td>{r.isu_main_shrholdr ?? '—'}</td><td>{r.sp_stock_lmp_cnt ?? '—'}</td><td className={(n(r.sp_stock_lmp_irds_cnt) ?? 0) > 0 ? 'up' : (n(r.sp_stock_lmp_irds_cnt) ?? 0) < 0 ? 'down' : ''}>{r.sp_stock_lmp_irds_cnt ?? '—'}</td>
          <td>{r.sp_stock_lmp_rate ? `${r.sp_stock_lmp_rate}%` : '—'}</td><td>{link(r)}</td></tr>)}</tbody></table></div> : <Empty>임원·주요주주 소유 보고가 없습니다.</Empty>}
      {rows.length > limit && <button type="button" className="secondary-action" onClick={() => setLimit(limit + 50)}>더 보기 ({rows.length - limit}건)</button>}
      <Learn terms={[['임원·주요주주 소유보고', '임원이나 10% 이상 주요주주가 자기 회사 주식을 사고팔면 5영업일 안에 알려야 하는 보고입니다.'], ['증감', '이번 보고로 늘거나 줄어든 주식 수입니다.']]}>
        회사 안 사람들의 거래라 관심 있게 보는 자료지만, 세금·상속·담보 등 이유가 다양해 그것만으로 의미를 단정할 수 없습니다.</Learn>
      <Source>{state.data?.source}</Source></>
  }
  const rows = state.data?.major ?? []
  return <>
    {rows.length ? <div className="table-scroll"><table className="stock-table"><thead><tr><th>접수일</th><th>보고자</th><th>보고 구분</th><th>보유 주식수</th><th>증감</th><th>보유 비율</th><th>비율 증감</th><th>사유</th><th>원문</th></tr></thead>
      <tbody>{rows.slice(0, limit).map((r, i) => <tr key={`${r.rcept_no}-${i}`}><td>{date(r)}</td><td>{r.repror ?? '—'}</td><td>{r.report_tp ?? '—'}</td><td>{r.stkqy ?? '—'}</td>
        <td className={(n(r.stkqy_irds) ?? 0) > 0 ? 'up' : (n(r.stkqy_irds) ?? 0) < 0 ? 'down' : ''}>{r.stkqy_irds ?? '—'}</td><td>{r.stkrt ? `${r.stkrt}%` : '—'}</td><td>{r.stkrt_irds ?? '—'}</td>
        <td className="wrap">{r.report_resn ?? '—'}</td><td>{link(r)}</td></tr>)}</tbody></table></div> : <Empty>5% 이상 대량보유 보고가 없습니다.</Empty>}
    {rows.length > limit && <button type="button" className="secondary-action" onClick={() => setLimit(limit + 50)}>더 보기 ({rows.length - limit}건)</button>}
    <Learn terms={[['대량보유 상황보고(5% 룰)', '한 회사 주식을 5% 넘게 가지게 되거나, 그 뒤 1% 넘게 바뀌면 알려야 하는 보고입니다.'], ['보고 구분', '처음 5%를 넘긴 신규 보고인지, 바뀐 것을 알리는 변동 보고인지입니다.']]}>
      국민연금·자산운용사 같은 큰 투자자의 지분 변화를 볼 수 있습니다.</Learn>
    <Source>{state.data?.source}</Source></>
}

// ---------------- 가늠하다 · 다섯 방향 요약 ----------------
function RadarView({ ctx }) {
  const table = useLazy(() => getScreener(), 'screener')
  const peers = ctx.peerState
  if (table.loading || peers.loading || !peers.asked) return <p className="status">같은 업종 회사와 비교하는 중입니다.</p>
  const code = peers.data?.matchedCode
  const rows = (table.data?.rows ?? []).map(screenRow)
  const group = code ? rows.filter(r => String(r.indutyCode ?? '').startsWith(code)) : []
  const me = rows.find(r => r.companyId === ctx.companyId)
  if (!me || group.length < 3) return <Empty>같은 업종 비교 자료가 아직 충분하지 않습니다(전 회사 자료를 채우는 데 30분쯤 걸립니다).</Empty>
  const axes = RADAR_AXES.map(axis => ({ ...axis, value: me[axis.key], pct: percentileOf(group.map(r => r[axis.key]), me[axis.key], axis.invert) }))
  const size = 300, c = size / 2, R = 110
  const point = (i, v) => { const a = -Math.PI / 2 + (i * 2 * Math.PI) / axes.length; return [c + Math.cos(a) * R * v, c + Math.sin(a) * R * v] }
  const poly = axes.map((ax, i) => point(i, (ax.pct ?? 0) / 100).join(',')).join(' ')
  const show = ax => ax.key === 'marketCap' ? formatWon(ax.value) : formatPercent(ax.value, { sign: ax.key === 'revenueGrowth' })
  return <>
    <p className="stock-hint">{industryName(code)} · 업종코드 {code} · {group.length}개 회사와 비교</p>
    <div className="stock-radar">
      <svg viewBox={`0 0 ${size} ${size}`} width="100%" style={{ maxWidth: 360 }} role="img" aria-label="다섯 방향 요약 도형">
        {[0.25, 0.5, 0.75, 1].map(r => <polygon key={r} points={axes.map((_, i) => point(i, r).join(',')).join(' ')} className="radar-ring" />)}
        {axes.map((ax, i) => { const [x, y] = point(i, 1); const [lx, ly] = point(i, 1.2)
          return <g key={ax.key}><line x1={c} y1={c} x2={x} y2={y} className="radar-ring" /><text x={lx} y={ly + 4} textAnchor="middle" className="radar-label">{ax.label}</text></g> })}
        <polygon points={poly} className="radar-shape" />
        {axes.map((ax, i) => ax.pct !== null && <circle key={ax.key} cx={point(i, ax.pct / 100)[0]} cy={point(i, ax.pct / 100)[1]} r="3.5" className="radar-dot" />)}
      </svg>
      <ul className="stock-ranks">{axes.map(ax => <li key={ax.key}><div><strong>{ax.label} <small className="muted">({ax.hint})</small></strong>
        <span>{ax.pct === null ? '자료 없음' : `업종 안 ${ax.pct}백분위`}</span></div><small className="muted">이 회사 {show(ax)}</small></li>)}</ul>
    </div>
    <Learn terms={[['백분위', '업종 회사들을 값 순서로 세웠을 때의 위치입니다. 100이면 업종에서 가장 높고, 50이면 가운데입니다.'],
      ['안정성', '부채비율이 낮을수록 바깥쪽에 그렸습니다.']]}>도형이 크다고 좋은 회사라는 뜻은 아닙니다. 어떤 방향에서 업종과 다른지 한눈에 보려는 그림입니다. 각 항목은 「업종 안에서」에서 회사별 값으로 확인할 수 있습니다.</Learn>
    <Source>OpenDART 주요계정 · 기업개황(업종) · KRX 일별 매매정보로 AIRA가 계산</Source>
  </>
}

// 처음 온 사람을 위한 세 걸음 길잡이(접을 수 있음). 고른 값은 이 브라우저에만 기억한다.
function Guide({ go }) {
  const [open, setOpen] = useState(() => { try { return window.localStorage.getItem('aira-stock-guide') !== 'closed' } catch { return true } })
  const close = () => { setOpen(false); try { window.localStorage.setItem('aira-stock-guide', 'closed') } catch { /* 저장이 막혀도 괜찮다 */ } }
  if (!open) return <button type="button" className="link-button stock-guide-open" onClick={() => setOpen(true)}>처음이세요? 보는 순서 안내</button>
  return <aside className="stock-guide" aria-label="보는 순서">
    <ol>
      <li><button type="button" onClick={() => go(['inspect', 'price', 'trend'])}><b>1</b><span><strong>보다</strong> 주가·실적·재무를 그래프로</span></button></li>
      <li><button type="button" onClick={() => go(['relate', 'industry', 'table'])}><b>2</b><span><strong>잇다</strong> 시장·업종·공시와 겹쳐 보기</span></button></li>
      <li><button type="button" onClick={() => go(['assess', 'radar'])}><b>3</b><span><strong>가늠하다</strong> 한눈에 정리하고 스스로 판단</span></button></li>
    </ol>
    <button type="button" className="link-button" onClick={close}>안내 닫기</button>
  </aside>
}

// ---------------- 보다 · 현금흐름 ----------------
const CASH_LABEL = { OPERATING: '영업활동 현금흐름', INVESTING: '투자활동 현금흐름', FINANCING: '재무활동 현금흐름', CAPEX: '유형자산 취득(설비 투자)', FREE_CASH_FLOW: '잉여현금흐름', CASH_END: '기말 현금' }

function CashFlowView({ ctx }) {
  const state = useLazy(() => getStockCashFlow(ctx.companyId), ctx.companyId)
  if (state.loading) return <p className="status">현금흐름표를 DART에서 불러오는 중입니다.</p>
  if (state.error || state.data?.state === 'UNAVAILABLE') return <Empty>DART 현금흐름표를 지금 불러오지 못했습니다.</Empty>
  const years = state.data?.years ?? []
  if (!years.length) return <Empty>최근 사업보고서에서 현금흐름표를 찾지 못했습니다.</Empty>
  const v = (y, k) => (y.values?.[k] === undefined || y.values?.[k] === null ? null : Number(y.values[k]))
  return <>
    <BarChart ariaLabel="현금흐름" categories={years.map(y => String(y.year))} series={[['OPERATING', COLORS.a], ['INVESTING', COLORS.c], ['FINANCING', COLORS.d]]
      .map(([k, color]) => ({ key: k, label: CASH_LABEL[k], color, values: years.map(y => v(y, k)) }))} format={formatWon} />
    <div className="table-scroll"><table className="stock-table"><thead><tr><th>항목</th>{years.map(y => <th key={y.year}>{y.year}</th>)}</tr></thead>
      <tbody>{Object.keys(CASH_LABEL).map(k => <tr key={k}><th scope="row">{CASH_LABEL[k]}</th>{years.map(y => <td key={y.year} className={v(y, k) < 0 ? 'down' : ''}>{formatWon(v(y, k))}</td>)}</tr>)}</tbody></table></div>
    {years.length >= 2 && (() => { const a = years[years.length - 2], b = years[years.length - 1]
      return <section className="why-box"><h3 className="stock-h3">🤔 현금흐름, 왜 이렇게 바뀌었을까? ({a.year} → {b.year})</h3>
        <ExplainStory subject={`${ctx.name} 현금흐름 (${a.year} → ${b.year})`} loggedIn={ctx.loggedIn} save={{ companyId: ctx.companyId, companyName: ctx.name }}
          receipt={state.data?.receiptNo} question="영업활동 현금흐름 투자활동 시설투자 변동 원인"
          facts={Object.keys(CASH_LABEL).flatMap(k => [{ label: `${a.year} ${CASH_LABEL[k]}`, value: formatWon(v(a, k)), period: `${a.year}` }, { label: `${b.year} ${CASH_LABEL[k]}`, value: formatWon(v(b, k)), period: `${b.year}` }])
            .filter(f => f.value !== '—').slice(0, 14)} /></section> })()}
    <Learn terms={[['영업활동 현금흐름', '본업으로 실제 들어오고 나간 현금입니다. 이익이 나도 이 값이 마이너스면 돈이 실제로는 안 들어온 것입니다.'],
      ['투자활동 현금흐름', '설비·다른 회사 등에 투자하며 나간(−) 또는 팔아서 들어온(+) 현금입니다.'], ['재무활동 현금흐름', '빌리거나 갚은 돈, 배당 등 주주·채권자와 오간 현금입니다.'],
      ['잉여현금흐름', '영업으로 번 현금에서 설비 투자를 뺀 값입니다. 회사가 자유롭게 쓸 수 있는 현금의 크기를 가늠할 때 씁니다.']]}>
      손익계산서의 이익과 실제 현금은 다를 수 있어서, 이익과 현금흐름을 함께 보면 더 정확하게 이해할 수 있습니다.</Learn>
    <Source>{state.data?.source} · {state.data?.fsDiv === 'CFS' ? '연결' : '별도'}{state.data?.receiptNo ? ` · 접수번호 ${state.data.receiptNo}` : ''}</Source>
  </>
}

function InterestButton({ interest }) {
  if (!interest.user) return <button type="button" className="secondary-action stock-star" onClick={interest.onLogin}>☆ 관심회사 저장 (로그인)</button>
  return <div className="stock-star-wrap"><button type="button" className={`secondary-action stock-star ${interest.saved ? 'saved' : ''}`} disabled={interest.busy}
    aria-pressed={interest.saved} onClick={() => interest.onToggle(interest.saved)}>{interest.saved ? '★ 관심회사' : '☆ 관심회사 저장'}</button>
    {interest.error && <small className="muted">{interest.error.status === 423 || interest.readOnly ? '데모 계정은 저장할 수 없어요.' : '저장하지 못했습니다. 다시 눌러 주세요.'}</small>}</div>
}

// 종목 메모: 로그인한 사람만, 본인만 보는 메모(모든 기기에서 같이 보임).
function NotePanel({ companyId }) {
  const [state, setState] = useState({ loading: true, body: '', saved: '', updatedAt: null, message: null })
  useEffect(() => {
    let alive = true
    getStockNote(companyId).then(note => alive && setState({ loading: false, body: note.body ?? '', saved: note.body ?? '', updatedAt: note.updatedAt, message: null }))
      .catch(() => alive && setState(s => ({ ...s, loading: false })))
    return () => { alive = false }
  }, [companyId])
  async function save() {
    try {
      const note = await putStockNote(companyId, state.body)
      setState(s => ({ ...s, saved: note.body, updatedAt: note.updatedAt, message: '저장했어요.' }))
    } catch (error) {
      setState(s => ({ ...s, message: error.status === 423 ? '데모 계정은 메모를 저장할 수 없어요.' : '저장하지 못했습니다.' }))
    }
  }
  return <details className="stock-note" open={Boolean(state.saved)}><summary>📝 내 메모{state.saved ? ' (있음)' : ''}</summary>
    <textarea value={state.body} maxLength={4000} rows={4} disabled={state.loading} placeholder="이 종목에 대해 기억해 둘 것을 적어 두세요. 나만 볼 수 있어요."
      onChange={event => setState(s => ({ ...s, body: event.target.value, message: null }))} />
    <div className="stock-actions"><button type="button" className="secondary-action" disabled={state.body === state.saved} onClick={save}>저장</button>
      {state.updatedAt && <small className="muted">마지막 저장 {state.updatedAt.slice(0, 16).replace('T', ' ')}</small>}{state.message && <small>{state.message}</small>}</div>
  </details>
}

// ---------------- 묻다 · AI에게 묻기 ----------------
export function companyFacts(ctx) {
  const facts = []
  const last = ctx.points[ctx.points.length - 1]
  if (last) facts.push({ label: '종가', value: formatPrice(last.close), period: last.date }, { label: '시가총액', value: formatWon(last.marketCap), period: last.date })
  if (ctx.yearStats) facts.push({ label: `${spanName(ctx.yearStats).change} 주가 변화율`, value: formatPercent(ctx.yearStats.change), period: `${ctx.yearStats.first.date} — ${ctx.yearStats.last.date}` },
    { label: `${spanName(ctx.yearStats).range} 최고 종가`, value: formatPrice(ctx.yearStats.high.close), period: ctx.yearStats.high.date }, { label: `${spanName(ctx.yearStats).range} 최저 종가`, value: formatPrice(ctx.yearStats.low.close), period: ctx.yearStats.low.date })
  for (const y of ctx.years.slice(-3)) {
    facts.push({ label: `${y.year} 매출`, value: formatWon(y.values.REVENUE), period: `${y.year} 사업연도` },
      { label: `${y.year} 영업이익`, value: formatWon(y.values.OPERATING_INCOME), period: `${y.year} 사업연도` })
  }
  const y = ctx.years[ctx.years.length - 1]
  if (y) facts.push({ label: `${y.year} 영업이익률`, value: formatPercent(y.ratios.operatingMargin, { sign: false }), period: `${y.year}` },
    { label: `${y.year} 부채비율`, value: formatPercent(y.ratios.debtRatio, { sign: false }), period: `${y.year} 말` },
    { label: `${y.year} ROE`, value: formatPercent(y.ratios.roe, { sign: false }), period: `${y.year}` })
  const v = valuation(last, y)
  if (v) facts.push({ label: 'PER', value: v.per === null ? '계산 안 함(적자)' : `${v.per}배`, period: v.date }, { label: 'PBR', value: v.pbr === null ? '—' : `${v.pbr}배`, period: v.date })
  for (const f of ctx.filings.slice(0, 3)) facts.push({ label: '최근 공시', value: f.title, period: f.date })
  return facts.filter(f => f.value && f.value !== '—').slice(0, 20)
}

function AskAi({ ctx }) {
  const [q, setQ] = useState('')
  const [state, setState] = useState({ loading: false, answer: null, error: null })
  const facts = companyFacts(ctx)
  const examples = ['매출은 늘고 있어?', '영업이익이 왜 늘었어?', '빚은 많은 편이야?', '최근 1년 주가는 어땠어?', 'PER이 뭐고 이 회사는 얼마야?']
  async function ask(question) {
    const text = (question ?? q).trim()
    if (!text) return
    setQ(text)
    setState({ loading: true, answer: null, error: null })
    let extra = []
    const annual = [...ctx.years].reverse().find(y => y.receiptNo)
    if (/왜|이유|원인|때문/.test(text) && annual) {
      try { extra = ((await requestQuote(annual.receiptNo, text.slice(0, 120))).quotes ?? []).slice(0, 3)
        .map(q => ({ label: '회사 설명(사업보고서 원문)', value: q.text.slice(0, 700), period: `${annual.year} 사업보고서 문단 ${q.passageNo}` })) } catch { extra = [] }
    }
    try { setState({ loading: false, answer: await requestAssist({ kind: 'QA', subject: text.slice(0, 200), facts: [...facts.slice(0, 24 - extra.length), ...extra] }), error: null }) }
    catch (error) { setState({ loading: false, answer: null, error }) }
  }
  if (!ctx.loggedIn) return <Empty>로그인(또는 첫 화면의 「데모로 둘러보기」)하면 AI에게 물어볼 수 있습니다. 질문은 저장하지 않습니다.</Empty>
  if (facts.length < 3) return <Empty>AI가 참고할 이 회사의 자료를 아직 모으는 중입니다. 잠시 뒤 다시 열어 주세요.</Empty>
  return <div className="stock-find">
    <label htmlFor="ask-ai-q">{ctx.name}에 대해 무엇이 궁금하세요?</label>
    <form className="stock-peer-search" onSubmit={event => { event.preventDefault(); ask() }}><div>
      <input id="ask-ai-q" value={q} maxLength={200} onChange={event => setQ(event.target.value)} placeholder="예: 매출은 늘고 있어?" />
      <button type="submit" className="primary-action" disabled={state.loading}>{state.loading ? '답을 만드는 중…' : '묻기'}</button></div></form>
    <div className="stock-range">{examples.map(example => <button key={example} type="button" onClick={() => ask(example)}>{example}</button>)}</div>
    {state.error && <p className="stock-hint">{state.error.status === 401 ? '로그인이 필요합니다.' : 'AI 답을 지금 받을 수 없습니다.'}</p>}
    {state.answer && <div className="ai-answer"><p className="eyebrow">{state.answer.method === 'AI' ? 'AI 답 · 근거 아님' : '공식 값 정리(규칙)'}</p><p>{state.answer.text}</p>
      {state.answer.method === 'AI' && <SaveToStudy note={{ companyId: ctx.companyId, companyName: ctx.name, kind: 'QA', title: q, body: state.answer.text, source: 'AIRA 공식 값' }} />}
      {(state.answer.limitations ?? []).map(note => <small key={note} className="muted">{note}</small>)}</div>}
    <details className="stock-learn"><summary>AI가 참고한 자료 {facts.length}개</summary><div><dl>{facts.map((f, i) => <div key={i}><dt>{f.label}</dt><dd>{f.value} · {f.period}</dd></div>)}</dl></div></details>
    <Source>AI 답은 위 공식 값 안에서만 만들고, 숫자가 하나라도 자료에 없으면 버리고 규칙 정리로 대신합니다.</Source>
  </div>
}

// ---------------- 보다 · 한눈에 ----------------
function SummaryCard({ title, to, ctx, children }) {
  return <section className="summary-card"><div className="summary-card-head"><h3>{title}</h3>
    <button type="button" className="link-button" onClick={() => ctx.go(to)}>자세히 →</button></div>{children}</section>
}

function SummaryView({ ctx }) {
  const year = lastDays(ctx.points, 365)
  const y = ctx.years[ctx.years.length - 1]
  const v = valuation(ctx.points[ctx.points.length - 1], y)
  const recent = ctx.years.slice(-5)
  return <div className="summary-grid">
    <SummaryCard title="주가 1년" to={['inspect', 'price', 'trend']} ctx={ctx}>
      {year.length > 1 ? <><LineChart ariaLabel="1년 종가" height={150} dates={year.map(p => p.date)} series={[{ key: 'c', label: '종가', color: COLORS.a, values: year.map(p => Number(p.close)) }]}
        format={x => Number(x).toLocaleString('ko-KR')} />
        <p className="summary-line">1년 {formatPercent(ctx.yearStats?.change)} · 최고 {formatPrice(ctx.yearStats?.high.close)} · 최저 {formatPrice(ctx.yearStats?.low.close)}</p></>
        : <p className="muted">주가를 채우는 중입니다.</p>}
    </SummaryCard>
    <SummaryCard title="실적 (사업연도)" to={['inspect', 'earnings', 'all']} ctx={ctx}>
      {recent.length ? <><BarChart ariaLabel="매출과 영업이익" height={150} categories={recent.map(r => String(r.year))}
        series={[{ key: 'r', label: '매출', color: COLORS.a, values: recent.map(r => r.values.REVENUE ?? null) }, { key: 'o', label: '영업이익', color: COLORS.b, values: recent.map(r => r.values.OPERATING_INCOME ?? null) }]} format={formatWon} />
        <p className="summary-line">{y.year} 매출 {formatWon(y.values.REVENUE)} ({y.ratios.revenueGrowth === null ? '—' : formatPercent(y.ratios.revenueGrowth)}) · 영업이익률 {formatPercent(y.ratios.operatingMargin, { sign: false })}</p></>
        : <p className="muted">재무 값을 채우는 중입니다.</p>}
    </SummaryCard>
    <SummaryCard title="재무 건전성" to={['inspect', 'ratios', '안정성']} ctx={ctx}>
      {y ? <dl className="summary-dl"><div><dt>부채비율</dt><dd>{formatPercent(y.ratios.debtRatio, { sign: false })}</dd></div><div><dt>유동비율</dt><dd>{formatPercent(y.ratios.currentRatio, { sign: false })}</dd></div>
        <div><dt>자기자본비율</dt><dd>{formatPercent(y.ratios.equityRatio, { sign: false })}</dd></div><div><dt>ROE</dt><dd>{formatPercent(y.ratios.roe, { sign: false })}</dd></div></dl> : <p className="muted">재무 값을 채우는 중입니다.</p>}
    </SummaryCard>
    <SummaryCard title="시장 평가" to={['inspect', 'ratios', '시장 평가']} ctx={ctx}>
      {v ? <dl className="summary-dl"><div><dt>PER</dt><dd>{v.per === null ? '적자' : `${v.per}배`}</dd></div><div><dt>PBR</dt><dd>{v.pbr === null ? '—' : `${v.pbr}배`}</dd></div>
        <div><dt>EPS</dt><dd>{v.eps === null ? '—' : formatPrice(v.eps)}</dd></div><div><dt>BPS</dt><dd>{v.bps === null ? '—' : formatPrice(v.bps)}</dd></div></dl> : <p className="muted">시가총액과 재무 값이 모두 있어야 계산합니다.</p>}
    </SummaryCard>
    <SummaryCard title="최근 공시" to={['inspect', 'filings', 'ALL']} ctx={ctx}>
      {ctx.filings.length ? <ul className="summary-list">{ctx.filings.slice(0, 5).map(f => <li key={f.receiptNo}><button type="button" className="link-button" onClick={() => ctx.go(['inspect', 'filings', 'ALL', f.receiptNo])}>{f.title}</button><small>{f.date}</small></li>)}</ul>
        : <p className="muted">공시를 불러오는 중이거나 없습니다.</p>}
    </SummaryCard>
    <SummaryCard title="시장과 비교 (1년)" to={['relate', 'market', 'compare']} ctx={ctx}>
      {ctx.yearStats && ctx.marketStats ? <dl className="summary-dl"><div><dt>{ctx.name}</dt><dd>{formatPercent(ctx.yearStats.change)}</dd></div>
        <div><dt>{ctx.market === 'KOSDAQ' ? '코스닥' : '코스피'}</dt><dd>{formatPercent(ctx.marketStats.change)}</dd></div></dl> : <p className="muted">지수를 채우는 중입니다.</p>}
    </SummaryCard>
  </div>
}

// ---------------- "왜 그럴까?" : 숫자로 보면 + 회사가 밝힌 이유 + 같은 업종·시장 ----------------
/** 회사가 공시(사업보고서 등)에 직접 적은 설명을 글자 그대로 찾아 준다. 해석의 출처는 회사이며 AIRA의 판단이 아니다. */
export function CompanyReason({ receipt, question, loggedIn, title = '회사가 공시에서 밝힌 이유' }) {
  const [state, setState] = useState({ status: 'idle', result: null })
  useEffect(() => { setState({ status: 'idle', result: null }) }, [receipt, question])
  if (!receipt) return null
  if (!loggedIn) return <p className="stock-hint">로그인하면 회사가 공시에서 직접 밝힌 이유를 원문 그대로 찾아 드립니다.</p>
  async function run() {
    setState({ status: 'loading', result: null })
    try { setState({ status: 'done', result: await requestQuote(receipt, question) }) } catch { setState({ status: 'error', result: null }) }
  }
  const r = state.result
  return <div className="why-reason">
    <div className="why-reason-head"><strong>📄 {title}</strong>
      {state.status !== 'done' && <button type="button" className="secondary-action" onClick={run} disabled={state.status === 'loading'}>{state.status === 'loading' ? '원문에서 찾는 중…' : '원문에서 찾기'}</button>}</div>
    {state.status === 'error' && <p className="stock-hint">원문을 지금 읽을 수 없습니다. <a href={`https://dart.fss.or.kr/dsaf001/main.do?rcpNo=${receipt}`} target="_blank" rel="noopener noreferrer">DART에서 직접 보기 ↗</a></p>}
    {r && <>{(r.quotes ?? []).length === 0 && <p className="stock-hint">질문과 맞는 문장을 찾지 못했습니다. 원문을 직접 확인해 주세요.</p>}
      {(r.quotes ?? []).slice(0, 4).map(q => <blockquote className="filing-quote-quote" key={q.passageNo}><span>원문 문단 {q.passageNo} · 글자 그대로</span><p>{q.text}</p></blockquote>)}
      {r.explanation && <div className="filing-quote-note"><span className="ai-assist-badge is-ai">AI 쉬운 풀이 · 인용 안의 내용만</span><p className="ai-assist-text">{r.explanation}</p></div>}
      <a className="ai-assist-link" href={`https://dart.fss.or.kr/dsaf001/main.do?rcpNo=${receipt}`} target="_blank" rel="noopener noreferrer">DART 원문 전체 보기 ↗</a></>}
  </div>
}

/** 지표 하나의 "왜?" 상자. account: 금액 항목 키, ratio: 비율 정의(선택). */
function WhyBox({ ctx, account, ratio }) {
  const y = ctx.years[ctx.years.length - 1]
  const prev = ctx.years[ctx.years.length - 2]
  if (!y || !prev) return null
  const label = ratio ? ratio.label : ACCOUNT_LABEL[account]
  const now = ratio ? y.ratios[ratio.key] : y.values[account]
  const before = ratio ? prev.ratios[ratio.key] : prev.values[account]
  if (now === null || now === undefined || before === null || before === undefined) return null
  const up = now > before
  const word = up ? '증가' : now < before ? '감소' : '변동'
  const parts = ratio ? ({ operatingMargin: ['OPERATING_INCOME', 'REVENUE'], netMargin: ['NET_INCOME', 'REVENUE'], roe: ['NET_INCOME', 'TOTAL_EQUITY'], roa: ['NET_INCOME', 'TOTAL_ASSETS'],
    debtRatio: ['TOTAL_LIABILITIES', 'TOTAL_EQUITY'], currentRatio: ['CURRENT_ASSETS', 'CURRENT_LIABILITIES'], equityRatio: ['TOTAL_EQUITY', 'TOTAL_ASSETS'],
    assetTurnover: ['REVENUE', 'TOTAL_ASSETS'], equityTurnover: ['REVENUE', 'TOTAL_EQUITY'], revenueGrowth: ['REVENUE'], operatingGrowth: ['OPERATING_INCOME'], netGrowth: ['NET_INCOME'] }[ratio.key] ?? []) : [account]
  const question = `${prev.year}년 대비 ${y.year}년 ${parts.map(k => ACCOUNT_LABEL[k]).join('·')} ${word} 원인`
  const change = (a, b) => (a ? Math.round((b - a) / Math.abs(a) * 1000) / 10 : null)
  const storyFacts = [
    ...parts.flatMap(k => [{ label: `${prev.year} ${ACCOUNT_LABEL[k]}`, value: formatWon(prev.values[k]), period: `${prev.year} 사업연도` },
      { label: `${y.year} ${ACCOUNT_LABEL[k]}`, value: formatWon(y.values[k]), period: `${y.year} 사업연도` },
      { label: `${ACCOUNT_LABEL[k]} 변화율`, value: formatPercent(change(prev.values[k], y.values[k])), period: `${prev.year}→${y.year}` }]),
    ...(ratio ? [{ label: `${prev.year} ${label}`, value: ratioText(ratio, before, ratio.group === '성장성'), period: `${prev.year}` },
      { label: `${y.year} ${label}`, value: ratioText(ratio, now, ratio.group === '성장성'), period: `${y.year}` }] : []),
  ]
  return <section className="why-box" aria-label={`${label} 왜 그럴까`}>
    <h3 className="stock-h3">🤔 {label}, 왜 {up ? '늘었을까' : now < before ? '줄었을까' : '이럴까'}? ({prev.year} → {y.year})</h3>
    <ExplainStory subject={`${ctx.name} ${label} (${prev.year} → ${y.year})`} facts={storyFacts} receipt={y.receiptNo} question={question} loggedIn={ctx.loggedIn}
      save={{ companyId: ctx.companyId, companyName: ctx.name }} />
    <details className="stock-learn"><summary>근거를 하나씩 보기</summary>
    <div className="why-steps">
      <div><strong>① 숫자로 보면</strong>
        <ul className="stock-bullets">{parts.map(k => { const a = prev.values[k], b = y.values[k]
          return <li key={k}>{ACCOUNT_LABEL[k]}: {formatWon(a)} → {formatWon(b)} ({a ? formatPercent((b - a) / Math.abs(a) * 100) : '—'})</li> })}</ul>
        <AiAssist kind="WHY" save={{ companyId: ctx.companyId, companyName: ctx.name }} subject={`${label} (${prev.year} → ${y.year})`} loggedIn={ctx.loggedIn}
          facts={ctx.years.slice(-2).flatMap(row => [...parts.map(k => ({ label: ACCOUNT_LABEL[k], value: formatWon(row.values[k]), period: `${row.year}` })),
            ...(ratio ? [{ label, value: ratioText(ratio, row.ratios[ratio.key], ratio.group === '성장성'), period: `${row.year}` }] : [])])} /></div>
      <div><CompanyReason receipt={y.receiptNo} question={question} loggedIn={ctx.loggedIn} title={`② 회사가 ${y.year} 사업보고서에서 밝힌 이유`} /></div>
      <div><strong>③ 같은 업종도 그랬을까?</strong>
        <p className="stock-hint">회사만의 일인지 업종 전체의 흐름인지 보려면 같은 업종 회사들과 견줘 보세요.</p>
        <button type="button" className="secondary-action" onClick={() => ctx.go(['relate', 'industry', 'table'])}>업종 안에서 비교하기 →</button></div>
    </div></details>
    <p className="muted why-note">②는 회사가 공시에 직접 적은 설명을 글자 그대로 옮긴 것이고, AIRA는 원인을 새로 지어내지 않습니다.</p>
  </section>
}

/**
 * 이해시키는 설명: 숫자(무엇이 바뀌었나) + 회사가 공시에 직접 쓴 이유(원문 인용) → AI가 이어지는 이야기로 풀어 준다.
 * AI는 받은 자료 안의 숫자·문장만 쓰며(서버가 검사), 원인은 회사 설명에서만 가져오고 예측·추천은 하지 않는다.
 */
export function ExplainStory({ subject, facts, receipt, question, loggedIn, save = null }) {
  const [state, setState] = useState({ status: 'idle', story: null, quotes: [] })
  useEffect(() => { setState({ status: 'idle', story: null, quotes: [] }) }, [subject, receipt, question])
  if (!loggedIn) return <div className="explain-story locked"><strong>🤖 이해하기 쉽게 설명 듣기</strong><p className="stock-hint">로그인(또는 데모)하면 숫자와 회사 공시를 엮어 쉬운 말로 설명해 드립니다.</p></div>
  async function run() {
    setState({ status: 'loading', story: null, quotes: [] })
    let quotes = []
    if (receipt && question) {
      try { quotes = ((await requestQuote(receipt, question)).quotes ?? []).slice(0, 3) } catch { quotes = [] }
    }
    const all = [...facts.slice(0, 24 - quotes.length), ...quotes.map(q => ({ label: '회사 설명(공시 원문)', value: q.text.slice(0, 700), period: `원문 문단 ${q.passageNo}` }))]
    try {
      const story = await requestAssist({ kind: 'EXPLAIN', subject, facts: all })
      setState({ status: 'done', story, quotes })
    } catch { setState({ status: 'error', story: null, quotes }) }
  }
  const st = state.story
  return <div className="explain-story">
    <div className="why-reason-head"><strong>🤖 이해하기 쉽게 설명 듣기</strong>
      {state.status !== 'done' && <button type="button" className="primary-action" onClick={run} disabled={state.status === 'loading'}>{state.status === 'loading' ? '숫자와 공시를 읽는 중…' : '설명 듣기'}</button>}</div>
    {state.status === 'error' && <p className="stock-hint">설명을 지금 만들 수 없습니다. 아래 근거를 직접 확인해 주세요.</p>}
    {st && <><p className="explain-text">{st.text}</p>
      {save && st.method === 'AI' && <SaveToStudy note={{ ...save, kind: 'WHY', title: subject, body: st.text,
        source: [receipt ? `공시 ${receipt}` : null, ...state.quotes.map(q => `원문 문단 ${q.passageNo}`)].filter(Boolean).join(' · ') }} />}
      <small className="muted">{st.method === 'AI' ? 'AI 설명 · 근거가 아니며 아래 숫자와 회사 원문 안에서만 만들었습니다.' : 'AI 설명이 확인 규칙을 통과하지 못해 자료만 정리했습니다.'}</small>
      {state.quotes.length > 0 && <details className="stock-learn"><summary>회사 원문 {state.quotes.length}문단 보기</summary><div>{state.quotes.map(q =>
        <blockquote className="filing-quote-quote" key={q.passageNo}><span>원문 문단 {q.passageNo} · 글자 그대로</span><p>{q.text}</p></blockquote>)}
        {receipt && <a className="ai-assist-link" href={`https://dart.fss.or.kr/dsaf001/main.do?rcpNo=${receipt}`} target="_blank" rel="noopener noreferrer">DART 원문 전체 ↗</a>}</div></details>}</>}
  </div>
}

// ---------------- 가늠하다 · 이해 확인 퀴즈 ----------------
function QuizView({ ctx }) {
  const quiz = buildQuiz(ctx.name, ctx.years, ctx.yearStats)
  const [picked, setPicked] = useState({})
  if (!quiz.length) return <Empty>문제를 낼 자료를 아직 모으는 중입니다.</Empty>
  const score = quiz.filter((q, i) => picked[i] === q.answer).length
  async function pick(i, choice) {
    if (picked[i] !== undefined) return
    setPicked(p => ({ ...p, [i]: choice }))
    if (choice === quiz[i].answer && ctx.loggedIn) { try { await learnTerm(quiz[i].term) } catch { /* 데모는 저장 안 함 */ } }
  }
  return <>
    <ol className="quiz-list">{quiz.map((q, i) => <li key={q.term}><p className="quiz-q">{q.question}</p>
      <div className="stock-range">{q.choices.map((c, k) => <button key={c} type="button" aria-pressed={picked[i] === k}
        className={picked[i] === undefined ? '' : k === q.answer ? 'quiz-right' : picked[i] === k ? 'quiz-wrong' : ''} onClick={() => pick(i, k)}>{c}</button>)}</div>
      {picked[i] !== undefined && <div className="quiz-why"><strong>{picked[i] === q.answer ? '⭕ 맞았어요!' : '❌ 아쉬워요.'} 정답: {q.choices[q.answer]}</strong><p>{q.why}</p>
        {ctx.loggedIn && <SaveToStudy note={{ companyId: ctx.companyId, companyName: ctx.name, kind: 'QUIZ', title: q.question, body: `정답: ${q.choices[q.answer]}. ${q.why}`, source: 'AIRA 퀴즈 · 공식 값' }} />}</div>}
    </li>)}</ol>
    {Object.keys(picked).length === quiz.length && <p className="stock-intro"><strong>{quiz.length}문제 중 {score}개 맞혔어요.</strong> {ctx.loggedIn ? '맞힌 용어는 「내 공부」에 쌓였어요.' : '로그인하면 맞힌 용어가 「내 공부」에 쌓여요.'}</p>}
    <Learn>퀴즈는 공부를 돕는 문제이며, 정답이 좋고 나쁨을 뜻하지 않습니다.</Learn>
  </>
}

// ---------------- 분류마다 붙는 안내: 왜 보나 · 어떻게 읽나 ----------------
function NodeGuide({ path }) {
  const guide = guideFor(path)
  if (!guide) return null
  return <aside className="stock-node-guide" aria-label="이 화면을 보는 이유">
    <div><b>왜 보나</b><p>{guide.why}</p></div>
    <div><b>어떻게 읽나</b><p>{guide.how}</p></div>
    {guide.caution && <div className="caution"><b>조심할 것</b><p>{guide.caution}</p></div>}
  </aside>
}

// ---------------- 사업보고서 주요정보 한 갈래 ----------------
const plainNumber = v => { const n = dartNumber(v); return n === null ? null : n }

export function sectionInsight(name, rows) {
  if (!rows?.length) return null
  if (name === 'audit') {
    const opinion = rows.map(r => r.adt_opinion).find(v => v && v !== '-')
    if (!opinion) return null
    const fine = /적정/.test(opinion) && !/부적정|한정|거절/.test(opinion)
    return fine ? `✅ 감사의견은 「${opinion}」이에요. 장부를 기준대로 썼다는 뜻이에요.` : `⚠️ 감사의견이 「${opinion}」이에요. 다른 숫자도 조심해서 봐야 해요.`
  }
  if (name === 'shares') {
    const row = rows.find(r => /합계/.test(r.se ?? '')) ?? rows.find(r => /보통/.test(r.se ?? '')) ?? rows[0]
    const total = plainNumber(row.istc_totqy), own = plainNumber(row.tesstk_co)
    if (!total || own === null) return null
    return `발행한 주식 ${total.toLocaleString('ko-KR')}주 중 회사가 들고 있는 자기주식은 ${(own / total * 100).toFixed(1)}%예요. 나머지가 시장에 도는 주식이에요.`
  }
  if (name === 'treasury') {
    const totals = rows.filter(r => /총계/.test(`${r.acqs_mth1 ?? ''}${r.acqs_mth2 ?? ''}${r.acqs_mth3 ?? ''}`))
    const base = totals.length ? totals : rows
    const sum = key => base.reduce((acc, r) => acc + (plainNumber(r[key]) ?? 0), 0)
    const bought = sum('change_qy_acqs'), burned = sum('change_qy_incnr'), held = sum('trmend_qy')
    if (!burned && !bought) return '이 해에는 자기주식을 새로 사거나 없앤 기록이 없어요.'
    return `이 해에 자기주식을 ${bought.toLocaleString('ko-KR')}주 샀고, ${burned ? `${burned.toLocaleString('ko-KR')}주를 소각(없앰)했어요. 소각하면 주식 수가 줄어 1주의 몫이 커져요.` : '소각한 주식은 없어요.'} 연말에 회사가 들고 있는 자기주식은 ${held.toLocaleString('ko-KR')}주예요(보통주·우선주 합계).`
  }
  if (name === 'minority') {
    const rate = rows.map(r => r.hold_stock_rate).find(v => v && v !== '-')
    return rate ? `소액주주가 전체 주식의 ${rate}를 갖고 있어요.` : null
  }
  if (name === 'bonds') {
    const sum = rows.find(r => /합계/.test(`${r.remndr_exprtn1 ?? ''}${r.remndr_exprtn2 ?? ''}`)) ?? rows[rows.length - 1]
    const total = plainNumber(sum.sm), soon = plainNumber(sum.yy1_below)
    if (!total) return null
    return `갚지 않은 회사채 중 ${((soon ?? 0) / total * 100).toFixed(0)}%가 1년 안에 만기예요.`
  }
  if (name === 'capital') return `기록된 주식 수 변동이 ${rows.length}건 있어요. 형태(유상증자·무상증자·감자·주식배당 등)를 보세요.`
  if (name === 'holderChange') return `최근 최대주주 변동이 ${rows.length}건 기록돼 있어요. 변동 원인을 함께 보세요.`
  return null
}

function SectionView({ ctx, name }) {
  const [state, reload] = useLoad(() => getStockSection(ctx.companyId, name), [ctx.companyId, name])
  const meta = SECTION_META[name]
  if (state.loading) return <p className="status">{meta.title}을(를) 사업보고서에서 불러오는 중입니다.</p>
  if (state.error || state.data?.state === 'UNAVAILABLE') return <Empty action={<button type="button" className="secondary-action" onClick={reload}>다시 시도</button>}>DART에서 지금 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.</Empty>
  const d = state.data
  const rows = visibleRows(name, d?.items)
  if (d?.state === 'NO_DATA' || !rows.length) return <><h3 className="stock-h3">{meta.title}</h3><Empty>{meta.empty} (최근 2년 사업보고서 기준)</Empty></>
  const cols = sectionColumns(name, rows)
  const insight = sectionInsight(name, rows)
  return <>
    <h3 className="stock-h3">{meta.title} <small className="muted">{d.year} 사업연도</small></h3>
    {insight && <p className="stock-insight">{insight}</p>}
    <div className="table-scroll"><table className="stock-table"><thead><tr>{cols.map(([k, label]) => <th key={k}>{label}</th>)}</tr></thead>
      <tbody>{rows.map((r, i) => <tr key={i}>{cols.map(([k]) => <td key={k}>{r[k] && r[k] !== '-' ? r[k] : '—'}</td>)}</tr>)}</tbody></table></div>
    <Source>{d.source} · {d.year} 사업연도{d.receiptNo ? ` · 접수번호 ${d.receiptNo}` : ''}</Source>
    {d.dartUrl && <div className="stock-actions"><a className="secondary-action" href={d.dartUrl} target="_blank" rel="noopener noreferrer">사업보고서 원문 (DART) ↗</a></div>}
  </>
}

// ---------------- 재무제표 원문 ----------------
function StatementsView({ ctx, view }) {
  const [state, reload] = useLoad(() => getStockStatements(ctx.companyId), [ctx.companyId])
  const [query, setQuery] = useState('')
  if (state.loading) return <p className="status">재무제표 원문을 불러오는 중입니다.</p>
  if (state.error || state.data?.state === 'UNAVAILABLE') return <Empty action={<button type="button" className="secondary-action" onClick={reload}>다시 시도</button>}>DART에서 재무제표를 지금 불러오지 못했습니다.</Empty>
  const d = state.data
  if (d?.state === 'NO_DATA') return <Empty>최근 2년 사업보고서의 재무제표가 없습니다(금융업·신규 상장 등).</Empty>
  const key = view === 'IS' && !d.groups.IS ? 'CIS' : view
  const lines = orderStatement(key, d.groups[key])
  const shown = query.trim() ? lines.filter(l => `${l.account}${l.detail ?? ''}`.includes(query.trim())) : lines
  const money = v => v === null || v === undefined ? '—' : formatWon(v)
  return <>
    <h3 className="stock-h3">{d.names?.[key] ?? key} <small className="muted">{d.year} 사업연도 · {d.fsDiv === 'CFS' ? '연결' : '별도'}</small></h3>
    {!lines.length ? <Empty>이 보고서에는 이 표가 따로 없습니다.</Empty> : <>
      <label className="stock-filter">항목 찾기 <input type="search" value={query} onChange={e => setQuery(e.target.value)} placeholder="예: 재고, 차입금, 배당" /></label>
      <div className="table-scroll"><table className="stock-table"><thead><tr><th>항목</th>{key === 'SCE' && <th>구분</th>}<th>{d.year}</th><th>{d.year - 1}</th><th>{d.year - 2}</th></tr></thead>
        <tbody>{shown.map((l, i) => <tr key={i}><td>{l.account}</td>{key === 'SCE' && <td>{l.detail ?? '—'}</td>}<td>{money(l.current)}</td><td>{money(l.previous)}</td><td>{money(l.beforePrevious)}</td></tr>)}</tbody></table></div>
      <p className="stock-hint">{shown.length}줄 · 「연결」은 자회사까지 합친 숫자, 「별도」는 이 회사 하나의 숫자예요.</p></>}
    <Source>{d.source}{d.receiptNo ? ` · 접수번호 ${d.receiptNo}` : ''}</Source>
    {d.dartUrl && <div className="stock-actions"><a className="secondary-action" href={d.dartUrl} target="_blank" rel="noopener noreferrer">사업보고서 원문 (DART) ↗</a></div>}
  </>
}

// ---------------- 회사 일정 ----------------
function todayIso() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function CalendarView({ ctx, view }) {
  if (ctx.filingState.loading) return <p className="status">공시를 불러오는 중입니다.</p>
  const fiscal = ctx.overview.data?.profile?.acc_mt ?? 12
  const { past, upcoming } = buildCalendar(ctx.filings, todayIso(), fiscal)
  if (view === 'past') return past.length ? <>
    <ul className="stock-calendar">{past.map(f => <li key={f.receiptNo}><time>{f.date}</time><span className="filing-tag">{f.label}</span>
      <button type="button" className="link-button" onClick={() => ctx.go(['relate', 'calendar', 'past', f.receiptNo])}>{f.title}</button></li>)}</ul>
    <Source>OpenDART 공시검색 · 최근 1년</Source></> : <Empty>최근 1년 동안 일정에 해당하는 공시가 없습니다.</Empty>
  return <>
    {Number(fiscal) !== 12 && <p className="stock-hint">이 회사의 결산월은 {Number(fiscal)}월이라 12월 결산 기준 법정 기한은 빼고 보여 드려요.</p>}
    {upcoming.length ? <ul className="stock-calendar">{upcoming.map((e, i) => <li key={i} className={e.kind === 'LAW' ? 'law' : 'last-year'}>
      <time>{e.date}</time><span className="filing-tag">{e.kind === 'LAW' ? '법정 기한' : '작년 참고'}</span><span>{e.label}</span></li>)}</ul>
      : <Empty>다가오는 일정을 찾지 못했습니다.</Empty>}
    <Learn terms={[['법정 제출 기한', '자본시장법에 따라 분기·반기보고서는 분기가 끝나고 45일 안에, 사업보고서는 사업연도가 끝나고 90일 안에 내야 합니다.'],
      ['잠정 실적', '감사 전에 회사가 먼저 알리는 실적입니다. 나중에 정기보고서 숫자와 조금 다를 수 있습니다.']]}>
      기한 전에 미리 내는 회사도 많아요. 새 보고서가 나오면 「보다 › 실적」 숫자가 바뀌니, 그날 다시 와서 무엇이 달라졌는지 확인해 보세요.</Learn>
    <Source>법정 기한 · OpenDART 공시검색(작년 같은 시기)</Source>
  </>
}

// ---------------- 회사 읽기 코스 ----------------
function readLocalCourse(companyId) {
  try { return new Set(JSON.parse(window.localStorage.getItem(`aira-course-${companyId}`) ?? '[]')) } catch { return new Set() }
}
function writeLocalCourse(companyId, done) {
  try { window.localStorage.setItem(`aira-course-${companyId}`, JSON.stringify([...done])) } catch { /* 저장이 막혀도 화면은 동작한다 */ }
}

function CourseView({ ctx }) {
  const [done, setDone] = useState(() => readLocalCourse(ctx.companyId))
  const [memo, setMemo] = useState({})
  const [message, setMessage] = useState('')
  useEffect(() => {
    if (!ctx.loggedIn) return
    getStudy(ctx.companyId).then(data => setDone(current => new Set([...current, ...courseDone(data.notes, ctx.companyId)]))).catch(() => {})
  }, [ctx.companyId, ctx.loggedIn])
  async function finish(index) {
    const step = COURSE[index]
    const next = new Set([...done, index])
    setDone(next)
    writeLocalCourse(ctx.companyId, next)
    if (!ctx.loggedIn) { setMessage('로그인하면 적은 내용이 「내 공부」에 쌓여요. 지금은 이 기기에만 표시해 둘게요.'); return }
    try {
      await saveStudyNote({ companyId: ctx.companyId, companyName: ctx.name, kind: 'COURSE', title: `${coursePrefix(index)}${step.title}`,
        body: memo[index]?.trim() || `${step.question} → ${step.look}을(를) 확인했어요.`, source: 'AIRA 회사 읽기 코스' })
      setMessage(`${index + 1}단계를 「내 공부」에 쌓았어요 📚`)
    } catch (error) {
      setMessage(error.status === 423 ? '데모 계정은 저장할 수 없어서 이 기기에만 표시해 둘게요.' : '노트 저장에 실패했어요. 이 기기에는 표시해 둘게요.')
    }
  }
  const count = done.size
  return <>
    <div className="course-progress" role="progressbar" aria-valuemin={0} aria-valuemax={COURSE.length} aria-valuenow={count} aria-label="코스 진행">
      <span style={{ width: `${count / COURSE.length * 100}%` }} /></div>
    <p className="stock-hint">{count} / {COURSE.length}단계 · {count === COURSE.length ? `다 읽었어요 🎉 「가늠하다 › 이해 확인 퀴즈」로 확인해 보고, 다른 회사도 같은 순서로 읽어 보세요.` : '단계마다 화면을 보고, 본 것을 내 말로 한 줄 적어 보세요.'}</p>
    {message && <p className="status" role="status">{message}</p>}
    <ol className="course-steps">{COURSE.map((step, i) => <li key={step.title} className={done.has(i) ? 'done' : ''}>
      <div className="course-head"><b>{i + 1}</b><div><strong>{step.title}</strong><p>{step.question}</p><small>볼 곳: {step.look}</small></div></div>
      <div className="course-actions">
        <button type="button" className="secondary-action" onClick={() => ctx.go(step.to)}>화면 보러 가기 →</button>
        <textarea rows={2} maxLength={300} aria-label={`${i + 1}단계에서 본 것`} placeholder="내가 본 것을 한 줄로 (예: 매출은 5년째 늘었다)" value={memo[i] ?? ''} onChange={e => setMemo({ ...memo, [i]: e.target.value })} />
        <button type="button" className="study-save" onClick={() => finish(i)}>{done.has(i) ? '✔ 이해했어요 · 다시 쌓기' : '이해했어요 · 노트에 쌓기'}</button>
      </div></li>)}</ol>
  </>
}

// ---------------- 회사 공부 리포트 (AIRA 플러스 기능 · 출시 전 무료) ----------------
function ReportView({ ctx }) {
  const [notes, setNotes] = useState({ loading: ctx.loggedIn, items: [] })
  useEffect(() => {
    if (!ctx.loggedIn) { setNotes({ loading: false, items: [] }); return undefined }
    let alive = true
    getStudy(ctx.companyId).then(data => alive && setNotes({ loading: false, items: data.notes ?? [] })).catch(() => alive && setNotes({ loading: false, items: [] }))
    return () => { alive = false }
  }, [ctx.companyId, ctx.loggedIn])
  const years = ctx.years
  const latest = years[years.length - 1]
  const last = ctx.points[ctx.points.length - 1]
  const v = valuation(last, latest)
  const span = spanName(ctx.yearStats)
  const checks = checklist(years, ctx.yearStats, ctx.marketStats, ctx.filings)
  const done = new Set([...readLocalCourse(ctx.companyId), ...courseDone(notes.items, ctx.companyId)])
  const courseNotes = notes.items.filter(n => n.kind === 'COURSE').sort((a, b) => String(a.title).localeCompare(String(b.title), 'ko', { numeric: true }))
  const otherNotes = notes.items.filter(n => n.kind !== 'COURSE').slice(0, 12)
  const keyFilings = ctx.filings.filter(f => ['PERIODIC', 'EARNINGS', 'MATERIAL'].includes(f.category)).slice(0, 8)
  const today = todayIso()
  function print() {
    const done = () => { document.body.classList.remove('printing-report'); window.removeEventListener('afterprint', done) }
    document.body.classList.add('printing-report')
    window.addEventListener('afterprint', done)
    window.print()
  }
  return <>
    <div className="stock-actions"><button type="button" className="primary-action" onClick={print}>PDF로 저장 · 인쇄</button>
      <a className="secondary-action" href="/plans">AIRA 플러스 기능 · 출시 전이라 지금은 무료</a></div>
    <article className="study-report" aria-label={`${ctx.name} 회사 공부 리포트`}>
      <header><p className="eyebrow">AIRA 회사 공부 리포트 · {today}</p><h2>{ctx.name}</h2>
        <p className="muted">{ctx.overview.data?.market ?? ''}{ctx.overview.data?.shortCode ? ` · ${ctx.overview.data.shortCode}` : ''} · 공식 자료(KRX·OpenDART)와 내가 쌓은 공부를 한 장으로 묶었습니다.</p></header>
      <section><h3>1. 한눈에</h3>
        <dl className="report-facts">
          <div><dt>종가</dt><dd>{last ? `${formatPrice(last.close)} (${last.date})` : '—'}</dd></div>
          <div><dt>시가총액</dt><dd>{last ? formatWon(last.marketCap) : '—'}</dd></div>
          <div><dt>{span.change} 주가 변화</dt><dd>{ctx.yearStats ? formatPercent(ctx.yearStats.change) : '—'}</dd></div>
          <div><dt>{latest ? `${latest.year} 매출` : '매출'}</dt><dd>{latest ? formatWon(latest.values.REVENUE) : '—'}</dd></div>
          <div><dt>영업이익률</dt><dd>{latest ? formatPercent(latest.ratios.operatingMargin, { sign: false }) : '—'}</dd></div>
          <div><dt>ROE</dt><dd>{latest ? formatPercent(latest.ratios.roe, { sign: false }) : '—'}</dd></div>
          <div><dt>부채비율</dt><dd>{latest ? formatPercent(latest.ratios.debtRatio, { sign: false }) : '—'}</dd></div>
          <div><dt>PER · PBR</dt><dd>{v ? `${v.per ?? '—'}배 · ${v.pbr ?? '—'}배` : '—'}</dd></div>
        </dl></section>
      <section><h3>2. 해마다 실적</h3>
        {years.length ? <div className="table-scroll"><table className="stock-table"><thead><tr><th>사업연도</th><th>매출</th><th>영업이익</th><th>당기순이익</th><th>영업이익률</th><th>부채비율</th></tr></thead>
          <tbody>{years.slice(-10).map(y => <tr key={y.year}><td>{y.year}</td><td>{formatWon(y.values.REVENUE)}</td><td>{formatWon(y.values.OPERATING_INCOME)}</td><td>{formatWon(y.values.NET_INCOME)}</td>
            <td>{formatPercent(y.ratios.operatingMargin, { sign: false })}</td><td>{formatPercent(y.ratios.debtRatio, { sign: false })}</td></tr>)}</tbody></table></div>
          : <p className="muted">사업보고서 주요계정을 아직 받지 못했습니다.</p>}</section>
      <section><h3>3. 기본 확인</h3>
        <ul className="report-checks">{checks.map(item => <li key={item.label}><b>{item.value === true ? '예' : item.value === false ? '아니오' : '자료 없음'}</b> {item.label} <small>({item.basis})</small></li>)}</ul></section>
      <section><h3>4. 내가 쌓은 공부</h3>
        <p>회사 읽기 코스 {done.size} / {COURSE.length}단계</p>
        {notes.loading ? <p className="status">노트를 불러오는 중입니다.</p> : <>
          {courseNotes.length > 0 && <ol className="report-notes">{courseNotes.map(n => <li key={n.id}><strong>{String(n.title).replace(/^회사 읽기 코스 \d+\.\s*/, '')}</strong> — {n.body}</li>)}</ol>}
          {otherNotes.length > 0 && <ul className="report-notes">{otherNotes.map(n => <li key={n.id}><strong>{n.title}</strong><p>{n.body}</p>{n.source && <small>근거: {n.source}</small>}</li>)}</ul>}
          {!courseNotes.length && !otherNotes.length && <p className="muted">{ctx.loggedIn ? '아직 이 회사에 쌓은 노트가 없어요. 「묻다 › 회사 읽기 코스」부터 따라가 보세요.' : '로그인하면 내가 적은 코스 노트와 저장한 설명이 여기에 함께 담겨요.'}</p>}</>}
      </section>
      <section><h3>5. 최근 주요 공시</h3>
        {keyFilings.length ? <ul className="report-filings">{keyFilings.map(f => <li key={f.receiptNo}>{f.date} · {f.title} <small>(접수번호 {f.receiptNo})</small></li>)}</ul> : <p className="muted">최근 1년 주요 공시가 없습니다.</p>}</section>
      <section><h3>6. 출처</h3>
        <ul className="report-sources">
          <li>주가: 한국거래소(KRX) 일별 매매정보{last ? ` · ${last.date} 종가 기준` : ''}</li>
          {years.slice(-3).map(y => <li key={y.year}>{y.year} 재무: OpenDART 사업보고서{y.receiptNo ? ` · 접수번호 ${y.receiptNo}` : ''}</li>)}
          <li>공시: OpenDART 공시검색</li>
        </ul></section>
      <footer className="muted">이 리포트는 공부 기록입니다. AIRA는 매수·매도를 권하거나 주가를 예측하지 않습니다.</footer>
    </article>
  </>
}
