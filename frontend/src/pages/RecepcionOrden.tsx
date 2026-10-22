import { ArrowLeft, CheckCircle2, History, Loader2, PackageOpen, RotateCw } from 'lucide-react'
import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { usePermisos } from '@/features/auth/permisos'
import { ETIQUETA_INCIDENCIA, franja } from '@/features/entregas/etiquetas'
import { FechaEntrega } from '@/features/entregas/FechaEntrega'
import { RecepcionDetalle } from '@/features/entregas/RecepcionDetalle'
import { RegistroRecepcion } from '@/features/entregas/RegistroRecepcion'
import { SituacionEntregaBadge } from '@/features/entregas/SituacionEntregaBadge'
import { EstadoOrdenBadge } from '@/features/ordenes/EstadoOrdenBadge'
import { useRecurso } from '@/hooks/useRecurso'
import { formatearDia } from '@/lib/dias'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearNumero } from '@/lib/numeros'
import { entregaService } from '@/services/entregaService'
import { numeroOrden, type Recepcion } from '@/services/ordenCompraService'

/**
 * Recepción de una orden (vista operativa): qué se espera y cuándo, qué falta por recibir, el
 * registro de la entrega y el historial de recepciones anteriores. Sin costos.
 */
export default function RecepcionOrden() {
  const ordenId = Number(useParams().ordenId)
  const permisos = usePermisos()
  const pedir = useCallback((signal: AbortSignal) => entregaService.detalle(ordenId, signal), [ordenId])
  const { datos, error, cargando, recargar } = useRecurso(pedir, `entrega-${ordenId}`)
  const [registrando, setRegistrando] = useState(false)
  const [resultado, setResultado] = useState<Recepcion | null>(null)

  if (!datos) {
    return (
      <div className="space-y-4">
        <Volver />
        {error ? (
          <div role="alert" className="u-alert u-alert--error flex flex-wrap items-center justify-between gap-3">
            {error}
            <button type="button" className="u-btn u-btn--ghost h-8 text-sm" onClick={recargar}>
              <RotateCw className="size-4" aria-hidden="true" />
              Reintentar
            </button>
          </div>
        ) : (
          <p className="flex items-center gap-2 text-sm text-foreground-muted" role="status">
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Cargando la orden…
          </p>
        )}
      </div>
    )
  }

  const { entrega, lineas, recepciones, programaciones } = datos
  const admiteRecepcion = entrega.estadoOrden === 'EMITIDA' || entrega.estadoOrden === 'RECIBIDA_PARCIALMENTE'

  return (
    <>
      <Volver />
      <div className="mt-4 mb-6">
        <h1 className="flex flex-wrap items-center gap-x-3 gap-y-2 text-[1.75rem] leading-tight font-semibold tracking-tight text-foreground">
            <span className="font-normal text-foreground-muted">Recepción de mercancía</span>
          {numeroOrden(entrega.numero)}
          <SituacionEntregaBadge situacion={entrega.situacion} diasRetraso={entrega.diasRetraso} />
          <EstadoOrdenBadge estado={entrega.estadoOrden} />
        </h1>
        <p className="mt-2 text-sm text-foreground-muted">{entrega.proveedor.razonSocial}</p>
      </div>

      <div role="status" aria-live="polite">
        {resultado && (
          <section className="u-alert u-alert--success mb-6 space-y-1" aria-label="Resultado de la recepción">
            <p className="flex items-center gap-2 font-medium">
              <CheckCircle2 className="size-4 shrink-0" aria-hidden="true" />
              Recepción registrada el {formatearFechaHora(resultado.recibidaEn)} por {resultado.recibidaPor.nombre}.
            </p>
            <p>
              {formatearNumero(resultado.unidades)} unidades entraron al inventario
              {resultado.incidencias.length > 0 &&
                `; novedades: ${resultado.incidencias.map((i) => ETIQUETA_INCIDENCIA[i.tipo].toLowerCase()).join(', ')}`}
              .{' '}
              {/* El estado nuevo llega al recargar la orden: mientras tanto no se muestra el anterior. */}
              {!cargando &&
                (entrega.estadoOrden === 'RECIBIDA'
                  ? 'La orden quedó recibida por completo.'
                  : `Quedan ${formatearNumero(entrega.unidadesPendientes)} unidades por recibir.`)}
            </p>
            <p>
              <Link to="/recepcion?pestana=mias" className="font-medium underline">
                Ver mis recepciones
              </Link>
            </p>
          </section>
        )}
      </div>

      <dl className="u-card mb-6 grid gap-x-6 gap-y-4 p-5 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <dt className="text-xs text-foreground-muted">Llegada estimada</dt>
          <dd className="mt-0.5">
            <FechaEntrega programacion={entrega.programacion} />
          </dd>
        </div>
        <div>
          <dt className="text-xs text-foreground-muted">Por recibir</dt>
          <dd className="mt-0.5 text-foreground tabular-nums">
            {formatearNumero(entrega.unidadesPendientes)} de {formatearNumero(entrega.unidadesPedidas)} unidades
          </dd>
        </div>
        <div>
          <dt className="text-xs text-foreground-muted">Referencia del proveedor</dt>
          <dd className="mt-0.5 text-foreground">{entrega.programacion?.referenciaProveedor ?? '—'}</dd>
        </div>
        <div>
          <dt className="text-xs text-foreground-muted">Última recepción</dt>
          <dd className="mt-0.5 text-foreground">{entrega.ultimaRecepcionEn ? formatearFechaHora(entrega.ultimaRecepcionEn) : 'Ninguna'}</dd>
        </div>
        {entrega.programacion?.observaciones && (
          <div className="sm:col-span-2 lg:col-span-4">
            <dt className="text-xs text-foreground-muted">Indicaciones de entrega</dt>
            <dd className="mt-0.5 whitespace-pre-line text-foreground">{entrega.programacion.observaciones}</dd>
          </div>
        )}
        {datos.observacionesOrden && (
          <div className="sm:col-span-2 lg:col-span-4">
            <dt className="text-xs text-foreground-muted">Observaciones de la orden</dt>
            <dd className="mt-0.5 whitespace-pre-line text-foreground">{datos.observacionesOrden}</dd>
          </div>
        )}
      </dl>

      <section aria-labelledby="registro" className="u-card mb-8 p-5">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 id="registro" className="font-medium text-foreground">
            Registrar entrega
          </h2>
          {!registrando && admiteRecepcion && permisos.registrarRecepciones && (
            <button
              type="button"
              className="u-btn u-btn--primary"
              onClick={() => {
                setResultado(null)
                setRegistrando(true)
              }}
            >
              <PackageOpen className="size-4" aria-hidden="true" />
              {resultado ? 'Registrar otra entrega' : 'Registrar lo que llegó'}
            </button>
          )}
        </div>
        {!admiteRecepcion && (
          <p className="text-sm text-foreground-muted">
            {entrega.estadoOrden === 'RECIBIDA' ? 'Esta orden ya se recibió por completo.' : 'Esta orden no admite recepciones.'}
          </p>
        )}
        {admiteRecepcion && !permisos.registrarRecepciones && (
          <p className="text-sm text-foreground-muted">Consulta de solo lectura: registra la recepción una persona de la empresa.</p>
        )}
        {registrando && (
          <RegistroRecepcion
            key={recepciones.length}
            ordenId={ordenId}
            lineas={lineas}
            onCancelar={() => setRegistrando(false)}
            onRegistrada={(recepcion) => {
              setResultado(recepcion)
              setRegistrando(false)
              recargar()
            }}
          />
        )}
        {!registrando && (
          <div className="overflow-x-auto">
            <table className={`w-full text-left text-sm ${cargando ? 'opacity-60' : ''}`}>
              <caption className="sr-only">Líneas de la orden</caption>
              <thead className="text-xs text-foreground-muted">
                <tr>
                  <th scope="col" className="pb-2 font-medium">
                    Producto
                  </th>
                  <th scope="col" className="pb-2 pl-3 text-right font-medium">
                    Pedido
                  </th>
                  <th scope="col" className="pb-2 pl-3 text-right font-medium">
                    Recibido
                  </th>
                  <th scope="col" className="pb-2 pl-3 text-right font-medium">
                    Pendiente
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {lineas.map((linea) => (
                  <tr key={linea.id}>
                    <td className="py-2 pr-3">
                      <p className="text-foreground">{linea.producto.nombre}</p>
                      <p className="font-mono text-xs text-foreground-muted">{linea.producto.sku}</p>
                    </td>
                    <td className="py-2 pl-3 text-right tabular-nums">{formatearNumero(linea.cantidad)}</td>
                    <td className="py-2 pl-3 text-right tabular-nums">{formatearNumero(linea.cantidadRecibida)}</td>
                    <td className="py-2 pl-3 text-right font-medium tabular-nums">{formatearNumero(linea.pendiente)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section aria-labelledby="anteriores" className="mb-8">
        <h2 id="anteriores" className="mb-3 flex items-center gap-2 font-medium text-foreground">
          <History className="size-4" aria-hidden="true" />
          Recepciones de esta orden
        </h2>
        {recepciones.length === 0 ? (
          <p className="text-sm text-foreground-muted">Aún no se ha recibido mercancía.</p>
        ) : (
          <ol className="space-y-3">
            {[...recepciones].reverse().map((recepcion) => (
              <li key={recepcion.id} className="u-card p-4">
                <RecepcionDetalle recepcion={recepcion} />
              </li>
            ))}
          </ol>
        )}
      </section>

      {programaciones.length > 1 && (
        <section aria-labelledby="fechas">
          <h2 id="fechas" className="mb-3 font-medium text-foreground">
            Cambios de fecha
          </h2>
          <ol className="u-card divide-y divide-border text-sm">
            {programaciones.map((p) => (
              <li key={p.version} className="px-4 py-2">
                <span className="text-foreground">
                  v{p.version}: {formatearDia(p.fechaEstimada)} · {franja(p)}
                </span>
                <span className="text-foreground-muted">
                  {' '}
                  · {p.confirmadaProveedor ? 'confirmada' : 'estimada'} · {p.registradaPor.nombre}
                  {p.motivoCambio && ` · ${p.motivoCambio}`}
                </span>
              </li>
            ))}
          </ol>
        </section>
      )}
    </>
  )
}

function Volver() {
  const permisos = usePermisos()
  const destino = permisos.supervisarEntregas ? '/entregas' : '/recepcion'
  return (
    <Link to={destino} className="inline-flex items-center gap-1 text-sm text-foreground-muted hover:text-foreground">
      <ArrowLeft className="size-4" aria-hidden="true" />
      {permisos.supervisarEntregas ? 'Entregas' : 'Recibir mercancía'}
    </Link>
  )
}
