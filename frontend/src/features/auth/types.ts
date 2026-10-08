/** Contratos de la API de autenticación (reflejan los DTO de Spring Boot). */

/**
 * Roles del backend (enum Rol):
 * - SUPER_ADMIN: operador de la plataforma, sin empresa. Elige empresa con X-Tenant-ID.
 * - GERENTE: administra su empresa (catálogos, revisión de solicitudes, invitaciones).
 * - USUARIO: crea solicitudes en su empresa y consulta las suyas.
 */
export type Rol = 'SUPER_ADMIN' | 'GERENTE' | 'USUARIO'

export interface LoginRequest {
  username: string
  password: string
}

/** RegistroRequestDTO: alta con un código de invitación de un solo uso. */
export interface RegistroRequest {
  username: string
  email: string
  password: string
  codigoInvitacion: string
}

export interface UsuarioResponse {
  id: number
  username: string
  email: string
  nombre: string
  rol: Rol
  /** Empresa del usuario; el backend la omite para SUPER_ADMIN. */
  empresaId?: number
  /** PENDIENTE tras registrarse con el código de empresa, hasta que un gerente lo apruebe. */
  estado?: EstadoUsuario
}

export type EstadoUsuario = 'PENDIENTE' | 'ACTIVO' | 'RECHAZADO' | 'INACTIVO'

export interface LoginResponse {
  accessToken: string
  tokenType: 'Bearer'
  /** Segundos de validez del token. */
  expiresIn: number
  usuario: UsuarioResponse
}

/** InvitacionResponseDTO: el código solo se devuelve una vez, al generarlo. */
export interface InvitacionResponse {
  /** Formato XXXX-XXXX-XXXX-XXXX. */
  codigo: string
  /** Instante ISO-8601 (UTC) a partir del cual el código deja de valer. */
  fechaExpiracion: string
}
