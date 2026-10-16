import { zodResolver } from '@hookform/resolvers/zod'
import { CheckCircle2, Loader2 } from 'lucide-react'
import { useEffect, useId, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { PageHeader } from '@/components/layout/PageHeader'
import { FormField } from '@/components/ui/FormField'
import { actualizarMisDatos } from '@/features/auth/authApi'
import { ETIQUETA_ROL_COMPLETA } from '@/features/auth/permisos'
import { useAviso } from '@/hooks/useAviso'
import { applyApiErrors } from '@/lib/formErrors'
import {
  datosPersonalesSchema,
  type DatosPersonalesFormValues,
  type DatosPersonalesPayload,
} from '@/schemas/personaSchema'
import { useAuthStore } from '@/store/authStore'

const CAMPOS = ['nombres', 'apellidos'] as const

/**
 * Perfil de la persona autenticada: su nombre real, su usuario de acceso, su rol y su empresa
 * (todo según el backend) y el formulario para completar o corregir sus nombres y apellidos.
 */
export default function Perfil() {
  const formId = useId()
  const sesion = useAuthStore()
  const { aviso, mostrarAviso } = useAviso()
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<DatosPersonalesFormValues, unknown, DatosPersonalesPayload>({
    resolver: zodResolver(datosPersonalesSchema),
    defaultValues: { nombres: sesion.nombres ?? '', apellidos: sesion.apellidos ?? '' },
  })

  // El perfil se refresca desde el backend al cargar: si llega después, se refleja mientras no se edite.
  useEffect(() => {
    if (!isDirty) reset({ nombres: sesion.nombres ?? '', apellidos: sesion.apellidos ?? '' })
  }, [sesion.nombres, sesion.apellidos, isDirty, reset])

  async function guardar(datos: DatosPersonalesPayload) {
    try {
      const usuario = await actualizarMisDatos(datos)
      sesion.actualizarPerfil(usuario)
      reset({ nombres: usuario.nombres ?? '', apellidos: usuario.apellidos ?? '' })
      mostrarAviso('Datos personales guardados.')
    } catch (error) {
      const general = applyApiErrors(error, setError, CAMPOS)
      if (general) setError('root.server', { message: general })
    }
  }

  return (
    <>
      <PageHeader eyebrow="Cuenta" title="Mi perfil" description="Tus datos tal como los conoce el sistema." />

      <div className="grid gap-4 lg:grid-cols-2">
        <section aria-labelledby="cuenta" className="u-card p-5 sm:p-6">
          <h2 id="cuenta" className="font-medium text-foreground">
            Cuenta
          </h2>
          <dl className="mt-4 grid grid-cols-[auto_minmax(0,1fr)] gap-x-6 gap-y-3 text-sm">
            <Dato termino="Nombre">{sesion.nombre}</Dato>
            <Dato termino="Usuario">
              <span className="font-mono">@{sesion.username}</span>
              <span className="block text-xs text-foreground-muted">Con él inicias sesión; no es tu nombre visible.</span>
            </Dato>
            <Dato termino="Correo">{sesion.email}</Dato>
            <Dato termino="Rol">{sesion.rol && ETIQUETA_ROL_COMPLETA[sesion.rol]}</Dato>
            <Dato termino="Empresa">
              {sesion.rol === 'SUPER_ADMIN' ? (
                'Plataforma (sin empresa)'
              ) : (
                <>
                  {sesion.empresaNombre ?? '—'}
                  {sesion.empresaCodigo && <span className="ml-2 font-mono text-xs text-foreground-muted">{sesion.empresaCodigo}</span>}
                </>
              )}
            </Dato>
          </dl>
        </section>

        <section aria-labelledby="datos-personales" className="u-card p-5 sm:p-6">
          <h2 id="datos-personales" className="font-medium text-foreground">
            Datos personales
          </h2>
          {!sesion.datosPersonalesCompletos && (
            <p className="mt-2 text-sm text-foreground-muted">
              Tu cuenta es anterior a la separación de nombres y apellidos. Escríbelos para que se muestren correctamente.
            </p>
          )}
          <form id={formId} onSubmit={handleSubmit(guardar)} noValidate className="mt-4 space-y-4">
            {errors.root?.server && (
              <p role="alert" className="u-alert u-alert--error">
                {errors.root.server.message}
              </p>
            )}
            <FormField label="Nombres" required error={errors.nombres?.message}>
              {(campo) => <input {...campo} {...register('nombres')} className="u-input" autoComplete="given-name" />}
            </FormField>
            <FormField label="Apellidos" required error={errors.apellidos?.message}>
              {(campo) => <input {...campo} {...register('apellidos')} className="u-input" autoComplete="family-name" />}
            </FormField>
            <div className="flex flex-wrap items-center gap-3">
              <button type="submit" className="u-btn u-btn--primary" disabled={isSubmitting || !isDirty} aria-busy={isSubmitting}>
                {isSubmitting && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
                Guardar
              </button>
              <p role="status" aria-live="polite" className="text-sm text-success">
                {aviso && (
                  <span className="inline-flex items-center gap-1.5">
                    <CheckCircle2 className="size-4" aria-hidden="true" />
                    {aviso}
                  </span>
                )}
              </p>
            </div>
          </form>
        </section>
      </div>
    </>
  )
}

function Dato({ termino, children }: { termino: string; children: ReactNode }) {
  return (
    <>
      <dt className="text-foreground-muted">{termino}</dt>
      <dd className="min-w-0 break-words text-foreground">{children}</dd>
    </>
  )
}
