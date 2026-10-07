import { Clock, Loader2, Ticket, TicketPlus } from 'lucide-react'
import { useState } from 'react'
import { PageHeader } from '@/components/layout/PageHeader'
import { BotonCopiar } from '@/components/ui/BotonCopiar'
import { generarCodigoInvitacion } from '@/features/auth/authApi'
import type { InvitacionResponse } from '@/features/auth/types'
import { getApiErrorMessage } from '@/lib/errors'

const formatoFecha = new Intl.DateTimeFormat('es', { dateStyle: 'medium', timeStyle: 'short' })

/** Enlace que precarga el código en el formulario de registro (/registro?codigo=…). */
function enlaceRegistro(codigo: string): string {
  return `${window.location.origin}/registro?codigo=${encodeURIComponent(codigo)}`
}

/**
 * Panel de administración: genera códigos de invitación de un solo uso. El backend solo
 * devuelve cada código al crearlo, así que la página conserva los de esta visita.
 */
export default function Invitaciones() {
  const [invitaciones, setInvitaciones] = useState<InvitacionResponse[]>([])
  const [generando, setGenerando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function generar() {
    setGenerando(true)
    setError(null)
    try {
      const nueva = await generarCodigoInvitacion()
      setInvitaciones((anteriores) => [nueva, ...anteriores])
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
        title="Invitaciones"
        description="Genera un código para cada persona que deba crear su cuenta. Es de un solo uso y caduca a las 72 horas."
        actions={
          <button type="button" className="u-btn u-btn--primary" onClick={generar} disabled={generando} aria-busy={generando}>
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

          <h2 id="codigo-actual" className="text-xs font-medium tracking-widest text-foreground-muted uppercase">
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
    </>
  )
}
