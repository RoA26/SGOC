import { Ban, CheckCircle2, FilePen, PackageCheck, Send, type LucideIcon } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { EstadoOrdenCompra } from '@/services/ordenCompraService'
import { ETIQUETA_ESTADO_ORDEN } from './estados'

const ESTILOS: Record<EstadoOrdenCompra, { clase: string; Icono: LucideIcon }> = {
  BORRADOR: { clase: 'border-border-strong bg-surface-muted text-foreground', Icono: FilePen },
  EMITIDA: { clase: 'border-border-strong bg-surface text-foreground', Icono: Send },
  RECIBIDA_PARCIALMENTE: { clase: 'border-warning-border bg-warning-surface text-warning', Icono: PackageCheck },
  RECIBIDA: { clase: 'border-success-border bg-success-surface text-success', Icono: CheckCircle2 },
  CANCELADA: { clase: 'border-danger-border bg-danger-surface text-danger', Icono: Ban },
}

/** Estado de una orden de compra: icono y texto, nunca solo color. */
export function EstadoOrdenBadge({ estado }: { estado: EstadoOrdenCompra }) {
  const { clase, Icono } = ESTILOS[estado]
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 font-sans text-xs font-medium whitespace-nowrap',
        clase,
      )}
    >
      <Icono className="size-3.5" aria-hidden="true" />
      {ETIQUETA_ESTADO_ORDEN[estado]}
    </span>
  )
}
