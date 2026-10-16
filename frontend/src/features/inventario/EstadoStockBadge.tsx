import { AlertTriangle, CheckCircle2, XCircle, type LucideIcon } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { EstadoStock } from '@/services/inventarioService'
import { ETIQUETA_ESTADO_STOCK } from './estados'

const ESTILOS: Record<EstadoStock, { clase: string; Icono: LucideIcon }> = {
  AGOTADO: { clase: 'border-danger-border bg-danger-surface text-danger', Icono: XCircle },
  CRITICO: { clase: 'border-warning-border bg-warning-surface text-warning', Icono: AlertTriangle },
  NORMAL: { clase: 'border-success-border bg-success-surface text-success', Icono: CheckCircle2 },
}

/** Situación de las existencias (estado reservado: siempre icono y texto). */
export function EstadoStockBadge({ estado }: { estado: EstadoStock }) {
  const { clase, Icono } = ESTILOS[estado]
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 font-sans text-xs font-medium whitespace-nowrap',
        clase,
      )}
    >
      <Icono className="size-3.5" aria-hidden="true" />
      {ETIQUETA_ESTADO_STOCK[estado]}
    </span>
  )
}
