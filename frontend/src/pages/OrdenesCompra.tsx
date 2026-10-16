import { CheckCircle2, Plus, X } from 'lucide-react'
import { useCallback, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { usePermisos } from '@/features/auth/permisos'
import { AvisoModoGlobal } from '@/features/empresa/AvisoModoGlobal'
import { EstadoOrdenBadge } from '@/features/ordenes/EstadoOrdenBadge'
import { ESTADOS_ORDEN, ETIQUETA_ESTADO_ORDEN } from '@/features/ordenes/estados'
import { OrdenFormModal } from '@/features/ordenes/OrdenFormModal'
import { useAviso } from '@/hooks/useAviso'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { cn } from '@/lib/cn'
import { formatearFecha } from '@/lib/fechas'
import { formatearCOP } from '@/lib/moneda'
import { formatearNumero } from '@/lib/numeros'
import { numeroOrden, ordenCompraService, type EstadoOrdenCompra, type OrdenCompra } from '@/services/ordenCompraService'
import type { PageParams } from '@/services/types'

const TAMANO_PAGINA = 10
const ORDEN = 'numero,desc'

function estadoDeUrl(valor: string | null): EstadoOrdenCompra | null {
  return ESTADOS_ORDEN.find((estado) => estado === valor) ?? null
}

/**
 * Órdenes de compra de la empresa (las filtra el backend por el contexto). El ADMIN_EMPRESA las
 * crea y gestiona; el SUPER_ADMIN las consulta. La URL guarda los filtros (?estado=, ?solicitudId=).
 */
export default function OrdenesCompra() {
  const permisos = usePermisos()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const estado = estadoDeUrl(searchParams.get('estado'))
  const solicitudId = Number(searchParams.get('solicitudId')) || null
  const [pagina, setPagina] = useState(0)
  const [creando, setCreando] = useState(false)
  const { aviso } = useAviso()

  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) =>
      ordenCompraService.listar({ ...params, estado: estado ?? undefined, solicitudId: solicitudId ?? undefined }, signal),
    [estado, solicitudId],
  )
  const { data, loading, error, reload } = usePaginatedResource(
    listar,
    pagina,
    TAMANO_PAGINA,
    ORDEN,
    `${estado ?? ''}|${solicitudId ?? ''}`,
  )

  function cambiarFiltro(clave: 'estado' | 'solicitudId', valor: string | null) {
    setSearchParams((actuales) => {
      const siguientes = new URLSearchParams(actuales)
      if (valor) siguientes.set(clave, valor)
      else siguientes.delete(clave)
      return siguientes
    })
    setPagina(0)
  }

  const columnas: Column<OrdenCompra>[] = [
    {
      id: 'numero',
      header: 'Orden',
      cell: (orden) => (
        <Link to={`/ordenes-compra/${orden.id}`} className="font-mono text-sm font-medium whitespace-nowrap text-foreground hover:underline">
          {numeroOrden(orden.numero)}
        </Link>
      ),
    },
    {
      id: 'proveedor',
      header: 'Proveedor',
      cell: (orden) => (
        <div className="min-w-40">
          <p className="text-foreground">{orden.proveedor.razonSocial}</p>
          {orden.solicitudId && <p className="text-xs text-foreground-muted">Desde la solicitud #{orden.solicitudId}</p>}
        </div>
      ),
    },
    { id: 'estado', header: 'Estado', cell: (orden) => <EstadoOrdenBadge estado={orden.estado} /> },
    {
      id: 'recibido',
      header: 'Recibido',
      align: 'right',
      className: 'hidden md:table-cell',
      cell: (orden) => (
        <span className="whitespace-nowrap tabular-nums text-foreground-muted">
          {formatearNumero(orden.unidadesRecibidas)} / {formatearNumero(orden.unidadesPedidas)}
        </span>
      ),
    },
    {
      id: 'total',
      header: 'Total',
      align: 'right',
      cell: (orden) => <span className="font-medium whitespace-nowrap tabular-nums">{formatearCOP(orden.total)}</span>,
    },
    {
      id: 'fecha',
      header: 'Fecha',
      className: 'hidden lg:table-cell',
      cell: (orden) => (
        <span className="whitespace-nowrap text-foreground-muted">
          {orden.emitidaEn ? `Emitida ${formatearFecha(orden.emitidaEn)}` : `Creada ${formatearFecha(orden.creadoEn)}`}
        </span>
      ),
    },
  ]

  const vacia = !loading && !error && data !== null && data.page.totalElements === 0 && !estado && !solicitudId

  return (
    <>
      <PageHeader
        eyebrow="Compras"
        title="Órdenes de compra"
        description="Lo que se pide a cada proveedor: se crean en borrador, se emiten y se reciben (en una o varias entregas)."
        actions={
          permisos.gestionarCompras && (
            <button type="button" className="u-btn u-btn--primary" onClick={() => setCreando(true)}>
              <Plus className="size-4" aria-hidden="true" />
              Nueva orden
            </button>
          )
        }
      />
      <AvisoModoGlobal que="las órdenes de compra" />
      <div role="status" aria-live="polite">
        {aviso && (
          <p className="u-alert u-alert--success mb-6 flex items-center gap-2">
            <CheckCircle2 className="size-4 shrink-0" aria-hidden="true" />
            {aviso}
          </p>
        )}
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-2" role="group" aria-label="Filtrar por estado">
        {[null, ...ESTADOS_ORDEN].map((valor) => (
          <button
            key={valor ?? 'todas'}
            type="button"
            aria-pressed={estado === valor}
            onClick={() => cambiarFiltro('estado', valor)}
            className={cn(
              'h-8 rounded-full border px-3 text-sm',
              estado === valor
                ? 'border-foreground bg-foreground font-medium text-background'
                : 'border-border text-foreground-muted hover:bg-surface-muted hover:text-foreground',
            )}
          >
            {valor ? ETIQUETA_ESTADO_ORDEN[valor] : 'Todas'}
          </button>
        ))}
        {solicitudId && (
          <button
            type="button"
            onClick={() => cambiarFiltro('solicitudId', null)}
            className="inline-flex h-8 items-center gap-1.5 rounded-full border border-border-strong bg-surface-muted px-3 text-sm text-foreground"
            aria-label={`Quitar el filtro de la solicitud #${solicitudId}`}
          >
            Solicitud #{solicitudId}
            <X className="size-3.5" aria-hidden="true" />
          </button>
        )}
      </div>

      {vacia ? (
        <section className="u-card flex flex-col items-center px-6 py-14 text-center">
          <h2 className="font-medium text-foreground">Aún no hay órdenes de compra</h2>
          <p className="mt-1 max-w-md text-sm text-foreground-muted">
            Genera una desde una solicitud aprobada (una por proveedor) o créala aquí directamente.
          </p>
          <div className="mt-5 flex flex-wrap justify-center gap-3">
            <Link to="/solicitudes?estado=APROBADA" className="u-btn u-btn--ghost">
              Ver solicitudes aprobadas
            </Link>
            {permisos.gestionarCompras && (
              <button type="button" className="u-btn u-btn--primary" onClick={() => setCreando(true)}>
                <Plus className="size-4" aria-hidden="true" />
                Nueva orden
              </button>
            )}
          </div>
        </section>
      ) : (
        <DataTable
          caption="Órdenes de compra"
          columns={columnas}
          rows={data?.content ?? []}
          rowKey={(orden) => orden.id}
          loading={loading}
          error={error}
          onRetry={reload}
          emptyMessage="Ninguna orden coincide con el filtro."
          page={data?.page}
          onPageChange={setPagina}
        />
      )}

      {creando && (
        <OrdenFormModal
          onClose={() => setCreando(false)}
          onSaved={(orden) => {
            setCreando(false)
            navigate(`/ordenes-compra/${orden.id}`, {
              state: { aviso: `Orden ${numeroOrden(orden.numero)} creada en borrador. Revísala y emítela.` },
            })
          }}
        />
      )}
    </>
  )
}
