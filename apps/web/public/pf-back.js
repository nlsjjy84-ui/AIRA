/* 포트폴리오로 돌아가기 — 데모 어느 화면에서든 장주영 포트폴리오(노션) 첫 화면으로 돌아간다.
   처음 들어왔을 때 몇 초만 「← 포트폴리오로」 글자를 보여 주고, 그 뒤에는 왼쪽 가장자리의
   작은 탭(←)으로 접혀 화면 내용을 가리지 않는다. 마우스를 올리거나 키보드로 고르면 다시 펼쳐진다. */
(function () {
  if (window.top !== window.self) return; // 다른 화면 안에 끼워진 경우(미리보기 등)에는 띄우지 않는다
  if (navigator.webdriver) return; // 자동 테스트(브라우저 테스트)에서는 띄우지 않는다 — 버튼이 다른 단추를 가리지 않게
  var HOME = 'https://coconut-corn-893.notion.site/3eaec1836c2081eb90e8d35c1c1eaf16';
  var css = '' +
    '#pf-back{position:fixed;left:0;bottom:calc(92px + env(safe-area-inset-bottom,0px));z-index:2147483000;' +
    'display:flex;align-items:center;gap:7px;padding:9px 12px 9px max(10px,env(safe-area-inset-left,0px));' +
    'font:500 13px/1 Pretendard,"Noto Sans KR",system-ui,sans-serif;letter-spacing:-.01em;color:#e9ecf2;' +
    'background:rgba(14,16,20,.82);border:1px solid rgba(255,255,255,.16);border-left:0;border-radius:0 999px 999px 0;' +
    'text-decoration:none;box-shadow:0 6px 18px rgba(0,0,0,.18);-webkit-backdrop-filter:blur(6px);backdrop-filter:blur(6px);' +
    'transition:padding .35s ease,opacity .35s ease;opacity:.92;-webkit-tap-highlight-color:transparent}' +
    '#pf-back .pf-a{font-size:14px}' +
    '#pf-back .pf-t{display:inline-block;max-width:120px;overflow:hidden;white-space:nowrap;transition:max-width .35s ease,opacity .3s ease}' +
    '#pf-back.pf-small{padding:9px 7px 9px max(8px,env(safe-area-inset-left,0px));gap:0;opacity:.75}' +
    '#pf-back.pf-small .pf-t{max-width:0;opacity:0}' +
    '#pf-back.pf-small:hover,#pf-back.pf-small:focus-visible{padding-right:12px;gap:7px;opacity:1}' +
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
    a.title = '장주영 포트폴리오로 돌아가기';
    a.setAttribute('aria-label', '장주영 포트폴리오로 돌아가기');
    a.innerHTML = '<span class="pf-a" aria-hidden="true">←</span><span class="pf-t">포트폴리오로</span>';
    if (seen()) a.classList.add('pf-small');
    else setTimeout(function () { a.classList.add('pf-small'); }, 4500);
    document.body.appendChild(a);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount);
  else mount();
})();
