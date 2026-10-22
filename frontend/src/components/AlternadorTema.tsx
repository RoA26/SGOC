import { Moon, Sun } from 'lucide-react'
import { cn } from '@/lib/cn'
import { cambiarTema, useTema } from '@/lib/tema'

/**
 * Botón de modo oscuro (conmutador): la etiqueta no cambia y `aria-pressed` indica si está
 * activo. El icono muestra el tema al que se pasa al pulsarlo.
 */
export function AlternadorTema({ className }: { className?: string }) {
  const oscuro = useTema() === 'dark'

  return (
    <button
      type="button"
      className={cn('u-btn u-btn--ghost size-10 shrink-0 p-0', className)}
      aria-label="Modo oscuro"
      aria-pressed={oscuro}
      title={oscuro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
      onClick={() => cambiarTema(oscuro ? 'light' : 'dark')}
    >
      {oscuro ? <Sun className="size-4" aria-hidden="true" /> : <Moon className="size-4" aria-hidden="true" />}
    </button>
  )
}
