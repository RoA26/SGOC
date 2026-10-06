import axios from 'axios'
import { tokenStorage } from './tokenStorage'

export const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8000/api'

/** Instancia única de Axios para toda la aplicación. */
export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
})

// Adjunta el JWT persistido salvo que la petición ya traiga su propio header.
api.interceptors.request.use((config) => {
  const token = tokenStorage.get()
  if (token && !config.headers.has('Authorization')) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null

/**
 * Registra la acción a ejecutar cuando una petición autenticada recibe un 401
 * (token caducado o revocado). El AuthProvider la usa para cerrar la sesión.
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler
}

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      error.config?.headers.has('Authorization')
    ) {
      unauthorizedHandler?.()
    }
    return Promise.reject(error)
  },
)
