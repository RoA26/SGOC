import axios from 'axios'
import { useEmpresaSeleccionadaStore } from '@/features/empresa/empresaSeleccionadaStore'
import { useAuthStore } from '@/store/authStore'

/** Cabecera con la que el SUPER_ADMIN indica al backend en qué empresa trabaja (TenantFilter). */
export const CABECERA_TENANT = 'X-Tenant-ID'

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
  // Arrays como "sort=a&sort=b" (formato de Spring), no "sort[]=a&sort[]=b".
  paramsSerializer: { indexes: null },
})

// Adjunta el JWT de la sesión a cada petición y, solo para el SUPER_ADMIN que eligió una
// empresa, X-Tenant-ID. GERENTE y USUARIO nunca la envían: el backend fija su empresa desde el
// token (y la ignoraría). Una cabecera puesta a mano en la petición (verificación) se respeta.
api.interceptors.request.use((config) => {
  const { accessToken, rol } = useAuthStore.getState()
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  const empresaElegida = useEmpresaSeleccionadaStore.getState().empresaId
  if (rol === 'SUPER_ADMIN' && empresaElegida !== null && !config.headers.has(CABECERA_TENANT)) {
    config.headers.set(CABECERA_TENANT, String(empresaElegida))
  }
  return config
})

/**
 * La sesión deja de servir si, en una petición autenticada, el backend responde:
 * - 401: el token caducó o ya no identifica a nadie;
 * - 403 con `motivo`: el token es válido, pero la cuenta ya no está autorizada (pendiente,
 *   rechazada, desactivada o de una empresa desactivada). Un 403 sin `motivo` es solo falta
 *   de permisos para esa acción y no cierra la sesión.
 */
function cuentaSinAcceso(status: number | undefined, data: unknown): boolean {
  if (status === 401) return true
  return status === 403 && typeof data === 'object' && data !== null && 'motivo' in data
}

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (
      axios.isAxiosError(error) &&
      cuentaSinAcceso(error.response?.status, error.response?.data) &&
      error.config?.headers.has('Authorization')
    ) {
      useAuthStore.getState().clearSession()
    }
    return Promise.reject(error)
  },
)
