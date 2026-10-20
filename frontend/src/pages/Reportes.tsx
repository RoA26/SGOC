import { CircleAlert, Loader2 } from 'lucide-react'
import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { BarList } from '@/components/charts/BarList'
import { ChartCard, TablaDatos } from '@/components/charts/ChartCard'
import { ColumnChart } from '@/components/charts/ColumnChart'
import { PageHeader } from '@/components/layout/PageHeader'
import { StatTile } from '@/components/ui/StatTile'
import { FiltroPeriodo } from '@/features/analitica/FiltroPeriodo'
import { aFiltro, PERIODO_POR_DEFECTO, type SeleccionPeriodo } from '@/features/analitica/seleccionPeriodo'
import { EstadoStockBadge } from '@/features/inventario/EstadoStockBadge'
import { useRecurso } from '@/hooks/useRecurso'
import { cn } from '@/lib/cn'
import { formatearFecha, formatearFechaHora } from '@/lib/fechas'
import { formatearImporte, formatearPorcentaje } from '@/lib/moneda'
import { formatearNumero } from '@/lib/numeros'
import { hoyNegocio } from '@/lib/periodo'
import { reporteService } from '@/services/reporteService'

/**
 * Reportes comerciales del período, calculados por el backend con los datos históricos de ventas y
 * devoluciones. Una métrica sin costo fiable aparece como «no disponible», nunca como cero.
 */
export default function Reportes() {
  const [seleccion, setSeleccion] = useState<SeleccionPeriodo>(PERIODO_POR_DEFECTO)
  const hoy = hoyNegocio()
  const esHoy = seleccion.tipo === 'personalizado' && seleccion.desde === hoy && seleccion.hasta === hoy
  const { desde, hasta } = aFiltro(seleccion)
  const pedir = useCallback((signal: AbortSignal) => reporteService.ventas({ desde, hasta }, signal), [desde, hasta])
  const { datos, error, cargando, recargar } = useRecurso(pedir, `${desde}|${hasta}`)
  const r = datos?.resumen
  const serieNegativa = datos?.serie.some((p) => p.ingresoNeto < 0) ?? false

  return (
    <>
      <PageHeader eyebrow="Ventas" title="Reportes comerciales" description="Ventas, devoluciones, rentabilidad bruta, descuentos e inventario de tu empresa." />
      <div className="mb-6 flex flex-wrap items-center gap-2">
        <button type="button" className={cn('u-btn', esHoy ? 'u-btn--primary' : 'u-btn--ghost')} onClick={() => setSeleccion({ tipo: 'personalizado', desde: hoy, hasta: hoy })} aria-pressed={esHoy}>
          Hoy
        </button>
        <FiltroPeriodo seleccion={seleccion} onCambio={setSeleccion} />
      </div>

      {error && (
        <p role="alert" className="u-alert u-alert--error mb-6 flex items-center justify-between gap-2">
          No se pudo cargar el reporte: {error}
          <button type="button" className="u-btn u-btn--ghost" onClick={recargar}>Reintentar</button>
        </p>
      )}
      {!datos && !error && (
        <p className="flex items-center gap-2 text-sm text-foreground-muted"><Loader2 className="size-4 animate-spin" aria-hidden="true" /> Calculando el reporte…</p>
      )}

      {datos && r && (
        <div className={cn('grid gap-6', cargando && 'opacity-60')} aria-busy={cargando}>
          <p className="text-sm text-foreground-muted">
            Del {formatearFecha(datos.periodo.desde)} al {formatearFecha(datos.periodo.hasta)} ({datos.periodo.zonaHoraria}). Costos con promedio ponderado
            {datos.valoracionProvisional && ' (método provisional, pendiente de validación contable)'}.
          </p>
          {!r.costoCompleto && (
            <p className="u-alert u-alert--warning flex items-start gap-2 text-sm">
              <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              Hay ventas sin costo histórico ({formatearImporte(r.ingresoSinCosto)} de ingreso): la utilidad, el margen y el recargo del período no están disponibles.
            </p>
          )}
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatTile etiqueta="Ventas" valor={formatearNumero(r.numeroVentas)} detalle={`${formatearNumero(r.unidadesVendidas)} unidades · ${r.ventasAnuladas} anuladas`} />
            <StatTile etiqueta="Ingreso neto (sin IVA)" valor={formatearImporte(r.ingresoNeto)} detalle={`Bruto ${formatearImporte(r.ingresoBruto)} − devoluciones ${formatearImporte(r.devoluciones)}`} />
            <StatTile etiqueta="Utilidad bruta" valor={r.utilidadBruta === undefined ? 'No disponible' : formatearImporte(r.utilidadBruta)} detalle={`Margen ${formatearPorcentaje(r.margenBruto)} · recargo ${formatearPorcentaje(r.recargo)}`} />
            <StatTile etiqueta="Descuentos concedidos" valor={formatearImporte(r.descuentos)} detalle={`IVA neto ${formatearImporte(r.impuestosNetos)} · venta promedio ${formatearImporte(r.ventaPromedio)}`} />
          </div>

          <ChartCard
            titulo="Ingreso neto por día"
            subtitulo="Base sin IVA de las ventas menos las devoluciones del día"
            recargando={cargando}
            tabla={<TablaDatos encabezados={['Día', 'Ingreso neto']} filas={datos.serie.map((p) => ({ clave: p.fecha, etiqueta: formatearFecha(p.fecha), valor: formatearImporte(p.ingresoNeto) }))} />}
          >
            {serieNegativa ? (
              <p className="text-sm text-foreground-muted">Algún día tiene más devoluciones que ventas: consulta la vista de tabla.</p>
            ) : (
              <ColumnChart puntos={datos.serie.map((p) => ({ fecha: p.fecha, valor: p.ingresoNeto }))} granularidad="DIA" unidad={formatearImporte} descripcion="Ingreso neto por día" />
            )}
          </ChartCard>

          <div className="grid gap-6 lg:grid-cols-2">
            <ChartCard titulo="Productos más vendidos" subtitulo="Unidades netas de devoluciones" tabla={<TablaDatos encabezados={['Producto', 'Unidades']} filas={datos.masVendidos.map((p) => ({ clave: String(p.productoId), etiqueta: p.nombre, valor: formatearNumero(p.unidades) }))} />}>
              {datos.masVendidos.length === 0 ? <p className="text-sm text-foreground-muted">Sin ventas en el período.</p> : (
                <BarList descripcion="Unidades vendidas por producto" filas={datos.masVendidos.map((p) => ({ clave: String(p.productoId), etiqueta: p.nombre, valor: p.unidades, valorVisible: formatearNumero(p.unidades) }))} />
              )}
            </ChartCard>
            <ChartCard titulo="Mayor contribución a la utilidad bruta" subtitulo="Solo productos con todos sus costos conocidos" tabla={<TablaDatos encabezados={['Producto', 'Utilidad']} filas={datos.mayorUtilidad.map((p) => ({ clave: String(p.productoId), etiqueta: p.nombre, valor: formatearImporte(p.utilidad) }))} />}>
              {datos.mayorUtilidad.length === 0 ? <p className="text-sm text-foreground-muted">Sin datos de utilidad en el período.</p> : (
                <BarList descripcion="Utilidad bruta por producto" filas={datos.mayorUtilidad.map((p) => ({ clave: String(p.productoId), etiqueta: p.nombre, valor: Math.max(0, p.utilidad ?? 0), valorVisible: `${formatearImporte(p.utilidad)} (${formatearPorcentaje(p.margen)})` }))} />
              )}
            </ChartCard>
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <section className="u-card p-5" aria-label="Ventas por trabajador">
              <h3 className="mb-3 font-medium text-foreground">Ventas por persona</h3>
              {datos.porVendedor.length === 0 ? <p className="text-sm text-foreground-muted">Sin ventas en el período.</p> : (
                <table className="w-full text-sm">
                  <thead className="text-xs text-foreground-muted"><tr><th scope="col" className="py-1 text-left font-medium">Persona</th><th scope="col" className="py-1 text-right font-medium">Ventas</th><th scope="col" className="py-1 text-right font-medium">Ingreso neto</th><th scope="col" className="py-1 text-right font-medium">Descuentos</th></tr></thead>
                  <tbody className="divide-y divide-border">
                    {datos.porVendedor.map((v) => (
                      <tr key={v.usuarioId}><td className="py-1.5">{v.nombre}</td><td className="py-1.5 text-right">{v.ventas}</td><td className="py-1.5 text-right">{formatearImporte(v.ingresoNeto)}</td><td className="py-1.5 text-right">{formatearImporte(v.descuentos)}</td></tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>
            <section className="u-card p-5" aria-label="Productos con bajo inventario">
              <h3 className="mb-3 font-medium text-foreground">Bajo inventario (a la venta)</h3>
              {datos.bajoInventario.length === 0 ? <p className="text-sm text-foreground-muted">Ningún producto a la venta está agotado ni en el mínimo.</p> : (
                <ul className="grid gap-2 text-sm">
                  {datos.bajoInventario.map((p) => (
                    <li key={p.productoId} className="flex items-center justify-between gap-2">
                      <span>{p.nombre} <span className="font-mono text-xs text-foreground-muted">{p.sku}</span></span>
                      <span className="flex items-center gap-2 text-foreground-muted">{p.existencias} / mín. {p.stockMinimo} <EstadoStockBadge estado={p.estado} /></span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>

          <section className="u-card p-5" aria-label="Ventas por debajo del costo">
            <h3 className="mb-1 font-medium text-foreground">Ventas por debajo del costo</h3>
            <p className="mb-3 text-sm text-foreground-muted">{r.lineasBajoCosto} líneas en el período con pérdida bruta.</p>
            {datos.ventasBajoCosto.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="text-xs text-foreground-muted"><tr><th scope="col" className="py-1 text-left font-medium">Venta</th><th scope="col" className="py-1 text-left font-medium">Producto</th><th scope="col" className="py-1 text-right font-medium">Ingreso</th><th scope="col" className="py-1 text-right font-medium">Costo</th><th scope="col" className="py-1 text-right font-medium">Pérdida</th></tr></thead>
                  <tbody className="divide-y divide-border">
                    {datos.ventasBajoCosto.map((l, i) => (
                      <tr key={`${l.ventaId}-${i}`}>
                        <td className="py-1.5"><Link className="text-primary hover:underline" to={`/ventas/${l.ventaId}`}>{l.numeroVisible}</Link><span className="block text-xs text-foreground-muted">{formatearFechaHora(l.vendidaEn)}</span></td>
                        <td className="py-1.5">{l.cantidad} × {l.nombre}</td>
                        <td className="py-1.5 text-right">{formatearImporte(l.ingreso)}</td>
                        <td className="py-1.5 text-right">{formatearImporte(l.costo)}</td>
                        <td className="py-1.5 text-right text-danger">{formatearImporte(l.perdida)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>
      )}
    </>
  )
}
