import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2, Plus, Trash2 } from 'lucide-react'
import { useEffect, useId, useMemo, useRef } from 'react'
import { Controller, useFieldArray, useForm, useWatch, type Path } from 'react-hook-form'
import { FormField } from '@/components/ui/FormField'
import { Modal } from '@/components/ui/Modal'
import { cn } from '@/lib/cn'
import { applyApiErrors } from '@/lib/formErrors'
import { formatearCOP } from '@/lib/moneda'
import {
  MAX_LINEAS_ORDEN,
  ordenCompraSchema,
  type OrdenCompraFormPayload,
  type OrdenCompraFormValues,
} from '@/schemas/ordenCompraSchema'
import { numeroOrden, ordenCompraService, type OrdenCompra } from '@/services/ordenCompraService'
import { useProductosStore } from '@/store/productosStore'
import { useProveedoresStore } from '@/store/proveedoresStore'

const SIN_FLECHAS =
  '[appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none'

interface OrdenFormModalProps {
  /** Sin orden: alta de un borrador. Con orden (en BORRADOR): edición de sus líneas. */
  orden?: OrdenCompra
  onClose: () => void
  onSaved: (orden: OrdenCompra) => void
}

/**
 * Alta o edición de un borrador de orden de compra. Cada orden es de un solo proveedor: el
 * selector de productos solo ofrece los suyos (y el backend lo vuelve a comprobar). El total es
 * una vista previa; el que cuenta lo calcula el backend.
 */
export function OrdenFormModal({ orden, onClose, onSaved }: OrdenFormModalProps) {
  const formId = useId()
  const editando = orden !== undefined
  const agregarRef = useRef<HTMLButtonElement>(null)

  const proveedores = useProveedoresStore((state) => state.items)
  const cargarProveedores = useProveedoresStore((state) => state.cargar)
  const productos = useProductosStore((state) => state.items)
  const estadoProductos = useProductosStore((state) => state.estado)
  const errorProductos = useProductosStore((state) => state.error)
  const cargarProductos = useProductosStore((state) => state.cargar)
  useEffect(() => {
    void cargarProveedores({ forzar: true })
    void cargarProductos({ forzar: true })
  }, [cargarProveedores, cargarProductos])
  const porId = useMemo(() => new Map(productos.map((producto) => [producto.id, producto])), [productos])

  const {
    register,
    control,
    handleSubmit,
    setError,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<OrdenCompraFormValues, unknown, OrdenCompraFormPayload>({
    resolver: zodResolver(ordenCompraSchema),
    defaultValues: orden
      ? {
          proveedorId: orden.proveedor.id,
          observaciones: orden.observaciones ?? '',
          detalles: orden.detalles.map((linea) => ({
            productoId: linea.producto.id,
            cantidad: linea.cantidad,
            costoUnitario: Math.round(linea.costoUnitario),
          })),
        }
      : { proveedorId: Number.NaN, observaciones: '', detalles: [{ productoId: Number.NaN, cantidad: 1, costoUnitario: Number.NaN }] },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'detalles' })
  const proveedorId = useWatch({ control, name: 'proveedorId' })
  const lineas = useWatch({ control, name: 'detalles' })
  const delProveedor = productos.filter((producto) => producto.proveedor.id === proveedorId)
  const total = lineas.reduce(
    (suma, linea) =>
      Number.isFinite(linea.cantidad) && Number.isFinite(linea.costoUnitario) ? suma + linea.cantidad * linea.costoUnitario : suma,
    0,
  )

  async function guardar(payload: OrdenCompraFormPayload) {
    try {
      const guardada = editando
        ? await ordenCompraService.actualizar(orden.id, { observaciones: payload.observaciones, detalles: payload.detalles })
        : await ordenCompraService.crear(payload)
      onSaved(guardada)
    } catch (error) {
      const campos: Path<OrdenCompraFormValues>[] = ['proveedorId', 'observaciones', 'detalles']
      fields.forEach((_, i) => campos.push(`detalles.${i}.productoId`, `detalles.${i}.cantidad`, `detalles.${i}.costoUnitario`))
      const general = applyApiErrors(error, setError, campos)
      if (general) setError('root.server', { message: general })
    }
  }

  const errorLista = errors.detalles?.root?.message ?? errors.detalles?.message

  return (
    <Modal
      open
      onClose={onClose}
      title={editando ? `Editar ${numeroOrden(orden.numero)}` : 'Nueva orden de compra'}
      description={
        editando
          ? `${orden.proveedor.razonSocial} · en borrador: puedes cambiar sus líneas hasta emitirla.`
          : 'Se crea en borrador. Revísala y emítela cuando esté lista para enviarla al proveedor.'
      }
      dismissible={!isSubmitting}
      size="xl"
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onClose} disabled={isSubmitting}>
            Cancelar
          </button>
          <button type="submit" form={formId} className="u-btn u-btn--primary" disabled={isSubmitting} aria-busy={isSubmitting}>
            {isSubmitting && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
            {editando ? 'Guardar cambios' : 'Crear borrador'}
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

        <FormField label="Proveedor" required error={errors.proveedorId?.message} hint={editando ? 'El proveedor de una orden no cambia.' : undefined}>
          {(campo) => (
            <Controller
              control={control}
              name="proveedorId"
              render={({ field }) => (
                <select
                  {...campo}
                  ref={field.ref}
                  name={field.name}
                  onBlur={field.onBlur}
                  value={Number.isFinite(field.value) ? String(field.value) : ''}
                  onChange={(event) => {
                    field.onChange(event.target.value === '' ? Number.NaN : Number(event.target.value))
                    // Otro proveedor, otros productos: las líneas elegidas dejan de valer.
                    setValue('detalles', [{ productoId: Number.NaN, cantidad: 1, costoUnitario: Number.NaN }])
                  }}
                  disabled={editando}
                  className="u-input"
                  autoFocus={!editando}
                >
                  <option value="">Selecciona un proveedor</option>
                  {(editando && !proveedores.some((p) => p.id === orden.proveedor.id)
                    ? [{ id: orden.proveedor.id, razonSocial: orden.proveedor.razonSocial, nit: orden.proveedor.nit }, ...proveedores]
                    : proveedores
                  ).map((proveedor) => (
                    <option key={proveedor.id} value={proveedor.id}>
                      {proveedor.razonSocial} · {proveedor.nit}
                    </option>
                  ))}
                </select>
              )}
            />
          )}
        </FormField>

        <fieldset className="space-y-3">
          <legend className="u-label mb-2">
            Productos
            <span className="ml-0.5 text-danger" aria-hidden="true">
              *
            </span>
          </legend>
          {errorProductos && (
            <p role="alert" className="u-alert u-alert--error">
              No se pudo cargar el catálogo: {errorProductos}
            </p>
          )}
          {Number.isFinite(proveedorId) && estadoProductos === 'listo' && delProveedor.length === 0 && (
            <p className="u-alert u-alert--error">Este proveedor no tiene productos activos en el catálogo.</p>
          )}
          <ul className="space-y-3">
            {fields.map((field, indice) => {
              const errorLinea = errors.detalles?.[indice]
              return (
                <li key={field.id} className="grid gap-3 rounded-lg border border-border p-3 sm:grid-cols-[minmax(0,1fr)_7rem_9rem_auto] sm:items-start">
                  <FormField label="Producto" error={errorLinea?.productoId?.message} className="min-w-0">
                    {(campo) => (
                      <Controller
                        control={control}
                        name={`detalles.${indice}.productoId`}
                        render={({ field: campoProducto }) => (
                          <select
                            {...campo}
                            ref={campoProducto.ref}
                            name={campoProducto.name}
                            onBlur={campoProducto.onBlur}
                            value={Number.isFinite(campoProducto.value) ? String(campoProducto.value) : ''}
                            onChange={(event) => {
                              const id = event.target.value === '' ? Number.NaN : Number(event.target.value)
                              campoProducto.onChange(id)
                              // Costo sugerido: el precio actual del catálogo (editable).
                              const producto = porId.get(id)
                              if (producto) setValue(`detalles.${indice}.costoUnitario`, Math.round(producto.precio), { shouldValidate: true })
                            }}
                            disabled={!Number.isFinite(proveedorId)}
                            className="u-input"
                          >
                            <option value="">{Number.isFinite(proveedorId) ? 'Selecciona un producto' : 'Elige antes el proveedor'}</option>
                            {delProveedor.map((producto) => (
                              <option key={producto.id} value={producto.id}>
                                {producto.nombre} · {producto.sku}
                              </option>
                            ))}
                            {/* Una línea de un producto que ya no está en la lista (edición) se conserva visible. */}
                            {Number.isFinite(campoProducto.value) && !delProveedor.some((p) => p.id === campoProducto.value) && (
                              <option value={campoProducto.value}>
                                {orden?.detalles.find((l) => l.producto.id === campoProducto.value)?.producto.nombre ?? `Producto ${campoProducto.value}`}
                              </option>
                            )}
                          </select>
                        )}
                      />
                    )}
                  </FormField>
                  <FormField label="Cantidad" error={errorLinea?.cantidad?.message}>
                    {(campo) => (
                      <input
                        {...campo}
                        {...register(`detalles.${indice}.cantidad`, { valueAsNumber: true })}
                        type="number"
                        inputMode="numeric"
                        min={1}
                        onWheel={(event) => event.currentTarget.blur()}
                        className={cn('u-input tabular-nums', SIN_FLECHAS)}
                      />
                    )}
                  </FormField>
                  <FormField label="Costo unitario" error={errorLinea?.costoUnitario?.message}>
                    {(campo) => (
                      <input
                        {...campo}
                        {...register(`detalles.${indice}.costoUnitario`, { valueAsNumber: true })}
                        type="number"
                        inputMode="numeric"
                        min={1}
                        onWheel={(event) => event.currentTarget.blur()}
                        className={cn('u-input tabular-nums', SIN_FLECHAS)}
                      />
                    )}
                  </FormField>
                  <button
                    type="button"
                    className="mt-0 self-end rounded-md p-2.5 text-foreground-muted hover:bg-danger-surface hover:text-danger disabled:opacity-40 sm:mt-6"
                    onClick={() => {
                      remove(indice)
                      agregarRef.current?.focus()
                    }}
                    disabled={fields.length === 1}
                    aria-label={`Quitar la línea ${indice + 1}`}
                  >
                    <Trash2 className="size-4" aria-hidden="true" />
                  </button>
                </li>
              )
            })}
          </ul>
          {errorLista && (
            <p role="alert" className="u-field-error">
              {errorLista}
            </p>
          )}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <button
              ref={agregarRef}
              type="button"
              className="u-btn u-btn--ghost"
              onClick={() => append({ productoId: Number.NaN, cantidad: 1, costoUnitario: Number.NaN })}
              disabled={fields.length >= MAX_LINEAS_ORDEN || !Number.isFinite(proveedorId)}
            >
              <Plus className="size-4" aria-hidden="true" />
              Añadir producto
            </button>
            <p className="text-sm text-foreground-muted">
              Total estimado: <strong className="font-semibold text-foreground tabular-nums">{formatearCOP(total)}</strong>
            </p>
          </div>
        </fieldset>

        <FormField label="Observaciones" error={errors.observaciones?.message} hint="Condiciones de entrega, referencias del proveedor…">
          {(campo) => <textarea {...campo} {...register('observaciones')} rows={2} className="u-input h-auto py-2.5" />}
        </FormField>
      </form>
    </Modal>
  )
}
