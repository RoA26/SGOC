import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { cn } from '@/lib/cn'

interface StatTileProps {
  etiqueta: string
  /** null mientras carga. */
  valor: string | null
  detalle?: ReactNode
  icono?: LucideIcon
  /** Clases del icono (p. ej. el color de un estado, que siempre va con su texto). */
  claseIcono?: string
  to?: string
}

/**
 * Indicador numérico: la cifra es la gráfica. Cifras proporcionales (no tabulares) en la misma
 * tipografía sans de la interfaz.
 */
export function StatTile({ etiqueta, valor, detalle, icono: Icono, claseIcono, to }: StatTileProps) {
  const contenido = (
    <>
      <p className="flex items-center gap-1.5 text-sm text-foreground-muted">
        {Icono && <Icono className={cn('size-4 shrink-0', claseIcono ?? 'text-foreground-muted')} aria-hidden="true" />}
        {etiqueta}
      </p>
      <p className="mt-2 text-3xl font-semibold tracking-tight text-foreground">{valor ?? '—'}</p>
      {detalle && <p className="mt-1 text-xs text-foreground-muted">{detalle}</p>}
    </>
  )
  return to ? (
    <Link to={to} className="u-card group block p-5 transition-colors hover:border-border-strong" aria-busy={valor === null}>
      {contenido}
    </Link>
  ) : (
    <div className="u-card p-5" aria-busy={valor === null}>
      {contenido}
    </div>
  )
}
