import { CheckCircle2, Info, Ticket } from 'lucide-react'
import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DataTable, type Column } from '@/components/ui/DataTable'
import type { EstadoUsuario } from '@/features/auth/types'
import { EstadoUsuarioBadge } from '@/features/empresa/EstadosBadges'
import { accionesDisponibles, describirUltimaOperacion, resumirRecientes, type AccionEstado } from '@/features/personal/actividad'
import { personalService } from '@/features/personal/personalApi'
import type { PersonalTrabajador } from '@/features/personal/types'
import { useAviso } from '@/hooks/useAviso'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { cn } from '@/lib/cn'
import { getApiErrorMessage } from '@/lib/errors'
import { formatearFecha } from '@/lib/fechas'
import type { PageParams } from '@/services/types'

const TAMANO_PAGINA = 20
const ORDEN = 'creadoEn,desc'

const ESTADOS_VALIDOS: readonly EstadoUsuario[] = ['PENDIENTE', 'ACTIVO', 'INACTIVO', 'RECHAZADO']
const FILTROS: { valor: EstadoUsuario | null; etiqueta: string }[] = [
  { valor: null, etiqueta: 'Todos' },
  { valor: 'PENDIENTE', etiqueta: 'Pendientes' },
  { valor: 'ACTIVO', etiqueta: 'Activos' },
  { valor: 'INACTIVO', etiqueta: 'Inactivos' },
  { valor: 'RECHAZADO', etiqueta: 'Rechazados' },
]
const VACIO: Record<EstadoUsuario, string> = {
  PENDIENTE: 'No hay trabajadores pendientes de aprobación.',
  ACTIVO: 'No hay trabajadores activos.',
  INACTIVO: 'No hay trabajadores inactivos.',
  RECHAZADO: 'No hay registros rechazados.',
}
const AVISO: Record<EstadoUsuario, string> = {
  ACTIVO: 'ya puede iniciar sesión y operar',
  INACTIVO: 'ya no puede operar',
  RECHAZADO: 'no tendrá acceso',
  PENDIENTE: '',
}

function estadoDeUrl(valor: string | null): EstadoUsuario | null {
  return ESTADOS_VALIDOS.find((estado) => estado === valor) ?? null
}

interface Pendiente {
  trabajador: PersonalTrabajador
  accion: AccionEstado
}

/**
 * Personal del ADMINISTRADOR_COMERCIAL: los trabajadores de su empresa con el estado real de su
 * cuenta y la actividad que registraron (solicitudes, recepciones y ventas). Desde aquí aprueba a
 * quien se registró con el código de empresa y desactiva o reactiva cuentas, como ya permite el
 * backend. La URL guarda el filtro (?estado=PENDIENTE).
 *
 * No muestra la "última conexión": el sistema no registra los inicios de sesión.
 */
export default function Personal() {
  const [searchParams, setSearchParams] = useSearchParams()
  const estado = estadoDeUrl(searchParams.get('estado'))
  const [pagina, setPagina] = useState(0)
  const { aviso, mostrarAviso } = useAviso()
  const [confirmando, setConfirmando] = useState<Pendiente | null>(null)
  const [enCurso, setEnCurso] = useState<number | null>(null)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)

  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) => personalService.listar({ ...params, estado: estado ?? undefined }, signal),
    [estado],
  )
  const { data, loading, error, reload } = usePaginatedResource(listar, pagina, TAMANO_PAGINA, ORDEN, estado ?? '')

  function filtrar(valor: EstadoUsuario | null) {
    setSearchParams((actuales) => {
      const siguientes = new URLSearchParams(actuales)
      if (valor) siguientes.set('estado', valor)
      else siguientes.delete('estado')
      return siguientes
    })
    setPagina(0)
  }

  async function aplicar({ trabajador, accion }: Pendiente) {
    setEnCurso(trabajador.id)
    setErrorAccion(null)
    try {
      await personalService.cambiarEstado(trabajador.id, accion.destino)
      setConfirmando(null)
      mostrarAviso(`${trabajador.nombre} ${AVISO[accion.destino]}.`)
      reload()
    } catch (err) {
      setErrorAccion(getApiErrorMessage(err))
    } finally {
      setEnCurso(null)
    }
  }

  function solicitar(pendiente: Pendiente) {
    setErrorAccion(null)
    if (pendiente.accion.restrictiva) setConfirmando(pendiente)
    else void aplicar(pendiente)
  }

  const columnas: Column<PersonalTrabajador>[] = [
    {
      id: 'nombre',
      header: 'Trabajador',
      cell: (t) => (
        <div className="min-w-40">
          <p className="font-medium text-foreground">{t.nombre}</p>
          <p className="font-mono text-xs text-foreground-muted">@{t.username}</p>
        </div>
      ),
    },
    { id: 'estado', header: 'Estado', cell: (t) => <EstadoUsuarioBadge estado={t.estado} /> },
    {
      id: 'actividad',
      header: 'Última operación registrada',
      cell: (t) => {
        const ultima = describirUltimaOperacion(t.actividad)
        return (
          <div className="min-w-56">
            {ultima ? <p>{ultima}</p> : <p className="text-foreground-muted">Sin actividad registrada</p>}
            <p className="text-xs text-foreground-muted">
              {`Últimos ${t.actividad.dias} días: ${resumirRecientes(t.actividad) ?? 'sin operaciones'}`}
            </p>
          </div>
        )
      },
    },
    {
      id: 'alta',
      header: 'Alta',
      className: 'hidden xl:table-cell',
      cell: (t) => <span className="whitespace-nowrap text-foreground-muted">{formatearFecha(t.creadoEn)}</span>,
    },
    {
      id: 'acciones',
      header: <span className="sr-only">Acciones</span>,
      align: 'right',
      cell: (t) => (
        <div className="flex justify-end gap-2">
          {accionesDisponibles(t.estado).map((accion) => (
            <button
              key={accion.destino}
              type="button"
              className={cn('u-btn h-9 px-3 text-sm', accion.restrictiva ? 'u-btn--ghost' : 'u-btn--primary')}
              onClick={() => solicitar({ trabajador: t, accion })}
              disabled={enCurso !== null}
              aria-busy={enCurso === t.id}
              aria-label={`${accion.etiqueta} a ${t.nombre}`}
            >
              {accion.etiqueta}
            </button>
          ))}
        </div>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        eyebrow="Administración"
        title="Personal"
        description="Trabajadores de tu empresa, el estado de su cuenta y la actividad que han registrado."
        actions={
          <Link to="/admin/invitaciones" className="u-btn u-btn--ghost">
            <Ticket className="size-4" aria-hidden="true" />
            Invitar usuarios
          </Link>
        }
      />

      <p className="mb-4 flex items-start gap-2 text-sm text-foreground-muted">
        <Info className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        La actividad cuenta las solicitudes, recepciones y ventas que registró cada persona. No indica cuándo se
        conectó por última vez: el sistema no guarda los inicios de sesión.
      </p>

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

      <div role="status" aria-live="polite">
        {aviso && (
          <p className="mb-4 flex items-center gap-2 rounded-md border border-success/30 bg-success/10 px-4 py-2.5 text-sm text-success">
            <CheckCircle2 className="size-4" aria-hidden="true" />
            {aviso}
          </p>
        )}
      </div>
      {errorAccion && confirmando === null && (
        <p role="alert" className="u-alert u-alert--error mb-4">
          {errorAccion}
        </p>
      )}

      <DataTable
        caption="Trabajadores de la empresa"
        columns={columnas}
        rows={data?.content ?? []}
        rowKey={(t) => t.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage={estado ? VACIO[estado] : 'Tu empresa aún no tiene trabajadores. Invítalos desde «Invitar usuarios».'}
        page={data?.page}
        onPageChange={setPagina}
      />

      <ConfirmDialog
        open={confirmando !== null}
        title={confirmando?.accion.destino === 'RECHAZADO' ? 'Rechazar registro' : 'Desactivar cuenta'}
        confirmLabel={confirmando?.accion.etiqueta ?? ''}
        busy={enCurso !== null}
        error={errorAccion}
        onConfirm={() => confirmando && void aplicar(confirmando)}
        onCancel={() => setConfirmando(null)}
      >
        {confirmando?.accion.destino === 'RECHAZADO' ? (
          <p>
            ¿Rechazar el registro de <strong className="text-foreground">{confirmando.trabajador.nombre}</strong>? No podrá
            iniciar sesión. Podrás aprobarlo más adelante si fue un error.
          </p>
        ) : (
          <p>
            ¿Desactivar la cuenta de <strong className="text-foreground">{confirmando?.trabajador.nombre}</strong>? Perderá
            el acceso desde su siguiente acción, aunque tenga la sesión abierta. Sus registros se conservan y podrás
            reactivarla después.
          </p>
        )}
      </ConfirmDialog>
    </>
  )
}
