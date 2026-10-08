import { api } from '@/lib/axios'
import type { SolicitudPayload } from '@/schemas/solicitudSchema'
import type { Page, PageParams } from './types'

export type EstadoSolicitud = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA'

/** UsuarioResumenDTO */
export interface UsuarioResumen {
  id: number
  username: string
  nombre: string
}

/** DetalleSolicitudResponseDTO */
export interface DetalleSolicitud {
  id: number
  producto: {
    id: number
    sku: string
    nombre: string
    /** Precio actual del catálogo, en COP. */
    precio: number
    /** false si el producto se dio de baja después de solicitarlo. */
    activo: boolean
  }
  cantidad: number
  subtotalEstimado: number
}

/** SolicitudResponseDTO. Los campos de revisión solo llegan cuando ya no está PENDIENTE. */
export interface Solicitud {
  id: number
  fecha: string
  estado: EstadoSolicitud
  justificacion: string
  solicitante: UsuarioResumen
  detalles: DetalleSolicitud[]
  totalEstimado: number
  revisadoPor?: UsuarioResumen
  fechaRevision?: string
  comentarioRevision?: string
}

export interface CambioEstado {
  estado: Exclude<EstadoSolicitud, 'PENDIENTE'>
  /** Obligatorio al rechazar. */
  comentario?: string
}

const BASE = '/v1/solicitudes'

export const solicitudService = {
  /** USUARIO recibe solo las suyas; GERENTE, todas las de su empresa; SUPER_ADMIN, las de la empresa elegida (o todas en modo global). */
  async listar(params: PageParams & { estado?: EstadoSolicitud }, signal?: AbortSignal): Promise<Page<Solicitud>> {
    const { data } = await api.get<Page<Solicitud>>(BASE, { params, signal })
    return data
  },

  async obtener(id: number, signal?: AbortSignal): Promise<Solicitud> {
    const { data } = await api.get<Solicitud>(`${BASE}/${id}`, { signal })
    return data
  },

  /** El backend fija el solicitante (usuario autenticado) y el estado PENDIENTE. */
  async crear(payload: SolicitudPayload): Promise<Solicitud> {
    const { data } = await api.post<Solicitud>(BASE, payload)
    return data
  },

  /** GERENTE o SUPER_ADMIN dentro de una empresa. 409 si ya estaba revisada. */
  async cambiarEstado(id: number, cambio: CambioEstado): Promise<Solicitud> {
    const { data } = await api.patch<Solicitud>(`${BASE}/${id}/estado`, cambio)
    return data
  },
}
