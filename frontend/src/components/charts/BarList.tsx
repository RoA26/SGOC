import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { cn } from '@/lib/cn'

export interface FilaBarra {
  clave: string
  etiqueta: ReactNode
  valor: number
  /** Texto del valor al final de la barra (por defecto, el número). */
  valorVisible?: string
  /** Enlace al detalle (p. ej. el listado filtrado por ese estado). */
  to?: string
}

/**
 * Barras horizontales de una sola serie (magnitud por categoría): un color para todas, sin
 * leyenda. Finas (12 px), con el extremo redondeado y la base recta, y el valor al final de cada
 * barra: ninguna cifra depende del puntero ni del color.
 */
export function BarList({
  filas,
  descripcion,
  apilado = false,
}: {
  filas: FilaBarra[]
  descripcion: string
  /** Etiqueta encima de la barra (nombres largos, p. ej. empresas): nunca se recorta. */
  apilado?: boolean
}) {
  const maximo = filas.reduce((max, fila) => Math.max(max, fila.valor), 0)
  return (
    <ul aria-label={descripcion} className="space-y-1">
      {filas.map((fila) => {
        const porcentaje = maximo > 0 ? (fila.valor / maximo) * 100 : 0
        const contenido = (
          <>
            <span className={cn('text-sm text-foreground', apilado ? 'break-words' : 'truncate')}>{fila.etiqueta}</span>
            <span className="flex min-w-0 items-center gap-2">
              {fila.valor > 0 && (
                <span
                  aria-hidden="true"
                  className="u-barra h-3 shrink-0 rounded-r-[4px] bg-viz-1 transition-opacity group-hover:opacity-75"
                  style={{ width: `max(4px, calc(${porcentaje}% - 3.5rem))` }}
                />
              )}
              <span className="text-sm font-medium text-foreground tabular-nums">{fila.valorVisible ?? fila.valor}</span>
            </span>
          </>
        )
        const clases = apilado
          ? 'group grid gap-1 rounded-md px-2 py-1.5'
          : 'group grid grid-cols-[minmax(0,9rem)_minmax(0,1fr)] items-center gap-3 rounded-md px-2 py-1.5'
        return (
          <li key={fila.clave}>
            {fila.to ? (
              <Link to={fila.to} className={cn(clases, 'hover:bg-surface-muted/60')}>
                {contenido}
              </Link>
            ) : (
              <div className={clases}>{contenido}</div>
            )}
          </li>
        )
      })}
    </ul>
  )
}
