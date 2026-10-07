/** Contratos de la API de autenticación (reflejan los DTO de Spring Boot). */

export type Rol = 'ADMIN' | 'USUARIO'

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
