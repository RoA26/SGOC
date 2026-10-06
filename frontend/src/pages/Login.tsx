import { useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { Alert, Button, Input } from '@/components/ui'
import { useAuth, type LoginCredentials } from '@/features/auth'
import { getApiErrorMessage } from '@/lib/errors'

type FieldErrors = Partial<Record<keyof LoginCredentials, string>>

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const CURRENT_YEAR = new Date().getFullYear()

function validate({ email, password }: LoginCredentials): FieldErrors {
  const errors: FieldErrors = {}
  if (!email.trim()) {
    errors.email = 'Ingresa tu correo electrónico.'
  } else if (!EMAIL_PATTERN.test(email.trim())) {
    errors.email = 'Ingresa un correo electrónico válido.'
  }
  if (!password) {
    errors.password = 'Ingresa tu contraseña.'
  }
  return errors
}

export default function Login() {
  const { login } = useAuth()
  const [values, setValues] = useState<LoginCredentials>({ email: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const passwordRef = useRef<HTMLInputElement>(null)

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const field = event.target.name as keyof LoginCredentials
    setValues((current) => ({ ...current, [field]: event.target.value }))
    setFieldErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (isSubmitting) return

    const errors = validate(values)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setFormError(null)
    setIsSubmitting(true)
    try {
      await login({ email: values.email.trim(), password: values.password })
      // Sin navegación explícita: <GuestRoute> redirige al detectar la sesión activa.
    } catch (error) {
      setFormError(getApiErrorMessage(error))
      setValues((current) => ({ ...current, password: '' }))
      setIsSubmitting(false)
      passwordRef.current?.focus()
    }
  }

  return (
    <div className="flex min-h-screen flex-col justify-center bg-gradient-to-br from-slate-50 via-white to-indigo-50 px-4 py-12">
      <div className="mx-auto w-full max-w-md">
        <header className="mb-8 text-center">
          <div className="mx-auto flex size-12 items-center justify-center rounded-xl bg-indigo-600 shadow-lg shadow-indigo-600/30">
            <svg viewBox="0 0 24 24" className="size-7 text-white" fill="none" aria-hidden="true">
              <path
                d="M7 4h10a2 2 0 0 1 2 2v14l-3-2-2 2-2-2-2 2-2-2-3 2V6a2 2 0 0 1 2-2Zm2 5h6m-6 4h6"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </div>
          <h1 className="mt-5 text-2xl font-bold tracking-tight text-slate-900">
            Inicia sesión en SGOC
          </h1>
          <p className="mt-1.5 text-sm text-slate-500">Sistema de Gestión de Órdenes de Compra</p>
        </header>

        <main className="rounded-2xl bg-white px-6 py-8 shadow-xl ring-1 shadow-slate-200/60 ring-slate-200 sm:px-10">
          <form className="space-y-5" onSubmit={handleSubmit} noValidate>
            {formError && <Alert variant="error">{formError}</Alert>}

            <Input
              label="Correo electrónico"
              name="email"
              type="email"
              inputMode="email"
              autoComplete="username"
              placeholder="nombre@empresa.com"
              autoFocus
              value={values.email}
              onChange={handleChange}
              error={fieldErrors.email}
              disabled={isSubmitting}
            />

            <Input
              ref={passwordRef}
              label="Contraseña"
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="••••••••"
              value={values.password}
              onChange={handleChange}
              error={fieldErrors.password}
              disabled={isSubmitting}
              endAdornment={
                <button
                  type="button"
                  onClick={() => setShowPassword((visible) => !visible)}
                  className="rounded-md p-1.5 text-slate-400 hover:text-slate-600 focus-visible:outline-2 focus-visible:outline-indigo-600"
                  aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  aria-pressed={showPassword}
                >
                  <EyeIcon crossed={showPassword} />
                </button>
              }
            />

            <Button type="submit" fullWidth isLoading={isSubmitting}>
              {isSubmitting ? 'Ingresando…' : 'Ingresar'}
            </Button>
          </form>
        </main>

        <p className="mt-8 text-center text-xs text-slate-400">
          © {CURRENT_YEAR} SGOC · Acceso restringido a personal autorizado
        </p>
      </div>
    </div>
  )
}

function EyeIcon({ crossed }: { crossed: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className="size-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
      {crossed && <path d="M4 4l16 16" />}
    </svg>
  )
}
