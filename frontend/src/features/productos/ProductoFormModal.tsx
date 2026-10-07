import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { useEffect, useId } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { FormField } from '@/components/ui/FormField'
import { Modal } from '@/components/ui/Modal'
import { applyApiErrors } from '@/lib/formErrors'
import { formatearCOP } from '@/lib/moneda'
import { productoSchema, type ProductoFormValues, type ProductoPayload } from '@/schemas/productoSchema'
import { productoService, type Producto } from '@/services/productoService'
import { useProveedoresStore } from '@/store/proveedoresStore'

const CAMPOS = ['sku', 'nombre', 'descripcion', 'precio', 'proveedorId'] as const

/** Oculta las flechas nativas del input numérico (Chrome/Safari/Edge y Firefox). */
const SIN_FLECHAS =
  '[appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none'

interface ProductoFormModalProps {
  producto?: Producto
  onClose: () => void
  onSaved: (producto: Producto, modo: 'creado' | 'actualizado') => void
}

export function ProductoFormModal({ producto, onClose, onSaved }: ProductoFormModalProps) {
  const formId = useId()
  const editando = producto !== undefined
  const proveedores = useProveedoresStore((state) => state.proveedores)
  const estadoProveedores = useProveedoresStore((state) => state.estado)
  const errorProveedores = useProveedoresStore((state) => state.error)
  const cargarProveedores = useProveedoresStore((state) => state.cargar)
  const cargando = estadoProveedores === 'inicial' || estadoProveedores === 'cargando'

  useEffect(() => {
    void cargarProveedores()
  }, [cargarProveedores])

  // Si el proveedor actual no está entre las opciones cargadas, se añade para no perderlo.
  const opciones =
    producto && !proveedores.some((opcion) => opcion.id === producto.proveedor.id)
      ? [producto.proveedor, ...proveedores]
      : proveedores

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
  const precio = useWatch({ control, name: 'precio' })
  const precioValido = Number.isInteger(precio) && precio > 0

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

        <FormField
          label="Precio unitario (COP)"
          required
          error={errors.precio?.message}
          hint={precioValido ? `${formatearCOP(precio)} · sin decimales` : 'Pesos colombianos, sin decimales.'}
        >
          {(fieldControl) => (
            <input
              {...fieldControl}
              {...register('precio', { valueAsNumber: true })}
              type="number"
              inputMode="numeric"
              step="500"
              min="0"
              // La rueda del ratón cambiaría el valor sin querer al desplazar el modal.
              onWheel={(event) => event.currentTarget.blur()}
              className={`u-input tabular-nums ${SIN_FLECHAS}`}
            />
          )}
        </FormField>

        <FormField label="Nombre" required error={errors.nombre?.message} className="sm:col-span-2">
          {(fieldControl) => <input {...fieldControl} {...register('nombre')} className="u-input" autoComplete="off" />}
        </FormField>

        <FormField
          label="Proveedor"
          required
          error={errors.proveedorId?.message ?? errorProveedores ?? undefined}
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
                  disabled={cargando}
                  className="u-input"
                >
                  <option value="">{cargando ? 'Cargando proveedores…' : 'Selecciona un proveedor'}</option>
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
