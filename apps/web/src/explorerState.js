export const STEPS = ['MAIN', 'Ask', 'Inspect', 'Relate', 'Assess']
export const DATA_STATES = new Set(['AVAILABLE', 'NO_DATA', 'PARTIAL', 'STALE', 'CONFLICTING', 'BLOCKED', 'UNSUPPORTED', 'UNAVAILABLE'])

export function initialExplorerState() {
  return { step: 'MAIN', target: null, perspective: null, category: null, detail: null,
    periodStart: '', periodEnd: '', receipt: '', predicate: 'CLOSE_PRICE', eventId: '',
    comparison: null, assessmentId: null, evidenceId: null }
}

export function advance(state, step) {
  if (!STEPS.includes(step) || state.step === step) return state
  if (step !== 'MAIN' && !state.target) return state
  // A change of subject meaning requires an explicit new selection, not context propagation.
  if (step === 'Assess' && !state.eventId) return state
  return { ...state, step }
}

export function selectTarget(state, target) {
  if (!target || !['COMPANY', 'SECURITY'].includes(target.entityType)) return state
  if (state.target?.entityId === target.entityId && state.target?.entityType === target.entityType) return state
  return { ...initialExplorerState(), target, step: 'Ask' }
}

export function contextTrail(state) {
  const targetType = state.target?.entityType === 'COMPANY' ? '기업' : state.target?.entityType === 'SECURITY' ? '종목' : null
  const base = [state.step, state.target && `${state.target.canonicalName} · ${targetType}`, state.perspective]
  if (state.step === 'Inspect') {
    const detailLabel = state.detail === 'Historical Exact' ? '정확한 기간·공시' : state.detail === 'KRX Current' ? '공식 거래일 현재값' : state.detail
    return [...base, state.category, detailLabel,
      state.periodStart && state.periodEnd && `${state.periodStart} — ${state.periodEnd}`,
      state.receipt && `공시 접수번호 ${state.receipt}`, state.comparison && `비교 ${state.comparison}`].filter(Boolean)
  }
  if (state.step === 'Relate') return [...base, '사건', '관련 사건'].filter(Boolean)
  if (state.step === 'Assess') return [...base, '사건', state.eventId && '선택한 사건', '현재 분석'].filter(Boolean)
  return base.filter(Boolean)
}

export function stateCopy(state) {
  return ({ NO_DATA: '정확히 일치하는 자료가 없습니다.', PARTIAL: '일부 항목만 확인되었습니다.',
    CONFLICTING: '서로 충돌하는 자료가 있어 값을 선택할 수 없습니다.',
    BLOCKED: '근거 확인이 필요해 표시할 수 없습니다.', UNSUPPORTED: '지원하지 않는 대상 또는 조건입니다.',
    UNAVAILABLE: '공식 자료 조회를 완료할 수 없습니다.', STALE: '자료의 갱신 상태를 확인해 주세요.' })[state] ?? ''
}
