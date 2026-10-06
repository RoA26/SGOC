/** Contratos de la API de autenticación (reflejan los DTO de Spring Boot). */

export type Rol = 'ADMIN' | 'USUARIO'

export interface LoginRequest {
  email: string
  password: string
}

export interface UsuarioResponse {
  id: number
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
