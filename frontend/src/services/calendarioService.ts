import { api } from '@/lib/axios'

export type TipoEventoCalendario = 'ORDEN_EMITIDA' | 'ENTREGA' | 'RECEPCION' | 'SOLICITUD_PENDIENTE'

/**
 * EventoCalendarioDTO. `fecha` y las horas están en la zona del negocio (America/Bogota): se
 * muestran tal cual, sin convertir a la zona del navegador. Sin hora (`todoElDia`), el evento es
 * del día completo: la interfaz no inventa una.
 */
export interface EventoCalendario {
  id: string
  tipo: TipoEventoCalendario
  /** ENTREGA: PROGRAMADA | CONFIRMADA | RETRASADA | COMPLETA. RECEPCION: PARCIAL | COMPLETA | SOLO_INCIDENCIAS. */
  estado: string
  fecha: string
  todoElDia: boolean
  horaInicio?: string
  horaFin?: string
  instante?: string
  titulo: string
  detalle?: string
  ordenId?: number
  ordenNumero?: number
  proveedor?: string
  solicitudId?: number
  recepcionId?: number
  confirmada: boolean
  parcial: boolean
  incidencias: number
}

export interface Calendario {
  desde: string
  hasta: string
  hoy: string
  zonaHoraria: string
  eventos: EventoCalendario[]
  truncado: boolean
}

export const calendarioService = {
  async eventos(desde: string, hasta: string, signal?: AbortSignal): Promise<Calendario> {
    const { data } = await api.get<Calendario>('/v1/calendario', { params: { desde, hasta }, signal })
    return data
  },
}
