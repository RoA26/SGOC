import { Building, FileText, History, Send } from 'lucide-react'
import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { BarList } from '@/components/charts/BarList'
import { ChartCard, TablaDatos } from '@/components/charts/ChartCard'
import { ColumnChart } from '@/components/charts/ColumnChart'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { StatTile } from '@/components/ui/StatTile'
import { EstadoEmpresaBadge } from '@/features/empresa/EstadosBadges'
import { ESTADOS_ORDEN, ETIQUETA_ESTADO_ORDEN } from '@/features/ordenes/estados'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { useRecurso } from '@/hooks/useRecurso'
import { formatearCOP } from '@/lib/moneda'
import { formatearNumero } from '@/lib/numeros'
import { describirPeriodo, etiquetaLarga } from '@/lib/periodo'
import { analiticaService, type OrdenesDeEmpresa } from '@/services/analiticaService'
import type { PageParams } from '@/services/types'
import { ErrorPanel, EsqueletoPanel, SinDatos } from './EstadoCarga'
import { FiltroPeriodo } from './FiltroPeriodo'
import { aFiltro, PERIODO_POR_DEFECTO, type SeleccionPeriodo } from './seleccionPeriodo'

const ordenes = (n: number) => `${formatearNumero(n)} ${n === 1 ? 'orden' : 'órdenes'}`

/**
 * Dashboard de la plataforma (SUPER_ADMIN en modo global explícito): empresas, órdenes emitidas
 * en el período, su evolución y las empresas con más actividad. Todo lo calcula el backend.
 */
export function DashboardGlobal() {
  const [seleccion, setSeleccion] = useState<SeleccionPeriodo>(PERIODO_POR_DEFECTO)
  const filtro = aFiltro(seleccion)
  const clave = `${filtro.desde ?? ''}|${filtro.hasta ?? ''}`
  const pedir = useCallback((signal: AbortSignal) => analiticaService.global(aFiltroDeClave(clave), signal), [clave])
  const { datos, error, cargando, recargar } = useRecurso(pedir, clave)

  return (
    <section aria-labelledby="dashboard-global" className="space-y-4">
      <h2 id="dashboard-global" className="sr-only">
        Indicadores de la plataforma
      </h2>
      <FiltroPeriodo seleccion={seleccion} onCambio={setSeleccion} />
      {error && <ErrorPanel mensaje={error} onReintentar={recargar} />}
      {!datos ? (
        !error && <EsqueletoPanel />
      ) : (
        <div className="space-y-4">
          <p className="text-xs text-foreground-muted">
            {describirPeriodo(datos.periodo.desde, datos.periodo.hasta)} · días de Colombia
          </p>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile
              etiqueta="Empresas registradas"
              valor={formatearNumero(datos.empresas.total)}
              detalle={`${formatearNumero(datos.empresas.activas)} activas · ${formatearNumero(datos.empresas.inactivas)} inactivas`}
              icono={Building}
              to="/admin/empresas"
            />
            <StatTile
              etiqueta="Órdenes emitidas en el período"
              valor={formatearNumero(datos.ordenes.emitidasEnPeriodo)}
              detalle="Incluye las canceladas después de emitirse."
              icono={Send}
            />
            <StatTile
              etiqueta="Órdenes abiertas"
              valor={formatearNumero(
                datos.ordenes.porEstado.BORRADOR + datos.ordenes.porEstado.EMITIDA + datos.ordenes.porEstado.RECIBIDA_PARCIALMENTE,
              )}
              detalle="En borrador, emitidas o recibidas en parte (ahora)."
              icono={FileText}
            />
            <StatTile
              etiqueta="Órdenes registradas"
              valor={formatearNumero(datos.ordenes.totalHistorico)}
              detalle="Desde el inicio, en todas las empresas."
              icono={History}
            />
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            <ChartCard
              className="lg:col-span-2"
              titulo="Órdenes emitidas"
              subtitulo={datos.periodo.granularidad === 'DIA' ? 'Por día' : 'Por mes'}
              recargando={cargando}
              tabla={
                <TablaDatos
                  encabezados={[datos.periodo.granularidad === 'DIA' ? 'Día' : 'Mes', 'Órdenes']}
                  filas={datos.evolucionOrdenes.map((punto) => ({
                    clave: punto.fecha,
                    etiqueta: etiquetaLarga(punto.fecha, datos.periodo.granularidad),
                    valor: formatearNumero(punto.valor),
                  }))}
                />
              }
            >
              {datos.ordenes.totalHistorico === 0 ? (
                <SinDatos titulo="Aún no hay órdenes de compra">
                  La evolución aparecerá cuando las empresas emitan sus primeras órdenes.
                </SinDatos>
              ) : (
                <ColumnChart
                  puntos={datos.evolucionOrdenes}
                  granularidad={datos.periodo.granularidad}
                  unidad={ordenes}
                  descripcion={`Órdenes emitidas ${datos.periodo.granularidad === 'DIA' ? 'por día' : 'por mes'}: ${formatearNumero(datos.ordenes.emitidasEnPeriodo)} en el período`}
                />
              )}
            </ChartCard>

            <ChartCard
              titulo="Empresas con más actividad"
              subtitulo="Top 5 por órdenes emitidas en el período"
              recargando={cargando}
              tabla={
                <TablaDatos
                  encabezados={['Empresa', 'Órdenes · valor']}
                  filas={datos.topEmpresas.map((empresa) => ({
                    clave: String(empresa.id),
                    etiqueta: `${empresa.nombre} (${empresa.codigo})`,
                    valor: `${formatearNumero(empresa.ordenes)} · ${formatearCOP(empresa.valorComprometido)}`,
                  }))}
                />
              }
            >
              {datos.topEmpresas.length === 0 ? (
                <SinDatos titulo={datos.ordenes.totalHistorico === 0 ? 'Aún no hay órdenes de compra' : 'Sin órdenes en este período'}>
                  {datos.ordenes.totalHistorico === 0
                    ? 'Ninguna empresa ha emitido órdenes todavía.'
                    : 'Ninguna empresa emitió órdenes entre estas fechas. Prueba con un período más amplio.'}
                </SinDatos>
              ) : (
                <BarList
                  apilado
                  descripcion="Empresas con más órdenes emitidas"
                  filas={datos.topEmpresas.map((empresa) => ({
                    clave: String(empresa.id),
                    etiqueta: (
                      <span title={`${empresa.nombre} · ${formatearCOP(empresa.valorComprometido)}`}>
                        {empresa.nombre} <span className="font-mono text-xs text-foreground-muted">{empresa.codigo}</span>
                      </span>
                    ),
                    valor: empresa.ordenes,
                    to: `/admin/empresas/${empresa.id}`,
                  }))}
                />
              )}
            </ChartCard>
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            <ChartCard
              titulo="Órdenes por estado"
              subtitulo="Situación actual, todas las empresas"
              recargando={cargando}
              tabla={
                <TablaDatos
                  encabezados={['Estado', 'Órdenes']}
                  filas={ESTADOS_ORDEN.map((estado) => ({
                    clave: estado,
                    etiqueta: ETIQUETA_ESTADO_ORDEN[estado],
                    valor: formatearNumero(datos.ordenes.porEstado[estado]),
                  }))}
                />
              }
            >
              {datos.ordenes.totalHistorico === 0 ? (
                <SinDatos titulo="Aún no hay órdenes de compra">Se mostrarán por estado en cuanto existan.</SinDatos>
              ) : (
                <BarList
                  descripcion="Órdenes por estado"
                  filas={ESTADOS_ORDEN.map((estado) => ({
                    clave: estado,
                    etiqueta: ETIQUETA_ESTADO_ORDEN[estado],
                    valor: datos.ordenes.porEstado[estado],
                  }))}
                />
              )}
            </ChartCard>
            <div className="lg:col-span-2">
              <OrdenesPorEmpresa claveFiltro={clave} />
            </div>
          </div>
        </div>
      )}
    </section>
  )
}

/** La clave codifica el filtro; reconstruirlo de ella mantiene estable el fetcher. */
function aFiltroDeClave(clave: string) {
  const [desde, hasta] = clave.split('|')
  return { desde: desde || undefined, hasta: hasta || undefined }
}

function OrdenesPorEmpresa({ claveFiltro }: { claveFiltro: string }) {
  const [pagina, setPagina] = useState(0)
  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) =>
      analiticaService.ordenesPorEmpresa({ ...params, ...aFiltroDeClave(claveFiltro) }, signal),
    [claveFiltro],
  )
  const { data, loading, error, reload } = usePaginatedResource(listar, pagina, 8, 'id', claveFiltro)
  const columnas: Column<OrdenesDeEmpresa>[] = [
    {
      id: 'empresa',
      header: 'Empresa',
      cell: (fila) => (
        <Link to={`/admin/empresas/${fila.id}`} className="font-medium text-foreground hover:underline">
          {fila.nombre} <span className="font-mono text-xs text-foreground-muted">{fila.codigo}</span>
        </Link>
      ),
    },
    { id: 'estado', header: 'Estado', className: 'hidden sm:table-cell', cell: (fila) => <EstadoEmpresaBadge activa={fila.activa} /> },
    {
      id: 'periodo',
      header: 'Emitidas en el período',
      align: 'right',
      cell: (fila) => <span className="tabular-nums">{formatearNumero(fila.ordenesEnPeriodo)}</span>,
    },
    {
      id: 'abiertas',
      header: 'Abiertas',
      align: 'right',
      cell: (fila) => <span className="tabular-nums">{formatearNumero(fila.ordenesAbiertas)}</span>,
    },
  ]
  return (
    <div>
      <h3 className="mb-2 font-medium text-foreground">Órdenes por empresa</h3>
      <DataTable
        caption="Órdenes de compra por empresa"
        columns={columnas}
        rows={data?.content ?? []}
        rowKey={(fila) => fila.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage="Aún no hay empresas registradas."
        page={data?.page}
        onPageChange={setPagina}
      />
    </div>
  )
}
