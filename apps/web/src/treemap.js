// 정사각형에 가깝게 나누는 트리맵 배치(squarified). items: [{value, ...}] 큰 값부터.
export function squarify(items, x, y, w, h) {
  const total = items.reduce((sum, item) => sum + item.value, 0)
  if (!items.length || total <= 0 || w <= 0 || h <= 0) return []
  const scale = (w * h) / total
  const nodes = items.map(item => ({ item, area: item.value * scale }))
  const out = []
  let rect = { x, y, w, h }
  let row = []
  const worst = (list, side) => {
    const sum = list.reduce((s, n) => s + n.area, 0)
    const max = Math.max(...list.map(n => n.area))
    const min = Math.min(...list.map(n => n.area))
    return Math.max((side * side * max) / (sum * sum), (sum * sum) / (side * side * min))
  }
  const layout = (list, r) => {
    const sum = list.reduce((s, n) => s + n.area, 0)
    const horizontal = r.w >= r.h
    const side = horizontal ? r.h : r.w
    const thick = sum / side
    let offset = 0
    for (const n of list) {
      const len = n.area / thick
      out.push(horizontal ? { ...n.item, x: r.x, y: r.y + offset, w: thick, h: len } : { ...n.item, x: r.x + offset, y: r.y, w: len, h: thick })
      offset += len
    }
    return horizontal ? { x: r.x + thick, y: r.y, w: r.w - thick, h: r.h } : { x: r.x, y: r.y + thick, w: r.w, h: r.h - thick }
  }
  for (const node of nodes) {
    const side = Math.min(rect.w, rect.h)
    if (!row.length || worst([...row, node], side) <= worst(row, side)) row.push(node)
    else { rect = layout(row, rect); row = [node] }
  }
  if (row.length) layout(row, rect)
  return out
}
