import { CheckCircle2, Eye, Info, Plus, SearchCheck } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { usePuedeGestionarCatalogos, usePuedeRevisarSolicitudes } from '@/features/auth/permisos'
import { DetalleSolicitudModal } from '@/features/solicitudes/DetalleSolicitudModal'
import { EstadoBadge } from '@/features/solicitudes/EstadoBadge'
import { ETIQUETA_ESTADO } from '@/features/solicitudes/estados'
import { FormSolicitud } from '@/features/solicitudes/FormSolicitud'
import { useAviso } from '@/hooks/useAviso'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { cn } from '@/lib/cn'
import { formatearFecha } from '@/lib/fechas'
import { formatearCOP } from '@/lib/moneda'
import { solicitudService, type EstadoSolicitud, type Solicitud } from '@/services/solicitudService'
import type { PageParams } from '@/services/types'
import { useProductosStore } from '@/store/productosStore'

const TAMANO_PAGINA = 10
// El backend ordena por defecto así; se envía explícito para que la paginación sea estable.
const ORDEN = 'fecha,desc'
const SIN_PRODUCTOS = 'Aún no hay productos en el catálogo'
const ID_AYUDA_SIN_PRODUCTOS = 'solicitudes-sin-productos'
const FILTROS: { valor: EstadoSolicitud | null; etiqueta: string }[] = [
  { valor: null, etiqueta: 'Todas' },
  { valor: 'PENDIENTE', etiqueta: 'Pendientes' },
  { valor: 'APROBADA', etiqueta: 'Aprobadas' },
  { valor: 'RECHAZADA', etiqueta: 'Rechazadas' },
]

/**
 * Solicitudes internas de compra. USUARIO ve y crea las suyas; ADMIN y GERENTE ven las de
 * todos y las revisan desde el detalle. El filtrado por usuario lo hace el backend.
 */
export default function Solicitudes() {
  const puedeRevisar = usePuedeRevisarSolicitudes()
  const puedeGestionarCatalogos = usePuedeGestionarCatalogos()
  const [pagina, setPagina] = useState(0)
  const [estado, setEstado] = useState<EstadoSolicitud | null>(null)

  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) =>
      solicitudService.listar({ ...params, estado: estado ?? undefined }, signal),
    [estado],
  )
  const { data, loading, error, reload } = usePaginatedResource(listar, pagina, TAMANO_PAGINA, ORDEN, estado ?? '')
  const filas = data?.content ?? []

  const [creando, setCreando] = useState(false)
  const [abierta, setAbierta] = useState<Solicitud | null>(null)
  const { aviso, mostrarAviso } = useAviso()

  // Para pedir productos hace falta que exista al menos uno en el catálogo.
  const productos = useProductosStore((state) => state.items)
  const estadoProductos = useProductosStore((state) => state.estado)
  const cargarProductos = useProductosStore((state) => state.cargar)
  useEffect(() => {
    void cargarProductos({ forzar: true })
  }, [cargarProductos])
  const sinProductos = estadoProductos === 'listo' && productos.length === 0
  const altaBloqueada = sinProductos || estadoProductos === 'inicial' || estadoProductos === 'cargando'

  function filtrar(valor: EstadoSolicitud | null) {
    setEstado(valor)
    setPagina(0)
  }

  function alCrear(solicitud: Solicitud) {
    setCreando(false)
    mostrarAviso(`Solicitud N.º ${solicitud.id} enviada. Queda pendiente de revisión.`)
    reload()
  }

  function alRevisar(solicitud: Solicitud) {
    setAbierta(null)
    mostrarAviso(`Solicitud N.º ${solicitud.id} ${ETIQUETA_ESTADO[solicitud.estado].toLowerCase()}.`)
    reload()
  }

  const columnas: Column<Solicitud>[] = [
    {
      id: 'id',
      header: 'N.º',
      cell: (solicitud) => <span className="font-mono text-xs">#{solicitud.id}</span>,
    },
    {
      id: 'fecha',
      header: 'Fecha',
      cell: (solicitud) => <span className="whitespace-nowrap">{formatearFecha(solicitud.fecha)}</span>,
    },
  ]
  if (puedeRevisar) {
    columnas.push({
      id: 'solicitante',
      header: 'Solicitante',
      cell: (solicitud) => <span className="whitespace-nowrap">{solicitud.solicitante.nombre}</span>,
    })
  }
  columnas.push(
    {
      id: 'productos',
      header: 'Productos',
      className: 'hidden md:table-cell',
      cell: (solicitud) => {
        const [primero, ...resto] = solicitud.detalles
        return (
          <span className="text-foreground-muted">
            {primero?.producto.nombre}
            {resto.length > 0 && ` y ${resto.length} más`}
          </span>
        )
      },
    },
    {
      id: 'total',
      header: 'Total estimado',
      align: 'right',
      cell: (solicitud) => (
        <span className="font-medium whitespace-nowrap tabular-nums">{formatearCOP(solicitud.totalEstimado)}</span>
      ),
    },
    {
      id: 'estado',
      header: 'Estado',
      cell: (solicitud) => <EstadoBadge estado={solicitud.estado} />,
    },
    {
      id: 'acciones',
      header: <span className="sr-only">Acciones</span>,
      align: 'right',
      cell: (solicitud) => {
        const revisar = puedeRevisar && solicitud.estado === 'PENDIENTE'
        const Icono = revisar ? SearchCheck : Eye
        return (
          <button
            type="button"
            className={cn('u-btn h-8 px-2.5 text-sm', revisar ? 'u-btn--primary' : 'u-btn--ghost')}
            onClick={() => setAbierta(solicitud)}
            aria-label={`${revisar ? 'Revisar' : 'Ver'} la solicitud N.º ${solicitud.id}`}
          >
            <Icono className="size-4" aria-hidden="true" />
            <span className="hidden sm:inline">{revisar ? 'Revisar' : 'Ver'}</span>
          </button>
        )
      },
    },
  )

  return (
    <>
      <PageHeader
        eyebrow="Compras"
        title="Solicitudes"
        description={
          puedeRevisar
            ? 'Solicitudes internas de todo el equipo. Revisa las pendientes para aprobarlas o rechazarlas.'
            : 'Pide los productos que necesitas y sigue el estado de tus solicitudes.'
        }
        actions={
          <button
            type="button"
            className="u-btn u-btn--primary disabled:cursor-not-allowed disabled:opacity-50"
            onClick={() => setCreando(true)}
            disabled={altaBloqueada}
            aria-busy={estadoProductos === 'cargando'}
            title={sinProductos ? SIN_PRODUCTOS : undefined}
            aria-describedby={sinProductos ? ID_AYUDA_SIN_PRODUCTOS : undefined}
          >
            <Plus className="size-4" aria-hidden="true" />
            Nueva solicitud
          </button>
        }
      />

      {sinProductos && (
        <p id={ID_AYUDA_SIN_PRODUCTOS} className="mb-6 flex items-start gap-2 text-sm text-foreground-muted">
          <Info className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>
            {SIN_PRODUCTOS}: las solicitudes se hacen sobre productos registrados.{' '}
            {puedeGestionarCatalogos ? (
              <Link to="/productos" className="font-medium text-foreground underline-offset-4 hover:underline">
                Ir a Productos
              </Link>
            ) : (
              'Pide a un administrador que los registre.'
            )}
          </span>
        </p>
      )}

      <div role="group" aria-label="Filtrar por estado" className="mb-4 flex flex-wrap gap-2">
        {FILTROS.map(({ valor, etiqueta }) => (
          <button
            key={etiqueta}
            type="button"
            onClick={() => filtrar(valor)}
            aria-pressed={estado === valor}
            className={cn(
              'rounded-full border px-3 py-1 text-sm font-medium transition-colors',
              estado === valor
                ? 'border-primary bg-primary text-primary-foreground'
                : 'border-border-strong text-foreground-muted hover:bg-surface-muted hover:text-foreground',
            )}
          >
            {etiqueta}
          </button>
        ))}
      </div>

      <p role="status" aria-live="polite">
        {aviso && (
          <span className="mb-4 flex items-center gap-2 rounded-md border border-success/30 bg-success/10 px-4 py-2.5 text-sm text-success">
            <CheckCircle2 className="size-4" aria-hidden="true" />
            {aviso}
          </span>
        )}
      </p>

      <DataTable
        caption="Listado de solicitudes internas"
        columns={columnas}
        rows={filas}
        rowKey={(solicitud) => solicitud.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage={
          estado
            ? `No hay solicitudes ${ETIQUETA_ESTADO[estado].toLowerCase()}s.`
            : puedeRevisar
              ? 'Aún no hay solicitudes.'
              : 'Aún no has hecho ninguna solicitud. Crea la primera con «Nueva solicitud».'
        }
        page={data?.page}
        onPageChange={setPagina}
      />

      {creando && <FormSolicitud onClose={() => setCreando(false)} onSaved={alCrear} />}
      {abierta && (
        <DetalleSolicitudModal solicitud={abierta} onClose={() => setAbierta(null)} onRevisada={alRevisar} />
      )}
    </>
  )
}
