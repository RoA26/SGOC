import { api } from '@/lib/api'
import type { LoginCredentials, TokenResponse, User } from './types'

export async function loginRequest(credentials: LoginCredentials): Promise<TokenResponse> {
  const { data } = await api.post<TokenResponse>('/auth/login', credentials)
  return data
}

interface FetchCurrentUserOptions {
  /** Token a validar; se envía explícitamente para no depender del almacenamiento. */
  token: string
  signal?: AbortSignal
}

export async function fetchCurrentUser({ token, signal }: FetchCurrentUserOptions): Promise<User> {
  const { data } = await api.get<User>('/auth/me', {
    signal,
    headers: { Authorization: `Bearer ${token}` },
  })
  return data
}
