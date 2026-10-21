import type { EstadoUsuario } from '@/features/auth/types'
import { api } from '@/lib/axios'
import type { Page, PageParams } from '@/services/types'
import type { PersonalTrabajador } from './types'

const BASE = '/v1/trabajadores'

/**
 * Personal de la empresa del ADMINISTRADOR_COMERCIAL. El backend fija la empresa (la de la cuenta) y
 * responde 403 a cualquier otro rol: el filtro de la interfaz no protege nada.
 */
export const personalService = {
  async listar(params: PageParams & { estado?: EstadoUsuario }, signal?: AbortSignal): Promise<Page<PersonalTrabajador>> {
    const { data } = await api.get<Page<PersonalTrabajador>>(`${BASE}/actividad`, { params, signal })
    return data
  },

  /** Aprobar, rechazar, desactivar o reactivar. 409 si la transición ya no es posible (otro la cambió). */
  async cambiarEstado(id: number, estado: EstadoUsuario): Promise<void> {
    await api.patch(`${BASE}/${id}/estado`, { estado })
  },
}
