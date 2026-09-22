import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import EconomicIndicatorBoard from './EconomicIndicatorBoard.jsx'

describe('EconomicIndicatorBoard states', () => {
  it('announces loading', () => {
    render(<EconomicIndicatorBoard state={{ loading: true }} />)
    expect(screen.getByRole('status')).toHaveTextContent('한국은행 공식 통계를 확인하는 중입니다.')
  })

  it('offers retry on failure', async () => {
    const retry = vi.fn()
    render(<EconomicIndicatorBoard state={{ loading: false, error: new Error('failed') }} retry={retry} />)
    expect(screen.getByRole('alert')).toHaveTextContent('실질 GDP를 불러오지 못했습니다.')
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(retry).toHaveBeenCalledOnce()
  })

  it('states when exact official data is unavailable', () => {
    render(<EconomicIndicatorBoard state={{ loading: false, data: { state: 'NO_DATA', observations: [] } }} />)
    expect(screen.getByText('아직 표시할 수 있는 한국은행 공식 실질 GDP가 없습니다.')).toBeInTheDocument()
    expect(screen.queryByText(/0.0/)).not.toBeInTheDocument()
  })
})
