import { useEffect, useRef, useState } from 'react'

// 랜딩 3D 장면: A·I·R·A 네 정거장.
// 진행도 progress.current(0~6)는 LandingPage가 스크롤로 정한다.
//  0 첫 화면 — 둥근 입체 글자 A·I·R·A가 한 줄로 떠 있다
//  1 Ask 묻다 — 들은 말(구슬)이 A 둘레를 돈다. 공시와 맞는 구슬만 A 꼭대기로, 맞지 않는 구슬은 떨어진다
//  2 Inspect 보다 — I 둘레로 분기 영업이익 막대 다섯 개가 솟는다(마지막은 잠정실적)
//  3 Relate 잇다 — 주가 흐름 줄과 공시 날짜 점이 실로 이어진다
//  4 Assess 가늠하다 — 다섯 방향 도형이 돈다(점수·등급 아님)
//  5 공식 자료 세 곳 — KRX · OpenDART · 한국은행 ECOS 빛줄기가 한 줄로 모인 글자로 흘러든다
//  6 끝 — 글자가 다시 AIRA로 선다
// three.js(MIT)는 랜딩에서만 불러온다. WebGL이 없으면 그리지 않고 글만 보인다.

const QUARTERS = [12.17, 20.07, 57.23, 89.49, 107.4] // 조 원 · 삼성전자 2025 3분기 ~ 2026 3분기(잠정)
const STATION_X = [0, 13, 26, 39]
const ROW_X = 19.5
// 글자마다 색(테마 변수) — A 연두 · I 청록 · R 보라 · A 금색
const LETTER_TONES = [['--series-a', '#C9EE6B'], ['--series-b', '#7FD1C0'], ['--series-d', '#B5A8E6'], ['--series-c', '#E8C27A']]

const clamp = (n, a = 0, b = 1) => Math.max(a, Math.min(b, n))
const smooth = n => { n = clamp(n); return n * n * (3 - 2 * n) }
const span = (p, a, b) => smooth((p - a) / (b - a))

function cssColor(name, fallback) {
  if (typeof window === 'undefined') return fallback
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}
function rand(seed) { const x = Math.sin(seed * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x) }

export default function LandingScene({ progress, paused }) {
  const mount = useRef(null)
  const pausedRef = useRef(paused)
  const [ready, setReady] = useState(false)
  pausedRef.current = paused

  useEffect(() => {
    let disposed = false
    let cleanup = () => {}
    const el = mount.current
    if (!el || typeof window === 'undefined' || /jsdom/i.test(navigator.userAgent || '')) return undefined
    const probe = document.createElement('canvas')
    if (!(probe.getContext && (probe.getContext('webgl2') || probe.getContext('webgl')))) return undefined
    Promise.all([import('three'), import('three/examples/jsm/environments/RoomEnvironment.js')]).then(([THREE, { RoomEnvironment }]) => {
      if (disposed) return
      const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
      const own = []
      const keep = r => { own.push(r); return r }
      const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
      renderer.setPixelRatio(Math.min(1.75, window.devicePixelRatio || 1))
      renderer.outputColorSpace = THREE.SRGBColorSpace
      renderer.toneMapping = THREE.ACESFilmicToneMapping
      renderer.toneMappingExposure = 1.05
      el.appendChild(renderer.domElement)
      const scene = new THREE.Scene()
      const pmrem = new THREE.PMREMGenerator(renderer)
      scene.environment = keep(pmrem.fromScene(new RoomEnvironment(), 0.04).texture)
      scene.fog = new THREE.Fog(new THREE.Color(cssColor('--paper', '#2F353B')), 24, 60)
      const camera = new THREE.PerspectiveCamera(38, 1, 0.1, 200)
      scene.add(new THREE.HemisphereLight(0xffffff, 0x223038, 0.55))
      const key = new THREE.DirectionalLight(0xffffff, 1.5); key.position.set(6, 9, 8); scene.add(key)
      const rim = new THREE.DirectionalLight(0xbfe8ff, 0.7); rim.position.set(-8, 3, -6); scene.add(rim)

      const shiny = color => keep(new THREE.MeshPhysicalMaterial({ color, roughness: 0.3, metalness: 0.05, clearcoat: 1, clearcoatRoughness: 0.16 }))
      const glow = (color, opacity = 1) => keep(new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 0.55, roughness: 0.4, transparent: opacity < 1, opacity }))

      // ── 둥근 입체 글자: 캡슐과 반원 고리로 빚는다(각지지 않게)
      const RAD = 0.34
      const capGeo = {}
      const capsule = (mat, ax, ay, bx, by) => {
        const dx = bx - ax, dy = by - ay, len = Math.hypot(dx, dy)
        const k = len.toFixed(2)
        const g = capGeo[k] || (capGeo[k] = keep(new THREE.CapsuleGeometry(RAD, len, 8, 24)))
        const m = new THREE.Mesh(g, mat)
        m.position.set((ax + bx) / 2, (ay + by) / 2, 0)
        m.rotation.z = -Math.atan2(dx, dy)
        return m
      }
      const letterMats = LETTER_TONES.map(([v, f]) => shiny(new THREE.Color(cssColor(v, f))))
      const build = {
        A: m => [capsule(m, -1.05, -1.5, 0, 1.5), capsule(m, 1.05, -1.5, 0, 1.5), capsule(m, -0.56, -0.38, 0.56, -0.38)],
        I: m => [capsule(m, 0, -1.5, 0, 1.5)],
        R: m => {
          const bowl = new THREE.Mesh(keep(new THREE.TorusGeometry(0.725, RAD, 16, 40, Math.PI)), m)
          bowl.position.set(-0.1, 0.775, 0); bowl.rotation.z = -Math.PI / 2
          return [capsule(m, -0.75, -1.5, -0.75, 1.5), capsule(m, -0.75, 1.5, -0.1, 1.5), capsule(m, -0.75, 0.05, -0.1, 0.05), bowl, capsule(m, -0.1, 0.05, 0.85, -1.5)]
        },
      }
      const letters = ['A', 'I', 'R', 'A'].map((ch, i) => {
        const g = new THREE.Group()
        build[ch](letterMats[i]).forEach(p => g.add(p))
        scene.add(g)
        return g
      })

      // ── 정거장마다의 장면
      const stations = STATION_X.map(x => { const g = new THREE.Group(); g.position.set(x, 0, 0); scene.add(g); return g })
      const neutral = shiny(new THREE.Color(cssColor('--series-neutral', '#9AA5A0')))
      const lime = glow(new THREE.Color(cssColor('--series-a', '#C9EE6B')))
      const red = glow(new THREE.Color(cssColor('--up', '#FF9A8F')), 0.999)
      // 1 묻다: 들은 말 구슬
      const orbGeo = keep(new THREE.SphereGeometry(0.13, 20, 14))
      const bigOrb = keep(new THREE.SphereGeometry(0.21, 24, 16))
      const orbs = Array.from({ length: 12 }, (_, k) => { const m = new THREE.Mesh(orbGeo, neutral); stations[0].add(m); return { m, a: (k / 12) * Math.PI * 2, r: 2.0 + rand(k) * 0.35, h: (rand(k + 9) - 0.5) * 0.8 } })
      const factMat = glow(new THREE.Color(cssColor('--series-a', '#C9EE6B')))
      const fact = new THREE.Mesh(bigOrb, factMat); stations[0].add(fact)
      const claim = new THREE.Mesh(bigOrb, red); stations[0].add(claim)
      // 2 보다: 분기 막대
      const teal = shiny(new THREE.Color(cssColor('--series-b', '#7FD1C0')))
      const gold = shiny(new THREE.Color(cssColor('--series-c', '#E8C27A')))
      const barGeo = keep(new THREE.CylinderGeometry(0.24, 0.24, 1, 28)); barGeo.translate(0, 0.5, 0)
      const capTop = keep(new THREE.SphereGeometry(0.24, 24, 12, 0, Math.PI * 2, 0, Math.PI / 2))
      const bars = QUARTERS.map((q, k) => {
        const ang = -1.05 + (k / (QUARTERS.length - 1)) * 2.1
        const mat = k === QUARTERS.length - 1 ? gold : teal
        const g = new THREE.Group(); g.position.set(Math.sin(ang) * 2.7, -1.6, -Math.cos(ang) * 1.3 + 0.1)
        const body = new THREE.Mesh(barGeo, mat), top = new THREE.Mesh(capTop, mat)
        g.add(body, top); stations[1].add(g)
        return { body, top, h: (q / QUARTERS[QUARTERS.length - 1]) * 3.5 }
      })
      // 3 잇다: 주가 흐름 줄 · 공시 날짜 점 · 잇는 실
      const lav = glow(new THREE.Color(cssColor('--series-d', '#B5A8E6')))
      const inkMat = shiny(new THREE.Color(cssColor('--ink', '#F4F6F2')))
      const wave = x => 0.75 + 0.42 * Math.sin(1.35 * x + 0.6) + 0.18 * Math.sin(3.2 * x)
      const curve = new THREE.CatmullRomCurve3(Array.from({ length: 25 }, (_, k) => { const x = -3.2 + (k / 24) * 6.4; return new THREE.Vector3(x, wave(x), 1.1) }))
      const tubeGeo = keep(new THREE.TubeGeometry(curve, 160, 0.06, 10, false))
      const tube = new THREE.Mesh(tubeGeo, inkMat); stations[2].add(tube)
      const tubeCount = tubeGeo.index.count
      const pulse = new THREE.Mesh(orbGeo, lime); stations[2].add(pulse)
      const dotGeo = keep(new THREE.SphereGeometry(0.15, 20, 14))
      const threadGeo = keep(new THREE.CylinderGeometry(0.018, 0.018, 1, 8)); threadGeo.translate(0, 0.5, 0)
      const DOTS = [-2.7, -1.6, -0.5, 0.8, 1.8, 2.8]
      const relate = DOTS.map(x => {
        const d = new THREE.Mesh(dotGeo, lav); d.position.set(x, -1.55, 1.1)
        const th = new THREE.Mesh(threadGeo, lav); th.position.set(x, -1.55, 1.1)
        stations[2].add(d, th)
        return { th, len: wave(x) + 1.55 }
      })
      // 4 가늠하다: 다섯 방향 도형(예시 모양 — 점수·등급 아님)
      const radar = new THREE.Group(); radar.position.set(0, 0.1, 1.7); stations[3].add(radar)
      const VALS = [0.86, 0.62, 0.74, 0.92, 0.58]
      const pt = (k, r) => new THREE.Vector3(Math.cos(Math.PI / 2 + (k * 2 * Math.PI) / 5) * r, Math.sin(Math.PI / 2 + (k * 2 * Math.PI) / 5) * r, 0)
      const faint = keep(new THREE.LineBasicMaterial({ color: new THREE.Color(cssColor('--muted', '#B9C2BD')), transparent: true, opacity: 0.45 }))
      for (const lv of [0.5, 1]) radar.add(new THREE.LineLoop(keep(new THREE.BufferGeometry().setFromPoints([0, 1, 2, 3, 4].map(k => pt(k, 2 * lv)))), faint))
      for (let k = 0; k < 5; k++) radar.add(new THREE.Line(keep(new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(), pt(k, 2)])), faint))
      const shape = new THREE.Shape(VALS.map((v, k) => { const p = pt(k, 2 * v); return new THREE.Vector2(p.x, p.y) }))
      const goldColor = new THREE.Color(cssColor('--series-c', '#E8C27A'))
      const fill = new THREE.Mesh(keep(new THREE.ShapeGeometry(shape)), keep(new THREE.MeshBasicMaterial({ color: goldColor, transparent: true, opacity: 0.32, side: THREE.DoubleSide, depthWrite: false })))
      const edge = new THREE.LineLoop(keep(new THREE.BufferGeometry().setFromPoints(VALS.map((v, k) => pt(k, 2 * v)))), keep(new THREE.LineBasicMaterial({ color: goldColor })))
      const valueGroup = new THREE.Group(); valueGroup.add(fill, edge)
      VALS.forEach((v, k) => { const d = new THREE.Mesh(orbGeo, gold); d.position.copy(pt(k, 2 * v)); valueGroup.add(d) })
      radar.add(valueGroup)
      // 5 공식 자료 세 곳: 흘러드는 빛줄기
      const STREAMS = [
        [new THREE.Vector3(ROW_X - 21, 8, -10), new THREE.Vector3(ROW_X - 10, 5, -4), new THREE.Vector3(ROW_X - 3, 0.5, 0), '--series-a', '#C9EE6B'],
        [new THREE.Vector3(ROW_X, 12, -14), new THREE.Vector3(ROW_X, 6, -5), new THREE.Vector3(ROW_X, 1, 0), '--series-b', '#7FD1C0'],
        [new THREE.Vector3(ROW_X + 21, 8, -10), new THREE.Vector3(ROW_X + 10, 5, -4), new THREE.Vector3(ROW_X + 3, 0.5, 0), '--series-d', '#B5A8E6'],
      ]
      const N_STREAM = 90
      const streams = STREAMS.map(([a, b, c, v, f]) => {
        const path = new THREE.CatmullRomCurve3([a, b, c])
        const geo = keep(new THREE.BufferGeometry()); geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(N_STREAM * 3), 3))
        const mat = keep(new THREE.PointsMaterial({ color: new THREE.Color(cssColor(v, f)), size: 0.16, transparent: true, opacity: 0, depthWrite: false }))
        const pts = new THREE.Points(geo, mat); scene.add(pts)
        return { path, geo, mat }
      })
      // 공중의 먼지(깊이감)
      const dustGeo = keep(new THREE.BufferGeometry())
      const dustPos = new Float32Array(600 * 3)
      for (let i = 0; i < 600; i++) { dustPos[i * 3] = -12 + rand(i) * 64; dustPos[i * 3 + 1] = -6 + rand(i + 600) * 14; dustPos[i * 3 + 2] = -18 + rand(i + 1200) * 22 }
      dustGeo.setAttribute('position', new THREE.BufferAttribute(dustPos, 3))
      const dust = new THREE.Points(dustGeo, keep(new THREE.PointsMaterial({ color: new THREE.Color(cssColor('--muted', '#B9C2BD')), size: 0.05, transparent: true, opacity: 0.4, depthWrite: false })))
      scene.add(dust)

      // ── 카메라 길: 정거장마다 다른 각도로 들른다
      const CAM = [
        [[ROW_X + 0.8, 1.0, 21], [ROW_X, 0.2, 0]],
        [[2.5, 1.8, 13.5], [0, 0.4, 0]],
        [[10.5, 3.4, 13.5], [13, 0.6, 0]],
        [[28.5, 0.6, 13.5], [26, 0.1, 0.5]],
        [[37.5, 3.2, 13.5], [39, 0.3, 0.7]],
        [[ROW_X, 5.5, 27], [ROW_X, 1.2, 0]],
        [[ROW_X - 0.6, 0.6, 21], [ROW_X, 0.2, 0]],
      ]
      const camPath = new THREE.CatmullRomCurve3(CAM.map(c => new THREE.Vector3(...c[0])), false, 'centripetal')
      const lookPath = new THREE.CatmullRomCurve3(CAM.map(c => new THREE.Vector3(...c[1])), false, 'centripetal')
      let far = 1
      const look = new THREE.Vector3()

      function resize() {
        const w = Math.max(1, el.clientWidth), h = Math.max(1, el.clientHeight)
        renderer.setSize(w, h, false)
        camera.aspect = w / h
        // 넓은 화면은 장면을 오른쪽에(왼쪽은 글), 좁은 화면은 위쪽에 둔다. 좁을수록 카메라를 뒤로 뺀다.
        if (w >= 900) camera.setViewOffset(w, h, -w * 0.22, 0, w, h)
        else camera.setViewOffset(w, h, 0, h * 0.2, w, h)
        far = camera.aspect >= 1.2 ? 1 : clamp((1.2 / camera.aspect) * 0.78, 1, 2)
        scene.fog.near = 24 * far; scene.fog.far = 60 * far
        camera.updateProjectionMatrix()
      }
      const ro = new ResizeObserver(resize); ro.observe(el); resize()

      // 테마가 바뀌면 색도 바꾼다
      const recolor = () => {
        LETTER_TONES.forEach(([v, f], i) => letterMats[i].color.set(cssColor(v, f)))
        neutral.color.set(cssColor('--series-neutral', '#9AA5A0'))
        for (const [m, v, f] of [[lime, '--series-a', '#C9EE6B'], [factMat, '--series-a', '#C9EE6B'], [red, '--up', '#FF9A8F'], [lav, '--series-d', '#B5A8E6']]) { m.color.set(cssColor(v, f)); m.emissive.set(cssColor(v, f)) }
        teal.color.set(cssColor('--series-b', '#7FD1C0')); gold.color.set(cssColor('--series-c', '#E8C27A'))
        inkMat.color.set(cssColor('--ink', '#F4F6F2')); faint.color.set(cssColor('--muted', '#B9C2BD'))
        goldColor.set(cssColor('--series-c', '#E8C27A')); fill.material.color.copy(goldColor); edge.material.color.copy(goldColor)
        streams.forEach((s, i) => s.mat.color.set(cssColor(STREAMS[i][3], STREAMS[i][4])))
        scene.fog.color.set(cssColor('--paper', '#2F353B'))
      }
      const mo = new MutationObserver(recolor)
      mo.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'class'] })

      let t = 0, last = performance.now(), raf = 0, first = true
      const tmp = new THREE.Vector3()
      function frame(now) {
        raf = requestAnimationFrame(frame)
        if (document.hidden) { last = now; return }
        const dt = clamp((now - last) / 1000, 0, 0.05); last = now
        if (!pausedRef.current && !reduced) t += dt
        const s = clamp(progress.current ?? 0, 0, 6)
        // 글자: 한 줄(처음 · 공식 자료 · 끝) ↔ 네 정거장
        const row = clamp(1 - span(s, 0.25, 0.95) + span(s, 4.35, 5.0))
        letters.forEach((g, i) => {
          const rx = ROW_X + (i - 1.5) * 2.7, sx = STATION_X[i]
          g.position.set(sx + (rx - sx) * row, Math.sin(t * 0.9 + i * 1.1) * 0.12 * (0.4 + row), Math.sin(Math.PI * row) * -2.5)
          const lp = clamp(s - (i + 1) + 0.5)
          g.rotation.y = Math.sin(t * 0.55 + i * 0.9) * (0.28 + 0.3 * (1 - row)) + (1 - row) * (lp - 0.5) * 0.9
          g.rotation.x = Math.sin(t * 0.4 + i) * 0.06
          const sc = 1 + 0.12 * (1 - row) * (1 - Math.abs(lp - 0.5) * 2)
          g.scale.setScalar(sc)
        })
        // 정거장 장면은 그 정거장 가까이에서만 보인다
        stations.forEach((g, i) => { const w = 1 - span(Math.abs(s - (i + 1)), 0.45, 0.9); g.visible = w > 0.01; g.scale.setScalar(Math.max(0.001, w)) })
        // 1 묻다
        const lp0 = clamp(s - 0.5)
        orbs.forEach(o => { const a = o.a + t * 0.35; o.m.position.set(Math.cos(a) * o.r, o.h + Math.sin(a * 2 + o.r) * 0.25, Math.sin(a) * o.r * 0.75) })
        const fa = t * 0.35 + 0.4, fe = span(lp0, 0.22, 0.5)
        tmp.set(Math.cos(fa) * 2.1, 0.2, Math.sin(fa) * 1.6)
        fact.position.set(tmp.x * (1 - fe), tmp.y + (2.3 - tmp.y) * fe + Math.sin(t * 2) * 0.05 * fe, tmp.z * (1 - fe))
        fact.material.emissiveIntensity = 0.4 + 0.8 * fe
        fact.scale.setScalar(1 + 0.5 * fe)
        const ce = span(lp0, 0.32, 0.6), ca = t * 0.35 + 3.4
        claim.position.set(Math.cos(ca) * 2.1, 0.1 - ce * 2.2, Math.sin(ca) * 1.6)
        claim.material.opacity = 1 - ce
        claim.visible = ce < 0.99
        // 2 보다
        const lp1 = clamp(s - 1.5)
        bars.forEach((b, k) => {
          const g = span(lp1, 0.1 + k * 0.06, 0.4 + k * 0.06)
          const h = Math.max(0.001, b.h * g)
          b.body.scale.y = h; b.top.position.y = h
          b.top.scale.setScalar(Math.max(0.001, g))
        })
        stations[1].rotation.y = Math.sin(t * 0.3) * 0.25
        // 3 잇다
        const lp2 = clamp(s - 2.5)
        tubeGeo.setDrawRange(0, Math.floor(tubeCount * span(lp2, 0.05, 0.4) / 3) * 3)
        relate.forEach((r, k) => { const e = span(lp2, 0.3 + k * 0.045, 0.5 + k * 0.045); r.th.scale.y = Math.max(0.001, r.len * e); r.th.visible = e > 0.01 })
        curve.getPointAt((t * 0.12) % 1, tmp); pulse.position.copy(tmp)
        pulse.visible = lp2 > 0.3
        stations[2].rotation.y = Math.sin(t * 0.25) * 0.18
        // 4 가늠하다
        const lp3 = clamp(s - 3.5)
        valueGroup.scale.setScalar(Math.max(0.001, span(lp3, 0.15, 0.5)))
        radar.rotation.z = t * 0.12
        radar.rotation.y = Math.sin(t * 0.4) * 0.35
        // 5 공식 자료 세 곳
        const w5 = 1 - span(Math.abs(s - 5), 0.5, 1.0)
        streams.forEach((st, i) => {
          st.mat.opacity = w5 * 0.9
          const arr = st.geo.attributes.position.array
          for (let k = 0; k < N_STREAM; k++) {
            st.path.getPointAt((k / N_STREAM + t * 0.07 + i * 0.13) % 1, tmp)
            arr[k * 3] = tmp.x + Math.sin(k * 1.7 + t) * 0.12; arr[k * 3 + 1] = tmp.y + Math.cos(k * 2.3 + t) * 0.12; arr[k * 3 + 2] = tmp.z
          }
          st.geo.attributes.position.needsUpdate = true
        })
        dust.rotation.y = Math.sin(t * 0.05) * 0.05
        dust.position.y = Math.sin(t * 0.2) * 0.2
        // 카메라
        const u = s / 6
        camPath.getPoint(u, camera.position); lookPath.getPoint(u, look)
        camera.position.sub(look).multiplyScalar(far).add(look)
        camera.position.x += Math.sin(t * 0.21) * 0.25; camera.position.y += Math.sin(t * 0.27) * 0.14
        camera.lookAt(look)
        renderer.render(scene, camera)
        if (first) { first = false; setReady(true) }
      }
      raf = requestAnimationFrame(frame)
      cleanup = () => {
        cancelAnimationFrame(raf); ro.disconnect(); mo.disconnect()
        own.forEach(r => r.dispose?.()); pmrem.dispose(); renderer.dispose()
        renderer.domElement.remove()
      }
    }).catch(() => {})
    return () => { disposed = true; cleanup() }
  }, [progress])

  return <div className={`landing-scene${ready ? ' is-ready' : ''}`} ref={mount} aria-hidden="true" />
}
