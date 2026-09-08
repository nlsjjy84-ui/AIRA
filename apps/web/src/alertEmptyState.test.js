import { describe, expect, it } from 'vitest'
import { alertEmptyMessage } from './alertEmptyState.js'

describe('alert empty state semantics', () => {
  it('distinguishes no interests', () => {
    expect(alertEmptyMessage([], [])).toBe('아직 관심 회사가 없습니다.')
  })

  it('distinguishes interests without explicit alert opt-in', () => {
    expect(alertEmptyMessage([{ alertEnabled: false }], []))
      .toBe('아직 알림을 켠 관심 회사가 없습니다.')
  })

  it('distinguishes an enabled interest with no sent alerts', () => {
    expect(alertEmptyMessage([{ alertEnabled: true }], []))
      .toBe('알림을 켠 이후 새로 전달된 변화가 없습니다.')
  })

  it.each([
    ['NO_INTERESTS', '아직 관심 회사가 없습니다.'],
    ['NO_ALERT_ENABLED_INTERESTS', '아직 알림을 켠 관심 회사가 없습니다.'],
    ['NO_ELIGIBLE_ASSESSMENTS', '알림을 켠 이후 새로 전달된 변화가 없습니다.'],
    ['NO_SENT_ALERTS', '아직 전달된 알림이 없습니다.'],
  ])('maps backend empty reason %s', (reason, message) => {
    expect(alertEmptyMessage([{ alertEnabled: true }], [], reason)).toBe(message)
  })

  it('does not show an empty message when a visible alert exists', () => {
    expect(alertEmptyMessage([{ alertEnabled: true }], [{}])).toBeNull()
  })
})
