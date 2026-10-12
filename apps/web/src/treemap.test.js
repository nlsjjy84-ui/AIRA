import { describe, expect, it } from 'vitest'
import { squarify } from './treemap.js'

describe('squarify', () => {
  it('fills the rectangle with areas proportional to values', () => {
    const rects = squarify([{ value: 6 }, { value: 6 }, { value: 4 }, { value: 3 }, { value: 2 }, { value: 2 }, { value: 1 }], 0, 0, 600, 400)
    expect(rects).toHaveLength(7)
    const total = rects.reduce((s, r) => s + r.w * r.h, 0)
    expect(Math.round(total)).toBe(240000)
    expect(Math.round(rects[0].w * rects[0].h)).toBe(Math.round(240000 * 6 / 24))
    for (const r of rects) { expect(r.x).toBeGreaterThanOrEqual(-0.001); expect(r.x + r.w).toBeLessThanOrEqual(600.001); expect(r.y + r.h).toBeLessThanOrEqual(400.001) }
    expect(squarify([], 0, 0, 10, 10)).toEqual([])
  })
})
