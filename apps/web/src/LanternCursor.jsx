import { useEffect, useRef } from 'react'

const CLICKABLE = 'a, button, [role="button"], summary, label, select, input[type="checkbox"], input[type="radio"]'

/**
 * 등불 커서. 기본 화살표는 그대로 두고(그래프를 정확히 짚어야 하니까), 그 뒤를 작은 불빛이 살짝 늦게 따라온다.
 * 누를 수 있는 곳 위에서는 불빛이 동그랗게 커지고, 길 안내의 목적지 위에서는 풍이가 "여기예요!" 하고 나타난다. 터치 기기·움직임 줄이기 설정에서는 그리지 않는다.
 */
export default function LanternCursor() {
  const lightRef = useRef(null)
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined
    if (!window.matchMedia('(pointer: fine)').matches || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return undefined
    const light = lightRef.current
    if (!light) return undefined
    let x = -100, y = -100, tx = -100, ty = -100, frame = 0, shown = false
    const loop = () => {
      x += (tx - x) * 0.2
      y += (ty - y) * 0.2
      light.style.transform = `translate3d(${x}px, ${y}px, 0)`
      frame = Math.abs(tx - x) + Math.abs(ty - y) > 0.3 ? requestAnimationFrame(loop) : 0
    }
    const onMove = event => {
      tx = event.clientX; ty = event.clientY
      if (!shown) { shown = true; x = tx; y = ty; light.classList.add('is-on') }
      light.classList.toggle('is-over', Boolean(event.target?.closest?.(CLICKABLE)))
      light.classList.toggle('is-guide', Boolean(event.target?.closest?.('[data-guide-target]')))
      if (!frame) frame = requestAnimationFrame(loop)
    }
    const onLeave = () => { shown = false; light.classList.remove('is-on') }
    const onDown = () => light.classList.add('is-press')
    const onUp = () => light.classList.remove('is-press')
    window.addEventListener('pointermove', onMove, { passive: true })
    document.documentElement.addEventListener('pointerleave', onLeave)
    window.addEventListener('pointerdown', onDown)
    window.addEventListener('pointerup', onUp)
    return () => {
      cancelAnimationFrame(frame)
      window.removeEventListener('pointermove', onMove)
      document.documentElement.removeEventListener('pointerleave', onLeave)
      window.removeEventListener('pointerdown', onDown)
      window.removeEventListener('pointerup', onUp)
    }
  }, [])
  return <span ref={lightRef} className="lantern-cursor" aria-hidden="true"><i /><b><img src="/guide/pung.webp" alt="" width="22" height="22" />여기예요!</b></span>
}
