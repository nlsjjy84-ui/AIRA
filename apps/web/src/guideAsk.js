import { GLOSSARY, routeQuestion } from './stockMath.js'
import { STOCK_TREE } from './StockPage.jsx'
import { requestRoute } from './api/assistApi.js'
import { searchEntities } from './api/canonicalApi.js'

/**
 * 「풍이에게 묻기」. 말로 물으면 AI가 질문을 이해해 갈 곳을 고르고, 풍이가 한두 줄로 답한 뒤 그 화면으로 데려간다.
 * 갈 곳은 AIRA 화면 목록 안에서만 고른다(엉뚱한 곳으로 데려가지 않게). 답하는 문장은 화면 설명·용어 사전처럼
 * 이미 검증된 글에서 가져오고, 사라·팔라·오를까 같은 질문에는 판단하지 않는다고 분명히 말한다.
 */

export const SAMSUNG_ID = '24f7883e-6992-452e-a5e0-099b886a923c'

const ADVICE = ['살까', '팔까', '사도 돼', '팔아도', '사야', '팔아야', '오를까', '내릴까', '오를 것', '떨어질까', '전망', '매수', '매도', '추천해']

export const PAGES = [
  { words: ['시장 지도', '시장 전체', '코스피 지수', '코스닥 지수', '환율', '금리', 'etf', '업종별'], path: '/market', place: '시장',
    say: '시장 전체를 한 장으로 보는 곳이에요. 지수·환율·금리·ETF·업종도 여기 모여 있어요.' },
  { words: ['조건', '골라', '고르', '스크리너', '종목 찾', '회사 찾', '추천'], path: '/screen', place: '조건 검색',
    say: '조건을 말로 적으면 AI가 검색 조건으로 바꾸고, 공식 숫자로 회사를 골라요. 추천은 하지 않아요.' },
  { words: ['사실', '맞는 말', '진짜', '루머', '들은 말', '팩트', '가짜'], path: '/check', place: '사실 확인',
    say: '들은 말을 붙여 넣으면 공시 숫자와 맞는지 바로 판정해요. 접수번호로 원문도 열 수 있어요.' },
  { words: ['내 공부', '노트', '저장한', '공부한', '복습'], path: '/study', place: '내 공부',
    say: '이해한 용어와 공부 노트가 회사별로 모이는 곳이에요.' },
  { words: ['공부 반', '강사', '수업', '스터디', '진도'], path: '/classes', place: '공부 반',
    say: '강사가 같은 회사 과제를 내고, 수강생 진도를 한 화면에서 보는 곳이에요.' },
  { words: ['요금', '플러스', '결제', '유료', '무료', '가격표'], path: '/plans', place: '요금 안내',
    say: '무료와 AIRA 플러스를 견줘 볼 수 있어요. 아직 무료 한도를 걸기 전이라 지금은 다 무료예요.' },
]

/** 용어 사전에서 질문에 들어 있는 용어를 찾는다. 'PER(주가수익비율)'은 'per'로도, '주가수익비율'로도 찾는다. */
export function findTerm(question) {
  const q = question.toLowerCase().replace(/\s+/g, '')
  return GLOSSARY.find(item => {
    const [head, inner] = item.term.toLowerCase().replace(/\s+/g, '').split(/[()]/)
    return (head && head.length >= 2 && q.includes(head)) || (inner && inner.length >= 2 && q.includes(inner))
  }) ?? null
}

export function shorten(text, max = 120) {
  if (text.length <= max) return text
  const cut = text.slice(0, max)
  const end = Math.max(cut.lastIndexOf('. '), cut.lastIndexOf('다.'), cut.lastIndexOf('요.'))
  return end > 40 ? cut.slice(0, end + 2).trim() : `${cut.trim()}…`
}

/** 받침에 맞춰 '로/으로'를 붙인다(받침 없음·ㄹ받침 → 로). 괄호·기호는 건너뛰고 마지막 한글 글자로 본다. */
export function ro(word) {
  const last = [...String(word)].reverse().find(ch => ch >= '가' && ch <= '힣')
  if (!last) return `${word}로`
  const jong = (last.charCodeAt(0) - 0xAC00) % 28
  return `${word}${jong === 0 || jong === 8 ? '로' : '으로'}`
}

export function viewDescription(to) {
  const top = STOCK_TREE.find(node => node[0] === to[0])
  const second = top?.[4]?.find(node => node[0] === to[1])
  return second ? { name: second[1], text: second[2] } : null
}

function stockHref(id, to) { return `/stock/${id}?v=${to.join('.')}` }

/** 지금 보는 종목(없으면 삼성전자). */
export function currentStock(path = window.location.pathname) {
  const id = /^\/stock\/([0-9a-fA-F-]{36})$/.exec(path)?.[1]
  return id ? { id, here: true } : { id: SAMSUNG_ID, here: false }
}

/**
 * 질문에 답하고 갈 곳을 고른다. 결과: { say, href, place, method: 'AI' | 'RULE' }.
 * route(AI 길찾기)·search(회사 찾기)는 테스트에서 바꿔 끼울 수 있다.
 */
export async function answerQuestion(question, { route = requestRoute, search = searchEntities, path } = {}) {
  const text = question.trim()
  if (!text) return null
  const stock = currentStock(path)
  const company = stock.here ? '이 회사' : '삼성전자'

  if (ADVICE.some(word => text.includes(word))) {
    return { method: 'RULE', say: 'AIRA는 사라·팔라, 오를지·내릴지는 말하지 않아요. 대신 공식 숫자를 쉽게 읽도록 도와드려요. 「한눈에」부터 볼까요?',
      place: `${company} › 한눈에`, href: stockHref(stock.id, ['inspect', 'summary']) }
  }

  const term = findTerm(text)
  if (term && /뭐야|뭐예요|뭔가요|무엇|뜻|의미|이란|란\?|란 /.test(text)) {
    return { method: 'RULE', say: shorten(`${term.term}: ${term.text}`), place: `${company} › 용어 사전`, href: stockHref(stock.id, ['ask', 'glossary']) }
  }

  const page = PAGES.find(item => item.words.some(word => text.toLowerCase().includes(word)))
  if (page) return { method: 'RULE', say: page.say, place: page.place, href: page.path }

  const local = routeQuestion(text)[0] ?? null

  // 질문에 다른 회사 이름이 있으면 AI 길찾기로 회사를 알아낸다(AI는 대상·관점만 정리하고, 화면은 목록 안에서만 고른다).
  try {
    const understood = await route(text)
    const name = understood?.understood ? understood.entityQuery?.trim() : null
    if (name) {
      const found = await search(name)
      const companies = (found?.entities ?? []).filter(item => item.entityType === 'COMPANY')
      const exact = companies.find(item => item.canonicalName === name) ?? (companies.length === 1 ? companies[0] : null)
      if (exact) {
        const to = local?.to ?? (understood.perspective === 'RELATE' ? ['relate', 'events', 'timeline'] : ['inspect', 'summary'])
        const view = viewDescription(to)
        return { method: 'AI', say: shorten(`${exact.canonicalName}의 ${ro(`「${view?.name ?? '한눈에'}」`)} 모실게요. ${view?.text ?? ''}`.trim()),
          place: `${exact.canonicalName} › ${view?.name ?? '한눈에'}`, href: stockHref(exact.entityId, to) }
      }
    }
  } catch { /* AI 길찾기를 못 쓰면 낱말 맞추기로 이어 간다. */ }

  if (local) {
    const view = viewDescription(local.to)
    return { method: 'RULE', say: shorten(`그 내용은 ${company}의 「${view?.name ?? local.label}」에서 볼 수 있어요. ${view?.text ?? ''}`.trim()),
      place: `${company} › ${view?.name ?? local.label}`, href: stockHref(stock.id, local.to) }
  }

  return { method: 'RULE', say: '그건 제가 아직 길을 몰라요. 「질문으로 찾기」에서 낱말로 같이 찾아봐요.',
    place: `${company} › 질문으로 찾기`, href: stockHref(stock.id, ['ask', 'find']) }
}
