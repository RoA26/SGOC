import { CalendarRange, Check } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { cn } from '@/lib/cn'
import { hoyNegocio, restarDias } from '@/lib/periodo'
import type { SeleccionPeriodo } from './seleccionPeriodo'

const PRESETS = [
  { dias: 7, etiqueta: '7 días' },
  { dias: 30, etiqueta: '30 días' },
  { dias: 90, etiqueta: '90 días' },
  { dias: 365, etiqueta: '12 meses' },
] as const

/**
 * Período que acota todos los indicadores de debajo (una sola fila, encima de las gráficas).
 * Primero los presets; el rango personalizado lo valida también el backend (máx. 366 días, nunca
 * futuro).
 */
export function FiltroPeriodo({
  seleccion,
  onCambio,
}: {
  seleccion: SeleccionPeriodo
  onCambio: (seleccion: SeleccionPeriodo) => void
}) {
  const hoy = hoyNegocio()
  const [personalizando, setPersonalizando] = useState(seleccion.tipo === 'personalizado')
  const [desde, setDesde] = useState(seleccion.tipo === 'personalizado' ? seleccion.desde : restarDias(hoy, 29))
  const [hasta, setHasta] = useState(seleccion.tipo === 'personalizado' ? seleccion.hasta : hoy)
  const idDesde = useId()
  const idHasta = useId()

  function aplicar(event: FormEvent) {
    event.preventDefault()
    if (desde && hasta) onCambio({ tipo: 'personalizado', desde, hasta })
  }

  return (
    <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Período de los indicadores">
      {PRESETS.map((preset) => {
        const activo = seleccion.tipo === 'preset' && seleccion.dias === preset.dias
        return (
          <button
            key={preset.dias}
            type="button"
            aria-pressed={activo}
            onClick={() => {
              setPersonalizando(false)
              onCambio({ tipo: 'preset', dias: preset.dias })
            }}
            className={cn(
              'inline-flex h-8 items-center gap-1 rounded-full border px-3 text-sm',
              activo
                ? 'border-foreground bg-foreground font-medium text-background'
                : 'border-border text-foreground-muted hover:bg-surface-muted hover:text-foreground',
            )}
          >
            {activo && <Check className="size-3.5" strokeWidth={3} aria-hidden="true" />}
            {preset.etiqueta}
          </button>
        )
      })}
      <button
        type="button"
        aria-pressed={seleccion.tipo === 'personalizado'}
        aria-expanded={personalizando}
        onClick={() => setPersonalizando((actual) => !actual)}
        className={cn(
          'inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-sm',
          seleccion.tipo === 'personalizado'
            ? 'border-foreground bg-foreground font-medium text-background'
            : 'border-border text-foreground-muted hover:bg-surface-muted hover:text-foreground',
        )}
      >
        <CalendarRange className="size-3.5" aria-hidden="true" />
        Personalizado
      </button>
      {personalizando && (
        <form onSubmit={aplicar} className="flex flex-wrap items-center gap-2 border-l border-border pl-3">
          <label htmlFor={idDesde} className="text-xs text-foreground-muted">
            Desde
          </label>
          <input
            id={idDesde}
            type="date"
            value={desde}
            max={hasta || hoy}
            onChange={(event) => setDesde(event.target.value)}
            className="u-input h-8 w-auto py-0 text-sm"
            required
          />
          <label htmlFor={idHasta} className="text-xs text-foreground-muted">
            Hasta
          </label>
          <input
            id={idHasta}
            type="date"
            value={hasta}
            min={desde}
            max={hoy}
            onChange={(event) => setHasta(event.target.value)}
            className="u-input h-8 w-auto py-0 text-sm"
            required
          />
          <button type="submit" className="u-btn u-btn--ghost h-8 px-3 text-sm">
            Aplicar
          </button>
        </form>
      )}
    </div>
  )
}
