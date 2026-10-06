import { api } from '@/lib/axios'
import type { ProductoPayload } from '@/schemas/productoSchema'
import type { Page, PageParams } from './types'

/** ProveedorResumenDTO */
export interface ProveedorResumen {
  id: number
  nit: string
  razonSocial: string
}

/** ProductoResponseDTO */
export interface Producto {
  id: number
  sku: string
  nombre: string
  descripcion?: string | null
  precio: number
  proveedor: ProveedorResumen
  creadoEn: string
  actualizadoEn: string
}

const BASE = '/v1/productos'

export const productoService = {
  async listar(params: PageParams, signal?: AbortSignal): Promise<Page<Producto>> {
    const { data } = await api.get<Page<Producto>>(BASE, { params, signal })
    return data
  },

  async obtener(id: number, signal?: AbortSignal): Promise<Producto> {
    const { data } = await api.get<Producto>(`${BASE}/${id}`, { signal })
    return data
  },

  async crear(payload: ProductoPayload): Promise<Producto> {
    const { data } = await api.post<Producto>(BASE, payload)
    return data
  },

  async actualizar(id: number, payload: ProductoPayload): Promise<Producto> {
    const { data } = await api.put<Producto>(`${BASE}/${id}`, payload)
    return data
  },

  /** Borrado lógico. */
  async eliminar(id: number): Promise<void> {
    await api.delete(`${BASE}/${id}`)
  },
}
