/* AI 길 안내 — 흐르는 빛(유체)이 마우스 커서를 따라다니며 그 자리에서 지시하고,
 * 누를 버튼에 꺾쇠를 잠그고, 화면의 버튼을 하나씩(넓이) 그리고 안쪽 메뉴까지(깊이) 설명한다.
 * 참고: React Bits(Splash·Target·Click Spark), Aceternity(Following Pointer·Glowing Effect), driver.js(미끄러지는 조명),
 *       Copilot Vision Highlights·Gemini Live(말하며 짚기), 노션 AI 에이전트(직접 해 보이기), Dia(끌려오는 목적지), Arc(날아 들어가기).
 * 프로젝트마다 따라다니는 효과를 고른다. 유체(splash)는 window.AiFluid(aiFluid.js, Pavel Dobryakov MIT)가 있을 때만 쓴다.
 */
  const CSS = `
  .aig{position:fixed;inset:0;pointer-events:none;z-index:2147483600;font-family:"Pretendard","Apple SD Gothic Neo","Malgun Gothic",system-ui,sans-serif}
  .aig canvas.fl,.aig canvas.fx{position:fixed;inset:0;width:100vw;height:100vh}.aig canvas.fl{mix-blend-mode:screen}
  .aig .core{position:fixed;left:0;top:0;width:16px;height:16px;margin:-8px 0 0 -8px;border-radius:50%;background:#fff;box-shadow:0 0 12px 4px var(--c1),0 0 34px 10px var(--g2);will-change:transform}
  .aig .core::after{content:"";position:absolute;inset:-10px;border-radius:50%;border:1.5px solid var(--c1);opacity:.7;animation:aig-pulse 1.6s ease-out infinite}
  @keyframes aig-pulse{from{transform:scale(.5);opacity:.9}to{transform:scale(1.8);opacity:0}}
  .aig .dim{position:fixed;inset:0;background:rgba(4,6,10,.2);opacity:0;transition:opacity .35s;-webkit-mask:var(--hole);mask:var(--hole)}
  .aig .dim.on{opacity:1}
  .aig .tg{position:fixed;left:0;top:0;width:10px;height:10px;opacity:0;transition:opacity .2s;will-change:transform}
  .aig .tg.on{opacity:1}
  .aig .tg i{position:absolute;width:16px;height:16px;border:3px solid var(--c1);filter:drop-shadow(0 0 6px var(--c1))}
  .aig .tg i:nth-child(1){left:0;top:0;border-right:0;border-bottom:0;border-radius:6px 0 0 0}
  .aig .tg i:nth-child(2){right:0;top:0;border-left:0;border-bottom:0;border-radius:0 6px 0 0}
  .aig .tg i:nth-child(3){right:0;bottom:0;border-left:0;border-top:0;border-radius:0 0 6px 0}
  .aig .tg i:nth-child(4){left:0;bottom:0;border-right:0;border-top:0;border-radius:0 0 0 6px}
  .aig .tg.soft i{border-color:var(--c3);filter:drop-shadow(0 0 5px var(--c3))}
  .aig .tg .lbl{position:absolute;left:0;top:-26px;padding:2px 8px;border-radius:999px;background:var(--c1);color:#0a0d12;font-size:12px;font-weight:800;white-space:nowrap}
  .aig .thread{position:fixed;inset:0;width:100%;height:100%;overflow:visible}
  .aig .say{position:fixed;left:0;top:0;width:max-content;max-width:300px;color:#fff;pointer-events:none;
    font-size:16px;line-height:1.5;font-weight:800;letter-spacing:-.01em;
    text-shadow:0 0 1px #000,0 1px 2px rgba(0,0,0,.95),0 0 10px rgba(0,0,0,.85),0 0 22px rgba(0,0,0,.6);opacity:0;transition:opacity .25s;will-change:transform}
  .aig .say.on{opacity:1}
  .aig .say.ink-dark{color:#1d1612;text-shadow:0 0 1px #fff,0 0 6px rgba(255,255,255,.98),0 0 14px rgba(255,255,255,.9),0 0 24px rgba(255,255,255,.7)}
  .aig .say.ink-dark b{color:var(--ink-strong,#9a3d12)}
  .aig .say.ink-dark .k{color:var(--ink-strong,#9a3d12)}
  .aig .say.ink-dark button{color:var(--ink-strong,#9a3d12)}
  .aig .hud.ink-dark .row{color:#1d1612;text-shadow:0 0 1px #fff,0 0 6px rgba(255,255,255,.98),0 0 14px rgba(255,255,255,.85)}
  @property --aa{syntax:'<angle>';inherits:false;initial-value:0deg}
  @keyframes aig-spin{to{--aa:360deg}}
  .aig .say .t{display:block}
  .aig .say .k{display:flex;gap:8px;align-items:center;margin-bottom:2px;font-size:11.5px;font-weight:800;letter-spacing:.06em;color:var(--c1)}
  .aig .say .k em{font-style:normal;color:#c8d2dc;font-weight:700;letter-spacing:0}
  .aig .say b{color:var(--c1)}
  .aig .say .think{background:linear-gradient(90deg,rgba(255,255,255,.35),#fff,rgba(255,255,255,.35));background-size:200% 100%;-webkit-background-clip:text;background-clip:text;color:transparent;animation:aig-shim 1.1s linear infinite}
  @keyframes aig-shim{from{background-position:200% 0}to{background-position:-200% 0}}
  .aig .say .bar{display:flex;flex-wrap:wrap;gap:14px;margin-top:6px}
  .aig .say button{all:unset;pointer-events:auto;cursor:pointer;font-size:13.5px;font-weight:800;color:var(--c2);text-decoration:underline;text-underline-offset:4px;text-decoration-thickness:2px}
  .aig .say button.main{color:var(--c1)}
  .aig .say button:hover{color:#fff}
  .aig .spark{position:fixed;left:0;top:0;width:14px;height:2.5px;margin:-1px 0 0 0;border-radius:2px;transform-origin:0 50%}
  .aig .dot{position:fixed;width:8px;height:8px;margin:-4px 0 0 -4px;border-radius:50%;background:var(--c2);box-shadow:0 0 10px var(--c2);animation:aig-blink 1.8s ease-in-out infinite}
  @keyframes aig-blink{0%,100%{opacity:.25;transform:scale(.8)}50%{opacity:1;transform:scale(1.2)}}
  html.aig-nocur,html.aig-nocur *{cursor:none!important}
  html.aig-nocur input:not([type=button]):not([type=submit]):not([type=checkbox]):not([type=radio]),html.aig-nocur textarea,html.aig-nocur [contenteditable=true]{cursor:text!important}
  .aig .cdot{position:fixed;left:0;top:0;width:8px;height:8px;margin:-4px 0 0 -4px;border-radius:50%;background:#fff;box-shadow:0 0 0 2px var(--c1),0 0 14px 3px var(--c1);will-change:transform;transition:opacity .15s}
  .aig .cbr{position:fixed;left:0;top:0;width:10px;height:10px;will-change:transform}
  .aig .cbr i{position:absolute;width:11px;height:11px;border:2.5px solid #fff;filter:drop-shadow(0 0 3px rgba(0,0,0,.9)) drop-shadow(0 0 6px var(--c1))}
  .aig .cbr i:nth-child(1){left:0;top:0;border-right:0;border-bottom:0}
  .aig .cbr i:nth-child(2){right:0;top:0;border-left:0;border-bottom:0}
  .aig .cbr i:nth-child(3){right:0;bottom:0;border-left:0;border-top:0}
  .aig .cbr i:nth-child(4){left:0;bottom:0;border-right:0;border-top:0}
  .aig .cbr.lock i{border-color:var(--c1)}
  .aig .menu{position:fixed;right:18px;bottom:64px;display:flex;flex-direction:column;align-items:flex-end;gap:6px;pointer-events:none;
    text-shadow:0 0 1px #000,0 1px 3px rgba(0,0,0,.95),0 0 12px rgba(0,0,0,.8)}
  .aig .menu .q{color:#fff;font-size:15px;font-weight:800}
  .aig .menu button{all:unset;pointer-events:auto;cursor:pointer;color:var(--c1);font-size:17px;font-weight:900;text-decoration:underline;text-underline-offset:5px;text-decoration-thickness:2px}
  .aig .menu button small{display:block;text-align:right;color:#dfe6ee;font-size:11.5px;font-weight:700;text-decoration:none}
  .aig .menu button:hover{color:#fff}
  .aig .hud{position:fixed;right:20px;top:calc(var(--aig-top,14px) + 34px);display:flex;flex-direction:column;align-items:flex-end;gap:5px;pointer-events:none;transition:top .25s,opacity .2s}
  html.aig-menu-open .aig .hud{opacity:0}
  .aig .hud .row{display:flex;align-items:center;gap:8px;color:#fff;font-size:14px;font-weight:800;text-shadow:0 0 1px #000,0 1px 3px rgba(0,0,0,.95),0 0 10px rgba(0,0,0,.85),0 0 18px rgba(0,0,0,.6)}
  .aig .hud .crumb{max-width:520px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#fff}
  .aig .hud .meter{width:90px;height:4px;border-radius:9px;background:rgba(255,255,255,.25);overflow:hidden;box-shadow:0 0 6px rgba(0,0,0,.6)}
  .aig .hud .meter b{display:block;height:100%;background:linear-gradient(90deg,var(--c1),var(--c2));transition:width .5s}
  .aig .hud button{all:unset;pointer-events:auto;cursor:pointer;color:var(--c1);font-weight:900;text-decoration:underline;text-underline-offset:4px;text-decoration-thickness:2px}
  @media (prefers-reduced-motion:reduce){.aig .core::after,.aig .say::before,.aig .dot{animation:none}}
  `;
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const lerp = (a, b, t) => a + (b - a) * t;
  const rgba = (hex, a) => { const n = parseInt(hex.slice(1), 16); return `rgba(${n >> 16},${(n >> 8) & 255},${n & 255},${a})`; };

  export function createAiGuide(opt = {}) {
    const C = opt.accent || ['#b7e36a', '#5fd4c4', '#7aa2ff', '#f08ad0'];
    const NAME = opt.name || 'AI 안내';
    const explain = opt.explain || (() => null);           // (el) => { title, text } | null — 넓이·깊이 설명 사전
    const where = opt.where || (() => document.title);       // 지금 위치(빵 부스러기)
    const touch = typeof matchMedia === 'function' ? matchMedia('(pointer: coarse)').matches : false;
    const raf = typeof requestAnimationFrame === 'function' ? requestAnimationFrame : (f) => setTimeout(f, 16);
    if (!document.getElementById('aig-css')) { const s = document.createElement('style'); s.id = 'aig-css'; s.textContent = CSS; document.head.appendChild(s); }
    const root = document.createElement('div'); root.className = 'aig';
    root.style.cssText = `--ink-strong:${opt.inkStrong || '#7a3410'};--c1:${C[0]};--c2:${C[1]};--c3:${C[2]};--c4:${C[3]};--g1:${rgba(C[0], .55)};--g2:${rgba(C[2], .35)}`;
    root.innerHTML = `<canvas class="fl"></canvas><canvas class="fx"></canvas><div class="dim"></div>
      <svg class="thread"><defs><linearGradient id="aig-lg" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="${C[0]}"/><stop offset="1" stop-color="${C[2]}"/></linearGradient></defs><path fill="none" stroke="url(#aig-lg)" stroke-width="2.6" stroke-linecap="round" stroke-dasharray="2 9" opacity="0"/></svg>
      <div class="tg main"><i></i><i></i><i></i><i></i><span class="lbl"></span></div><div class="tg soft"><i></i><i></i><i></i><i></i></div>
      <div class="dots"></div><div class="core"></div><div class="say"></div><div class="cbr"><i></i><i></i><i></i><i></i></div><div class="cdot"></div>
      <div class="hud"><div class="row"><span class="crumb"></span></div><div class="row"><span class="prog"></span><span class="meter"><b style="width:0"></b></span><span class="cov"></span><button class="x">그만 보기</button></div></div>`;
    document.body.appendChild(root);
    const $ = (s) => root.querySelector(s);
    const cdot = $('.cdot'), cbr = $('.cbr'), core = $('.core'), say = $('.say'), tg = $('.tg.main'), tgSoft = $('.tg.soft'), lbl = $('.tg .lbl'), dim = $('.dim'), thread = $('.thread path'), dots = $('.dots');
    const EFFECT = opt.effect || 'splash';
    let fluid = null;
    try { if (EFFECT === 'splash' && window.AiFluid && !touch) fluid = window.AiFluid($('canvas.fl'), { colors: C, sim: opt.sim, dye: opt.dye, fade: opt.fade, bloom: opt.bloom, iter: opt.iter, calm: opt.calm, radius: opt.radius }); } catch (e) { fluid = null; }
    if (!fluid) $('canvas.fl').remove();
    const fx = makeFx($('canvas.fx'), EFFECT === 'splash' && fluid ? 'none' : EFFECT, C, opt);

    let mouse = { x: innerWidth * .6, y: innerHeight * .55 }, pos = { ...mouse }, vel = { x: 0, y: 0 };
    let mode = 'follow', perchAt = null, target = null, hoverEl = null, frozen = false, sayAt = null;
    const hole = { x: innerWidth / 2, y: innerHeight / 2, w: innerWidth, h: innerHeight };
    const box = { x: 0, y: 0, w: 10, h: 10 }, sbox = { x: 0, y: 0, w: 10, h: 10 };
    const seen = new Set(); let seenTotal = 0;
    const CUR = !touch && opt.cursor !== false;
    if (CUR) document.documentElement.classList.add('aig-nocur'); else { cdot.remove(); cbr.remove(); }
    if (CUR) core.style.display = 'none';
    const cb = { x: 0, y: 0, w: 30, h: 30, rot: 0 }; let press = 0;
    addEventListener('mousemove', (e) => { mouse = { x: e.clientX, y: e.clientY }; if (CUR) cdot.style.transform = `translate(${e.clientX}px,${e.clientY}px)`; }, { passive: true });
    addEventListener('mousedown', () => { press = 1; }, true); addEventListener('mouseup', () => { press = 0; }, true);
    const CLICK_SEL = 'a[href],button,[role=button],[role=tab],select,summary,label,input[type=button],input[type=submit],input[type=checkbox],input[type=radio],[onclick]' + (opt.clickSel ? ',' + opt.clickSel : '');
    const clickable = (el) => el && el.closest && el.closest(CLICK_SEL);
    const typeable = (el) => el && el.closest && el.closest('input:not([type=button]):not([type=submit]):not([type=checkbox]):not([type=radio]),textarea,[contenteditable=true]');
    addEventListener('pointerdown', (e) => { if (!e.target.closest('.aig')) spark(e.clientX, e.clientY); }, true);
    $('.hud .x').onclick = () => (opt.onQuit ? (opt.onQuit(), say.classList.remove('on')) : stop());

    const R = (el) => { const r = el.getBoundingClientRect(); return { x: r.left, y: r.top, w: r.width, h: r.height, cx: r.left + r.width / 2, cy: r.top + r.height / 2 }; };
    const perchPoint = (r) => ({ x: r.x + r.w + 26 < innerWidth - 20 ? r.x + r.w + 20 : r.x - 20, y: Math.max(28, r.y - 6) });

    function frame() {
      if (stopped) return;
      let goal;
      if (mode === 'follow') {
        const b = say.classList.contains('on') ? say.getBoundingClientRect() : null;
        goal = touch && target ? perchPoint(R(target)) : CUR ? { x: mouse.x, y: mouse.y } : { x: mouse.x + 26, y: mouse.y + 22 };
      } else goal = perchAt;
      const k = mode === 'fly' ? .1 : .17, d = mode === 'fly' ? .76 : .7;
      vel.x = (vel.x + (goal.x - pos.x) * k) * d; vel.y = (vel.y + (goal.y - pos.y) * k) * d;
      pos.x += vel.x; pos.y += vel.y;
      core.style.transform = `translate(${pos.x}px,${pos.y}px)`;
      if (fluid) fluid.move(pos.x, pos.y, mode === 'fly' ? 1.6 : 1);
      fx.step(pos.x, pos.y, mode === 'fly');
      // 꺾쇠: 지금 목적지(굵은 꺾쇠)와 마우스가 올라간 설명 대상(옅은 꺾쇠)
      const lock = (el, bx, node, pad) => {
        if (!el || !document.contains(el)) { node.classList.remove('on'); return; }
        const r = R(el), t = performance.now() / 1000, breathe = 3 + Math.sin(t * 3) * 2.5;
        bx.x = lerp(bx.x, r.x - pad - breathe, .25); bx.y = lerp(bx.y, r.y - pad - breathe, .25);
        bx.w = lerp(bx.w, r.w + (pad + breathe) * 2, .25); bx.h = lerp(bx.h, r.h + (pad + breathe) * 2, .25);
        node.style.transform = `translate(${bx.x}px,${bx.y}px)`; node.style.width = bx.w + 'px'; node.style.height = bx.h + 'px'; node.classList.add('on');
      };
      lock(target, box, tg, 7);
      lock(!CUR && hoverEl && hoverEl !== target ? hoverEl : null, sbox, tgSoft, 5); // 꺾쇠 커서가 이미 감싸면 옅은 꺾쇠는 겹쳐 띄우지 않는다
      if (target && document.contains(target)) {
        const r = R(target);
        hole.x = lerp(hole.x, r.cx, .12); hole.y = lerp(hole.y, r.cy, .12); hole.w = lerp(hole.w, r.w / 2 + 46, .12); hole.h = lerp(hole.h, r.h / 2 + 38, .12);
        dim.style.setProperty('--hole', `radial-gradient(ellipse ${hole.w}px ${hole.h}px at ${hole.x}px ${hole.y}px,transparent 92%,#000 100%)`);
        const far = Math.hypot(pos.x - r.cx, pos.y - r.cy);
        if (mode === 'follow' && !touch && far > 80) {
          thread.setAttribute('d', `M${pos.x},${pos.y} Q${(pos.x + r.cx) / 2},${Math.min(pos.y, r.cy) - 70} ${r.cx},${r.cy}`);
          thread.style.opacity = .9; thread.style.strokeDashoffset = (-performance.now() / 40) % 11;
        } else thread.style.opacity = 0;
      } else thread.style.opacity = 0;
      if (CUR) {
        const under = document.elementFromPoint(mouse.x, mouse.y);
        const over = under && !under.closest('.aig') ? clickable(under) : null;
        const typing = under && typeable(under);
        cdot.style.opacity = typing ? 0 : 1; cbr.style.opacity = typing ? 0 : 1;
        let gx, gy, gw, gh, grot;
        if (over && over.getBoundingClientRect().width < innerWidth * .7) { const r = R(over); gx = r.x - 5; gy = r.y - 5; gw = r.w + 10; gh = r.h + 10; grot = 0; cbr.classList.add('lock'); }
        else { const sz = 30 * (press ? .7 : 1); gx = mouse.x - sz / 2; gy = mouse.y - sz / 2; gw = sz; gh = sz; grot = (performance.now() / 12) % 360; cbr.classList.remove('lock'); }
        if (press && over) { gx += 3; gy += 3; gw -= 6; gh -= 6; }
        const k = .3; cb.x = lerp(cb.x, gx, k); cb.y = lerp(cb.y, gy, k); cb.w = lerp(cb.w, gw, k); cb.h = lerp(cb.h, gh, k);
        let dr = ((grot - cb.rot + 540) % 360) - 180; cb.rot = over ? lerp(cb.rot, 0 + Math.round(cb.rot / 90) * 90, .3) : grot;
        cbr.style.transform = `translate(${cb.x}px,${cb.y}px) rotate(${over ? 0 : cb.rot}deg)`; cbr.style.width = cb.w + 'px'; cbr.style.height = cb.h + 'px';
        cbr.style.transformOrigin = '50% 50%';
      }
      if (CUR && opt.magnify !== false) magnify();
      sayAt = target || hoverEl || sayAt;
      placeSay();
      raf(frame);
    }
    // 커서가 다가가면 가까운 버튼·메뉴 글씨가 거리만큼 커진다(배치는 밀리지 않게 scale만 쓴다)
    let cands = [], candAt = 0; const zs = new Map();
    function magnify() {
      const now = performance.now();
      if (now - candAt > 400) { candAt = now;
        cands = [...document.querySelectorAll('a[href],button,[role=tab],[role=button]')].filter((el) => { if (el.closest('.aig')) return false; const r = el.getBoundingClientRect(); return r.width > 0 && r.width < 420 && r.height < 120 && r.bottom > 0 && r.top < innerHeight; }); }
      const live = new Set();
      for (const el of cands) {
        const r = el.getBoundingClientRect();
        const dx = Math.max(r.left - mouse.x, 0, mouse.x - r.right), dy = Math.max(r.top - mouse.y, 0, mouse.y - r.bottom);
        const d = Math.hypot(dx, dy), f = Math.max(0, 1 - d / 110);
        const goal = 1 + (opt.zoom || .16) * f * f;
        const cur = zs.get(el) || 1, nx = cur + (goal - cur) * .25;
        if (Math.abs(nx - 1) < .002 && goal === 1) { if (zs.has(el)) { el.style.scale = ''; zs.delete(el); } continue; }
        zs.set(el, nx); el.style.scale = nx.toFixed(4); live.add(el);
      }
      for (const [el] of zs) if (!live.has(el) && !cands.includes(el)) { el.style.scale = ''; zs.delete(el); }
    }
    function placeSay() {
      if (!say.classList.contains('on')) return;
      const w = say.offsetWidth, h = say.offsetHeight;
      const anchor = (sayAt && document.contains(sayAt)) ? sayAt : null;
      let x, y;
      if (anchor) {
        const r = R(anchor);
        // 자리는 버튼이 바뀌거나 화면이 움직일 때만 다시 고른다(매 장면마다 고르면 무겁다)
        const key = anchor === slot.a && Math.abs(r.x - slot.rx) < 2 && Math.abs(r.y - slot.ry) < 2 && Math.abs(w - slot.w) < 4 && Math.abs(h - slot.h) < 4;
        if (!key || ++slot.n > 90) { const p = pickSlot(r, w, h); Object.assign(slot, { a: anchor, rx: r.x, ry: r.y, w, h, n: 0, x: p.x, y: p.y }); }
        x = slot.x; y = slot.y;
      } else { x = pos.x + 22; y = pos.y + 18; }
      x = Math.max(10, Math.min(innerWidth - w - 10, x)); y = Math.max(10, Math.min(innerHeight - h - 50, y));
      if (++inkTick % 20 === 0) { const dark = isLightAt(x + w / 2, y + h / 2); say.classList.toggle('ink-dark', dark); }
      sx = sx == null ? x : R2(sx, x); sy = sy == null ? y : R2(sy, y);
      say.style.transform = `translate(${sx}px,${sy}px)`;
    }
    let sx = null, sy = null, inkTick = 0; const R2 = (a, b) => a + (b - a) * .25;
    const slot = { a: null, n: 0 };
    // 그 자리에 화면 글자·그림·입력칸이 있는지(설명 글이 겹치면 안 된다)
    function contentAt(px, py) {
      if (px < 4 || py < 4 || px > innerWidth - 4 || py > innerHeight - 4) return 3;
      if (!document.elementsFromPoint) return 0;
      for (const e of document.elementsFromPoint(px, py)) {
        if (e.closest && e.closest('.aig,.aig-launch-wrap')) continue;
        if (e === document.body || e === document.documentElement) return 0;
        if (/^(IMG|CANVAS|SVG|VIDEO|INPUT|TEXTAREA|BUTTON|SELECT|PATH|CIRCLE|RECT|TEXT)$/i.test(e.tagName)) return 1;
        for (const n of e.childNodes) if (n.nodeType === 3 && n.textContent.trim()) return 1;
      }
      return 0;
    }
    function crowd(x, y, w, h) { let n = 0; for (let i = 0; i < 8; i++) for (let j = 0; j < 4; j++) n += contentAt(x + w * (i + .5) / 8, y + h * (j + .5) / 4); return n; }
    // 버튼 오른쪽·아래·위·왼쪽·오른쪽 위(위치 표시 아래) 가운데 글자가 가장 적은 빈자리를 고른다
    function pickSlot(r, w, h) {
      const hb = hudEl.getBoundingClientRect();
      const dockY = (hb.height ? hb.bottom : (parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--aig-top')) || 14) + 40) + 14;
      const fit = (p) => ({ x: Math.max(10, Math.min(innerWidth - w - 10, p.x)), y: Math.max(10, Math.min(innerHeight - h - 50, p.y)) });
      const c = [
        { x: r.x + r.w + 18, y: r.cy - h / 2 }, { x: r.x, y: r.y + r.h + 14 }, { x: r.x, y: r.y - 14 - h }, { x: r.x - 18 - w, y: r.cy - h / 2 },
        { x: r.x + r.w - w, y: r.y + r.h + 14 }, { x: r.x + r.w - w, y: r.y - 14 - h }, { x: innerWidth - w - 20, y: dockY },
      ].map(fit);
      let best = null;
      c.forEach((p, i) => {
        const hitsAnchor = p.x < r.x + r.w + 6 && p.x + w > r.x - 6 && p.y < r.y + r.h + 6 && p.y + h > r.y - 34; // 꺾쇠 위 이름표 자리까지 비운다
        const score = crowd(p.x, p.y, w, h) * 10 + (hitsAnchor ? 500 : 0) + i; // 같으면 버튼 가까운 자리부터
        if (!best || score < best.score) best = { ...p, score };
      });
      return best;
    }
    const hudEl = $('.hud');
    // 글씨 뒤 바탕이 밝으면 진한 글씨 + 흰 빛, 어두우면 흰 글씨 + 검은 그림자
    function isLightAt(x, y) {
      if (!document.elementsFromPoint) return false;
      for (const el of document.elementsFromPoint(Math.max(0, x), Math.max(0, y))) {
        if (el.closest && el.closest('.aig')) continue;
        const m = getComputedStyle(el).backgroundColor.match(/[\d.]+/g);
        if (!m) continue; const a = m[3] === undefined ? 1 : +m[3]; if (a < .5) continue;
        return (0.299 * m[0] + 0.587 * m[1] + 0.114 * m[2]) / 255 > .62;
      }
      return false;
    }
    // Click Spark — 누르는 자리에서 빛살 여덟 줄이 튄다
    function spark(x, y, big) {
      const n = big ? 12 : 8;
      for (let i = 0; i < n; i++) {
        const s = document.createElement('div'); s.className = 'spark'; const a = (i / n) * 360, c = C[i % 4];
        s.style.background = c; s.style.boxShadow = `0 0 8px ${c}`; root.appendChild(s);
        const d0 = big ? 14 : 8, d1 = big ? 70 : 38;
        s.animate([{ transform: `translate(${x}px,${y}px) rotate(${a}deg) translateX(${d0}px) scaleX(1)`, opacity: 1 }, { transform: `translate(${x}px,${y}px) rotate(${a}deg) translateX(${d1}px) scaleX(.1)`, opacity: 0 }],
          { duration: big ? 650 : 420, easing: 'cubic-bezier(.2,.8,.3,1)' }).onfinish = () => s.remove();
      }
      if (fluid && big) fluid.burst(x, y, 12);
      if (big) fx.burst(x, y);
    }

    let typing = 0;
    let sayKind = 'info';
    async function speak(text, { kicker = NAME, sub = '', buttons = [] } = {}) {
      const id = ++typing; sayKind = buttons.length ? 'ask' : 'info';
      say.innerHTML = `<span class="k">${kicker}${sub ? `<em>${sub}</em>` : ''}</span><span class="t"><span class="think">생각하는 중…</span></span>` + (buttons.length ? `<div class="bar">${buttons.map((b, i) => `<button data-i="${i}" class="${b.main ? 'main' : ''}">${b.label}</button>`).join('')}</div>` : '');
      say.classList.add('on');
      return new Promise(async (res) => {
        say.querySelectorAll('button').forEach((b) => b.onclick = (e) => { e.stopPropagation(); typing++; sayKind = 'info'; res(buttons[+b.dataset.i].value); });
        await sleep(420); if (id !== typing) return;
        const t = say.querySelector('.t'); const plain = text.replace(/<[^>]+>/g, '');
        for (let i = 1; i <= plain.length; i += 2) { if (id !== typing) return; t.textContent = plain.slice(0, i); await sleep(14); }
        t.innerHTML = text; if (!buttons.length) res();
      });
    }

    async function flyTo(el) {
      const v = el.getBoundingClientRect();
      if (v.top < 70 || v.bottom > innerHeight - 90) { el.scrollIntoView({ block: 'center', behavior: 'smooth' }); await sleep(650); }
      mode = 'fly'; perchAt = perchPoint(R(el)); await sleep(650); mode = 'perch';
    }
    async function waitFor(fn, ms = 15000) { const t0 = performance.now(); while (performance.now() - t0 < ms) { const el = typeof fn === 'string' ? document.querySelector(fn) : fn(); if (el) return el; await sleep(150); } return null; }

    // 넓이: 마우스가 설명할 수 있는 버튼에 머물면 옅은 꺾쇠로 잠그고 커서 옆에서 설명한다
    let hoverTimer = 0, hoverBusy = false, lastHover = null, hideT = 0;
    addEventListener('mouseover', (e) => {
      if (stopped || e.target.closest('.aig')) return;
      // 설명은 누를 수 있는 버튼·링크에만 붙인다(제목·본문 글자에 올렸을 때는 띄우지 않는다)
      let el = clickable(e.target), info = null;
      while (el && el !== document.body && !(info = explain(el))) { const up = el.parentElement; el = up ? clickable(up) : null; }
      clearTimeout(hoverTimer);
      if (!info) { hoverEl = null; clearTimeout(hideT); if (!target) hideT = setTimeout(() => { if (!hoverEl && !target && !say.querySelector('button')) say.classList.remove('on'); }, 2200); return; }
      hoverTimer = setTimeout(() => sayHover(el, info), 320);
    }, true);
    // 마우스가 다른 버튼으로 옮겨 가면, 앞 설명이 다 써지기 전이라도 지금 버튼 설명으로 바꾼다(엉뚱한 설명이 남지 않게)
    // 안내 중 「다음」·「데려다 드릴게요」 같은 버튼이 떠 있으면 그 글은 덮지 않는다(버튼이 사라져 안내가 멈추지 않게)
    function sayHover(el, info) {
      hoverEl = el;
      if (mode !== 'follow' || el === lastHover || el === target) return;
      if (say.classList.contains('on') && sayKind === 'ask') return;
      lastHover = el; markSeen(el);
      speak(`<b>${info.title}</b> — ${info.text}`, { kicker: '이건 뭐예요?', sub: target ? '빛나는 곳은 아직 그대로예요' : '' });
    }
    function markSeen(el) { const k = (explain(el) || {}).title; if (k && !seen.has(k)) { seen.add(k); refreshDots(); } }
    let dotsFor = null;
    function refreshDots() {
      dots.innerHTML = ''; const list = dotsFor ? dotsFor() : [];
      seenTotal = 0;
      list.forEach((el) => { const info = explain(el); if (!info) return; if (seen.has(info.title)) { seenTotal++; return; } const r = R(el); const d = document.createElement('div'); d.className = 'dot'; d.style.left = (r.x + r.w - 4) + 'px'; d.style.top = (r.y + 4) + 'px'; dots.appendChild(d); });
      $('.cov').textContent = list.length ? `이 화면 버튼 ${seenTotal}/${list.length}` : '';
      $('.meter b').style.width = list.length ? `${Math.round((seenTotal / list.length) * 100)}%` : '0';
    }
    // 위쪽 메뉴줄 바로 아래를 기준선으로 삼는다(스크롤로 메뉴줄이 사라지면 화면 맨 위). 「길 안내」·위치 표시가 여기 붙는다.
    function placeTop() {
      let top = 14;
      for (const el of document.querySelectorAll(opt.topBar || 'header')) {
        if (el.closest('.aig')) continue;
        const r = el.getBoundingClientRect();
        if (r.height > 0 && r.top < 60 && r.bottom > 0 && r.right > innerWidth - 320) { top = Math.max(top, Math.min(r.bottom + 8, 220)); break; }
      }
      document.documentElement.style.setProperty('--aig-top', Math.round(top) + 'px');
    }
    placeTop(); addEventListener('scroll', placeTop, { passive: true }); addEventListener('resize', placeTop);
    setInterval(() => { if (!stopped) { placeTop(); { const hr = hudEl.getBoundingClientRect(); if (hr.width) hudEl.classList.toggle('ink-dark', isLightAt(hr.left + hr.width / 2, hr.top + hr.height / 2)); } const show = opt.hud ? opt.hud() : true; $('.hud').style.display = show ? '' : 'none'; if (!show) { dots.innerHTML = ''; return; } refreshDots(); $('.crumb').textContent = '지금 위치 · ' + where(); } }, 900);

    // 한 걸음: 목적지를 짚고, 사용자가 누르거나 「AI가 직접 해 보기」를 고를 때까지 같이 다닌다
    async function pointStep(st, label) {
      const el = await waitFor(st.target); if (!el) return false;
      target = el; dim.classList.add('on'); lbl.textContent = st.label || '여기!';
      await flyTo(el);
      let resolveStep; const done = new Promise((r) => resolveStep = r);
      const finish = () => { if (target !== el) return; removeEventListener('pointerdown', onDown, true); const r = R(el); spark(r.cx, r.cy, true); collect(el); target = null; dim.classList.remove('on'); resolveStep(); };
      const showMe = async () => {
        const r = R(el); const ghost = document.createElement('div');
        ghost.style.cssText = `position:fixed;left:0;top:0;width:28px;height:28px;filter:drop-shadow(0 2px 6px rgba(0,0,0,.45)) drop-shadow(0 0 12px ${C[0]})`;
        ghost.innerHTML = `<svg viewBox="0 0 24 24" width="28" height="28"><path d="M3 2l7 19 2.6-7.4L20 11z" fill="${C[0]}" stroke="#0b0f14" stroke-width="1.4" stroke-linejoin="round"/></svg>`;
        root.appendChild(ghost); mode = 'fly'; perchAt = { x: r.cx + 26, y: r.cy + 20 };
        const from = { x: pos.x, y: pos.y }, to = { x: r.cx - 3, y: r.cy - 2 };
        await ghost.animate([{ transform: `translate(${from.x}px,${from.y}px)` }, { transform: `translate(${(from.x + to.x) / 2}px,${Math.min(from.y, to.y) - 90}px)`, offset: .5 }, { transform: `translate(${to.x}px,${to.y}px)` }], { duration: 1050, easing: 'cubic-bezier(.45,.05,.25,1)', fill: 'forwards' }).finished;
        await ghost.animate([{ transform: `translate(${to.x}px,${to.y}px) scale(1)` }, { transform: `translate(${to.x}px,${to.y}px) scale(.78)` }, { transform: `translate(${to.x}px,${to.y}px) scale(1)` }], { duration: 240, fill: 'forwards' }).finished;
        finish(); (st.act || ((e) => e.click()))(el); mode = 'follow';
        setTimeout(() => ghost.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 400, fill: 'forwards' }).onfinish = () => ghost.remove(), 500);
      };
      const ask = (text) => speak(text, { kicker: `${NAME} · ${label}`, buttons: [{ label: '▶ AI가 직접 해 보기', value: 'show' }] }).then((v) => { if (v === 'show') showMe(); });
      const onDown = async (e) => {
        if (e.target.closest('.aig')) return;
        if (el.contains(e.target)) { finish(); return; }
        ask('여기가 아니에요. <b>꺾쇠가 잠긴 곳</b>이에요! 어려우면 제가 해 볼게요.'); await flyTo(el); await sleep(700); if (target === el) mode = 'follow';
      };
      addEventListener('pointerdown', onDown, true);
      if (st.look) { speak(st.say, { kicker: `${NAME} · ${label}`, buttons: [{ label: '다음 ▶', value: 1, main: true }] }).then(() => finish()); }
      else ask(st.say);
      if (typeof st.fill === 'function') st.fill();
      await sleep(1100); if (target === el) mode = 'follow';
      await done;
      if (st.react || st.after) {
        await sleep(st.wait || 1300);
        const msg = (st.react && (await waitFor(st.react, 12000))) || st.after;
        const near = st.result ? await waitFor(st.result, 4000) : null;
        if (near) { hoverEl = near; mode = 'fly'; perchAt = perchPoint(R(near)); await sleep(650); mode = 'perch'; }
        if (msg) await speak(msg, { kicker: `${NAME} · 결과`, buttons: [{ label: '다음', value: 1, main: true }] });
        hoverEl = null; mode = 'follow';
      }
      return true;
    }

    // 넓이 훑기: 화면의 버튼을 하나씩 꺾쇠로 짚으며 설명. 사용자는 아무거나 눌러 깊이 들어갈 수 있다
    async function sweepStep(st, label) {
      await waitFor(() => (st.items() || [])[0]);
      const items = st.items().filter((el) => explain(el));
      dotsFor = st.items; refreshDots();
      await speak(st.say || `이 화면에는 버튼이 <b>${items.length}개</b> 있어요. 하나씩 짚어 볼게요.`, { kicker: `${NAME} · ${label}`, sub: '넓이' });
      await sleep(900);
      let stopSweep = false, picked = null;
      const onPick = (e) => { const hit = items.find((el) => el.contains(e.target)); if (hit) { picked = hit; stopSweep = true; } };
      addEventListener('pointerdown', onPick, true);
      for (let i = 0; i < items.length && !stopSweep && !stopped; i++) {
        const el = items[i], info = explain(el);
        target = el; lbl.textContent = `${i + 1}/${items.length}`; dim.classList.add('on');
        await flyTo(el);
        markSeen(el);
        const v = await Promise.race([speak(`<b>${info.title}</b> — ${info.text}`, { kicker: `${i + 1} / ${items.length}`, sub: st.title || '', buttons: [{ label: '다음 ▶', value: 'next', main: true }, { label: '이거 눌러 보기', value: 'go' }, { label: '그만 훑기', value: 'stop' }] }), sleep(st.dwell || 5200).then(() => 'next')]);
        if (v === 'go') { picked = el; stopSweep = true; }
        if (v === 'stop') stopSweep = true;
      }
      removeEventListener('pointerdown', onPick, true);
      target = null; dim.classList.remove('on'); mode = 'follow';
      if (picked && !picked.isConnected) return picked;
      if (picked) { spark(R(picked).cx, R(picked).cy, true); collect(picked); if (!st.noClickOnPick) picked.click(); }
      return picked;
    }

    function collect(el) {
      const r = R(el), b = $('.hud .row:last-child').getBoundingClientRect();
      const s = document.createElement('div'); s.style.cssText = `position:fixed;left:0;top:0;width:14px;height:14px;margin:-7px 0 0 -7px;border-radius:50%;background:${C[0]};box-shadow:0 0 18px 4px ${C[0]}`;
      root.appendChild(s);
      s.animate([{ transform: `translate(${r.cx}px,${r.cy}px) scale(1.4)` }, { transform: `translate(${(r.cx + b.left) / 2}px,${Math.min(r.cy, b.top) - 120}px) scale(1)`, offset: .5 }, { transform: `translate(${b.left + 30}px,${b.top + b.height / 2}px) scale(.5)` }],
        { duration: 900, easing: 'cubic-bezier(.5,0,.3,1)' }).onfinish = () => { s.remove(); $('.hud').animate([{ transform: 'scale(1)' }, { transform: 'scale(1.08)' }, { transform: 'scale(1)' }], { duration: 360 }); };
    }

    let stopped = false;
    function stop() { stopped = true; root.remove(); document.documentElement.classList.remove('aig-nocur'); for (const [el] of zs) el.style.scale = ''; }

    async function run(steps) {
      for (let i = 0; i < steps.length && !stopped; i++) {
        const st = steps[i];
        $('.prog').textContent = `${i + 1} / ${steps.length}`;
        if (st.sweep) await sweepStep(st, `${i + 1}단계`); else await pointStep(st, `${i + 1}단계`);
        if (st.then) await st.then();
      }
      if (!stopped) speak(opt.outro || '여기까지 길 안내였어요. 이제 아무 버튼에나 마우스를 올려 보세요. 무엇인지 바로 알려 드려요.', { kicker: NAME });
    }
    function menu(q, options) {
      return new Promise((res) => {
        const m = document.createElement('div'); m.className = 'menu';
        m.innerHTML = `<span class="q">${q}</span>` + options.map((o, i) => `<button data-i="${i}">${o.label}<small>${o.sub || ''}</small></button>`).join('') + `<button data-i="-1" style="font-size:12.5px;color:#cfd8e2">나중에 볼게요</button>`;
        root.appendChild(m);
        m.querySelectorAll('button').forEach((b) => b.onclick = (e) => { e.stopPropagation(); m.remove(); res(+b.dataset.i < 0 ? null : options[+b.dataset.i].value); });
      });
    }
    raf(frame);
    return { run, menu, point: pointStep, sweep: sweepStep, explainEl: (el) => explain(el), speak, stop, spark, setDots: (fn) => { dotsFor = fn; } };
  }

  // 프로젝트마다 다른 따라다님 효과(2D 캔버스): glow 빛줄기 · ribbons 오방색 리본 · text 글자 꼬리 · pixel 픽셀 꼬리
  function makeFx(cv, kind, C, opt) {
    let ctx = null; try { ctx = cv.getContext && cv.getContext('2d'); } catch (e) { ctx = null; }
    if (!ctx) return { step() {}, burst() {} };
    let W = 0, H = 0, dpr = Math.min(2, (typeof devicePixelRatio === 'number' && devicePixelRatio) || 1);
    const size = () => { W = innerWidth; H = innerHeight; cv.width = W * dpr; cv.height = H * dpr; ctx.setTransform(dpr, 0, 0, dpr, 0, 0); };
    size(); addEventListener('resize', size);
    const hist = []; const parts = []; let lastDrop = null, t = 0;
    const ribbons = (opt.ribbonColors || C).map((c, i) => ({ c, pts: [], k: .32 - i * .045 }));
    const glyphs = opt.glyphs || 'ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎㅏㅑㅓㅕㅗㅛㅜㅠabcdefghijklmnopqrstuvwxyz'.split('');
    const cell = opt.pixel || 14;
    function step(x, y, flying) {
      t++; if (kind === 'none') return;
      ctx.clearRect(0, 0, W, H);
      hist.unshift({ x, y }); if (hist.length > (opt.trail || 34)) hist.pop();
      if (kind === 'glow') {
        ctx.globalCompositeOperation = 'lighter';
        for (let pass = 0; pass < 2; pass++) {
          ctx.beginPath();
          for (let i = 0; i < hist.length - 1; i++) {
            const a = hist[i], b = hist[i + 1];
            const wob = Math.sin(t * .09 + i * .5) * (4 + i * .35);
            const nx = -(b.y - a.y), ny = b.x - a.x, nl = Math.hypot(nx, ny) || 1;
            const px = a.x + nx / nl * wob, py = a.y + ny / nl * wob;
            i ? ctx.lineTo(px, py) : ctx.moveTo(px, py);
          }
          ctx.strokeStyle = pass ? '#fff' : C[0]; ctx.lineWidth = (pass ? 2 : 9) * (opt.calm ? .6 : 1); ctx.lineCap = 'round'; ctx.lineJoin = 'round';
          ctx.shadowColor = C[pass ? 1 : 0]; ctx.shadowBlur = pass ? 10 : 28; ctx.globalAlpha = pass ? .9 : .55; ctx.stroke();
        }
        // 커서를 감싸 도는 작은 빛
        for (let k = 0; k < 3; k++) { const a = t * .08 + k * 2.1, r = 22 + Math.sin(t * .05 + k) * 5; ctx.beginPath(); ctx.arc(x + Math.cos(a) * r, y + Math.sin(a) * r, 3, 0, 7); ctx.fillStyle = C[k % 4]; ctx.shadowBlur = 16; ctx.shadowColor = C[k % 4]; ctx.globalAlpha = .9; ctx.fill(); }
        ctx.globalAlpha = 1; ctx.shadowBlur = 0; ctx.globalCompositeOperation = 'source-over';
      } else if (kind === 'ribbons') {
        ribbons.forEach((rb, i) => {
          const head = rb.pts[0] || { x, y, vx: 0, vy: 0 };
          const nh = { x: head.x + (x - head.x) * rb.k + Math.sin(t * .07 + i) * 1.5, y: head.y + (y - head.y) * rb.k + Math.cos(t * .06 + i) * 1.5 };
          rb.pts.unshift(nh); if (rb.pts.length > (opt.calm ? 15 : 26)) rb.pts.pop();
          if (rb.pts.length < 3) return;
          ctx.beginPath(); ctx.moveTo(rb.pts[0].x, rb.pts[0].y);
          for (let j = 1; j < rb.pts.length - 1; j++) { const m = { x: (rb.pts[j].x + rb.pts[j + 1].x) / 2, y: (rb.pts[j].y + rb.pts[j + 1].y) / 2 }; ctx.quadraticCurveTo(rb.pts[j].x, rb.pts[j].y, m.x, m.y); }
          ctx.strokeStyle = rb.c; ctx.lineCap = 'round'; ctx.lineJoin = 'round'; ctx.globalAlpha = .9;
          ctx.lineWidth = (10 - i * 1.3) * (opt.calm ? .55 : 1); ctx.shadowColor = rb.c; ctx.shadowBlur = opt.calm ? 8 : 12; ctx.stroke();
        });
        ctx.globalAlpha = 1; ctx.shadowBlur = 0;
      } else if (kind === 'text') {
        if (!lastDrop || Math.hypot(x - lastDrop.x, y - lastDrop.y) > (flying ? 16 : 22)) {
          lastDrop = { x, y };
          parts.push({ x: x + (Math.random() - .5) * 10, y: y + (Math.random() - .5) * 10, vx: (Math.random() - .5) * .8, vy: -.4 - Math.random() * .6, life: 1, ch: glyphs[Math.floor(Math.random() * glyphs.length)], c: C[Math.floor(Math.random() * 4)], s: 15 + Math.random() * 12, r: (Math.random() - .5) * .6 });
        }
        for (let i = parts.length - 1; i >= 0; i--) { const p = parts[i]; p.x += p.vx; p.y += p.vy; p.vy += .035; p.life -= .016; if (p.life <= 0) { parts.splice(i, 1); continue; }
          ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.r * (1 - p.life)); ctx.globalAlpha = Math.min(1, p.life * 1.4); ctx.fillStyle = p.c; ctx.shadowColor = p.c; ctx.shadowBlur = 10;
          ctx.font = `800 ${p.s}px "Pretendard","Malgun Gothic",monospace`; ctx.fillText(p.ch, -p.s / 3, p.s / 3); ctx.restore(); }
        // 깜박이는 타자 커서
        if (Math.floor(t / 22) % 2) { ctx.fillStyle = C[0]; ctx.shadowColor = C[0]; ctx.shadowBlur = 12; ctx.fillRect(x + 12, y - 11, 3, 22); ctx.shadowBlur = 0; }
      } else if (kind === 'pixel') {
        const gx = Math.floor(x / cell), gy = Math.floor(y / cell);
        for (let k = 0; k < (flying ? 3 : 2); k++) { const ox = gx + Math.round((Math.random() - .5) * 3), oy = gy + Math.round((Math.random() - .5) * 3); parts.push({ gx: ox, gy: oy, life: 1, c: C[Math.floor(Math.random() * 4)], scr: Math.random() < .35 }); }
        for (let i = parts.length - 1; i >= 0; i--) { const p = parts[i]; p.life -= .02; if (p.life <= 0) { parts.splice(i, 1); continue; }
          // 처음엔 흩어진(깨진) 픽셀이 흔들리다 제자리로 모이며 사라진다
          const jx = p.scr ? (Math.random() - .5) * 8 * p.life : 0, jy = p.scr ? (Math.random() - .5) * 8 * p.life : 0;
          ctx.globalAlpha = p.life; ctx.fillStyle = p.c; ctx.shadowColor = p.c; ctx.shadowBlur = 8;
          ctx.fillRect(p.gx * cell + 1 + jx, p.gy * cell + 1 + jy, cell - 2, cell - 2); }
        ctx.globalAlpha = 1; ctx.shadowBlur = 0;
      }
    }
    function burst(x, y) {
      if (kind === 'text') for (let i = 0; i < 14; i++) { const a = i / 14 * 6.28; parts.push({ x, y, vx: Math.cos(a) * 3, vy: Math.sin(a) * 3 - 1, life: 1, ch: glyphs[Math.floor(Math.random() * glyphs.length)], c: C[i % 4], s: 18, r: .8 }); }
      if (kind === 'pixel') for (let i = 0; i < 40; i++) parts.push({ gx: Math.floor(x / cell) + Math.round((Math.random() - .5) * 8), gy: Math.floor(y / cell) + Math.round((Math.random() - .5) * 6), life: 1, c: C[i % 4], scr: true });
    }
    return { step, burst };
  }
