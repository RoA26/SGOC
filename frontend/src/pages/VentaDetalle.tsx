import { ArrowLeft, Ban, FileDown, Loader2, Undo2 } from 'lucide-react'
import { useCallback, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Modal } from '@/components/ui/Modal'
import { usePermisos } from '@/features/auth/permisos'
import { EstadoVentaBadge } from '@/features/ventas/EstadoVentaBadge'
import { ETIQUETA_MEDIO, etiquetaIva } from '@/features/ventas/etiquetas'
import { useAviso } from '@/hooks/useAviso'
import { useRecurso } from '@/hooks/useRecurso'
import { nuevaClaveIdempotencia } from '@/lib/claves'
import { getApiErrorMessage } from '@/lib/errors'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearImporte, formatearPorcentaje } from '@/lib/moneda'
import { ventaService, type VentaDetalle as Venta } from '@/services/ventaService'

/**
 * Detalle de una venta con su copia histórica (precios, descuentos, IVA y, para la gestión, costos
 * y rentabilidad), pagos, devoluciones y anulación. El comprobante interno se descarga en PDF.
 */
export default function VentaDetalle() {
  const { id } = useParams()
  const ventaId = Number(id)
  const permisos = usePermisos()
  const [version, setVersion] = useState(0)
  const [actualizada, setActualizada] = useState<Venta | null>(null)
  const [dialogo, setDialogo] = useState<'devolver' | 'anular' | null>(null)
  const [descargando, setDescargando] = useState(false)
  const [errorDescarga, setErrorDescarga] = useState<string | null>(null)
  const { aviso, mostrarAviso } = useAviso()
  const pedir = useCallback((signal: AbortSignal) => ventaService.obtener(ventaId, signal), [ventaId])
  const { datos, error } = useRecurso(pedir, `${ventaId}|${version}`)
  const venta = actualizada?.id === ventaId ? actualizada : datos

  async function descargar() {
    setDescargando(true)
    setErrorDescarga(null)
    try {
      mostrarAviso(`Comprobante ${await ventaService.descargarComprobante(ventaId)} descargado.`)
    } catch (err) {
      setErrorDescarga(getApiErrorMessage(err))
    } finally {
      setDescargando(false)
    }
  }

  if (!venta) {
    return error ? (
      <div className="space-y-4">
        <Volver />
        <p role="alert" className="u-alert u-alert--error">{error}</p>
      </div>
    ) : (
      <p className="flex items-center gap-2 text-sm text-foreground-muted"><Loader2 className="size-4 animate-spin" aria-hidden="true" /> Cargando la venta…</p>
    )
  }

  const devolvible = permisos.gestionarVentas && (venta.estado === 'CONFIRMADA' || venta.estado === 'DEVUELTA_PARCIALMENTE')
  const anulable = permisos.gestionarVentas && venta.estado === 'CONFIRMADA'
  const r = venta.rentabilidad

  function trasOperacion(nueva: Venta, mensaje: string) {
    setActualizada(nueva)
    setDialogo(null)
    setVersion((v) => v + 1)
    mostrarAviso(mensaje)
  }

  return (
    <>
      <Volver />
      <div className="mt-4 mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="flex flex-wrap items-center gap-x-3 gap-y-2 text-[1.75rem] leading-tight font-semibold tracking-tight text-foreground">
            <span className="font-normal text-foreground-muted">Venta</span>
            {venta.numeroVisible}
            <EstadoVentaBadge estado={venta.estado} />
          </h1>
          <p className="mt-2 text-sm text-foreground-muted">
            {formatearFechaHora(venta.vendidaEn)} · vendió {venta.vendidaPor.nombre}
            {venta.clienteNombre && ` · cliente ${venta.clienteNombre}`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" className="u-btn u-btn--ghost" onClick={() => void descargar()} disabled={descargando} aria-busy={descargando}>
            {descargando ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <FileDown className="size-4" aria-hidden="true" />}
            Comprobante PDF
          </button>
          {devolvible && (
            <button type="button" className="u-btn u-btn--ghost" onClick={() => setDialogo('devolver')}>
              <Undo2 className="size-4" aria-hidden="true" /> Registrar devolución
            </button>
          )}
          {anulable && (
            <button type="button" className="u-btn u-btn--ghost text-danger" onClick={() => setDialogo('anular')}>
              <Ban className="size-4" aria-hidden="true" /> Anular
            </button>
          )}
        </div>
      </div>

      <div role="status" aria-live="polite">{aviso && <p className="u-alert u-alert--success mb-6">{aviso}</p>}</div>
      {errorDescarga && <p role="alert" className="u-alert u-alert--error mb-6">No se pudo descargar el comprobante: {errorDescarga}</p>}
      {venta.estado === 'ANULADA' && (
        <p className="u-alert u-alert--error mb-6">
          Anulada el {formatearFechaHora(venta.anuladaEn!)} por {venta.anuladaPor?.nombre}. Motivo: {venta.motivoAnulacion}
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="u-card overflow-x-auto">
          <table className="w-full min-w-[36rem] text-sm">
            <caption className="sr-only">Líneas de la venta</caption>
            <thead className="border-b border-border text-xs text-foreground-muted">
              <tr>
                <th scope="col" className="px-4 py-3 text-left font-medium">Producto</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">Cant.</th>
                <th scope="col" className="hidden px-4 py-3 text-right font-medium sm:table-cell">Precio</th>
                <th scope="col" className="hidden px-4 py-3 text-right font-medium md:table-cell">Desc.</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">IVA</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">Total</th>
                {r && <th scope="col" className="hidden px-4 py-3 text-right font-medium lg:table-cell">Costo · utilidad</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {venta.lineas.map((l) => (
                <tr key={l.id}>
                  <td className="px-4 py-3">
                    <p className="font-medium text-foreground">{l.nombre}</p>
                    <p className="font-mono text-xs text-foreground-muted">{l.sku}</p>
                    {l.cantidadDevuelta > 0 && <p className="text-xs text-warning">Devueltas: {l.cantidadDevuelta}</p>}
                    {l.bajoCosto && <p className="text-xs text-danger">Vendido por debajo del costo</p>}
                  </td>
                  <td className="px-4 py-3 text-right">{l.cantidad}</td>
                  <td className="hidden px-4 py-3 text-right sm:table-cell">{formatearImporte(l.precioUnitario)}</td>
                  <td className="hidden px-4 py-3 text-right md:table-cell">
                    {l.descuentoLinea + l.descuentoGeneral > 0 ? `− ${formatearImporte(l.descuentoLinea + l.descuentoGeneral)}` : '—'}
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    {formatearImporte(l.impuesto)}
                    <span className="block text-xs text-foreground-muted">{etiquetaIva(l.tratamientoIva, l.tarifaIva)}</span>
                  </td>
                  <td className="px-4 py-3 text-right font-medium">{formatearImporte(l.total)}</td>
                  {r && (
                    <td className="hidden px-4 py-3 text-right whitespace-nowrap lg:table-cell">
                      {l.costoUnitario === undefined ? <span className="text-foreground-muted">Costo desconocido</span> : formatearImporte(l.costoUnitario * l.cantidad)}
                      <span className="block text-xs text-foreground-muted">
                        {l.utilidad === undefined ? 'Utilidad no disponible' : `${formatearImporte(l.utilidad)} (${formatearPorcentaje(l.margen)})`}
                      </span>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <aside className="grid content-start gap-4">
          <dl className="u-card grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 p-4 text-sm">
            <dt className="text-foreground-muted">Subtotal</dt><dd className="text-right">{formatearImporte(venta.subtotal)}</dd>
            <dt className="text-foreground-muted">Descuentos</dt><dd className="text-right">− {formatearImporte(venta.descuentoLineas + venta.descuentoGeneral)}</dd>
            <dt className="text-foreground-muted">Base gravable</dt><dd className="text-right">{formatearImporte(venta.baseGravable)}</dd>
            {venta.impuestosPorTarifa.map((t) => (
              <div key={`${t.tratamiento}-${t.tarifa}`} className="contents">
                <dt className="text-foreground-muted">{etiquetaIva(t.tratamiento, t.tarifa)}</dt>
                <dd className="text-right">{formatearImporte(t.impuesto)}</dd>
              </div>
            ))}
            <dt className="border-t border-border pt-2 font-semibold text-foreground">Total</dt>
            <dd className="border-t border-border pt-2 text-right text-lg font-semibold">{formatearImporte(venta.total)}</dd>
            {venta.totalDevuelto > 0 && (
              <>
                <dt className="text-foreground-muted">Devuelto</dt><dd className="text-right">− {formatearImporte(venta.totalDevuelto)}</dd>
              </>
            )}
          </dl>
          <div className="u-card p-4 text-sm">
            <h2 className="mb-2 font-medium text-foreground">Pagos</h2>
            <ul className="grid gap-1">
              {venta.pagos.map((p, i) => (
                <li key={i} className="flex justify-between gap-2">
                  <span>{ETIQUETA_MEDIO[p.medio]}{p.referencia && ` · ${p.referencia}`}</span>
                  <span>{formatearImporte(p.valor)}</span>
                </li>
              ))}
            </ul>
            {venta.pagos.some((p) => p.cambio !== undefined && p.cambio > 0) && (
              <p className="mt-2 text-foreground-muted">Cambio entregado: {formatearImporte(venta.pagos.find((p) => p.cambio)?.cambio)}</p>
            )}
          </div>
          {venta.autorizacion && (
            <p className="u-card p-4 text-sm text-foreground-muted">
              Excepción autorizada por <strong className="text-foreground">{venta.autorizacion.autorizadaPor.nombre}</strong> (hasta{' '}
              {formatearPorcentaje(venta.autorizacion.descuentoMaximo)}{venta.autorizacion.permiteBajoCosto && ', bajo el costo'}): {venta.autorizacion.motivo}
            </p>
          )}
          {r && (
            <dl className="u-card grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 p-4 text-sm" aria-label="Rentabilidad bruta">
              <dt className="col-span-2 mb-1 font-medium text-foreground">Rentabilidad bruta (neta de devoluciones)</dt>
              <dt className="text-foreground-muted">Ingreso neto</dt><dd className="text-right">{formatearImporte(r.ingresoNeto)}</dd>
              <dt className="text-foreground-muted">Costo</dt><dd className="text-right">{r.costoCompleto ? formatearImporte(r.costo) : 'No disponible'}</dd>
              <dt className="text-foreground-muted">Utilidad bruta</dt><dd className="text-right">{r.costoCompleto ? formatearImporte(r.utilidad) : 'No disponible'}</dd>
              <dt className="text-foreground-muted">Margen</dt><dd className="text-right">{formatearPorcentaje(r.margen)}</dd>
              <dt className="text-foreground-muted">Recargo sobre costo</dt><dd className="text-right">{formatearPorcentaje(r.recargo)}</dd>
            </dl>
          )}
        </aside>
      </div>

      {venta.devoluciones.length > 0 && (
        <section className="mt-8" aria-label="Devoluciones y anulaciones">
          <h2 className="mb-3 text-lg font-semibold text-foreground">Devoluciones y anulaciones</h2>
          <ul className="grid gap-3">
            {venta.devoluciones.map((d) => (
              <li key={d.id} className="u-card p-4 text-sm">
                <p className="font-medium text-foreground">
                  {d.tipo === 'ANULACION' ? 'Anulación' : 'Devolución'} · {formatearImporte(d.total)} · {formatearFechaHora(d.registradaEn)} · {d.registradaPor.nombre}
                </p>
                <p className="text-foreground-muted">Motivo: {d.motivo}</p>
                <ul className="mt-1 text-foreground-muted">
                  {d.lineas.map((l) => (
                    <li key={l.detalleVentaId}>
                      {l.cantidad} × {l.nombre} — {l.reingresaInventario ? 'vuelve al inventario' : 'no vuelve al inventario'}
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </section>
      )}

      {dialogo === 'devolver' && (
        <DevolucionModal venta={venta} onCerrar={() => setDialogo(null)} onHecho={(v) => trasOperacion(v, 'Devolución registrada.')} />
      )}
      {dialogo === 'anular' && (
        <AnulacionModal venta={venta} onCerrar={() => setDialogo(null)} onHecho={(v) => trasOperacion(v, `Venta ${v.numeroVisible} anulada.`)} />
      )}
    </>
  )
}

function Volver() {
  return (
    <Link to="/ventas" className="inline-flex items-center gap-1 text-sm text-foreground-muted hover:text-foreground">
      <ArrowLeft className="size-4" aria-hidden="true" /> Ventas
    </Link>
  )
}

function DevolucionModal({ venta, onCerrar, onHecho }: { venta: Venta; onCerrar: () => void; onHecho: (v: Venta) => void }) {
  const [cantidades, setCantidades] = useState<Record<number, string>>({})
  const [reingresa, setReingresa] = useState<Record<number, boolean>>({})
  const [motivo, setMotivo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [clave] = useState(nuevaClaveIdempotencia)
  const pendientes = venta.lineas.filter((l) => l.pendienteDeDevolver > 0)
  const lineas = pendientes
    .map((l) => ({ l, cantidad: Number(cantidades[l.id] ?? '0') }))
    .filter(({ cantidad }) => cantidad > 0)
  const invalida = pendientes.some((l) => {
    const n = Number(cantidades[l.id] ?? '0')
    return !Number.isInteger(n) || n < 0 || n > l.pendienteDeDevolver
  })

  async function enviar(e: FormEvent) {
    e.preventDefault()
    if (lineas.length === 0 || invalida || !motivo.trim()) {
      setError(!motivo.trim() ? 'Indica el motivo.' : 'Indica al menos una cantidad válida.')
      return
    }
    setEnviando(true)
    setError(null)
    try {
      onHecho(await ventaService.devolver(venta.id, {
        claveIdempotencia: clave,
        motivo: motivo.trim(),
        lineas: lineas.map(({ l, cantidad }) => ({ detalleVentaId: l.id, cantidad, reingresaInventario: reingresa[l.id] ?? true })),
      }))
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Modal
      open
      onClose={onCerrar}
      dismissible={!enviando}
      size="lg"
      title={`Devolución de ${venta.numeroVisible}`}
      description="Registra las unidades devueltas. Nada se borra: la venta conserva sus líneas y la devolución queda en el historial."
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onCerrar} disabled={enviando}>Cancelar</button>
          <button type="submit" form="form-devolucion" className="u-btn u-btn--primary" disabled={enviando} aria-busy={enviando}>
            {enviando && <Loader2 className="size-4 animate-spin" aria-hidden="true" />} Registrar devolución
          </button>
        </>
      }
    >
      <form id="form-devolucion" onSubmit={(e) => void enviar(e)} className="grid gap-4">
        <ul className="grid gap-3">
          {pendientes.map((l) => (
            <li key={l.id} className="grid gap-2 rounded-md border border-border p-3 sm:grid-cols-[1fr_auto_auto] sm:items-center">
              <div>
                <p className="font-medium text-foreground">{l.nombre}</p>
                <p className="text-xs text-foreground-muted">Quedan {l.pendienteDeDevolver} de {l.cantidad} por devolver</p>
              </div>
              <label className="flex items-center gap-2 text-sm">
                Cantidad
                <input className="u-input w-20" inputMode="numeric" aria-label={`Unidades a devolver de ${l.nombre}`} value={cantidades[l.id] ?? ''} onChange={(e) => setCantidades({ ...cantidades, [l.id]: e.target.value })} />
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={reingresa[l.id] ?? true} onChange={(e) => setReingresa({ ...reingresa, [l.id]: e.target.checked })} />
                Vuelve al inventario
              </label>
            </li>
          ))}
        </ul>
        <label className="grid gap-1 text-sm">
          Motivo
          <textarea className="u-input min-h-20" maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
        </label>
        {error && <p role="alert" className="u-alert u-alert--error text-sm">{error}</p>}
      </form>
    </Modal>
  )
}

function AnulacionModal({ venta, onCerrar, onHecho }: { venta: Venta; onCerrar: () => void; onHecho: (v: Venta) => void }) {
  const [motivo, setMotivo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [clave] = useState(nuevaClaveIdempotencia)

  async function enviar(e: FormEvent) {
    e.preventDefault()
    if (!motivo.trim()) {
      setError('Indica el motivo de la anulación.')
      return
    }
    setEnviando(true)
    setError(null)
    try {
      onHecho(await ventaService.anular(venta.id, { claveIdempotencia: clave, motivo: motivo.trim() }))
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Modal
      open
      onClose={onCerrar}
      dismissible={!enviando}
      title={`Anular ${venta.numeroVisible}`}
      description="Solo el mismo día y sin devoluciones previas. Todas las unidades vuelven al inventario y la venta deja de contar en los reportes. Queda registrado quién y por qué."
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onCerrar} disabled={enviando}>Cancelar</button>
          <button type="submit" form="form-anulacion" className="u-btn bg-danger text-danger-surface hover:opacity-90" disabled={enviando} aria-busy={enviando}>
            {enviando && <Loader2 className="size-4 animate-spin" aria-hidden="true" />} Anular venta
          </button>
        </>
      }
    >
      <form id="form-anulacion" onSubmit={(e) => void enviar(e)} className="grid gap-3">
        <label className="grid gap-1 text-sm">
          Motivo
          <textarea className="u-input min-h-20" maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
        </label>
        {error && <p role="alert" className="u-alert u-alert--error text-sm">{error}</p>}
      </form>
    </Modal>
  )
}
