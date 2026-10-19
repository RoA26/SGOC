import { api } from '@/lib/axios'
import type { EstadoOrdenCompra, ProductoLinea, ProgramacionEntrega, Recepcion } from './ordenCompraService'
import type { Page, PageParams } from './types'

/** Situación logística derivada en el backend (no se guarda). */
export type SituacionEntrega = 'BORRADOR' | 'SIN_PROGRAMAR' | 'PROGRAMADA' | 'RETRASADA' | 'COMPLETA' | 'CANCELADA'

export type AlertaEntrega = 'RETRASADA' | 'SIN_PROGRAMAR' | 'INCIDENCIAS'

/** Vistas del listado. El trabajador solo consulta las de lo pendiente. */
export type VistaEntregas =
  | 'PENDIENTES'
  | 'SIN_PROGRAMAR'
  | 'PROGRAMADAS'
  | 'RETRASADAS'
  | 'PARCIALES'
  | 'COMPLETAS'
  | 'CON_INCIDENCIAS'
  | 'INTERVENCION'
  | 'TODAS'

/** EntregaDTO: sin costos (la consulta también el trabajador). */
export interface Entrega {
  ordenId: number
  numero: number
  estadoOrden: EstadoOrdenCompra
  situacion: SituacionEntrega
  proveedor: { id: number; razonSocial: string }
  emitidaEn?: string
  programacion?: ProgramacionEntrega
  diasRetraso: number
  unidadesPedidas: number
  unidadesRecibidas: number
  unidadesPendientes: number
  recepciones: number
  ultimaRecepcionEn?: string
  incidencias: number
  alertas: AlertaEntrega[]
}

export interface LineaEntrega {
  id: number
  producto: ProductoLinea
  cantidad: number
  cantidadRecibida: number
  pendiente: number
}

/** EntregaDetalleDTO */
export interface EntregaDetalle {
  entrega: Entrega
  observacionesOrden?: string
  lineas: LineaEntrega[]
  recepciones: Recepcion[]
  programaciones: ProgramacionEntrega[]
}

export interface ResumenEntregas {
  pendientes: number
  paraHoy: number
  retrasadas: number
  sinProgramar: number
  parciales: number
  conIncidencias: number
  requierenIntervencion: number
}

export interface FiltroEntregas {
  vista?: VistaEntregas
  /** Fecha estimada desde/hasta (AAAA-MM-DD). */
  desde?: string
  hasta?: string
  proveedorId?: number
  numero?: number
}

const BASE = '/v1/entregas'

export const entregaService = {
  async listar(params: PageParams & FiltroEntregas, signal?: AbortSignal): Promise<Page<Entrega>> {
    // El orden lo decide el backend (fecha estimada más próxima primero).
    const { sort: _sort, ...resto } = params
    const { data } = await api.get<Page<Entrega>>(BASE, { params: resto, signal })
    return data
  },

  async resumen(signal?: AbortSignal): Promise<ResumenEntregas> {
    const { data } = await api.get<ResumenEntregas>(`${BASE}/resumen`, { signal })
    return data
  },

  async detalle(ordenId: number, signal?: AbortSignal): Promise<EntregaDetalle> {
    const { data } = await api.get<EntregaDetalle>(`${BASE}/${ordenId}`, { signal })
    return data
  },
}
