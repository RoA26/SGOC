import { ArrowLeft, Ban, CheckCircle2, Eye, Loader2, PackageCheck, Pencil, Send } from 'lucide-react'
import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { usePermisos } from '@/features/auth/permisos'
import { CancelarOrdenModal } from '@/features/ordenes/CancelarOrdenModal'
import { EstadoOrdenBadge } from '@/features/ordenes/EstadoOrdenBadge'
import { OrdenFormModal } from '@/features/ordenes/OrdenFormModal'
import { RecepcionModal } from '@/features/ordenes/RecepcionModal'
import { useAviso } from '@/hooks/useAviso'
import { useRecurso } from '@/hooks/useRecurso'
import { getApiErrorMessage } from '@/lib/errors'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearCOP } from '@/lib/moneda'
import { formatearNumero } from '@/lib/numeros'
import { numeroOrden, ordenCompraService, type OrdenCompra } from '@/services/ordenCompraService'

type Dialogo = 'editar' | 'emitir' | 'cancelar' | 'recibir' | null

/**
 * Ficha de una orden de compra: líneas con lo recibido y lo pendiente, su historial de
 * recepciones y las acciones que admite su estado (las valida el backend; aquí solo se ofrecen
 * las que tienen sentido). El SUPER_ADMIN la consulta en solo lectura.
 */
export default function OrdenCompraDetalle() {
  const id = Number(useParams().id)
  const permisos = usePermisos()
  const location = useLocation()
  const { aviso, mostrarAviso } = useAviso()
  const [dialogo, setDialogo] = useState<Dialogo>(null)
  const [emitiendo, setEmitiendo] = useState(false)
  const [errorEmision, setErrorEmision] = useState<string | null>(null)

  const pedirOrden = useCallback((signal: AbortSignal) => ordenCompraService.obtener(id, signal), [id])
  const { datos: cargada, error, recargar } = useRecurso(pedirOrden, `orden-${id}`)
  const pedirRecepciones = useCallback((signal: AbortSignal) => ordenCompraService.recepciones(id, signal), [id])
  const recepciones = useRecurso(pedirRecepciones, `recepciones-${id}`)
  // Tras una acción se muestra la orden que devolvió el backend sin esperar a recargarla.
  const [actualizada, setActualizada] = useState<OrdenCompra | null>(null)
  const orden = actualizada ?? cargada

  const avisoInicial = (location.state as { aviso?: string } | null)?.aviso
  useEffect(() => {
    if (avisoInicial) mostrarAviso(avisoInicial)
  }, [avisoInicial, mostrarAviso])

  function trasAccion(nueva: OrdenCompra, mensaje: string) {
    setActualizada(nueva)
    setDialogo(null)
    mostrarAviso(mensaje)
  }

  async function emitir() {
    if (!orden) return
    setEmitiendo(true)
    setErrorEmision(null)
    try {
      trasAccion(await ordenCompraService.emitir(orden.id), `Orden ${numeroOrden(orden.numero)} emitida.`)
    } catch (err) {
      setErrorEmision(getApiErrorMessage(err))
    } finally {
      setEmitiendo(false)
    }
  }

  if (!orden) {
    return error ? (
      <div className="space-y-4">
        <Volver />
        <p role="alert" className="u-alert u-alert--error">
          {error}
        </p>
      </div>
    ) : (
      <p className="flex items-center gap-2 text-sm text-foreground-muted">
        <Loader2 className="size-4 animate-spin" aria-hidden="true" />
        Cargando la orden…
      </p>
    )
  }

  const gestionar = permisos.gestionarCompras
  const borrador = orden.estado === 'BORRADOR'
  const admiteRecepcion = orden.estado === 'EMITIDA' || orden.estado === 'RECIBIDA_PARCIALMENTE'
  const cancelable = borrador || orden.estado === 'EMITIDA'

  return (
    <>
      <Volver />
      <div className="mt-4 mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-medium tracking-widest text-foreground-muted uppercase">Orden de compra</p>
          <h1 className="mt-2 flex flex-wrap items-center gap-3 font-serif text-4xl text-foreground">
            {numeroOrden(orden.numero)}
            <EstadoOrdenBadge estado={orden.estado} />
          </h1>
          <p className="mt-2 text-sm text-foreground-muted">
            {orden.proveedor.razonSocial} · NIT {orden.proveedor.nit}
            {!orden.proveedor.activo && ' (proveedor dado de baja)'}
          </p>
        </div>
        {gestionar ? (
          <div className="flex flex-wrap gap-2">
            {borrador && (
              <>
                <button type="button" className="u-btn u-btn--ghost" onClick={() => setDialogo('editar')}>
                  <Pencil className="size-4" aria-hidden="true" />
                  Editar
                </button>
                <button
                  type="button"
                  className="u-btn u-btn--primary"
                  onClick={() => {
                    setErrorEmision(null)
                    setDialogo('emitir')
                  }}
                >
                  <Send className="size-4" aria-hidden="true" />
                  Emitir
                </button>
              </>
            )}
            {admiteRecepcion && (
              <button type="button" className="u-btn u-btn--primary" onClick={() => setDialogo('recibir')}>
                <PackageCheck className="size-4" aria-hidden="true" />
                Registrar recepción
              </button>
            )}
            {cancelable && (
              <button type="button" className="u-btn u-btn--ghost text-danger" onClick={() => setDialogo('cancelar')}>
                <Ban className="size-4" aria-hidden="true" />
                Cancelar
              </button>
            )}
          </div>
        ) : (
          <p className="flex items-center gap-2 text-sm text-foreground-muted">
            <Eye className="size-4" aria-hidden="true" />
            Consulta de solo lectura
          </p>
        )}
      </div>

      <div role="status" aria-live="polite">
        {aviso && (
          <p className="u-alert u-alert--success mb-6 flex items-center gap-2">
            <CheckCircle2 className="size-4 shrink-0" aria-hidden="true" />
            {aviso}
          </p>
        )}
      </div>

      {orden.estado === 'CANCELADA' && (
        <p className="u-alert u-alert--error mb-6">
          Cancelada por {orden.canceladaPor?.nombre} el {orden.canceladaEn && formatearFechaHora(orden.canceladaEn)}:{' '}
          {orden.motivoCancelacion}
        </p>
      )}

      <dl className="u-card mb-6 grid gap-x-6 gap-y-4 p-5 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <Dato titulo="Creada">
          {formatearFechaHora(orden.creadoEn)} · {orden.creadoPor.nombre}
        </Dato>
        <Dato titulo="Emitida">{orden.emitidaEn ? `${formatearFechaHora(orden.emitidaEn)} · ${orden.emitidaPor?.nombre}` : 'Aún no'}</Dato>
        <Dato titulo="Recibido">
          {formatearNumero(orden.unidadesRecibidas)} de {formatearNumero(orden.unidadesPedidas)} unidades
        </Dato>
        <Dato titulo="Origen">
          {orden.solicitudId ? (
            <Link to={`/solicitudes?ver=${orden.solicitudId}`} className="font-medium text-foreground hover:underline">
              Solicitud #{orden.solicitudId}
            </Link>
          ) : (
            'Creada directamente'
          )}
        </Dato>
        {orden.observaciones && (
          <div className="sm:col-span-2 lg:col-span-4">
            <dt className="text-xs text-foreground-muted">Observaciones</dt>
            <dd className="mt-0.5 whitespace-pre-line text-foreground">{orden.observaciones}</dd>
          </div>
        )}
      </dl>

      <section aria-labelledby="lineas" className="u-card overflow-hidden">
        <h2 id="lineas" className="sr-only">
          Líneas de la orden
        </h2>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-surface-muted/60 text-xs tracking-wide text-foreground-muted uppercase">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">
                  Producto
                </th>
                <th scope="col" className="px-4 py-3 text-right font-medium">
                  Cantidad
                </th>
                <th scope="col" className="px-4 py-3 text-right font-medium">
                  Costo unitario
                </th>
                <th scope="col" className="px-4 py-3 text-right font-medium">
                  Subtotal
                </th>
                <th scope="col" className="px-4 py-3 text-right font-medium">
                  Recibido
                </th>
                <th scope="col" className="px-4 py-3 text-right font-medium">
                  Pendiente
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {orden.detalles.map((linea) => (
                <tr key={linea.id}>
                  <td className="px-4 py-3">
                    <p className="text-foreground">
                      {linea.producto.nombre}
                      {!linea.producto.activo && <span className="ml-2 text-xs text-foreground-muted">(dado de baja)</span>}
                    </p>
                    <p className="font-mono text-xs text-foreground-muted">
                      {linea.producto.sku}
                      {linea.producto.codigoBarras && ` · ${linea.producto.codigoBarras}`}
                    </p>
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums">{formatearNumero(linea.cantidad)}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{formatearCOP(linea.costoUnitario)}</td>
                  <td className="px-4 py-3 text-right font-medium tabular-nums">{formatearCOP(linea.subtotal)}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{formatearNumero(linea.cantidadRecibida)}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{formatearNumero(linea.pendiente)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="border-t border-border">
              <tr>
                <th scope="row" colSpan={3} className="px-4 py-3 text-right font-medium text-foreground-muted">
                  Total
                </th>
                <td className="px-4 py-3 text-right font-semibold tabular-nums">{formatearCOP(orden.total)}</td>
                <td colSpan={2} />
              </tr>
            </tfoot>
          </table>
        </div>
      </section>

      <section aria-labelledby="historial" className="mt-8">
        <h2 id="historial" className="font-medium text-foreground">
          Recepciones
        </h2>
        {recepciones.error && (
          <p role="alert" className="u-alert u-alert--error mt-3">
            {recepciones.error}
          </p>
        )}
        {recepciones.datos && recepciones.datos.length === 0 && (
          <p className="mt-2 text-sm text-foreground-muted">
            {admiteRecepcion ? 'Aún no se ha recibido mercancía de esta orden.' : 'Sin recepciones.'}
          </p>
        )}
        {recepciones.datos && recepciones.datos.length > 0 && (
          <ul className="u-card mt-3 divide-y divide-border">
            {recepciones.datos.map((recepcion) => (
              <li key={recepcion.id} className="px-5 py-3 text-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-medium text-foreground">
                    {formatearFechaHora(recepcion.recibidaEn)} · {recepcion.recibidaPor.nombre}
                  </p>
                  <p className="text-foreground-muted tabular-nums">
                    {formatearNumero(recepcion.unidades)} unidades · {formatearCOP(recepcion.valor)}
                  </p>
                </div>
                <p className="mt-1 text-foreground-muted">
                  {recepcion.detalles.map((d) => `${d.producto.nombre} × ${formatearNumero(d.cantidad)}`).join(' · ')}
                </p>
                {recepcion.observaciones && <p className="mt-1 text-xs text-foreground-muted">{recepcion.observaciones}</p>}
              </li>
            ))}
          </ul>
        )}
      </section>

      {dialogo === 'editar' && (
        <OrdenFormModal orden={orden} onClose={() => setDialogo(null)} onSaved={(nueva) => trasAccion(nueva, 'Cambios guardados.')} />
      )}
      <ConfirmDialog
        open={dialogo === 'emitir'}
        title={`Emitir ${numeroOrden(orden.numero)}`}
        confirmLabel="Emitir orden"
        busy={emitiendo}
        error={errorEmision}
        onConfirm={() => void emitir()}
        onCancel={() => setDialogo(null)}
      >
        <p>
          Se enviará a {orden.proveedor.razonSocial} por {formatearCOP(orden.total)}. Desde ese momento sus líneas no se podrán
          modificar; solo recibir o cancelar (mientras no llegue mercancía).
        </p>
      </ConfirmDialog>
      {dialogo === 'cancelar' && (
        <CancelarOrdenModal
          orden={orden}
          onClose={() => setDialogo(null)}
          onCancelada={(nueva) => trasAccion(nueva, `Orden ${numeroOrden(nueva.numero)} cancelada.`)}
        />
      )}
      {dialogo === 'recibir' && (
        <RecepcionModal
          orden={orden}
          onClose={() => setDialogo(null)}
          onRegistrada={(recepcion) => {
            setDialogo(null)
            setActualizada(null)
            recargar()
            recepciones.recargar()
            mostrarAviso(`Recepción registrada: ${formatearNumero(recepcion.unidades)} unidades sumadas a las existencias.`)
          }}
        />
      )}
    </>
  )
}

function Volver() {
  return (
    <Link to="/ordenes-compra" className="inline-flex items-center gap-1 text-sm text-foreground-muted hover:text-foreground">
      <ArrowLeft className="size-4" aria-hidden="true" />
      Órdenes de compra
    </Link>
  )
}

function Dato({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-foreground-muted">{titulo}</dt>
      <dd className="mt-0.5 text-foreground">{children}</dd>
    </div>
  )
}
