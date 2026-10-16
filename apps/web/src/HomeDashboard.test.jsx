import { describe, expect, it } from 'vitest'
import { closeDayLabel, intradayChange } from './stockMath.js'

describe('closeDayLabel', () => {
  it('shows the official close date instead of "today"', () => {
    expect(closeDayLabel('2026-10-15')).toBe('10월 15일')
    expect(closeDayLabel('2026-01-02')).toBe('1월 2일')
    expect(closeDayLabel(null)).toBeNull()
    expect(closeDayLabel('어제')).toBeNull()
  })
})

describe('intradayChange', () => {
  it('compares the opening and closing values without AI', () => {
    expect(intradayChange(5673.01, 5554.22)).toBeCloseTo(-2.094, 2)
    expect(intradayChange(100, 110)).toBeCloseTo(10, 5)
    expect(intradayChange(null, 110)).toBeNull()
    expect(intradayChange(0, 110)).toBeNull()
  })
})
