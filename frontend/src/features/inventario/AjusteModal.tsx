import { Loader2 } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { Modal } from '@/components/ui/Modal'
import { nuevaClaveIdempotencia } from '@/lib/claves'
import { cn } from '@/lib/cn'
import { getApiErrorMessage } from '@/lib/errors'
import { formatearNumero } from '@/lib/numeros'
import { inventarioService, type Existencia, type Movimiento } from '@/services/inventarioService'

const SIN_FLECHAS =
  '[appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none'
const MOTIVOS = ['Inventario inicial', 'Conteo físico', 'Merma o daño', 'Devolución al proveedor']

/**
 * Ajuste manual de existencias (ADMINISTRADOR_COMERCIAL) con motivo obligatorio. Las salidas nunca dejan
 * existencias negativas: lo comprueba el backend con la fila bloqueada. La clave de idempotencia
 * evita duplicar el ajuste si se reintenta.
 */
export function AjusteModal({
  existencia,
  onClose,
  onAjustado,
}: {
  existencia: Existencia
  onClose: () => void
  onAjustado: (movimiento: Movimiento) => void
}) {
  const [clave] = useState(nuevaClaveIdempotencia)
  const [sentido, setSentido] = useState<'entrada' | 'salida'>('entrada')
  const [cantidad, setCantidad] = useState('')
  const [motivo, setMotivo] = useState(existencia.cantidad === 0 ? 'Inventario inicial' : '')
  const [costo, setCosto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const id = useId()

  const unidades = Number(cantidad)
  const cantidadValida = Number.isInteger(unidades) && unidades > 0 && unidades <= 999_999
  const excede = sentido === 'salida' && cantidadValida && unidades > existencia.cantidad
  const costoNumero = costo === '' ? undefined : Number(costo)
  const costoValido = costoNumero === undefined || (Number.isInteger(costoNumero) && costoNumero > 0)
  const saldo = cantidadValida ? existencia.cantidad + (sentido === 'entrada' ? unidades : -unidades) : null

  async function guardar(event: FormEvent) {
    event.preventDefault()
    if (!cantidadValida || excede || !motivo.trim() || !costoValido) {
      setError(!motivo.trim() ? 'Indica el motivo del ajuste.' : 'Revisa la cantidad y el costo.')
      return
    }
    setEnviando(true)
    setError(null)
    try {
      onAjustado(
        await inventarioService.ajustar({
          productoId: existencia.productoId,
          cantidad: sentido === 'entrada' ? unidades : -unidades,
          motivo: motivo.trim(),
          costoUnitario: sentido === 'entrada' ? costoNumero : undefined,
          claveIdempotencia: clave,
        }),
      )
    } catch (err) {
      setError(getApiErrorMessage(err))
      setEnviando(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title="Ajustar existencias"
      description={`${existencia.nombre} · ${existencia.sku} · hay ${formatearNumero(existencia.cantidad)} unidades`}
      size="md"
      dismissible={!enviando}
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onClose} disabled={enviando}>
            Cancelar
          </button>
          <button type="submit" form={id} className="u-btn u-btn--primary" disabled={enviando} aria-busy={enviando}>
            {enviando && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
            {error ? 'Reintentar' : 'Registrar ajuste'}
          </button>
        </>
      }
    >
      <form id={id} onSubmit={guardar} noValidate className="space-y-5">
        {error && (
          <p role="alert" className="u-alert u-alert--error">
            {error}
          </p>
        )}
        <fieldset>
          <legend className="u-label mb-2">Tipo de ajuste</legend>
          <div className="grid grid-cols-2 gap-2">
            {(['entrada', 'salida'] as const).map((opcion) => (
              <label
                key={opcion}
                className={cn(
                  'flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2 text-sm',
                  sentido === opcion ? 'border-foreground text-foreground' : 'border-border text-foreground-muted',
                )}
              >
                <input type="radio" name={`${id}-sentido`} checked={sentido === opcion} onChange={() => setSentido(opcion)} />
                {opcion === 'entrada' ? 'Entrada (suma)' : 'Salida (resta)'}
              </label>
            ))}
          </div>
        </fieldset>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <label htmlFor={`${id}-cantidad`} className="u-label">
              Unidades<span className="ml-0.5 text-danger" aria-hidden="true">*</span>
            </label>
            <input
              id={`${id}-cantidad`}
              type="number"
              inputMode="numeric"
              min={1}
              value={cantidad}
              onChange={(event) => setCantidad(event.target.value)}
              onWheel={(event) => event.currentTarget.blur()}
              className={cn('u-input tabular-nums', SIN_FLECHAS)}
              aria-invalid={excede || undefined}
              autoFocus
            />
            <p className={excede ? 'u-field-error' : 'text-xs text-foreground-muted'}>
              {excede
                ? `Solo hay ${formatearNumero(existencia.cantidad)} unidades.`
                : saldo !== null
                  ? `Quedarán ${formatearNumero(saldo)} unidades.`
                  : 'Número entero mayor que 0.'}
            </p>
          </div>
          {sentido === 'entrada' && (
            <div className="space-y-1.5">
              <label htmlFor={`${id}-costo`} className="u-label">
                Costo unitario (COP)
              </label>
              <input
                id={`${id}-costo`}
                type="number"
                inputMode="numeric"
                min={1}
                value={costo}
                onChange={(event) => setCosto(event.target.value)}
                onWheel={(event) => event.currentTarget.blur()}
                className={cn('u-input tabular-nums', SIN_FLECHAS)}
                aria-invalid={!costoValido || undefined}
              />
              <p className={costoValido ? 'text-xs text-foreground-muted' : 'u-field-error'}>
                {costoValido ? 'Opcional. Queda registrado en el kárdex.' : 'Número entero mayor que 0.'}
              </p>
            </div>
          )}
        </div>
        <div className="space-y-1.5">
          <label htmlFor={`${id}-motivo`} className="u-label">
            Motivo<span className="ml-0.5 text-danger" aria-hidden="true">*</span>
          </label>
          <input
            id={`${id}-motivo`}
            value={motivo}
            onChange={(event) => setMotivo(event.target.value)}
            maxLength={300}
            list={`${id}-motivos`}
            className="u-input"
          />
          <datalist id={`${id}-motivos`}>
            {MOTIVOS.map((opcion) => (
              <option key={opcion} value={opcion} />
            ))}
          </datalist>
        </div>
      </form>
    </Modal>
  )
}
