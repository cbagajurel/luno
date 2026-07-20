import { ArrowRightIcon } from '@components/luno-logo'
import cn from 'clsx'
import Link from 'next/link'
import type { ComponentProps, FC, ReactNode } from 'react'
import styles from './style.module.css'

export const Feature: FC<
  {
    large?: boolean
    centered?: boolean
    children: ReactNode
    href?: string
    index: number
  } & ComponentProps<'div'>
> = ({ large, centered, children, className, href, index, style, ...props }) => {
  return (
    <div
      className={cn(
        styles.feature,
        styles.reveal,
        large && styles.large,
        centered && styles.centered,
        className
      )}
      // Staggers the reveal without a JS animation loop.
      style={{ animationDelay: `${Math.min(index * 60, 480)}ms`, ...style }}
      {...props}
    >
      {children}
      {href && (
        <Link
          className={cn('x:focus-visible:nextra-focus', styles.link)}
          href={href}
          {...(href.startsWith('http') && {
            target: '_blank',
            rel: 'noreferrer'
          })}
        >
          <ArrowRightIcon height="24" />
        </Link>
      )}
    </div>
  )
}

export const Features: FC<{ children: ReactNode }> = ({ children }) => {
  return <div className={styles.features}>{children}</div>
}
