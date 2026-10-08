import { api, CABECERA_TENANT } from '@/lib/axios'

/**
 * Comprueba que el SUPER_ADMIN puede trabajar en la empresa indicada antes de elegirla.
 *
 * El backend aún no expone un listado de empresas: se usa GET /api/auth/me con la cabecera
 * X-Tenant-ID, que TenantFilter valida en cualquier petición autenticada (400 si no es un id
 * numérico o la empresa no existe). La respuesta en sí no se usa.
 */
export async function verificarEmpresa(empresaId: number): Promise<void> {
  await api.get('/auth/me', { headers: { [CABECERA_TENANT]: String(empresaId) } })
}
