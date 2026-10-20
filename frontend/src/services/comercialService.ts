import { api } from '@/lib/axios'
import type { EstadoStock } from './inventarioService'
import type { UsuarioResumen } from './solicitudService'
import type { Page, PageParams } from './types'

/** Tratamiento del IVA: la tarifa cero no convierte un producto en exento ni excluido. */
export type TratamientoIva = 'GRAVADO' | 'EXENTO' | 'EXCLUIDO'
export type VentaBajoCosto = 'PERMITIR' | 'AUTORIZACION'
export type FiltroComercial = 'TODOS' | 'A_LA_VENTA' | 'SIN_PRECIO' | 'SIN_COSTO' | 'BAJO_COSTO'

/** ComercialDTO.ProductoComercial: los indicadores ausentes no están definidos (desconocido ≠ 0). */
export interface ProductoComercial {
  id: number
  sku: string
  codigoBarras?: string
  nombre: string
  activo: boolean
  precioCompraReferencia: number
  precioVenta?: number
  tratamientoIva?: TratamientoIva
  tarifaIva?: number
  precioConIva?: number
  costoPromedio?: number
  utilidadUnitaria?: number
  margen?: number
  recargo?: number
  bajoCosto: boolean
  precioSugerido?: number
  existencias: number
  estadoStock: EstadoStock
}

export interface CondicionesVenta {
  precioVenta: number
  tratamientoIva: TratamientoIva
  tarifaIva: number
  motivo?: string
}

export interface CambioPrecio {
  id: number
  productoId: number
  sku: string
  nombre: string
  precioAnterior?: number
  precioNuevo: number
  tratamientoAnterior?: TratamientoIva
  tratamientoNuevo: TratamientoIva
  tarifaAnterior?: number
  tarifaNueva: number
  costoReferencia?: number
  motivo?: string
  usuario: UsuarioResumen
  fecha: string
}

export interface Politica {
  descuentoMaxTrabajador: number
  descuentoMaxAdministrador: number
  ventaBajoCosto: VentaBajoCosto
  margenObjetivo?: number
  porDefecto: boolean
  actualizadaPor?: UsuarioResumen
  actualizadaEn?: string
}

export interface PoliticaPayload {
  descuentoMaxTrabajador: number
  descuentoMaxAdministrador: number
  ventaBajoCosto: VentaBajoCosto
  margenObjetivo?: number | null
}

export interface Autorizacion {
  id: number
  /** Completo solo al crearla; en el listado, solo sus últimos caracteres. */
  codigo: string
  descuentoMaximo: number
  permiteBajoCosto: boolean
  motivo: string
  estado: 'VIGENTE' | 'USADA' | 'VENCIDA'
  creadaPor: UsuarioResumen
  creadaEn: string
  expiraEn: string
  ventaId?: number
  usadaPor?: UsuarioResumen
  usadaEn?: string
}

export interface AutorizacionPayload {
  descuentoMaximo: number
  permiteBajoCosto: boolean
  motivo: string
  minutosVigencia?: number
}

const BASE = '/v1/comercial'

export const comercialService = {
  async productos(
    params: PageParams & { q?: string; filtro?: FiltroComercial },
    signal?: AbortSignal,
  ): Promise<Page<ProductoComercial>> {
    const { data } = await api.get<Page<ProductoComercial>>(`${BASE}/productos`, { params, signal })
    return data
  },

  async cambiarCondiciones(id: number, payload: CondicionesVenta): Promise<ProductoComercial> {
    const { data } = await api.put<ProductoComercial>(`${BASE}/productos/${id}/condiciones`, payload)
    return data
  },

  async historial(params: PageParams & { productoId?: number }, signal?: AbortSignal): Promise<Page<CambioPrecio>> {
    const { data } = await api.get<Page<CambioPrecio>>(`${BASE}/historial-precios`, { params, signal })
    return data
  },

  async politica(signal?: AbortSignal): Promise<Politica> {
    const { data } = await api.get<Politica>(`${BASE}/politica`, { signal })
    return data
  },

  async guardarPolitica(payload: PoliticaPayload): Promise<Politica> {
    const { data } = await api.put<Politica>(`${BASE}/politica`, payload)
    return data
  },

  async crearAutorizacion(payload: AutorizacionPayload): Promise<Autorizacion> {
    const { data } = await api.post<Autorizacion>(`${BASE}/autorizaciones`, payload)
    return data
  },

  async autorizaciones(params: PageParams, signal?: AbortSignal): Promise<Page<Autorizacion>> {
    const { data } = await api.get<Page<Autorizacion>>(`${BASE}/autorizaciones`, { params, signal })
    return data
  },
}
