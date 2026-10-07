import { Check, Loader2, X } from 'lucide-react'
import { useId, useState } from 'react'
import { Modal } from '@/components/ui/Modal'
import { usePuedeRevisarSolicitudes } from '@/features/auth/permisos'
import { getApiErrorMessage } from '@/lib/errors'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearCOP } from '@/lib/moneda'
import { solicitudService, type CambioEstado, type Solicitud } from '@/services/solicitudService'
import { EstadoBadge } from './EstadoBadge'
import { ETIQUETA_ESTADO } from './estados'

interface DetalleSolicitudModalProps {
  solicitud: Solicitud
  onClose: () => void
  /** Tras aprobarla o rechazarla, con la solicitud ya actualizada. */
  onRevisada: (solicitud: Solicitud) => void
}

/**
 * Detalle de una solicitud. A ADMIN y GERENTE, si está pendiente, les permite aprobarla o
 * rechazarla (con motivo obligatorio al rechazar).
 */
export function DetalleSolicitudModal({ solicitud, onClose, onRevisada }: DetalleSolicitudModalProps) {
  const puedeRevisar = usePuedeRevisarSolicitudes()
  const revisable = puedeRevisar && solicitud.estado === 'PENDIENTE'
  const comentarioId = useId()

  const [comentario, setComentario] = useState('')
  const [errorComentario, setErrorComentario] = useState<string | null>(null)
  const [enviando, setEnviando] = useState<CambioEstado['estado'] | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function revisar(estado: CambioEstado['estado']) {
    const motivo = comentario.trim()
    if (estado === 'RECHAZADA' && !motivo) {
      setErrorComentario('Indica el motivo del rechazo.')
      document.getElementById(comentarioId)?.focus()
      return
    }
    setEnviando(estado)
    setError(null)
    try {
      onRevisada(await solicitudService.cambiarEstado(solicitud.id, { estado, comentario: motivo || undefined }))
    } catch (err) {
      // 409: otro gestor la revisó antes. 400: motivo ausente o demasiado largo.
      setError(getApiErrorMessage(err))
      setEnviando(null)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={`Solicitud N.º ${solicitud.id}`}
      description={`${solicitud.solicitante.nombre} (@${solicitud.solicitante.username}) · ${formatearFechaHora(solicitud.fecha)}`}
      dismissible={enviando === null}
      size="xl"
      footer={
        revisable ? (
          <>
            <button type="button" className="u-btn u-btn--ghost" onClick={onClose} disabled={enviando !== null}>
              Cerrar
            </button>
            <button
              type="button"
              className="u-btn bg-danger text-danger-surface hover:opacity-90"
              onClick={() => revisar('RECHAZADA')}
              disabled={enviando !== null}
              aria-busy={enviando === 'RECHAZADA'}
            >
              {enviando === 'RECHAZADA' ? (
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <X className="size-4" aria-hidden="true" />
              )}
              Rechazar
            </button>
            <button
              type="button"
              className="u-btn u-btn--primary"
              onClick={() => revisar('APROBADA')}
              disabled={enviando !== null}
              aria-busy={enviando === 'APROBADA'}
            >
              {enviando === 'APROBADA' ? (
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <Check className="size-4" aria-hidden="true" />
              )}
              Aprobar
            </button>
          </>
        ) : (
          <button type="button" className="u-btn u-btn--ghost" onClick={onClose}>
            Cerrar
          </button>
        )
      }
    >
      <div className="space-y-6 text-sm">
        <div className="flex flex-wrap items-center gap-3">
          <EstadoBadge estado={solicitud.estado} />
          {solicitud.revisadoPor && solicitud.fechaRevision && (
            <span className="text-foreground-muted">
              {ETIQUETA_ESTADO[solicitud.estado]} por {solicitud.revisadoPor.nombre} el{' '}
              {formatearFechaHora(solicitud.fechaRevision)}
            </span>
          )}
        </div>

        {solicitud.comentarioRevision && (
          <blockquote className="border-l-2 border-border-strong pl-3 text-foreground">
            <p className="text-xs font-medium tracking-wide text-foreground-muted uppercase">Comentario de la revisión</p>
            <p className="mt-1 whitespace-pre-line">{solicitud.comentarioRevision}</p>
          </blockquote>
        )}

        <section>
          <h3 className="text-xs font-medium tracking-wide text-foreground-muted uppercase">Justificación</h3>
          <p className="mt-1 whitespace-pre-line text-foreground">{solicitud.justificacion}</p>
        </section>

        <section>
          <h3 className="text-xs font-medium tracking-wide text-foreground-muted uppercase">Productos solicitados</h3>
          <div className="mt-2 overflow-x-auto rounded-lg border border-border">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Líneas de la solicitud {solicitud.id}</caption>
              <thead className="border-b border-border bg-surface-muted/60 text-xs text-foreground-muted">
                <tr>
                  <th scope="col" className="px-3 py-2 font-medium">Producto</th>
                  <th scope="col" className="px-3 py-2 text-right font-medium">Cantidad</th>
                  <th scope="col" className="hidden px-3 py-2 text-right font-medium sm:table-cell">Precio unit.</th>
                  <th scope="col" className="px-3 py-2 text-right font-medium">Subtotal</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {solicitud.detalles.map((detalle) => (
                  <tr key={detalle.id}>
                    <td className="px-3 py-2">
                      <p className="font-medium text-foreground">{detalle.producto.nombre}</p>
                      <p className="font-mono text-xs text-foreground-muted">
                        {detalle.producto.sku}
                        {!detalle.producto.activo && <span className="ml-2 font-sans">· dado de baja</span>}
                      </p>
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums">{detalle.cantidad}</td>
                    <td className="hidden px-3 py-2 text-right tabular-nums sm:table-cell">
                      {formatearCOP(detalle.producto.precio)}
                    </td>
                    <td className="px-3 py-2 text-right font-medium tabular-nums">
                      {formatearCOP(detalle.subtotalEstimado)}
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot className="border-t border-border">
                <tr>
                  <th scope="row" colSpan={2} className="px-3 py-2 text-left font-medium text-foreground-muted sm:hidden">
                    Total estimado
                  </th>
                  <th scope="row" colSpan={3} className="hidden px-3 py-2 text-left font-medium text-foreground-muted sm:table-cell">
                    Total estimado
                  </th>
                  <td className="px-3 py-2 text-right text-base font-semibold tabular-nums">
                    {formatearCOP(solicitud.totalEstimado)}
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
          <p className="mt-1 text-xs text-foreground-muted">Importes orientativos con los precios actuales del catálogo.</p>
        </section>

        {revisable && (
          <section className="space-y-1.5 border-t border-border pt-5">
            <label htmlFor={comentarioId} className="u-label">
              Comentario de la revisión
            </label>
            <textarea
              id={comentarioId}
              value={comentario}
              onChange={(event) => {
                setComentario(event.target.value)
                setErrorComentario(null)
              }}
              maxLength={500}
              rows={2}
              className="u-input h-auto py-2.5"
              aria-invalid={errorComentario ? true : undefined}
              aria-describedby={`${comentarioId}-ayuda`}
            />
            <p id={`${comentarioId}-ayuda`} className={errorComentario ? 'u-field-error' : 'text-xs text-foreground-muted'}>
              {errorComentario ?? 'Opcional al aprobar; obligatorio al rechazar.'}
            </p>
          </section>
        )}

        {error && (
          <p role="alert" className="u-alert u-alert--error">
            {error}
          </p>
        )}
      </div>
    </Modal>
  )
}
