import { CheckCircle2, Clock, XCircle } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { EstadoSolicitud } from '@/services/solicitudService'
import { ETIQUETA_ESTADO } from './estados'

const ESTILOS: Record<EstadoSolicitud, { clase: string; Icono: typeof Clock }> = {
  PENDIENTE: { clase: 'border-border-strong bg-surface-muted text-foreground', Icono: Clock },
  APROBADA: { clase: 'border-success-border bg-success-surface text-success', Icono: CheckCircle2 },
  RECHAZADA: { clase: 'border-danger-border bg-danger-surface text-danger', Icono: XCircle },
}

/** Estado de una solicitud: color e icono, nunca solo color. */
export function EstadoBadge({ estado }: { estado: EstadoSolicitud }) {
  const { clase, Icono } = ESTILOS[estado]
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium whitespace-nowrap',
        clase,
      )}
    >
      <Icono className="size-3.5" aria-hidden="true" />
      {ETIQUETA_ESTADO[estado]}
    </span>
  )
}
