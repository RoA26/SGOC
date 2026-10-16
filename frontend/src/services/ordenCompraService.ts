import { api } from '@/lib/axios'
import type { UsuarioResumen } from './solicitudService'
import type { Page, PageParams } from './types'

export type EstadoOrdenCompra = 'BORRADOR' | 'EMITIDA' | 'RECIBIDA_PARCIALMENTE' | 'RECIBIDA' | 'CANCELADA'

/** Producto tal como lo referencia un documento: activo = false si se dio de baja después. */
export interface ProductoLinea {
  id: number
  sku: string
  nombre: string
  codigoBarras?: string
  activo: boolean
}

export interface LineaOrden {
  id: number
  producto: ProductoLinea
  cantidad: number
  costoUnitario: number
  subtotal: number
  cantidadRecibida: number
  pendiente: number
}

/** OrdenCompraResponseDTO. Emisión y cancelación solo llegan cuando ocurrieron. */
export interface OrdenCompra {
  id: number
  numero: number
  estado: EstadoOrdenCompra
  proveedor: { id: number; nit: string; razonSocial: string; activo: boolean }
  solicitudId?: number
  observaciones?: string
  detalles: LineaOrden[]
  total: number
  unidadesPedidas: number
  unidadesRecibidas: number
  creadoPor: UsuarioResumen
  creadoEn: string
  actualizadoEn: string
  emitidaPor?: UsuarioResumen
  emitidaEn?: string
  canceladaPor?: UsuarioResumen
  canceladaEn?: string
  motivoCancelacion?: string
}

export interface LineaOrdenPayload {
  productoId: number
  cantidad: number
  /** Sin valor, el backend usa el precio actual del producto. */
  costoUnitario?: number
}

export interface OrdenCompraPayload {
  proveedorId: number
  observaciones: string | null
  detalles: LineaOrdenPayload[]
}

/** RecepcionResponseDTO */
export interface Recepcion {
  id: number
  ordenId: number
  ordenNumero: number
  proveedor: string
  recibidaEn: string
  recibidaPor: UsuarioResumen
  observaciones?: string
  detalles: { id: number; detalleOrdenId: number; producto: ProductoLinea; cantidad: number; costoUnitario: number }[]
  unidades: number
  valor: number
}

export interface RecepcionPayload {
  /** Una por intento de registro: reintentar con la misma no duplica la mercancía. */
  claveIdempotencia: string
  observaciones: string | null
  detalles: { detalleOrdenId: number; cantidad: number }[]
}

export interface FiltroOrdenes {
  estado?: EstadoOrdenCompra
  proveedorId?: number
  solicitudId?: number
}

/** Número visible de una orden, igual que en los mensajes del backend: OC-00042. */
export function numeroOrden(numero: number): string {
  return `OC-${String(numero).padStart(5, '0')}`
}

const BASE = '/v1/ordenes-compra'

export const ordenCompraService = {
  async listar(params: PageParams & FiltroOrdenes, signal?: AbortSignal): Promise<Page<OrdenCompra>> {
    const { data } = await api.get<Page<OrdenCompra>>(BASE, { params, signal })
    return data
  },

  async obtener(id: number, signal?: AbortSignal): Promise<OrdenCompra> {
    const { data } = await api.get<OrdenCompra>(`${BASE}/${id}`, { signal })
    return data
  },

  async crear(payload: OrdenCompraPayload): Promise<OrdenCompra> {
    const { data } = await api.post<OrdenCompra>(BASE, payload)
    return data
  },

  /** Solo en BORRADOR (409 en otro estado). */
  async actualizar(id: number, payload: Omit<OrdenCompraPayload, 'proveedorId'>): Promise<OrdenCompra> {
    const { data } = await api.put<OrdenCompra>(`${BASE}/${id}`, payload)
    return data
  },

  async emitir(id: number): Promise<OrdenCompra> {
    const { data } = await api.post<OrdenCompra>(`${BASE}/${id}/emitir`)
    return data
  },

  async cancelar(id: number, motivo: string): Promise<OrdenCompra> {
    const { data } = await api.post<OrdenCompra>(`${BASE}/${id}/cancelar`, { motivo })
    return data
  },

  /** Un borrador por proveedor de los productos de una solicitud aprobada. */
  async crearDesdeSolicitud(solicitudId: number): Promise<OrdenCompra[]> {
    const { data } = await api.post<OrdenCompra[]>(`${BASE}/desde-solicitud/${solicitudId}`)
    return data
  },

  async recepciones(id: number, signal?: AbortSignal): Promise<Recepcion[]> {
    const { data } = await api.get<Recepcion[]>(`${BASE}/${id}/recepciones`, { signal })
    return data
  },

  async registrarRecepcion(id: number, payload: RecepcionPayload): Promise<Recepcion> {
    const { data } = await api.post<Recepcion>(`${BASE}/${id}/recepciones`, payload)
    return data
  },

  async listarRecepciones(params: PageParams, signal?: AbortSignal): Promise<Page<Recepcion>> {
    const { data } = await api.get<Page<Recepcion>>('/v1/recepciones', { params, signal })
    return data
  },
}
