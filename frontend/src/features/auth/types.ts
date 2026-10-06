/** Contratos de la API de autenticación (reflejan los schemas Pydantic del backend). */

export interface LoginCredentials {
  email: string
  password: string
}

export interface TokenResponse {
  access_token: string
  token_type: 'bearer'
  /** Segundos de validez del token. */
  expires_in: number
}

export interface User {
  id: string
  email: string
  full_name: string
  is_active: boolean
  /** Fecha ISO 8601. */
  created_at: string
}

/**
 * Estado de la sesión como unión discriminada: al comprobar `status === 'authenticated'`
 * TypeScript garantiza que `token` y `user` no son nulos.
 */
export type AuthState =
  | { status: 'loading'; token: null; user: null }
  | { status: 'unauthenticated'; token: null; user: null }
  | { status: 'authenticated'; token: string; user: User }

export type AuthStatus = AuthState['status']

export type AuthContextValue = AuthState & {
  isAuthenticated: boolean
  /** Lanza el error de Axios si las credenciales son inválidas o la red falla. */
  login: (credentials: LoginCredentials) => Promise<void>
  logout: () => void
}
