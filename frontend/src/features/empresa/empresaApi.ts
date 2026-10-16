import { api, CABECERA_TENANT } from '@/lib/axios'
import type { EmpresaPayload } from '@/schemas/empresaSchema'
import type { Page, PageParams } from '@/services/types'
import type { CodigoEmpresarial, Empresa, UsuarioEmpresa } from './types'

const BASE = '/v1/empresas'

/**
 * Gestión de empresas (solo SUPER_ADMIN; el backend responde 403 a cualquier otro rol).
 * Empresa y usuarios se piden por el id de la ruta; los datos de negocio de una empresa
 * (catálogos, solicitudes) se consultan con la cabecera X-Tenant-ID, como el resto de la app.
 */
export const empresaService = {
  async listar(params: PageParams, signal?: AbortSignal): Promise<Page<Empresa>> {
    const { data } = await api.get<Page<Empresa>>(BASE, { params, signal })
    return data
  },

  async obtener(id: number, signal?: AbortSignal): Promise<Empresa> {
    const { data } = await api.get<Empresa>(`${BASE}/${id}`, { signal })
    return data
  },

  /**
   * Empresa y administrador principal en una sola transacción. 409 con `errors.codigo`, `errors.nit`,
   * `errors['administrador.username']` o `errors['administrador.email']` si algo ya existe (no se crea nada).
   */
  async crear(payload: EmpresaPayload): Promise<Empresa> {
    const { data } = await api.post<Empresa>(BASE, payload)
    return data
  },

  /** Código propuesto para un nombre (NASCAR → NASC01, o el siguiente libre). */
  async sugerirCodigo(nombre: string, signal?: AbortSignal): Promise<CodigoEmpresarial> {
    const { data } = await api.get<CodigoEmpresarial>(`${BASE}/codigo-sugerido`, { params: { nombre }, signal })
    return data
  },

  /** 400 con `errors.codigo` si el formato no es válido. */
  async comprobarCodigo(codigo: string, signal?: AbortSignal): Promise<CodigoEmpresarial> {
    const { data } = await api.get<CodigoEmpresarial>(`${BASE}/codigo-disponible`, { params: { codigo }, signal })
    return data
  },

  async listarUsuarios(empresaId: number, params: PageParams, signal?: AbortSignal): Promise<Page<UsuarioEmpresa>> {
    const { data } = await api.get<Page<UsuarioEmpresa>>(`${BASE}/${empresaId}/usuarios`, { params, signal })
    return data
  },

  /** Desactivarla bloquea a todos sus usuarios desde su siguiente petición. 409 si ya estaba así. */
  async cambiarEstado(empresaId: number, activa: boolean): Promise<Empresa> {
    const { data } = await api.patch<Empresa>(`${BASE}/${empresaId}/estado`, { activa })
    return data
  },

  async cambiarEstadoAdministrador(empresaId: number, usuarioId: number, estado: 'ACTIVO' | 'INACTIVO'): Promise<UsuarioEmpresa> {
    const { data } = await api.patch<UsuarioEmpresa>(`${BASE}/${empresaId}/administradores/${usuarioId}/estado`, { estado })
    return data
  },

  /**
   * Total de registros de un listado paginado DENTRO de la empresa indicada (lee
   * page.totalElements con size=1). La cabecera X-Tenant-ID se fija aquí de forma explícita
   * (el interceptor respeta la que ya trae la petición), así que el dato es de esa empresa
   * aunque se esté supervisando otra.
   */
  async contarEn(empresaId: number, ruta: string, params: Record<string, string> = {}, signal?: AbortSignal): Promise<number> {
    const { data } = await api.get<Page<unknown>>(ruta, {
      params: { ...params, page: 0, size: 1 },
      headers: { [CABECERA_TENANT]: String(empresaId) },
      signal,
    })
    return data.page.totalElements
  },
}
