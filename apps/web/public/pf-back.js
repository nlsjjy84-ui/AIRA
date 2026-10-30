/* \uD3EC\uD2B8\uD3F4\uB9AC\uC624\uB85C \uB3CC\uC544\uAC00\uAE30 \u2014 \uB370\uBAA8 \uC5B4\uB290 \uD654\uBA74\uC5D0\uC11C\uB4E0 \uC7A5\uC8FC\uC601 \uD3EC\uD2B8\uD3F4\uB9AC\uC624(\uB178\uC158) \uCCAB \uD654\uBA74\uC73C\uB85C \uB3CC\uC544\uAC04\uB2E4.
   \uCC98\uC74C \uB4E4\uC5B4\uC654\uC744 \uB54C \uBA87 \uCD08\uB9CC \u300C\u2190 \uD3EC\uD2B8\uD3F4\uB9AC\uC624\uB85C\u300D \uAE00\uC790\uB97C \uBCF4\uC5EC \uC8FC\uACE0, \uADF8 \uB4A4\uC5D0\uB294 \uC67C\uCABD \uAC00\uC7A5\uC790\uB9AC\uC758
   \uC791\uC740 \uD0ED(\u2190)\uC73C\uB85C \uC811\uD600 \uD654\uBA74 \uB0B4\uC6A9\uC744 \uAC00\uB9AC\uC9C0 \uC54A\uB294\uB2E4. \uB9C8\uC6B0\uC2A4\uB97C \uC62C\uB9AC\uAC70\uB098 \uD0A4\uBCF4\uB4DC\uB85C \uACE0\uB974\uBA74 \uB2E4\uC2DC \uD3BC\uCCD0\uC9C4\uB2E4. */
(function () {
  if (window.top !== window.self) return; // \uB2E4\uB978 \uD654\uBA74 \uC548\uC5D0 \uB07C\uC6CC\uC9C4 \uACBD\uC6B0(\uBBF8\uB9AC\uBCF4\uAE30 \uB4F1)\uC5D0\uB294 \uB744\uC6B0\uC9C0 \uC54A\uB294\uB2E4
  if (navigator.webdriver) return; // \uC790\uB3D9 \uD14C\uC2A4\uD2B8(\uBE0C\uB77C\uC6B0\uC800 \uD14C\uC2A4\uD2B8)\uC5D0\uC11C\uB294 \uB744\uC6B0\uC9C0 \uC54A\uB294\uB2E4 \u2014 \uBC84\uD2BC\uC774 \uB2E4\uB978 \uB2E8\uCD94\uB97C \uAC00\uB9AC\uC9C0 \uC54A\uAC8C
  var HOME = 'https://coconut-corn-893.notion.site/3eaec1836c2081eb90e8d35c1c1eaf16';
  var css = '' +
    '#pf-back{position:fixed;left:0;bottom:calc(92px + env(safe-area-inset-bottom,0px));z-index:2147483000;' +
    'display:flex;align-items:center;gap:0;padding:9px 12px 9px max(10px,env(safe-area-inset-left,0px));' +
    'font:500 13px/1 Pretendard,"Noto Sans KR",system-ui,sans-serif;letter-spacing:-.01em;color:#e9ecf2;' +
    'background:rgba(14,16,20,.82);border:1px solid rgba(255,255,255,.16);border-left:0;border-radius:0 999px 999px 0;' +
    'text-decoration:none;box-shadow:0 6px 18px rgba(0,0,0,.18);-webkit-backdrop-filter:blur(6px);backdrop-filter:blur(6px);' +
    'transition:padding .35s ease,opacity .35s ease;opacity:.92;-webkit-tap-highlight-color:transparent}' +
    '#pf-back .pf-a{font-size:14px;margin-right:7px;transition:margin .35s ease}' +
    '#pf-back .pf-t{display:inline-block;max-width:120px;overflow:hidden;white-space:nowrap;transition:max-width .35s ease,opacity .3s ease}' +
    '#pf-back.pf-small{padding:9px 7px 9px max(8px,env(safe-area-inset-left,0px));gap:0;opacity:.75}' +
    '#pf-back.pf-small .pf-a{margin-right:0}' +
    '#pf-back.pf-small:hover .pf-a,#pf-back.pf-small:focus-visible .pf-a{margin-right:7px}' +
    '#pf-back.pf-small .pf-t{max-width:0;opacity:0}' +
    '#pf-back.pf-small:hover,#pf-back.pf-small:focus-visible{padding-right:12px;opacity:1}' +
    '#pf-back.pf-small:hover .pf-t,#pf-back.pf-small:focus-visible .pf-t{max-width:120px;opacity:1}' +
    '#pf-back:focus-visible{outline:2px solid #ffd76a;outline-offset:2px}' +
    '@media print{#pf-back{display:none}}' +
    '@media (prefers-reduced-motion:reduce){#pf-back,#pf-back .pf-t{transition:none}}';
  function seen() {
    try { if (sessionStorage.getItem('pf-back-seen')) return true; sessionStorage.setItem('pf-back-seen', '1'); } catch (e) {}
    return false;
  }
  function mount() {
    if (document.getElementById('pf-back')) return;
    var st = document.createElement('style');
    st.id = 'pf-back-style';
    st.textContent = css;
    document.head.appendChild(st);
    var a = document.createElement('a');
    a.id = 'pf-back';
    a.href = HOME;
    a.title = '\uC7A5\uC8FC\uC601 \uD3EC\uD2B8\uD3F4\uB9AC\uC624\uB85C \uB3CC\uC544\uAC00\uAE30';
    a.setAttribute('aria-label', '\uC7A5\uC8FC\uC601 \uD3EC\uD2B8\uD3F4\uB9AC\uC624\uB85C \uB3CC\uC544\uAC00\uAE30');
    a.innerHTML = '<span class="pf-a" aria-hidden="true">\u2190</span><span class="pf-t">\uD3EC\uD2B8\uD3F4\uB9AC\uC624\uB85C</span>';
    if (seen()) a.classList.add('pf-small');
    else setTimeout(function () { a.classList.add('pf-small'); }, 4500);
    document.body.appendChild(a);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount);
  else mount();
})();
