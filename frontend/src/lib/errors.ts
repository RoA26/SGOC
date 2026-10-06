import axios from 'axios'

interface FastApiValidationIssue {
  loc: (string | number)[]
  msg: string
  type: string
}

interface FastApiErrorBody {
  detail?: string | FastApiValidationIssue[]
}

/** Traduce cualquier error (Axios, FastAPI o desconocido) a un mensaje legible. */
export function getApiErrorMessage(
  error: unknown,
  fallback = 'Ha ocurrido un error inesperado. Inténtalo de nuevo.',
): string {
  if (!axios.isAxiosError<FastApiErrorBody>(error)) return fallback

  if (!error.response) {
    return error.code === 'ECONNABORTED'
      ? 'El servidor tardó demasiado en responder.'
      : 'No se pudo conectar con el servidor. Comprueba tu conexión.'
  }

  const detail = error.response.data?.detail
  if (typeof detail === 'string') return detail
  if (Array.isArray(detail) && detail.length > 0) {
    return detail.map((issue) => issue.msg).join(' ')
  }
  return fallback
}

/** `true` si el backend rechazó la petición por credenciales/token (401) o permisos (403). */
export function isAuthError(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false
  const status = error.response?.status
  return status === 401 || status === 403
}
