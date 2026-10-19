import type { AlertaEntrega, SituacionEntrega, VistaEntregas } from '@/services/entregaService'
import type { ProgramacionEntrega, TipoIncidencia } from '@/services/ordenCompraService'

export const ETIQUETA_SITUACION: Record<SituacionEntrega, string> = {
  BORRADOR: 'Borrador',
  SIN_PROGRAMAR: 'Sin programar',
  PROGRAMADA: 'Programada',
  RETRASADA: 'Retrasada',
  COMPLETA: 'Completa',
  CANCELADA: 'Cancelada',
}

export const ETIQUETA_VISTA: Record<VistaEntregas, string> = {
  PENDIENTES: 'Pendientes',
  RETRASADAS: 'Retrasadas',
  PROGRAMADAS: 'Programadas',
  SIN_PROGRAMAR: 'Sin programar',
  PARCIALES: 'Recibidas en parte',
  COMPLETAS: 'Completas',
  CON_INCIDENCIAS: 'Con incidencias',
  INTERVENCION: 'Requieren intervención',
  TODAS: 'Todas',
}

/** Vistas del trabajador (lo pendiente de recibir). */
export const VISTAS_OPERATIVAS: readonly VistaEntregas[] = ['PENDIENTES', 'RETRASADAS', 'PROGRAMADAS', 'SIN_PROGRAMAR', 'PARCIALES']
/** Vistas del gerente. */
export const VISTAS_SUPERVISION: readonly VistaEntregas[] = [
  'INTERVENCION',
  'PENDIENTES',
  'RETRASADAS',
  'PROGRAMADAS',
  'SIN_PROGRAMAR',
  'PARCIALES',
  'CON_INCIDENCIAS',
  'COMPLETAS',
  'TODAS',
]

export const ETIQUETA_ALERTA: Record<AlertaEntrega, string> = {
  RETRASADA: 'Retrasada',
  SIN_PROGRAMAR: 'Sin fecha',
  INCIDENCIAS: 'Con incidencias',
}

export const ETIQUETA_INCIDENCIA: Record<TipoIncidencia, string> = {
  FALTANTE: 'Faltante',
  DANADO: 'Dañado',
  EXCEDENTE: 'Excedente (no aceptado)',
  PRODUCTO_INCORRECTO: 'Producto incorrecto',
  OTRO: 'Otra novedad',
}

export function esVista(valor: string | null, permitidas: readonly VistaEntregas[]): valor is VistaEntregas {
  return valor !== null && (permitidas as readonly string[]).includes(valor)
}

/** "08:00–12:00" o "Todo el día" (sin franja acordada no se inventa una hora). */
export function franja(programacion: Pick<ProgramacionEntrega, 'horaDesde' | 'horaHasta'>): string {
  return programacion.horaDesde && programacion.horaHasta
    ? `${programacion.horaDesde}–${programacion.horaHasta}`
    : 'Todo el día'
}
