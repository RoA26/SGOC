import { useId, useRef, useState, type FormEvent } from 'react'
import { BrandLogo } from '@/components/BrandLogo'
import { login } from '@/features/auth/authApi'
import type { LoginRequest } from '@/features/auth/types'
import { getApiErrorMessage } from '@/lib/errors'
import { useAuthStore } from '@/store/authStore'

type FieldErrors = Partial<Record<keyof LoginRequest, string>>

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const CURRENT_YEAR = new Date().getFullYear()

function validate({ email, password }: LoginRequest): FieldErrors {
  const errors: FieldErrors = {}
  if (!email.trim()) errors.email = 'Ingresa tu correo electrónico.'
  else if (!EMAIL_PATTERN.test(email.trim())) errors.email = 'El correo no tiene un formato válido.'
  if (!password) errors.password = 'Ingresa tu contraseña.'
  return errors
}

export default function Login() {
  const setSession = useAuthStore((state) => state.setSession)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const passwordRef = useRef<HTMLInputElement>(null)
  const emailId = useId()
  const passwordId = useId()

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return

    const credentials = { email: email.trim(), password }
    const errors = validate(credentials)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setFormError(null)
    setSubmitting(true)
    try {
      // Al guardar la sesión, <RutaInvitado> redirige automáticamente.
      setSession(await login(credentials))
    } catch (error) {
      setFormError(getApiErrorMessage(error))
      setPassword('')
      setSubmitting(false)
      passwordRef.current?.focus()
    }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      {/* Panel de marca (escritorio) */}
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-brand-panel p-12 text-brand-panel-foreground lg:flex">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -right-32 -bottom-40 size-[28rem] rounded-full bg-accent/20 blur-3xl"
        />
        <BrandLogo variant="claro" className="relative h-10 w-auto self-start" />
        <div className="relative max-w-md">
          <p className="text-sm font-medium tracking-widest text-brand-panel-muted uppercase">
            Sistema de gestión de compras
          </p>
          <h2 className="mt-4 font-serif text-5xl leading-[1.05] xl:text-6xl">
            Órdenes de compra, <em className="text-accent">claras</em> de principio a fin.
          </h2>
          <p className="mt-6 text-base leading-relaxed text-brand-panel-muted">
            Solicita, aprueba y da seguimiento a cada compra de Unisen desde un único lugar.
          </p>
        </div>
        <p className="relative text-xs text-brand-panel-muted">© {CURRENT_YEAR} Unisen</p>
      </aside>

      {/* Formulario */}
      <main className="flex items-center justify-center px-5 py-12 sm:px-10">
        <div className="w-full max-w-sm">
          <BrandLogo className="mb-10 h-9 w-auto lg:hidden" />

          <h1 className="font-serif text-4xl text-foreground">Inicia sesión</h1>
          <p className="mt-2 text-sm text-foreground-muted">
            Accede con tu correo corporativo para continuar.
          </p>

          <form className="mt-8 space-y-5" onSubmit={handleSubmit} noValidate>
            {formError && (
              <div role="alert" className="u-alert u-alert--error">
                {formError}
              </div>
            )}

            <div className="space-y-1.5">
              <label htmlFor={emailId} className="u-label">
                Correo electrónico
              </label>
              <input
                id={emailId}
                name="email"
                type="email"
                inputMode="email"
                autoComplete="username"
                placeholder="nombre@unisen.com"
                autoFocus
                className="u-input"
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value)
                  setFieldErrors((current) => ({ ...current, email: undefined }))
                }}
                aria-invalid={fieldErrors.email ? true : undefined}
                aria-describedby={fieldErrors.email ? `${emailId}-error` : undefined}
                disabled={submitting}
              />
              {fieldErrors.email && (
                <p id={`${emailId}-error`} className="u-field-error">
                  {fieldErrors.email}
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <label htmlFor={passwordId} className="u-label">
                Contraseña
              </label>
              <div className="relative">
                <input
                  ref={passwordRef}
                  id={passwordId}
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  className="u-input pr-12"
                  value={password}
                  onChange={(event) => {
                    setPassword(event.target.value)
                    setFieldErrors((current) => ({ ...current, password: undefined }))
                  }}
                  aria-invalid={fieldErrors.password ? true : undefined}
                  aria-describedby={fieldErrors.password ? `${passwordId}-error` : undefined}
                  disabled={submitting}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((visible) => !visible)}
                  className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-md text-foreground-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-focus"
                  aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  aria-pressed={showPassword}
                >
                  <EyeIcon crossed={showPassword} />
                </button>
              </div>
              {fieldErrors.password && (
                <p id={`${passwordId}-error`} className="u-field-error">
                  {fieldErrors.password}
                </p>
              )}
            </div>

            <button type="submit" className="u-btn u-btn--primary w-full" disabled={submitting} aria-busy={submitting}>
              {submitting && <Spinner />}
              {submitting ? 'Ingresando…' : 'Ingresar'}
            </button>
          </form>

          <p className="mt-10 text-center text-xs text-foreground-muted lg:hidden">© {CURRENT_YEAR} Unisen</p>
        </div>
      </main>
    </div>
  )
}

function EyeIcon({ crossed }: { crossed: boolean }) {
  return (
    <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
      {crossed && <path d="M4 4l16 16" />}
    </svg>
  )
}

function Spinner() {
  return (
    <svg viewBox="0 0 24 24" className="size-4 animate-spin" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" className="opacity-25" />
      <path d="M22 12a10 10 0 0 0-10-10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  )
}
