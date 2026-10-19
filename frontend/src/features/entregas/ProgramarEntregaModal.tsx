import { CalendarCheck, Loader2 } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { Modal } from '@/components/ui/Modal'
import { formatearDia, hoyEnBogota } from '@/lib/dias'
import { getApiErrorMessage, getFieldErrors } from '@/lib/errors'
import { numeroOrden, ordenCompraService, type ProgramacionEntrega } from '@/services/ordenCompraService'
import { franja } from './etiquetas'

interface Props {
  ordenId: number
  numero: number
  proveedor: string
  /** Programación vigente, si existe: entonces es una reprogramación y pide motivo. */
  vigente?: ProgramacionEntrega
  onClose: () => void
  onGuardada: (programacion: ProgramacionEntrega) => void
}

/**
 * Programa o reprograma la entrega (ADMIN_EMPRESA). La fecha estimada es un día; la franja es
 * opcional ("todo el día" si no se acordó una hora). Confirmada por el proveedor y estimada son
 * cosas distintas. Cada cambio queda en el historial: reprogramar exige motivo.
 */
export function ProgramarEntregaModal({ ordenId, numero, proveedor, vigente, onClose, onGuardada }: Props) {
  const formId = useId()
  const hoy = hoyEnBogota()
  const [fecha, setFecha] = useState(vigente?.fechaEstimada ?? '')
  const [conFranja, setConFranja] = useState(Boolean(vigente?.horaDesde))
  const [desde, setDesde] = useState(vigente?.horaDesde ?? '08:00')
  const [hasta, setHasta] = useState(vigente?.horaHasta ?? '12:00')
  const [confirmada, setConfirmada] = useState(vigente?.confirmadaProveedor ?? false)
  const [referencia, setReferencia] = useState(vigente?.referenciaProveedor ?? '')
  const [observaciones, setObservaciones] = useState(vigente?.observaciones ?? '')
  const [motivo, setMotivo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})

  const reprogramacion = vigente !== undefined
  const franjaInvalida = conFranja && (!desde || !hasta || desde >= hasta)
  // Una fecha pasada solo se acepta si es la vigente (p. ej. para confirmarla sin moverla).
  const fechaInvalida = !fecha || (fecha < hoy && fecha !== vigente?.fechaEstimada)
  const faltaMotivo = reprogramacion && !motivo.trim()

  async function guardar(event: FormEvent) {
    event.preventDefault()
    if (fechaInvalida || franjaInvalida || faltaMotivo) return
    setEnviando(true)
    setError(null)
    setErrores({})
    try {
      onGuardada(
        await ordenCompraService.programarEntrega(ordenId, {
          fechaEstimada: fecha,
          horaDesde: conFranja ? desde : null,
          horaHasta: conFranja ? hasta : null,
          confirmadaProveedor: confirmada,
          referenciaProveedor: referencia.trim() || null,
          observaciones: observaciones.trim() || null,
          motivo: motivo.trim() || null,
        }),
      )
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getApiErrorMessage(err))
      setEnviando(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={`${reprogramacion ? 'Reprogramar' : 'Programar'} entrega · ${numeroOrden(numero)}`}
      description={`${proveedor}. Es la fecha en que se espera la mercancía; la recepción real la registra quien la recibe.`}
      dismissible={!enviando}
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onClose} disabled={enviando}>
            Cancelar
          </button>
          <button
            type="submit"
            form={formId}
            className="u-btn u-btn--primary"
            disabled={enviando || fechaInvalida || franjaInvalida || faltaMotivo}
            aria-busy={enviando}
          >
            {enviando ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <CalendarCheck className="size-4" aria-hidden="true" />}
            {reprogramacion ? 'Guardar nueva fecha' : 'Programar'}
          </button>
        </>
      }
    >
      <form id={formId} onSubmit={guardar} noValidate className="space-y-5">
        {error && (
          <p role="alert" className="u-alert u-alert--error">
            {error}
          </p>
        )}
        {vigente && (
          <p className="rounded-md border border-border bg-surface-muted/60 px-3 py-2 text-sm text-foreground-muted">
            Vigente (versión {vigente.version}): {formatearDia(vigente.fechaEstimada)} · {franja(vigente)} ·{' '}
            {vigente.confirmadaProveedor ? 'confirmada por el proveedor' : 'estimada'}
          </p>
        )}

        <div className="space-y-1.5">
          <label htmlFor={`${formId}-fecha`} className="u-label">
            Fecha estimada de llegada
          </label>
          <input
            id={`${formId}-fecha`}
            type="date"
            required
            min={vigente && vigente.fechaEstimada < hoy ? vigente.fechaEstimada : hoy}
            value={fecha}
            onChange={(event) => setFecha(event.target.value)}
            aria-invalid={(fecha !== '' && fechaInvalida) || Boolean(errores.fechaEstimada) || undefined}
            aria-describedby={`${formId}-fecha-ayuda`}
            className="u-input max-w-56"
          />
          <p id={`${formId}-fecha-ayuda`} className="text-xs text-foreground-muted">
            {errores.fechaEstimada ?? (fecha !== '' && fechaInvalida ? 'La fecha no puede ser anterior a hoy.' : 'Un día; la hora es opcional.')}
          </p>
        </div>

        <fieldset className="space-y-3">
          <legend className="u-label">Franja horaria</legend>
          <div className="flex flex-wrap gap-4 text-sm">
            <label className="inline-flex items-center gap-2">
              <input type="radio" name={`${formId}-franja`} checked={!conFranja} onChange={() => setConFranja(false)} />
              Todo el día (sin hora acordada)
            </label>
            <label className="inline-flex items-center gap-2">
              <input type="radio" name={`${formId}-franja`} checked={conFranja} onChange={() => setConFranja(true)} />
              Entre dos horas
            </label>
          </div>
          {conFranja && (
            <div className="flex flex-wrap items-end gap-3">
              <div className="space-y-1.5">
                <label htmlFor={`${formId}-desde`} className="u-label">
                  Desde
                </label>
                <input id={`${formId}-desde`} type="time" value={desde} onChange={(e) => setDesde(e.target.value)} className="u-input w-32" />
              </div>
              <div className="space-y-1.5">
                <label htmlFor={`${formId}-hasta`} className="u-label">
                  Hasta
                </label>
                <input
                  id={`${formId}-hasta`}
                  type="time"
                  value={hasta}
                  onChange={(e) => setHasta(e.target.value)}
                  aria-invalid={franjaInvalida || undefined}
                  className="u-input w-32"
                />
              </div>
              {franjaInvalida && <p className="u-field-error pb-2">La hora final debe ser posterior a la inicial.</p>}
            </div>
          )}
        </fieldset>

        <label className="flex items-start gap-2 text-sm">
          <input type="checkbox" className="mt-0.5" checked={confirmada} onChange={(e) => setConfirmada(e.target.checked)} />
          <span>
            <span className="font-medium text-foreground">El proveedor confirmó esta fecha</span>
            <span className="block text-foreground-muted">Si no, queda como una estimación interna.</span>
          </span>
        </label>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <label htmlFor={`${formId}-ref`} className="u-label">
              Referencia del proveedor <span className="font-normal text-foreground-muted">(opcional)</span>
            </label>
            <input
              id={`${formId}-ref`}
              value={referencia}
              onChange={(e) => setReferencia(e.target.value)}
              maxLength={60}
              placeholder="Remisión, guía, pedido…"
              className="u-input"
            />
          </div>
        </div>

        <div className="space-y-1.5">
          <label htmlFor={`${formId}-obs`} className="u-label">
            Observaciones logísticas <span className="font-normal text-foreground-muted">(opcional)</span>
          </label>
          <textarea
            id={`${formId}-obs`}
            value={observaciones}
            onChange={(e) => setObservaciones(e.target.value)}
            maxLength={500}
            rows={2}
            placeholder="Muelle, contacto, condiciones de descarga…"
            className="u-input h-auto py-2.5"
          />
        </div>

        {reprogramacion && (
          <div className="space-y-1.5">
            <label htmlFor={`${formId}-motivo`} className="u-label">
              Motivo del cambio
            </label>
            <input
              id={`${formId}-motivo`}
              value={motivo}
              onChange={(e) => setMotivo(e.target.value)}
              maxLength={300}
              required
              aria-invalid={Boolean(errores.motivo) || undefined}
              placeholder="Queda en el historial de la orden"
              className="u-input"
            />
            {errores.motivo && <p className="u-field-error">{errores.motivo}</p>}
          </div>
        )}
      </form>
    </Modal>
  )
}
