import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { FormField } from '@/components/ui/FormField'
import { PasswordInput } from '@/components/ui/PasswordInput'
import { AuthLayout } from '@/features/auth/AuthLayout'
import { login, registrarUsuario } from '@/features/auth/authApi'
import { applyApiErrors } from '@/lib/formErrors'
import { registroSchema, type RegistroFormValues, type RegistroPayload } from '@/schemas/authSchema'
import { useAuthStore } from '@/store/authStore'

const CAMPOS = ['codigoInvitacion', 'username', 'email', 'password'] as const

/**
 * Alta con código de invitación. El código puede llegar en el enlace que comparte el
 * administrador: /registro?codigo=7KQ2-M9XA-4HPR-T3VW.
 */
export default function Registro() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const setSession = useAuthStore((state) => state.setSession)
  const codigoEnlace = searchParams.get('codigo') ?? ''
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegistroFormValues, unknown, RegistroPayload>({
    resolver: zodResolver(registroSchema),
    defaultValues: { codigoInvitacion: codigoEnlace, username: '', email: '', password: '' },
  })

  async function crearCuenta(payload: RegistroPayload) {
    try {
      await registrarUsuario(payload)
    } catch (error) {
      // 400 (código inexistente, usado o caducado; validación) y 409 (username o correo en uso)
      // traen { errors: { campo: mensaje } }: cada mensaje va junto a su campo.
      const general = applyApiErrors(error, setError, CAMPOS)
      if (general) setError('root.server', { message: general })
      return
    }

    try {
      // Cuenta creada: se inicia sesión con las mismas credenciales y <RutaInvitado> redirige.
      setSession(await login({ username: payload.username, password: payload.password }))
    } catch {
      navigate('/login', { replace: true, state: { registrado: payload.username } })
    }
  }

  return (
    <AuthLayout title="Crea tu cuenta" description="Necesitas el código de invitación que te facilitó el administrador.">
      <form className="mt-8 space-y-5" onSubmit={handleSubmit(crearCuenta)} noValidate>
        {errors.root?.server && (
          <p role="alert" className="u-alert u-alert--error">
            {errors.root.server.message}
          </p>
        )}

        <FormField label="Código de invitación" required error={errors.codigoInvitacion?.message} hint="Formato XXXX-XXXX-XXXX-XXXX">
          {(control) => (
            <input
              {...control}
              {...register('codigoInvitacion')}
              className="u-input font-mono tracking-wider uppercase"
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
              autoFocus={!codigoEnlace}
            />
          )}
        </FormField>

        <FormField
          label="Usuario"
          required
          error={errors.username?.message}
          hint="De 3 a 50 caracteres: letras, números, punto, guion o guion bajo. Sin espacios."
        >
          {(control) => (
            <input
              {...control}
              {...register('username')}
              className="u-input"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              placeholder="nombre.apellido"
              autoFocus={Boolean(codigoEnlace)}
            />
          )}
        </FormField>

        <FormField label="Correo electrónico" required error={errors.email?.message}>
          {(control) => (
            <input
              {...control}
              {...register('email')}
              type="email"
              inputMode="email"
              className="u-input"
              autoComplete="email"
              placeholder="nombre@unisen.com"
            />
          )}
        </FormField>

        <FormField label="Contraseña" required error={errors.password?.message} hint="Entre 8 y 72 caracteres.">
          {(control) => <PasswordInput {...control} {...register('password')} autoComplete="new-password" />}
        </FormField>

        <button type="submit" className="u-btn u-btn--primary w-full" disabled={isSubmitting} aria-busy={isSubmitting}>
          {isSubmitting && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
          {isSubmitting ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>
      </form>

      <p className="mt-8 text-center text-sm text-foreground-muted">
        ¿Ya tienes cuenta?{' '}
        <Link to="/login" className="font-medium text-foreground underline-offset-4 hover:underline">
          Inicia sesión
        </Link>
      </p>
    </AuthLayout>
  )
}
