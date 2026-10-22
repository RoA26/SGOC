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
 * Indicador numérico: la cifra es la gráfica, en cifras tabulares de la tipografía de la interfaz.
 * Mientras carga, un hueco del tamaño de la cifra (la tarjeta no cambia de alto al llegar el dato).
 */
export function StatTile({ etiqueta, valor, detalle, icono: Icono, claseIcono, to }: StatTileProps) {
  const contenido = (
    <>
      <p className="flex items-center gap-1.5 text-sm text-foreground-muted">
        {Icono && <Icono className={cn('size-4 shrink-0', claseIcono ?? 'text-foreground-muted')} aria-hidden="true" />}
        {etiqueta}
      </p>
      {valor === null ? (
        <span className="u-skeleton mt-2.5 block h-7 w-24" aria-hidden="true" />
      ) : (
        <p className="mt-2 text-[1.75rem] leading-tight font-semibold tracking-tight text-foreground tabular-nums">{valor}</p>
      )}
      {detalle && <p className="mt-1 text-xs text-foreground-muted">{detalle}</p>}
    </>
  )
  return to ? (
    <Link to={to} className="u-card u-card-interactiva group block p-5" aria-busy={valor === null}>
      {contenido}
    </Link>
  ) : (
    <div className="u-card p-5" aria-busy={valor === null}>
      {contenido}
    </div>
  )
}
