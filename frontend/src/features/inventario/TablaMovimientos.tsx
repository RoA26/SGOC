import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { cn } from '@/lib/cn'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearCOP } from '@/lib/moneda'
import { formatearNumero } from '@/lib/numeros'
import { inventarioService, type Movimiento } from '@/services/inventarioService'
import { numeroOrden } from '@/services/ordenCompraService'
import type { PageParams } from '@/services/types'

/** Kárdex: cada cambio de existencias con su origen, quién, cuándo, el saldo y el costo factual. */
export function TablaMovimientos({ productoId, recarga = 0 }: { productoId?: number; recarga?: number }) {
  const [pagina, setPagina] = useState(0)
  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) => inventarioService.movimientos({ ...params, productoId }, signal),
    [productoId],
  )
  const { data, loading, error, reload } = usePaginatedResource(
    listar,
    pagina,
    10,
    'fecha,desc',
    `${productoId ?? ''}|${recarga}`,
  )
  const columnas: Column<Movimiento>[] = [
    { id: 'fecha', header: 'Fecha', cell: (m) => <span className="whitespace-nowrap">{formatearFechaHora(m.fecha)}</span> },
  ]
  if (productoId === undefined) {
    columnas.push({
      id: 'producto',
      header: 'Producto',
      cell: (m) => (
        <div className="min-w-36">
          <p className="text-foreground">{m.producto.nombre}</p>
          <p className="font-mono text-xs text-foreground-muted">{m.producto.sku}</p>
        </div>
      ),
    })
  }
  columnas.push(
    {
      id: 'origen',
      header: 'Origen',
      cell: (m) =>
        m.tipo === 'RECEPCION' && m.ordenId && m.ordenNumero ? (
          <Link to={`/ordenes-compra/${m.ordenId}`} className="whitespace-nowrap hover:underline">
            Recepción · <span className="font-mono">{numeroOrden(m.ordenNumero)}</span>
          </Link>
        ) : (
          <span className="text-foreground-muted">Ajuste · {m.motivo}</span>
        ),
    },
    {
      id: 'cantidad',
      header: 'Movimiento',
      align: 'right',
      cell: (m) => (
        <span className={cn('font-medium whitespace-nowrap tabular-nums', m.cantidad > 0 ? 'text-success' : 'text-danger')}>
          {m.cantidad > 0 ? '+' : '−'}
          {formatearNumero(Math.abs(m.cantidad))}
        </span>
      ),
    },
    { id: 'saldo', header: 'Saldo', align: 'right', cell: (m) => <span className="tabular-nums">{formatearNumero(m.saldoResultante)}</span> },
    {
      id: 'costo',
      header: 'Costo unit.',
      align: 'right',
      className: 'hidden md:table-cell',
      cell: (m) => <span className="tabular-nums text-foreground-muted">{m.costoUnitario ? formatearCOP(m.costoUnitario) : '—'}</span>,
    },
    {
      id: 'usuario',
      header: 'Usuario',
      className: 'hidden lg:table-cell',
      cell: (m) => <span className="whitespace-nowrap text-foreground-muted">{m.usuario.nombre}</span>,
    },
  )
  return (
    <DataTable
      caption="Movimientos de inventario"
      columns={columnas}
      rows={data?.content ?? []}
      rowKey={(m) => m.id}
      loading={loading}
      error={error}
      onRetry={reload}
      emptyMessage="Aún no hay movimientos: las recepciones y los ajustes aparecerán aquí."
      page={data?.page}
      onPageChange={setPagina}
    />
  )
}
