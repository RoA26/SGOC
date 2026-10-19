import axios from 'axios'

/** Error RFC 9457 (Problem Details) tal como lo devuelve Spring Boot. */
interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
  /** Errores de validación: campo → mensaje. */
  errors?: Record<string, string>
}

const GENERIC_MESSAGE = 'Ha ocurrido un error inesperado. Inténtalo de nuevo.'

/** Convierte cualquier error de la API en un mensaje legible para el usuario. */
export function getApiErrorMessage(error: unknown): string {
  if (!axios.isAxiosError<ProblemDetail>(error)) return GENERIC_MESSAGE

  if (!error.response) {
    return error.code === 'ECONNABORTED'
      ? 'El servidor tardó demasiado en responder.'
      : 'No se pudo conectar con el servidor.'
  }

  const data = error.response.data
  if (typeof data !== 'object' || data === null) return GENERIC_MESSAGE

  const firstFieldError = data.errors ? Object.values(data.errors)[0] : undefined
  return firstFieldError ?? data.detail ?? GENERIC_MESSAGE
}

/** Errores por campo de un ProblemDetail (`errors: { campo: mensaje }`), o vacío. */
export function getFieldErrors(error: unknown): Record<string, string> {
  if (!axios.isAxiosError<ProblemDetail>(error)) return {}
  const data = error.response?.data
  return typeof data === 'object' && data !== null && data.errors ? data.errors : {}
}

/**
 * Con `responseType: 'blob'` (descargas), el cuerpo de un error también llega como Blob: se
 * convierte a JSON para que getApiErrorMessage pueda leer el ProblemDetail.
 */
export async function leerErrorBlob(error: unknown): Promise<unknown> {
  if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
    try {
      error.response.data = JSON.parse(await error.response.data.text())
    } catch {
      // No era JSON: se queda como estaba y el mensaje será el genérico.
    }
  }
  return error
}
