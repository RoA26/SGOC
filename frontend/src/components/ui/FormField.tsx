import { useId, type ReactNode } from 'react'
import { cn } from '@/lib/cn'

/** Atributos de accesibilidad que el campo debe aplicar a su control. */
export interface FieldControlProps {
  id: string
  'aria-invalid'?: true
  'aria-describedby'?: string
}

interface FormFieldProps {
  label: string
  error?: string
  hint?: string
  required?: boolean
  className?: string
  /** Render-prop: recibe id y atributos ARIA ya enlazados con la etiqueta y el error. */
  children: (control: FieldControlProps) => ReactNode
}

export function FormField({ label, error, hint, required, className, children }: FormFieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={cn('space-y-1.5', className)}>
      <label htmlFor={id} className="u-label">
        {label}
        {required && (
          <span className="ml-0.5 text-danger" aria-hidden="true">
            *
          </span>
        )}
      </label>
      {children({ id, 'aria-invalid': error ? true : undefined, 'aria-describedby': describedBy })}
      {hint && !error && (
        <p id={hintId} className="text-xs text-foreground-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="u-field-error">
          {error}
        </p>
      )}
    </div>
  )
}
