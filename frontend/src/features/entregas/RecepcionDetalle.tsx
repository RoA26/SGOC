import { AlertTriangle, Loader2 } from 'lucide-react'
import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { Modal } from '@/components/ui/Modal'
import { useRecurso } from '@/hooks/useRecurso'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearCOP } from '@/lib/moneda'
import { formatearNumero } from '@/lib/numeros'
import { numeroOrden, ordenCompraService, type Recepcion } from '@/services/ordenCompraService'
import { ETIQUETA_INCIDENCIA } from './etiquetas'

/** Líneas aceptadas e incidencias de una recepción. Los costos solo llegan (y se muestran) para gestión. */
export function RecepcionDetalle({ recepcion }: { recepcion: Recepcion }) {
  return (
    <div className="space-y-5 text-sm">
      <dl className="grid gap-3 sm:grid-cols-3">
        <div>
          <dt className="text-xs text-foreground-muted">Registrada</dt>
          <dd className="text-foreground">{formatearFechaHora(recepcion.recibidaEn)}</dd>
        </div>
        <div>
          <dt className="text-xs text-foreground-muted">Por</dt>
          <dd className="text-foreground">{recepcion.recibidaPor.nombre}</dd>
        </div>
        <div>
          <dt className="text-xs text-foreground-muted">Aceptado</dt>
          <dd className="text-foreground tabular-nums">
            {formatearNumero(recepcion.unidades)} unidades{recepcion.valor !== undefined && ` · ${formatearCOP(recepcion.valor)}`}
          </dd>
        </div>
      </dl>

      <section aria-label="Unidades aceptadas">
        <h3 className="mb-2 text-[0.8125rem] font-medium text-foreground-muted">Entró al inventario</h3>
        {recepcion.detalles.length === 0 ? (
          <p className="text-foreground-muted">Nada: la entrega solo trajo novedades.</p>
        ) : (
          <ul className="divide-y divide-border rounded-md border border-border">
            {recepcion.detalles.map((d) => (
              <li key={d.id} className="flex flex-wrap items-baseline justify-between gap-2 px-3 py-2">
                <span className="text-foreground">
                  {d.producto.nombre} <span className="font-mono text-xs text-foreground-muted">{d.producto.sku}</span>
                </span>
                <span className="text-foreground-muted tabular-nums">
                  {formatearNumero(d.cantidad)} u.{d.costoUnitario !== undefined && ` × ${formatearCOP(d.costoUnitario)}`}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {recepcion.incidencias.length > 0 && (
        <section aria-label="Incidencias">
          <h3 className="mb-2 flex items-center gap-1.5 text-[0.8125rem] font-medium text-warning">
            <AlertTriangle className="size-3.5" aria-hidden="true" />
            Incidencias ({recepcion.incidencias.length})
          </h3>
          <ul className="divide-y divide-border rounded-md border border-warning-border">
            {recepcion.incidencias.map((i) => (
              <li key={i.id} className="px-3 py-2">
                <span className="font-medium text-foreground">{ETIQUETA_INCIDENCIA[i.tipo]}</span>
                {i.producto && <span className="text-foreground"> · {i.producto.nombre}</span>}
                {i.cantidad !== undefined && <span className="text-foreground-muted tabular-nums"> · {formatearNumero(i.cantidad)} u.</span>}
                {i.descripcion && <p className="text-foreground-muted">{i.descripcion}</p>}
              </li>
            ))}
          </ul>
        </section>
      )}

      {recepcion.observaciones && (
        <p className="text-foreground-muted">
          <span className="font-medium text-foreground">Observaciones:</span> {recepcion.observaciones}
        </p>
      )}
    </div>
  )
}

/** Abre una recepción por su id (la pide al backend, que decide si llegan costos). */
export function RecepcionDetalleModal({
  recepcionId,
  enlaceOrden,
  onClose,
}: {
  recepcionId: number
  /** Ruta a la que lleva "Ver la orden" (ficha de gestión o vista de recepción). */
  enlaceOrden?: (ordenId: number) => string
  onClose: () => void
}) {
  const pedir = useCallback((signal: AbortSignal) => ordenCompraService.obtenerRecepcion(recepcionId, signal), [recepcionId])
  const { datos, error } = useRecurso(pedir, `recepcion-${recepcionId}`)
  return (
    <Modal
      open
      onClose={onClose}
      size="lg"
      title={datos ? `Recepción · ${numeroOrden(datos.ordenNumero)}` : 'Recepción'}
      description={datos?.proveedor}
      footer={
        <>
          {datos && enlaceOrden && (
            <Link to={enlaceOrden(datos.ordenId)} className="u-btn u-btn--ghost">
              Ver la orden
            </Link>
          )}
          <button type="button" className="u-btn u-btn--primary" onClick={onClose}>
            Cerrar
          </button>
        </>
      }
    >
      {error && (
        <p role="alert" className="u-alert u-alert--error">
          {error}
        </p>
      )}
      {!datos && !error && (
        <p className="flex items-center gap-2 text-sm text-foreground-muted">
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Cargando…
        </p>
      )}
      {datos && <RecepcionDetalle recepcion={datos} />}
    </Modal>
  )
}
