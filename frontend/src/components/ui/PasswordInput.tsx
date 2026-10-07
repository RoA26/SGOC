import { Eye, EyeOff } from 'lucide-react'
import { forwardRef, useState, type InputHTMLAttributes } from 'react'
import { cn } from '@/lib/cn'

type PasswordInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>

/** Campo de contraseña con botón para mostrarla u ocultarla. Compatible con `register()`. */
export const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(function PasswordInput(
  { className, ...props },
  ref,
) {
  const [visible, setVisible] = useState(false)

  return (
    <div className="relative">
      <input ref={ref} type={visible ? 'text' : 'password'} className={cn('u-input pr-12', className)} {...props} />
      <button
        type="button"
        onClick={() => setVisible((actual) => !actual)}
        className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-md text-foreground-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-focus"
        aria-label="Mostrar contraseña"
        aria-pressed={visible}
      >
        {visible ? <EyeOff className="size-5" aria-hidden="true" /> : <Eye className="size-5" aria-hidden="true" />}
      </button>
    </div>
  )
})
