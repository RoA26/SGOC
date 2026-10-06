import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from 'react'
import { cn } from '@/lib/cn'

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  /** Mensaje de validación; activa el estilo de error y `aria-invalid`. */
  error?: string
  /** Elemento posicionado a la derecha del campo (p. ej. mostrar/ocultar contraseña). */
  endAdornment?: ReactNode
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, endAdornment, id, className, ...props },
  ref,
) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const errorId = `${inputId}-error`

  return (
    <div className="space-y-1.5">
      <label htmlFor={inputId} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <div className="relative">
        <input
          ref={ref}
          id={inputId}
          {...props}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          className={cn(
            'block w-full rounded-lg border-0 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm',
            'ring-1 ring-inset placeholder:text-slate-400 focus:ring-2 focus:outline-none focus:ring-inset',
            'disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500',
            error ? 'ring-red-400 focus:ring-red-500' : 'ring-slate-300 focus:ring-indigo-600',
            endAdornment ? 'pr-11' : undefined,
            className,
          )}
        />
        {endAdornment && (
          <div className="absolute inset-y-0 right-0 flex items-center pr-1.5">{endAdornment}</div>
        )}
      </div>
      {error && (
        <p id={errorId} className="text-sm text-red-600">
          {error}
        </p>
      )}
    </div>
  )
})
