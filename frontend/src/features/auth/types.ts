/** Contratos de la API de autenticación (reflejan los DTO de Spring Boot). */

/** Roles del backend: GERENTE mantiene catálogos y revisa solicitudes, como ADMIN, sin gestionar invitaciones. */
export type Rol = 'ADMIN' | 'GERENTE' | 'USUARIO'

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
}

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
