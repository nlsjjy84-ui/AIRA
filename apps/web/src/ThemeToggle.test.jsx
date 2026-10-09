import { afterEach, describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ThemeToggle from './ThemeToggle.jsx'

afterEach(() => {
  document.documentElement.removeAttribute('data-theme')
  window.localStorage.clear()
})

describe('ThemeToggle', () => {
  it('기본은 다크이고 눌러서 라이트로 바꾸면 저장된다', async () => {
    render(<ThemeToggle />)
    await userEvent.click(screen.getByRole('button', { name: '라이트 모드로 바꾸기' }))
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
    expect(window.localStorage.getItem('aira-theme')).toBe('light')
    expect(screen.getByRole('button', { name: '다크 모드로 바꾸기' })).toHaveAttribute('aria-pressed', 'true')
  })
})
