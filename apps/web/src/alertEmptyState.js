export function alertEmptyMessage(interests, alerts, emptyReason = null) {
  if (alerts.length > 0) return null
  if (emptyReason === 'NO_INTERESTS' || (!emptyReason && interests.length === 0)) {
    return '아직 관심 회사가 없습니다.'
  }
  if (emptyReason === 'NO_ALERT_ENABLED_INTERESTS'
      || (!emptyReason && !interests.some(interest => interest.alertEnabled))) {
    return '아직 알림을 켠 관심 회사가 없습니다.'
  }
  if (emptyReason === 'NO_SENT_ALERTS') return '아직 전달된 알림이 없습니다.'
  return '알림을 켠 이후 새로 전달된 변화가 없습니다.'
}
