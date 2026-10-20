import { api } from '@/lib/axios'
import type { EstadoOrdenCompra } from './ordenCompraService'
import type { EstadoSolicitud } from './solicitudService'
import type { EstadoStock } from './inventarioService'
import type { Page, PageParams } from './types'

export type Granularidad = 'DIA' | 'MES'

/** Período en fechas locales del negocio (America/Bogota), ambas incluidas. */
export interface Periodo {
  desde: string
  hasta: string
  zonaHoraria: string
  granularidad: Granularidad
}

/** Un punto de una serie: el día (o el primer día del mes) y su valor. */
export interface Punto {
  fecha: string
  valor: number
}

export interface FiltroPeriodo {
  desde?: string
  hasta?: string
}

/** AnaliticaDTO.Global (SUPER_ADMIN en modo global). */
export interface AnaliticaGlobal {
  periodo: Periodo
  empresas: { total: number; activas: number; inactivas: number }
  ordenes: { emitidasEnPeriodo: number; totalHistorico: number; porEstado: Record<EstadoOrdenCompra, number> }
  topEmpresas: { id: number; nombre: string; codigo: string; ordenes: number; valorComprometido: number }[]
  evolucionOrdenes: Punto[]
}

export interface OrdenesDeEmpresa {
  id: number
  nombre: string
  codigo: string
  activa: boolean
  ordenesEnPeriodo: number
  ordenesAbiertas: number
}

export interface ProductoEnAlerta {
  id: number
  sku: string
  nombre: string
  cantidad: number
  stockMinimo: number
  estado: EstadoStock
}

/** AnaliticaDTO.DeEmpresa (ADMINISTRADOR_COMERCIAL, o SUPER_ADMIN supervisando). */
export interface AnaliticaEmpresa {
  periodo: Periodo
  inventario: {
    productosActivos: number
    disponibles: number
    agotados: number
    criticos: number
    requierenAtencion: ProductoEnAlerta[]
  }
  solicitudesPorEstado: Record<EstadoSolicitud, number>
  compras: {
    ordenesEmitidas: number
    valorComprometido: number
    valorRecibido: number
    totalHistorico: number
    ordenesPorEstado: Record<EstadoOrdenCompra, number>
  }
  recepciones: { enPeriodo: number; unidades: number; totalHistorico: number; evolucion: Punto[] }
}

/** AnaliticaDTO.Personal (TRABAJADOR). Sin ventas: no existen hasta que llegue el POS. */
export interface AnaliticaPersonal {
  productosDisponibles: number
  productosCriticos: number
  productosAgotados: number
  misSolicitudes: Record<EstadoSolicitud, number>
}

const BASE = '/v1/analitica'

/** Cifras calculadas por el backend en cada petición; el frontend nunca las inventa ni las completa. */
export const analiticaService = {
  async global(periodo: FiltroPeriodo, signal?: AbortSignal): Promise<AnaliticaGlobal> {
    const { data } = await api.get<AnaliticaGlobal>(`${BASE}/global`, { params: periodo, signal })
    return data
  },

  async ordenesPorEmpresa(
    params: PageParams & FiltroPeriodo,
    signal?: AbortSignal,
  ): Promise<Page<OrdenesDeEmpresa>> {
    const { data } = await api.get<Page<OrdenesDeEmpresa>>(`${BASE}/global/empresas`, { params, signal })
    return data
  },

  async empresa(periodo: FiltroPeriodo, signal?: AbortSignal): Promise<AnaliticaEmpresa> {
    const { data } = await api.get<AnaliticaEmpresa>(`${BASE}/empresa`, { params: periodo, signal })
    return data
  },

  async personal(signal?: AbortSignal): Promise<AnaliticaPersonal> {
    const { data } = await api.get<AnaliticaPersonal>(`${BASE}/personal`, { signal })
    return data
  },
}
