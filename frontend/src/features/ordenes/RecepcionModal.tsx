import { Loader2, PackageCheck } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { Modal } from '@/components/ui/Modal'
import { nuevaClaveIdempotencia } from '@/lib/claves'
import { cn } from '@/lib/cn'
import { getApiErrorMessage } from '@/lib/errors'
import { formatearNumero } from '@/lib/numeros'
import { numeroOrden, ordenCompraService, type OrdenCompra, type Recepcion } from '@/services/ordenCompraService'

const SIN_FLECHAS =
  '[appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none'

/**
 * Registro de mercancía recibida (parcial o total). Cada línea propone lo pendiente; con 0 no se
 * registra. La clave de idempotencia se genera al abrir el formulario y se reutiliza si hay que
 * reintentar: aunque la primera petición llegara al servidor, la mercancía no se duplica.
 */
export function RecepcionModal({
  orden,
  onClose,
  onRegistrada,
}: {
  orden: OrdenCompra
  onClose: () => void
  onRegistrada: (recepcion: Recepcion) => void
}) {
  const pendientes = orden.detalles.filter((linea) => linea.pendiente > 0)
  const [clave] = useState(nuevaClaveIdempotencia)
  const [cantidades, setCantidades] = useState<Record<number, string>>(() =>
    Object.fromEntries(pendientes.map((linea) => [linea.id, String(linea.pendiente)])),
  )
  const [observaciones, setObservaciones] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const formId = useId()

  const lineas = pendientes.map((linea) => {
    const texto = cantidades[linea.id] ?? ''
    const valor = texto === '' ? 0 : Number(texto)
    const invalida = !Number.isInteger(valor) || valor < 0 || valor > linea.pendiente
    return { linea, valor, invalida }
  })
  const aRecibir = lineas.filter((l) => !l.invalida && l.valor > 0)
  const hayInvalidas = lineas.some((l) => l.invalida)

  async function registrar(event: FormEvent) {
    event.preventDefault()
    if (hayInvalidas || aRecibir.length === 0) return
    setEnviando(true)
    setError(null)
    try {
      onRegistrada(
        await ordenCompraService.registrarRecepcion(orden.id, {
          claveIdempotencia: clave,
          observaciones: observaciones.trim() || null,
          detalles: aRecibir.map(({ linea, valor }) => ({ detalleOrdenId: linea.id, cantidad: valor })),
        }),
      )
    } catch (err) {
      // 400: supera lo pendiente (p. ej. otra persona registró antes). 409: la orden ya no admite recepciones.
      setError(getApiErrorMessage(err))
      setEnviando(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={`Registrar recepción · ${numeroOrden(orden.numero)}`}
      description={`${orden.proveedor.razonSocial}. Las unidades se suman a las existencias y quedan en el kárdex.`}
      dismissible={!enviando}
      size="lg"
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onClose} disabled={enviando}>
            Cancelar
          </button>
          <button
            type="submit"
            form={formId}
            className="u-btn u-btn--primary"
            disabled={enviando || hayInvalidas || aRecibir.length === 0}
            aria-busy={enviando}
          >
            {enviando ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <PackageCheck className="size-4" aria-hidden="true" />}
            {error ? 'Reintentar' : 'Registrar recepción'}
          </button>
        </>
      }
    >
      <form id={formId} onSubmit={registrar} noValidate className="space-y-5">
        {error && (
          <p role="alert" className="u-alert u-alert--error">
            {error}
          </p>
        )}
        <table className="w-full text-left text-sm">
          <caption className="sr-only">Unidades recibidas por línea</caption>
          <thead className="text-xs text-foreground-muted">
            <tr>
              <th scope="col" className="pb-2 font-medium">
                Producto
              </th>
              <th scope="col" className="pb-2 text-right font-medium">
                Pendiente
              </th>
              <th scope="col" className="w-32 pb-2 text-right font-medium">
                Recibido ahora
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {lineas.map(({ linea, invalida }) => (
              <tr key={linea.id}>
                <td className="py-2 pr-3">
                  <p className="text-foreground">{linea.producto.nombre}</p>
                  <p className="font-mono text-xs text-foreground-muted">{linea.producto.sku}</p>
                </td>
                <td className="py-2 text-right tabular-nums text-foreground-muted">
                  {formatearNumero(linea.pendiente)} de {formatearNumero(linea.cantidad)}
                </td>
                <td className="py-2 pl-3">
                  <input
                    type="number"
                    inputMode="numeric"
                    min={0}
                    max={linea.pendiente}
                    value={cantidades[linea.id] ?? ''}
                    onChange={(event) => setCantidades((actual) => ({ ...actual, [linea.id]: event.target.value }))}
                    onWheel={(event) => event.currentTarget.blur()}
                    aria-label={`Unidades recibidas de ${linea.producto.nombre}`}
                    aria-invalid={invalida || undefined}
                    className={cn('u-input text-right tabular-nums', SIN_FLECHAS)}
                  />
                  {invalida && <p className="u-field-error mt-1 text-right">Entre 0 y {formatearNumero(linea.pendiente)}</p>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="space-y-1.5">
          <label htmlFor={`${formId}-obs`} className="u-label">
            Observaciones
          </label>
          <textarea
            id={`${formId}-obs`}
            value={observaciones}
            onChange={(event) => setObservaciones(event.target.value)}
            maxLength={500}
            rows={2}
            className="u-input h-auto py-2.5"
            placeholder="Remisión del proveedor, novedades de la entrega…"
          />
        </div>
      </form>
    </Modal>
  )
}
