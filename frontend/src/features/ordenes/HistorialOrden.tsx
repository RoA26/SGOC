import {
  AlertTriangle,
  Ban,
  CalendarClock,
  CalendarSync,
  CheckCircle2,
  ClipboardCheck,
  ClipboardList,
  FileText,
  FilePen,
  PackageCheck,
  RotateCw,
  Send,
  type LucideIcon,
} from 'lucide-react'
import { useCallback } from 'react'
import { useRecurso } from '@/hooks/useRecurso'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearNumero } from '@/lib/numeros'
import { ordenCompraService, type EventoHistorial, type TipoEventoHistorial } from '@/services/ordenCompraService'
import { ETIQUETA_INCIDENCIA } from '@/features/entregas/etiquetas'

const META: Record<TipoEventoHistorial, { titulo: string; Icono: LucideIcon }> = {
  SOLICITUD_CREADA: { titulo: 'Solicitud creada', Icono: ClipboardList },
  SOLICITUD_APROBADA: { titulo: 'Solicitud aprobada', Icono: ClipboardCheck },
  ORDEN_CREADA: { titulo: 'Orden creada', Icono: FilePen },
  ORDEN_EMITIDA: { titulo: 'Orden emitida', Icono: Send },
  DOCUMENTO_GENERADO: { titulo: 'Documento Word', Icono: FileText },
  ENTREGA_PROGRAMADA: { titulo: 'Entrega programada', Icono: CalendarClock },
  ENTREGA_REPROGRAMADA: { titulo: 'Entrega reprogramada', Icono: CalendarSync },
  RECEPCION_PARCIAL: { titulo: 'Recepción parcial', Icono: PackageCheck },
  RECEPCION_FINAL: { titulo: 'Recepción final', Icono: CheckCircle2 },
  RECEPCION_CON_INCIDENCIAS: { titulo: 'Entrega con solo incidencias', Icono: AlertTriangle },
  ORDEN_CANCELADA: { titulo: 'Orden cancelada', Icono: Ban },
}

/**
 * Secuencia de la orden tal como la reconstruye el backend con los datos guardados: solicitud y
 * aprobación, emisión y documento, programaciones, recepciones con sus incidencias y los
 * movimientos de kárdex que causaron, y la cancelación si la hubo.
 */
export function HistorialOrden({ ordenId, version }: { ordenId: number; version: number }) {
  const pedir = useCallback((signal: AbortSignal) => ordenCompraService.historial(ordenId, signal), [ordenId])
  const { datos, error, recargar } = useRecurso(pedir, `historial-${ordenId}-${version}`)

  return (
    <section aria-labelledby="historial-orden" className="mt-8">
      <h2 id="historial-orden" className="font-medium text-foreground">
        Historial
      </h2>
      {error && (
        <div role="alert" className="u-alert u-alert--error mt-3 flex flex-wrap items-center justify-between gap-3">
          {error}
          <button type="button" className="u-btn u-btn--ghost h-8 text-sm" onClick={recargar}>
            <RotateCw className="size-4" aria-hidden="true" />
            Reintentar
          </button>
        </div>
      )}
      {!datos && !error && <p className="mt-2 text-sm text-foreground-muted">Cargando el historial…</p>}
      {datos && (
        <ol className="mt-3 space-y-0 border-l border-border pl-5">
          {datos.map((evento, indice) => (
            <Evento key={`${evento.tipo}-${evento.instante}-${indice}`} evento={evento} />
          ))}
        </ol>
      )}
    </section>
  )
}

function Evento({ evento }: { evento: EventoHistorial }) {
  const { titulo, Icono } = META[evento.tipo]
  return (
    <li className="relative pb-5 text-sm last:pb-0">
      <span className="absolute top-0.5 -left-[1.95rem] flex size-6 items-center justify-center rounded-full border border-border bg-surface">
        <Icono className="size-3.5 text-foreground-muted" aria-hidden="true" />
      </span>
      <p className="font-medium text-foreground">
        {titulo}
        <span className="font-normal text-foreground-muted">
          {' '}
          · {formatearFechaHora(evento.instante)}
          {evento.usuario && ` · ${evento.usuario.nombre}`}
        </span>
      </p>
      <p className="text-foreground-muted">{evento.descripcion}</p>
      {evento.documento && (
        <p className="font-mono text-xs text-foreground-muted" title={evento.documento.huellaSha256}>
          SHA-256 {evento.documento.huellaSha256.slice(0, 16)}…
        </p>
      )}
      {evento.recepcion && evento.recepcion.incidencias.length > 0 && (
        <ul className="mt-1 space-y-0.5 text-xs text-warning">
          {evento.recepcion.incidencias.map((i) => (
            <li key={i.id}>
              {ETIQUETA_INCIDENCIA[i.tipo]}
              {i.producto && ` · ${i.producto.nombre}`}
              {i.cantidad !== undefined && ` · ${formatearNumero(i.cantidad)} u.`}
              {i.descripcion && ` · ${i.descripcion}`}
            </li>
          ))}
        </ul>
      )}
      {evento.movimientos && evento.movimientos.length > 0 && (
        <ul className="mt-1 space-y-0.5 text-xs text-foreground-muted">
          {evento.movimientos.map((m) => (
            <li key={m.id}>
              Kárdex #{m.id}: {m.producto.nombre} +{formatearNumero(m.cantidad)} → saldo {formatearNumero(m.saldoResultante)}
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}
