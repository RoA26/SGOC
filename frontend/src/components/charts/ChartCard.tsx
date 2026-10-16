import { BarChart3, Table2 } from 'lucide-react'
import { useId, useState, type ReactNode } from 'react'
import { cn } from '@/lib/cn'

interface ChartCardProps {
  titulo: string
  subtitulo?: ReactNode
  /** La gráfica. */
  children: ReactNode
  /** Su equivalente en tabla (accesible y sin depender del color ni del puntero). */
  tabla: ReactNode
  /** Mientras se recargan los datos se conserva la gráfica anterior, atenuada. */
  recargando?: boolean
  className?: string
}

/**
 * Contenedor de una gráfica: título, subtítulo y el cambio a vista de tabla. La altura crece con
 * el contenido (incluido el eje X): nunca aparece un desplazamiento interno.
 */
export function ChartCard({ titulo, subtitulo, children, tabla, recargando = false, className }: ChartCardProps) {
  const [verTabla, setVerTabla] = useState(false)
  const tituloId = useId()
  return (
    <figure aria-labelledby={tituloId} className={cn('u-card flex min-w-0 flex-col p-5', className)}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 id={tituloId} className="font-medium text-foreground">
            {titulo}
          </h3>
          {subtitulo && <p className="mt-0.5 text-xs text-foreground-muted">{subtitulo}</p>}
        </div>
        <button
          type="button"
          className="inline-flex shrink-0 items-center gap-1.5 rounded-md px-2 py-1 text-xs text-foreground-muted hover:bg-surface-muted hover:text-foreground"
          onClick={() => setVerTabla((actual) => !actual)}
          aria-pressed={verTabla}
        >
          {verTabla ? <BarChart3 className="size-3.5" aria-hidden="true" /> : <Table2 className="size-3.5" aria-hidden="true" />}
          {verTabla ? 'Ver gráfica' : 'Ver tabla'}
        </button>
      </div>
      <div className={cn('mt-4 transition-opacity', recargando && 'opacity-50')} aria-busy={recargando}>
        {verTabla ? tabla : children}
      </div>
    </figure>
  )
}

/** Tabla sencilla para la vista accesible de una gráfica. */
export function TablaDatos({
  encabezados,
  filas,
}: {
  encabezados: [string, string]
  filas: { clave: string; etiqueta: ReactNode; valor: ReactNode }[]
}) {
  return (
    <div className="max-h-80 overflow-y-auto rounded-md border border-border">
      <table className="w-full text-left text-sm">
        <thead className="sticky top-0 bg-surface-muted text-xs text-foreground-muted">
          <tr>
            <th scope="col" className="px-3 py-2 font-medium">
              {encabezados[0]}
            </th>
            <th scope="col" className="px-3 py-2 text-right font-medium">
              {encabezados[1]}
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {filas.map((fila) => (
            <tr key={fila.clave}>
              <td className="px-3 py-1.5 text-foreground">{fila.etiqueta}</td>
              <td className="px-3 py-1.5 text-right text-foreground tabular-nums">{fila.valor}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
