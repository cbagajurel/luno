'use client'

import cn from 'clsx'
import { Command as CommandPrimitive } from 'cmdk'
import { SearchIcon } from 'lucide-react'
import type { ComponentProps, FC } from 'react'

export const Command: FC<ComponentProps<typeof CommandPrimitive>> = ({
  className,
  ...props
}) => (
  <CommandPrimitive
    className={cn(
      'nextra-cmd flex size-full flex-col overflow-hidden rounded-xl',
      className
    )}
    {...props}
  />
)

export const CommandDialog: FC<
  ComponentProps<typeof CommandPrimitive.Dialog>
> = ({ children, className, ...props }) => (
  // cmdk spreads `className` onto the inner Command, so the panel styling has
  // to go here — a wrapping <Command> would never be rendered.
  <CommandPrimitive.Dialog
    className={cn(
      'nextra-cmd flex w-full flex-col overflow-hidden rounded-xl',
      className
    )}
    overlayClassName="nextra-cmd-overlay"
    contentClassName="nextra-cmd-content"
    {...props}
  >
    {children}
  </CommandPrimitive.Dialog>
)

export const CommandInput: FC<
  ComponentProps<typeof CommandPrimitive.Input>
> = ({ className, ...props }) => (
  <div className="nextra-cmd-input-wrapper flex items-center gap-2 px-4">
    <SearchIcon className="size-4 shrink-0 opacity-50" aria-hidden />
    <CommandPrimitive.Input
      className={cn(
        'h-12 w-full bg-transparent text-sm outline-none placeholder:opacity-50 disabled:opacity-50',
        className
      )}
      {...props}
    />
  </div>
)

export const CommandList: FC<ComponentProps<typeof CommandPrimitive.List>> = ({
  className,
  ...props
}) => (
  <CommandPrimitive.List
    className={cn('max-h-[min(60vh,22rem)] overflow-y-auto p-2', className)}
    {...props}
  />
)

export const CommandEmpty: FC<
  ComponentProps<typeof CommandPrimitive.Empty>
> = props => (
  <CommandPrimitive.Empty
    className="py-8 text-center text-sm opacity-60"
    {...props}
  />
)

export const CommandGroup: FC<
  ComponentProps<typeof CommandPrimitive.Group>
> = ({ className, ...props }) => (
  <CommandPrimitive.Group
    className={cn('nextra-cmd-group', className)}
    {...props}
  />
)

export const CommandSeparator: FC<
  ComponentProps<typeof CommandPrimitive.Separator>
> = ({ className, ...props }) => (
  <CommandPrimitive.Separator
    className={cn('nextra-border my-1 h-px border-t', className)}
    {...props}
  />
)

export const CommandItem: FC<ComponentProps<typeof CommandPrimitive.Item>> = ({
  className,
  ...props
}) => (
  <CommandPrimitive.Item
    className={cn(
      'nextra-cmd-item flex cursor-pointer items-center gap-2.5 rounded-lg px-3 py-2 text-sm',
      className
    )}
    {...props}
  />
)

export const CommandShortcut: FC<ComponentProps<'kbd'>> = ({
  className,
  ...props
}) => (
  <kbd
    className={cn(
      'ms-auto font-sans text-[.7rem] tracking-widest opacity-50',
      className
    )}
    {...props}
  />
)
