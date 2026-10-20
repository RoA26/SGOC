import { Ban, CheckCircle2, Undo2, type LucideIcon } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { EstadoVenta } from '@/services/ventaService'
import { ETIQUETA_ESTADO_VENTA } from './etiquetas'

const ESTILOS: Record<EstadoVenta, { clase: string; Icono: LucideIcon }> = {
  CONFIRMADA: { clase: 'border-success-border bg-success-surface text-success', Icono: CheckCircle2 },
  DEVUELTA_PARCIALMENTE: { clase: 'border-warning-border bg-warning-surface text-warning', Icono: Undo2 },
  DEVUELTA: { clase: 'border-border bg-surface-muted text-foreground-muted', Icono: Undo2 },
  ANULADA: { clase: 'border-danger-border bg-danger-surface text-danger', Icono: Ban },
}

/** Estado de una venta (siempre icono y texto). */
export function EstadoVentaBadge({ estado }: { estado: EstadoVenta }) {
  const { clase, Icono } = ESTILOS[estado]
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 font-sans text-xs font-medium whitespace-nowrap', clase)}>
      <Icono className="size-3.5" aria-hidden="true" />
      {ETIQUETA_ESTADO_VENTA[estado]}
    </span>
  )
}
