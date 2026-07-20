import { PLAY_STORE_URL } from './site'
import type { FC } from 'react'

const PlayIcon: FC<{ className?: string }> = ({ className }) => (
  <svg viewBox="0 0 24 24" className={className} aria-hidden focusable="false">
    <path
      fill="currentColor"
      d="M3.6 1.9a1 1 0 0 0-.5.9v18.4a1 1 0 0 0 .5.9l10-10.1-10-10.1Zm11.1 9 2.9-2.9-9.9-5.6a1 1 0 0 0-.4-.1l7.4 8.6Zm0 2.2-7.4 8.6c.1 0 .3 0 .4-.1l9.9-5.6-2.9-2.9Zm5.9-3.9-2.6-1.5-3.1 3.1 3.1 3.1 2.6-1.5a1.3 1.3 0 0 0 0-3.2Z"
    />
  </svg>
)

export const PlayStoreButton: FC = () => (
  <a
    href={PLAY_STORE_URL}
    target="_blank"
    rel="noreferrer"
    className="nextra-play-button x:focus-visible:nextra-focus flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium max-md:hidden"
  >
    <PlayIcon className="size-4" />
    Get the app
  </a>
)
