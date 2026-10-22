import { zodResolver } from '@hookform/resolvers/zod'
import axios from 'axios'
import { CheckCircle2, KeyRound, Loader2, Sparkles, XCircle } from 'lucide-react'
import { useEffect, useId, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { BotonCopiar } from '@/components/ui/BotonCopiar'
import { FormField } from '@/components/ui/FormField'
import { Modal } from '@/components/ui/Modal'
import { PasswordInput } from '@/components/ui/PasswordInput'
import { applyApiErrors } from '@/lib/formErrors'
import { generarPassword } from '@/lib/password'
import { PATRONES } from '@/schemas/patrones'
import { empresaSchema, type EmpresaFormValues, type EmpresaPayload } from '@/schemas/empresaSchema'
import { empresaService } from './empresaApi'
import type { Empresa } from './types'

const CAMPOS = [
  'nombre',
  'nit',
  'codigo',
  'administrador.nombres',
  'administrador.apellidos',
  'administrador.email',
  'administrador.username',
  'administrador.password',
] as const

const VALORES_INICIALES: EmpresaFormValues = {
  nombre: '',
  nit: '',
  codigo: '',
  administrador: { nombres: '', apellidos: '', email: '', username: '', password: '' },
}

/** Espera tras la última pulsación antes de consultar al backend. */
const ESPERA_MS = 400

type Disponibilidad = { codigo: string; disponible: boolean } | null

interface CrearEmpresaModalProps {
  onClose: () => void
  /** Empresa recién creada (con su administrador en `administrador`). */
  onCreated: (empresa: Empresa) => void
}

/**
 * Alta de una empresa y de su administrador inicial (ADMINISTRADOR_COMERCIAL): o se crean los dos o ninguno.
 * El código empresarial se propone a partir del nombre (NASCAR → NASC01) y se puede cambiar; su
 * disponibilidad se comprueba mientras se escribe, pero quien decide es el backend al guardar.
 */
export function CrearEmpresaModal({ onClose, onCreated }: CrearEmpresaModalProps) {
  const formId = useId()
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    control,
    formState: { errors, isSubmitting },
  } = useForm<EmpresaFormValues, unknown, EmpresaPayload>({
    resolver: zodResolver(empresaSchema),
    defaultValues: VALORES_INICIALES,
  })

  const nombre = useWatch({ control, name: 'nombre' })
  const codigo = useWatch({ control, name: 'codigo' })
  // Mientras el SUPER_ADMIN no escriba su propio código, se sigue proponiendo según el nombre.
  const [codigoManual, setCodigoManual] = useState(false)
  // Pulsar «Sugerir» relanza la propuesta aunque nada más haya cambiado (p. ej. tras un error de red).
  const [peticionSugerencia, setPeticionSugerencia] = useState(0)
  const [disponibilidad, setDisponibilidad] = useState<Disponibilidad>(null)
  const [passwordGenerada, setPasswordGenerada] = useState<string | null>(null)

  // Propuesta del código a partir del nombre.
  useEffect(() => {
    const limpio = nombre.trim()
    if (codigoManual || !limpio) return
    const controller = new AbortController()
    const timeoutId = window.setTimeout(() => {
      empresaService
        .sugerirCodigo(limpio, controller.signal)
        .then((sugerido) => {
          setValue('codigo', sugerido.codigo, { shouldValidate: true })
          setDisponibilidad(sugerido)
        })
        // Sin sugerencia se puede escribir el código a mano; el alta lo validará igualmente.
        .catch((error: unknown) => {
          if (!axios.isCancel(error)) setDisponibilidad(null)
        })
    }, ESPERA_MS)
    return () => {
      window.clearTimeout(timeoutId)
      controller.abort()
    }
  }, [nombre, codigoManual, peticionSugerencia, setValue])

  // Comprobación de un código escrito a mano.
  const codigoNormalizado = codigo.trim().toUpperCase()
  const formatoValido = PATRONES.codigoEmpresarial.test(codigoNormalizado)
  const yaComprobado = disponibilidad?.codigo === codigoNormalizado
  useEffect(() => {
    if (!formatoValido || yaComprobado) return
    const controller = new AbortController()
    const timeoutId = window.setTimeout(() => {
      empresaService
        .comprobarCodigo(codigoNormalizado, controller.signal)
        .then(setDisponibilidad)
        .catch((error: unknown) => {
          if (!axios.isCancel(error)) setDisponibilidad(null)
        })
    }, ESPERA_MS)
    return () => {
      window.clearTimeout(timeoutId)
      controller.abort()
    }
  }, [codigoNormalizado, formatoValido, yaComprobado])

  function sugerirDeNuevo() {
    setCodigoManual(false)
    setDisponibilidad(null)
    setPeticionSugerencia((n) => n + 1)
  }

  function generar() {
    const password = generarPassword()
    setPasswordGenerada(password)
    setValue('administrador.password', password, { shouldValidate: true, shouldDirty: true })
  }

  async function guardar(payload: EmpresaPayload) {
    try {
      onCreated(await empresaService.crear(payload))
    } catch (error) {
      // 400/409 con { errors: { codigo | nit | 'administrador.email' ... } } se muestran junto al campo.
      const general = applyApiErrors(error, setError, CAMPOS)
      if (general) setError('root.server', { message: general })
      if (axios.isAxiosError(error) && error.response?.status === 409) setDisponibilidad(null)
    }
  }

  const estadoCodigo = formatoValido && yaComprobado ? disponibilidad : null

  return (
    <Modal
      open
      onClose={onClose}
      size="lg"
      title="Crear empresa"
      description="Registra la empresa y su administrador principal. Se crean juntos: si algo falla, no se guarda nada."
      dismissible={!isSubmitting}
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onClose} disabled={isSubmitting}>
            Cancelar
          </button>
          <button type="submit" form={formId} className="u-btn u-btn--primary" disabled={isSubmitting} aria-busy={isSubmitting}>
            {isSubmitting && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
            {isSubmitting ? 'Creando…' : 'Crear empresa'}
          </button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit(guardar)} noValidate className="space-y-6">
        {errors.root?.server && (
          <p role="alert" className="u-alert u-alert--error">
            {errors.root.server.message}
          </p>
        )}

        <fieldset>
          <legend className="mb-3 text-[0.8125rem] font-medium text-foreground-muted">Empresa</legend>
          <div className="grid gap-5 sm:grid-cols-2">
            <FormField label="Nombre comercial" required error={errors.nombre?.message} className="sm:col-span-2">
              {(campo) => (
                <input {...campo} {...register('nombre')} className="u-input" autoComplete="organization" autoFocus />
              )}
            </FormField>

            <FormField label="NIT" required error={errors.nit?.message} hint="Ej.: 900123456-7 (los puntos se ignoran)">
              {(campo) => <input {...campo} {...register('nit')} className="u-input font-mono" autoComplete="off" />}
            </FormField>

            <div className="space-y-1.5">
              <FormField
                label="Código empresarial"
                required
                error={errors.codigo?.message}
                hint="Identifica a la empresa (p. ej. NASC01). No es una contraseña."
              >
                {(campo) => (
                  <div className="flex gap-2">
                    <input
                      {...campo}
                      {...register('codigo', { onChange: () => setCodigoManual(true) })}
                      className="u-input min-w-0 flex-1 font-mono uppercase"
                      autoComplete="off"
                      maxLength={12}
                      spellCheck={false}
                    />
                    <button
                      type="button"
                      className="u-btn u-btn--ghost shrink-0 px-3"
                      onClick={sugerirDeNuevo}
                      disabled={!nombre.trim()}
                      title="Proponer un código a partir del nombre"
                    >
                      <Sparkles className="size-4" aria-hidden="true" />
                      Sugerir
                    </button>
                  </div>
                )}
              </FormField>
              <p role="status" aria-live="polite" className="text-xs">
                {estadoCodigo?.disponible === true && (
                  <span className="inline-flex items-center gap-1 text-success">
                    <CheckCircle2 className="size-3.5" aria-hidden="true" />
                    {estadoCodigo.codigo} está disponible
                  </span>
                )}
                {estadoCodigo?.disponible === false && (
                  <span className="inline-flex items-center gap-1 text-danger">
                    <XCircle className="size-3.5" aria-hidden="true" />
                    {estadoCodigo.codigo} ya pertenece a otra empresa
                  </span>
                )}
              </p>
            </div>
          </div>
        </fieldset>

        <fieldset className="border-t border-border pt-5">
          <legend className="sr-only">Administrador de la empresa</legend>
          <p aria-hidden="true" className="mb-3 text-[0.8125rem] font-medium text-foreground-muted">
            Administrador de la empresa
          </p>
          <div className="grid gap-5 sm:grid-cols-2">
            <FormField label="Nombres" required error={errors.administrador?.nombres?.message}>
              {(campo) => <input {...campo} {...register('administrador.nombres')} className="u-input" autoComplete="off" />}
            </FormField>

            <FormField label="Apellidos" required error={errors.administrador?.apellidos?.message}>
              {(campo) => <input {...campo} {...register('administrador.apellidos')} className="u-input" autoComplete="off" />}
            </FormField>

            <FormField label="Correo electrónico" required error={errors.administrador?.email?.message}>
              {(campo) => (
                <input {...campo} {...register('administrador.email')} type="email" inputMode="email" className="u-input" autoComplete="off" />
              )}
            </FormField>

            <FormField
              label="Usuario"
              required
              error={errors.administrador?.username?.message}
              hint="Con él inicia sesión. Ej.: admin.nascar"
            >
              {(campo) => (
                <input
                  {...campo}
                  {...register('administrador.username')}
                  className="u-input"
                  autoComplete="off"
                  autoCapitalize="none"
                  spellCheck={false}
                />
              )}
            </FormField>

            <div className="space-y-1.5">
              <FormField
                label="Contraseña inicial"
                required
                error={errors.administrador?.password?.message}
                hint="Entre 8 y 72 caracteres. Entrégala al administrador por un canal seguro."
              >
                {(campo) => (
                  <div className="flex gap-2">
                    <div className="min-w-0 flex-1">
                      <PasswordInput {...campo} {...register('administrador.password')} autoComplete="new-password" />
                    </div>
                    <button type="button" className="u-btn u-btn--ghost shrink-0 px-3" onClick={generar} title="Generar una contraseña segura">
                      <KeyRound className="size-4" aria-hidden="true" />
                      Generar
                    </button>
                  </div>
                )}
              </FormField>
              {passwordGenerada && (
                <div className="rounded-md bg-surface-muted px-3 py-2">
                  <p className="text-xs text-foreground-muted">Contraseña generada (cópiala antes de crear la empresa):</p>
                  <div className="mt-1 flex flex-wrap items-center gap-x-3">
                    <code className="font-mono text-sm break-all text-foreground select-all">{passwordGenerada}</code>
                    <BotonCopiar texto={passwordGenerada} etiqueta="Copiar" ariaLabel="Copiar contraseña generada" />
                  </div>
                </div>
              )}
            </div>
          </div>
        </fieldset>
      </form>
    </Modal>
  )
}
