import axios from 'axios'
import { useAuthStore } from '@/store/authStore'

/**
 * Instancia única de Axios para toda la aplicación.
 *
 * VITE_API_URL vale "/api" por defecto: en producción Nginx (rrtf.duckdns.org) reenvía
 * /api/ al contenedor de Spring Boot, y en desarrollo lo hace el proxy de Vite.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 15_000,
  headers: { Accept: 'application/json' },
})

// Adjunta el JWT de la sesión a cada petición.
api.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState()
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

// Un 401 en una petición autenticada significa token expirado o revocado: se cierra la sesión.
api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      error.config?.headers.has('Authorization')
    ) {
      useAuthStore.getState().clearSession()
    }
    return Promise.reject(error)
  },
)
