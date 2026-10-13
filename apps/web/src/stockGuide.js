// 분류마다 "왜 보나 · 어떻게 읽나 · 조심할 것"을 적어 둔다. 숫자를 보여 주는 데서 끝나지 않고,
// 이 칸이 무엇을 알려 주는지 이해하게 하는 것이 목적이다. 키는 화면 경로(보다/주가/가격 흐름 → inspect/price/trend).

export const GUIDE = {
  // ---------- 묻다 ----------
  'ask/ai': { why: '화면이 많아도 내가 궁금한 건 한 문장일 때가 많아요. 그 한 문장을 이 회사의 공식 숫자로만 답해 줘요.', how: '답 아래 붙은 기간·출처를 꼭 같이 보세요. 숫자가 어디서 왔는지 알면 그 답을 믿을지 스스로 정할 수 있어요.', caution: 'AI는 자료에 없는 것은 모른다고 답해요. 앞날이나 사고팔 때를 묻는 질문에는 답하지 않아요.' },
  'ask/course': { why: '분류가 많으면 어디서부터 봐야 할지 막막해요. 회사를 읽는 기본 순서를 따라가면 처음 보는 회사도 같은 틀로 이해할 수 있어요.', how: '단계마다 해당 화면으로 가서 보고, 본 것을 한 줄로 적어 두세요. 적은 줄이 「내 공부」에 쌓여요.', caution: '순서는 많이 쓰는 기본 틀일 뿐 정답은 아니에요.' },
  'ask/faq': { why: '처음 오신 분들이 가장 많이 묻는 질문을 모았어요. 질문에서 출발하면 무엇을 봐야 할지 덜 헤매요.', how: '질문을 누르면 답이 있는 화면으로 바로 가요.' },
  'ask/find': { why: '메뉴 이름을 몰라도 낱말로 원하는 화면을 찾을 수 있어요.', how: '「빚」「배당」「직원」처럼 짧은 낱말이 잘 맞아요.' },
  'ask/claim': { why: '기사·커뮤니티의 숫자가 틀리거나 기간이 섞여 있는 경우가 꽤 있어요. 공식 값과 맞춰 보는 습관이 가장 확실한 공부예요.', how: '숫자가 들어간 문장을 그대로 붙여 넣으세요. 일치·다름과 함께 어느 보고서의 값과 비교했는지 접수번호로 보여 줘요.', caution: '「좋아질 것」 같은 전망은 사실 확인 대상이 아니에요.' },
  'ask/quote': { why: '「왜 그랬는지」는 숫자에 없고 회사가 쓴 글에 있어요. 사업보고서에서 그 문장을 찾아 줘요.', how: 'AI가 요약하지 않고 원문 문장을 글자 그대로 보여 줘요. 문장 옆 접수번호로 원문을 열어 앞뒤 맥락을 확인하세요.' },
  'ask/glossary': { why: '용어를 모르면 숫자를 봐도 뜻이 안 들어와요. 이 회사 화면에 나오는 용어를 한곳에 모았어요.', how: '읽고 이해했다면 「이해했어요」를 눌러 두세요. 내가 아는 용어가 「내 공부」에 쌓여요.' },

  // ---------- 보다 ----------
  'inspect/summary': { why: '자세히 들어가기 전에 회사의 전체 모양부터 보는 화면이에요.', how: '칸마다 숫자 하나와 기간이 있어요. 궁금한 칸을 누르면 그 자료로 깊게 들어가요.' },
  'inspect/company/basic': { why: '어떤 회사인지, 1년 장부를 언제 마감하는지 알아야 다른 숫자를 바르게 읽을 수 있어요.', how: '결산월이 12월이 아니면 「2025년 실적」이 가리키는 기간이 달라져요. 업종 코드는 「잇다 › 업종 안에서」 비교의 기준이 돼요.' },
  'inspect/company/listing': { why: '주가는 1주의 값이고, 회사 전체의 값은 주식 수를 곱해야 나와요.', how: '주가만 보고 비싸다·싸다를 말할 수 없어요. 주식 수가 다르면 같은 주가라도 회사 크기가 완전히 달라요. 그래서 시가총액으로 비교해요.' },
  'inspect/company/audit': { why: '회계법인이 이 회사 장부를 검사하고 믿을 만한지 의견을 낸 결과예요. 다른 모든 숫자의 바탕이에요.', how: '「적정」이면 장부를 기준대로 썼다는 뜻이에요. 한정·부적정·의견거절이면 다른 숫자 전체를 조심해서 봐야 해요. 「핵심감사사항」은 감사인이 특히 꼼꼼히 들여다본 부분이에요.', caution: '적정 의견이 회사가 좋다는 뜻은 아니에요. 장부가 기준대로 쓰였다는 뜻이에요.' },
  'inspect/company/execs': { why: '누가 회사를 이끄는지, 이사회가 어떻게 짜였는지 보는 칸이에요.', how: '등기임원은 이사회 구성원이에요. 사외이사는 회사 밖 사람이라 경영진을 견제하는 역할을 해요.' },
  'inspect/company/pay': { why: '경영진이 얼마를 받는지, 회사 실적과 같이 움직이는지 볼 수 있어요.', how: '「이사·감사 전체」는 1인 평균을, 「5억 이상」은 개인별 금액을 보여 줘요. 「주주·배당·직원 › 직원」의 평균 급여와 나란히 보면 차이가 보여요.' },
  'inspect/company/affiliates': { why: '큰 회사의 실적은 자회사·투자한 회사에서 나오는 경우가 많아요. 연결재무제표가 왜 필요한지 여기서 이해돼요.', how: '지분율이 50%를 넘으면 대개 연결 대상(자회사)이고, 20~50%면 관계기업이에요. 장부가액은 지금 장부에 올라 있는 투자 금액이에요.' },
  'inspect/company/shares': { why: '주당 이익·주당 배당 같은 「주당」 숫자는 모두 주식 수로 나눈 값이에요. 그 주식 수가 여기 있어요.', how: '발행한 주식에서 회사가 들고 있는 자기주식을 빼면 실제로 시장에 도는 주식(유통주식)이에요. 「발행할 주식 총수」는 정관에 정한 한도예요.' },
  'inspect/price/trend': { why: '그동안 시장이 이 회사를 어떻게 평가해 왔는지 보는 그래프예요.', how: '이동평균선은 며칠 평균이라 흐름을 부드럽게 보여 줘요. 크게 움직인 날을 누르면 그날 공시가 함께 나와요.', caution: '지난 흐름은 앞으로를 알려 주지 않아요.' },
  'inspect/price/candle': { why: '하루 안에서 얼마나 출렁였는지까지 보여 줘요.', how: '몸통은 시작가와 끝난 가격, 위아래 선은 그날 최고·최저예요. 빨강은 오른 날, 파랑은 내린 날이에요.' },
  'inspect/price/daily': { why: '그래프 뒤의 숫자 그대로예요. 특정한 날을 정확히 확인할 때 써요.', how: '날짜를 누르면 그날 시세와 그날 접수된 공시를 함께 볼 수 있어요.' },
  'inspect/price/volume': { why: '거래가 갑자기 많아진 날은 사람들 관심이 몰린 날이에요.', how: '거래량이 튄 날의 공시·뉴스를 같이 보세요. 「왜 그날?」의 실마리가 대개 거기 있어요.' },
  'inspect/price/cap': { why: '시가총액은 시장이 매긴 회사 전체의 크기예요. 회사끼리 크기를 비교할 때 주가 대신 써요.', how: '주가 × 상장주식수예요. 주식 수가 바뀌면 주가가 그대로여도 시가총액이 달라져요.' },
  'inspect/price/range': { why: '저장된 기간(최대 1년) 안에서 지금 가격이 어디쯤인지 보여 줘요.', how: '0%면 그 기간 최저, 100%면 그 기간 최고 근처예요.', caution: '위치가 낮다고 싸다는 뜻은 아니에요.' },
  'inspect/earnings/REVENUE': { why: '매출은 얼마나 팔았는지예요. 모든 이익의 출발점이라 가장 먼저 봐요.', how: '해마다 늘었는지, 줄었다면 한 해만인지 몇 해 이어졌는지를 보세요. 이유는 「왜 그럴까」에서 회사가 쓴 글로 확인해요.' },
  'inspect/earnings/OPERATING_INCOME': { why: '영업이익은 본업으로 남긴 돈이에요. 회사가 하는 일 자체가 돈이 되는지를 보여 줘요.', how: '매출이 늘었는데 영업이익이 줄었다면 비용이 더 빨리 늘었다는 뜻이에요. 영업이익률(이익 ÷ 매출)과 같이 보세요.' },
  'inspect/earnings/NET_INCOME': { why: '당기순이익은 세금·이자·일회성 손익까지 빼고 최종적으로 남은 돈이에요.', how: '영업이익과 차이가 크면 본업 밖에서 생긴 손익이 있다는 뜻이에요. 그 해 공시에서 이유를 찾아보세요.', caution: '자산을 판 이익처럼 한 번뿐인 숫자가 섞일 수 있어요.' },
  'inspect/earnings/quarters': { why: '1년 숫자만 보면 최근 흐름을 놓쳐요. 석 달 단위로 쪼개 보면 계절 차이와 요즘 방향이 보여요.', how: '같은 분기끼리(올해 2분기 vs 작년 2분기) 비교하세요. 업종마다 잘 팔리는 계절이 달라요.' },
  'inspect/earnings/all': { why: '손익과 재무 숫자를 한 표에 모아 해마다 나란히 봐요.', how: '연도를 누르면 그해 사업보고서의 모든 항목과 비율이 나와요.' },
  'inspect/balance/structure': { why: '회사가 가진 것(자산)이 어떤 돈으로 마련됐는지 보여 줘요. 남의 돈(부채)인지 내 돈(자본)인지요.', how: '자산 = 부채 + 자본이에요. 부채비율(부채 ÷ 자본)이 높을수록 빚에 기대는 정도가 커요.', caution: '업종마다 보통인 부채비율이 달라요(은행·항공은 원래 높아요).' },
  'inspect/balance/liquidity': { why: '1년 안에 갚아야 할 빚을 1년 안에 현금이 될 자산으로 감당할 수 있는지 봐요.', how: '유동비율(유동자산 ÷ 유동부채)이 100%보다 크면 당장 갚을 빚보다 곧 쓸 수 있는 자산이 많다는 뜻이에요.' },
  'inspect/balance/cash': { why: '장부상 이익과 실제로 들어온 현금은 달라요. 이익이 현금으로 들어오는지가 중요해요.', how: '영업활동 현금흐름이 순이익보다 꾸준히 작으면 외상으로 판 돈이 쌓이고 있는지 살펴보세요. 잉여현금흐름은 설비 투자 뒤에 남는 돈이에요.' },
  'inspect/balance/bonds': { why: '회사채는 시장에서 빌린 돈이에요. 만기가 언제 몰려 있는지 알면 돈 갚을 부담이 언제 큰지 보여요.', how: '1년 이하 칸이 크면 곧 갚거나 다시 빌려야 할 돈이 많다는 뜻이에요. 「유동자산·유동부채」와 같이 보세요.' },
  'inspect/ratios/수익성': { why: '같은 매출이라도 얼마나 남기느냐가 회사마다 달라요.', how: '영업이익률·ROE는 해마다 변화를 보고, 같은 업종 회사와 비교해야 뜻이 생겨요.' },
  'inspect/ratios/안정성': { why: '어려운 때가 와도 버틸 수 있는지를 봐요.', how: '부채비율은 낮을수록, 유동비율은 높을수록 여유가 있다는 뜻이에요. 다만 업종 평균과 함께 보세요.' },
  'inspect/ratios/활동성': { why: '가진 자산을 얼마나 바쁘게 돌려 매출을 만드는지 봐요.', how: '자산회전율 1회는 자산만큼 1년에 팔았다는 뜻이에요. 공장이 큰 업종은 원래 낮아요.' },
  'inspect/ratios/성장성': { why: '회사가 커지고 있는지 작아지고 있는지 봐요.', how: '한 해만 보지 말고 몇 해 이어지는지 보세요. 기저 효과(작년이 유난히 나빴던 경우)도 조심하세요.' },
  'inspect/ratios/시장 평가': { why: '시장이 이 회사의 이익·자산을 몇 배로 쳐주는지 보여 줘요.', how: 'PER은 주가가 1년 이익의 몇 배인지, PBR은 장부상 자본의 몇 배인지예요. 같은 업종끼리 비교해야 뜻이 있어요.', caution: '배수가 낮다고 싸다, 높다고 비싸다는 뜻은 아니에요. 시장이 그렇게 보는 이유가 있을 수 있어요.' },
  'inspect/owners/dividend': { why: '배당은 회사가 번 돈을 주주에게 나눠 주는 방식이에요.', how: '주당 배당금이 해마다 어떻게 변했는지, 이익 중 얼마를 배당했는지(배당성향)를 보세요.' },
  'inspect/owners/holders': { why: '누가 이 회사를 가장 많이 갖고 있는지 보여 줘요. 큰 결정은 대개 최대주주 뜻을 따라가요.', how: '최대주주와 특수관계인(가족·계열사)을 합친 지분이 실제 영향력이에요.' },
  'inspect/owners/holderChange': { why: '최대주주가 바뀌었거나 지분이 크게 움직였다면 회사의 방향이 달라질 수 있어요.', how: '변동일과 변동 원인을 함께 보세요. 상속·증여·장내매수처럼 이유가 적혀 있어요.' },
  'inspect/owners/insiders': { why: '회사 사정을 가장 잘 아는 임원·주요주주가 주식을 사고판 기록이에요.', how: '한 사람의 한 번 거래보다 여러 사람이 같은 방향으로 움직였는지를 보세요.', caution: '세금·대출 상환처럼 개인 사정으로 파는 경우도 많아요.' },
  'inspect/owners/major': { why: '5% 넘게 가진 투자자가 생기거나 지분을 바꾸면 알려야 해요.', how: '보유 목적(단순 투자·경영 참여)을 같이 보세요.' },
  'inspect/owners/treasury': { why: '회사가 자기 주식을 사들이면 시장에 도는 주식이 줄어요. 소각까지 하면 주식 수 자체가 줄어 주당 숫자가 커져요.', how: '취득·처분·소각 칸을 구분해서 보세요. 사기만 하고 들고 있는 것과 없애는 것(소각)은 효과가 달라요.' },
  'inspect/owners/capital': { why: '증자는 주식을 새로 찍어 돈을 모으는 것이고, 감자는 주식 수를 줄이는 거예요. 내 주식의 몫이 달라져요.', how: '유상증자가 잦으면 회사가 돈이 필요했다는 신호일 수 있어요. 발행 형태와 가격을 같이 보세요.' },
  'inspect/owners/minority': { why: '소액주주가 얼마나 많이 갖고 있는지 보면 주식이 얼마나 넓게 퍼져 있는지 알 수 있어요.', how: '소액주주 지분이 크면 시장에서 거래되는 물량이 많다는 뜻이에요.' },
  'inspect/owners/employees': { why: '사람은 회사의 가장 큰 자원이에요. 직원 수와 근속, 급여를 봐요.', how: '평균 근속연수가 짧고 직원 수가 크게 바뀌면 그 이유를 공시에서 찾아보세요.' },
  'inspect/statements': { why: '요약 숫자 뒤에 있는 재무제표 원문 전체예요. 궁금한 항목이 요약에 없으면 여기서 찾아요.', how: '당기·전기·전전기 세 해가 나란히 있어요. 들여쓰기는 하위 항목이에요.' },
  'inspect/statements/BS': { why: '연말 기준으로 회사가 가진 것과 갚을 것, 주주 몫을 한 장에 정리한 표예요.', how: '자산총계 = 부채총계 + 자본총계가 맞는지 보면 표 읽는 법이 손에 익어요.' },
  'inspect/statements/IS': { why: '1년 동안 얼마를 팔고 어떤 비용을 써서 얼마를 남겼는지의 흐름이에요.', how: '위에서 아래로 매출 → 매출원가 → 판매비와관리비 → 영업이익 → 순이익 순서로 깎여 내려가요.' },
  'inspect/statements/CF': { why: '실제 현금이 어디서 들어오고 어디로 나갔는지 보여 줘요.', how: '영업(본업)·투자(설비·지분)·재무(빌리고 갚기) 세 갈래로 나눠 읽어요.' },
  'inspect/statements/SCE': { why: '주주 몫(자본)이 1년 동안 왜 늘고 줄었는지 보여 줘요.', how: '이익이 쌓이고, 배당·자기주식으로 빠져나간 흐름이 보여요.' },
  'inspect/filings': { why: '공시는 회사가 법에 따라 직접 밝힌 공식 소식이에요. 뉴스보다 먼저, 더 정확해요.', how: '분류를 고르면 정기보고서·실적·지분·주요 사항 등으로 나눠 보여 줘요. 공시를 누르면 원문 문장과 전후 주가가 함께 나와요.' },
  'inspect/news/list': { why: '공시에 없는 업계 이야기와 시장 반응을 기사로 볼 수 있어요.', how: '기사는 언론사의 해석이 담긴 글이에요. 숫자는 「이 말, 사실이야?」에서 공식 값과 맞춰 보세요.' },
  'inspect/news/links': { why: '더 깊게 찾아볼 수 있는 공식 창구를 모았어요.', how: 'DART에서 공시 전체를, 회사 IR 페이지에서 설명 자료를 볼 수 있어요.' },

  // ---------- 잇다 ----------
  'relate/filings': { why: '주가가 움직인 날 무슨 일이 있었는지 공시 날짜와 겹쳐 보면 실마리가 생겨요.', how: '점을 누르면 그 공시로 들어가요.', caution: '같은 날이었다고 그 공시 때문이라고 단정할 수 없어요.' },
  'relate/market': { why: '이 회사만 오른 건지, 시장 전체가 오른 건지 구분해야 회사 이야기를 제대로 읽을 수 있어요.', how: '시작일을 100으로 맞췄어요. 두 선의 간격이 벌어진 구간이 이 회사만의 이야기가 있었던 때예요.' },
  'relate/around': { why: '공시가 나온 뒤 주가가 어떻게 움직였는지 사례로 모아 봐요.', how: '전날 종가와 5거래일 뒤 종가를 비교해요. 비슷한 공시에서 반응이 어땠는지 보는 공부용이에요.', caution: '과거 반응이 다음에도 같다는 보장은 없어요.' },
  'relate/industry': { why: '숫자 하나만으로는 높은지 낮은지 알 수 없어요. 같은 업종 회사 옆에 놓아야 뜻이 생겨요.', how: '통계청 업종 코드가 같은 회사끼리 묶었어요. 표 머리를 누르면 그 기준으로 줄을 세워요.' },
  'relate/events': { why: 'AIRA가 공식 근거로 확인해 둔 이 회사의 사건을 시간순으로 모았어요.', how: '사건을 누르면 근거 문서와 연결된 자료로 가요.' },
  'relate/calendar': { why: '언제 새 숫자가 나오는지 알면 그때 무엇을 확인할지 미리 준비할 수 있어요.', how: '법정 제출 기한과, 작년 같은 시기에 이 회사가 낸 공시를 함께 보여 줘요.', caution: '「작년 같은 시기」는 참고일 뿐 올해 날짜를 약속하지 않아요.' },

  // ---------- 가늠하다 ----------
  'assess/summary': { why: '흩어진 변화를 한곳에 모아 「무엇이 늘고 무엇이 줄었나」를 정리해요.', how: '늘어난 것과 줄어든 것을 같이 보고, 그 이유를 각 화면의 「왜 그럴까」에서 확인하세요.' },
  'assess/radar': { why: '다섯 방향을 한 도형으로 보면 이 회사의 모양(강한 쪽·약한 쪽)이 한눈에 들어와요.', how: '같은 업종 안에서의 위치(백분위)예요. 50이면 업종 한가운데예요.', caution: '점수나 등급이 아니에요. 넓다고 좋은 회사라는 뜻이 아니에요.' },
  'assess/yoy': { why: '작년과 올해를 나란히 놓으면 변화의 크기가 정확히 보여요.', how: '항목을 누르면 그 항목의 10년 흐름으로 들어가요.' },
  'assess/industry': { why: '업종 중간값은 「이 업종에서 보통」의 기준선이에요.', how: '중간값보다 위인지 아래인지, 그 차이가 해마다 벌어지는지 좁아지는지 보세요.' },
  'assess/check': { why: '많은 사람이 기본으로 확인하는 질문을 자료로 답해 봐요. 빠뜨린 확인이 없는지 점검하는 용도예요.', how: '예·아니오 옆의 근거 숫자를 눌러 직접 확인하세요.' },
  'assess/quiz': { why: '봤다고 아는 건 아니에요. 실제 숫자로 문제를 풀어 보면 이해했는지 확인돼요.', how: '틀려도 괜찮아요. 풀이를 읽고 다시 풀면 그게 공부예요.' },
  'assess/report': { why: '흩어져 본 숫자와 내가 적은 노트를 한 장으로 묶으면, 이 회사를 내 말로 설명할 수 있는지 확인돼요.', how: '공식 숫자(출처·접수번호 포함)와 내 공부 기록이 나뉘어 있어요. 「PDF로 저장」으로 내려받아 두고, 다음 보고서가 나오면 다시 만들어 비교해 보세요.', caution: '리포트는 공부 기록이지 투자 판단이나 추천이 아니에요.' },
  'relate/peer': { why: '비슷한 회사 여럿과 나란히 놓으면 차이가 또렷하게 보여요.', how: '최대 3개 회사를 더해 4개까지 같은 기준으로 비교해요. 주가는 시작일을 100으로 맞췄고, 비율은 각 회사의 최근 사업연도예요.' },
  'assess/unknown': { why: '자료로 알 수 없는 것을 아는 것도 판단의 일부예요.', how: '여기 적힌 한계를 염두에 두고 다른 화면의 숫자를 읽으세요.' },
}

/** 경로에 맞는 안내. 가장 깊은 경로부터 찾고, 없으면 한 단계씩 올라간다. */
export function guideFor(path) {
  for (let n = Math.min(path.length, 3); n >= 2; n--) {
    const hit = GUIDE[path.slice(0, n).join('/')]
    if (hit) return hit
  }
  return null
}

// ---------- 사업보고서 주요정보 (한 갈래씩) ----------
// cols: [OpenDART 필드, 화면 이름]. 보고서마다 없는 칸은 건너뛴다.
export const SECTION_META = {
  audit: { labels: 1, title: '회계감사인과 감사의견', cols: [['bsns_year', '사업연도'], ['adtor', '감사인'], ['adt_opinion', '감사의견'], ['adt_reprt_spcmnt_matter', '감사보고서 특기사항'], ['emphs_matter', '강조사항 등'], ['core_adt_matter', '핵심감사사항']], empty: '감사의견 자료가 없습니다.' },
  execs: { labels: 2, title: '임원 현황', cols: [['nm', '이름'], ['ofcps', '직위'], ['rgist_exctv_at', '등기 여부'], ['fte_at', '상근 여부'], ['chrg_job', '담당 업무'], ['hffc_pd', '재직 기간'], ['tenure_end_on', '임기 만료']], empty: '임원 현황 자료가 없습니다.' },
  payAll: { labels: 1, title: '이사·감사 전체 보수', cols: [['se', '구분'], ['nmpr', '인원(명)'], ['mendng_totamt', '보수 총액(원)'], ['jan_avrg_mendng_am', '1인 평균 보수(원)'], ['rm', '비고']], empty: '보수 자료가 없습니다.' },
  payTop: { labels: 2, title: '보수 5억 원 이상 (개인별)', cols: [['nm', '이름'], ['ofcps', '직위'], ['mendng_totamt', '보수 총액(원)'], ['mendng_totamt_ct_incls_mendng', '총액에 포함되지 않는 보수']], empty: '5억 원 이상 받은 사람이 없거나 공개 대상이 아닙니다.' },
  affiliates: { labels: 1, title: '다른 회사 출자 현황', cols: [['inv_prm', '회사 이름'], ['invstmnt_purps', '출자 목적'], ['frst_acqs_de', '처음 취득일'], ['trmend_blce_qy', '기말 주식 수'], ['trmend_blce_qota_rt', '기말 지분율(%)'], ['trmend_blce_acntbk_amount', '기말 장부가액'], ['recent_bsns_year_fnnr_sttus_tot_assets', '그 회사 총자산'], ['recent_bsns_year_fnnr_sttus_thstrm_ntpf', '그 회사 순이익']], empty: '다른 회사 출자 자료가 없습니다.' },
  shares: { labels: 1, title: '주식의 총수', cols: [['se', '구분'], ['isu_stock_totqy', '발행할 주식 총수(한도)'], ['now_to_isu_stock_totqy', '지금까지 발행'], ['now_to_dcrs_stock_totqy', '지금까지 감소'], ['istc_totqy', '발행주식 총수'], ['tesstk_co', '자기주식 수'], ['distb_stock_co', '유통주식 수']], empty: '주식 총수 자료가 없습니다.' },
  treasury: { labels: 4, title: '자기주식 취득·처분', cols: [['acqs_mth1', '취득 방법'], ['acqs_mth2', '세부'], ['acqs_mth3', '세부2'], ['stock_knd', '주식 종류'], ['bsis_qy', '기초 수량'], ['change_qy_acqs', '취득'], ['change_qy_dsps', '처분'], ['change_qy_incnr', '소각'], ['trmend_qy', '기말 수량'], ['rm', '비고']], empty: '자기주식 자료가 없습니다(보유하지 않았거나 보고서에 없음).' },
  capital: { labels: 0, title: '증자·감자 현황', cols: [['isu_dcrs_de', '날짜'], ['isu_dcrs_stle', '형태'], ['isu_dcrs_stock_knd', '주식 종류'], ['isu_dcrs_qy', '수량'], ['isu_dcrs_mstvdv_fval_amount', '주당 액면가'], ['isu_dcrs_mstvdv_amount', '주당 발행가']], empty: '최근 증자·감자 기록이 없습니다.' },
  minority: { labels: 1, title: '소액주주 현황', cols: [['se', '구분'], ['shrholdr_co', '소액주주 수'], ['shrholdr_tot_co', '전체 주주 수'], ['shrholdr_rate', '주주 수 비율'], ['hold_stock_co', '소액주주 보유 주식'], ['stock_tot_co', '총 발행 주식'], ['hold_stock_rate', '보유 비율']], empty: '소액주주 자료가 없습니다.' },
  holderChange: { labels: 0, title: '최대주주 변동', cols: [['change_on', '변동일'], ['mxmm_shrholdr_nm', '최대주주'], ['posesn_stock_co', '소유 주식 수'], ['qota_rt', '지분율(%)'], ['change_cause', '변동 원인'], ['rm', '비고']], empty: '최근 최대주주 변동이 없습니다.' },
  bonds: { labels: 2, title: '회사채 미상환 잔액 (남은 만기별)', cols: [['remndr_exprtn1', '구분'], ['remndr_exprtn2', '발행 방법'], ['yy1_below', '1년 이하'], ['yy1_excess_yy2_below', '1~2년'], ['yy2_excess_yy3_below', '2~3년'], ['yy3_excess_yy4_below', '3~4년'], ['yy4_excess_yy5_below', '4~5년'], ['yy5_excess_yy10_below', '5~10년'], ['yy10_excess', '10년 초과'], ['sm', '합계']], empty: '갚지 않은 회사채가 없습니다.' },
}

const META_FIELDS = new Set(['rcept_no', 'corp_cls', 'corp_code', 'corp_name', 'stlm_dt'])

const blank = v => v === undefined || v === null || String(v).trim() === '' || String(v).trim() === '-'

/** 값이 하나도 없는 줄(모든 값 칸이 「-」)은 뺀다. 앞쪽 이름 칸(labels개)은 값으로 치지 않는다. */
export function visibleRows(name, rows) {
  const meta = SECTION_META[name]
  if (!meta) return rows ?? []
  const valueKeys = meta.cols.slice(meta.labels ?? 0).map(([key]) => key)
  return (rows ?? []).filter(r => valueKeys.some(key => !blank(r[key])))
}

/** 표에 보일 칸: 정해 둔 이름 중 실제 값이 있는 칸. 하나도 안 맞으면 받은 칸을 그대로 쓴다. */
export function sectionColumns(name, rows) {
  const meta = SECTION_META[name]
  const present = key => rows.some(r => r[key] !== undefined && r[key] !== null && String(r[key]).trim() !== '' && String(r[key]).trim() !== '-')
  const known = (meta?.cols ?? []).filter(([key]) => present(key))
  if (known.length) return known
  const keys = [...new Set(rows.flatMap(r => Object.keys(r)))].filter(k => !META_FIELDS.has(k) && present(k))
  return keys.map(k => [k, k])
}

// ---------- 회사 읽기 코스 ----------
export const COURSE = [
  { title: '어떤 회사인가', question: '이 회사는 무슨 업종이고, 1년 장부를 언제 마감하나요?', to: ['inspect', 'company', 'basic'], look: '업종 코드와 결산월' },
  { title: '얼마나 파나', question: '매출은 10년 동안 늘었나요, 줄었나요?', to: ['inspect', 'earnings', 'REVENUE'], look: '매출 그래프의 방향과 꺾인 해' },
  { title: '본업으로 얼마 남기나', question: '100원어치 팔면 본업으로 몇 원이 남나요?', to: ['inspect', 'ratios', '수익성'], look: '영업이익률' },
  { title: '장부를 믿을 수 있나', question: '회계법인은 이 회사 장부에 어떤 의견을 냈나요?', to: ['inspect', 'company', 'audit'], look: '감사의견(적정인지)과 핵심감사사항' },
  { title: '빚은 감당할 만한가', question: '자본에 비해 빚은 얼마나 되고, 1년 안에 갚을 빚은 감당되나요?', to: ['inspect', 'balance', 'structure'], look: '부채비율과 유동비율' },
  { title: '이익이 현금으로 들어오나', question: '장부상 이익만큼 실제 현금이 들어왔나요?', to: ['inspect', 'balance', 'cash'], look: '영업활동 현금흐름과 순이익 비교' },
  { title: '주주에게 무엇을 돌려주나', question: '배당이나 자기주식으로 주주에게 얼마를 돌려줬나요?', to: ['inspect', 'owners', 'dividend'], look: '주당 배당금, 배당성향, 자기주식 소각' },
  { title: '누가 주인인가', question: '최대주주는 누구고 지분은 얼마인가요? 최근에 바뀌었나요?', to: ['inspect', 'owners', 'holders'], look: '최대주주와 특수관계인 지분 합계' },
  { title: '같은 업종에서 어디쯤인가', question: '같은 업종 회사들 사이에서 규모·수익성은 어디쯤인가요?', to: ['relate', 'industry', 'rank'], look: '업종 안 순위와 중간값 대비' },
  { title: '시장은 얼마로 보나', question: '시장은 이 회사의 이익과 자본을 몇 배로 쳐주나요?', to: ['inspect', 'ratios', '시장 평가'], look: 'PER·PBR과 업종 비교' },
]

export const coursePrefix = index => `회사 읽기 코스 ${index + 1}. `

/** 공부 노트 중 이 회사의 코스 노트로 끝낸 단계 번호를 찾는다. */
export function courseDone(notes, companyId) {
  const done = new Set()
  for (const note of notes ?? []) {
    if (note.kind !== 'COURSE' || (companyId && note.companyId !== companyId)) continue
    const m = /^회사 읽기 코스 (\d+)\./.exec(note.title ?? '')
    if (m) done.add(Number(m[1]) - 1)
  }
  return done
}

// ---------- 회사 일정 ----------
const EVENT_RULES = [
  [/사업보고서/, '사업보고서'], [/반기보고서/, '반기보고서'], [/분기보고서/, '분기보고서'],
  [/주주총회소집/, '주주총회 소집 공고'], [/정기주주총회결과/, '정기주주총회 결과'],
  [/배당결정|현금ㆍ현물배당/, '배당 결정'], [/잠정\)?실적|영업\(잠정\)/, '잠정 실적 발표'],
  [/기업설명회/, '기업설명회(IR)'], [/자기주식(취득|처분|소각)/, '자기주식 결정'],
]

export function eventLabel(title) {
  const clean = String(title ?? '').replace(/\s+/g, '').replace(/^\[[^\]]*\]/, '')
  return EVENT_RULES.find(([re]) => re.test(clean))?.[1] ?? null
}

const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/**
 * 지난 일정: 최근 1년 공시 중 일정에 해당하는 것.
 * 다가올 일정: ① 12월 결산 회사의 정기보고서 법정 기한 ② 작년 같은 시기에 이 회사가 낸 공시(참고).
 */
export function buildCalendar(filings, todayIso, fiscalMonth = 12) {
  const today = new Date(`${todayIso}T00:00:00`)
  const past = (filings ?? []).map(f => ({ ...f, label: eventLabel(f.title) })).filter(f => f.label)
    .sort((a, b) => String(b.date).localeCompare(String(a.date)))
  const upcoming = []
  if (Number(fiscalMonth) === 12) {
    for (const y of [today.getFullYear(), today.getFullYear() + 1]) {
      for (const [m, d, label] of [[3, 31, '사업보고서 제출 기한'], [5, 15, '1분기보고서 제출 기한'], [8, 14, '반기보고서 제출 기한'], [11, 14, '3분기보고서 제출 기한']]) {
        const date = new Date(y, m - 1, d)
        if (date >= today && (date - today) / 86400000 <= 366) upcoming.push({ date: iso(date), label, kind: 'LAW' })
      }
    }
  }
  const seen = new Set()
  // 오래된 것부터 보며 같은 종류는 가장 가까운 날 하나만 남긴다.
  for (const f of [...past].reverse()) {
    const d = new Date(`${f.date}T00:00:00`)
    const next = new Date(d.getFullYear() + 1, d.getMonth(), d.getDate())
    const days = (next - today) / 86400000
    if (days < 0 || days > 120 || seen.has(f.label)) continue
    seen.add(f.label)
    upcoming.push({ date: iso(next), label: `${f.label} (작년 ${d.getMonth() + 1}월 ${d.getDate()}일에 냄)`, kind: 'LAST_YEAR', receiptNo: f.receiptNo })
  }
  upcoming.sort((a, b) => a.date.localeCompare(b.date))
  return { past, upcoming }
}

// ---------- 무료·유료 ----------
export const PLANS = [
  { key: 'free', name: '무료', price: '0원', lines: ['모든 종목 화면 · 그래프 · 표 · 재무제표 원문', '공시 원문 문장 찾기 · 사실 확인', 'AI 쉬운 설명 · 「왜 그럴까」 하루 5번', '회사 읽기 코스 · 용어 사전 · 퀴즈', '공부 노트 30개 · 가격 알림 3개'] },
  { key: 'plus', name: 'AIRA 플러스', price: '연 99,000원 (월 8,250원꼴)', lines: ['AI 설명 · 「왜 그럴까」 · AI 조건 찾기 무제한', '공부 노트 · 가격 알림 무제한', '회사 공부 리포트(내가 쌓은 노트 + 공식 숫자를 한 문서로)', '재무 · 조건 검색 결과 내려받기', '관심 회사 새 공시 아침 요약'] },
  { key: 'class', name: '공부 반 (강사·스터디)', price: '수강생 1명당 월 3,000원', lines: ['반 만들기 · 초대 코드로 수강생 받기', '같은 회사 같이 읽기 과제 · 마감일', '수강생별 회사 읽기 코스 진도표', '수강생 코스 노트 읽기(수강생에게 미리 안내)', '반 수강생은 AIRA 플러스 기능을 함께 사용'] },
]

// ---------- 재무제표 읽는 순서 ----------
// DART의 ord는 회사마다 제멋대로라(매출액이 23번째인 경우도 있다) 손익계산서는 읽는 순서를 정해 둔다.
const INCOME_ORDER = [/^매출액$|^수익\(매출액\)$|^영업수익$|^매출$/, /매출원가|영업비용/, /매출총이익/, /판매비와\s*관리비/, /^영업이익|영업손실/,
  /기타수익/, /기타비용/, /지분법/, /금융수익/, /금융비용|금융원가/, /법인세비용차감전/, /법인세/, /당기순이익|당기순손실/,
  /지배기업|지배주주/, /비지배/, /기본주당/, /희석주당/]

export function orderStatement(kind, lines) {
  const byOrder = [...(lines ?? [])].sort((a, b) => (Number(a.order) || 0) - (Number(b.order) || 0))
  if (kind !== 'IS' && kind !== 'CIS') return byOrder
  const rank = line => { const i = INCOME_ORDER.findIndex(re => re.test(String(line.account ?? '').trim())); return i < 0 ? INCOME_ORDER.length : i }
  return byOrder.map((line, i) => ({ line, i })).sort((a, b) => rank(a.line) - rank(b.line) || a.i - b.i).map(x => x.line)
}
