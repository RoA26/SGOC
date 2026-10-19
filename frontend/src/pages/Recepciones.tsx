import { AlertTriangle, X } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { AvisoModoGlobal } from '@/features/empresa/AvisoModoGlobal'
import { RecepcionDetalleModal } from '@/features/entregas/RecepcionDetalle'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearCOP } from '@/lib/moneda'
import { formatearNumero } from '@/lib/numeros'
import { numeroOrden, ordenCompraService, type Recepcion } from '@/services/ordenCompraService'
import type { PageParams } from '@/services/types'
import { useProveedoresStore } from '@/store/proveedoresStore'

const TAMANO_PAGINA = 15
const ORDEN = 'recibidaEn,desc'

/**
 * Historial de recepciones de mercancía (supervisión): quién registró cada una, qué entró al
 * inventario y qué discrepancias se informaron. Cada recepción es un hecho registrado: no se
 * edita. Filtros en la URL (?desde=&hasta=&proveedorId=&conIncidencias=true).
 */
export default function Recepciones() {
  const [searchParams, setSearchParams] = useSearchParams()
  const desde = searchParams.get('desde') ?? ''
  const hasta = searchParams.get('hasta') ?? ''
  const proveedorId = Number(searchParams.get('proveedorId')) || undefined
  const incidenciasUrl = searchParams.get('conIncidencias')
  const conIncidencias = incidenciasUrl === 'true' ? true : incidenciasUrl === 'false' ? false : undefined
  const [pagina, setPagina] = useState(0)
  const [abierta, setAbierta] = useState<number | null>(null)

  const proveedores = useProveedoresStore((state) => state.items)
  const cargarProveedores = useProveedoresStore((state) => state.cargar)
  useEffect(() => {
    void cargarProveedores()
  }, [cargarProveedores])

  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) =>
      ordenCompraService.listarRecepciones(
        { ...params, desde: desde || undefined, hasta: hasta || undefined, proveedorId, conIncidencias },
        signal,
      ),
    [desde, hasta, proveedorId, conIncidencias],
  )
  const { data, loading, error, reload } = usePaginatedResource(
    listar,
    pagina,
    TAMANO_PAGINA,
    ORDEN,
    `${desde}|${hasta}|${proveedorId ?? ''}|${conIncidencias ?? ''}`,
  )

  function cambiar(cambios: Record<string, string | null>) {
    setSearchParams((actuales) => {
      const siguientes = new URLSearchParams(actuales)
      for (const [clave, valor] of Object.entries(cambios)) {
        if (valor) siguientes.set(clave, valor)
        else siguientes.delete(clave)
      }
      return siguientes
    })
    setPagina(0)
  }
  const hayFiltros = Boolean(desde || hasta || proveedorId || conIncidencias !== undefined)

  const columnas: Column<Recepcion>[] = [
    {
      id: 'fecha',
      header: 'Recibida',
      cell: (recepcion) => (
        <button
          type="button"
          className="text-left font-medium whitespace-nowrap text-foreground hover:underline"
          onClick={() => setAbierta(recepcion.id)}
        >
          {formatearFechaHora(recepcion.recibidaEn)}
        </button>
      ),
    },
    {
      id: 'orden',
      header: 'Orden',
      cell: (recepcion) => (
        <Link to={`/ordenes-compra/${recepcion.ordenId}`} className="font-mono text-sm font-medium hover:underline">
          {numeroOrden(recepcion.ordenNumero)}
        </Link>
      ),
    },
    { id: 'proveedor', header: 'Proveedor', cell: (recepcion) => recepcion.proveedor },
    {
      id: 'productos',
      header: 'Productos',
      className: 'hidden lg:table-cell',
      cell: (recepcion) => {
        const [primero, ...resto] = recepcion.detalles
        return (
          <span className="text-foreground-muted">
            {primero ? primero.producto.nombre : 'Nada aceptado'}
            {resto.length > 0 && ` y ${resto.length} más`}
          </span>
        )
      },
    },
    {
      id: 'unidades',
      header: 'Unidades',
      align: 'right',
      cell: (recepcion) => <span className="tabular-nums">{formatearNumero(recepcion.unidades)}</span>,
    },
    {
      id: 'valor',
      header: 'Valor',
      align: 'right',
      className: 'hidden sm:table-cell',
      cell: (recepcion) => (
        <span className="font-medium whitespace-nowrap tabular-nums">{recepcion.valor !== undefined ? formatearCOP(recepcion.valor) : '—'}</span>
      ),
    },
    {
      id: 'incidencias',
      header: 'Incidencias',
      align: 'right',
      cell: (recepcion) =>
        recepcion.incidencias.length > 0 ? (
          <span className="inline-flex items-center gap-1 text-warning tabular-nums">
            <AlertTriangle className="size-3.5" aria-hidden="true" />
            {recepcion.incidencias.length}
          </span>
        ) : (
          <span className="text-foreground-muted">—</span>
        ),
    },
    {
      id: 'usuario',
      header: 'Registró',
      className: 'hidden md:table-cell',
      cell: (recepcion) => <span className="whitespace-nowrap text-foreground-muted">{recepcion.recibidaPor.nombre}</span>,
    },
  ]

  return (
    <>
      <PageHeader
        eyebrow="Compras"
        title="Recepciones"
        description="Mercancía recibida de las órdenes emitidas, valorada al costo pactado, con quién la registró y sus discrepancias."
      />
      <AvisoModoGlobal que="las recepciones" />

      <div className="mb-4 flex flex-wrap items-end gap-3" role="group" aria-label="Filtros">
        <label className="space-y-1">
          <span className="u-label">Desde</span>
          <input type="date" value={desde} max={hasta || undefined} onChange={(e) => cambiar({ desde: e.target.value || null })}
            className="u-input w-40" />
        </label>
        <label className="space-y-1">
          <span className="u-label">Hasta</span>
          <input type="date" value={hasta} min={desde || undefined} onChange={(e) => cambiar({ hasta: e.target.value || null })}
            className="u-input w-40" />
        </label>
        <label className="space-y-1">
          <span className="u-label">Proveedor</span>
          <select value={proveedorId ?? ''} onChange={(e) => cambiar({ proveedorId: e.target.value || null })} className="u-input w-56">
            <option value="">Todos</option>
            {proveedores.map((p) => (
              <option key={p.id} value={p.id}>
                {p.razonSocial}
              </option>
            ))}
          </select>
        </label>
        <label className="space-y-1">
          <span className="u-label">Incidencias</span>
          <select value={incidenciasUrl ?? ''} onChange={(e) => cambiar({ conIncidencias: e.target.value || null })} className="u-input w-44">
            <option value="">Todas</option>
            <option value="true">Con incidencias</option>
            <option value="false">Sin incidencias</option>
          </select>
        </label>
        {hayFiltros && (
          <button
            type="button"
            className="u-btn u-btn--ghost h-control"
            onClick={() => cambiar({ desde: null, hasta: null, proveedorId: null, conIncidencias: null })}
          >
            <X className="size-4" aria-hidden="true" />
            Quitar filtros
          </button>
        )}
      </div>

      <DataTable
        caption="Recepciones de mercancía"
        columns={columnas}
        rows={data?.content ?? []}
        rowKey={(recepcion) => recepcion.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage={
          hayFiltros
            ? 'Ninguna recepción coincide con los filtros.'
            : 'Aún no se ha recibido mercancía. La registra el trabajador en «Recibir mercancía» o el administrador desde la orden.'
        }
        page={data?.page}
        onPageChange={setPagina}
      />
      {abierta !== null && (
        <RecepcionDetalleModal recepcionId={abierta} enlaceOrden={(id) => `/ordenes-compra/${id}`} onClose={() => setAbierta(null)} />
      )}
    </>
  )
}
