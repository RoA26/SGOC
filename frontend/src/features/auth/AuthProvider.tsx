import axios from 'axios'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { setUnauthorizedHandler } from '@/lib/api'
import { isAuthError } from '@/lib/errors'
import { getTokenExpiration, isTokenExpired } from '@/lib/jwt'
import { tokenStorage } from '@/lib/tokenStorage'
import { AuthContext } from './AuthContext'
import { fetchCurrentUser, loginRequest } from './authApi'
import type { AuthContextValue, AuthState, LoginCredentials } from './types'

const UNAUTHENTICATED: AuthState = { status: 'unauthenticated', token: null, user: null }
const LOADING: AuthState = { status: 'loading', token: null, user: null }

// setTimeout no admite retardos mayores a 2^31 - 1 ms (~24,8 días).
const MAX_TIMEOUT_MS = 2_147_483_647

function getInitialState(): AuthState {
  const token = tokenStorage.get()
  // Con un token vigente se arranca en "loading" hasta que el backend lo confirme.
  return token && !isTokenExpired(token) ? LOADING : UNAUTHENTICATED
}

/**
 * Valida un token persistido contra `GET /auth/me` y devuelve el estado de sesión
 * resultante, o `null` si la petición se canceló.
 */
async function resolveSession(token: string, signal?: AbortSignal): Promise<AuthState | null> {
  try {
    const user = await fetchCurrentUser({ token, signal })
    return { status: 'authenticated', token, user }
  } catch (error) {
    if (axios.isCancel(error)) return null
    // Solo se descarta el token si el backend lo rechaza. Ante una caída de red
    // se conserva para reintentar en la próxima carga de la aplicación.
    if (isAuthError(error)) tokenStorage.clear()
    return UNAUTHENTICATED
  }
}

interface AuthProviderProps {
  children: ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [state, setState] = useState<AuthState>(getInitialState)

  const logout = useCallback(() => {
    tokenStorage.clear()
    setState(UNAUTHENTICATED)
  }, [])

  const login = useCallback(async (credentials: LoginCredentials) => {
    const { access_token: token } = await loginRequest(credentials)
    // El token solo se persiste una vez confirmado que resuelve a un usuario válido.
    const user = await fetchCurrentUser({ token })
    tokenStorage.set(token)
    setState({ status: 'authenticated', token, user })
  }, [])

  // 1) Al montar: validar el token persistido de una sesión anterior.
  useEffect(() => {
    const token = tokenStorage.get()
    if (!token) return
    if (isTokenExpired(token)) {
      // getInitialState ya arrancó en "unauthenticated"; solo falta limpiar.
      tokenStorage.clear()
      return
    }
    const controller = new AbortController()
    void resolveSession(token, controller.signal).then((next) => {
      if (next) setState(next)
    })
    return () => controller.abort()
  }, [])

  // 2) Un 401 en cualquier petición autenticada cierra la sesión.
  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  // 3) Cierre de sesión automático cuando el token expira.
  useEffect(() => {
    if (!state.token) return
    const expiration = getTokenExpiration(state.token)
    if (expiration === null) return
    const delay = expiration - Date.now()
    if (delay > MAX_TIMEOUT_MS) return
    const timeoutId = window.setTimeout(logout, Math.max(delay, 0))
    return () => window.clearTimeout(timeoutId)
  }, [state.token, logout])

  // 4) Sincronización entre pestañas: login/logout en otra pestaña se refleja aquí.
  useEffect(() => {
    function handleStorage(event: StorageEvent) {
      // `key === null` indica localStorage.clear().
      if (event.key !== null && event.key !== tokenStorage.key) return
      const token = tokenStorage.get()
      if (!token || isTokenExpired(token)) {
        setState(UNAUTHENTICATED)
        return
      }
      void resolveSession(token).then((next) => {
        if (next) setState(next)
      })
    }
    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ ...state, isAuthenticated: state.status === 'authenticated', login, logout }),
    [state, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
