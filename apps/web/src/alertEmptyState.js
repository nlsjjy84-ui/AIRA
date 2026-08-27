export function alertEmptyMessage(interests, alerts) {
  if (alerts.length > 0) return null
  if (interests.length === 0) return '아직 관심 회사가 없습니다.'
  if (!interests.some(interest => interest.alertEnabled)) {
    return '아직 알림을 켠 관심 회사가 없습니다.'
  }
  return '알림을 켠 이후 새로 전달된 변화가 없습니다.'
}
