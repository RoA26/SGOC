import { zodResolver } from '@hookform/resolvers/zod'
import axios from 'axios'
import { Loader2 } from 'lucide-react'
import { useEffect, useId, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { FormField } from '@/components/ui/FormField'
import { Modal } from '@/components/ui/Modal'
import { getApiErrorMessage } from '@/lib/errors'
import { applyApiErrors } from '@/lib/formErrors'
import { productoSchema, type ProductoFormValues, type ProductoPayload } from '@/schemas/productoSchema'
import { productoService, type Producto, type ProveedorResumen } from '@/services/productoService'
import { proveedorService } from '@/services/proveedorService'

const CAMPOS = ['sku', 'nombre', 'descripcion', 'precio', 'proveedorId'] as const
/** Opciones del selector: suficiente para el catálogo inicial (luego, búsqueda remota). */
const MAX_PROVEEDORES = 100

interface OpcionesProveedor {
  opciones: ProveedorResumen[]
  cargando: boolean
  error: string | null
}

function useOpcionesProveedor(): OpcionesProveedor {
  const [estado, setEstado] = useState<OpcionesProveedor>({ opciones: [], cargando: true, error: null })

  useEffect(() => {
    const controller = new AbortController()
    proveedorService
      .listar({ page: 0, size: MAX_PROVEEDORES, sort: 'razonSocial,asc' }, controller.signal)
      .then(({ content }) =>
        setEstado({
          opciones: content.map(({ id, nit, razonSocial }) => ({ id, nit, razonSocial })),
          cargando: false,
          error: null,
        }),
      )
      .catch((error: unknown) => {
        if (!axios.isCancel(error)) setEstado({ opciones: [], cargando: false, error: getApiErrorMessage(error) })
      })
    return () => controller.abort()
  }, [])

  return estado
}

interface ProductoFormModalProps {
  producto?: Producto
  onClose: () => void
  onSaved: (producto: Producto, modo: 'creado' | 'actualizado') => void
}

export function ProductoFormModal({ producto, onClose, onSaved }: ProductoFormModalProps) {
  const formId = useId()
  const editando = producto !== undefined
  const proveedores = useOpcionesProveedor()

  // Si el proveedor actual no está entre las opciones cargadas, se añade para no perderlo.
  const opciones =
    producto && !proveedores.opciones.some((opcion) => opcion.id === producto.proveedor.id)
      ? [producto.proveedor, ...proveedores.opciones]
      : proveedores.opciones

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProductoFormValues, unknown, ProductoPayload>({
    resolver: zodResolver(productoSchema),
    defaultValues: {
      sku: producto?.sku ?? '',
      nombre: producto?.nombre ?? '',
      descripcion: producto?.descripcion ?? '',
      precio: producto?.precio ?? Number.NaN,
      proveedorId: producto?.proveedor.id ?? Number.NaN,
    },
  })

  async function guardar(payload: ProductoPayload) {
    try {
      const guardado = editando
        ? await productoService.actualizar(producto.id, payload)
        : await productoService.crear(payload)
      onSaved(guardado, editando ? 'actualizado' : 'creado')
    } catch (error) {
      const general = applyApiErrors(error, setError, CAMPOS)
      if (general) setError('root.server', { message: general })
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={editando ? 'Editar producto' : 'Nuevo producto'}
      description={editando ? `${producto.sku} · ${producto.nombre}` : 'Los campos marcados con * son obligatorios.'}
      dismissible={!isSubmitting}
      size="lg"
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onClose} disabled={isSubmitting}>
            Cancelar
          </button>
          <button type="submit" form={formId} className="u-btn u-btn--primary" disabled={isSubmitting} aria-busy={isSubmitting}>
            {isSubmitting && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
            {editando ? 'Guardar cambios' : 'Crear producto'}
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

        <FormField label="SKU" required error={errors.sku?.message} hint="Letras, números, . - _ (se guarda en mayúsculas)">
          {(fieldControl) => (
            <input {...fieldControl} {...register('sku')} className="u-input font-mono uppercase" autoComplete="off" autoFocus />
          )}
        </FormField>

        <FormField label="Precio unitario" required error={errors.precio?.message}>
          {(fieldControl) => (
            <input
              {...fieldControl}
              {...register('precio', { valueAsNumber: true })}
              type="number"
              inputMode="decimal"
              step="0.01"
              min="0.01"
              className="u-input tabular-nums"
            />
          )}
        </FormField>

        <FormField label="Nombre" required error={errors.nombre?.message} className="sm:col-span-2">
          {(fieldControl) => <input {...fieldControl} {...register('nombre')} className="u-input" autoComplete="off" />}
        </FormField>

        <FormField
          label="Proveedor"
          required
          error={errors.proveedorId?.message ?? proveedores.error ?? undefined}
          className="sm:col-span-2"
        >
          {(fieldControl) => (
            // Controlado: las opciones llegan de forma asíncrona y el valor debe reflejarse al cargar.
            <Controller
              control={control}
              name="proveedorId"
              render={({ field }) => (
                <select
                  {...fieldControl}
                  ref={field.ref}
                  name={field.name}
                  onBlur={field.onBlur}
                  value={Number.isFinite(field.value) ? String(field.value) : ''}
                  onChange={(event) => field.onChange(event.target.value === '' ? Number.NaN : Number(event.target.value))}
                  disabled={proveedores.cargando}
                  className="u-input"
                >
                  <option value="">{proveedores.cargando ? 'Cargando proveedores…' : 'Selecciona un proveedor'}</option>
                  {opciones.map((opcion) => (
                    <option key={opcion.id} value={opcion.id}>
                      {opcion.razonSocial} · {opcion.nit}
                    </option>
                  ))}
                </select>
              )}
            />
          )}
        </FormField>

        <FormField label="Descripción" error={errors.descripcion?.message} className="sm:col-span-2">
          {(fieldControl) => (
            <textarea {...fieldControl} {...register('descripcion')} rows={3} className="u-input h-auto py-2.5" />
          )}
        </FormField>
      </form>
    </Modal>
  )
}
