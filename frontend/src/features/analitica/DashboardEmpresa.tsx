import { AlertTriangle, ArrowRight, Package, Wallet, XCircle } from 'lucide-react'
import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { BarList } from '@/components/charts/BarList'
import { ChartCard, TablaDatos } from '@/components/charts/ChartCard'
import { ColumnChart } from '@/components/charts/ColumnChart'
import { StatTile } from '@/components/ui/StatTile'
import { EstadoStockBadge } from '@/features/inventario/EstadoStockBadge'
import { ESTADOS_ORDEN, ETIQUETA_ESTADO_ORDEN } from '@/features/ordenes/estados'
import { ETIQUETA_ESTADO } from '@/features/solicitudes/estados'
import { useRecurso } from '@/hooks/useRecurso'
import { formatearCOP } from '@/lib/moneda'
import { formatearNumero } from '@/lib/numeros'
import { describirPeriodo, etiquetaLarga } from '@/lib/periodo'
import { analiticaService } from '@/services/analiticaService'
import type { EstadoSolicitud } from '@/services/solicitudService'
import { ErrorPanel, EsqueletoPanel, SinDatos } from './EstadoCarga'
import { FiltroPeriodo } from './FiltroPeriodo'
import { aFiltro, PERIODO_POR_DEFECTO, type SeleccionPeriodo } from './seleccionPeriodo'

const ESTADOS_SOLICITUD: readonly EstadoSolicitud[] = ['PENDIENTE', 'APROBADA', 'RECHAZADA']
const recepciones = (n: number) => `${formatearNumero(n)} ${n === 1 ? 'recepción' : 'recepciones'}`

/**
 * Dashboard de una empresa (ADMIN_EMPRESA, o SUPER_ADMIN supervisándola en solo lectura):
 * inventario, compras del período, recepciones y el estado de solicitudes y órdenes. La empresa
 * la fija el backend; el cliente no la elige.
 */
export function DashboardEmpresa() {
  const [seleccion, setSeleccion] = useState<SeleccionPeriodo>(PERIODO_POR_DEFECTO)
  const { desde, hasta } = aFiltro(seleccion)
  const clave = `${desde ?? ''}|${hasta ?? ''}`
  const pedir = useCallback(
    (signal: AbortSignal) => {
      const [d, h] = clave.split('|')
      return analiticaService.empresa({ desde: d || undefined, hasta: h || undefined }, signal)
    },
    [clave],
  )
  const { datos, error, cargando, recargar } = useRecurso(pedir, clave)

  return (
    <section aria-labelledby="dashboard-empresa" className="space-y-4">
      <h2 id="dashboard-empresa" className="sr-only">
        Indicadores de la empresa
      </h2>
      <FiltroPeriodo seleccion={seleccion} onCambio={setSeleccion} />
      {error && <ErrorPanel mensaje={error} onReintentar={recargar} />}
      {!datos ? (
        !error && <EsqueletoPanel />
      ) : (
        <div className="space-y-4">
          <p className="text-xs text-foreground-muted">
            {describirPeriodo(datos.periodo.desde, datos.periodo.hasta)} · días de Colombia. Inventario y estados: situación
            actual.
          </p>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile
              etiqueta="Productos activos"
              valor={formatearNumero(datos.inventario.productosActivos)}
              detalle={`${formatearNumero(datos.inventario.disponibles)} con existencias`}
              icono={Package}
              to="/inventario"
            />
            <StatTile
              etiqueta="Stock crítico"
              valor={formatearNumero(datos.inventario.criticos)}
              detalle="En su stock mínimo o por debajo."
              icono={AlertTriangle}
              claseIcono="text-warning"
              to="/inventario?estado=CRITICO"
            />
            <StatTile
              etiqueta="Sin existencias"
              valor={formatearNumero(datos.inventario.agotados)}
              detalle="Productos activos con 0 unidades."
              icono={XCircle}
              claseIcono="text-danger"
              to="/inventario?estado=AGOTADO"
            />
            <StatTile
              etiqueta="Compras del período"
              valor={formatearCOP(datos.compras.valorComprometido)}
              detalle={`${formatearNumero(datos.compras.ordenesEmitidas)} ${datos.compras.ordenesEmitidas === 1 ? 'orden emitida' : 'órdenes emitidas'} · recibido ${formatearCOP(datos.compras.valorRecibido)}`}
              icono={Wallet}
              to="/ordenes-compra"
            />
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            <ChartCard
              className="lg:col-span-2"
              titulo="Actividad de recepciones"
              subtitulo={`${recepciones(datos.recepciones.enPeriodo)} · ${formatearNumero(datos.recepciones.unidades)} unidades recibidas · ${datos.periodo.granularidad === 'DIA' ? 'por día' : 'por mes'}`}
              recargando={cargando}
              tabla={
                <TablaDatos
                  encabezados={[datos.periodo.granularidad === 'DIA' ? 'Día' : 'Mes', 'Recepciones']}
                  filas={datos.recepciones.evolucion.map((punto) => ({
                    clave: punto.fecha,
                    etiqueta: etiquetaLarga(punto.fecha, datos.periodo.granularidad),
                    valor: formatearNumero(punto.valor),
                  }))}
                />
              }
            >
              {datos.recepciones.totalHistorico === 0 ? (
                <SinDatos titulo="Aún no hay recepciones">
                  Cuando registres la mercancía recibida de una orden emitida, su actividad aparecerá aquí.
                </SinDatos>
              ) : (
                <ColumnChart
                  puntos={datos.recepciones.evolucion}
                  granularidad={datos.periodo.granularidad}
                  unidad={recepciones}
                  descripcion={`Recepciones ${datos.periodo.granularidad === 'DIA' ? 'por día' : 'por mes'}: ${formatearNumero(datos.recepciones.enPeriodo)} en el período`}
                />
              )}
            </ChartCard>

            <section aria-labelledby="atencion" className="u-card flex flex-col p-5">
              <h3 id="atencion" className="font-medium text-foreground">
                Requieren atención
              </h3>
              <p className="mt-0.5 text-xs text-foreground-muted">Sin existencias o en su stock mínimo</p>
              {datos.inventario.requierenAtencion.length === 0 ? (
                <div className="mt-4 flex-1">
                  <SinDatos titulo="Todo en orden">Ningún producto activo está agotado ni en su stock mínimo.</SinDatos>
                </div>
              ) : (
                <ul className="mt-3 flex-1 divide-y divide-border">
                  {datos.inventario.requierenAtencion.map((producto) => (
                    <li key={producto.id} className="flex items-center justify-between gap-3 py-2">
                      <div className="min-w-0">
                        <p className="truncate text-sm text-foreground">{producto.nombre}</p>
                        <p className="font-mono text-xs text-foreground-muted">
                          {producto.sku} · {formatearNumero(producto.cantidad)} / mín. {formatearNumero(producto.stockMinimo)}
                        </p>
                      </div>
                      <EstadoStockBadge estado={producto.estado} />
                    </li>
                  ))}
                </ul>
              )}
              <Link to="/inventario" className="mt-3 inline-flex items-center gap-1 text-sm font-medium text-foreground hover:underline">
                Ver inventario
                <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
            </section>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <ChartCard
              titulo="Órdenes de compra por estado"
              subtitulo="Situación actual"
              recargando={cargando}
              tabla={
                <TablaDatos
                  encabezados={['Estado', 'Órdenes']}
                  filas={ESTADOS_ORDEN.map((estado) => ({
                    clave: estado,
                    etiqueta: ETIQUETA_ESTADO_ORDEN[estado],
                    valor: formatearNumero(datos.compras.ordenesPorEstado[estado]),
                  }))}
                />
              }
            >
              {datos.compras.totalHistorico === 0 ? (
                <SinDatos titulo="Aún no hay órdenes de compra">
                  Créalas desde una solicitud aprobada o desde «Órdenes de compra».
                </SinDatos>
              ) : (
                <BarList
                  descripcion="Órdenes de compra por estado"
                  filas={ESTADOS_ORDEN.map((estado) => ({
                    clave: estado,
                    etiqueta: ETIQUETA_ESTADO_ORDEN[estado],
                    valor: datos.compras.ordenesPorEstado[estado],
                    to: `/ordenes-compra?estado=${estado}`,
                  }))}
                />
              )}
            </ChartCard>
            <ChartCard
              titulo="Solicitudes por estado"
              subtitulo="Situación actual"
              recargando={cargando}
              tabla={
                <TablaDatos
                  encabezados={['Estado', 'Solicitudes']}
                  filas={ESTADOS_SOLICITUD.map((estado) => ({
                    clave: estado,
                    etiqueta: ETIQUETA_ESTADO[estado],
                    valor: formatearNumero(datos.solicitudesPorEstado[estado]),
                  }))}
                />
              }
            >
              {ESTADOS_SOLICITUD.every((estado) => datos.solicitudesPorEstado[estado] === 0) ? (
                <SinDatos titulo="Aún no hay solicitudes">Las necesidades de compra del equipo aparecerán aquí.</SinDatos>
              ) : (
                <BarList
                  descripcion="Solicitudes por estado"
                  filas={ESTADOS_SOLICITUD.map((estado) => ({
                    clave: estado,
                    etiqueta: ETIQUETA_ESTADO[estado],
                    valor: datos.solicitudesPorEstado[estado],
                    to: `/solicitudes?estado=${estado}`,
                  }))}
                />
              )}
            </ChartCard>
          </div>
        </div>
      )}
    </section>
  )
}
