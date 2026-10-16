import { api } from '@/lib/axios'
import type { ProductoLinea } from './ordenCompraService'
import type { Producto } from './productoService'
import type { UsuarioResumen } from './solicitudService'
import type { Page, PageParams } from './types'

/** Situación frente al stock mínimo (la misma definición que usa la analítica). */
export type EstadoStock = 'AGOTADO' | 'CRITICO' | 'NORMAL'

/** ExistenciaDTO: sin costos, visible para toda la empresa. */
export interface Existencia {
  productoId: number
  sku: string
  nombre: string
  codigoBarras?: string
  cantidad: number
  stockMinimo: number
  estado: EstadoStock
  actualizadoEn: string
}

export type TipoMovimiento = 'RECEPCION' | 'AJUSTE'

/** MovimientoInventarioDTO (kárdex, con costos: solo gestión). */
export interface Movimiento {
  id: number
  producto: ProductoLinea
  tipo: TipoMovimiento
  cantidad: number
  saldoResultante: number
  costoUnitario?: number
  motivo?: string
  recepcionId?: number
  ordenId?: number
  ordenNumero?: number
  usuario: UsuarioResumen
  fecha: string
}

export interface AjustePayload {
  productoId: number
  /** Con signo: positiva entra, negativa sale. */
  cantidad: number
  motivo: string
  costoUnitario?: number
  claveIdempotencia: string
}

/** Resultado de la búsqueda por escáner (código de barras o SKU). */
export interface ProductoEncontrado {
  producto: Producto
  existencias: number
  estadoStock: EstadoStock
  coincidePor: 'CODIGO_BARRAS' | 'SKU'
}

const BASE = '/v1/inventario'

export const inventarioService = {
  async listar(params: PageParams & { estado?: EstadoStock; q?: string }, signal?: AbortSignal): Promise<Page<Existencia>> {
    const { data } = await api.get<Page<Existencia>>(BASE, { params, signal })
    return data
  },

  async movimientos(params: PageParams & { productoId?: number }, signal?: AbortSignal): Promise<Page<Movimiento>> {
    const { data } = await api.get<Page<Movimiento>>(`${BASE}/movimientos`, { params, signal })
    return data
  },

  async ajustar(payload: AjustePayload): Promise<Movimiento> {
    const { data } = await api.post<Movimiento>(`${BASE}/ajustes`, payload)
    return data
  },

  /** Siempre en la empresa del contexto: el backend no admite elegir otra. */
  async buscarPorCodigo(codigo: string, signal?: AbortSignal): Promise<ProductoEncontrado> {
    const { data } = await api.get<ProductoEncontrado>('/v1/productos/buscar', { params: { codigo }, signal })
    return data
  },
}
