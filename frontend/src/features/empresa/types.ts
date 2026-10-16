import type { EstadoUsuario, Rol, UsuarioResponse } from '@/features/auth/types'

/** EmpresaResponseDTO */
export interface Empresa {
  id: number
  nombre: string
  /** Código empresarial: identificador público y estable (p. ej. NASC01). */
  codigo: string
  nit: string
  activa: boolean
  /**
   * Código de registro de trabajadores (secreto). La interfaz del SUPER_ADMIN no lo muestra: lo
   * comparte el gerente con su equipo.
   */
  codigoEmpresa: string
  creadoEn: string
  /** Administrador principal (el gerente fundador); ausente si la empresa no tiene gerente. */
  gerente?: UsuarioResponse
}

/** CodigoEmpresarialDTO: código propuesto o consultado y si está libre ahora mismo. */
export interface CodigoEmpresarial {
  codigo: string
  disponible: boolean
}

/** TrabajadorResponseDTO: persona de la empresa, sin datos de acceso. */
export interface UsuarioEmpresa {
  id: number
  username: string
  email: string
  nombre: string
  rol: Rol
  estado: EstadoUsuario
  creadoEn: string
}

/** Empresa que supervisa el SUPER_ADMIN (lo necesario para mostrarla sin pedirla de nuevo). */
export interface EmpresaSupervisada {
  id: number
  nombre: string
  codigo: string
}
