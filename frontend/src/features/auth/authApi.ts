import { api } from '@/lib/axios'
import type { LoginRequest, LoginResponse, RegistroRequest, UsuarioResponse } from './types'

/** POST /api/auth/login con `{ username, password }`. 401 si las credenciales no son válidas. */
export async function login(credentials: LoginRequest): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/auth/login', credentials)
  return data
}

/**
 * POST /api/auth/registro. Crea una cuenta con rol USUARIO; no inicia sesión.
 * Errores con `errors: { campo: mensaje }`: 400 (validación o código de invitación
 * inexistente, usado o caducado) y 409 (username o correo ya en uso).
 */
export async function registrarUsuario(data: RegistroRequest): Promise<UsuarioResponse> {
  const { data: usuario } = await api.post<UsuarioResponse>('/auth/registro', data)
  return usuario
}

export async function fetchCurrentUser(signal?: AbortSignal): Promise<UsuarioResponse> {
  const { data } = await api.get<UsuarioResponse>('/auth/me', { signal })
  return data
}
