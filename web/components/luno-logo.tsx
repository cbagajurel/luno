import type { FC, SVGProps } from 'react'

/** The Luno mark: a device node radiating a signal. */
export const LunoMark: FC<SVGProps<SVGSVGElement>> = props => (
  <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
    <rect
      x="11"
      y="4"
      width="10"
      height="24"
      rx="3"
      stroke="currentColor"
      strokeWidth="2"
    />
    <circle cx="16" cy="22" r="1.75" fill="currentColor" />
    <path
      d="M6.5 11.5a8 8 0 0 0 0 9M25.5 11.5a8 8 0 0 1 0 9"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      opacity=".55"
    />
    <path
      d="M2.5 8.5a13 13 0 0 0 0 15M29.5 8.5a13 13 0 0 1 0 15"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      opacity=".25"
    />
  </svg>
)

/** Mark + wordmark, sized by `height`. Used in the navbar. */
export const LunoLogo: FC<SVGProps<SVGSVGElement>> = props => (
  <svg viewBox="0 0 116 32" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
    <g>
      <rect
        x="11"
        y="4"
        width="10"
        height="24"
        rx="3"
        stroke="currentColor"
        strokeWidth="2"
      />
      <circle cx="16" cy="22" r="1.75" fill="currentColor" />
      <path
        d="M6.5 11.5a8 8 0 0 0 0 9M25.5 11.5a8 8 0 0 1 0 9"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        opacity=".55"
      />
      <path
        d="M2.5 8.5a13 13 0 0 0 0 15M29.5 8.5a13 13 0 0 1 0 15"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        opacity=".25"
      />
    </g>
    <text
      x="40"
      y="22.5"
      fill="currentColor"
      fontFamily="var(--font-sans, system-ui, sans-serif)"
      fontSize="21"
      fontWeight="700"
      letterSpacing="-.5"
    >
      Luno
    </text>
  </svg>
)

export const ArrowRightIcon: FC<SVGProps<SVGSVGElement>> = props => (
  <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
    <path
      d="M5 12h14m0 0-6-6m6 6-6 6"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
)
