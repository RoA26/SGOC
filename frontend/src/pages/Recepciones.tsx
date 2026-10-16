import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { AvisoModoGlobal } from '@/features/empresa/AvisoModoGlobal'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearCOP } from '@/lib/moneda'
import { formatearNumero } from '@/lib/numeros'
import { numeroOrden, ordenCompraService, type Recepcion } from '@/services/ordenCompraService'

const TAMANO_PAGINA = 15
const ORDEN = 'recibidaEn,desc'

/** Historial de recepciones de mercancía: cada una es un hecho registrado, que no se edita. */
export default function Recepciones() {
  const [pagina, setPagina] = useState(0)
  const { data, loading, error, reload } = usePaginatedResource(
    ordenCompraService.listarRecepciones,
    pagina,
    TAMANO_PAGINA,
    ORDEN,
  )

  const columnas: Column<Recepcion>[] = [
    {
      id: 'fecha',
      header: 'Recibida',
      cell: (recepcion) => <span className="whitespace-nowrap">{formatearFechaHora(recepcion.recibidaEn)}</span>,
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
            {primero?.producto.nombre}
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
      cell: (recepcion) => <span className="font-medium whitespace-nowrap tabular-nums">{formatearCOP(recepcion.valor)}</span>,
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
        description="Mercancía recibida de las órdenes emitidas, valorada al costo pactado. Se registra desde cada orden."
      />
      <AvisoModoGlobal que="las recepciones" />
      <DataTable
        caption="Recepciones de mercancía"
        columns={columnas}
        rows={data?.content ?? []}
        rowKey={(recepcion) => recepcion.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage="Aún no se ha recibido mercancía. Registra la primera desde una orden emitida."
        page={data?.page}
        onPageChange={setPagina}
      />
    </>
  )
}
