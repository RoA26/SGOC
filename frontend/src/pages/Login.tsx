import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useLocation } from 'react-router-dom'
import { FormField } from '@/components/ui/FormField'
import { PasswordInput } from '@/components/ui/PasswordInput'
import { AuthLayout } from '@/features/auth/AuthLayout'
import { login } from '@/features/auth/authApi'
import { applyApiErrors } from '@/lib/formErrors'
import { loginSchema, type LoginFormValues, type LoginPayload } from '@/schemas/authSchema'
import { useAuthStore } from '@/store/authStore'

const CAMPOS = ['username', 'password'] as const

/** Username recién registrado que llega desde /registro (si el inicio de sesión automático falló). */
function usuarioRegistrado(state: unknown): string | null {
  if (typeof state === 'object' && state !== null && 'registrado' in state && typeof state.registrado === 'string') {
    return state.registrado
  }
  return null
}

export default function Login() {
  const setSession = useAuthStore((state) => state.setSession)
  const registrado = usuarioRegistrado(useLocation().state)
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    resetField,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues, unknown, LoginPayload>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: registrado ?? '', password: '' },
  })

  async function entrar(credenciales: LoginPayload) {
    try {
      // Al guardar la sesión, <RutaInvitado> redirige automáticamente.
      setSession(await login(credenciales))
    } catch (error) {
      resetField('password')
      // 400 con { errors: { username: "..." } } va junto al campo; 401 y fallos de red, arriba.
      const general = applyApiErrors(error, setError, CAMPOS)
      if (general) {
        setError('root.server', { message: general })
        setFocus('password')
      }
    }
  }

  return (
    <AuthLayout title="Inicia sesión" description="Accede con tu usuario de Unisen para continuar.">
      <form className="mt-8 space-y-5" onSubmit={handleSubmit(entrar)} noValidate>
        {errors.root?.server ? (
          <p role="alert" className="u-alert u-alert--error">
            {errors.root.server.message}
          </p>
        ) : (
          registrado && (
            <p role="status" className="u-alert u-alert--success">
              Tu cuenta <strong>{registrado}</strong> está lista. Inicia sesión con tu contraseña.
            </p>
          )
        )}

        <FormField label="Usuario" error={errors.username?.message}>
          {(control) => (
            <input
              {...control}
              {...register('username')}
              className="u-input"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              placeholder="nombre.apellido"
              autoFocus={!registrado}
            />
          )}
        </FormField>

        <FormField label="Contraseña" error={errors.password?.message}>
          {(control) => (
            <PasswordInput
              {...control}
              {...register('password')}
              autoComplete="current-password"
              autoFocus={Boolean(registrado)}
            />
          )}
        </FormField>

        <button type="submit" className="u-btn u-btn--primary w-full" disabled={isSubmitting} aria-busy={isSubmitting}>
          {isSubmitting && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
          {isSubmitting ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>

      <p className="mt-8 text-center text-sm text-foreground-muted">
        ¿Tienes un código de invitación?{' '}
        <Link to="/registro" className="font-medium text-foreground underline-offset-4 hover:underline">
          Crea tu cuenta
        </Link>
      </p>
    </AuthLayout>
  )
}
