import { CheckCircle2, CircleSlash, Clock, XCircle } from 'lucide-react'
import type { EstadoUsuario } from '@/features/auth/types'
import { cn } from '@/lib/cn'

const CLASE_BASE = 'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium whitespace-nowrap'

const ESTADO_USUARIO: Record<EstadoUsuario, { etiqueta: string; clase: string; Icono: typeof Clock }> = {
  ACTIVO: { etiqueta: 'Activo', clase: 'border-success-border bg-success-surface text-success', Icono: CheckCircle2 },
  PENDIENTE: { etiqueta: 'Pendiente', clase: 'border-border-strong bg-surface-muted text-foreground', Icono: Clock },
  RECHAZADO: { etiqueta: 'Rechazado', clase: 'border-danger-border bg-danger-surface text-danger', Icono: XCircle },
  INACTIVO: { etiqueta: 'Inactivo', clase: 'border-border bg-surface-muted text-foreground-muted', Icono: CircleSlash },
}

/** Estado de una cuenta: color e icono, nunca solo color. */
export function EstadoUsuarioBadge({ estado }: { estado: EstadoUsuario }) {
  const { etiqueta, clase, Icono } = ESTADO_USUARIO[estado]
  return (
    <span className={cn(CLASE_BASE, clase)}>
      <Icono className="size-3.5" aria-hidden="true" />
      {etiqueta}
    </span>
  )
}

/** Empresa activa o desactivada (desactivada: ninguno de sus usuarios puede operar). */
export function EstadoEmpresaBadge({ activa }: { activa: boolean }) {
  return activa ? (
    <span className={cn(CLASE_BASE, 'border-success-border bg-success-surface text-success')}>
      <CheckCircle2 className="size-3.5" aria-hidden="true" />
      Activa
    </span>
  ) : (
    <span className={cn(CLASE_BASE, 'border-danger-border bg-danger-surface text-danger')}>
      <CircleSlash className="size-3.5" aria-hidden="true" />
      Desactivada
    </span>
  )
}
