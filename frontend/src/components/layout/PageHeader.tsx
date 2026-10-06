import type { ReactNode } from 'react'

interface PageHeaderProps {
  eyebrow: string
  title: string
  description?: string
  actions?: ReactNode
}

export function PageHeader({ eyebrow, title, description, actions }: PageHeaderProps) {
  return (
    <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p className="text-xs font-medium tracking-widest text-foreground-muted uppercase">{eyebrow}</p>
        <h1 className="mt-2 font-serif text-4xl text-foreground sm:text-5xl">{title}</h1>
        {description && <p className="mt-2 max-w-xl text-sm text-foreground-muted">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 gap-3">{actions}</div>}
    </div>
  )
}
