import { useEffect, useRef, useState } from 'react'

// 랜딩 3D 장면 하나. 진행도(progress.current, 0~1)를 따라 같은 물체들이 이어서 바뀐다. 장면이 툭 끊기지 않는다.
// AIRA 로고 'A'는 날카로운 조각 세 개로 되어 있다. 그 조각을 삼각형으로 나눠 유리 조각으로 만든다.
//  0.00 흩어진 유리 조각(들은 말)이 떠다닌다
//  0.16 조각이 가까이 모인다. 맞지 않는 조각(붉은 빛)이 섞여 있다
//  0.33 공시와 맞는 조각만 회전하며 제자리로 → 로고 'A' 완성, 맞지 않는 조각은 흐려지며 떨어진다
//  0.52 A가 바닥으로 눕고, 그 위로 분기 영업이익 막대가 솟는다
//  0.70 잠정실적 막대가 하나 더 솟고, 막대마다 빛줄기가 공시 문서로 이어진다
//  0.87 막대가 내려가고 A가 다시 일어서며 빛난다
// three.js(MIT)는 랜딩에서만 불러온다. WebGL이 없으면 그리지 않고 글만 보인다.

// Logo.jsx와 같은 좌표(viewBox 170 0 500 540의 마크 부분)
const LOGO = [
  { tone: 'a', points: [[428, 0], [434, 118], [328, 300], [250, 345], [180, 425]] },
  { tone: 'b', points: [[428, 0], [660, 425], [575, 392], [505, 292], [548, 228], [434, 118]] },
  { tone: 'b', points: [[180, 425], [266, 398], [520, 292], [548, 228], [480, 236], [330, 302], [250, 345]] },
]
const QUARTERS = [12.17, 20.07, 57.23, 89.49, 107.4] // 조 원 · 2025 3분기 ~ 2026 3분기(잠정)

const clamp = (n, a = 0, b = 1) => Math.max(a, Math.min(b, n))
const smooth = n => { n = clamp(n); return n * n * (3 - 2 * n) }
const span = (p, a, b) => smooth((p - a) / (b - a))

function cssColor(name, fallback) {
  if (typeof window === 'undefined') return fallback
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

// 결정적인 의사 난수(새로고침해도 같은 배치)
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
      renderer.toneMappingExposure = 1.1
      el.appendChild(renderer.domElement)
      const scene = new THREE.Scene()
      const pmrem = new THREE.PMREMGenerator(renderer)
      scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
      const camera = new THREE.PerspectiveCamera(40, 1, 0.1, 200)

      const accent = new THREE.Color(cssColor('--accent', '#C9EE6B'))
      const teal = new THREE.Color(cssColor('--series-b', '#7FD1C0'))
      const warn = new THREE.Color(cssColor('--up', '#FF9A8F'))
      const toneA = new THREE.Color('#f3f6f1'), toneB = new THREE.Color('#9fb3ad')
      scene.add(new THREE.HemisphereLight('#dfe8e3', '#20262a', 1.1))
      const key = new THREE.DirectionalLight('#ffffff', 2.2); key.position.set(6, 10, 8); scene.add(key)
      const glow = new THREE.PointLight(accent, 0, 14, 2); glow.position.set(0, 2.2, 2.5); scene.add(glow)

      const world = new THREE.Group(); scene.add(world)
      const mark = new THREE.Group(); world.add(mark) // 로고 A(조각이 모이는 자리)

      // --- 로고 조각: 다각형을 삼각형으로 나눠 두께 있는 유리 조각으로
      const toWorld = ([x, y]) => new THREE.Vector2((x - 420) / 100, (430 - y) / 100)
      const shards = []
      const makeShard = (tri, tone, seed, fake = false) => {
        const c = new THREE.Vector2().add(tri[0]).add(tri[1]).add(tri[2]).multiplyScalar(1 / 3)
        const shape = new THREE.Shape(tri.map(v => v.clone().sub(c)))
        const geo = keep(new THREE.ExtrudeGeometry(shape, { depth: 0.16, bevelEnabled: true, bevelThickness: 0.03, bevelSize: 0.025, bevelSegments: 2 }))
        geo.translate(0, 0, -0.08)
        const mat = keep(new THREE.MeshPhysicalMaterial({ color: fake ? warn : tone === 'a' ? toneA : toneB, metalness: 0.05, roughness: 0.12,
          transmission: 0.55, thickness: 0.6, ior: 1.45, clearcoat: 1, clearcoatRoughness: 0.05, transparent: true, opacity: 1, emissive: fake ? warn : accent, emissiveIntensity: 0 }))
        const mesh = new THREE.Mesh(geo, mat)
        const edges = new THREE.LineSegments(keep(new THREE.EdgesGeometry(geo, 25)), keep(new THREE.LineBasicMaterial({ color: fake ? warn : accent, transparent: true, opacity: 0 })))
        mesh.add(edges)
        const r = (k) => rand(seed * 13 + k)
        const radius = 4.2 + r(1) * 3.2, theta = r(2) * Math.PI * 2, phi = (r(3) - 0.5) * 1.6
        mesh.userData = {
          fake, edges,
          home: new THREE.Vector3(c.x, c.y, 0),
          far: new THREE.Vector3(1.2 + Math.cos(theta) * radius * 0.85, 2.3 + Math.sin(phi) * 2.6, -2.2 + Math.sin(theta) * radius * 0.45),
          near: new THREE.Vector3(Math.cos(theta) * radius * 0.48, 2.2 + Math.sin(phi) * 1.5, Math.sin(theta) * radius * 0.3),
          fall: new THREE.Vector3(Math.cos(theta) * 6, -4 - r(4) * 3, Math.sin(theta) * 3),
          spin: new THREE.Euler(r(5) * 6, r(6) * 6, r(7) * 6), speed: 0.35 + r(8) * 0.6, phase: r(9) * 6,
        }
        mark.add(mesh); shards.push(mesh)
      }
      let seed = 1
      for (const part of LOGO) {
        const pts = part.points.map(toWorld)
        for (const [a, b, c] of THREE.ShapeUtils.triangulateShape(pts, [])) makeShard([pts[a], pts[b], pts[c]], part.tone, seed++)
      }
      // 맞지 않는 조각(들은 말 가운데 틀린 것)
      for (let i = 0; i < 9; i++) {
        const s = 0.35 + rand(i + 90) * 0.5, a = rand(i + 70) * Math.PI * 2
        const tri = [0, 1, 2].map(k => new THREE.Vector2(Math.cos(a + k * 2.1 + rand(i * 3 + k) * 0.6) * s, Math.sin(a + k * 2.1 + rand(i * 5 + k) * 0.6) * s))
        makeShard(tri, 'b', 200 + i, true)
      }

      // --- 분기 막대(누운 A 위로 솟는다)
      const barGeo = keep(new THREE.BoxGeometry(0.62, 1, 0.62)); barGeo.translate(0, 0.5, 0)
      const bars = QUARTERS.map((v, i) => {
        const mat = keep(new THREE.MeshPhysicalMaterial({ color: i === 4 ? teal : accent, roughness: 0.16, metalness: 0.1, clearcoat: 1, clearcoatRoughness: 0.06,
          transmission: i === 4 ? 0.35 : 0.15, thickness: 0.5, emissive: i === 4 ? teal : accent, emissiveIntensity: 0.08 }))
        const m = new THREE.Mesh(barGeo, mat)
        m.position.set((i - 2) * 0.95, 0.02, 0.9 - Math.abs(i - 2) * 0.12)
        m.userData.h = Math.max(0.12, v / 107.4 * 3.2)
        m.scale.y = 0.001; world.add(m); return m
      })
      // --- 막대 꼭대기에서 떠오르는 빛 알갱이
      const SPARKS = 90
      const sparkGeo = keep(new THREE.BufferGeometry())
      const sparkPos = new Float32Array(SPARKS * 3)
      const sparkSeed = Array.from({ length: SPARKS }, (_, i) => ({ bar: i % 5, off: rand(i + 400), dx: (rand(i + 500) - 0.5) * 0.5, dz: (rand(i + 600) - 0.5) * 0.5 }))
      sparkGeo.setAttribute('position', new THREE.BufferAttribute(sparkPos, 3))
      const sparkMat = keep(new THREE.PointsMaterial({ color: accent, size: 0.07, transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending }))
      const sparks = new THREE.Points(sparkGeo, sparkMat); world.add(sparks)

      // --- 공시 문서(종이 한 장)와 빛줄기
      const docTex = (() => {
        const c = document.createElement('canvas'); c.width = 256; c.height = 320
        const g = c.getContext('2d')
        g.fillStyle = '#f4f6f1'; g.fillRect(0, 0, 256, 320)
        g.fillStyle = '#c9d2cc'; for (let y = 70; y < 300; y += 22) g.fillRect(24, y, 150 + ((y * 7) % 60), 8)
        g.fillStyle = '#587508'; g.fillRect(24, 26, 90, 14)
        return keep(new THREE.CanvasTexture(c))
      })()
      docTex.colorSpace = THREE.SRGBColorSpace
      const docGeo = keep(new THREE.PlaneGeometry(0.6, 0.76))
      const docs = bars.map((bar, i) => {
        const d = new THREE.Mesh(docGeo, keep(new THREE.MeshBasicMaterial({ map: docTex, transparent: true, opacity: 0, side: THREE.DoubleSide })))
        d.position.set(bar.position.x * 1.25, 5.1 + (i % 2) * 0.45, -1.4)
        d.rotation.set(-0.12, (i - 2) * -0.12, (i - 2) * 0.04)
        world.add(d); return d
      })
      const threads = bars.map((bar, i) => {
        const pts = Array.from({ length: 30 }, (_, k) => new THREE.Vector3())
        const geo = keep(new THREE.BufferGeometry().setFromPoints(pts))
        const line = new THREE.Line(geo, keep(new THREE.LineBasicMaterial({ color: i === 4 ? teal : accent, transparent: true, opacity: 0 })))
        world.add(line); return { line, geo, bar, doc: docs[i] }
      })

      const pointer = { x: 0, y: 0 }
      const onMove = e => { pointer.x = e.clientX / window.innerWidth - 0.5; pointer.y = e.clientY / window.innerHeight - 0.5 }
      window.addEventListener('pointermove', onMove, { passive: true })
      let narrow = false
      const resize = () => {
        const w = el.clientWidth || 1, h = el.clientHeight || 1
        narrow = w / h < 0.9
        renderer.setSize(w, h, false); camera.aspect = w / h; camera.fov = narrow ? 52 : 40
        // 넓은 화면: 글이 왼쪽에 있으니 장면을 오른쪽으로 민다(투영만 옮겨 돌아도 자리가 흔들리지 않게)
        if (narrow) camera.clearViewOffset(); else camera.setViewOffset(w, h, -w * 0.2, 0, w, h)
        camera.updateProjectionMatrix()
      }
      resize()
      const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(resize) : null
      ro?.observe(el)

      const v = new THREE.Vector3(), q = new THREE.Quaternion(), qa = new THREE.Quaternion(), e = new THREE.Euler()
      let raf = 0, t = 0, last = performance.now()
      const tick = now => {
        raf = requestAnimationFrame(tick)
        const dt = Math.min(0.05, (now - last) / 1000); last = now
        if (!pausedRef.current && !reduced && !document.hidden) t += dt
        const p = progress.current ?? 0
        const near = span(p, 0.08, 0.26)        // 흩어진 → 가까이
        const fit = span(p, 0.34, 0.5)          // 제자리로
        const lie = span(p, 0.53, 0.62) * (1 - span(p, 0.87, 0.96)) // 눕기 → 다시 서기
        const rise = span(p, 0.56, 0.68) * (1 - span(p, 0.86, 0.93))
        const pre = span(p, 0.7, 0.77) * (1 - span(p, 0.86, 0.93))
        const link = span(p, 0.73, 0.83) * (1 - span(p, 0.85, 0.9))
        const shine = span(p, 0.47, 0.52) * (1 - lie) + span(p, 0.9, 0.98)

        shards.forEach(m => {
          const d = m.userData
          const bob = Math.sin(t * d.speed + d.phase)
          if (d.fake) {
            v.copy(d.far).lerp(d.near, near).lerp(d.fall, fit)
          } else {
            v.copy(d.far).lerp(d.near, near).lerp(d.home, fit)
          }
          v.y += bob * (0.22 * (1 - fit) + 0.025 * fit)
          v.z += Math.sin(t * d.speed * 0.7 + d.phase) * 0.04 * fit * (1 - lie)
          m.position.copy(v)
          e.set(d.spin.x + t * 0.3 * d.speed, d.spin.y + t * 0.25 * d.speed, d.spin.z); qa.setFromEuler(e)
          q.identity(); m.quaternion.copy(qa).slerp(q, d.fake ? 0 : fit)
          const fade = d.fake ? 1 - fit : 1
          m.material.opacity = fade; m.visible = fade > 0.02
          m.material.emissiveIntensity = d.fake ? 0.25 * near * (1 - fit) : 0.35 * shine
          d.edges.material.opacity = d.fake ? 0.6 * near * fade : 0.15 * near + 0.75 * shine
        })
        // A: 서 있다가 바닥으로 눕고, 끝에 다시 선다
        mark.rotation.x = -Math.PI / 2 * lie
        mark.rotation.y = Math.sin(t * 0.35) * 0.12 * fit * (1 - lie)
        mark.position.set(0, 0.03 * lie, -1.2 * lie)
        mark.scale.setScalar(1 + 0.12 * lie)
        glow.intensity = 18 * shine

        bars.forEach((m, i) => {
          // 왼쪽부터 차례로 솟고, 다 선 뒤에도 조금씩 숨쉰다
          const own = i === 4 ? pre : smooth(clamp(rise * 1.6 - i * 0.15))
          const breathe = 1 + Math.sin(t * 1.3 + i * 0.8) * 0.035 * own
          const h = own * m.userData.h * breathe
          m.scale.y = Math.max(0.001, h); m.visible = h > 0.02
          m.scale.x = m.scale.z = 1 + (1 - own) * 0.25
          m.rotation.y = Math.sin(t * 0.5 + i) * 0.06 * own
          // 표면 빛이 막대에서 막대로 흘러간다
          const wave = Math.pow(Math.max(0, Math.sin(t * 1.8 - i * 0.9)), 6)
          m.material.emissiveIntensity = (i === 4 ? 0.18 : 0.06) + 0.45 * wave * own
        })
        {
          const pos = sparkGeo.attributes.position
          sparkSeed.forEach((sd, k) => {
            const bar = bars[sd.bar]
            const life = (t * 0.35 + sd.off) % 1
            pos.setXYZ(k, bar.position.x + sd.dx * (1 + life), bar.scale.y + 0.1 + life * 1.8, bar.position.z + sd.dz * (1 + life))
          })
          pos.needsUpdate = true
          sparkMat.opacity = 0.75 * Math.max(rise, pre) * (1 - link * 0.5)
          sparks.visible = sparkMat.opacity > 0.02
        }
        docs.forEach((d, i) => { d.material.opacity = link; d.visible = link > 0.02; d.position.y = 5.1 + (i % 2) * 0.45 + Math.sin(t * 0.6 + i) * 0.06 })
        threads.forEach(({ geo, bar, doc }, i) => {
          const a = new THREE.Vector3(bar.position.x, bar.scale.y + 0.05, bar.position.z), b = doc.position.clone().add(new THREE.Vector3(0, -0.5, 0))
          const pos = geo.attributes.position
          const n = pos.count, drawn = Math.round(n * link)
          for (let k = 0; k < n; k++) {
            const s = k / (n - 1)
            v.copy(a).lerp(b, s); v.z += Math.sin(s * Math.PI) * 0.6; v.x += Math.sin(s * Math.PI * 2 + i) * 0.08
            pos.setXYZ(k, v.x, v.y, v.z)
          }
          pos.needsUpdate = true
          geo.setDrawRange(0, Math.max(0, drawn))
          threads[i].line.material.opacity = 0.9 * link; threads[i].line.visible = link > 0.02
        })

        // 카메라: 진행에 따라 천천히 돌며 다가간다(끊기지 않게 하나의 길)
        const ang = -0.35 + p * 0.9 + pointer.x * 0.25
        const dist = (narrow ? 15 : 12.5) - 1.5 * fit + 1.8 * rise
        const height = 3.2 + 2.2 * rise + pointer.y * -0.6 - 0.6 * span(p, 0.9, 1)
        camera.position.set(Math.sin(ang) * dist, height, Math.cos(ang) * dist)
        camera.lookAt(0, (narrow ? 3.2 : 2) + 1.2 * rise - 0.5 * lie, 0)
        renderer.render(scene, camera)
      }
      raf = requestAnimationFrame(tick)
      setReady(true)
      cleanup = () => {
        cancelAnimationFrame(raf)
        window.removeEventListener('pointermove', onMove)
        ro?.disconnect()
        own.forEach(r => r.dispose?.())
        pmrem.dispose(); renderer.dispose(); renderer.domElement.remove()
      }
    }).catch(() => {})
    return () => { disposed = true; cleanup() }
  }, [progress])

  return <div ref={mount} className={`landing-scene${ready ? ' is-ready' : ''}`} aria-hidden="true" />
}
