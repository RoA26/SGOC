import { AlertTriangle, CalendarClock, CalendarPlus, CalendarX2, CheckCircle2, PackageSearch, Siren, X } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { StatTile } from '@/components/ui/StatTile'
import { useContextoEmpresa, usePermisos } from '@/features/auth/permisos'
import { AvisoModoGlobal } from '@/features/empresa/AvisoModoGlobal'
import { ETIQUETA_ALERTA, ETIQUETA_VISTA, VISTAS_SUPERVISION, esVista } from '@/features/entregas/etiquetas'
import { FechaEntrega } from '@/features/entregas/FechaEntrega'
import { ProgramarEntregaModal } from '@/features/entregas/ProgramarEntregaModal'
import { SituacionEntregaBadge } from '@/features/entregas/SituacionEntregaBadge'
import { useAviso } from '@/hooks/useAviso'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { useRecurso } from '@/hooks/useRecurso'
import { cn } from '@/lib/cn'
import { hoyEnBogota } from '@/lib/dias'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearNumero } from '@/lib/numeros'
import { entregaService, type Entrega, type VistaEntregas } from '@/services/entregaService'
import { numeroOrden } from '@/services/ordenCompraService'
import type { PageParams } from '@/services/types'
import { useProveedoresStore } from '@/store/proveedoresStore'

const TAMANO_PAGINA = 15

/**
 * Supervisión de entregas (gerente): qué está programado, retrasado, recibido en parte o con
 * incidencias, y qué necesita intervención. Desde aquí se programa y reprograma; la recepción la
 * registra el trabajador (o el administrador, desde la vista de recepción). Filtros en la URL.
 */
export default function Entregas() {
  const { modoGlobal } = useContextoEmpresa()
  if (modoGlobal) {
    return (
      <>
        <PageHeader eyebrow="Compras" title="Entregas" description="Lo que se espera de cada proveedor y cuándo." />
        <AvisoModoGlobal>
          las entregas se supervisan empresa por empresa. Elige una en{' '}
          <Link to="/admin/empresas" className="font-medium text-foreground underline-offset-4 hover:underline">
            Empresas
          </Link>{' '}
          y pulsa «Supervisar».
        </AvisoModoGlobal>
      </>
    )
  }
  return <EntregasEmpresa />
}

function EntregasEmpresa() {
  const permisos = usePermisos()
  const [searchParams, setSearchParams] = useSearchParams()
  const vistaUrl = searchParams.get('vista')
  const vista: VistaEntregas = esVista(vistaUrl, VISTAS_SUPERVISION) ? vistaUrl : 'PENDIENTES'
  const desde = searchParams.get('desde') ?? ''
  const hasta = searchParams.get('hasta') ?? ''
  const proveedorId = Number(searchParams.get('proveedorId')) || undefined
  const numero = Number(searchParams.get('numero')) || undefined
  const [numeroTexto, setNumeroTexto] = useState(numero ? String(numero) : '')
  const [pagina, setPagina] = useState(0)
  const [programando, setProgramando] = useState<Entrega | null>(null)
  const { aviso, mostrarAviso } = useAviso()
  const hoy = hoyEnBogota()

  const proveedores = useProveedoresStore((state) => state.items)
  const cargarProveedores = useProveedoresStore((state) => state.cargar)
  useEffect(() => {
    void cargarProveedores()
  }, [cargarProveedores])

  const filtro = `${vista}|${desde}|${hasta}|${proveedorId ?? ''}|${numero ?? ''}`
  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) =>
      entregaService.listar(
        { ...params, vista, desde: desde || undefined, hasta: hasta || undefined, proveedorId, numero },
        signal,
      ),
    [vista, desde, hasta, proveedorId, numero],
  )
  const { data, loading, error, reload } = usePaginatedResource(listar, pagina, TAMANO_PAGINA, '', filtro)
  const pedirResumen = useCallback((signal: AbortSignal) => entregaService.resumen(signal), [])
  const resumen = useRecurso(pedirResumen, 'resumen-entregas')

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

  const hayFiltrosExtra = Boolean(desde || hasta || proveedorId || numero)
  const r = resumen.datos
  const cifra = (valor: number | undefined) => (valor === undefined ? null : formatearNumero(valor))

  const columnas: Column<Entrega>[] = [
    {
      id: 'orden',
      header: 'Orden',
      cell: (e) => (
        <Link to={`/ordenes-compra/${e.ordenId}`} className="font-mono text-sm font-medium whitespace-nowrap text-foreground hover:underline">
          {numeroOrden(e.numero)}
        </Link>
      ),
    },
    { id: 'proveedor', header: 'Proveedor', cell: (e) => <span className="block min-w-36">{e.proveedor.razonSocial}</span> },
    { id: 'fecha', header: 'Llegada estimada', cell: (e) => <FechaEntrega programacion={e.programacion} compacta /> },
    {
      id: 'situacion',
      header: 'Situación',
      cell: (e) => (
        <div className="flex flex-col items-start gap-1">
          <SituacionEntregaBadge situacion={e.situacion} diasRetraso={e.diasRetraso} />
          {e.alertas.includes('INCIDENCIAS') && (
            <span className="inline-flex items-center gap-1 text-xs text-warning">
              <AlertTriangle className="size-3.5" aria-hidden="true" />
              {ETIQUETA_ALERTA.INCIDENCIAS} ({e.incidencias})
            </span>
          )}
        </div>
      ),
    },
    {
      id: 'recibido',
      header: 'Recibido',
      align: 'right',
      className: 'hidden md:table-cell',
      cell: (e) => (
        <span className="whitespace-nowrap tabular-nums text-foreground-muted">
          {formatearNumero(e.unidadesRecibidas)} / {formatearNumero(e.unidadesPedidas)}
          {e.recepciones > 0 && <span className="block text-xs">{e.recepciones} recepciones</span>}
        </span>
      ),
    },
    {
      id: 'ultima',
      header: 'Última recepción',
      className: 'hidden 2xl:table-cell',
      cell: (e) => (
        <span className="whitespace-nowrap text-foreground-muted">{e.ultimaRecepcionEn ? formatearFechaHora(e.ultimaRecepcionEn) : '—'}</span>
      ),
    },
    {
      id: 'acciones',
      header: <span className="sr-only">Acciones</span>,
      align: 'right',
      cell: (e) => {
        const abierta = e.estadoOrden === 'EMITIDA' || e.estadoOrden === 'RECIBIDA_PARCIALMENTE'
        return (
          <div className="flex justify-end gap-2">
            {permisos.programarEntregas && abierta && (
              <button type="button" className="u-btn u-btn--ghost h-8 px-2.5 text-sm whitespace-nowrap" onClick={() => setProgramando(e)}>
                <CalendarPlus className="size-4" aria-hidden="true" />
                {e.programacion ? 'Reprogramar' : 'Programar'}{' '}
                <span className="sr-only">{numeroOrden(e.numero)}</span>
              </button>
            )}
            <Link
              to={`/recepcion/${e.ordenId}`}
              className="u-btn u-btn--ghost h-8 px-2 text-sm whitespace-nowrap"
              title="Abrir la vista de recepción"
            >
              <PackageSearch className="size-4" aria-hidden="true" />
              <span className="sr-only">Recepción de {numeroOrden(e.numero)}</span>
            </Link>
          </div>
        )
      },
    },
  ]

  return (
    <>
      <PageHeader
        eyebrow="Compras"
        title="Entregas"
        description="Lo que se espera de cada proveedor y cuándo. Programa las fechas y revisa lo que necesita intervención."
        actions={
          <Link to="/calendario" className="u-btn u-btn--ghost">
            <CalendarClock className="size-4" aria-hidden="true" />
            Calendario
          </Link>
        }
      />
      <div role="status" aria-live="polite">
        {aviso && (
          <p className="u-alert u-alert--success mb-6 flex items-center gap-2">
            <CheckCircle2 className="size-4 shrink-0" aria-hidden="true" />
            {aviso}
          </p>
        )}
      </div>

      {resumen.error && (
        <p role="alert" className="u-alert u-alert--error mb-4">
          No se pudieron cargar las cifras: {resumen.error}
        </p>
      )}
      <div className="mb-6 grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <StatTile etiqueta="Requieren intervención" valor={cifra(r?.requierenIntervencion)} icono={Siren} claseIcono="text-danger"
          to="?vista=INTERVENCION" detalle="Retrasadas, sin fecha o con incidencias" />
        <StatTile etiqueta="Retrasadas" valor={cifra(r?.retrasadas)} icono={AlertTriangle} claseIcono="text-danger" to="?vista=RETRASADAS" />
        <StatTile etiqueta="Llegan hoy" valor={cifra(r?.paraHoy)} icono={CalendarClock} to={`?desde=${hoy}&hasta=${hoy}`} />
        <StatTile etiqueta="Sin programar" valor={cifra(r?.sinProgramar)} icono={CalendarX2} claseIcono="text-warning" to="?vista=SIN_PROGRAMAR" />
        <StatTile etiqueta="Recibidas en parte" valor={cifra(r?.parciales)} to="?vista=PARCIALES" />
        <StatTile etiqueta="Con incidencias" valor={cifra(r?.conIncidencias)} icono={AlertTriangle} claseIcono="text-warning"
          to="?vista=CON_INCIDENCIAS" />
      </div>

      <div className="mb-3 flex flex-wrap items-center gap-2" role="group" aria-label="Filtrar por situación">
        {VISTAS_SUPERVISION.map((valor) => (
          <button
            key={valor}
            type="button"
            aria-pressed={vista === valor}
            onClick={() => cambiar({ vista: valor === 'PENDIENTES' ? null : valor })}
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
      <form
        className="mb-4 flex flex-wrap items-end gap-3"
        aria-label="Más filtros"
        onSubmit={(event) => {
          event.preventDefault()
          cambiar({ numero: numeroTexto.replace(/\D/g, '') || null })
        }}
      >
        <label className="space-y-1">
          <span className="u-label">Llegada desde</span>
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
          <span className="u-label">N.º de orden</span>
          <input
            inputMode="numeric"
            value={numeroTexto}
            onChange={(e) => setNumeroTexto(e.target.value)}
            onBlur={() => cambiar({ numero: numeroTexto.replace(/\D/g, '') || null })}
            placeholder="42 o OC-00042"
            className="u-input w-36"
          />
        </label>
        {hayFiltrosExtra && (
          <button
            type="button"
            className="u-btn u-btn--ghost h-control"
            onClick={() => {
              setNumeroTexto('')
              cambiar({ desde: null, hasta: null, proveedorId: null, numero: null })
            }}
          >
            <X className="size-4" aria-hidden="true" />
            Quitar filtros
          </button>
        )}
      </form>

      <DataTable
        caption={`Entregas: ${ETIQUETA_VISTA[vista].toLowerCase()}`}
        columns={columnas}
        rows={data?.content ?? []}
        rowKey={(e) => e.ordenId}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage={
          hayFiltrosExtra || vista !== 'PENDIENTES'
            ? 'Ninguna entrega coincide con los filtros.'
            : 'No hay entregas pendientes. Aparecen al emitir órdenes de compra.'
        }
        page={data?.page}
        onPageChange={setPagina}
      />

      {programando && (
        <ProgramarEntregaModal
          ordenId={programando.ordenId}
          numero={programando.numero}
          proveedor={programando.proveedor.razonSocial}
          vigente={programando.programacion}
          onClose={() => setProgramando(null)}
          onGuardada={(p) => {
            const orden = numeroOrden(programando.numero)
            setProgramando(null)
            reload()
            resumen.recargar()
            mostrarAviso(`Entrega de ${orden} ${p.version > 1 ? 'reprogramada' : 'programada'} (versión ${p.version}).`)
          }}
        />
      )}
    </>
  )
}
