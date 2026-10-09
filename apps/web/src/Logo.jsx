// AIRA 로고 마크 + 깨진 글자 워드마크. 색은 CSS 변수(--lgA1.. / --ink)를 따라 다크·라이트에 맞게 바뀐다.
export default function Logo({ height = 44 }) {
  const width = Math.round((height * 500) / 540)
  return (
    <svg className="brand-logo" width={width} height={height} viewBox="170 0 500 540" role="img" aria-label="AIRA">
      <defs>
        <linearGradient id="aira-lg-a" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="430">
          <stop offset="0" stopColor="var(--lgA1)" /><stop offset="1" stopColor="var(--lgA2)" />
        </linearGradient>
        <linearGradient id="aira-lg-b" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="430">
          <stop offset="0" stopColor="var(--lgB1)" /><stop offset="1" stopColor="var(--lgB2)" />
        </linearGradient>
      </defs>
      <polygon points="428,0 434,118 328,300 250,345 180,425" fill="url(#aira-lg-a)" />
      <polygon points="428,0 660,425 575,392 505,292 548,228 434,118" fill="url(#aira-lg-b)" />
      <polygon points="180,425 266,398 520,292 548,228 480,236 330,302 250,345" fill="url(#aira-lg-b)" />
      <g fill="none" stroke="var(--ink)" strokeWidth="10" strokeLinejoin="miter">
        <path d="M252,493 L282,438 L325,528 M258,488 L306,488 M262,508 L240,525" />
        <path d="M362,433 V528" />
        <path d="M420,528 V433 H466 Q492,433 492,458 Q492,483 466,483 H420 M452,483 L494,528" />
        <path d="M543,488 L590,488 M597,500 L565,438 L525,528 M592,510 L610,528" />
      </g>
    </svg>
  )
}
