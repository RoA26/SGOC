import { ChevronRight, Inbox, Loader2, PackageOpen, RotateCw } from 'lucide-react'
import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { ETIQUETA_VISTA, VISTAS_OPERATIVAS, esVista } from '@/features/entregas/etiquetas'
import { FechaEntrega } from '@/features/entregas/FechaEntrega'
import { RecepcionDetalleModal } from '@/features/entregas/RecepcionDetalle'
import { SituacionEntregaBadge } from '@/features/entregas/SituacionEntregaBadge'
import { EstadoOrdenBadge } from '@/features/ordenes/EstadoOrdenBadge'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { cn } from '@/lib/cn'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearNumero } from '@/lib/numeros'
import { entregaService, type Entrega } from '@/services/entregaService'
import { numeroOrden, ordenCompraService, type Recepcion } from '@/services/ordenCompraService'
import type { PageParams } from '@/services/types'

const TAMANO_PAGINA = 20

/**
 * Recepción de mercancía (vista operativa del trabajador): qué está por llegar, qué está
 * retrasado y su propio historial de recepciones. Sin costos. Registrar una entrega se hace en la
 * ficha de cada orden (/recepcion/:ordenId).
 */
export default function RecepcionMercancia() {
  const [searchParams, setSearchParams] = useSearchParams()
  const pestana = searchParams.get('pestana') === 'mias' ? 'mias' : 'pendientes'

  function irA(siguiente: 'pendientes' | 'mias') {
    setSearchParams(siguiente === 'mias' ? { pestana: 'mias' } : {})
  }

  return (
    <>
      <PageHeader
        eyebrow="Operación"
        title="Recibir mercancía"
        description="Órdenes emitidas con unidades por llegar. Abre una para contar lo recibido e informar faltantes o daños."
      />
      <div role="tablist" aria-label="Recepción de mercancía" className="mb-6 flex gap-1 border-b border-border">
        {(
          [
            ['pendientes', 'Por recibir'],
            ['mias', 'Mis recepciones'],
          ] as const
        ).map(([valor, etiqueta]) => (
          <button
            key={valor}
            type="button"
            role="tab"
            id={`tab-${valor}`}
            aria-selected={pestana === valor}
            aria-controls={`panel-${valor}`}
            onClick={() => irA(valor)}
            className={cn(
              '-mb-px border-b-2 px-3 py-2 text-sm',
              pestana === valor
                ? 'border-foreground font-medium text-foreground'
                : 'border-transparent text-foreground-muted hover:text-foreground',
            )}
          >
            {etiqueta}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${pestana}`} aria-labelledby={`tab-${pestana}`}>
        {pestana === 'pendientes' ? <PorRecibir /> : <MisRecepciones />}
      </div>
    </>
  )
}

function PorRecibir() {
  const [searchParams, setSearchParams] = useSearchParams()
  const vistaUrl = searchParams.get('vista')
  const vista = esVista(vistaUrl, VISTAS_OPERATIVAS) ? vistaUrl : 'PENDIENTES'
  const [pagina, setPagina] = useState(0)
  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) => entregaService.listar({ ...params, vista }, signal),
    [vista],
  )
  const { data, loading, error, reload } = usePaginatedResource(listar, pagina, TAMANO_PAGINA, '', vista)

  function cambiarVista(valor: string) {
    setSearchParams(valor === 'PENDIENTES' ? {} : { vista: valor })
    setPagina(0)
  }

  const entregas = data?.content ?? []
  return (
    <>
      <div className="mb-4 flex flex-wrap gap-2" role="group" aria-label="Filtrar entregas">
        {VISTAS_OPERATIVAS.map((valor) => (
          <button
            key={valor}
            type="button"
            aria-pressed={vista === valor}
            onClick={() => cambiarVista(valor)}
            className={cn(
              'h-8 rounded-full border px-3 text-sm',
              vista === valor
                ? 'border-foreground bg-foreground font-medium text-background'
                : 'border-border text-foreground-muted hover:bg-surface-muted hover:text-foreground',
            )}
          >
            {ETIQUETA_VISTA[valor]}
          </button>
        ))}
      </div>

      {error && (
        <div role="alert" className="u-alert u-alert--error mb-4 flex flex-wrap items-center justify-between gap-3">
          {error}
          <button type="button" className="u-btn u-btn--ghost h-8 text-sm" onClick={reload}>
            <RotateCw className="size-4" aria-hidden="true" />
            Reintentar
          </button>
        </div>
      )}
      {loading && entregas.length === 0 && !error && (
        <p className="flex items-center gap-2 text-sm text-foreground-muted" role="status">
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Cargando entregas…
        </p>
      )}
      {!loading && !error && entregas.length === 0 && (
        <section className="u-card flex flex-col items-center px-6 py-14 text-center">
          <Inbox className="size-8 text-foreground-muted" aria-hidden="true" />
          <h2 className="mt-3 font-medium text-foreground">
            {vista === 'PENDIENTES' ? 'No hay mercancía por recibir' : `No hay entregas: ${ETIQUETA_VISTA[vista].toLowerCase()}`}
          </h2>
          <p className="mt-1 max-w-md text-sm text-foreground-muted">
            Las órdenes aparecen aquí cuando el administrador las emite al proveedor.
          </p>
        </section>
      )}

      {entregas.length > 0 && (
        <ul className={cn('grid gap-3 lg:grid-cols-2', loading && 'opacity-60')} aria-busy={loading}>
          {entregas.map((entrega) => (
            <li key={entrega.ordenId}>
              <TarjetaEntrega entrega={entrega} />
            </li>
          ))}
        </ul>
      )}
      {data && data.page.totalPages > 1 && (
        <nav className="mt-4 flex items-center justify-between text-sm text-foreground-muted" aria-label="Paginación">
          <button type="button" className="u-btn u-btn--ghost h-9" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>
            Anterior
          </button>
          <span>
            Página {pagina + 1} de {data.page.totalPages}
          </span>
          <button
            type="button"
            className="u-btn u-btn--ghost h-9"
            disabled={pagina + 1 >= data.page.totalPages}
            onClick={() => setPagina(pagina + 1)}
          >
            Siguiente
          </button>
        </nav>
      )}
    </>
  )
}

function TarjetaEntrega({ entrega }: { entrega: Entrega }) {
  return (
    <Link
      to={`/recepcion/${entrega.ordenId}`}
      className={cn(
        'u-card group flex items-start gap-4 p-4 transition-colors hover:border-border-strong focus-visible:outline-2 focus-visible:outline-focus',
        entrega.situacion === 'RETRASADA' && 'border-danger-border',
      )}
    >
      <div className="min-w-0 flex-1 space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-sm font-medium text-foreground">{numeroOrden(entrega.numero)}</span>
          <SituacionEntregaBadge situacion={entrega.situacion} diasRetraso={entrega.diasRetraso} />
          {entrega.estadoOrden === 'RECIBIDA_PARCIALMENTE' && <EstadoOrdenBadge estado={entrega.estadoOrden} />}
        </div>
        <p className="truncate text-foreground">{entrega.proveedor.razonSocial}</p>
        <div className="text-sm">
          <FechaEntrega programacion={entrega.programacion} compacta />
        </div>
        <p className="text-sm text-foreground-muted tabular-nums">
          Por recibir: <span className="font-medium text-foreground">{formatearNumero(entrega.unidadesPendientes)}</span> de{' '}
          {formatearNumero(entrega.unidadesPedidas)} unidades
          {entrega.incidencias > 0 && ` · ${entrega.incidencias} incidencias`}
        </p>
      </div>
      <span className="mt-1 inline-flex items-center gap-1 text-sm font-medium text-foreground group-hover:underline">
        <PackageOpen className="size-4" aria-hidden="true" />
        Recibir
        <ChevronRight className="size-4" aria-hidden="true" />
      </span>
    </Link>
  )
}

function MisRecepciones() {
  const [pagina, setPagina] = useState(0)
  const [abierta, setAbierta] = useState<number | null>(null)
  const { data, loading, error, reload } = usePaginatedResource(
    ordenCompraService.misRecepciones,
    pagina,
    15,
    'recibidaEn,desc',
  )
  const columnas: Column<Recepcion>[] = [
    {
      id: 'fecha',
      header: 'Registrada',
      cell: (r) => (
        <button type="button" className="whitespace-nowrap text-left font-medium text-foreground hover:underline" onClick={() => setAbierta(r.id)}>
          {formatearFechaHora(r.recibidaEn)}
        </button>
      ),
    },
    { id: 'orden', header: 'Orden', cell: (r) => <span className="font-mono text-sm">{numeroOrden(r.ordenNumero)}</span> },
    { id: 'proveedor', header: 'Proveedor', className: 'hidden sm:table-cell', cell: (r) => r.proveedor },
    {
      id: 'unidades',
      header: 'Unidades',
      align: 'right',
      cell: (r) => <span className="tabular-nums">{formatearNumero(r.unidades)}</span>,
    },
    {
      id: 'incidencias',
      header: 'Novedades',
      align: 'right',
      cell: (r) =>
        r.incidencias.length > 0 ? (
          <span className="text-warning tabular-nums">{r.incidencias.length}</span>
        ) : (
          <span className="text-foreground-muted">—</span>
        ),
    },
  ]
  return (
    <>
      <DataTable
        caption="Mis recepciones"
        columns={columnas}
        rows={data?.content ?? []}
        rowKey={(r) => r.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage="Aún no has registrado ninguna recepción."
        page={data?.page}
        onPageChange={setPagina}
      />
      {abierta !== null && (
        <RecepcionDetalleModal recepcionId={abierta} enlaceOrden={(id) => `/recepcion/${id}`} onClose={() => setAbierta(null)} />
      )}
    </>
  )
}
