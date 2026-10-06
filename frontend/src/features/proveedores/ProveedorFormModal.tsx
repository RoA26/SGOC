import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { useId } from 'react'
import { useForm } from 'react-hook-form'
import { FormField } from '@/components/ui/FormField'
import { Modal } from '@/components/ui/Modal'
import { applyApiErrors } from '@/lib/formErrors'
import {
  proveedorSchema,
  type ProveedorFormValues,
  type ProveedorPayload,
} from '@/schemas/proveedorSchema'
import { proveedorService, type Proveedor } from '@/services/proveedorService'

const CAMPOS = ['nit', 'razonSocial', 'email', 'telefono', 'direccion'] as const

interface ProveedorFormModalProps {
  /** Proveedor a editar; sin él, el formulario crea uno nuevo. */
  proveedor?: Proveedor
  onClose: () => void
  onSaved: (proveedor: Proveedor, modo: 'creado' | 'actualizado') => void
}

function valoresIniciales(proveedor?: Proveedor): ProveedorFormValues {
  return {
    nit: proveedor?.nit ?? '',
    razonSocial: proveedor?.razonSocial ?? '',
    email: proveedor?.email ?? '',
    telefono: proveedor?.telefono ?? '',
    direccion: proveedor?.direccion ?? '',
  }
}

/** Alta y edición de proveedores: React Hook Form + Zod dentro del Modal. Se monta al abrir. */
export function ProveedorFormModal({ proveedor, onClose, onSaved }: ProveedorFormModalProps) {
  const formId = useId()
  const editando = proveedor !== undefined
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProveedorFormValues, unknown, ProveedorPayload>({
    resolver: zodResolver(proveedorSchema),
    defaultValues: valoresIniciales(proveedor),
  })

  async function guardar(payload: ProveedorPayload) {
    try {
      const guardado = editando
        ? await proveedorService.actualizar(proveedor.id, payload)
        : await proveedorService.crear(payload)
      onSaved(guardado, editando ? 'actualizado' : 'creado')
    } catch (error) {
      // 400/409 con { errors: { nit: "..." } } se muestran junto al campo correspondiente.
      const general = applyApiErrors(error, setError, CAMPOS)
      if (general) setError('root.server', { message: general })
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={editando ? 'Editar proveedor' : 'Nuevo proveedor'}
      description={editando ? proveedor.razonSocial : 'Los campos marcados con * son obligatorios.'}
      dismissible={!isSubmitting}
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onClose} disabled={isSubmitting}>
            Cancelar
          </button>
          <button type="submit" form={formId} className="u-btn u-btn--primary" disabled={isSubmitting} aria-busy={isSubmitting}>
            {isSubmitting && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
            {editando ? 'Guardar cambios' : 'Crear proveedor'}
          </button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit(guardar)} noValidate className="grid gap-5 sm:grid-cols-2">
        {errors.root?.server && (
          <p role="alert" className="u-alert u-alert--error sm:col-span-2">
            {errors.root.server.message}
          </p>
        )}

        <FormField label="NIT" required error={errors.nit?.message} hint="Ej.: 900123456-7 (los puntos se ignoran)">
          {(control) => (
            <input {...control} {...register('nit')} className="u-input font-mono" autoComplete="off" autoFocus />
          )}
        </FormField>

        <FormField label="Teléfono" error={errors.telefono?.message}>
          {(control) => (
            <input {...control} {...register('telefono')} type="tel" className="u-input" autoComplete="tel" />
          )}
        </FormField>

        <FormField label="Razón social" required error={errors.razonSocial?.message} className="sm:col-span-2">
          {(control) => (
            <input {...control} {...register('razonSocial')} className="u-input" autoComplete="organization" />
          )}
        </FormField>

        <FormField label="Correo electrónico" required error={errors.email?.message} className="sm:col-span-2">
          {(control) => (
            <input {...control} {...register('email')} type="email" inputMode="email" className="u-input" autoComplete="email" />
          )}
        </FormField>

        <FormField label="Dirección" error={errors.direccion?.message} className="sm:col-span-2">
          {(control) => (
            <input {...control} {...register('direccion')} className="u-input" autoComplete="street-address" />
          )}
        </FormField>
      </form>
    </Modal>
  )
}
