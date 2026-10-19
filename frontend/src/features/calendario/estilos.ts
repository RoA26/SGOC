import { AlertTriangle, ClipboardList, PackageCheck, Send, Truck, type LucideIcon } from 'lucide-react'
import type { EventoCalendario, TipoEventoCalendario } from '@/services/calendarioService'

/**
 * Cada tipo se distingue por icono y texto además del color (borde y fondo suaves con los tokens
 * de estado, que cumplen contraste en claro y oscuro). Retrasada añade su propio icono.
 */
export const ESTILO_TIPO: Record<TipoEventoCalendario, { Icono: LucideIcon; clase: string; marca: string }> = {
  ENTREGA: { Icono: Truck, clase: 'border-l-primary bg-surface', marca: 'bg-primary' },
  RECEPCION: { Icono: PackageCheck, clase: 'border-l-success bg-success-surface', marca: 'bg-success' },
  ORDEN_EMITIDA: { Icono: Send, clase: 'border-l-border-strong bg-surface-muted', marca: 'bg-border-strong' },
  SOLICITUD_PENDIENTE: { Icono: ClipboardList, clase: 'border-l-warning bg-warning-surface', marca: 'bg-warning' },
}

export function estiloEvento(evento: EventoCalendario): { Icono: LucideIcon; clase: string; marca: string } {
  if (evento.tipo === 'ENTREGA' && evento.estado === 'RETRASADA') {
    return { Icono: AlertTriangle, clase: 'border-l-danger bg-danger-surface', marca: 'bg-danger' }
  }
  return ESTILO_TIPO[evento.tipo]
}
