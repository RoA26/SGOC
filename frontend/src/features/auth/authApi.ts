import { api } from '@/lib/axios'
import type { LoginRequest, LoginResponse, UsuarioResponse } from './types'

export async function login(credentials: LoginRequest): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/auth/login', credentials)
  return data
}

export async function fetchCurrentUser(signal?: AbortSignal): Promise<UsuarioResponse> {
  const { data } = await api.get<UsuarioResponse>('/auth/me', { signal })
  return data
}
