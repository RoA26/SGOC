import { AlertTriangle, Ban, CalendarClock, CalendarX2, CheckCircle2, FilePen, type LucideIcon } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { SituacionEntrega } from '@/services/entregaService'
import { ETIQUETA_SITUACION } from './etiquetas'

const ESTILOS: Record<SituacionEntrega, { clase: string; Icono: LucideIcon }> = {
  BORRADOR: { clase: 'border-border-strong bg-surface-muted text-foreground', Icono: FilePen },
  SIN_PROGRAMAR: { clase: 'border-warning-border bg-warning-surface text-warning', Icono: CalendarX2 },
  PROGRAMADA: { clase: 'border-border-strong bg-surface text-foreground', Icono: CalendarClock },
  RETRASADA: { clase: 'border-danger-border bg-danger-surface text-danger', Icono: AlertTriangle },
  COMPLETA: { clase: 'border-success-border bg-success-surface text-success', Icono: CheckCircle2 },
  CANCELADA: { clase: 'border-danger-border bg-danger-surface text-danger', Icono: Ban },
}

/** Situación de la entrega: icono y texto, nunca solo color. */
export function SituacionEntregaBadge({ situacion, diasRetraso = 0 }: { situacion: SituacionEntrega; diasRetraso?: number }) {
  const { clase, Icono } = ESTILOS[situacion]
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 font-sans text-xs font-medium whitespace-nowrap',
        clase,
      )}
    >
      <Icono className="size-3.5" aria-hidden="true" />
      {ETIQUETA_SITUACION[situacion]}
      {situacion === 'RETRASADA' && diasRetraso > 0 && ` · ${diasRetraso} ${diasRetraso === 1 ? 'día' : 'días'}`}
    </span>
  )
}
