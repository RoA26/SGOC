import { CalendarPlus, ClipboardList, FileText, PackageOpen } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Modal } from '@/components/ui/Modal'
import { usePermisos } from '@/features/auth/permisos'
import { ProgramarEntregaModal } from '@/features/entregas/ProgramarEntregaModal'
import { RecepcionDetalleModal } from '@/features/entregas/RecepcionDetalle'
import { formatearDiaLargo } from '@/lib/dias'
import type { EventoCalendario } from '@/services/calendarioService'
import { entregaService } from '@/services/entregaService'
import type { ProgramacionEntrega } from '@/services/ordenCompraService'
import { getApiErrorMessage } from '@/lib/errors'
import { estiloEvento } from './estilos'
import { ETIQUETA_ESTADO_EVENTO, ETIQUETA_TIPO, horaDeEvento } from './filtros'

/**
 * Detalle de un evento con las acciones de cada rol: el gerente abre la orden y reprograma; el
 * trabajador va a registrar la recepción. Las acciones solo se ofrecen: el backend las autoriza.
 */
export function DetalleEventoModal({
  evento,
  onClose,
  onCambio,
}: {
  evento: EventoCalendario
  onClose: () => void
  /** Tras reprogramar: el calendario se recarga. */
  onCambio: (mensaje: string) => void
}) {
  const permisos = usePermisos()
  const [reprogramando, setReprogramando] = useState<ProgramacionEntrega | 'nueva' | null>(null)
  const [cargandoVigente, setCargandoVigente] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { Icono } = estiloEvento(evento)
  const hora = horaDeEvento(evento)
  const abierta = evento.tipo === 'ENTREGA' && evento.estado !== 'COMPLETA'

  if (evento.tipo === 'RECEPCION' && evento.recepcionId !== undefined) {
    return (
      <RecepcionDetalleModal
        recepcionId={evento.recepcionId}
        enlaceOrden={(id) => (permisos.verCompras ? `/ordenes-compra/${id}` : `/recepcion/${id}`)}
        onClose={onClose}
      />
    )
  }

  async function abrirReprogramacion() {
    if (evento.ordenId === undefined) return
    setCargandoVigente(true)
    setError(null)
    try {
      // La programación vigente se lee al momento: puede haber cambiado desde que se cargó el calendario.
      const detalle = await entregaService.detalle(evento.ordenId)
      setReprogramando(detalle.entrega.programacion ?? 'nueva')
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setCargandoVigente(false)
    }
  }

  if (reprogramando && evento.ordenId !== undefined && evento.ordenNumero !== undefined) {
    return (
      <ProgramarEntregaModal
        ordenId={evento.ordenId}
        numero={evento.ordenNumero}
        proveedor={evento.proveedor ?? ''}
        vigente={reprogramando === 'nueva' ? undefined : reprogramando}
        onClose={() => setReprogramando(null)}
        onGuardada={(p) => onCambio(`${evento.titulo}: nueva fecha guardada (versión ${p.version}).`)}
      />
    )
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={evento.titulo}
      description={ETIQUETA_TIPO[evento.tipo]}
      footer={
        <>
          {evento.tipo === 'SOLICITUD_PENDIENTE' && evento.solicitudId !== undefined && (
            <Link to={`/solicitudes?ver=${evento.solicitudId}`} className="u-btn u-btn--ghost">
              <ClipboardList className="size-4" aria-hidden="true" />
              Ver solicitud
            </Link>
          )}
          {evento.ordenId !== undefined && permisos.verCompras && (
            <Link to={`/ordenes-compra/${evento.ordenId}`} className="u-btn u-btn--ghost">
              <FileText className="size-4" aria-hidden="true" />
              Ver orden
            </Link>
          )}
          {abierta && permisos.programarEntregas && (
            <button type="button" className="u-btn u-btn--ghost" onClick={() => void abrirReprogramacion()} disabled={cargandoVigente}>
              <CalendarPlus className="size-4" aria-hidden="true" />
              Reprogramar
            </button>
          )}
          {abierta && permisos.registrarRecepciones && evento.ordenId !== undefined && (
            <Link to={`/recepcion/${evento.ordenId}`} className="u-btn u-btn--primary">
              <PackageOpen className="size-4" aria-hidden="true" />
              Registrar recepción
            </Link>
          )}
          <button type="button" className="u-btn u-btn--ghost" onClick={onClose}>
            Cerrar
          </button>
        </>
      }
    >
      {error && (
        <p role="alert" className="u-alert u-alert--error mb-4">
          {error}
        </p>
      )}
      <dl className="space-y-3 text-sm">
        <div className="flex items-center gap-2">
          <Icono className="size-4 text-foreground-muted" aria-hidden="true" />
          <span className="font-medium text-foreground">{ETIQUETA_ESTADO_EVENTO[evento.estado] ?? evento.estado}</span>
          {evento.parcial && evento.tipo === 'ENTREGA' && <span className="text-foreground-muted">· ya se recibió una parte</span>}
        </div>
        <div>
          <dt className="text-xs text-foreground-muted">
            {evento.tipo === 'ENTREGA' ? 'Llegada estimada' : evento.tipo === 'ORDEN_EMITIDA' ? 'Emitida' : 'Fecha'}
          </dt>
          <dd className="text-foreground first-letter:uppercase">
            {formatearDiaLargo(evento.fecha)} · {hora ?? 'todo el día (sin hora registrada)'}
          </dd>
        </div>
        {evento.proveedor && (
          <div>
            <dt className="text-xs text-foreground-muted">Proveedor</dt>
            <dd className="text-foreground">{evento.proveedor}</dd>
          </div>
        )}
        {evento.detalle && evento.detalle !== evento.proveedor && (
          <div>
            <dt className="text-xs text-foreground-muted">Detalle</dt>
            <dd className="text-foreground">{evento.detalle}</dd>
          </div>
        )}
        {evento.tipo === 'ENTREGA' && (
          <p className="text-xs text-foreground-muted">
            Es la fecha planificada para la llegada, no una recepción confirmada. La recepción real aparece como su propio evento.
          </p>
        )}
      </dl>
    </Modal>
  )
}
