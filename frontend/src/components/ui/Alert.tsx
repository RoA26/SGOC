import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

type AlertVariant = 'error' | 'success' | 'info'

interface AlertProps {
  variant?: AlertVariant
  title?: string
  children: ReactNode
}

const VARIANT_CLASSES: Record<AlertVariant, string> = {
  error: 'border-red-200 bg-red-50 text-red-800',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  info: 'border-sky-200 bg-sky-50 text-sky-800',
}

export function Alert({ variant = 'info', title, children }: AlertProps) {
  return (
    <div
      role={variant === 'error' ? 'alert' : 'status'}
      className={cn('rounded-lg border px-4 py-3 text-sm', VARIANT_CLASSES[variant])}
    >
      {title && <p className="font-semibold">{title}</p>}
      <div className={cn(title && 'mt-0.5')}>{children}</div>
    </div>
  )
}
