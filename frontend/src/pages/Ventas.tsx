import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { usePermisos } from '@/features/auth/permisos'
import { EstadoVentaBadge } from '@/features/ventas/EstadoVentaBadge'
import { ETIQUETA_ESTADO_VENTA } from '@/features/ventas/etiquetas'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearImporte } from '@/lib/moneda'
import type { PageParams } from '@/services/types'
import { ventaService, type EstadoVenta, type VentaResumen } from '@/services/ventaService'

const ESTADOS: readonly EstadoVenta[] = ['CONFIRMADA', 'DEVUELTA_PARCIALMENTE', 'DEVUELTA', 'ANULADA']

/**
 * Historial de ventas. El trabajador ve solo las suyas (el backend lo impone aunque se pida otro
 * vendedor); la gestión ve todas las de la empresa.
 */
export default function Ventas() {
  const permisos = usePermisos()
  const [pagina, setPagina] = useState(0)
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [estado, setEstado] = useState<EstadoVenta | ''>('')
  const filtro = `${desde}|${hasta}|${estado}`

  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) =>
      ventaService.listar({ ...params, desde: desde || undefined, hasta: hasta || undefined, estado: estado || undefined }, signal),
    [desde, hasta, estado],
  )
  const { data, error, loading, reload } = usePaginatedResource(listar, pagina, 20, 'vendidaEn,desc', filtro)

  const columnas: Column<VentaResumen>[] = [
    { id: 'numero', header: 'Venta', cell: (v) => <Link className="font-medium text-primary hover:underline" to={`/ventas/${v.id}`}>{v.numeroVisible}</Link> },
    { id: 'fecha', header: 'Fecha', cell: (v) => formatearFechaHora(v.vendidaEn), className: 'whitespace-nowrap' },
    { id: 'vendedor', header: 'Vendió', cell: (v) => v.vendidaPor.nombre, className: 'hidden md:table-cell' },
    { id: 'unidades', header: 'Unid.', cell: (v) => v.unidades, align: 'right', className: 'hidden sm:table-cell' },
    { id: 'descuentos', header: 'Descuentos', cell: (v) => (v.descuentos > 0 ? formatearImporte(v.descuentos) : '—'), align: 'right', className: 'hidden lg:table-cell' },
    { id: 'total', header: 'Total', cell: (v) => <span className="font-medium">{formatearImporte(v.total)}</span>, align: 'right' },
    { id: 'estado', header: 'Estado', cell: (v) => <EstadoVentaBadge estado={v.estado} /> },
  ]

  return (
    <>
      <PageHeader
        eyebrow="Ventas"
        title={permisos.verComercial ? 'Ventas' : 'Mis ventas'}
        description={permisos.verComercial ? 'Todas las ventas de la empresa, con sus devoluciones y anulaciones.' : 'Las ventas que registraste, con sus comprobantes.'}
        actions={permisos.vender ? <Link className="u-btn u-btn--primary" to="/pos">Nueva venta</Link> : undefined}
      />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <label className="grid gap-1 text-sm">
          <span className="text-foreground-muted">Desde</span>
          <input type="date" className="u-input" value={desde} onChange={(e) => { setDesde(e.target.value); setPagina(0) }} />
        </label>
        <label className="grid gap-1 text-sm">
          <span className="text-foreground-muted">Hasta</span>
          <input type="date" className="u-input" value={hasta} onChange={(e) => { setHasta(e.target.value); setPagina(0) }} />
        </label>
        <label className="grid gap-1 text-sm">
          <span className="text-foreground-muted">Estado</span>
          <select className="u-input" value={estado} onChange={(e) => { setEstado(e.target.value as EstadoVenta | ''); setPagina(0) }}>
            <option value="">Todos</option>
            {ESTADOS.map((e) => <option key={e} value={e}>{ETIQUETA_ESTADO_VENTA[e]}</option>)}
          </select>
        </label>
      </div>
      <DataTable
        caption="Historial de ventas"
        columns={columnas}
        rows={data?.content ?? []}
        rowKey={(v) => v.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage={desde || hasta || estado ? 'Ninguna venta coincide con los filtros.' : 'Aún no hay ventas registradas.'}
        page={data?.page}
        onPageChange={setPagina}
      />
    </>
  )
}
