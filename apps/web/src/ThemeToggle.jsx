import { useState } from 'react'

const KEY = 'aira-theme'

function currentTheme() {
  return document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark'
}

// 화면 모드(다크/라이트) 전환. 선택은 이 브라우저에만 저장하고, 저장이 막혀 있어도 동작한다.
export default function ThemeToggle() {
  const [theme, setTheme] = useState(currentTheme)
  const light = theme === 'light'
  function toggle() {
    const next = light ? 'dark' : 'light'
    document.documentElement.setAttribute('data-theme', next)
    try { window.localStorage.setItem(KEY, next) } catch { /* 저장 불가 시 이번 방문에만 적용 */ }
    setTheme(next)
  }
  return (
    <button type="button" className="theme-toggle" onClick={toggle} aria-pressed={light}
      aria-label={light ? '다크 모드로 바꾸기' : '라이트 모드로 바꾸기'} title={light ? '다크 모드' : '라이트 모드'}>
      {light
        ? <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M20 14.5A8 8 0 019.5 4 8 8 0 1020 14.5z" /></svg>
        : <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></svg>}
    </button>
  )
}
