import { CheckCircle2, Eye, Info, Plus, SearchCheck } from 'lucide-react'
import axios from 'axios'
import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { usePermisos } from '@/features/auth/permisos'
import { AvisoModoGlobal } from '@/features/empresa/AvisoModoGlobal'
import { DetalleSolicitudModal } from '@/features/solicitudes/DetalleSolicitudModal'
import { EstadoBadge } from '@/features/solicitudes/EstadoBadge'
import { ETIQUETA_ESTADO } from '@/features/solicitudes/estados'
import { FormSolicitud } from '@/features/solicitudes/FormSolicitud'
import { useAviso } from '@/hooks/useAviso'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { getApiErrorMessage } from '@/lib/errors'
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
const ESTADOS_VALIDOS: readonly EstadoSolicitud[] = ['PENDIENTE', 'APROBADA', 'RECHAZADA']
const FILTROS: { valor: EstadoSolicitud | null; etiqueta: string }[] = [
  { valor: null, etiqueta: 'Todas' },
  { valor: 'PENDIENTE', etiqueta: 'Pendientes' },
  { valor: 'APROBADA', etiqueta: 'Aprobadas' },
  { valor: 'RECHAZADA', etiqueta: 'Rechazadas' },
]

function estadoDeUrl(valor: string | null): EstadoSolicitud | null {
  return ESTADOS_VALIDOS.find((estado) => estado === valor) ?? null
}

/**
 * Solicitudes de compra: la necesidad interna, antes de cualquier orden de compra. USUARIO ve
 * y crea las suyas; GERENTE ve y revisa las de su empresa; SUPER_ADMIN las consulta (y las
 * revisa dentro de una empresa). El filtrado por usuario y empresa lo hace el backend.
 *
 * La URL guarda el filtro (?estado=PENDIENTE) y permite abrir una solicitud (?ver=12) o el
 * formulario (?nueva=1) desde otras páginas.
 */
export default function Solicitudes() {
  const permisos = usePermisos()
  const [searchParams, setSearchParams] = useSearchParams()
  const estado = estadoDeUrl(searchParams.get('estado'))
  const [pagina, setPagina] = useState(0)

  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) =>
      solicitudService.listar({ ...params, estado: estado ?? undefined }, signal),
    [estado],
  )
  const { data, loading, error, reload } = usePaginatedResource(listar, pagina, TAMANO_PAGINA, ORDEN, estado ?? '')
  const filas = data?.content ?? []

  // Enlaces desde el inicio: ?nueva=1 abre el formulario y ?ver=12 abre esa solicitud.
  const nueva = searchParams.get('nueva') === '1'
  const ver = Number(searchParams.get('ver'))
  const [creando, setCreando] = useState(() => nueva && permisos.crearSolicitudes)
  const [abierta, setAbierta] = useState<Solicitud | null>(null)
  const [errorEnlace, setErrorEnlace] = useState<string | null>(null)
  const { aviso, mostrarAviso } = useAviso()

  // Una vez leídos, los parámetros se quitan para que recargar o volver atrás no los repita.
  useEffect(() => {
    if (nueva) setSearchParams((actuales) => quitarParametro(actuales, 'nueva'), { replace: true })
  }, [nueva, setSearchParams])
  useEffect(() => {
    if (!Number.isInteger(ver) || ver <= 0) return
    const controller = new AbortController()
    solicitudService
      .obtener(ver, controller.signal)
      .then(setAbierta)
      .catch((err: unknown) => {
        if (!axios.isCancel(err)) setErrorEnlace(getApiErrorMessage(err))
      })
      .finally(() => {
        // Si se canceló (desmontaje, o el doble montaje de StrictMode), el parámetro se queda para el siguiente intento.
        if (!controller.signal.aborted) setSearchParams((actuales) => quitarParametro(actuales, 'ver'), { replace: true })
      })
    return () => controller.abort()
  }, [ver, setSearchParams])

  // Para pedir productos hace falta que exista al menos uno en el catálogo.
  const productos = useProductosStore((state) => state.items)
  const estadoProductos = useProductosStore((state) => state.estado)
  const cargarProductos = useProductosStore((state) => state.cargar)
  useEffect(() => {
    if (permisos.crearSolicitudes) void cargarProductos({ forzar: true })
  }, [permisos.crearSolicitudes, cargarProductos])
  const sinProductos = estadoProductos === 'listo' && productos.length === 0
  const altaBloqueada = sinProductos || estadoProductos === 'inicial' || estadoProductos === 'cargando'

  function filtrar(valor: EstadoSolicitud | null) {
    setSearchParams((actuales) => {
      const siguientes = new URLSearchParams(actuales)
      if (valor) siguientes.set('estado', valor)
      else siguientes.delete('estado')
      return siguientes
    })
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
  if (permisos.verTodasLasSolicitudes) {
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
        const revisar = permisos.revisarSolicitudes && solicitud.estado === 'PENDIENTE'
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
        title={permisos.verTodasLasSolicitudes ? 'Solicitudes de compra' : 'Mis solicitudes'}
        description={
          permisos.verTodasLasSolicitudes
            ? 'Necesidades internas de compra del equipo. Revisa las pendientes para aprobarlas o rechazarlas; las aprobadas pasan a orden de compra.'
            : 'Registra lo que necesitas comprar y sigue su aprobación. No hace falta elegir proveedor: eso llega con la orden de compra.'
        }
        actions={
          permisos.crearSolicitudes && (
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
          )
        }
      />

      <AvisoModoGlobal que="las solicitudes" />
      {errorEnlace && (
        <p role="alert" className="u-alert u-alert--error mb-4">
          {errorEnlace}
        </p>
      )}

      {permisos.crearSolicitudes && sinProductos && (
        <p id={ID_AYUDA_SIN_PRODUCTOS} className="mb-6 flex items-start gap-2 text-sm text-foreground-muted">
          <Info className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>
            {SIN_PRODUCTOS}: las solicitudes se hacen sobre productos registrados.{' '}
            {permisos.gestionarCatalogos ? (
              <Link to="/productos" className="font-medium text-foreground underline-offset-4 hover:underline">
                Ir a Productos
              </Link>
            ) : (
              'Pide a un gerente que los registre.'
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
            : permisos.verTodasLasSolicitudes
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

function quitarParametro(actuales: URLSearchParams, nombre: string): URLSearchParams {
  const siguientes = new URLSearchParams(actuales)
  siguientes.delete(nombre)
  return siguientes
}
