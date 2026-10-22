import { Ban, CheckCircle2, CircleSlash, Clock, Loader2, Ticket, TicketPlus } from 'lucide-react'
import { useState } from 'react'
import { PageHeader } from '@/components/layout/PageHeader'
import { BotonCopiar } from '@/components/ui/BotonCopiar'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { generarCodigoInvitacion, listarInvitaciones, revocarInvitacion } from '@/features/auth/authApi'
import { usePermisos } from '@/features/auth/permisos'
import type { EstadoInvitacion, InvitacionResponse, InvitacionResumen } from '@/features/auth/types'
import { useAviso } from '@/hooks/useAviso'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { cn } from '@/lib/cn'
import { getApiErrorMessage } from '@/lib/errors'
import { formatearFechaHora } from '@/lib/fechas'

const formatoFecha = new Intl.DateTimeFormat('es', { dateStyle: 'medium', timeStyle: 'short' })

/** Enlace que precarga el código en el formulario de registro (/registro?codigo=…). */
function enlaceRegistro(codigo: string): string {
  return `${window.location.origin}/registro?codigo=${encodeURIComponent(codigo)}`
}

/**
 * Panel del ADMINISTRADOR_COMERCIAL: genera códigos de invitación de un solo uso para su empresa (la cuenta
 * creada con el código entra en ella con rol TRABAJADOR). Es el alta de trabajadores, no de
 * empresas: esas las crea el SUPER_ADMIN en «Empresas». El backend solo devuelve cada código al
 * crearlo, así que la página conserva los de esta visita.
 */
export default function Invitaciones() {
  // Solo el ADMINISTRADOR_COMERCIAL invita, siempre a su propia empresa (la ruta ya está restringida a su rol).
  const puedeInvitar = usePermisos().gestionarInvitaciones
  const [invitaciones, setInvitaciones] = useState<InvitacionResponse[]>([])
  const [generando, setGenerando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [recargaHistorial, setRecargaHistorial] = useState(0)

  async function generar() {
    setGenerando(true)
    setError(null)
    try {
      const nueva = await generarCodigoInvitacion()
      setInvitaciones((anteriores) => [nueva, ...anteriores])
      setRecargaHistorial((n) => n + 1)
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setGenerando(false)
    }
  }

  const [actual, ...anteriores] = invitaciones

  return (
    <>
      <PageHeader
        eyebrow="Administración"
        title="Invitar usuarios"
        description="Genera un código para cada persona de tu empresa que deba crear su cuenta. Entrará con el rol Trabajador. El código es de un solo uso y caduca a las 72 horas."
        actions={
          <button
            type="button"
            className="u-btn u-btn--primary disabled:cursor-not-allowed disabled:opacity-50"
            onClick={generar}
            disabled={generando || !puedeInvitar}
            aria-busy={generando}
          >
            {generando ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              <TicketPlus className="size-4" aria-hidden="true" />
            )}
            {generando ? 'Generando…' : 'Generar Nuevo Código'}
          </button>
        }
      />

      {/* Fuera de la tarjeta (que se vuelve a montar con cada código) para que se anuncie siempre. */}
      <p role="status" className="sr-only">
        {actual && `Nuevo código generado: ${actual.codigo}`}
      </p>

      {error && (
        <p role="alert" className="u-alert u-alert--error mb-6 max-w-3xl">
          {error}
        </p>
      )}

      {actual ? (
        <section
          key={actual.codigo}
          aria-labelledby="codigo-actual"
          className="u-card relative max-w-3xl overflow-hidden p-6 sm:p-8"
        >
          <div aria-hidden="true" className="absolute inset-y-0 left-0 w-1.5 bg-accent" />

          <h2 id="codigo-actual" className="text-[0.8125rem] font-medium text-foreground-muted">
            Código de invitación
          </h2>
          <div className="mt-3 flex flex-wrap items-center gap-x-6 gap-y-3">
            <p className="font-mono text-xl font-semibold tracking-[0.05em] text-foreground select-all sm:text-4xl sm:tracking-[0.12em]">
              {actual.codigo}
            </p>
            <BotonCopiar texto={actual.codigo} etiqueta="Copiar" ariaLabel="Copiar código" />
          </div>

          <p className="mt-4 flex items-center gap-2 text-sm text-foreground-muted">
            <Clock className="size-4 shrink-0" aria-hidden="true" />
            <span>
              Caduca el{' '}
              <time dateTime={actual.fechaExpiracion} className="font-medium text-foreground">
                {formatoFecha.format(new Date(actual.fechaExpiracion))}
              </time>
              . Sirve para una sola cuenta.
            </span>
          </p>

          <div className="mt-6 border-t border-border pt-5">
            <p className="text-sm font-medium text-foreground">Enlace de registro</p>
            <p className="mt-1 text-sm text-foreground-muted">
              Abre el formulario con el código ya escrito. Compártelo solo con la persona invitada.
            </p>
            <div className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-center">
              <code className="min-w-0 flex-1 truncate rounded-md bg-surface-muted px-3 py-2 font-mono text-sm text-foreground select-all">
                {enlaceRegistro(actual.codigo)}
              </code>
              <BotonCopiar texto={enlaceRegistro(actual.codigo)} etiqueta="Copiar enlace" />
            </div>
          </div>
        </section>
      ) : (
        <section className="u-card flex max-w-3xl flex-col items-center px-6 py-12 text-center">
          <span className="flex size-12 items-center justify-center rounded-full bg-surface-muted">
            <Ticket className="size-6 text-accent" aria-hidden="true" />
          </span>
          <h2 className="mt-4 font-medium text-foreground">Aún no has generado códigos</h2>
          <p className="mt-1 max-w-md text-sm text-foreground-muted">
            Pulsa «Generar Nuevo Código» y entrégalo a la persona invitada. Por seguridad, cada código
            solo se muestra mientras no salgas de esta página.
          </p>
        </section>
      )}

      {anteriores.length > 0 && (
        <section aria-labelledby="codigos-anteriores" className="mt-8 max-w-3xl">
          <h2 id="codigos-anteriores" className="text-sm font-semibold text-foreground">
            Generados antes en esta visita
          </h2>
          <ul className="mt-3 divide-y divide-border rounded-lg border border-border bg-surface">
            {anteriores.map(({ codigo, fechaExpiracion }) => (
              <li key={codigo} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                <div>
                  <p className="font-mono text-base font-medium tracking-wider text-foreground select-all">{codigo}</p>
                  <p className="text-xs text-foreground-muted">
                    Caduca el <time dateTime={fechaExpiracion}>{formatoFecha.format(new Date(fechaExpiracion))}</time>
                  </p>
                </div>
                <BotonCopiar texto={codigo} etiqueta="Copiar" ariaLabel={`Copiar código ${codigo}`} />
              </li>
            ))}
          </ul>
        </section>
      )}

      {puedeInvitar && <HistorialInvitaciones recarga={recargaHistorial} />}
    </>
  )
}

const TAMANO_PAGINA = 10
const ORDEN = 'creadoEn,desc'

const ESTADO: Record<EstadoInvitacion, { etiqueta: string; clase: string; Icono: typeof Clock }> = {
  VIGENTE: { etiqueta: 'Vigente', clase: 'border-success-border bg-success-surface text-success', Icono: CheckCircle2 },
  USADA: { etiqueta: 'Usada', clase: 'border-border-strong bg-surface-muted text-foreground', Icono: Ticket },
  REVOCADA: { etiqueta: 'Revocada', clase: 'border-danger-border bg-danger-surface text-danger', Icono: Ban },
  CADUCADA: { etiqueta: 'Caducada', clase: 'border-border bg-surface-muted text-foreground-muted', Icono: CircleSlash },
}

function EstadoInvitacionBadge({ estado }: { estado: EstadoInvitacion }) {
  const { etiqueta, clase, Icono } = ESTADO[estado]
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium whitespace-nowrap', clase)}>
      <Icono className="size-3.5" aria-hidden="true" />
      {etiqueta}
    </span>
  )
}

/**
 * Invitaciones de la empresa con su estado real según el backend. El código completo no se
 * vuelve a mostrar (es una credencial): se reconoce por sus 4 últimos caracteres. Una vigente
 * se puede revocar; deja de aceptarse y se conserva en el historial.
 */
function HistorialInvitaciones({ recarga }: { recarga: number }) {
  const [pagina, setPagina] = useState(0)
  const { data, loading, error, reload } = usePaginatedResource(listarInvitaciones, pagina, TAMANO_PAGINA, ORDEN, String(recarga))
  const [porRevocar, setPorRevocar] = useState<InvitacionResumen | null>(null)
  const [revocando, setRevocando] = useState(false)
  const [errorRevocar, setErrorRevocar] = useState<string | null>(null)
  const { aviso, mostrarAviso } = useAviso()

  async function confirmarRevocacion() {
    if (!porRevocar) return
    setRevocando(true)
    setErrorRevocar(null)
    try {
      await revocarInvitacion(porRevocar.id)
      mostrarAviso(`Invitación ${porRevocar.codigoParcial} revocada.`)
      setPorRevocar(null)
      reload()
    } catch (err) {
      // 409: se usó, se revocó o caducó mientras tanto.
      setErrorRevocar(getApiErrorMessage(err))
    } finally {
      setRevocando(false)
    }
  }

  const columnas: Column<InvitacionResumen>[] = [
    {
      id: 'codigo',
      header: 'Código',
      cell: (invitacion) => <span className="font-mono text-xs whitespace-nowrap text-foreground">{invitacion.codigoParcial}</span>,
    },
    { id: 'estado', header: 'Estado', cell: (invitacion) => <EstadoInvitacionBadge estado={invitacion.estado} /> },
    {
      id: 'creada',
      header: 'Creada',
      className: 'hidden md:table-cell',
      cell: (invitacion) => (
        <div className="whitespace-nowrap">
          <p>{formatearFechaHora(invitacion.creadoEn)}</p>
          <p className="text-xs text-foreground-muted">por {invitacion.creador.nombre}</p>
        </div>
      ),
    },
    {
      id: 'detalle',
      header: 'Detalle',
      cell: (invitacion) => (
        <span className="text-sm text-foreground-muted">
          {invitacion.estado === 'USADA' && invitacion.usadoPor && `Cuenta de ${invitacion.usadoPor.nombre}`}
          {invitacion.estado === 'REVOCADA' && invitacion.revocadoPor && `Revocada por ${invitacion.revocadoPor.nombre}`}
          {(invitacion.estado === 'VIGENTE' || invitacion.estado === 'CADUCADA') &&
            `${invitacion.estado === 'VIGENTE' ? 'Caduca' : 'Caducó'} el ${formatearFechaHora(invitacion.fechaExpiracion)}`}
        </span>
      ),
    },
    {
      id: 'acciones',
      header: <span className="sr-only">Acciones</span>,
      align: 'right',
      cell: (invitacion) =>
        invitacion.estado === 'VIGENTE' && (
          <button
            type="button"
            className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm text-foreground-muted hover:bg-danger-surface hover:text-danger"
            onClick={() => {
              setErrorRevocar(null)
              setPorRevocar(invitacion)
            }}
            aria-label={`Revocar la invitación ${invitacion.codigoParcial}`}
          >
            <Ban className="size-4" aria-hidden="true" />
            Revocar
          </button>
        ),
    },
  ]

  return (
    <section aria-labelledby="historial-invitaciones" className="mt-10 max-w-5xl">
      <h2 id="historial-invitaciones" className="text-sm font-semibold text-foreground">
        Invitaciones de la empresa
      </h2>
      <p className="mt-1 mb-3 text-sm text-foreground-muted">
        Estado de cada código generado. Revoca los que ya no deban usarse: dejan de valer al instante.
      </p>
      <p role="status" aria-live="polite">
        {aviso && <span className="u-alert u-alert--success mb-3 block">{aviso}</span>}
      </p>
      <DataTable
        caption="Invitaciones de la empresa"
        columns={columnas}
        rows={data?.content ?? []}
        rowKey={(invitacion) => invitacion.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage="Aún no se ha generado ninguna invitación."
        page={data?.page}
        onPageChange={setPagina}
      />
      <ConfirmDialog
        open={porRevocar !== null}
        title="Revocar invitación"
        confirmLabel="Revocar"
        busy={revocando}
        error={errorRevocar}
        onConfirm={() => void confirmarRevocacion()}
        onCancel={() => setPorRevocar(null)}
      >
        <p>
          ¿Revocar la invitación <strong className="font-mono text-foreground">{porRevocar?.codigoParcial}</strong>? Nadie
          podrá registrarse con ella. Si ya la compartiste, genera otra para esa persona.
        </p>
      </ConfirmDialog>
    </section>
  )
}
