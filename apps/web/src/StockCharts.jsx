import { useEffect, useRef, useState } from 'react'

/** 차트 폭을 실제 화면 폭에 맞춘다(글씨가 작아지지 않도록). 측정할 수 없으면 기본 폭을 쓴다. */
function useWidth(fallback = 640) {
  const ref = useRef(null)
  const [width, setWidth] = useState(fallback)
  useEffect(() => {
    const node = ref.current
    if (!node || typeof ResizeObserver === 'undefined') return undefined
    const observer = new ResizeObserver(entries => {
      const next = Math.round(entries[0]?.contentRect?.width ?? 0)
      if (next > 0) setWidth(next)
    })
    observer.observe(node)
    return () => observer.disconnect()
  }, [])
  return [ref, width]
}

function niceTicks(min, max, count = 4) {
  if (!Number.isFinite(min) || !Number.isFinite(max)) return []
  if (min === max) { min -= 1; max += 1 }
  const raw = (max - min) / count
  const power = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map(m => m * power).find(s => s >= raw) ?? raw
  const start = Math.floor(min / step) * step
  const ticks = []
  for (let v = start; ticks.length < 12; v += step) {
    ticks.push(Math.round(v / step) * step)
    if (v >= max - step * 0.001) break
  }
  return ticks
}

/**
 * 선 그래프. series: [{key, label, color, values:[number|null]}], dates: ['2026-01-02', ...]
 * markers: [{date, label, color}] — 날짜 위에 점과 세로선을 그린다(잇다: 공시 날짜).
 */
export function LineChart({ dates = [], series = [], height = 240, format = v => v, markers = [], ariaLabel, baseline = null, onMarker }) {
  const [ref, width] = useWidth()
  const [hover, setHover] = useState(null)
  const pad = { top: 16, right: 16, bottom: 28, left: 64 }
  const innerW = Math.max(40, width - pad.left - pad.right)
  const innerH = height - pad.top - pad.bottom
  const all = series.flatMap(s => s.values).filter(v => v !== null && v !== undefined && Number.isFinite(v))
  if (dates.length < 2 || all.length < 2) return <p className="chart-empty">그래프로 그릴 자료가 아직 부족합니다(2일 이상 필요).</p>
  let min = Math.min(...all)
  let max = Math.max(...all)
  if (baseline !== null) { min = Math.min(min, baseline); max = Math.max(max, baseline) }
  const ticks = niceTicks(min, max)
  const lo = Math.min(min, ticks[0] ?? min)
  const hi = Math.max(max, ticks[ticks.length - 1] ?? max)
  const x = i => pad.left + (dates.length === 1 ? innerW / 2 : (i / (dates.length - 1)) * innerW)
  const y = v => pad.top + innerH - ((v - lo) / (hi - lo || 1)) * innerH
  const path = values => values.map((v, i) => (v === null || v === undefined ? null : `${x(i).toFixed(1)},${y(v).toFixed(1)}`))
    .reduce((acc, point) => { if (!point) { acc.push([]); return acc } acc[acc.length - 1].push(point); return acc }, [[]])
    .filter(part => part.length > 1).map(part => `M${part.join('L')}`).join(' ')
  const dateIndex = new Map(dates.map((d, i) => [d, i]))
  const labelEvery = Math.max(1, Math.ceil(dates.length / Math.max(2, Math.floor(innerW / 90))))
  function move(event) {
    const box = event.currentTarget.getBoundingClientRect()
    const px = event.clientX - box.left
    const i = Math.round(((px - pad.left) / innerW) * (dates.length - 1))
    setHover(Math.max(0, Math.min(dates.length - 1, i)))
  }
  const tip = hover !== null ? { left: Math.min(Math.max(x(hover) + 10, 8), width - 190) } : null
  return <div className="chart-wrap" ref={ref}>
    <svg width={width} height={height} role="img" aria-label={ariaLabel} onPointerMove={move} onPointerLeave={() => setHover(null)}
      onClick={() => { const hit = hover !== null && markers.find(m => m.date === dates[hover]); if (hit && onMarker) onMarker(hit) }}
      style={onMarker && hover !== null && markers.some(m => m.date === dates[hover]) ? { cursor: 'pointer' } : undefined}>
      {ticks.map(t => <g key={t}><line x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} className="chart-grid" />
        <text x={pad.left - 8} y={y(t) + 4} textAnchor="end" className="chart-axis">{format(t)}</text></g>)}
      {baseline !== null && <line x1={pad.left} x2={width - pad.right} y1={y(baseline)} y2={y(baseline)} className="chart-baseline" />}
      {dates.map((d, i) => i % labelEvery === 0 && <text key={d} x={x(i)} y={height - 8} textAnchor="middle" className="chart-axis">{d.slice(2).replaceAll('-', '.')}</text>)}
      {markers.map((m, i) => dateIndex.has(m.date) && <g key={`${m.date}-${i}`}>
        <line x1={x(dateIndex.get(m.date))} x2={x(dateIndex.get(m.date))} y1={pad.top} y2={pad.top + innerH} className="chart-marker-line" style={{ stroke: m.color }} />
        <circle cx={x(dateIndex.get(m.date))} cy={pad.top + 4} r="4" style={{ fill: m.color }}><title>{`${m.date} ${m.label}`}</title></circle></g>)}
      {series.map(s => <path key={s.key} d={path(s.values)} className="chart-line" style={{ stroke: s.color }} />)}
      {hover !== null && <line x1={x(hover)} x2={x(hover)} y1={pad.top} y2={pad.top + innerH} className="chart-cursor" />}
      {hover !== null && series.map(s => s.values[hover] !== null && s.values[hover] !== undefined &&
        <circle key={s.key} cx={x(hover)} cy={y(s.values[hover])} r="4" style={{ fill: s.color }} />)}
    </svg>
    {hover !== null && <div className="chart-tip" style={tip}><strong>{dates[hover]}</strong>
      {series.map(s => <span key={s.key}><i style={{ background: s.color }} />{s.label} {s.values[hover] === null || s.values[hover] === undefined ? '—' : format(s.values[hover])}</span>)}
      {markers.filter(m => m.date === dates[hover]).map((m, i) => <span key={i} className="chart-tip-marker"><i style={{ background: m.color }} />{m.label}</span>)}
      {onMarker && markers.some(m => m.date === dates[hover]) && <small className="chart-tip-hint">누르면 공시 상세로 →</small>}
    </div>}
    {series.length > 1 && <div className="chart-legend">{series.map(s => <span key={s.key}><i style={{ background: s.color }} />{s.label}</span>)}</div>}
  </div>
}

/** 막대 그래프. categories: ['2021', ...], series: [{key,label,color,values}], stacked=true면 쌓는다. 음수도 그린다. */
export function BarChart({ categories = [], series = [], height = 240, format = v => v, stacked = false, ariaLabel }) {
  const [ref, width] = useWidth()
  const [hover, setHover] = useState(null)
  const pad = { top: 16, right: 12, bottom: 28, left: 72 }
  const innerW = Math.max(40, width - pad.left - pad.right)
  const innerH = height - pad.top - pad.bottom
  const totals = categories.map((_, i) => stacked
    ? [series.reduce((a, s) => a + Math.min(0, s.values[i] ?? 0), 0), series.reduce((a, s) => a + Math.max(0, s.values[i] ?? 0), 0)]
    : [Math.min(0, ...series.map(s => s.values[i] ?? 0)), Math.max(0, ...series.map(s => s.values[i] ?? 0))])
  const hasData = series.some(s => s.values.some(v => v !== null && v !== undefined))
  if (!categories.length || !hasData) return <p className="chart-empty">그래프로 그릴 자료가 아직 없습니다.</p>
  const ticks = niceTicks(Math.min(0, ...totals.map(t => t[0])), Math.max(0, ...totals.map(t => t[1])))
  const lo = Math.min(0, ticks[0])
  const hi = Math.max(0, ticks[ticks.length - 1])
  const y = v => pad.top + innerH - ((v - lo) / (hi - lo || 1)) * innerH
  const band = innerW / categories.length
  const groupW = band * 0.7
  const barW = stacked ? groupW : groupW / Math.max(1, series.length)
  return <div className="chart-wrap" ref={ref}>
    <svg width={width} height={height} role="img" aria-label={ariaLabel} onPointerLeave={() => setHover(null)}>
      {ticks.map(t => <g key={t}><line x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} className={t === 0 ? 'chart-baseline' : 'chart-grid'} />
        <text x={pad.left - 8} y={y(t) + 4} textAnchor="end" className="chart-axis">{format(t)}</text></g>)}
      {categories.map((c, i) => {
        const left = pad.left + band * i + (band - groupW) / 2
        let up = 0
        let down = 0
        return <g key={c} onPointerEnter={() => setHover(i)}>
          <rect x={pad.left + band * i} y={pad.top} width={band} height={innerH} className="chart-hit" />
          {series.map((s, k) => {
            const v = s.values[i]
            if (v === null || v === undefined) return null
            let top
            let bottom
            if (stacked) {
              if (v >= 0) { bottom = up; top = up + v; up = top } else { top = down; bottom = down + v; down = bottom }
            } else { top = Math.max(0, v); bottom = Math.min(0, v) }
            return <rect key={s.key} x={stacked ? left : left + barW * k} width={Math.max(2, barW - 3)} y={y(top)}
              height={Math.max(1, y(bottom) - y(top))} rx="3" style={{ fill: s.color }} className={hover === i ? 'chart-bar active' : 'chart-bar'} />
          })}
          <text x={pad.left + band * i + band / 2} y={height - 8} textAnchor="middle" className="chart-axis">{c}</text>
        </g>
      })}
    </svg>
    {hover !== null && <div className="chart-tip" style={{ left: Math.min(Math.max(pad.left + band * hover + band / 2, 8), width - 190) }}>
      <strong>{categories[hover]}</strong>
      {series.map(s => <span key={s.key}><i style={{ background: s.color }} />{s.label} {s.values[hover] === null || s.values[hover] === undefined ? '—' : format(s.values[hover])}</span>)}
    </div>}
    <div className="chart-legend">{series.map(s => <span key={s.key}><i style={{ background: s.color }} />{s.label}</span>)}</div>
  </div>
}

/** 봉 차트: 시가·고가·저가·종가. 종가가 시가보다 높으면 빨강(상승), 낮으면 파랑(하락) — 국내 표기 관례. */
export function CandleChart({ points = [], height = 280, ariaLabel, onPick }) {
  const [ref, width] = useWidth()
  const [hover, setHover] = useState(null)
  const pad = { top: 12, right: 12, bottom: 28, left: 64 }
  const rows = points.filter(p => [p.open, p.high, p.low, p.close].every(v => v !== null && v !== undefined))
  if (rows.length < 2) return <p className="chart-empty">봉 차트를 그릴 시가·고가·저가 자료가 부족합니다.</p>
  const innerW = Math.max(40, width - pad.left - pad.right)
  const innerH = height - pad.top - pad.bottom
  const lo = Math.min(...rows.map(p => Number(p.low)))
  const hi = Math.max(...rows.map(p => Number(p.high)))
  const ticks = niceTicks(lo, hi)
  const min = Math.min(lo, ticks[0])
  const max = Math.max(hi, ticks[ticks.length - 1])
  const y = v => pad.top + innerH - ((v - min) / (max - min || 1)) * innerH
  const band = innerW / rows.length
  const body = Math.max(1, Math.min(10, band * 0.7))
  const labelEvery = Math.max(1, Math.ceil(rows.length / Math.max(2, Math.floor(innerW / 90))))
  function move(event) {
    const box = event.currentTarget.getBoundingClientRect()
    setHover(Math.max(0, Math.min(rows.length - 1, Math.floor((event.clientX - box.left - pad.left) / band))))
  }
  const h = hover !== null ? rows[hover] : null
  return <div className="chart-wrap" ref={ref}>
    <svg width={width} height={height} role="img" aria-label={ariaLabel} onPointerMove={move} onPointerLeave={() => setHover(null)}
      onClick={() => h && onPick?.(h.date)} style={onPick ? { cursor: 'pointer' } : undefined}>
      {ticks.map(t => <g key={t}><line x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} className="chart-grid" />
        <text x={pad.left - 8} y={y(t) + 4} textAnchor="end" className="chart-axis">{Number(t).toLocaleString('ko-KR')}</text></g>)}
      {rows.map((p, i) => {
        const cx = pad.left + band * i + band / 2
        const open = Number(p.open), close = Number(p.close)
        const color = close > open ? 'var(--up)' : close < open ? 'var(--down)' : 'var(--muted)'
        return <g key={p.date}>
          <line x1={cx} x2={cx} y1={y(Number(p.high))} y2={y(Number(p.low))} style={{ stroke: color }} />
          <rect x={cx - body / 2} width={body} y={y(Math.max(open, close))} height={Math.max(1, Math.abs(y(open) - y(close)))} style={{ fill: color }} />
          {i % labelEvery === 0 && <text x={cx} y={height - 8} textAnchor="middle" className="chart-axis">{p.date.slice(2).replaceAll('-', '.')}</text>}
        </g>
      })}
      {h && <line x1={pad.left + band * hover + band / 2} x2={pad.left + band * hover + band / 2} y1={pad.top} y2={pad.top + innerH} className="chart-cursor" />}
    </svg>
    {h && <div className="chart-tip" style={{ left: Math.min(Math.max(pad.left + band * hover + 10, 8), width - 190) }}><strong>{h.date}</strong>
      <span>시가 {Number(h.open).toLocaleString('ko-KR')}</span><span>고가 {Number(h.high).toLocaleString('ko-KR')}</span>
      <span>저가 {Number(h.low).toLocaleString('ko-KR')}</span><span>종가 {Number(h.close).toLocaleString('ko-KR')}</span>
      {onPick && <small className="chart-tip-hint">누르면 그날 상세로 →</small>}</div>}
  </div>
}
