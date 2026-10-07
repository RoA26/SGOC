import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2, Plus, Trash2 } from 'lucide-react'
import { useEffect, useId, useMemo, useRef } from 'react'
import {
  Controller,
  useFieldArray,
  useForm,
  useWatch,
  type Control,
  type FieldErrors,
  type Path,
  type UseFormRegister,
} from 'react-hook-form'
import { FormField } from '@/components/ui/FormField'
import { Modal } from '@/components/ui/Modal'
import { cn } from '@/lib/cn'
import { applyApiErrors } from '@/lib/formErrors'
import { formatearCOP } from '@/lib/moneda'
import {
  MAX_LINEAS,
  solicitudSchema,
  type SolicitudFormValues,
  type SolicitudPayload,
} from '@/schemas/solicitudSchema'
import type { Producto } from '@/services/productoService'
import { solicitudService, type Solicitud } from '@/services/solicitudService'
import { useProductosStore } from '@/store/productosStore'

const LINEA_VACIA = { productoId: Number.NaN, cantidad: 1 }

interface FormSolicitudProps {
  onClose: () => void
  onSaved: (solicitud: Solicitud) => void
}

/**
 * Alta de una solicitud interna (maestro-detalle): la justificación es la cabecera y cada
 * línea, un producto del catálogo con su cantidad. Las líneas se gestionan con
 * useFieldArray; cada una se pinta en su propio componente, que solo observa sus campos.
 */
export function FormSolicitud({ onClose, onSaved }: FormSolicitudProps) {
  const formId = useId()
  const agregarRef = useRef<HTMLButtonElement>(null)

  const productos = useProductosStore((state) => state.items)
  const estadoProductos = useProductosStore((state) => state.estado)
  const errorProductos = useProductosStore((state) => state.error)
  const cargarProductos = useProductosStore((state) => state.cargar)
  useEffect(() => {
    void cargarProductos()
  }, [cargarProductos])
  const cargando = estadoProductos === 'inicial' || estadoProductos === 'cargando'
  const porId = useMemo(() => new Map(productos.map((producto) => [producto.id, producto])), [productos])

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SolicitudFormValues, unknown, SolicitudPayload>({
    resolver: zodResolver(solicitudSchema),
    defaultValues: { justificacion: '', detalles: [LINEA_VACIA] },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'detalles' })

  async function enviar(payload: SolicitudPayload) {
    try {
      onSaved(await solicitudService.crear(payload))
    } catch (error) {
      // 400 con { errors: { "detalles[1].productoId": "…" } } se muestra en la línea afectada.
      const campos: Path<SolicitudFormValues>[] = ['justificacion', 'detalles']
      fields.forEach((_, indice) => campos.push(`detalles.${indice}.productoId`, `detalles.${indice}.cantidad`))
      const general = applyApiErrors(error, setError, campos)
      if (general) setError('root.server', { message: general })
    }
  }

  function quitar(indice: number) {
    remove(indice)
    // El botón pulsado desaparece: el foco pasa a un lugar estable.
    agregarRef.current?.focus()
  }

  const errorLista = errors.detalles?.root?.message ?? errors.detalles?.message

  return (
    <Modal
      open
      onClose={onClose}
      title="Nueva solicitud"
      description="Indica qué productos necesitas y por qué. Quedará pendiente hasta que un gestor la revise."
      dismissible={!isSubmitting}
      size="xl"
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onClose} disabled={isSubmitting}>
            Cancelar
          </button>
          <button
            type="submit"
            form={formId}
            className="u-btn u-btn--primary"
            disabled={isSubmitting || cargando}
            aria-busy={isSubmitting}
          >
            {isSubmitting && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
            Enviar solicitud
          </button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit(enviar)} noValidate className="space-y-6">
        {errors.root?.server && (
          <p role="alert" className="u-alert u-alert--error">
            {errors.root.server.message}
          </p>
        )}

        <FormField
          label="Justificación"
          required
          error={errors.justificacion?.message}
          hint="Para qué se necesita y dónde se usará (entre 10 y 1000 caracteres)."
        >
          {(fieldControl) => (
            <textarea
              {...fieldControl}
              {...register('justificacion')}
              rows={3}
              className="u-input h-auto py-2.5"
              autoFocus
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

          {/* Encabezado visual de la tabla de líneas (en móvil cada campo muestra su etiqueta). */}
          <div
            aria-hidden="true"
            className="hidden grid-cols-[minmax(0,1fr)_7rem_8rem_2.5rem] gap-3 px-1 text-xs font-medium tracking-wide text-foreground-muted uppercase sm:grid"
          >
            <span>Producto</span>
            <span>Cantidad</span>
            <span className="text-right">Subtotal</span>
            <span />
          </div>

          <ol className="space-y-3">
            {fields.map((field, indice) => (
              <LineaSolicitud
                key={field.id}
                indice={indice}
                control={control}
                register={register}
                errors={errors}
                productos={productos}
                porId={porId}
                cargando={cargando}
                puedeQuitar={fields.length > 1}
                onQuitar={() => quitar(indice)}
              />
            ))}
          </ol>

          {errorLista && (
            <p role="alert" className="u-field-error">
              {errorLista}
            </p>
          )}

          <div className="flex flex-col gap-3 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between">
            <button
              ref={agregarRef}
              type="button"
              className="u-btn u-btn--ghost h-9 self-start px-3"
              // Sin focusName, RHF enfocaría el primer campo registrado de la línea (la cantidad).
              onClick={() => append(LINEA_VACIA, { focusName: `detalles.${fields.length}.productoId` })}
              disabled={fields.length >= MAX_LINEAS || cargando}
            >
              <Plus className="size-4" aria-hidden="true" />
              Agregar producto
            </button>
            <TotalEstimado control={control} porId={porId} />
          </div>
        </fieldset>
      </form>
    </Modal>
  )
}

interface LineaSolicitudProps {
  indice: number
  control: Control<SolicitudFormValues, unknown, SolicitudPayload>
  register: UseFormRegister<SolicitudFormValues>
  errors: FieldErrors<SolicitudFormValues>
  productos: Producto[]
  porId: Map<number, Producto>
  cargando: boolean
  puedeQuitar: boolean
  onQuitar: () => void
}

/** Una línea del detalle. Observa solo su producto y su cantidad para calcular el subtotal. */
function LineaSolicitud({
  indice,
  control,
  register,
  errors,
  productos,
  porId,
  cargando,
  puedeQuitar,
  onQuitar,
}: LineaSolicitudProps) {
  const numero = indice + 1
  const [productoId, cantidad] = useWatch({
    control,
    name: [`detalles.${indice}.productoId`, `detalles.${indice}.cantidad`],
  })
  const producto = porId.get(productoId)
  const subtotal = producto && Number.isInteger(cantidad) && cantidad > 0 ? producto.precio * cantidad : null

  const errorProducto = errors.detalles?.[indice]?.productoId?.message
  const errorCantidad = errors.detalles?.[indice]?.cantidad?.message
  const idProducto = `linea-${numero}-producto`
  const idCantidad = `linea-${numero}-cantidad`

  return (
    <li className="grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-2 rounded-lg border border-border p-3 sm:grid-cols-[minmax(0,1fr)_7rem_8rem_2.5rem] sm:items-start sm:border-0 sm:p-1">
      <div className="space-y-1">
        <label htmlFor={idProducto} className="text-xs font-medium text-foreground-muted sm:sr-only">
          Producto de la línea {numero}
        </label>
        <Controller
          control={control}
          name={`detalles.${indice}.productoId`}
          render={({ field }) => (
            <select
              id={idProducto}
              ref={field.ref}
              name={field.name}
              onBlur={field.onBlur}
              value={Number.isFinite(field.value) ? String(field.value) : ''}
              onChange={(event) =>
                field.onChange(event.target.value === '' ? Number.NaN : Number(event.target.value))
              }
              disabled={cargando}
              aria-invalid={errorProducto ? true : undefined}
              aria-describedby={errorProducto ? `${idProducto}-error` : undefined}
              className="u-input"
            >
              <option value="">{cargando ? 'Cargando productos…' : 'Selecciona un producto'}</option>
              {productos.map((opcion) => (
                <option key={opcion.id} value={opcion.id}>
                  {opcion.nombre} · {opcion.sku} · {formatearCOP(opcion.precio)}
                </option>
              ))}
            </select>
          )}
        />
        {errorProducto && (
          <p id={`${idProducto}-error`} className="u-field-error">
            {errorProducto}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <label htmlFor={idCantidad} className="text-xs font-medium text-foreground-muted sm:sr-only">
          Cantidad de la línea {numero}
        </label>
        <input
          id={idCantidad}
          {...register(`detalles.${indice}.cantidad`, { valueAsNumber: true })}
          type="number"
          inputMode="numeric"
          min="1"
          step="1"
          aria-invalid={errorCantidad ? true : undefined}
          aria-describedby={errorCantidad ? `${idCantidad}-error` : undefined}
          className="u-input w-28 tabular-nums sm:w-full"
        />
        {errorCantidad && (
          <p id={`${idCantidad}-error`} className="u-field-error">
            {errorCantidad}
          </p>
        )}
      </div>

      <p
        className={cn(
          'self-end pb-3 text-right text-sm tabular-nums sm:self-start sm:pt-3 sm:pb-0',
          subtotal === null ? 'text-foreground-muted' : 'font-medium text-foreground',
        )}
      >
        <span className="sr-only">Subtotal de la línea {numero}: </span>
        {subtotal === null ? '—' : formatearCOP(subtotal)}
      </p>

      <button
        type="button"
        onClick={onQuitar}
        disabled={!puedeQuitar}
        className="col-start-2 row-start-1 mt-5 flex size-10 items-center justify-center self-start justify-self-end rounded-md text-foreground-muted hover:bg-danger-surface hover:text-danger disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-foreground-muted sm:col-start-auto sm:row-start-auto sm:mt-0 sm:size-11"
        aria-label={`Quitar la línea ${numero}`}
        title={puedeQuitar ? 'Quitar línea' : 'La solicitud necesita al menos un producto'}
      >
        <Trash2 className="size-4" aria-hidden="true" />
      </button>
    </li>
  )
}

/** Total orientativo con los precios actuales del catálogo; se recalcula sin repintar las líneas. */
function TotalEstimado({
  control,
  porId,
}: {
  control: Control<SolicitudFormValues, unknown, SolicitudPayload>
  porId: Map<number, Producto>
}) {
  const detalles = useWatch({ control, name: 'detalles' })
  const total = detalles.reduce((suma, { productoId, cantidad }) => {
    const producto = porId.get(productoId)
    return producto && Number.isInteger(cantidad) && cantidad > 0 ? suma + producto.precio * cantidad : suma
  }, 0)

  return (
    <p className="text-right text-sm text-foreground-muted" aria-live="polite">
      Total estimado{' '}
      <strong className="ml-1 text-lg font-semibold text-foreground tabular-nums">{formatearCOP(total)}</strong>
      <span className="block text-xs">Con los precios actuales del catálogo.</span>
    </p>
  )
}
