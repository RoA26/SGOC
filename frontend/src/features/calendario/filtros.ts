import type { EventoCalendario, TipoEventoCalendario } from '@/services/calendarioService'

export const TIPOS_EVENTO: readonly TipoEventoCalendario[] = ['ENTREGA', 'RECEPCION', 'ORDEN_EMITIDA', 'SOLICITUD_PENDIENTE']

export const ETIQUETA_TIPO: Record<TipoEventoCalendario, string> = {
  ENTREGA: 'Entregas programadas',
  RECEPCION: 'Recepciones',
  ORDEN_EMITIDA: 'Órdenes emitidas',
  SOLICITUD_PENDIENTE: 'Solicitudes por aprobar',
}

/** Estados que se pueden filtrar (cada uno agrupa los de varios tipos). */
export type FiltroEstado = 'TODOS' | 'RETRASADAS' | 'POR_LLEGAR' | 'PARCIALES' | 'COMPLETAS' | 'CON_INCIDENCIAS'

export const ETIQUETA_FILTRO_ESTADO: Record<FiltroEstado, string> = {
  TODOS: 'Todos los estados',
  RETRASADAS: 'Retrasadas',
  POR_LLEGAR: 'Por llegar (programadas o confirmadas)',
  PARCIALES: 'Recibidas en parte',
  COMPLETAS: 'Completas',
  CON_INCIDENCIAS: 'Con incidencias',
}

export const ETIQUETA_ESTADO_EVENTO: Record<string, string> = {
  PROGRAMADA: 'Programada (estimada)',
  CONFIRMADA: 'Confirmada por el proveedor',
  RETRASADA: 'Retrasada',
  COMPLETA: 'Completa',
  PARCIAL: 'Parcial',
  SOLO_INCIDENCIAS: 'Solo incidencias',
  PENDIENTE: 'Pendiente de aprobación',
  EMITIDA: 'Emitida',
  RECIBIDA_PARCIALMENTE: 'Recibida en parte',
  RECIBIDA: 'Recibida',
  CANCELADA: 'Cancelada',
}

/** Para las tarjetas estrechas de la semana; el detalle muestra la etiqueta completa. */
export const ETIQUETA_ESTADO_CORTA: Record<string, string> = {
  PROGRAMADA: 'Estimada',
  CONFIRMADA: 'Confirmada',
  RETRASADA: 'Retrasada',
  COMPLETA: 'Completa',
  PARCIAL: 'Parcial',
  SOLO_INCIDENCIAS: 'Solo incidencias',
  PENDIENTE: 'Por aprobar',
  EMITIDA: 'Emitida',
  RECIBIDA_PARCIALMENTE: 'Recibida en parte',
  RECIBIDA: 'Recibida',
  CANCELADA: 'Cancelada',
}

export function cumpleEstado(evento: EventoCalendario, filtro: FiltroEstado): boolean {
  switch (filtro) {
    case 'TODOS':
      return true
    case 'RETRASADAS':
      return evento.tipo === 'ENTREGA' && evento.estado === 'RETRASADA'
    case 'POR_LLEGAR':
      return evento.tipo === 'ENTREGA' && (evento.estado === 'PROGRAMADA' || evento.estado === 'CONFIRMADA')
    case 'PARCIALES':
      return (evento.tipo === 'RECEPCION' && evento.estado === 'PARCIAL') || (evento.tipo === 'ENTREGA' && evento.parcial)
    case 'COMPLETAS':
      return (evento.tipo === 'ENTREGA' || evento.tipo === 'RECEPCION') && evento.estado === 'COMPLETA'
    case 'CON_INCIDENCIAS':
      return evento.tipo === 'RECEPCION' && (evento.incidencias > 0 || evento.estado === 'SOLO_INCIDENCIAS')
  }
}

export function filtrarEventos(
  eventos: readonly EventoCalendario[],
  tipos: ReadonlySet<TipoEventoCalendario>,
  estado: FiltroEstado,
): EventoCalendario[] {
  return eventos.filter((evento) => tipos.has(evento.tipo) && cumpleEstado(evento, estado))
}

/** Eventos por día (AAAA-MM-DD), en el orden del backend (todo el día primero, luego por hora). */
export function agruparPorDia(eventos: readonly EventoCalendario[]): Map<string, EventoCalendario[]> {
  const porDia = new Map<string, EventoCalendario[]>()
  for (const evento of eventos) {
    const lista = porDia.get(evento.fecha)
    if (lista) lista.push(evento)
    else porDia.set(evento.fecha, [evento])
  }
  return porDia
}

/** "08:00–10:30", "14:05" o null (todo el día: sin hora inventada). */
export function horaDeEvento(evento: Pick<EventoCalendario, 'todoElDia' | 'horaInicio' | 'horaFin'>): string | null {
  if (evento.todoElDia || !evento.horaInicio) return null
  return evento.horaFin ? `${evento.horaInicio}–${evento.horaFin}` : evento.horaInicio
}
