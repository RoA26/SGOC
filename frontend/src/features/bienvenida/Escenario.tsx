import { useEffect, useState, type ReactNode } from 'react'
import { cn } from '@/lib/cn'
import { FragmentoExistencias, FragmentoOrden, FragmentoRecepcion } from './fragmentos'

const VISTAS: readonly { nombre: string; fragmento: ReactNode }[] = [
  { nombre: 'Orden', fragmento: <FragmentoOrden /> },
  { nombre: 'Recepción', fragmento: <FragmentoRecepcion /> },
  { nombre: 'Existencias', fragmento: <FragmentoExistencias /> },
]

/** Tiempo que se mantiene cada vista antes del fundido (el fundido dura 1,6 s, en el CSS). */
const PERMANENCIA_MS = 6000

function prefiereMenosMovimiento(): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false
}

/**
 * Escenario del encabezado: la misma compra vista en tres momentos, con un fundido lento entre
 * ellos. Se detiene con la pestaña oculta, mientras el puntero o el foco están encima, cuando la
 * persona elige una vista y con movimiento reducido (entonces solo cambia con los botones).
 */
export function Escenario() {
  const [actual, setActual] = useState(0)
  const [pausado, setPausado] = useState(false)
  const [elegido, setElegido] = useState(prefiereMenosMovimiento)

  useEffect(() => {
    if (pausado || elegido) return
    const id = window.setInterval(() => {
      if (!document.hidden) setActual((i) => (i + 1) % VISTAS.length)
    }, PERMANENCIA_MS)
    return () => window.clearInterval(id)
  }, [pausado, elegido])

  return (
    <div
      className="mx-auto flex h-full w-full max-w-[34rem] flex-col justify-center gap-6"
      onPointerEnter={() => setPausado(true)}
      onPointerLeave={() => setPausado(false)}
      onFocus={() => setPausado(true)}
      onBlur={() => setPausado(false)}
    >
      <div className="grid grid-cols-[minmax(0,1fr)]">
        {VISTAS.map((vista, i) => (
          <div
            key={vista.nombre}
            aria-hidden={i !== actual}
            className={cn('u-fundido col-start-1 row-start-1 self-center', i === actual ? 'opacity-100' : 'pointer-events-none opacity-0')}
          >
            {vista.fragmento}
          </div>
        ))}
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div role="group" aria-label="Vista de ejemplo" className="flex gap-1">
          {VISTAS.map((vista, i) => (
            <button
              key={vista.nombre}
              type="button"
              aria-pressed={i === actual}
              onClick={() => {
                setActual(i)
                setElegido(true)
              }}
              className={cn(
                'rounded-full px-3 py-1 text-[0.8125rem] font-medium transition-colors duration-150',
                i === actual ? 'bg-surface text-foreground' : 'text-foreground-muted hover:text-foreground',
              )}
            >
              {vista.nombre}
            </button>
          ))}
        </div>
        <p className="text-[0.8125rem] text-foreground-muted">Datos de ejemplo de una empresa de demostración</p>
      </div>
    </div>
  )
}
