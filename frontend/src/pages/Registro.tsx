import { zodResolver } from '@hookform/resolvers/zod'
import { Clock, Loader2 } from 'lucide-react'
import { useState } from 'react'
import { useForm, useWatch, type FieldErrors } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { FormField } from '@/components/ui/FormField'
import { PasswordInput } from '@/components/ui/PasswordInput'
import { AuthLayout } from '@/features/auth/AuthLayout'
import { login, registrarUsuario } from '@/features/auth/authApi'
import type { UsuarioResponse } from '@/features/auth/types'
import { applyApiErrors } from '@/lib/formErrors'
import { cn } from '@/lib/cn'
import { registroSchema, type RegistroFormValues, type RegistroPayload, type ViaRegistro } from '@/schemas/authSchema'
import { useAuthStore } from '@/store/authStore'

/** En el orden en que aparecen en pantalla (el foco va al primero con error). */
const CAMPOS = ['codigoInvitacion', 'codigoEmpresa', 'nombres', 'apellidos', 'username', 'email', 'password'] as const

const VIAS: { valor: ViaRegistro; etiqueta: string; detalle: string }[] = [
  { valor: 'invitacion', etiqueta: 'Tengo una invitación', detalle: 'Entras directamente.' },
  { valor: 'empresa', etiqueta: 'Tengo el código de mi empresa', detalle: 'Tu administrador debe aprobar la cuenta.' },
]

/** Cuenta creada con el código de la empresa: existe, pero no puede entrar hasta que la aprueben. */
interface CuentaPendiente {
  username: string
  empresaNombre?: string
}

/**
 * Alta de un trabajador con una de las dos vías que ofrece el backend:
 * - Código de invitación de un solo uso: la cuenta queda ACTIVA y se inicia sesión. El código
 *   puede llegar en el enlace que comparte el administrador: /registro?codigo=7KQ2-M9XA-4HPR-T3VW.
 *   Con `?via=empresa`, el código del enlace se toma como código de la empresa.
 * - Código de la empresa: la cuenta queda PENDIENTE hasta que su administrador la apruebe en
 *   «Personal»; no se intenta iniciar sesión.
 * Rol, estado y empresa los decide el servidor a partir del código.
 */
export default function Registro() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const setSession = useAuthStore((state) => state.setSession)
  const codigoEnlace = searchParams.get('codigo') ?? ''
  // La página pública puede abrir directamente la vía del código de la empresa (?via=empresa).
  const viaEnlace: ViaRegistro = searchParams.get('via') === 'empresa' ? 'empresa' : 'invitacion'
  const [pendiente, setPendiente] = useState<CuentaPendiente | null>(null)
  const {
    control,
    register,
    handleSubmit,
    setError,
    setFocus,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<RegistroFormValues, unknown, RegistroPayload>({
    resolver: zodResolver(registroSchema),
    // El campo del código cambia con la vía: el foco se lleva a mano según el orden visual.
    shouldFocusError: false,
    defaultValues: {
      via: viaEnlace,
      codigoInvitacion: viaEnlace === 'invitacion' ? codigoEnlace : '',
      codigoEmpresa: viaEnlace === 'empresa' ? codigoEnlace : '',
      nombres: '',
      apellidos: '',
      username: '',
      email: '',
      password: '',
    },
  })
  const via = useWatch({ control, name: 'via' })

  function enfocarPrimerError(fallos: FieldErrors<RegistroFormValues>) {
    const primero = CAMPOS.find((campo) => fallos[campo])
    if (primero) setFocus(primero)
  }

  async function crearCuenta(payload: RegistroPayload) {
    let usuario: UsuarioResponse
    try {
      usuario = await registrarUsuario(payload)
    } catch (error) {
      // 400 (código inexistente, usado, caducado o de una empresa inactiva; validación) y 409
      // (username o correo en uso) traen { errors: { campo: mensaje } }: cada uno junto a su campo.
      const general = applyApiErrors(error, setError, CAMPOS)
      if (general) setError('root.server', { message: general })
      return
    }

    // El servidor decide el estado: solo una cuenta ACTIVA puede iniciar sesión.
    if (usuario.estado !== undefined && usuario.estado !== 'ACTIVO') {
      setPendiente({ username: usuario.username, empresaNombre: usuario.empresaNombre })
      return
    }
    try {
      // Cuenta activa: se inicia sesión con las mismas credenciales y <RutaInvitado> redirige.
      setSession(await login({ username: payload.username, password: payload.password }))
    } catch {
      navigate('/login', { replace: true, state: { registrado: payload.username } })
    }
  }

  if (pendiente) {
    return (
      <AuthLayout title="Cuenta pendiente de aprobación" description="Tu cuenta se creó, pero aún no puedes entrar.">
        <div role="status" className="mt-8 rounded-lg border border-border-strong bg-surface-muted p-5 text-sm">
          <p className="flex items-center gap-2 font-medium text-foreground">
            <Clock className="size-4 shrink-0 text-accent" aria-hidden="true" />
            Solicitud enviada{pendiente.empresaNombre ? ` a ${pendiente.empresaNombre}` : ''}
          </p>
          <p className="mt-2 text-foreground-muted">
            El administrador de tu empresa debe aprobar la cuenta <strong className="text-foreground">{pendiente.username}</strong>.
            Cuando lo haga, podrás iniciar sesión con tu usuario y tu contraseña. Si tarda, consúltalo con él.
          </p>
        </div>
        <Link to="/login" className="u-btn u-btn--primary mt-6 w-full">
          Ir a iniciar sesión
        </Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout
      title="Crea tu cuenta"
      description="Necesitas el código de invitación o el código de tu empresa que te facilitó el administrador."
    >
      <form className="mt-8 space-y-5" onSubmit={handleSubmit(crearCuenta, enfocarPrimerError)} noValidate>
        {errors.root?.server && (
          <p role="alert" className="u-alert u-alert--error">
            {errors.root.server.message}
          </p>
        )}

        <fieldset>
          <legend className="u-label">¿Con qué código te registras?</legend>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            {VIAS.map(({ valor, etiqueta, detalle }) => (
              <label
                key={valor}
                className={cn(
                  'flex cursor-pointer gap-3 rounded-md border px-3 py-2.5 text-sm transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus',
                  via === valor ? 'border-primary bg-primary/5' : 'border-border-strong hover:bg-surface-muted',
                )}
              >
                <input
                  type="radio"
                  value={valor}
                  {...register('via', { onChange: () => clearErrors(['codigoInvitacion', 'codigoEmpresa']) })}
                  className="mt-0.5 accent-primary"
                />
                <span>
                  <span className="block font-medium text-foreground">{etiqueta}</span>
                  <span className="block text-xs text-foreground-muted">{detalle}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        {via === 'invitacion' ? (
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
        ) : (
          <FormField
            label="Código de tu empresa"
            required
            error={errors.codigoEmpresa?.message}
            hint="Formato XXXX-XXXX-XXXX-XXXX. Pídeselo al administrador de tu empresa."
          >
            {(control) => (
              <input
                {...control}
                {...register('codigoEmpresa')}
                className="u-input font-mono tracking-wider uppercase"
                autoComplete="off"
                autoCapitalize="characters"
                spellCheck={false}
              />
            )}
          </FormField>
        )}

        <div className="grid gap-5 sm:grid-cols-2">
          <FormField label="Nombres" required error={errors.nombres?.message}>
            {(control) => (
              <input
                {...control}
                {...register('nombres')}
                className="u-input"
                autoComplete="given-name"
                autoFocus={Boolean(codigoEnlace)}
              />
            )}
          </FormField>
          <FormField label="Apellidos" required error={errors.apellidos?.message}>
            {(control) => <input {...control} {...register('apellidos')} className="u-input" autoComplete="family-name" />}
          </FormField>
        </div>

        <FormField
          label="Usuario"
          required
          error={errors.username?.message}
          hint="Con él inicias sesión. De 3 a 50 caracteres: letras, números, punto, guion o guion bajo. Sin espacios."
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
              placeholder="nombre@empresa.com"
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
