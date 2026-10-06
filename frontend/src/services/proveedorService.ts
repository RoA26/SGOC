import { api } from '@/lib/axios'
import type { ProveedorPayload } from '@/schemas/proveedorSchema'
import type { Page, PageParams } from './types'

/** ProveedorResponseDTO */
export interface Proveedor {
  id: number
  nit: string
  razonSocial: string
  email: string
  telefono?: string | null
  direccion?: string | null
  creadoEn: string
  actualizadoEn: string
}

const BASE = '/v1/proveedores'

export const proveedorService = {
  async listar(params: PageParams, signal?: AbortSignal): Promise<Page<Proveedor>> {
    const { data } = await api.get<Page<Proveedor>>(BASE, { params, signal })
    return data
  },

  async obtener(id: number, signal?: AbortSignal): Promise<Proveedor> {
    const { data } = await api.get<Proveedor>(`${BASE}/${id}`, { signal })
    return data
  },

  async crear(payload: ProveedorPayload): Promise<Proveedor> {
    const { data } = await api.post<Proveedor>(BASE, payload)
    return data
  },

  async actualizar(id: number, payload: ProveedorPayload): Promise<Proveedor> {
    const { data } = await api.put<Proveedor>(`${BASE}/${id}`, payload)
    return data
  },

  /** Borrado lógico. 409 si el proveedor tiene productos activos. */
  async eliminar(id: number): Promise<void> {
    await api.delete(`${BASE}/${id}`)
  },
}
