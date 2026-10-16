import { RotateCw } from 'lucide-react'
import type { ReactNode } from 'react'

/** Error de carga de un panel, con reintento. */
export function ErrorPanel({ mensaje, onReintentar }: { mensaje: string; onReintentar: () => void }) {
  return (
    <div role="alert" className="u-alert u-alert--error flex flex-wrap items-center justify-between gap-3">
      <span>No se pudieron cargar los indicadores: {mensaje}</span>
      <button type="button" className="u-btn u-btn--ghost h-8 px-3 text-sm" onClick={onReintentar}>
        <RotateCw className="size-4" aria-hidden="true" />
        Reintentar
      </button>
    </div>
  )
}

/** Primera carga: huecos del tamaño final (las recargas conservan los datos anteriores). */
export function EsqueletoPanel({ tarjetas = 4 }: { tarjetas?: number }) {
  return (
    <div aria-busy="true" aria-label="Cargando indicadores" className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: tarjetas }, (_, indice) => (
          <div key={indice} className="u-card h-28 animate-pulse bg-surface-muted/40" />
        ))}
      </div>
      <div className="u-card h-64 animate-pulse bg-surface-muted/40" />
    </div>
  )
}

/** Ninguna actividad registrada aún (distinto de "0 en este período"). */
export function SinDatos({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <div className="flex h-full min-h-40 flex-col items-center justify-center rounded-md border border-dashed border-border px-6 py-8 text-center">
      <p className="text-sm font-medium text-foreground">{titulo}</p>
      <p className="mt-1 max-w-sm text-xs text-foreground-muted">{children}</p>
    </div>
  )
}
